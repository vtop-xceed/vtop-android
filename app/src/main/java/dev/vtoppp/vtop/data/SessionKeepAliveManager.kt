package dev.vtoppp.vtop.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.vtoppp.vtop.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Asks Android's JobScheduler for an inexact keep-alive ping every 10-15 minutes
 * (under VTOP's 20-minute session timeout without waking the phone from Doze).
 * Surfaces a silent, momentary notification during each ping and always
 * dismisses it right after (whether the ping succeeds or fails).
 */
object SessionKeepAliveManager {
    private const val PREFS_NAME = "vtop_keep_alive"
    private const val KEY_ENABLED = "keep_alive_enabled"
    private const val CHANNEL_ID = "vtop_keep_alive_channel"
    private const val NOTIFICATION_ID = 2026
    private const val JOB_ID = 2026
    internal const val MIN_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes
    internal const val MAX_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    private const val MOMENTARY_DISPLAY_MS = 1500L
    private const val SAFETY_TIMEOUT_MS = 10_000L

    @Volatile
    private var activeClient: VtopClient? = null

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean, client: VtopClient?, scope: CoroutineScope) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (enabled && client != null && client.hasSession) {
            start(context, client, scope)
        } else {
            stop(context)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun start(context: Context, client: VtopClient, @Suppress("UNUSED_PARAMETER") scope: CoroutineScope) {
        if (!isEnabled(context) || !client.hasSession) return
        val appContext = context.applicationContext
        activeClient = client
        clearNotification(appContext)
        val scheduler = appContext.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) == null) {
            scheduleNext(appContext)
            AppLog.d("keep-alive: scheduled inexact OS window (10-15m, Doze-safe)")
        }
    }

    internal fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(JobScheduler::class.java) ?: return
        // ponytail: JobScheduler with minLatency=10m & overrideDeadline=15m lets Doze defer pings safely
        val job = JobInfo.Builder(JOB_ID, ComponentName(appContext, KeepAliveJobService::class.java))
            .setMinimumLatency(MIN_INTERVAL_MS)
            .setOverrideDeadline(MAX_INTERVAL_MS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build()
        runCatching { scheduler.schedule(job) }
            .onFailure { AppLog.d("keep-alive: schedule failed: ${it.message}") }
    }

    internal fun activeClientOrNull(): VtopClient? = activeClient

    internal suspend fun executeScheduledPing(context: Context, client: VtopClient): Boolean {
        val ok = runMomentaryPing(
            show = { showNotification(context) },
            hide = { clearNotification(context) },
            ping = { client.keepAlivePing() },
        )
        AppLog.d("keep-alive: ping executed, session active = $ok")
        return ok
    }

    internal suspend fun runMomentaryPing(
        show: () -> Unit,
        hide: () -> Unit,
        minDisplayMs: Long = MOMENTARY_DISPLAY_MS,
        ping: suspend () -> Boolean,
    ): Boolean {
        show()
        val startedAt = System.currentTimeMillis()
        return try {
            val ok = ping()
            val remaining = minDisplayMs - (System.currentTimeMillis() - startedAt)
            if (remaining > 0L) delay(remaining)
            ok
        } catch (t: Throwable) {
            AppLog.d("keep-alive: ping error: ${t.javaClass.simpleName}")
            false
        } finally {
            hide()
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        activeClient = null
        appContext.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        clearNotification(appContext)
        AppLog.d("keep-alive: stopped")
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VTOP Session Keep-Alive",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Silent momentary indicator while pinging VTOP session"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("VTOP Keep-Alive")
            .setContentText("Keep-alive pinging to VTOP")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(SAFETY_TIMEOUT_MS)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    internal fun clearNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFICATION_ID)
    }
}

class KeepAliveJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pingJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val client = SessionKeepAliveManager.activeClientOrNull()
        if (!SessionKeepAliveManager.isEnabled(applicationContext) || client == null || !client.hasSession) {
            SessionKeepAliveManager.clearNotification(applicationContext)
            return false
        }
        pingJob = serviceScope.launch {
            try {
                SessionKeepAliveManager.executeScheduledPing(applicationContext, client)
                if (SessionKeepAliveManager.isEnabled(applicationContext) && client.hasSession) {
                    SessionKeepAliveManager.scheduleNext(applicationContext)
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        pingJob?.cancel()
        SessionKeepAliveManager.clearNotification(applicationContext)
        return false
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
