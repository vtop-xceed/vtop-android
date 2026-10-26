package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin tests for T11 (error taxonomy) + T12 (cookie lifecycle, pacing)
 * on [VtopClient]: Set-Cookie deletion parsing, clearSession allowlist, the
 * reauth failure pass-through mapping and the monotonic pacing window. Cookie
 * and clearSession internals are reached through reflection (private by
 * design); no Android runtime is touched.
 */
class VtopClientT11T12Test {

    private fun newClient(nowMs: () -> Long = { 0L }) = VtopClient(AutoCaptchaClient(), nowMs)

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

    // -------------------------------------------------- T12.1: deletion parse

    @Test
    fun `maxAge zero Set-Cookie deletes the stored name`() {
        val c = newClient()
        c.merge("JSESSIONID=stale")
        assertEquals("stale", c.cookieMap()["JSESSIONID"])
        c.merge("JSESSIONID=; Max-Age=0; Path=/vtop")
        assertFalse(c.cookieMap().containsKey("JSESSIONID"))
    }

    @Test
    fun `empty value Set-Cookie deletes the stored name`() {
        val c = newClient()
        c.merge("JSESSIONID=stale")
        c.merge("JSESSIONID=; Path=/vtop")
        assertFalse(c.cookieMap().containsKey("JSESSIONID"))
    }

    @Test
    fun `nonzero maxAge and nonempty value still store`() {
        val c = newClient()
        c.merge("JSESSIONID=s1; Max-Age=3600; Path=/", "SHIBE=w1")
        assertEquals("s1", c.cookieMap()["JSESSIONID"])
        assertEquals("w1", c.cookieMap()["SHIBE"])
        // Replace-on-set still works.
        c.merge("JSESSIONID=s2")
        assertEquals("s2", c.cookieMap()["JSESSIONID"])
    }

    @Test
    fun `maxAge matching is case-insensitive and boundary-exact`() {
        val c = newClient()
        c.merge("JSESSIONID=stale")
        c.merge("JSESSIONID=; max-age=0")
        assertFalse(c.cookieMap().containsKey("JSESSIONID"))
        c.merge("JSESSIONID=keep")
        c.merge("JSESSIONID=keep; Max-Age=0123")
        assertEquals("keep", c.cookieMap()["JSESSIONID"])
    }

    // -------------------------------------------------- T12.2: allowlist clear

    @Test
    fun `clearSession drops session cookies but keeps the WAF allowlist`() {
        val c = newClient()
        c.merge("JSESSIONID=sess", "cookiesession1=waf", "OTHER=1")
        c.clear()
        assertEquals(setOf("cookiesession1"), c.cookieMap().keys)
        assertEquals("waf", c.cookieMap()["cookiesession1"])
    }

    @Test
    fun `clearSession on an empty store is a no-op`() {
        val c = newClient()
        c.clear()
        assertTrue(c.cookieMap().isEmpty())
    }

    // --------------------------------------------- T11.3: reauth failure mapping

    @Test
    fun `reauthFailure passes known VtopErrors through unchanged`() {
        for (e in listOf(
            VtopError.Network,
            VtopError.CaptchaRejected,
            VtopError.CredentialsRejected("bad creds"),
            VtopError.Blocked("WAF_BLOCKED(403)"),
            VtopError.RateLimited,
        )) {
            assertSame(e, reauthFailure(e))
        }
    }

    @Test
    fun `reauthFailure maps only ambiguous failures to SessionExpired`() {
        val e = IllegalStateException("no csrf")
        val mapped = reauthFailure(e)
        assertEquals(VtopError.SessionExpired, mapped)
        assertEquals("SESSION_EXPIRED", mapped.message)
    }

    // -------------------------------------------------- T12.3: monotonic pacing

    @Test
    fun `paceDelayMs zero sentinel means go now`() {
        assertEquals(0L, paceDelayMs(0L, 999_999L))
    }

    @Test
    fun `paceDelayMs waits the remaining window`() {
        assertEquals(5_000L, paceDelayMs(5_000L, 10_000L))
        assertEquals(1L, paceDelayMs(10_000L, 19_999L))
    }

    @Test
    fun `paceDelayMs never goes negative once the window elapsed`() {
        assertEquals(0L, paceDelayMs(10_000L, 20_000L))
        assertEquals(0L, paceDelayMs(10_000L, 500_000L))
    }

    @Test
    fun `clock seam is injectable and monotonic-backed`() {
        var tick = 0L
        val c = VtopClient(AutoCaptchaClient(), { ++tick })
        // The seam is stored and used for CAPTCHA bookkeeping; verify injection
        // by reading the field back through the constructor's captured lambda.
        val f = VtopClient::class.java.getDeclaredField("nowMs")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val seam = f.get(c) as () -> Long
        assertEquals(1L, seam())
        assertEquals(2L, seam())
    }
}
