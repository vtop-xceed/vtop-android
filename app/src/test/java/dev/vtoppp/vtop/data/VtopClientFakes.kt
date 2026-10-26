package dev.vtoppp.vtop.data

/**
 * T20 support fakes: solver + transport doubles so login-chain scenarios run
 * pure-JVM with zero network. VtopClient merges cookies from the reported
 * Set-Cookie headers exactly like production, so no jar bridging is needed.
 */

/** Solver fake counting solve() calls and returning queued answers. */
class FakeSolver(private val answers: List<String?>) : CaptchaSolver {
    var calls: Int = 0
        private set

    override suspend fun solve(imageJpeg: ByteArray): String? =
        answers.getOrNull(calls++)
}

/** Solver that always answers the same (e.g. an always-wrong captcha). */
class SteadySolver(private val answer: String?) : CaptchaSolver {
    var calls: Int = 0
        private set

    override suspend fun solve(imageJpeg: ByteArray): String? = answer.also { calls++ }
}

/** Transport fake driven by a URL router; records request order. */
class FakeTransport(
    private val router: (request: HttpRequest) -> HttpResponse,
) : HttpTransport {

    val requests = mutableListOf<HttpRequest>()

    override fun execute(request: HttpRequest): HttpResponse =
        router(request).also { requests += request }

    override fun executeRaw(request: HttpRequest): HttpRawResponse {
        val r = router(request)
        requests += request
        return HttpRawResponse(
            r.httpCode,
            r.body.toByteArray(Charsets.UTF_8),
            r.setCookieHeaders,
            r.location,
        )
    }

    fun countTo(pathSuffix: String): Int = requests.count { it.url.endsWith(pathSuffix) }

    fun postCountTo(pathSuffix: String): Int =
        requests.count { it.method == Method.PostForm && it.url.endsWith(pathSuffix) }
}

/** Canonical HTML fixtures for the two-phase establish-then-post chain. */
object VtopPages {

    /** Login page marker: stdForm, no page-wrapper ([looksLikeLoginPage] true). */
    fun loginForm(csrf: String): String =
        """<html><body><form id="vtopLoginForm" class="stdForm">""" +
            """<input type="hidden" name="_csrf" value="$csrf"/></form></body></html>"""

    /** Dashboard marker page ([looksLikeLoginPage] false, [deriveFromDashboard] happy). */
    fun dashboard(csrfValue: String, authId: String): String = """
        <html><body class="page-wrapper">
            <input name="authorizedID" value="$authId"/>
            <script>var csrfValue = "$csrfValue";</script>
        </body></html>
    """.trimIndent()

    /** Data-URI JPEG payload; decodes to >500 bytes (mime-decoder path). */
    fun captchaPage(): String {
        val encoded = java.util.Base64.getEncoder().encodeToString(ByteArray(600) { 0x41 })
        return """<html><img src="data:image/jpeg;base64,$encoded"/></html>"""
    }

    /** Server's failure redirect target, as observed on the real portal. */
    const val ERROR_LOCATION = "/vtop/login/error"
}

/** Set-Cookie headers for the establish hop (JSESSIONID + the WAF cookie). */
object FakeCookies {
    val SESSION_PAIR = listOf(
        "JSESSIONID=E2E7A2789068E81B4C463E9CF5A4E263; Path=/; HttpOnly",
        "cookiesession1=54D6631EABC41A1AE5CD2F65B1CC11D0; Path=/",
    )
}
