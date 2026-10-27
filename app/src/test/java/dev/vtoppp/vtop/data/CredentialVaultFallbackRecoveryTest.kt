package dev.vtoppp.vtop.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * vT-vault-boot-memory: the transient-fallback boot journals sign-in pairs
 * into the plain fallback file while later healthy boots open the (possibly
 * emptied) encrypted store — the pair then hid from hasCredentials and every
 * launch landed on the manual form (OnePlus 0.1.6, 2026-10-27). Pin the
 * one-shot recovery decision matrix here.
 */
class CredentialVaultFallbackRecoveryTest {

    /** Minimal in-memory prefs; only the operations recovery touches. */
    private class MemoryPrefs : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor = this.apply { data[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this.apply { data[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = this.apply { data.remove(key) }
            override fun clear(): SharedPreferences.Editor = this.apply { data.clear() }
            override fun commit(): Boolean = true
            override fun apply() = Unit
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
        }

        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun getAll(): MutableMap<String, *> = data
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private fun seededPrefs(): MemoryPrefs = MemoryPrefs().apply {
        data[CredentialVault.KEY_USER] = "EXAMPLE0000001"
        data[CredentialVault.KEY_PASS] = "correct horse"
    }

    @Test
    fun copiesFallbackCredentialsIntoEmptyActiveStore() {
        val prefs = MemoryPrefs()
        val fallback = seededPrefs()

        val recovered = recoverFallbackCredentialsInto(prefs, fallback)

        assertTrue(recovered)
        assertEquals("EXAMPLE0000001", prefs.getString(CredentialVault.KEY_USER, null))
        assertEquals("correct horse", prefs.getString(CredentialVault.KEY_PASS, null))
        assertFalse(prefs.getBoolean(CredentialVault.KEY_GATED, true))
        // The plain copy leaves: the encrypted store is now the only home.
        assertFalse(fallback.contains(CredentialVault.KEY_USER))
        assertFalse(fallback.contains(CredentialVault.KEY_PASS))
        assertFalse(fallback.contains(CredentialVault.KEY_GATED))
    }

    @Test
    fun neverOverwritesAnActiveStoreThatHasCredentials() {
        val prefs = seededPrefs().apply {
            data[CredentialVault.KEY_USER] = "NEWER00000001"
            data[CredentialVault.KEY_PASS] = "newer secret"
        }
        val fallback = seededPrefs()

        val recovered = recoverFallbackCredentialsInto(prefs, fallback)

        assertFalse(recovered)
        assertEquals("NEWER00000001", prefs.getString(CredentialVault.KEY_USER, null))
        assertTrue(fallback.contains(CredentialVault.KEY_USER))
    }

    @Test
    fun leavesGatedActiveStoresAlone() {
        val prefs = MemoryPrefs().apply { data[CredentialVault.KEY_GATED] = true }
        val fallback = seededPrefs()

        val recovered = recoverFallbackCredentialsInto(prefs, fallback)

        assertFalse(recovered)
        assertFalse(prefs.contains(CredentialVault.KEY_USER))
        assertTrue(fallback.contains(CredentialVault.KEY_USER))
    }

    @Test
    fun isANoOpWhenTheFallbackHasNothing() {
        val prefs = MemoryPrefs()
        val fallback = MemoryPrefs()

        val recovered = recoverFallbackCredentialsInto(prefs, fallback)

        assertFalse(recovered)
        assertFalse(prefs.contains(CredentialVault.KEY_USER))
    }
}
