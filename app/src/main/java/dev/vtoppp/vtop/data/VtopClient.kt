package dev.vtoppp.vtop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

private const val BASE = "https://vtop.vitbhopal.ac.in"

/** Never more than one login POST per 10s (VTOP locks hammering accounts). */
private const val LOGIN_PACE_MS = 10_000L
private const val MAX_LOGIN_ATTEMPTS = 3

/** VTOP captcha answers go stale within a couple of minutes. */
private const val CAPTCHA_MAX_AGE_MS = 60_000L

/** T13.2: wall-time bound on the whole captcha chase = [CAPTCHA_MAX_AGE_MS]; attempts cap. */
private const val CAPTCHA_ROLL_ATTEMPTS = 3

/** [clearSession] keeps these across resets (WAF cookie must survive). */
private val PROTECTED_COOKIES = setOf("cookiesession1")

/** VTOP answers an "Unidentified OS or Browser" error page to other UAs. */
private const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.6099.144 Mobile Safari/537.36"

/**
 * HTTPS client for the Jira VTOP flavor (flow verified on-device & from a real
 * browser session; see docs/vtop-session.md bullets and Gamma §5/§6; transport
 * = Gamma-proven OkHttp client per idea-RE/GAMMA_REA_REPORT.md):
 *
 *   GET  /vtop/login              -> role page with _csrf
 *   POST /vtop/prelogin/setup     -> {_csrf, flag=VTOP} (302)
 *   GET  /vtop/init/page          -> real login form (fresh rotated _csrf)
 *   GET  /vtop/get/new/captcha    -> captcha (embedded data URI or raw JPEG)
 *   POST /vtop/login              -> {username(upper), password, captchaStr,
 *                                    _csrf, gResponse=""} (302 -> content)
 *      rejection = body containing vtopLoginForm / statsl markers
 *   GET  /vtop/content            -> authorizedID (hidden authorizedIDX) +
 *                                    csrfValue + label
 *
 * Sections: form POSTs {verifyMenu, authorizedID, _csrf, nocache} (§6).
 * Session restore: cookie map + GET /vtop/content -> re-derive id/csrf (§5).
 * OkHttp no-frills presentation (custom android UA + bridged cookie jar, per
 * idea-RE/GAMMA_REA_REPORT.md §2/§5) satisfies the same portal WAF; the login
 * POST chain needs the un-followed 302. In-process only: no subprocess HTTP
 * exists anywhere anymore, so OEM resource managers cannot interpose.
 */
class VtopClient(
    /** T19 seam: captcha solver; prod = captop [AutoCaptchaClient]. */
    private val autoCaptcha: CaptchaSolver,
    /** Monotonic clock seam (ms); prod = System.nanoTime (see plan T12.3). */
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val credentialsProvider: () -> Pair<String, String>? = { null },
    /**
     * T8 sibling of CredentialVault: encrypted session snapshot used to seed
     * [prewarm] and written at session establishment; null = feature off.
     */
    private val sessionStore: SessionStore? = null,
    /** T19 seam: transport injection; null = the Gamma-proven OkHttp stack. */
    private val transportOverride: HttpTransport? = null,
) : AutoCloseable {

    private val _warm = MutableStateFlow<WarmState>(WarmState.Idle)
    val warm: StateFlow<WarmState> = _warm.asStateFlow()

    var authorizedId: String? = null
        private set
    var csrf: String? = null
        private set

    /** A live session exists (restored app can rely on it coming back with cookies). */
    val hasSession: Boolean
        get() = csrf != null && authorizedId != null

    /** csrf + solved captcha of the prepared login (fast-login path). */
    private var warmCsrf: String? = null
    private var warmSolution: String? = null
    private var warmSolvedAtMs = 0L

    /** Session credentials, held in memory only (never logged, never persisted here). */
    private var sessionCredentials: Pair<String, String>? = null

    private var lastLoginPostAt = 0L

    private val sessionLock = Mutex()

    /**
     * Cookie store stapled into the transport jar (name → value,
     * replace-on-set). The portal's WAF cookie (`cookiesession1` — issued
     * rarely and never re-issued once the portal has seen a session from this
     * client) lives here too; [clearSession] must never drop it or the login
     * POST gets answered "Invalid LoginId/Password".
     */
    private val cookies = ConcurrentHashMap<String, String>()

    /** Transport: Gamma-proven OkHttp stack (plain client + bridged jar). */
    private val http: HttpTransport = transportOverride ?: OkHttpHttpClient(
        userAgent = BROWSER_UA,
        jarLoader = { cookiePairs() },
        jarSaver = { mergeCookies(it) },
    )

    /** Session cookies for the hidden section host's asset/XHR requests. */
    fun cookiePairs(): List<Pair<String, String>> = cookies.entries.map { it.key to it.value }

    /** Drops every session cookie except the [PROTECTED_COOKIES] allowlist. */
    private fun clearSession() {
        for (name in cookies.keys) {
            if (name !in PROTECTED_COOKIES) cookies.remove(name)
        }
    }

    private fun urlOf(path: String): String = when {
        path.startsWith("http") -> path
        path.startsWith("/") -> "$BASE$path"
        else -> "$BASE/vtop/$path"
    }

    /** GET with redirect-following (the dashboard chase). */
    private fun get(path: String, referer: String? = null): Pair<Int, String> {
        val r = http.execute(
            HttpRequest(
                method = Method.Get,
                url = urlOf(path),
                referer = referer,
                followRedirects = true,
            ),
        )
        mergeCookies(r.setCookieHeaders)
        return r.httpCode to r.body
    }

    /** POST without redirect-following; returns (code, body). */
    private fun post(
        path: String,
        form: Map<String, String>,
        referer: String? = null,
    ): Pair<Int, String> {
        val r = http.execute(
            HttpRequest(
                method = Method.PostForm,
                url = urlOf(path),
                referer = referer,
                form = form.map { (k, v) -> k to v },
            ),
        )
        mergeCookies(r.setCookieHeaders)
        return r.httpCode to r.body
    }

    /** Like [post] but also returns the Location response header if present. */
    private fun postLoc(
        path: String,
        form: Map<String, String>,
        referer: String? = null,
    ): Triple<Int, String, String?> {
        val r = http.execute(
            HttpRequest(
                method = Method.PostForm,
                url = urlOf(path),
                referer = referer,
                form = form.map { (k, v) -> k to v },
            ),
        )
        mergeCookies(r.setCookieHeaders)
        return Triple(r.httpCode, r.body, r.location)
    }

    /**
     * Section-path POST on the cancellable transport bridge: instead of the
     * blocking [post], a cancelled coroutine (spinner back-press, HomeViewModel
     * job cancel) cancels the underlying OkHttp call, releasing the IO thread
     * and socket immediately. Cookie-merge and return shape are identical to
     * [post]; transport failures stay IOException-mapped by the bridge.
     */
    private suspend fun postAwaiting(
        path: String,
        form: Map<String, String>,
        referer: String? = null,
    ): Pair<Int, String> {
        val request = HttpRequest(
            method = Method.PostForm,
            url = urlOf(path),
            referer = referer,
            form = form.map { (k, v) -> k to v },
        )
        val r = when (http) {
            is OkHttpHttpClient -> http.executeSuspend(request)
            else -> http.execute(request)
        }
        mergeCookies(r.setCookieHeaders)
        return r.httpCode to r.body
    }

    /** Matches a `Max-Age=0` attribute (server-side deletion directive). */
    private val MAX_AGE_ZERO = Regex(
        """(?:^|;)\s*max-age\s*=\s*0(?:\s*;|$)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Merges raw `Set-Cookie` values (already case-insensitively extracted)
     * into the cookie map: `name=value` before the first `;` (no-space
     * splits included — `;Expires=...`), value = remainder after the first
     * `=`. A `Max-Age=0` directive or an empty value deletes the name
     * instead of storing it.
     */
    private fun mergeCookies(setCookieHeaders: List<String>) {
        for (raw in setCookieHeaders) {
            val pair = raw.substringBefore(';').trim()
            val name = pair.substringBefore('=').trim()
            if (name.isEmpty()) continue
            val value = if ('=' in pair) pair.substringAfter('=').trim() else ""
            if (value.isEmpty() || MAX_AGE_ZERO.containsMatchIn(raw)) {
                cookies.remove(name)
            } else {
                cookies[name] = value
            }
        }
    }

    private fun csrfOf(html: String): String? =
        Regex("""name="_csrf" value=["']([^"']+)["']""").find(html)?.groupValues?.get(1)

    /** Captcha embedded as data URI inside the login page HTML (Jira flavor). */
    private val DATA_URI = Regex("""data:image/jpeg;base64,([A-Za-z0-9+/=]+)""")

    // swal({title: "...", text: "..."}) variants; messages may be URL-encoded.
    private val SWAL_TEXT = Regex(
        """swal\s*\(\s*\{[\s\S]{0,500}?\b(?:title|text|html)\s*:\s*["']([^"']{3,200})["']""",
        RegexOption.IGNORE_CASE,
    )
    private val SWAL_SIMPLE = Regex("""swal\s*\(\s*["']([^"']{3,200})["']""")

    private val credentialPhrases = listOf(
        "invalid credentials", "invalid credential", "incorrect credentials",
        "invalid userid", "invalid user id", "invalid username", "invalid login id",
        "incorrect userid", "incorrect user id", "incorrect username",
        "wrong userid", "wrong user id", "wrong username",
        "invalid password", "incorrect password", "wrong password",
        "authentication failed", "login failed", "account is locked",
        "user id or password", "userid or password",
    )

    // ---------------------------------------------------------------- prewarm

    /**
     * Lets the login UI surface beats (retry countdowns) without touching the session.
     */
    fun announceWarm(step: String) {
        _warm.value = WarmState.Working(step)
    }

    /**
     * T8.2: hydrates cookies/csrf/authorizedId from the encrypted session
     * snapshot before the first request. A stale (>N h) or unreadable entry
     * is treated as absent and the normal login chain runs; a seeded-but-dead
     * session is still validated by [prepareCaptcha]'s first content GET.
     */
    private fun seedFromPersistedSession() {
        val store = sessionStore ?: return
        val snap = store.loadFresh() ?: run {
            AppLog.d("prewarm: no fresh session snapshot")
            return
        }
        cookies.putAll(snap.cookies)
        if (snap.csrf != null && snap.csrf.isNotBlank()) csrf = snap.csrf
        authorizedId = snap.authorizedId
        AppLog.d("prewarm: seeded persisted session snapshot")
    }

    /**
     * Prepares the session as early as possible: live-session restore first,
     * else csrf + a captcha solved offscreen so signing in is a single POST.
     */
    suspend fun prewarm() {
        sessionLock.withLock {
            // Never clobber a live session; hasSession can flip while we wait on the lock.
            if (hasSession) {
                _warm.value = WarmState.CaptchaReady
                return
            }
            seedFromPersistedSession()
            withContext(Dispatchers.IO) {
                try {
                    prepareCaptcha()
                } catch (e: Exception) {
                    AppLog.d("prewarm failed: ${e.javaClass.simpleName} ${e.message}")
                    _warm.value = WarmState.Failed(warmFailureText(e))
                }
            }
        }
    }

    private suspend fun prepareCaptcha() {
        _warm.value = WarmState.Working("Connecting to VTOP…")
        val splash = get("content", referer = BASE).second
        if (!looksLikeLoginPage(splash)) {
            // Maybe a live session from persisted cookies: restore it.
            val restored = deriveFromDashboard(splash)
            if (restored != null) {
                _warm.value = WarmState.CaptchaReady
                AppLog.d("prewarm: session restored")
                return
            }
            clearSession()
            authorizedId = null
            csrf = null
        }
        // Two-phase login (Jira flavor): role setup first, then the real form.
        val roleCsrf = csrfOf(get("login", referer = BASE).second)
            ?: fail("no csrf on login page")
        _warm.value = WarmState.Working("Opening login form…")
        val (phase1Code, _) = post(
            "prelogin/setup",
            mapOf("_csrf" to roleCsrf, "flag" to "VTOP"),
            referer = "$BASE/vtop/login",
        )
        if (phase1Code !in 200..299) AppLog.d("phase1 prelogin -> $phase1Code")
        val formPage = get("init/page", referer = "$BASE/vtop/prelogin/setup").second
        warmCsrf = csrfOf(formPage) ?: fail("no csrf after prelogin")
        _warm.value = WarmState.Working("Waiting for captcha…")
        warmSolution = null
        val bytes = rollCaptcha()
        _warm.value = WarmState.Working("Solving captcha…")
        val solution = autoCaptcha.solve(bytes)?.trim()?.uppercase() ?: fail("solver unavailable")
        warmSolution = solution
        warmSolvedAtMs = nowMs()
        _warm.value = WarmState.CaptchaReady
        AppLog.dump("c-${solution.replace(Regex("[^A-Za-z0-9]"), "")}", java.util.Base64.getEncoder().encodeToString(bytes))
        AppLog.d("prewarm: captcha solved")
    }

    private fun warmFailureText(e: Exception): String = when (e) {
        VtopError.RateLimited -> "VTOP is not offering a captcha right now"
        VtopError.Network -> "No connection"
        is VtopError -> e.message ?: "VTOP error"
        else -> "Could not prepare the session"
    }

    /**
     * Fresh captcha bytes every call. The Jira VTOP flavor serves the login
     * page HTML with the captcha embedded as a data:image/jpeg;base64 URI;
     * some installs return the raw JPEG. Both handled.
     *
     * T13.2: the whole chase is wall-time bounded ([CAPTCHA_MAX_AGE_MS]) and
     * capped at [CAPTCHA_ROLL_ATTEMPTS] attempts, so a tarpit cannot lock the
     * sessionLock for minutes.
     */
    private suspend fun rollCaptcha(): ByteArray {
        val result = withTimeoutOrNull(CAPTCHA_MAX_AGE_MS) {
            for (attempt in 1..CAPTCHA_ROLL_ATTEMPTS) {
                val resp = http.executeRaw(
                    HttpRequest(
                        method = Method.Get,
                        url = urlOf("get/new/captcha"),
                        referer = "$BASE/vtop/login",
                        followRedirects = true,
                    ),
                )
                mergeCookies(resp.setCookieHeaders)
                val body = resp.bodyBytes
                val found = if (body.size > 500 && body[0] == 0xFF.toByte() && body[1] == 0xD8.toByte()) {
                    body
                } else {
                    DATA_URI.find(String(body, Charsets.UTF_8))?.groupValues?.get(1)
                        // MIME decoder: Android-DEFAULT-level tolerance for
                        // whitespace/newlines in the data URI payload.
                        ?.let { java.util.Base64.getMimeDecoder().decode(it) }
                }
                if (found != null && found.size > 500) return@withTimeoutOrNull found
                AppLog.d("prewarm: captcha roll $attempt empty")
                delay(1_500)
            }
            null
        }
        if (result != null) return result
        fail("captcha endpoint did not serve an image")
    }

    // ------------------------------------------------------------------ login

    suspend fun login(username: String, password: String): Result<LoginResult> =
        runCatchingLogin {
            sessionLock.withLock {
                _warm.value = WarmState.Working("Signing in…")
                try {
                    val res = authenticate(username.uppercase(), password)
                    sessionCredentials = username to password
                    res
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // T13.4: a dead manual login must not leave warm wedged in
                    // Working, or the prewarm waiter would wait forever.
                    clearWarm()
                    throw e
                }
            }
        }

    /** Paced captcha chain; returns LoginResult or throws VtopError. */
    private suspend fun authenticate(username: String, password: String): LoginResult {
        // T15.3: an already-established live session (restored from the
        // encrypted snapshot and validated by prepareCaptcha's content GET)
        // IS the requested login outcome — re-running the POST with a missing
        // warmCsrf can only throw "no csrf" and discard a perfectly good
        // session (the surprise-second-biometric-prompt path).
        if (hasSession) {
            val authId = authorizedId ?: fail("no session id")
            AppLog.d("login: live session already established ($authId); POST skipped")
            return LoginResult(authId, "$authId (STUDENT)")
        }
        var csrfNow = warmCsrf
        if (csrfNow == null) {
            prepareCaptcha()
            csrfNow = warmCsrf ?: fail("no csrf")
        }
        for (attempt in 1..MAX_LOGIN_ATTEMPTS) {
            val solution = currentSolution()
            paceLoginPost()
            lastLoginPostAt = nowMs()
            // POSTs do not follow redirects: the POST itself answers with the
            // un-followed status. A 3xx is only a real success when the Location
            // is NOT the server's failure redirect (observed
            // `location: /vtop/login/error` on refused logins).
            val (code, resultPage, location) = postLoc(
                "login",
                mapOf(
                    "username" to username,
                    "password" to password,
                    "captchaStr" to solution,
                    "_csrf" to (csrfNow ?: fail("no csrf")),
                    "gResponse" to "",
                ),
                referer = "$BASE/vtop/login",
            )
        if (code in 300..399 && location != null && location.contains("/vtop/login/error")) {
            // Server bounced to its failure redirect; the error page itself
            // carries the real rejection text ("Invalid LoginId/Password",
            // "Invalid Captcha"), so classify that instead of guessing from
            // the empty body. T13.1: fetched ONCE, shared by both classifiers.
            val rejectPage = get("login/error", referer = "$BASE/vtop/login").second
            AppLog.dump("rejection-$attempt", rejectPage)
            val reason = RejectionClassifier.messages(rejectPage).firstOrNull()
                ?: location.substringAfterLast('/')
            AppLog.d("login: attempt $attempt bounced (VTOP said: $reason)")
            classifyCredentialRejection(rejectPage)
            if (attempt == MAX_LOGIN_ATTEMPTS) throw VtopError.CaptchaRejected
            prepareCaptcha()
            csrfNow = warmCsrf ?: fail("no csrf")
            delay(2_000)
            continue
        }
            if (code in 300..399) {
                // Real redirect success: derive straight from the live session;
                // a re-prelogin would invalidate it, so finishLogin no longer
                // replays phase 1.
                AppLog.d("login: success (code $code, -> $location)")
                val res = finishLogin("")
                clearWarm()
                return res
            }
            if (!isReject(resultPage)) {
                if (resultPage.contains("Unidentified OS or Browser")) {
                    fail("VTOP refused this client (UA block)")
                }
                AppLog.d("login: success")
                val res = finishLogin(resultPage)
                clearWarm()
                return res
            }
            AppLog.d("login: attempt $attempt rejected (${resultPage.length}b) ${resultPage.take(160)}")
            AppLog.dump("rejection-$attempt", resultPage)
            classifyCredentialRejection(resultPage)
            if (attempt == MAX_LOGIN_ATTEMPTS) throw VtopError.CaptchaRejected
            // The failed POST rotates the session; redo phase-1 so the next
            // attempt goes out with a fresh csrf + captcha.
            prepareCaptcha()
            csrfNow = warmCsrf ?: fail("no csrf")
            delay(2_000)
        }
        throw VtopError.CaptchaRejected
    }

    private suspend fun currentSolution(): String {
        val solution = warmSolution
        val age = nowMs() - warmSolvedAtMs
        if (solution != null && age <= CAPTCHA_MAX_AGE_MS) return solution
        if (solution != null) {
            AppLog.d("login: captcha stale (${age / 1000}s), re-solving")
        }
        reSolve()
        return warmSolution ?: throw VtopError.Network
    }

    /** Fresh captcha + answer for the current (phase-1 resolved) session. */
    private suspend fun reSolve() {
        warmSolution = null
        _warm.value = WarmState.Working("Waiting for captcha…")
        val bytes = rollCaptcha()
        _warm.value = WarmState.Working("Solving captcha…")
        warmSolution = autoCaptcha.solve(bytes)?.trim()?.uppercase() ?: fail("solver unavailable")
        warmSolvedAtMs = nowMs()
        _warm.value = WarmState.CaptchaReady
    }

    /** Never more than one login POST per [LOGIN_PACE_MS]. */
    private suspend fun paceLoginPost() {
        val wait = paceDelayMs(lastLoginPostAt, nowMs())
        if (wait > 0) {
            AppLog.d("login: pacing next POST ${wait}ms")
            delay(wait)
        }
    }

    private fun clearWarm() {
        warmCsrf = null
        warmSolution = null
        _warm.value = WarmState.Idle
    }

    /** Gamma's rejection test (§5): form/Invalid/captcha markers in the body. */
    private fun isReject(page: String): Boolean =
        page.contains("vtopLoginForm") ||
            page.contains("Invalid") ||
            page.contains("captchaStr")

    /**
     * Span/swal rejection classifier; a credential phrase aborts immediately so
     * wrong passwords never burn the captcha retry budget.
     */
    private fun classifyCredentialRejection(page: String) {
        RejectionClassifier.classify(page)?.let { throw it }
    }

    /** Post-login landing: the POST 302s into the session; derive from content. */
    private suspend fun finishLogin(successPage: String): LoginResult {
        // The POST redirect chain (302 -> main/page -> open -> content) may
        // already end on the dashboard.
        deriveFromDashboard(successPage)?.let { return it }
        // Followed init/page chase lands on the dashboard once post-login is
        // committed; one retry absorbs the session-commit race. Content is
        // the direct fallback. No prelogin replay: the phase-1 restart after
        // a successful login invalidates the session (observed: bounce +
        // fresh login form afterwards).
        for (tryIndex in 1..2) {
            val dash = get("init/page", referer = "$BASE/vtop/login").second
            deriveFromDashboard(dash)?.let { return it }
            if (tryIndex == 1) delay(2_000)
        }
        val direct = get("content", referer = "$BASE/vtop/init/page").second
        return deriveFromDashboard(direct) ?: fail("dashboard missing authorizedID")
    }

    /** authorizedID + csrfValue + label from dashboard HTML; establishes hasSession. */
    private fun deriveFromDashboard(html: String): LoginResult? {
        if (!html.contains("page-wrapper")) return null
        val authId = Regex("""name="authorizedID"[\s\S]{0,120}?value="([^"]+)"""")
            .find(html)?.groupValues?.get(1)
            ?: Regex("""id="authorizedIDX"[\s\S]{0,120}?value="([^"]+)"""")
                .find(html)?.groupValues?.get(1)
            ?: return null
        authorizedId = authId
        csrf = Regex("""var csrfValue = "([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: csrfOf(html) ?: csrf
        val label = Regex("""([A-Z0-9]{6,20})\s*\((?:STUDENT|EMPLOYEE)\)""").find(html)?.value
            ?: "$authId (STUDENT)"
        persistSession()
        return LoginResult(authId, label.trim())
    }

    /**
     * T8.1: fires at session establishment — login success and prewarm restore
     * both land here — so the encrypted snapshot always mirrors the live session.
     */
    private fun persistSession() {
        val store = sessionStore ?: return
        val authId = authorizedId ?: return
        val c = csrf ?: return
        store.save(cookiePairs().toMap(), c, authId)
    }

    // ---------------------------------------------------------------- section

    /**
     * Fetches a section page; on session expiry re-authenticates once with the
     * session's stored credentials and retries.
     */
    suspend fun fetchSection(endpoint: String): Result<String> = withContext(Dispatchers.IO) {
        runCatchingLogin {
            if (csrf == null) reloginAfterExpiry()
            try {
                rawSection(endpoint)
            } catch (e: VtopError.SessionExpired) {
                reloginAfterExpiry()
                AppLog.d("section: re-authenticated, retrying $endpoint")
                rawSection(endpoint)
            }
        }
    }

    private fun looksLikeLoginPage(body: String): Boolean =
        !body.contains("page-wrapper") &&
            (body.contains("stdForm") || body.contains("empForm") || body.contains("parentForm"))

    private suspend fun rawSection(endpoint: String): String {
        val c = csrf ?: fail("no session")
        val form = mapOf(
            "verifyMenu" to "true",
            "authorizedID" to (authorizedId ?: ""),
            "_csrf" to c,
            "nocache" to System.currentTimeMillis().toString(),
        )
        val (code, body) = postAwaiting(endpoint, form, referer = "$BASE/vtop/content")
        if (code == 301 || code == 302 || code == 303 || code == 307) throw VtopError.SessionExpired
        if (code == 403 || code == 429) throw VtopError.Blocked("WAF_BLOCKED($code) on $endpoint")
        check(code in 200..299) { "section $endpoint -> $code" }
        AppLog.d("section: $endpoint -> $code ${body.length}B")
        if (looksLikeLoginPage(body)) throw VtopError.SessionExpired
        return body
    }

    /** Transparent auto re-login; never surfaces wrong-credentials as such mid-app. */
    private suspend fun reloginAfterExpiry() {
        val creds = sessionCredentials
            ?: credentialsProvider()?.also { sessionCredentials = it }
            ?: throw VtopError.SessionExpired
        AppLog.d("session: expired, re-authenticating")
        sessionLock.withLock {
            // Another in-flight action may have already refreshed the session.
            if (hasSession) return
            try {
                clearSession()
                authorizedId = null
                csrf = null
                warmCsrf = null
                warmSolution = null
                _warm.value = WarmState.Working("Signing in…")
                authenticate(creds.first.uppercase(), creds.second)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("session: re-auth failed: ${e.javaClass.simpleName} ${e.message}")
                throw reauthFailure(e)
            }
        }
        AppLog.d("session: re-authenticated successfully")
    }

    override fun close() {
        sessionCredentials = null
    }
}

private fun fail(msg: String): Nothing {
    AppLog.d("fail: $msg")
    throw IllegalStateException(msg)
}

/** T11: a VtopError passes through unchanged; only ambiguous failures map to SessionExpired. */
internal fun reauthFailure(e: Exception): VtopError = e as? VtopError ?: VtopError.SessionExpired

/** Monotonic pacing window: ms to wait before the next login POST (0 = go now). */
internal fun paceDelayMs(lastLoginPostAt: Long, now: Long): Long =
    if (lastLoginPostAt == 0L) 0 else (lastLoginPostAt + LOGIN_PACE_MS - now).coerceAtLeast(0)

private inline fun <T> runCatchingLogin(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: VtopError) {
    AppLog.d("login failed: ${e::class.simpleName} ${e.message}")
    Result.failure(e)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: java.io.IOException) {
    AppLog.d("io fail: ${e.javaClass.simpleName} ${e.message}")
    Result.failure(VtopError.Network)
} catch (e: Exception) {
    AppLog.d("login err: ${e.javaClass.simpleName} ${e.message}")
    Result.failure(VtopError.Unknown(e.message ?: "unknown"))
}
