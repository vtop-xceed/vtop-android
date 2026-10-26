package dev.vtoppp.vtop.ui.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vtoppp.vtop.data.AppLog
import dev.vtoppp.vtop.data.AutoCaptchaClient
import dev.vtoppp.vtop.data.CredentialVault
import dev.vtoppp.vtop.data.SessionStore
import dev.vtoppp.vtop.data.VtopClient
import dev.vtoppp.vtop.data.VtopError
import dev.vtoppp.vtop.data.WarmState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data class Loading(val line: String) : LoginUiState
    data class Success(val authorizedId: String) : LoginUiState
    data class Failure(val error: VtopError, val detail: String?) : LoginUiState
}

/**
 * Credentials pending a biometric save (T16). Passwords never reach
 * SavedStateHandle — only the remembered-flag and username survive death.
 */
enum class SavedKeys { LAST_REMEMBER, LAST_USERNAME, PENDING_SAVE_USER }

class LoginViewModel(
    private val vault: CredentialVault,
    private val autoCaptcha: AutoCaptchaClient,
    private val sessionStore: SessionStore? = null,
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    lateinit var currentClient: VtopClient
        private set

    private val client: VtopClient by lazy {
        VtopClient(
            autoCaptcha,
            credentialsProvider = {
                // Only non-gated vaults can feed silent re-auth.
                vault.username?.takeIf { !vault.isGated }?.let { user ->
                    vault.password?.let { user to it }
                }
            },
            sessionStore = sessionStore,
        ).also { currentClient = it }
    }

    /** Session warm-up status (captcha pre-solved at launch). */
    val warm: StateFlow<WarmState> = client.warm

    private var lastCredentials: Pair<String, String>? = null
    private var lastRemember = false

    val haveSavedCredentials: Boolean get() = vault.hasCredentials
    val savedUsername: String? get() = vault.username

    /**
     * Credentials from the most recent successful manual login with
     * "remember me" checked; consumed by the UI to run the secure-save prompt.
     */
    var pendingRemember: Pair<String, String>? = null
        private set

    fun consumePendingRemember(): Pair<String, String>? {
        val pending = pendingRemember
        pendingRemember = null
        savedStateHandle?.remove<String>(SavedKeys.PENDING_SAVE_USER.name)
        return pending
    }

    /** True when the client still holds a live VTOP session. */
    fun hasLiveSession(): Boolean = client.hasSession

    /** T13.4 lifecycle gate: the warm loop only runs while the app is foregrounded. */
    private val foregrounded = MutableStateFlow(true)

    /** Wired from the Activity's ON_START/ON_STOP in MainActivity. */
    fun setForeground(value: Boolean) {
        foregrounded.value = value
    }

    /** T13.3: warm failures allowed per foreground resume before the loop goes quiet. */
    private companion object {
        const val MAX_WARM_FAILURES_PER_FOREGROUND = 5
    }

    private var lastUsername: String? = null

    init {
        // T16.4: a process death between "login succeeded (remember me)" and
        // "save completed" recovers here — the remembered FACT (username only,
        // never the password) re-stages the pending save; a password-less pair
        // makes saveSecurely skip with an explicit notice instead of storing
        // an empty secret. The boolean flag loads back too, so a "Try again"
        // after death keeps the remember-semantics.
        savedStateHandle?.get<String>(SavedKeys.PENDING_SAVE_USER.name)?.let { user ->
            pendingRemember = user to ""
            savedStateHandle.remove<String>(SavedKeys.PENDING_SAVE_USER.name)
            AppLog.d("login: pending remember-save survived process death")
        }
        savedStateHandle?.get<Boolean>(SavedKeys.LAST_REMEMBER.name)?.let { lastRemember = it }
        savedStateHandle?.get<String>(SavedKeys.LAST_USERNAME.name)?.let { lastUsername = it }
    }

    init {
        // Warm the session at launch, bounded (T13): at most
        // [MAX_WARM_FAILURES_PER_FOREGROUND] warm failures per foreground
        // resume; no prewarm while a manual login is Loading (wait for a
        // terminal state instead of delay(500)+prewarm fighting it); a
        // Working warm chain is awaited rather than abandoned; the whole
        // loop pauses while the app is backgrounded. Pacing (10s) stays
        // inside the client.
        viewModelScope.launch(Dispatchers.IO) {
            var failures = 0
            while (true) {
                if (!foregrounded.value) {
                    foregrounded.first { it }
                    failures = 0 // fresh failure budget on every foreground resume
                }
                val warmNow = warm.value
                if (warmNow is WarmState.Working || _state.value is LoginUiState.Loading) {
                    // Wait for the in-flight chain/manual login to reach a
                    // terminal state; only success/failure resumes the loop.
                    combine(warm, _state) { w, s ->
                        w !is WarmState.Working && s !is LoginUiState.Loading
                    }.first { it }
                    continue
                }
                when {
                    client.hasSession || warmNow == WarmState.CaptchaReady -> return@launch
                    warmNow is WarmState.Failed -> {
                        failures++
                        if (failures > MAX_WARM_FAILURES_PER_FOREGROUND) return@launch
                        val waitMs = backoffMs(failures)
                        client.announceWarm("Retrying VTOP connection in ${waitMs / 1000}s…")
                        delay(waitMs)
                    }
                    else -> client.prewarm()
                }
            }
        }
    }

    /** 4s for the first three retries, then 8, 16, capped at 30s. */
    private fun backoffMs(retry: Int): Long = when {
        retry <= 3 -> 4_000L
        retry == 4 -> 8_000L
        retry == 5 -> 16_000L
        else -> 30_000L
    }

    fun submit(username: String, password: String, rememberCredentials: Boolean) {
        if (_state.value is LoginUiState.Loading) return
        lastCredentials = username to password
        lastRemember = rememberCredentials
        lastUsername = username
        savedStateHandle?.set(SavedKeys.LAST_REMEMBER.name, rememberCredentials)
        savedStateHandle?.set(SavedKeys.LAST_USERNAME.name, username)
        _state.value = LoginUiState.Loading("Connecting to VTOP…")
        viewModelScope.launch(Dispatchers.IO) {
            val clientNow = client
            val result = clientNow.login(username, password)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { res ->
                        // T16.2: the save is staged whenever "remember me" is
                        // checked AND the submitted pair differs from what is
                        // stored (same passwords means nothing to update).
                        val unchanged = vault.username == username && vault.password == password
                        if (rememberCredentials && !unchanged) {
                            pendingRemember = username to password
                            // T16.4: only the username survives death —
                            // passwords are excluded from SavedStateHandle.
                            savedStateHandle?.set(SavedKeys.PENDING_SAVE_USER.name, username)
                        }
                        _state.value = LoginUiState.Success(res.authorizedId)
                    },
                    onFailure = { e ->
                        val err = e as? VtopError
                            ?: if (e is java.io.IOException) {
                                // network/DNS failures surface as IOException; the
                                // UI branches on VtopError.Network to stay on cached
                                // content.
                                VtopError.Network
                            } else VtopError.Unknown(e.message ?: "unknown")
                        val detail = detailOf(err) ?: e.message
                        val dbg = " [${e.javaClass.simpleName}]" +
                            (e.cause?.let { " cause=${it.javaClass.simpleName}: ${it.message ?: ""}" } ?: "")
                        _state.value = LoginUiState.Failure(err, (detail ?: "") + dbg)
                    },
                )
            }
        }
    }

    /** Re-runs the last submitted credentials (ErrorCard "Try again"). */
    fun retry() {
        val creds = lastCredentials ?: return
        submit(creds.first, creds.second, lastRemember)
    }

    fun reset() {
        _state.value = LoginUiState.Idle
    }

    private fun detailOf(err: VtopError): String? = when (err) {
        is VtopError.Unknown -> err.message
        VtopError.Network -> "Check your internet connection"
        VtopError.CaptchaRejected -> "Captcha kept failing after 3 tries"
        VtopError.RateLimited -> "VTOP is not offering a captcha right now — wait a bit"
        is VtopError.CredentialsRejected -> err.message
        else -> null
    }

    override fun onCleared() {
        client.close()
    }
}
