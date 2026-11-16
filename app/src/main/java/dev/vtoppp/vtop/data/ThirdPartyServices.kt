package dev.vtoppp.vtop.data

import android.content.Context

/**
 * One outbound service the app can be told not to contact.
 *
 * [id] doubles as the preference key suffix, so adding a service is:
 *  1. one entry in [ThirdPartyServices.all],
 *  2. one gate check in that service's network entry point.
 */
data class ThirdPartyService(
    val id: String,
    val label: String,
    val description: String,
)

object ThirdPartyServices {
    /** Shared with the update checker; the opt-out keys live next to it. */
    const val PREFS_NAME = "app_settings"
    private const val KEY_PREFIX = "service_enabled_"

    val usagePing = ThirdPartyService(
        id = "usage_ping",
        label = "Anonymous usage ping",
        description = "Anonymized ping to a public endpoint to estimate the number of users.",
    )

    val all = listOf(usagePing)

    fun isEnabled(context: Context, service: ThirdPartyService): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFIX + service.id, true)

    fun setEnabled(context: Context, service: ThirdPartyService, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX + service.id, enabled)
            .apply()
    }

    /** Called after the user switches a service back on. */
    fun onEnabled(context: Context, service: ThirdPartyService) {
        when (service.id) {
            usagePing.id -> UsagePing.ping(context)
        }
    }
}
