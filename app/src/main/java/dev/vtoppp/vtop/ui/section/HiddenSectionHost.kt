package dev.vtoppp.vtop.ui.section

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONTokener

/**
 * Hidden WebView used only as a DOM and JavaScript engine for VTOP section fragments; the user never sees it.
 */
@SuppressLint("SetJavaScriptEnabled")
class HiddenSectionHost(
    context: Context,
    cookies: List<Pair<String, String>>,
) : AutoCloseable {

    private companion object {
        private const val BASE_URL = "https://vtop.vitbhopal.ac.in"
        private const val BASE_PAGE_URL = "$BASE_URL/vtop/"
        private const val SCRAPE_INTERVAL_MS = 250L
    }

    val view: WebView = WebView(context)

    var onSnapshot: ((String) -> Unit)? = null

    /**
     * Session cookies posted by in-section XHRs. Copied from the latest
     * [SectionScreen] argument on every recomposition (T17.2) and applied to
     * the system CookieManager by every [open], so a mid-view re-auth with a
     * rotated `JSESSIONID` never leaves the hidden WebView posting the
     * login-page version.
     */
    private var cookies: List<Pair<String, String>> = cookies

    /** Keeps the re-applied cookies current; cheap field copy on the main thread. */
    fun updateCookies(newCookies: List<Pair<String, String>>) {
        cookies = newCookies
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingScrape = false
    private var lastScrapeAt = 0L
    private var generation = 0

    @Volatile
    private var closed = false

    private val scrapeRunnable = Runnable { scrapeNow() }

    init {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }
        view.setBackgroundColor(Color.TRANSPARENT)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.addJavascriptInterface(Bridge(), SectionBridge.INTERFACE_NAME)
    }

    fun open(fragmentHtml: String) {
        if (closed) {
            return
        }
        applySessionCookies()
        handler.removeCallbacks(scrapeRunnable)
        pendingScrape = false
        lastScrapeAt = 0L
        generation += 1
        view.loadDataWithBaseURL(
            BASE_PAGE_URL,
            SectionBridge.hostPage(fragmentHtml),
            "text/html",
            "utf-8",
            null,
        )
    }

    fun trigger(actionIndex: Int, value: String?) {
        if (closed) {
            return
        }
        view.evaluateJavascript(SectionBridge.actionJs(actionIndex, value), null)
        scheduleScrape()
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        handler.removeCallbacksAndMessages(null)
        onSnapshot = null
        try {
            view.removeJavascriptInterface(SectionBridge.INTERFACE_NAME)
        } catch (_: Throwable) {
        }
        try {
            view.stopLoading()
        } catch (_: Throwable) {
        }
        try {
            view.destroy()
        } catch (_: Throwable) {
        }
    }

    private fun applySessionCookies() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            for ((name, value) in cookies) {
                setCookie(BASE_URL, "$name=$value; Path=/vtop; Secure")
            }
            flush()
        }
    }

    private fun scheduleScrape() {
        if (closed || pendingScrape) {
            return
        }
        pendingScrape = true
        val now = SystemClock.uptimeMillis()
        val delay = (lastScrapeAt + SCRAPE_INTERVAL_MS - now).coerceAtLeast(0L)
        handler.postDelayed(scrapeRunnable, delay)
    }

    private fun scrapeNow() {
        pendingScrape = false
        if (closed) {
            return
        }
        lastScrapeAt = SystemClock.uptimeMillis()
        val expectedGeneration = generation
        view.evaluateJavascript(SectionBridge.scrapeJs()) { raw ->
            if (closed || raw == null || expectedGeneration != generation) {
                return@evaluateJavascript
            }
            val html = try {
                JSONTokener(raw).nextValue() as? String
            } catch (_: Throwable) {
                null
            }
            if (html != null) {
                onSnapshot?.invoke(html)
            }
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onDomChanged() {
            if (closed) {
                return
            }
            handler.post { scheduleScrape() }
        }
    }
}
