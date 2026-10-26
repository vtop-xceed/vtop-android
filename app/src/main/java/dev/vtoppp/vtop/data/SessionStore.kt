package dev.vtoppp.vtop.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted, credential-free snapshot of the last live VTOP session: the
 * cookie map, session csrf and authorizedID, plus a saved-at stamp. A
 * [CredentialVault] sibling (separate pref file and MasterKey alias, so a
 * vault bootstrap reset can never destroy it) — T8: lets a killed process
 * restore via the existing `prewarm()` -> `deriveFromDashboard` path instead
 * of re-running the full login chain on flaky campus Wi-Fi.
 *
 * Failures never crash the app: save/load are best-effort (a broken or stale
 * snapshot is simply treated as absent and the normal login chain runs), and
 * the guarded bootstrap mirrors [CredentialVault]: one delete + rebuild
 * cycle, then a plain (non-credential, low-sensitivity) fallback file.
 */
class SessionStore(
    private val context: Context?,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
    prefsForTest: (() -> SharedPreferences)? = null,
) {

    /** Persisted session snapshot; [cookies] is name -> value. */
    data class Snapshot(
        val cookies: Map<String, String>,
        val csrf: String?,
        val authorizedId: String?,
        val savedAtMs: Long,
    )

    /** Lazy so the [prefsForTest] seam keeps this JVM-testable (no Android Context needed). */
    private val appContext: Context
        get() = checkNotNull(context) { "SessionStore needs a Context unless prefsForTest is supplied" }
            .applicationContext

    private val prefs: SharedPreferences = prefsForTest?.invoke() ?: bootstrapStorage()

    /** Best-effort: storage trouble only means no restore this boot. */
    fun save(cookies: Map<String, String>, csrf: String?, authorizedId: String?) {
        if (cookies.isEmpty() || authorizedId.isNullOrBlank()) return
        try {
            prefs.edit()
                .putString(KEY_COOKIES, encodeCookieMap(cookies))
                .putString(KEY_CSRF, csrf ?: "")
                .putString(KEY_AUTH_ID, authorizedId)
                .putLong(KEY_SAVED_AT, clock())
                .apply()
        } catch (e: Exception) {
            AppLog.d("session-store: save failed (${e.javaClass.simpleName})")
        }
    }

    /** Latest snapshot, or null when absent, unreadable, or older than [maxAgeMs]. */
    fun loadFresh(): Snapshot? = try {
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        val authId = prefs.getString(KEY_AUTH_ID, null)
        val cookieText = prefs.getString(KEY_COOKIES, null)
        val cookies = if (cookieText != null) decodeCookieMap(cookieText) else emptyMap()
        if (!stale(savedAt, clock(), maxAgeMs) && !authId.isNullOrBlank() && cookies.isNotEmpty()) {
            Snapshot(cookies, prefs.getString(KEY_CSRF, null), authId, savedAt)
        } else {
            null
        }
    } catch (e: Exception) {
        AppLog.d("session-store: load failed (${e.javaClass.simpleName})")
        null
    }

    /** T8.4: the explicit-logout ("Forget saved") counterpart of CredentialVault.clear(). */
    fun clear() = try {
        prefs.edit().clear().apply()
        true
    } catch (e: Exception) {
        AppLog.d("session-store: clear failed (${e.javaClass.simpleName})")
        false
    }

    /** Guarded bootstrap: one delete + rebuild cycle before falling back to plain prefs. */
    private fun bootstrapStorage(): SharedPreferences = try {
        encryptedPrefs()
    } catch (base: Exception) {
        AppLog.d("session-store: vtop_session unreadable (${base.javaClass.simpleName}) — deleting storage, recreating once")
        resetStorage()
        try {
            encryptedPrefs()
        } catch (retry: Exception) {
            AppLog.d("session-store: reset-recreate failed (${retry.javaClass.simpleName}) — last-resort plain prefs")
            plainPrefs()
        }
    }

    private fun encryptedPrefs(): SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        MasterKey.Builder(appContext, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Deletes the corrupted pref file and this store's master-key alias. Best effort. */
    private fun resetStorage() {
        try {
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry(MASTER_KEY_ALIAS)
        } catch (e: Exception) {
            AppLog.d("session-store: master-key delete failed (${e.javaClass.simpleName})")
        }
        try {
            appContext.deleteSharedPreferences(PREFS_NAME)
        } catch (e: Exception) {
            AppLog.d("session-store: pref-file delete failed (${e.javaClass.simpleName})")
        }
    }

    private fun plainPrefs(): SharedPreferences =
        appContext.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)

    internal companion object {
        const val PREFS_NAME = "vtop_session"
        const val PREFS_FALLBACK_NAME = "vtop_session_fallback"
        const val MASTER_KEY_ALIAS = "vtop_session_key"
        const val KEY_COOKIES = "sess_cookies"
        const val KEY_CSRF = "sess_csrf"
        const val KEY_AUTH_ID = "sess_auth_id"
        const val KEY_SAVED_AT = "sess_saved_at"

        /** Snapshots older than this never restore (T8.2); configurable per instance. */
        const val DEFAULT_MAX_AGE_MS = 6L * 60 * 60 * 1000

        /** Pure staleness arithmetic so JVM unit tests can pin it. */
        fun stale(savedAtMs: Long, nowMs: Long, maxAgeMs: Long): Boolean =
            savedAtMs <= 0L || nowMs - savedAtMs > maxAgeMs

        /** `name=value` lines; first `=` splits, empty values dropped. */
        fun encodeCookieMap(cookies: Map<String, String>): String =
            cookies.entries.filter { it.key.isNotEmpty() && it.value.isNotEmpty() }
                .joinToString("\n") { "${it.key}=${it.value}" }

        fun decodeCookieMap(text: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (line in text.split('\n')) {
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                val value = line.substring(eq + 1)
                if (value.isNotEmpty()) out[line.substring(0, eq)] = value
            }
            return out
        }
    }
}
