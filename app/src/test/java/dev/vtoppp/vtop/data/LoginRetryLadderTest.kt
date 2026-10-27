package dev.vtoppp.vtop.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * T20.3 [LoginRetryLadderTest] — the login retry ladder on
 * [VtopClient.authenticate] with injected transport + solver + clock:
 * three captcha rejections exhaust the ladder → CaptchaRejected; a wrong
 * password aborts on attempt 1; a UA block fails fast; a stale captcha is
 * re-solved before the POST. Virtual time drives the injected clock, so the
 * pacing delays are deterministic (timestamps proved in [LoginPacingTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginRetryLadderTest {

    /**
     * Two-phase establish chain + scenario-driven POSTs: content(GET) →
     * login(GET role csrf) → prelogin/setup(POST) → init/page(GET warm csrf)
     * → get/new/captcha(GET data URI) → [loginResult(attempt)] per login POST,
     * then login/error(GET) for every bounce. GET round-trips build dashboards
     * only once [dashboardFromAttempt] login POSTs succeeded (restore path).
     */
    private fun router(
        loginResult: (attempt: Int) -> HttpResponse,
        errorBody: String = "another Invalid Captcha round",
        dashboardFromAttempt: Int? = null,
    ): FakeTransport {
        var loginPosts = 0
        val dashboardHtml = VtopPages.dashboard("derived-csrf", "EXAMPLE0000001")
        val dashboard = HttpResponse(200, dashboardHtml, emptyList(), null)
        return FakeTransport { req ->
            when {
                req.method == Method.PostForm && req.url.endsWith("/vtop/prelogin/setup") ->
                    HttpResponse(200, "", FakeCookies.SESSION_PAIR, null)
                req.method == Method.PostForm && req.url.endsWith("/vtop/login") ->
                    loginResult(++loginPosts)
                req.url.endsWith("/vtop/login/error") ->
                    HttpResponse(
                        200,
                        """<html><body><span class="text-danger">$errorBody</span></body></html>""",
                        FakeCookies.SESSION_PAIR,
                        null,
                    )
                req.url.endsWith("/vtop/content") ->
                    if (loginPosts >= (dashboardFromAttempt ?: Int.MAX_VALUE)) dashboard
                    else HttpResponse(200, VtopPages.loginForm("content-csrf"), FakeCookies.SESSION_PAIR, null)
                req.url.endsWith("/vtop/login") -> // GET role page
                    HttpResponse(200, VtopPages.loginForm("role-csrf"), FakeCookies.SESSION_PAIR, null)
                req.url.endsWith("/vtop/init/page") ->
                    if (loginPosts >= (dashboardFromAttempt ?: Int.MAX_VALUE)) dashboard
                    else HttpResponse(200, VtopPages.loginForm("warm-csrf"), FakeCookies.SESSION_PAIR, null)
                req.url.endsWith("/vtop/get/new/captcha") ->
                    HttpResponse(200, VtopPages.captchaPage(), FakeCookies.SESSION_PAIR, null)
                else -> HttpResponse(404, "", FakeCookies.SESSION_PAIR, null)
            }
        }
    }

    private val captchaBounce: HttpResponse =
        HttpResponse(302, "", emptyList(), VtopPages.ERROR_LOCATION)

    private val loginSuccess = HttpResponse(302, "", emptyList(), "/vtop/init/page")

    @Test
    fun threeCaptchaBouncesExhaustTheLadder() = runTest {
        val http = router(loginResult = { captchaBounce })
        val solver = SteadySolver("ABC")
        val client = VtopClient(solver, { testScheduler.currentTime }, transportOverride = http)

        val result = client.login("user", "pass")

        val err = result.exceptionOrNull()
        check(err is VtopError.CaptchaRejected) { "expected CaptchaRejected, got $err" }
        check(solver.calls == 3) { "expected 3 solves, got ${solver.calls}" }
        check(http.postCountTo("/vtop/login") == 3) { "expected 3 POSTs" }
        check(http.countTo("/vtop/login/error") == 3) { "error page fetched once per bounce" }
        check(http.countTo("/vtop/get/new/captcha") == 3) { "captcha re-rolled per attempt" }
    }

    @Test
    fun wrongPasswordAbortsOnAttemptOne() = runTest {
        val http = router(
            loginResult = { captchaBounce },
            errorBody = "Invalid LoginId/Password",
        )
        val client = VtopClient(SteadySolver("ABC"), { testScheduler.currentTime }, transportOverride = http)

        val result = client.login("user", "wrongpass")

        val err = result.exceptionOrNull()
        check(err is VtopError.CredentialsRejected) { "expected CredentialsRejected, got $err" }
        check(err.message == "Invalid LoginId/Password")
        check(http.postCountTo("/vtop/login") == 1) { "no retry after a credential verdict" }
        check(http.countTo("/vtop/get/new/captcha") == 1) { "captcha budget untouched" }
    }

    @Test
    fun uaBlockFailsFastWithoutRetries() = runTest {
        val http = router(loginResult = { _ ->
            HttpResponse(200, "Unidentified OS or Browser", emptyList(), null)
        })
        val client = VtopClient(SteadySolver("ABC"), { testScheduler.currentTime }, transportOverride = http)

        val result = client.login("user", "pass")

        // fail("VTOP refused...") lands in the Unknown bucket; the ladder
        // must NOT burn its captcha budget on a UA block.
        val err = result.exceptionOrNull()
        check(err is VtopError.Unknown && err.message.contains("refused this client")) {
            "expected Unknown(refused), got $err"
        }
        check(http.postCountTo("/vtop/login") == 1) { "UA block is final, no retries" }
    }

    @Test
    fun staleCaptchaIsReSolvedBeforeThePost() = runTest {
        val http = router(
            loginResult = { loginSuccess },
            dashboardFromAttempt = 1,
        )
        val solver = SteadySolver("ABC")
        val client = VtopClient(solver, { testScheduler.currentTime }, transportOverride = http)

        // Captcha solved by prewarm at virtual t=0.
        client.prewarm()
        check(client.warm.value is WarmState.CaptchaReady) { "prewarm: ${client.warm.value}" }
        // Past CAPTCHA_MAX_AGE_MS, the POST must not use the stale text.
        kotlinx.coroutines.delay(65_000)

        val result = client.login("user", "pass")

        check(result.isSuccess) { "expected success, got ${result.exceptionOrNull()}" }
        check(solver.calls == 2) { "expected re-solve (prep + stale), got ${solver.calls}" }
        check(http.countTo("/vtop/get/new/captcha") == 2) { "captcha re-rolled with the re-solve" }
    }
}
