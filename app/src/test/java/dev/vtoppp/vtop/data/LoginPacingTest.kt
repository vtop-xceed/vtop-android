package dev.vtoppp.vtop.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * T20.5 [LoginPacingTest] — a 10s floor stands between consecutive login
 * POSTs, proven by virtual-clock timestamps at the injected transport plus
 * the pure pacing-window function. Companion to [LoginRetryLadderTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginPacingTest {

    private val captchaBounce = HttpResponse(302, "", emptyList(), VtopPages.ERROR_LOCATION)

    @Test
    fun paceDelayMsPureWindow() {
        // Sentinel: never logged a POST → go now.
        check(paceDelayMs(0L, 999_999L) == 0L)
        // Inside the window: the remainder waits.
        check(paceDelayMs(5_000L, 10_000L) == 10_000L - 5_000L)
        // Past the window: 0, never negative.
        check(paceDelayMs(5_000L, 20_000L) == 0L)
    }

    @Test
    fun consecutivePostsWaitTheFullFloor() = runTest {
        val postTimes = mutableListOf<Long>()
        val http = FakeTransport { req ->
            when {
                req.method == Method.PostForm && req.url.endsWith("/vtop/prelogin/setup") ->
                    HttpResponse(200, "", FakeCookies.SESSION_PAIR, null)
                req.method == Method.PostForm && req.url.endsWith("/vtop/login") -> {
                    postTimes += testScheduler.currentTime
                    captchaBounce
                }
                req.url.endsWith("/vtop/login/error") ->
                    HttpResponse(
                        200,
                        """<html><body><span class="text-danger">Invalid Captcha</span></body></html>""",
                        FakeCookies.SESSION_PAIR,
                        null,
                    )
                req.url.endsWith("/vtop/content") ->
                    HttpResponse(200, VtopPages.loginForm("content"), FakeCookies.SESSION_PAIR, null)
                req.url.endsWith("/vtop/login") -> // GET role page
                    HttpResponse(200, VtopPages.loginForm("role-csrf"), FakeCookies.SESSION_PAIR, null)
                req.url.endsWith("/vtop/init/page") ->
                    HttpResponse(200, VtopPages.loginForm("warm"), FakeCookies.SESSION_PAIR, null)
                req.url.endsWith("/vtop/get/new/captcha") ->
                    HttpResponse(200, VtopPages.captchaPage(), FakeCookies.SESSION_PAIR, null)
                else -> HttpResponse(404, "", FakeCookies.SESSION_PAIR, null)
            }
        }

        val client = VtopClient(SteadySolver("ABC"), { testScheduler.currentTime }, transportOverride = http)

        // Move off the pacing sentinel (0 == "never posted" in paceDelayMs):
        // the first POST lands at t=1_000_000, so every later POST is paced
        // against a real lastLoginPostAt.
        kotlinx.coroutines.delay(1_000_000)
        val result = client.login("user", "pass")

        check(result.exceptionOrNull() is VtopError.CaptchaRejected)

        // 3 attempts; each gap ≥ the 10s floor (both delays skipped by
        // virtual time, but the injected clock proves the pacing happened).
        check(postTimes.size == 3) { "expected 3 POST timestamps, got $postTimes" }
        check(postTimes[1] - postTimes[0] >= 10_000L) { "gap1: ${postTimes[1] - postTimes[0]}" }
        check(postTimes[2] - postTimes[1] >= 10_000L) { "gap2: ${postTimes[2] - postTimes[1]}" }
    }
}
