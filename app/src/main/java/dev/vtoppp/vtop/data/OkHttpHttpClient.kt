package dev.vtoppp.vtop.data

import java.io.IOException
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp transport for the VTOP login pipeline, ported from the decompiled
 * Gamma VTOP app (com.vtopu.app 4.8, idea-RE/GAMMA_REA_REPORT.md §5/§6 and
 * /tmp/vtop-src VtopRepository.java): a plain `OkHttpClient.Builder()` with a
 * cookie jar, redirect-following enabled, a custom android `User-Agent` and
 * form-encoded POST bodies. Gamma ships this exact stack against the same
 * portal WAF that earlier rejected this app's first OkHttp attempt — the WAF
 * accepts an OkHttp client as long as cookie fidelity is kept (every
 * Set-Cookie re-sent on later hops, the `cookiesession1` WAF cookie never
 * dropped). That jar is bridged to the caller's cookie map so the caller keeps
 * owning session/WAF cookie lifecycle (name-keyed, replace-on-set).
 *
 * In-process by design: the only HTTPS transport, no subprocesses, so OEM
 * resource managers (OnePlus/Xiaomi-style process killers) can never interpose
 * on network calls.
 *
 * Wire semantics: [HttpRequest] models per-request flags (method, ordered
 * form pairs, referer, follow-hop redirects), and the [HttpResponse] carries
 * the final status, a lenient-UTF-8 body (binary payloads stay bytes via
 * [executeRaw]), every Set-Cookie value collected across followed hops (via
 * the priorResponse chain), and the last Location header.
 * Transport/DNS/socket failures surface as [IOException] so the caller's
 * error taxonomy (Network) is unchanged.
 */
class OkHttpHttpClient(
    private val userAgent: String,
    /** VTOP cookie map loader (name → value), stapled from the caller. */
    private val jarLoader: () -> List<Pair<String, String>>,
    /** Caller-side merge of raw Set-Cookie values; split-by-name semantics. */
    private val jarSaver: (List<String>) -> Unit,
    baseTimeoutSec: Int = 45,
    /** Whole-call ceiling: a WAF tarpit dribbling bytes cannot reset this. */
    baseCallTimeoutSec: Long = 90,
    /** Injectable DNS; defaults to the app-wide lazy DoH-fallback instance. */
    dns: Dns = sharedDnsWithDohFallback,
) : HttpTransport {

    private val base = OkHttpClient.Builder()
        .cookieJar(JarBridge())
        .connectTimeout(baseTimeoutSec.toLong(), TimeUnit.SECONDS)
        .readTimeout(baseTimeoutSec.toLong(), TimeUnit.SECONDS)
        .writeTimeout(baseTimeoutSec.toLong(), TimeUnit.SECONDS)
        .callTimeout(baseCallTimeoutSec, TimeUnit.SECONDS)
        .dns(dns)
        .build()

    /** Every POST stays un-followed so the 302 carries the auth verdict. */
    private val noFollow = base.newBuilder().followRedirects(false).build()

    private inner class JarBridge : CookieJar {
        override fun saveFromResponse(url: okhttp3.HttpUrl, jarCookies: List<Cookie>) {
            // Plain `name=value` strings — the caller's merge is name-keyed
            // and replace-on-set, identical to the WAF cookie it protects.
            jarSaver(jarCookies.map { "${it.name}=${it.value}" })
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> =
            jarLoader().map { (name, value) ->
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .build()
            }
    }

    override fun execute(request: HttpRequest): HttpResponse {
        val wire = run(request)
        return HttpResponse(
            wire.httpCode,
            wire.bodyBytes.decodeLenientUtf8(),
            wire.setCookieHeaders,
            wire.location,
        )
    }

    override fun executeRaw(request: HttpRequest): HttpRawResponse {
        val wire = run(request)
        return HttpRawResponse(
            wire.httpCode,
            wire.bodyBytes,
            wire.setCookieHeaders,
            wire.location,
        )
    }

    /**
     * Cancellable bridge for the section path ([HttpResponse] flavor, matching
     * [execute]): the coroutine's cancellation cancels the underlying OkHttp
     * call (socket released + IO thread unpinned), while the failure contract
     * stays identical to [run] — every transport failure surfaces as
     * [IOException]. A caller cancellation rethrows as CancellationException
     * and is never converted to a result here.
     */
    suspend fun executeSuspend(request: HttpRequest): HttpResponse {
        val wire = runSuspend(request)
        return HttpResponse(
            wire.httpCode,
            wire.bodyBytes.decodeLenientUtf8(),
            wire.setCookieHeaders,
            wire.location,
        )
    }

    /** Diagnostics hook for tests/telemetry: calls queued or executing now. */
    val runningCalls: Int
        get() = base.dispatcher.runningCallsCount()

    /** One HTTP round trip, before any body decoding. */
    private class Wire(
        val httpCode: Int,
        val setCookieHeaders: List<String>,
        val bodyBytes: ByteArray,
        val location: String?,
    )

    private fun run(request: HttpRequest): Wire {
        val response = runCatching { clientFor(request).newCall(buildRequest(request)).execute() }
            .getOrElse { e ->
                throw wrapTransportFailure(e)
            }
        return extractWire(response)
    }

    private suspend fun runSuspend(request: HttpRequest): Wire =
        suspendCancellableCoroutine { continuation ->
            val call = clientFor(request).newCall(buildRequest(request))
            // Spinner-driven back-press / ViewModel job cancel drives this:
            // close the socket instead of riding the read timeout out.
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, resp: Response) {
                    if (continuation.isCancelled) return
                    continuation.resume(extractWire(resp))
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(wrapTransportFailure(e))
                }
            })
        }

    private fun clientFor(request: HttpRequest): OkHttpClient =
        if (request.followRedirects) base else noFollow

    private fun buildRequest(request: HttpRequest): Request {
        val builder = Request.Builder()
            .url(request.url)
            .header("User-Agent", userAgent)
        // Browser-behavior parity with the UA: an identity header Chrome would
        // not send is exactly what a WAF sniffs; keep the request bare above
        // the mandatory plumbings.
        request.referer?.let { builder.header("Referer", it) }
        when (request.method) {
            Method.Get -> builder.get()
            Method.PostForm -> {
                val form = FormBody.Builder()
                for ((name, value) in request.form) {
                    form.add(name, value)
                }
                builder.post(form.build())
            }
        }
        return builder.build()
    }

    /** Map every transport failure onto the caller's IOException taxonomy. */
    private fun wrapTransportFailure(e: Throwable): IOException = IOException(
        "okhttp ${e.javaClass.simpleName}: ${e.message ?: "transport failure"}",
        e,
    )

    /** Response.use = finally close, after all wire data extracted (OkHttp
     * drops connection back to the pool on close). */
    private fun extractWire(response: Response): Wire = response.use { resp ->
        Wire(
            resp.code,
            collectSetCookies(resp),
            resp.body.bytes(),
            lastLocation(resp),
        )
    }

    /** Set-Cookie values from every hop of the follow chain, in hop order. */
    private fun collectSetCookies(final: Response): List<String> {
        val hops = mutableListOf<Response>()
        var cur: Response? = final
        while (cur != null) {
            hops.add(cur)
            cur = cur.priorResponse
        }
        hops.reverse()
        return hops.flatMap { it.headers("Set-Cookie") }
    }

    /** Last Location header value of the final response. */
    private fun lastLocation(resp: Response): String? =
        resp.headers("Location").lastOrNull()?.takeIf { it.isNotBlank() }

    private fun ByteArray.decodeLenientUtf8(): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(ByteBuffer.wrap(this))
            .toString()
}
