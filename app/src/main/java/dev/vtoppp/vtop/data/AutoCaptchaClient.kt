package dev.vtoppp.vtop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

data class LoginResult(
    val authorizedId: String,
    val studentLabel: String,
)

/**
 * Session warm-up state, surfaced to the login UI:
 * the hidden session progresses through request/login/captcha beats.
 */
sealed interface WarmState {
    data object Idle : WarmState
    data class Working(val step: String) : WarmState
    data object CaptchaReady : WarmState
    data class Failed(val message: String) : WarmState
}

/**
 * T19 seam: anything that turns captcha JPEG bytes into candidate text —
 * prod is [AutoCaptchaClient] (captop HTTP solver); tests inject fakes so the
 * login ladder is exercised with zero network.
 */
fun interface CaptchaSolver {
    suspend fun solve(imageJpeg: ByteArray): String?
}

class AutoCaptchaClient(
    /** T19 seam: solver endpoint; MockWebServer URL in tests. */
    private val solveUrl: String = CAPTOP_SOLVE,
    /** T19 seam: transport; defaults to the production captop client. */
    http: OkHttpClient = OkHttpClient.Builder()
        .dns(sharedDnsWithDohFallback)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build(),
) : CaptchaSolver, AutoCloseable {
    private val http = http

    override suspend fun solve(imageJpeg: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                // java.util.Base64 (minSdk 26): identical NO_WRAP bytes, works on JVM.
                put("image", Base64.getEncoder().encodeToString(imageJpeg))
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(solveUrl).post(body).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val obj = JSONObject(resp.body.string())
                obj.optString("text").takeIf { obj.has("text") }
            }
        }.getOrNull()
    }

    override fun close() {
        http.dispatcher.executorService.shutdown()
    }

    private companion object {
        const val CAPTOP_SOLVE = "https://captop-server-latest.onrender.com/solve"
    }
}
