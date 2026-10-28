package dev.vtoppp.vtop.data

/** HTTP method the transport issues. */
enum class Method { Get, PostForm }

/**
 * One HTTPS request; form pairs stay strictly ordered for POST bodies.
 * Cookies move exclusively through the transport's jar bridge ([OkHttpHttpClient]) —
 * there is no per-request cookie override, matching how a browser behaves.
 */
data class HttpRequest(
    val method: Method,
    val url: String,
    val referer: String? = null,
    val form: List<Pair<String, String>> = emptyList(),
    /** Lets the transport follow the redirect chain (e.g. the dashboard chase). */
    val followRedirects: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

/** Result of one request; Set-Cookie headers stay raw and ordered. */
data class HttpResponse(
    val httpCode: Int,
    val body: String,
    val setCookieHeaders: List<String>,
    /** Last Location response header (redirect target); null when absent. */
    val location: String? = null,
)

/** Like [HttpResponse] but the body bytes stay undecoded for binary payloads. */
data class HttpRawResponse(
    val httpCode: Int,
    val bodyBytes: ByteArray,
    val setCookieHeaders: List<String>,
    val location: String? = null,
)

/**
 * The single in-process HTTPS transport for the VTOP login pipeline
 * ([OkHttpHttpClient]). Deliberately the ONLY transport: no subprocesses, so
 * OEM resource managers (OnePlus, Xiaomi, Vivo, …) cannot kill HTTP calls the
 * way they can spawn child processes.
 */
interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse
    fun executeRaw(request: HttpRequest): HttpRawResponse
}
