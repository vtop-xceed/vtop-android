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
 * Failures never crash the app: bootstrap failures no longer destroy the
 * storage — vT-vault-update-hardening: transient open trouble at cold start
 * (common right after an app update or just after unlock on some OEM builds)
 * is retried with small waits and then degrades to a plain fallback store for
 * this process, keeping the encrypted file for later boots; the destructive
 * reset runs only when the master-key alias is provably gone, because those
 * stored blobs can never decrypt again. Keystore trouble only destroys sealed
 * data that is provably unrecoverable, and transient failures leave prefs
 * untouched.
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

    private val prefs: SharedPreferences

    var canGate: Boolean
        private set

    init {
        // Gated mode seals the password with a per-use Keystore cipher and needs
        // a Crypto-based prompt, which Android only allows for Class 3 (strong)
        // biometrics. Devices whose only authenticator is Class 2 (e.g. face
        // unlock on some ColorOS/OnePlus builds) must NOT gate: prompting with
        // a CryptoObject there throws IllegalArgumentException and crashes.
        // vT-gate-refusal: the Manager's verdict alone is not proof — ColorOS
        // on this OnePlus reports BIOMETRIC_SUCCESS for BIOMETRIC_STRONG yet
        // refuses every CryptoObject prompt at runtime, so a persisted refusal
        // marker (set when a save first sees the refusal) forces the ungated
        // tier for the rest of the device's life with this app.
        val managerAllowsGate = BiometricManager.from(appContext).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        ) == BiometricManager.BIOMETRIC_SUCCESS
        // vT-vault-update-hardening: the seam keeps JVM tests on plain prefs;
        // production runs the guarded bootstrap and stamps health from its
        // outcome instead of assuming every degraded boot is LostCredentials.
        prefs = prefsForTest?.invoke(appContext) ?: run {
            val boot = guardedBootstrap(
                openEncrypted = ::encryptedPrefs,
                fallbackPrefs = ::plainPrefs,
                masterKeyPresent = ::keystoreHasMasterKey,
                resetStorage = { resetStorage() },
                sleep = { Thread.sleep(it) },
                log = AppLog::d,
            )
            if (boot.outcome != BootOutcome.OPENED) {
                state = when (boot.outcome) {
                    BootOutcome.RESET_AND_OPENED, BootOutcome.RESET_FALLBACK -> VaultState.LostCredentials
                    BootOutcome.TRANSIENT_FALLBACK -> VaultState.TemporarilyLocked
                    BootOutcome.OPENED -> VaultState.Ready
                }
            }
            // vT-vault-boot-memory: a transient boot may have journaled the last
            // sign-in into the plain fallback file while later healthy boots open
            // the (older, possibly emptied) encrypted store — the pair then hid
            // from hasCredentials and every launch landed on the manual form
            // (OnePlus, 2026-10-27). One-shot recovery copies a fallback-only
            // user into the active store. The test seam skips this: production
            // storage is not in play there.
            recoverFallbackCredentialsInto(boot.prefs, plainPrefs(), AppLog::d)
            boot.prefs
        }
        canGate = managerAllowsGate && !prefs.getBoolean(KEY_GATE_BROKEN, false)
    }

    /**
     * vT-gate-refusal: records a runtime crypto-prompt refusal permanently.
     * The Manager claimed biometrics; the prompt path is broken for Crypto
     * use, and future saves/unlocks must not dirge through it again.
     */
    fun markGateBroken() {
        if (canGate) {
            prefs.edit().putBoolean(KEY_GATE_BROKEN, true).apply()
            canGate = false
            AppLog.d("vault: crypto gate marked broken for this device")
        }
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
        // vT-vault-boot-memory: "Forget saved account" must also clear the
        // plain fallback copy, or the one-shot recovery resurrects a
        // deliberately forgotten account on the next healthy boot.
        plainPrefs().edit().clear().apply()
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

    /**
     * Master-key presence check. Failure here is treated conservatively as
     * "present", so a flaky check can never justify wiping the store.
     */
    private fun keystoreHasMasterKey(): Boolean = try {
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
    } catch (e: Exception) {
        AppLog.d("vault: master-key presence check failed (${e.javaClass.simpleName})")
        true
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
    internal companion object {
        const val KEY_ALIAS = "vtop_creds_gated"
        const val KEY_USER = "vtop_user"
        const val KEY_PASS = "vtop_pass"
        const val KEY_BLOB = "vtop_pass_blob"
        const val KEY_IV = "vtop_pass_iv"
        const val KEY_GATED = "vtop_gated"
        const val KEY_GATE_BROKEN = "vtop_gate_broken"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val PREFS_NAME = "vtop_creds"
        const val PREFS_FALLBACK_NAME = "vtop_creds_fallback"
        const val NOTICE_LOST = "Your saved account was lost during startup — sign in again; you may be asked to re-save your password."
        const val NOTICE_LOCKED = "Saved credentials can't be unlocked right now — try again in a moment."
    }
}

/** Surfaced vault health, shared by boot reset (T5) and unlock/save handling (T6). */
enum class VaultState { Ready, LostCredentials, TemporarilyLocked }

/** How the guarded vault bootstrap resolved this process's backing store. */
internal enum class BootOutcome {
    OPENED,
    RESET_AND_OPENED,
    TRANSIENT_FALLBACK,
    RESET_FALLBACK,
}

internal data class VaultBoot(
    val prefs: SharedPreferences,
    val outcome: BootOutcome,
)

/**
 * Guarded vault bootstrap decision matrix (vT-vault-update-hardening).
 *
 * History: the previous implementation deleted the pref file and the master
 * key on the FIRST failed open, so one transient exception right after an
 * app update erased every saved credential and forced a full manual sign-in.
 *
 * Now: (1) retries the open with small waits; (2) persistent trouble degrades
 * to the plain fallback WITHOUT deleting the encrypted store — this boot does
 * not see saved credentials, but the next healthy boot self-heals and nothing
 * is lost; (3) the destructive reset runs only when the encrypted store exists
 * while its master-key alias is provably absent, since those blobs can never
 * decrypt again. Pure over callables so JVM tests can pin it.
 */
internal fun guardedBootstrap(
    openEncrypted: () -> SharedPreferences,
    fallbackPrefs: () -> SharedPreferences,
    masterKeyPresent: () -> Boolean,
    resetStorage: () -> Unit,
    attempts: Int = 3,
    sleep: (Long) -> Unit = {},
    log: (String) -> Unit = {},
): VaultBoot {
    var lastFailure: Exception? = null
    repeat(attempts) { attempt ->
        try {
            return VaultBoot(openEncrypted(), BootOutcome.OPENED)
        } catch (e: Exception) {
            lastFailure = e
            log("vault: vtop_creds open attempt ${attempt + 1}/$attempts failed (${e.javaClass.simpleName})")
            sleep(50L * (attempt + 1))
        }
    }
    if (!masterKeyPresent()) {
        log("vault: store survives without its master key — resetting storage (${lastFailure?.javaClass?.simpleName})")
        resetStorage()
        return try {
            VaultBoot(openEncrypted(), BootOutcome.RESET_AND_OPENED)
        } catch (e: Exception) {
            log("vault: reset-recreate failed (${e.javaClass.simpleName}) — last-resort plain fallback")
            VaultBoot(fallbackPrefs(), BootOutcome.RESET_FALLBACK)
        }
    }
    log("vault: treating open failure as transient — encrypted store kept, plain fallback for this process")
    return VaultBoot(fallbackPrefs(), BootOutcome.TRANSIENT_FALLBACK)
}

/**
 * vT-vault-boot-memory one-shot recovery.
 *
 * The guarded bootstrap's transient path degrades this process to the plain
 * fallback file; a sign-in saved on such a boot lands there, while every later
 * healthy boot that opens the encrypted store successfully would look at an
 * older (possibly emptied) store and miss the pair — the app then showed the
 * manual sign-in form on every launch even though credentials were saved
 * (observed on a class-2 OnePlus running 0.1.6, 2026-10-27).
 *
 * When the freshly opened active store is a non-gated store WITHOUT a user
 * row while the fallback file HAS one, the fallback pair is copied across and
 * then wiped from the plain copy so the encrypted store stays the single home
 * of the secret. Returns true when a recovery happened.
 */
internal fun recoverFallbackCredentialsInto(
    prefs: SharedPreferences,
    fallback: SharedPreferences,
    log: (String) -> Unit = {},
): Boolean {
    if (prefs.getBoolean(CredentialVault.KEY_GATED, false)) return false
    if (prefs.contains(CredentialVault.KEY_USER)) return false
    val user = fallback.getString(CredentialVault.KEY_USER, null) ?: return false
    val pass = fallback.getString(CredentialVault.KEY_PASS, null) ?: return false
    prefs.edit()
        .putString(CredentialVault.KEY_USER, user)
        .putString(CredentialVault.KEY_PASS, pass)
        .putBoolean(CredentialVault.KEY_GATED, false)
        .apply()
    fallback.edit()
        .remove(CredentialVault.KEY_USER)
        .remove(CredentialVault.KEY_PASS)
        .remove(CredentialVault.KEY_GATED)
        .remove(CredentialVault.KEY_BLOB)
        .remove(CredentialVault.KEY_IV)
        .apply()
    log("vault: recovered fallback-only credentials into the active store")
    return true
}
