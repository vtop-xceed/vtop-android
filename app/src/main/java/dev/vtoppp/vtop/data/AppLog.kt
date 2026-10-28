package dev.vtoppp.vtop.data

import android.content.Context
import dev.vtoppp.vtop.BuildConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** logcat replacement for this device: appended in-app to filesDir/vtop-debug.log */
object AppLog {
    private var file: File? = null

    private val lock = Any()

    /** guarded by [lock]; SimpleDateFormat is not thread-safe. */
    private val ts = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** file names whose dumps are debug evidence, not user data. */
    private val dumpPrefixes = listOf("c-", "rejection-", "captcha.html", "post-body.html")
    private const val MAX_DUMP_FILES = 16
    private const val MAX_DUMP_AGE_MS = 48L * 60 * 60 * 1000
    private const val MAX_LOG_BYTES = 512L * 1024

    /** Small replayed stream of recent log lines, for the login UI. */
    private val _lines = MutableSharedFlow<String>(
        replay = 6,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val lines: SharedFlow<String> = _lines

    fun init(context: Context) {
        val f = File(context.filesDir, "vtop-debug.log")
        // Rotate: keep one generation (.1). The live log is first moved to a
        // staging name; every cleanup happens only after the preceding move
        // is verified, so a failed rotation never deletes history.
        try {
            if (f.length() > MAX_LOG_BYTES) {
                val rotated = File(context.filesDir, "vtop-debug.log.1")
                val staging = File(context.filesDir, "vtop-debug.log.rotating")
                if (!f.renameTo(staging)) {
                    // A stale staging file from an interrupted rotation can
                    // block the move: clear it and retry (live log untouched).
                    if (!(staging.exists() && staging.delete() && f.renameTo(staging))) {
                        file = f
                        return
                    }
                }
                // The live log is safely in staging; only now drop the
                // previous generation and finish the rotation.
                rotated.delete()
                if (!staging.renameTo(rotated)) {
                    // Restore the live log rather than leave it stranded.
                    staging.renameTo(f)
                }
            }
        } catch (_: Exception) {
        }
        file = f
    }

    fun dump(name: String, content: String) {
        if (!BuildConfig.DEBUG) return
        val f = file ?: return
        synchronized(lock) {
            try {
                java.io.File(f.parent, "$name.html").writeText(content)
            } catch (_: Exception) {
            }
            pruneDumps(f.parent)
        }
    }

    /** Keeps the newest [MAX_DUMP_FILES] dumps that are younger than 48h. */
    private fun pruneDumps(dirPath: String?) {
        val dir = dirPath ?: return
        val cutoff = System.currentTimeMillis() - MAX_DUMP_AGE_MS
        val dumps = File(dir).listFiles { file ->
            file.isFile &&
                file.name.endsWith(".html") &&
                dumpPrefixes.any { file.name.startsWith(it) || file.name == it }
        } ?: return
        dumps.filter { it.lastModified() < cutoff }.forEach { it.delete() }
        dumps.sortedByDescending { it.lastModified() }
            .drop(MAX_DUMP_FILES)
            .forEach { it.delete() }
    }

    fun d(msg: String) {
        _lines.tryEmit(msg)
        try {
            android.util.Log.d("VtopLog", msg)
        } catch (_: Throwable) {
        }
        val f = file ?: return
        synchronized(lock) {
            // SimpleDateFormat.format is not thread-safe: format strictly
            // inside the same lock that serializes appends.
            val line = "${ts.format(Date())} $msg"
            try {
                f.appendText("$line\n")
            } catch (_: Exception) {
            }
        }
    }
}
