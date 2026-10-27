package dev.vtoppp.vtop.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * One GitHub release fact the updater needs. [version] is the tag with any
 * leading "v" stripped, directly comparable to [android.content.Context]
 * versionName.
 */
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val title: String,
    val notes: String?,
    val publishedAt: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
)

/**
 * Startup update check flow: ping the GitHub Releases API once, compare the
 * latest tag against this build, download the APK into cacheDir/updates with
 * progress, then hand the user a one-tap Install entry (FileProvider-backed).
 *
 * The check itself is silent: no network, old SDK quirks or GitHub hiccups
 * leave the app unchanged (only [AppLog] notes the reason), so the login/home
 * flow never pays for update plumbing. Failures surface in the dialog only
 * after an explicit (manual or accepted download) step.
 */
class AutoUpdater(context: Context) {

    /** Backing state for the update dialog; consumed by [AutoUpdateDialog]. */
    var state: AutoUpdateState by mutableStateOf<AutoUpdateState>(AutoUpdateState.Idle)
        private set

    private val appContext = context.applicationContext

    /** Overview prefs; the auto-check opt-out lives here, not in the vault. */
    private val settings =
        appContext.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)

    /** Whether a startup ping is allowed; the dialog exposes the switch. */
    var autoCheckEnabled: Boolean
        get() = settings.getBoolean(KEY_AUTO_CHECK, true)
        set(value) {
            settings.edit().putBoolean(KEY_AUTO_CHECK, value).apply()
        }

    /** [Later] makes this session stop nagging about one particular release. */
    private var dismissedTags = mutableSetOf<String>()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Startup ping: only fires when enabled and the release is not dismissed. */
    fun autoCheck() {
        if (!autoCheckEnabled) return
        check(force = false)
    }

    /** Explicit user check (Home action): always honors [Later]-dismissed tags for nag-suppression. */
    fun check(force: Boolean = true) {
        if (state is AutoUpdateState.Downloading) return
        state = AutoUpdateState.Checking
        kotlin.concurrent.thread(name = "update-check") {
            val result = runCatching { fetchLatest() }
            mainHandler.post {
                when {
                    result.isSuccess -> {
                        val release = result.getOrNull()
                        val current = currentVersion()
                        when {
                            release == null ->
                                state = if (force) AutoUpdateState.UpToDate(current) else AutoUpdateState.Idle
                            isNewer(release.version, current) && !(release.tag in dismissedTags && !force) ->
                                state = AutoUpdateState.Available(release)
                            force ->
                                state = AutoUpdateState.UpToDate(current)
                            else -> state = AutoUpdateState.Idle
                        }
                    }
                    force -> state = AutoUpdateState.Failed(describe(result.exceptionOrNull()))
                    else -> {
                        AppLog.d("update: startup check failed (${result.exceptionOrNull()?.javaClass?.simpleName})")
                        state = AutoUpdateState.Idle
                    }
                }
            }
        }
    }

    fun dismiss(release: ReleaseInfo) {
        dismissedTags += release.tag
        state = AutoUpdateState.Idle
    }

    /** Downloads the release APK into cacheDir/updates/<version>.apk. */
    fun startDownload(release: ReleaseInfo) {
        if (state is AutoUpdateState.Downloading) return
        state = AutoUpdateState.Downloading(received = 0, total = release.apkSize.takeIf { it > 0 })
        val file = apkFileFor(release)
        AppLog.d("update: downloading ${release.tag} from ${release.apkUrl}")
        kotlin.concurrent.thread(name = "update-download") {
            val outcome = runCatching {
                http.newCall(
                    okhttp3.Request.Builder()
                        .url(release.apkUrl)
                        .header("User-Agent", getUserAgent())
                        .header("Accept", "application/octet-stream")
                        .build(),
                ).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    file.parentFile?.mkdirs()
                    file.outputStream().use { sink ->
                        response.body.byteStream().use { src ->
                            val buf = ByteArray(BUFFER_SIZE)
                            var read: Int
                            var received = 0L
                            while (src.read(buf).also { read = it } != -1) {
                                sink.write(buf, 0, read)
                                received += read
                                sink.flush()
                                mainHandler.post {
                                    if (state is AutoUpdateState.Downloading) {
                                        state = AutoUpdateState.Downloading(
                                            received = received,
                                            total = response.body.contentLength().takeIf { it > 0 },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            mainHandler.post {
                if (outcome.isSuccess) {
                    state = AutoUpdateState.Ready(release, file)
                } else {
                    // Clean up a partial download; bytes cannot become an APK.
                    file.delete()
                    AppLog.d("update: download failed (${outcome.exceptionOrNull()?.javaClass?.simpleName})")
                    state = AutoUpdateState.Failed(describe(outcome.exceptionOrNull()))
                }
            }
        }
    }
    /** The user pressed Update in the ready dialog; hands off to the system installer. */
    fun install(release: ReleaseInfo, file: File) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        runCatching {
            appContext.startActivity(intent)
            AppLog.d("update: launching installer for ${release.tag}")
        }.onFailure {
            state = AutoUpdateState.Failed("Could not open the installer: ${describe(it)}")
        }
    }

    fun reset() {
        state = AutoUpdateState.Idle
    }

    /** True only when the stored build to install is older than the local one. */
    fun isNewerInstall(release: ReleaseInfo): Boolean =
        isNewer(release.version, currentVersion())

    private fun fetchLatest(): ReleaseInfo? {
        val request = okhttp3.Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", getUserAgent())
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return null // repo has no releases yet
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val json = JSONObject(response.body.string())
            val assets = json.optJSONArray("assets") ?: return null
            var apk: JSONObject? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                    apk = asset
                    break
                }
            }
            val chosen = apk ?: return null
            val tag = json.optString("tag_name", "")
            return ReleaseInfo(
                tag = tag,
                version = tag.removePrefix("v"),
                title = json.optString("name").ifBlank { tag },
                notes = json.optString("body").takeIf { it.isNotBlank() },
                publishedAt = json.optString("published_at", ""),
                apkName = chosen.optString("name", "app-update.apk"),
                apkUrl = chosen.optString("browser_download_url", ""),
                apkSize = chosen.optLong("size", 0L),
            ).takeIf { it.apkUrl.isNotBlank() && it.version.isNotBlank() }
        }
    }

    private fun apkFileFor(release: ReleaseInfo): File =
        File(File(appContext.cacheDir, "updates"), release.version + ".apk")

    private fun currentVersion(): String = appContext.getString(appContext.applicationInfo.labelRes).let {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: ""
    }

    private fun getUserAgent(): String = "vtop-android/" + currentVersion()

    private fun describe(error: Throwable?): String =
        error?.message?.take(120) ?: error?.javaClass?.simpleName ?: "unknown"

    internal companion object {
        const val RELEASES_URL = "https://api.github.com/repos/vtop-xceed/vtop-android/releases/latest"
        private const val SETTINGS_NAME = "app_settings"
        private const val KEY_AUTO_CHECK = "auto_update_check"
        private const val BUFFER_SIZE = 65536

        /**
         * Semver-ish compare: "0.2" > "0.1.9" (missing segments count as 0),
         * non-numeric trailing parts stay ignored; equal means not newer.
         */
        fun isNewer(remote: String, local: String): Boolean {
            val r = segments(remote)
            val l = segments(local)
            for (i in 0 until maxOf(r.size, l.size)) {
                val ri = r.getOrNull(i) ?: 0
                val li = l.getOrNull(i) ?: 0
                if (ri != li) return ri > li
            }
            return false
        }

        private fun segments(version: String): List<Int> = version
            .trim()
            .removePrefix("v")
            .takeWhile { it.isDigit() || it == '.' }
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
    }
}

/** Update-flow states surfaced by the dialog UI. */
sealed interface AutoUpdateState {
    data object Idle : AutoUpdateState
    data object Checking : AutoUpdateState

    /** Manual check with no newer release; the dialog says up-to-date. */
    data class UpToDate(val currentVersion: String) : AutoUpdateState
    data class Available(val release: ReleaseInfo) : AutoUpdateState
    data class Downloading(val received: Long, val total: Long?) : AutoUpdateState
    data class Ready(val release: ReleaseInfo, val file: File) : AutoUpdateState
    data class Failed(val reason: String) : AutoUpdateState
}
