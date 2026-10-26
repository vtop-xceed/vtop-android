package dev.vtoppp.vtop.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T20.4 [VtopClientSessionRestoreTest] — a persisted snapshot (JSESSIONID +
 * cookiesession1 + csrf + authorizedID) re-establishes the session either by
 * validating the dashboard on [VtopClient.prewarm] (one GET, no captcha
 * chain) or by the T15.3 short-circuit in [VtopClient.authenticate] (zero
 * round trips). Seeding goes through reflection, mirroring what
 * [SessionStore.loadFresh] would deliver.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VtopClientSessionRestoreTest {

    /** Cookie map only — authenticated fields stay null so prewarm runs. */
    private fun seedCookies(client: VtopClient) {
        val cookiesField = VtopClient::class.java.getDeclaredField("cookies")
        cookiesField.isAccessible = true
        val map = cookiesField[client] as MutableMap<String, String>
        map.putAll(
            listOf(
                "JSESSIONID" to "E2E7A2789068E81B4C463E9CF5A4E263",
                "cookiesession1" to "54D6631EABC41A1AE5CD2F65B1CC11D0",
            ),
        )
    }

    /** Full snapshot replica: authenticated fields set (T15.3 short-circuit). */
    private fun seedSession(client: VtopClient) {
        seedCookies(client)
        for (name in listOf("csrf", "authorizedId")) {
            val f = VtopClient::class.java.getDeclaredField(name)
            f.isAccessible = true
            f[client] = if (name == "csrf") "seed-csrf" else "AUTH1234567"
        }
    }

    private val dashboardPage = VtopPages.dashboard("restored-csrf", "AUTH1234567")

    @Test
    fun prewarmRestoresViaDashboardGetWithoutCaptchaChain() = runTest {
        val http = FakeTransport { req ->
            check(req.url.endsWith("/vtop/content")) { "unexpected request ${req.url}" }
            HttpResponse(200, dashboardPage, emptyList(), null)
        }
        val client = VtopClient(SteadySolver("ABC"), { 0L }, transportOverride = http)
        seedCookies(client)

        client.prewarm()

        assertTrue(client.warm.value is WarmState.CaptchaReady)
        assertTrue(client.hasSession)
        // Exactly one validation GET; no prelogin/captcha/login round trips.
        assertEquals(1, http.requests.size)
        assertNull((client.warm.value as? WarmState.Failed)?.message)
        assertFalse(http.requests.any { it.url.endsWith("/vtop/get/new/captcha") })
    }

    @Test
    fun deadSnapshotFallsBackToLoginChain() = runTest {
        // Stale cookies + a login-lookalike content page: restore fails at
        // content and the client immediately proceeds to the two-phase chain.
        val http = FakeTransport { req ->
            when {
                req.url.endsWith("/vtop/content") ->
                    HttpResponse(200, VtopPages.loginForm("content-csrf"), emptyList(), null)
                req.method == Method.PostForm && req.url.endsWith("/vtop/prelogin/setup") ->
                    HttpResponse(200, "", emptyList(), null)
                req.url.endsWith("/vtop/init/page") ->
                    HttpResponse(200, VtopPages.loginForm("warm-csrf"), emptyList(), null)
                req.url.endsWith("/vtop/get/new/captcha") ->
                    HttpResponse(200, VtopPages.captchaPage(), emptyList(), null)
                req.url.endsWith("/vtop/login") ->
                    HttpResponse(200, VtopPages.loginForm("role-csrf"), emptyList(), null)
                else -> HttpResponse(404, "", emptyList(), null)
            }
        }
        val solver = SteadySolver("ABC")
        val client = VtopClient(solver, { 0L }, transportOverride = http)
        seedCookies(client)

        client.prewarm()

        assertTrue(client.warm.value is WarmState.CaptchaReady)
        check(solver.calls == 1) { "captcha chain ran once after dead-snapshot fallback" }
        check(http.countTo("/vtop/get/new/captcha") == 1) { "single captcha roll" }
    }

    @Test
    fun authenticateShortCircuitsOnLiveSession() = runTest {
        val http = FakeTransport { req -> throw AssertionError("no request ever sent: ${req.url}") }
        val client = VtopClient(SteadySolver("ABC"), { 0L }, transportOverride = http)
        seedSession(client)

        val result = client.login("user", "pass")

        val res = result.getOrNull()!!
        assertEquals("AUTH1234567", res.authorizedId)
        assertTrue(res.studentLabel.endsWith("(STUDENT)"))
        assertTrue(client.hasSession)
    }
}
