package dev.vtoppp.vtop.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * vT-vault-update-hardening: the guarded bootstrap used to delete the whole
 * pref file + master key on the first failed open, erasing saved credentials
 * on a transient post-update Keystore failure. Pin the decision matrix here.
 */
class CredentialVaultBootstrapTest {

    /** Guarded bootstrap never reads/writes while deciding; identity only. */
    private class NoopPrefs : SharedPreferences {
        private val unsupported: Nothing get() = throw UnsupportedOperationException("unused in bootstrap")
        override fun getAll(): MutableMap<String, *> = unsupported
        override fun getString(key: String?, defValue: String?): String? = unsupported
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String> = unsupported
        override fun getInt(key: String?, defValue: Int): Int = unsupported
        override fun getLong(key: String?, defValue: Long): Long = unsupported
        override fun getFloat(key: String?, defValue: Float): Float = unsupported
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = unsupported
        override fun contains(key: String?): Boolean = unsupported
        override fun edit(): SharedPreferences.Editor = unsupported
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = unsupported
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = unsupported
    }

    @Test
    fun opensOnFirstAttempt() {
        val prefs = NoopPrefs()
        var opens = 0

        val boot = guardedBootstrap(
            openEncrypted = { opens++; prefs },
            fallbackPrefs = { throw UnsupportedOperationException("fallback must not run") },
            masterKeyPresent = { throw UnsupportedOperationException("keystore check must not run") },
            resetStorage = { throw UnsupportedOperationException("reset must not run") },
            sleep = { throw UnsupportedOperationException("no retry on first success") },
        )

        assertEquals(BootOutcome.OPENED, boot.outcome)
        assertEquals(1, opens)
        assertSame(prefs, boot.prefs)
    }

    @Test
    fun retriesTransientTroubleBeforeOpening() {
        val prefs = NoopPrefs()
        val opens = AtomicInteger(0)
        val sleeps = mutableListOf<Long>()

        val boot = guardedBootstrap(
            openEncrypted = {
                if (opens.incrementAndGet() <= 2) throw IllegalStateException("tooling jolt")
                prefs
            },
            fallbackPrefs = { throw UnsupportedOperationException("fallback must not run") },
            masterKeyPresent = { true },
            resetStorage = { throw UnsupportedOperationException("reset must not run") },
            sleep = { sleeps += it },
        )

        assertEquals(BootOutcome.OPENED, boot.outcome)
        assertEquals(3, opens.get())
        assertEquals(listOf(50L, 100L), sleeps)
        assertSame(prefs, boot.prefs)
    }

    @Test
    fun persistentTroubleKeepsEncryptedStoreAndUsesFallback() {
        val prefs = NoopPrefs()
        val resets = AtomicInteger(0)
        val opens = AtomicInteger(0)

        val boot = guardedBootstrap(
            openEncrypted = { opens.incrementAndGet(); throw IllegalStateException("dead master cipher") },
            fallbackPrefs = { prefs },
            masterKeyPresent = { true },
            resetStorage = { resets.incrementAndGet() },
            sleep = {},
        )

        assertEquals(BootOutcome.TRANSIENT_FALLBACK, boot.outcome)
        assertEquals(3, opens.get())
        assertEquals(0, resets.get())
        assertSame(prefs, boot.prefs)
    }

    @Test
    fun missingMasterKeyJustifiesOneDestructiveReset() {
        val prefs = NoopPrefs()
        val resets = AtomicInteger(0)
        val opens = AtomicInteger(0)

        val boot = guardedBootstrap(
            openEncrypted = {
                // Dead on every entry since disk lost the master key, but the
                // reset re-creates an openable store with no stale blobs.
                if (resets.get() == 0) { opens.incrementAndGet(); throw IllegalStateException("key gone") }
                prefs
            },
            fallbackPrefs = { throw UnsupportedOperationException("reset must open a store") },
            masterKeyPresent = { false },
            resetStorage = { resets.incrementAndGet() },
            sleep = {},
        )

        assertEquals(BootOutcome.RESET_AND_OPENED, boot.outcome)
        assertEquals(1, resets.get())
        assertSame(prefs, boot.prefs)
    }

    @Test
    fun resetThatStillFailsFallsBackPlain() {
        val prefs = NoopPrefs()
        val resets = AtomicInteger(0)

        val boot = guardedBootstrap(
            openEncrypted = { throw IllegalStateException("paused key") },
            fallbackPrefs = { prefs },
            masterKeyPresent = { false },
            resetStorage = { resets.incrementAndGet() },
            sleep = {},
        )

        assertEquals(BootOutcome.RESET_FALLBACK, boot.outcome)
        assertEquals(1, resets.get())
        assertSame(prefs, boot.prefs)
    }
}
