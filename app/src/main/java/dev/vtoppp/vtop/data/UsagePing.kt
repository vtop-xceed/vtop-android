package dev.vtoppp.vtop.data

import android.content.Context
import dev.vtoppp.vtop.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Anonymous daily usage ping. Sends { v, d, h } where
 * h = sha256(installId + ":" + d) and d is the UTC day; the install id is
 * a random UUID that never leaves the device. The server stores one
 * unlinkable row per device per day — see usage/ in the repo. No IP,
 * no user-agent, no persistent server-side identity. Release builds only.
 */
object UsagePing {
    private const val PING_URL = "https://vtop-usage.sykik.workers.dev/ping"
    private const val SETTINGS_NAME = "app_settings"
    private const val KEY_LAST_PING = "usage_ping_day"
    private const val KEY_INSTALL_ID = "usage_install_id"

    // ponytail: release builds only — dev installs never touch the counter.
    // Drop the guard later if debug builds should count too.
    fun ping(context: Context) {
        if (BuildConfig.DEBUG) return
        val appContext = context.applicationContext
        val settings = appContext.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
        val today = utcDay()
        val enabled = ThirdPartyServices.isEnabled(appContext, ThirdPartyServices.usagePing)
        if (!shouldSendPing(enabled, settings.getString(KEY_LAST_PING, null), today)) return

        kotlin.concurrent.thread(name = "usage-ping") {
            runCatching {
                val installId = settings.getString(KEY_INSTALL_ID, null)
                    ?: UUID.randomUUID().toString().also {
                        settings.edit().putString(KEY_INSTALL_ID, it).apply()
                    }
                val hash = sha256Hex("$installId:$today")
                val body = JSONObject()
                    .put("v", BuildConfig.VERSION_NAME)
                    .put("d", today)
                    .put("h", hash)
                    .toString()
                val ok = http
                    .newCall(
                        Request.Builder()
                            .url(PING_URL)
                            .post(body.toRequestBody(JSON_TYPE))
                            .build(),
                    )
                    .execute()
                    .use { it.isSuccessful }
                if (ok) settings.edit().putString(KEY_LAST_PING, today).apply()
                else AppLog.d("usage ping: server said no")
            }.onFailure { AppLog.d("usage ping failed (${it.javaClass.simpleName})") }
        }
    }

    private val http: OkHttpClient by lazy {
        sharedLightweightHttpClient(connectTimeoutSec = 5, readTimeoutSec = 5)
    }

    private fun utcDay(): String = LocalDate.now(ZoneOffset.UTC).toString()

    /**
     * The whole gate, as one pure decision: a disabled service never sends,
     * and a day that already carries a successful ping never sends twice.
     */
    internal fun shouldSendPing(enabled: Boolean, lastPingDay: String?, today: String): Boolean =
        enabled && lastPingDay != today

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
}
