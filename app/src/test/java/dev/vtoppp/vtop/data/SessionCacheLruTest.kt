package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * T20.6 [SessionCacheLruTest] — save/load round-trip, prune-24 (newest wins),
 * blank→null, and endpoint→slug collision pinning, via the T19
 * [SessionCache.init] root-file seam (pure JVM).
 */
class SessionCacheLruTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun resetRoot() {
        // Fresh cache root per test; the object is process-global.
        SessionCache.init(tmp.newFolder("cache"))
    }

    @Test
    fun saveLoadRoundTripReturnsHtmlAndSavedAt() {
        SessionCache.save("studentsRecord/StudentProfileAllView", "<html>profile body</html>")

        val (html, savedAt) = SessionCache.load("studentsRecord/StudentProfileAllView")!!

        assertEquals("<html>profile body</html>", html)
        assertTrue(savedAt > 0)
    }

    @Test
    fun blankHtmlLoadReturnsNull() {
        SessionCache.save("divider", "   ")
        assertNull(SessionCache.load("divider"))
    }

    @Test
    fun unknownEndpointReturnsNull() {
        assertNull(SessionCache.load("never/saved"))
    }

    @Test
    fun pruneKeepsOnlyThe24NewestEntries() {
        val endpoints = (1..30).map { "sec$it" }
        endpoints.forEach { SessionCache.save(it, "<html>$it</html>") }

        // Distinct, ordered mtimes so prune's newest-wins is well-defined.
        val root = tmp.root.resolve("cache")
        root.listFiles()!!.forEach { f ->
            val index = endpoints.indexOfFirst { f.name.startsWith(it) }
            f.setLastModified(1_700_000_000_000L + index)
        }

        // Oldest six are gone; the 24 newest survive.
        for (i in 1..6) assertNull("sec$i should be pruned", SessionCache.load("sec$i"))
        for (i in 7..30) {
            val loaded = SessionCache.load("sec$i")
            assertEquals("<html>sec$i</html>", loaded?.first)
        }
    }

    @Test
    fun slugCollisionOverwritesTheOlderFile() {
        // "a b" and "a?b" share the alnum slug → same file, second wins.
        SessionCache.save("ab", "old")
        SessionCache.save("a b", "newer")
        SessionCache.save("a-b", "newest")

        val loaded = SessionCache.load("a*b")  // slugs to "ab"

        assertEquals("newest", loaded?.first)
        assertEquals("newest", SessionCache.load("ab")?.first)
    }
}
