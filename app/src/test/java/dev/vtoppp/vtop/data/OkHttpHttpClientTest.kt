package dev.vtoppp.vtop.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-semantics tests for the only transport, [OkHttpHttpClient], on a local
 * MockWebServer: cookieJar bridging, redirect-hop Set-Cookie collection, the
 * un-followed POST Location, and ordered form bodies. Live-VTOP behavior was
 * additionally verified on-device (Mi A3 + Pixel 6 Pro, login chain + section
 * POST 200) — these tests lock the contract in.
 */
class OkHttpHttpClientTest {

    private class JarCapturer {
        /** The live map, exactly like VtopClient's: saver merges into it. */
        private val jar = mutableMapOf("JSESSIONID" to "abc123")
        val saved = mutableListOf<String>()

        fun transport(
            ua: String = "test-ua",
            baseTimeoutSec: Int = 10,
            baseCallTimeoutSec: Long = 90,
        ): OkHttpHttpClient =
            OkHttpHttpClient(
                userAgent = ua,
                jarLoader = { jar.map { it.key to it.value } },
                jarSaver = { lines ->
                    saved.addAll(lines)
                    for (raw in lines) {
                        val pair = raw.substringBefore(';').trim()
                        jar[pair.substringBefore('=').trim()] = pair.substringAfter('=').trim()
                    }
                },
                baseTimeoutSec = baseTimeoutSec,
                baseCallTimeoutSec = baseCallTimeoutSec,
            )
    }

    @Test
    fun `sendRequest transmits the custom UA, jar cookies and records the Header`() {
        val cap = JarCapturer()
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("hello").build())
            val gen = runBlocking {
                withContext(Dispatchers.IO) {
                    cap.transport().execute(
                        HttpRequest(
                            method = Method.Get,
                            url = server.url("/vtop/login").toString(),
                        ),
                    )
                }
            }
            assertEquals(200, gen.httpCode)
            assertEquals("hello", gen.body)
            val request = server.takeRequest()
            assertEquals("test-ua", request.headers["User-Agent"])
            assertEquals("JSESSIONID=abc123", request.headers["Cookie"])
            assertTrue("saved: ${cap.saved}", cap.saved.isEmpty())
        }
    }

    @Test
    fun `sendRequest follows the 302 redirect chain and re-sends cookies per hop`() {
        val cap = JarCapturer()
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Set-Cookie", "SHIBE=st1; Path=/vtop")
                    .addHeader("Location", "/vtop/next-hop")
                    .code(302)
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Set-Cookie", "JSESSIONID=sess2; Path=/test-cookie")
                    .body("dashboard-page")
                    .build(),
            )
            val resp = runBlocking {
                withContext(Dispatchers.IO) {
                    cap.transport().execute(
                        HttpRequest(
                            method = Method.Get,
                            url = server.url("/vtop/content").toString(),
                            followRedirects = true,
                        ),
                    )
                }
            }
            assertEquals(200, resp.httpCode)
            assertEquals("dashboard-page", resp.body)
            // Both hops' Set-Cookie lines merged in hop order.
            assertEquals(2, resp.setCookieHeaders.size)
            assertTrue(resp.setCookieHeaders[0].contains("SHIBE=st1"))
            assertTrue(resp.setCookieHeaders[1].contains("JSESSIONID=sess2"))
            // Second hop re-sent the first hop's cookie (OkHttp jar bridge).
            server.takeRequest() // consume hop 1
            val second = server.takeRequest()
            val cookieHeader = second.headers["Cookie"]
            // The jar serves both the loader cookie and hop-1's saved one.
            assertTrue("Cookie line: $cookieHeader", cookieHeader?.contains("SHIBE=st1") == true)
            assertTrue("Cookie line: $cookieHeader", cookieHeader?.contains("JSESSIONID=abc123") == true)
            assertNull(resp.location)
        }
    }

    @Test
    fun `unfollowed POST returns the Location with the ordered encoded body`() {
        val cap = JarCapturer()
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", "/vtop/init/page")
                    .build(),
            )
            val resp = runBlocking {
                withContext(Dispatchers.IO) {
                    cap.transport().execute(
                        HttpRequest(
                            method = Method.PostForm,
                            url = server.url("/vtop/login").toString(),
                            form = listOf("username" to "TESTUSER", "captchaStr" to "a b+c"),
                        ),
                    )
                }
            }
            assertEquals(302, resp.httpCode)
            assertEquals("/vtop/init/page", resp.location)
            val request = server.takeRequest()
            val body = request.body!!.utf8()
            // Form-encoded, order preserved. (+ encodes to %2B etc.)
            assertTrue("body: $body", body.startsWith("username=TESTUSER"))
            assertTrue("body: $body", body.endsWith("captchaStr=a+b%2Bc"))
            // Cookies move through the jar bridge only; the per-request map
            // from the curl era is gone.
            assertEquals("JSESSIONID=abc123", request.headers["Cookie"])
        }
    }

    @Test
    fun `rawResponse body bytes stay undecoded`() {
        val cap = JarCapturer()
        MockWebServer().use { server ->
            server.start()
            val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x0A, 0x70)
            server.enqueue(
                MockResponse.Builder()
                    .body(okio.Buffer().write(jpg))
                    .addHeader("Content-Type", "image/jpeg")
                    .build(),
            )
            val raw = runBlocking {
                withContext(Dispatchers.IO) {
                    cap.transport().executeRaw(
                        HttpRequest(
                            method = Method.Get,
                            url = server.url("/vtop/get/new/captcha").toString(),
                        ),
                    )
                }
            }
            assertTrue(raw.bodyBytes.contentEquals(jpg))
        }
    }

    @Test
    fun `jarSaver receives every staged Set-Cookie with the alias`() {
        val cap = JarCapturer()
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Set-Cookie", "cookiesession1=waf-value; Path=/vtop")
                    .body("ok")
                    .build(),
            )
            runBlocking {
                withContext(Dispatchers.IO) {
                    cap.transport().execute(
                        HttpRequest(
                            method = Method.Get,
                            url = server.url("/vtop/login").toString(),
                        ),
                    )
                }
            }
            assertEquals(listOf("cookiesession1=waf-value"), cap.saved)
        }
    }

    @Test
    fun `header and referer are transmitted`() {
        val cap = JarCapturer()
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body("<form id=\"vtopLoginForm\">")
                    .build(),
            )
            runBlocking {
                withContext(Dispatchers.IO) {
                    cap.transport().execute(
                        HttpRequest(
                            method = Method.Get,
                            url = server.url("/vtop/login").toString(),
                            referer = "https://vtop.vitbhopal.ac.in",
                        ),
                    )
                }
            }
            val request = server.takeRequest()
            assertEquals("https://vtop.vitbhopal.ac.in", request.headers["Referer"])
        }
    }

    @Test
    fun `exception surfaces as an IOException`() {
        val cap = JarCapturer()
        val zeroClient = OkHttpHttpClient(
            userAgent = "test-ua",
            jarLoader = { emptyList() },
            jarSaver = {},
            baseTimeoutSec = 1,
        )
        runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    // A closed-loop port will never answer; short timeout.
                    zeroClient.execute(
                        HttpRequest(
                            method = Method.Get,
                            url = "http://127.0.0.1:1/vtop/login",
                        ),
                    )
                    org.junit.Assert.fail("expected an IOException")
                } catch (expected: java.io.IOException) {
                    // correct per contract
                }
            }
        }
    }

    // ------------------------------------------------ T10 timeout + cancel

    @Test
    fun `executeSuspend delivers the response on the happy path`() {
        val cap = JarCapturer()
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Set-Cookie", "cookiesession1=waf2; Path=/vtop")
                    .body("suspended-body")
                    .build(),
            )
            val resp = runBlocking {
                withContext(Dispatchers.IO) {
                    cap.transport().executeSuspend(
                        HttpRequest(
                            method = Method.PostForm,
                            url = server.url("/vtop/content").toString(),
                            form = listOf("verifyMenu" to "true"),
                        ),
                    )
                }
            }
            assertEquals(200, resp.httpCode)
            assertEquals("suspended-body", resp.body)
            assertEquals(1, resp.setCookieHeaders.size)
            assertTrue(resp.setCookieHeaders.single().contains("cookiesession1=waf2"))
            val request = server.takeRequest()
            assertEquals("verifyMenu=true", request.body!!.utf8())
        }
    }

    @Test
    fun `callTimeout cuts off a slow-dribbling body as an IOException`() {
        val cap = JarCapturer()
        MockWebServer().use { server ->
            server.start()
            // ~250ms per byte → a "never finishing" tarpit body; only the
            // whole-call ceiling can cut this, per-read timers never expire
            // while bytes keep trickling.
            server.enqueue(
                MockResponse.Builder()
                    .throttleBody(1, 250, TimeUnit.MILLISECONDS)
                    .body("x".repeat(200))
                    .build(),
            )
            val transport = cap.transport(baseTimeoutSec = 30, baseCallTimeoutSec = 1)
            val elapsed = measureTimeMillis {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        try {
                            transport.execute(
                                HttpRequest(
                                    method = Method.Get,
                                    url = server.url("/vtop/content").toString(),
                                ),
                            )
                            org.junit.Assert.fail("expected IOException from callTimeout")
                        } catch (expected: IOException) {
                            // callTimeout cut the dribble, mapped to IOException
                        }
                    }
                }
            }
            // Only the 1s callTimeout (≈ its wrap slack) should be needed — if
            // the dribble were allowed to run, the body takes ≈50s.
            assertTrue("dribble cut elapsed=${elapsed}ms", elapsed < 5_000)
        }
    }

    @Test
    fun `executeSuspend releases the underlying call when the coroutine is cancelled`() {
        val cap = JarCapturer()
        MockWebServer().use { server ->
            server.start()
            // Body scheduled 30s out: a released call can never see it; a
            // non-cancelling bridge would keep the call alive until the 10s
            // read timeout instead.
            server.enqueue(
                MockResponse.Builder()
                    .body("late")
                    .bodyDelay(30, TimeUnit.SECONDS)
                    .build(),
            )
            val transport = cap.transport()
            runBlocking {
                withContext(Dispatchers.IO) {
                    val job = launch {
                        transport.executeSuspend(
                            HttpRequest(
                                    method = Method.Get,
                                url = server.url("/vtop/content").toString(),
                            ),
                        )
                    }
                    delay(500) // the call is now mid-flight against the held body
                    job.cancel()
                    job.join() // cancelled continuation returned promptly
                }
            }
            // The dispatcher must lose the call promptly — long before the
            // 30s delayed body or the 10s read timeout could end it. Only
            // invokeOnCancellation → call.cancel() manages that.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (transport.runningCalls > 0 && System.nanoTime() < deadline) {
                Thread.sleep(50)
            }
            assertEquals("running calls after cancel", 0, transport.runningCalls)
        }
    }
}
