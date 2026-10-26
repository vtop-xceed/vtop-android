package dev.vtoppp.vtop.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T8: SessionStore snapshot serialization, staleness arithmetic, and
 * save/load/clear round-trips on an in-memory SharedPreferences seam
 * (production uses EncryptedSharedPreferences under `vtop_session`).
 */
class SessionStoreTest {

    /** Minimal in-memory SharedPreferences backing the JVM test seam. */
    private class FakePrefs : SharedPreferences {
        val map = HashMap<String, Any?>()

        private inner class Ed : SharedPreferences.Editor {
            override fun putString(k: String, v: String?): SharedPreferences.Editor =
                apply { if (v == null) map.remove(k) else map[k] = v }

            override fun putStringSet(k: String, v: Set<String>?): SharedPreferences.Editor = this
            override fun putInt(k: String, v: Int): SharedPreferences.Editor = this
            override fun putLong(k: String, v: Long): SharedPreferences.Editor = apply { map[k] = v }
            override fun putFloat(k: String, v: Float): SharedPreferences.Editor = this
            override fun putBoolean(k: String, v: Boolean): SharedPreferences.Editor = this
            override fun remove(k: String): SharedPreferences.Editor = apply { map.remove(k) }
            override fun clear(): SharedPreferences.Editor = apply { map.clear() }
            override fun commit(): Boolean = true
            override fun apply() {}
        }

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(k: String, def: String?): String? = map[k] as String?
        override fun getStringSet(k: String, def: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(k: String, def: Int): Int = def
        override fun getLong(k: String, def: Long): Long = map[k] as Long? ?: def
        override fun getFloat(k: String, def: Float): Float = def
        override fun getBoolean(k: String, def: Boolean): Boolean = def
        override fun contains(k: String): Boolean = map.containsKey(k)
        override fun edit(): SharedPreferences.Editor = Ed()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    /** Both stores must hang off the SAME backing map to share the snapshot. */
    private val backing = FakePrefs()

    private fun store(nowMs: Long) = SessionStore(
        context = null,
        maxAgeMs = 60_000L,
        clock = { nowMs },
        prefsForTest = { backing },
    )

    @Test
    fun `cookie map round-trips through the line encoding`() {
        val src = mapOf("JSESSIONID" to "1a2B3c", "cookiesession1" to "x=y_Bar")
        val encoded = SessionStore.encodeCookieMap(src)
        val decoded = SessionStore.decodeCookieMap(encoded)
        assertEquals(src, decoded)
    }

    @Test
    fun `stale arithmetic treats zero saved-at and beyond-max-age as stale`() {
        assertTrue(SessionStore.stale(0L, 1_000L, 60_000L))
        assertTrue(SessionStore.stale(7_000L, 67_001L, 60_000L))
        assertFalse(SessionStore.stale(7_000L, 67_000L, 60_000L))
    }

    @Test
    fun `loadFresh returns the fresh snapshot`() {
        val saver = store(nowMs = 1_000L)
        saver.save(mapOf("JSESSIONID" to "abc"), "csrf-1", "ST1234")
        // Same snapshot read later, still within the max age.
        val reader = store(nowMs = 31_000L)
        val snap = reader.loadFresh()
        assertEquals(mapOf("JSESSIONID" to "abc"), snap?.cookies)
        assertEquals("csrf-1", snap?.csrf)
        assertEquals("ST1234", snap?.authorizedId)
    }

    @Test
    fun `stale snapshot is treated as absent`() {
        val saver = store(nowMs = 31_000L)
        saver.save(mapOf("JSESSIONID" to "abc"), "csrf-1", "ST1234")
        // Same snapshot read later, past the configurable max age.
        val reader = store(nowMs = 1_000L + 90_999L)
        assertNull(reader.loadFresh())
    }

    @Test
    fun `clear wipes the snapshot`() {
        val s = store(nowMs = 31_000L)
        s.save(mapOf("JSESSIONID" to "abc"), "csrf-1", "ST1234")
        assertTrue(s.clear())
        assertNull(s.loadFresh())
    }

    @Test
    fun `save refuses empty cookie maps or missing authorizedId`() {
        val s = store(nowMs = 31_000L)
        s.save(emptyMap(), "csrf-1", "ST1234")
        s.save(mapOf("JSESSIONID" to "abc"), "csrf-1", "")
        assertNull(s.loadFresh())
    }
}
