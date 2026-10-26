package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T20.1 [VtopCookieLifecycleTest] — the four cookie-lifecycle invariants
 * (T8/T12 synergy) not already pinned by [VtopClientT11T12Test]:
 * the WAF cookie's survival across clear+merge cycles, replace-on-set
 * rotation, attribute-heavy full Set-Cookie strings, and multi-hop merges
 * keeping the last hop's value.
 */
class VtopCookieLifecycleTest {

    private fun newClient() = VtopClient(AutoCaptchaClient())

    private fun VtopClient.merge(vararg raw: String) {
        val m = VtopClient::class.java.getDeclaredMethod("mergeCookies", List::class.java)
        m.isAccessible = true
        m.invoke(this, raw.toList())
    }

    @Suppress("UNCHECKED_CAST")
    private fun VtopClient.cookieMap(): MutableMap<String, String> {
        val f = VtopClient::class.java.getDeclaredField("cookies")
        f.isAccessible = true
        return f.get(this) as MutableMap<String, String>
    }

    private fun VtopClient.clear() {
        val m = VtopClient::class.java.getDeclaredMethod("clearSession")
        m.isAccessible = true
        m.invoke(this)
    }

    @Test
    fun wafCookieSurvivesAClearMergeClearCycle() {
        val c = newClient()
        // Full-establishment: JSESSIONID rotate + WAF cookie arrive in one hop.
        c.merge(
            "JSESSIONID=54D6631EABC41A1AE5CD2F65B1CC1BFD; Path=/; HttpOnly",
            "cookiesession1=511E1E519C1EE6CC92D0; Path=/",
        )
        c.clear() // session reset (e.g. expiry path)
        c.merge(
            "JSESSIONID=FF0000AA55; Path=/; HttpOnly",
            "cookiesession1=511E1E519C1EE6CC92D0; Path=/",
        )

        val kept = c.cookieMap()
        assertEquals(setOf("JSESSIONID", "cookiesession1"), kept.keys)
        assertEquals("FF0000AA55", kept["JSESSIONID"])
        // The WAF cookie survives both clears with the same issuer value.
        assertEquals("511E1E519C1EE6CC92D0", kept["cookiesession1"])
    }

    @Test
    fun replaceOnSetRotationKeepsOnlyTheLastValue() {
        val c = newClient()
        c.merge("JSESSIONID=first")
        c.merge("JSESSIONID=second")
        c.merge("JSESSIONID=third; Path=/; Secure")

        assertEquals("third", c.cookieMap()["JSESSIONID"])
    }

    @Test
    fun attributeHeavySetCookieStillParsesThePair() {
        val c = newClient()
        // No-space `;Expires=...` splits (server seen in the wild) and
        // long attribute chains must not corrupt the pair split.
        c.merge(
            "JSESSIONID=ABCDEF0123456789; Path=/;	HttpOnly;Expires=Wed;21-Oct-2026 07:28:00 GMT",
        )
        assertEquals("ABCDEF0123456789", c.cookieMap()["JSESSIONID"])
    }

    @Test
    fun multiHopMergeKeepsTheLastHopsValue() {
        val c = newClient()
        // A redirect chain re-issues the session cookie mid-hop (302 hop).
        c.merge(
            "JSESSIONID=hop1; Path=/",
            "JSESSIONID=hop2; Path=/",
            "cookiesession1=WAFVALUE; Path=/",
        )
        c.clear()
        c.merge("JSESSIONID=hopFinal; Path=/")

        assertTrue(c.cookieMap()["JSESSIONID"] == "hopFinal")
        assertEquals("WAFVALUE", c.cookieMap()["cookiesession1"])
    }

    @Test
    fun headerWithoutNameIsIgnored() {
        val c = newClient()
        c.merge("=novalue", "; Path=/", "  ")
        assertTrue(c.cookieMap().isEmpty())
    }
}
