package dev.vtoppp.vtop.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Credential storage. Two modes:
 *  - gated (default when the device has an authenticator): the password is sealed
 *    with a Keystore key that requires biometric/device-credential confirmation
 *    per use; unlock happens through BiometricPrompt with a CryptoObject;
 *  - fallback (no authenticator available): password lives in
 *    EncryptedSharedPreferences only, readable without user auth.
 *
 * Failures never crash the app: bootstrap failures rebuild the storage once and
 * surface a degraded [state]; keystore trouble only destroys sealed data that is
 * provably unrecoverable, and transient failures leave prefs untouched.
 */
class CredentialVault(
    context: Context,
    /**
     * Test seam: inject backing storage instead of the AndroidKeyStore-backed
     * EncryptedSharedPreferences. JVM unit tests use this; production passes
     * nothing and takes the guarded bootstrap path.
     */
    prefsForTest: ((Context) -> SharedPreferences)? = null,
) {

    private val appContext = context.applicationContext

    private var degradedBoot = false

    /** User-visible vault state, consumed by MainActivity to show a login banner. */
    var state: VaultState = VaultState.Ready
        private set

    /** One-line notice for [state]; null when everything is normal. */
    val stateNotice: String?
        get() = when (state) {
            VaultState.Ready -> null
            VaultState.LostCredentials -> NOTICE_LOST
            VaultState.TemporarilyLocked -> NOTICE_LOCKED
        }

    private val prefs: SharedPreferences = prefsForTest?.invoke(appContext) ?: bootstrapStorage()

    var canGate: Boolean
        private set

    init {
        // Gated mode seals the password with a per-use Keystore cipher and needs
        // a Crypto-based prompt, which Android only allows for Class 3 (strong)
        // biometrics. Devices whose only authenticator is Class 2 (e.g. face
        // unlock on some ColorOS/OnePlus builds) must NOT gate: prompting with
        // a CryptoObject there throws IllegalArgumentException and crashes.
        canGate = BiometricManager.from(appContext).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        ) == BiometricManager.BIOMETRIC_SUCCESS
        if (degradedBoot) state = VaultState.LostCredentials
    }

    val isGated: Boolean
        get() = prefs.getBoolean(KEY_GATED, false)

    val hasCredentials: Boolean
        get() = prefs.contains(KEY_USER)

    val username: String?
        get() = prefs.getString(KEY_USER, null)

    /** Plaintext password; only readable in the non-gated fallback mode. */
    val password: String?
        get() = if (isGated) null else prefs.getString(KEY_PASS, null)

    fun savePlaintext(user: String, pass: String) {
        prefs.edit()
            .putString(KEY_USER, user)
            .putString(KEY_PASS, pass)
            .putBoolean(KEY_GATED, false)
            .remove(KEY_BLOB)
            .remove(KEY_IV)
            .apply()
        state = VaultState.Ready
    }

    /** Cipher seam for the save prompt; the same cipher turns the password into the blob. */
    fun newSaveCipher(): Cipher = initSaveCipher(obtainKey())

    /**
     * ENCRYPT init around the gated key. An invalidated key (device
     * fingerprint/PIN change) is cleaned up and regenerated exactly once;
     * every other init failure propagates to the caller, which retries once
     * and then falls back explicitly — never silently — to plaintext.
     */
    private fun initSaveCipher(key: SecretKey): Cipher = try {
        Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        invalidKeyCleanup()
        initSaveCipher(obtainKey())
    }

    fun saveGated(user: String, pass: String, cipher: Cipher) {
        val blob = cipher.doFinal(pass.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_USER, user)
            .putString(KEY_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putBoolean(KEY_GATED, true)
            .remove(KEY_PASS)
            .apply()
        state = VaultState.Ready
    }

    /** DECRYPT cipher for the unlock prompt; null when the gated key is unusable (vault cleared). */
    fun newUnlockCipher(): Cipher? {
        if (!isGated) return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        var lastTransient: Exception? = null
        repeat(2) {
            val key = try {
                obtainKey()
            } catch (e: KeyPermanentlyInvalidatedException) {
                invalidKeyCleanup()
                return null
            } catch (e: Exception) {
                lastTransient = e
                return@repeat
            }
            try {
                return Cipher.getInstance(TRANSFORM).apply {
                    init(
                        Cipher.DECRYPT_MODE,
                        key,
                        GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
                    )
                }
            } catch (e: KeyPermanentlyInvalidatedException) {
                invalidKeyCleanup()
                return null
            } catch (e: InvalidAlgorithmParameterException) {
                // A corrupted/foreign IV never recovers: the sealed blob is gone.
                invalidKeyCleanup()
                return null
            } catch (e: IllegalArgumentException) {
                // Malformed stored IV.
                invalidKeyCleanup()
                return null
            } catch (e: GeneralSecurityException) {
                lastTransient = e
            }
        }
        // Transient keystore trouble (seen on Xiaomi/vivo right after unlock or
        // boot): retried once above, prefs untouched, a later prompt may win.
        AppLog.d("vault: unlock temporarily unavailable (${lastTransient?.javaClass?.simpleName})")
        state = VaultState.TemporarilyLocked
        return null
    }

    /** Decrypts the stored blob with the authentication-proven [cipher]. */
    fun revealPassword(cipher: Cipher): String? = try {
        val blob = Base64.decode(prefs.getString(KEY_BLOB, "") ?: "", Base64.NO_WRAP)
        String(cipher.doFinal(blob), Charsets.UTF_8)
    } catch (e: AEADBadTagException) {
        // The sealed blob no longer matches the key: permanently undecryptable.
        AppLog.d("vault: stored blob rejected (AEADBadTag)")
        invalidKeyCleanup()
        null
    } catch (e: Exception) {
        // Any other decrypt trouble is treated as transient: prefs untouched.
        AppLog.d("vault: unlock failed transiently (${e.javaClass.simpleName})")
        state = VaultState.TemporarilyLocked
        null
    }

    fun clear() {
        prefs.edit().clear().apply()
        state = VaultState.Ready
    }

    /**
     * Keystore invalidation: the sealed data can never be recovered with the
     * old key, so the undecryptable guarded material is dropped. KEY_USER is
     * kept so the login screen still offers the saved username, and the state
     * surfaces "re-save your password".
     */
    private fun invalidKeyCleanup() {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
        prefs.edit()
            .putBoolean(KEY_GATED, false)
            .remove(KEY_BLOB)
            .remove(KEY_IV)
            .remove(KEY_PASS)
            .apply()
        state = VaultState.LostCredentials
    }

    private fun obtainKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** Guarded bootstrap: one delete + rebuild cycle before giving up encrypted storage. */
    private fun bootstrapStorage(): SharedPreferences = try {
        encryptedPrefs()
    } catch (base: Exception) {
        AppLog.d("vault: vtop_creds unreadable (${base.javaClass.simpleName}) — deleting storage, recreating once")
        resetStorage()
        try {
            degradedBoot = true
            encryptedPrefs()
        } catch (retry: Exception) {
            AppLog.d("vault: reset-recreate failed (${retry.javaClass.simpleName}) — last-resort plain prefs")
            degradedBoot = true
            plainPrefs()
        }
    }

    private fun encryptedPrefs(): SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Deletes the corrupted pref file and the master-key alias. Best effort. */
    private fun resetStorage() {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (e: Exception) {
            AppLog.d("vault: master-key delete failed (${e.javaClass.simpleName})")
        }
        try {
            appContext.deleteSharedPreferences(PREFS_NAME)
        } catch (e: Exception) {
            AppLog.d("vault: pref-file delete failed (${e.javaClass.simpleName})")
        }
    }

    private fun plainPrefs(): SharedPreferences =
        appContext.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val KEY_ALIAS = "vtop_creds_gated"
        const val KEY_USER = "vtop_user"
        const val KEY_PASS = "vtop_pass"
        const val KEY_BLOB = "vtop_pass_blob"
        const val KEY_IV = "vtop_pass_iv"
        const val KEY_GATED = "vtop_gated"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val PREFS_NAME = "vtop_creds"
        const val PREFS_FALLBACK_NAME = "vtop_creds_fallback"
        const val NOTICE_LOST = "Your saved account was lost during startup — sign in again; you may be asked to re-save your password."
        const val NOTICE_LOCKED = "Saved credentials can't be unlocked right now — try again in a moment."
    }
}

/** Surfaced vault health, shared by boot reset (T5) and unlock/save handling (T6). */
enum class VaultState { Ready, LostCredentials, TemporarilyLocked }
