package dev.vtoppp.vtop

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vtoppp.vtop.data.AppLog
import dev.vtoppp.vtop.data.AutoCaptchaClient
import dev.vtoppp.vtop.data.AutoUpdater
import dev.vtoppp.vtop.data.CredentialVault
import dev.vtoppp.vtop.data.SessionCache
import dev.vtoppp.vtop.data.SessionStore
import dev.vtoppp.vtop.data.VtopError
import dev.vtoppp.vtop.data.WarmState
import dev.vtoppp.vtop.data.pillOf
import dev.vtoppp.vtop.ui.home.AutoUpdateDialog
import dev.vtoppp.vtop.ui.home.HomeScreen
import dev.vtoppp.vtop.ui.home.HomeViewModel
import dev.vtoppp.vtop.ui.login.LoginScreen
import dev.vtoppp.vtop.ui.login.LoginUiState
import dev.vtoppp.vtop.ui.login.LoginViewModel
import dev.vtoppp.vtop.ui.theme.VtopTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

/** FragmentActivity so BiometricPrompt works while the UI stays Compose. */
class MainActivity : FragmentActivity() {

    private val autoCaptcha by lazy { AutoCaptchaClient() }
    private val vault by lazy { CredentialVault(this) }

    /** T8: encrypted, credential-free session snapshot store. */
    private val sessionStore by lazy { SessionStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        SessionCache.init(this)
        AppLog.d("app: onCreate")
        setContent {
            VtopTheme {
                Root(vault, autoCaptcha, sessionStore)
            }
        }
    }
}

/** Boot phase: straight into the cached app (saved profile) or the login gate. */
enum class BootPhase { Auto, Login }

@Composable
private fun Root(vault: CredentialVault, autoCaptcha: AutoCaptchaClient, sessionStore: SessionStore) {
    val activity = LocalContext.current
    val scope = rememberCoroutineScope()

    val loginVm: LoginViewModel = viewModel(factory = LoginVmFactory(vault, autoCaptcha, sessionStore))
    var authorizedId by rememberSaveable { mutableStateOf<String?>(null) }
    var phase by rememberSaveable {
        mutableStateOf(if (vault.hasCredentials) BootPhase.Auto else BootPhase.Login)
    }

    val loginState by loginVm.state.collectAsStateWithLifecycle()
    val warm by loginVm.warm.collectAsStateWithLifecycle()
    // T15 fix: the warm give-up must be visible to composition even when no
    // NEW LoginUiState arrives (a stale Failure already occupies _state).
    val warmGaveUp by loginVm.warmGaveUp.collectAsStateWithLifecycle()

    // T5/T6: the vault surfaces one notice (bootstrap reset, lost credentials,
    // transient unlock trouble); shown here, on the login gate.
    var vaultBanner by remember { mutableStateOf<String?>(null) }

    // Self-update gate: silent startup ping (opt-out remembered), then the
    // popup drives download with a loading animation and the final Update tap.
    val updater = remember { AutoUpdater(activity.applicationContext) }
    LaunchedEffect(Unit) { updater.autoCheck() }

    // T16.1: login-success handling must survive the Auto/Home branch flip, so
    // it lives at Root level and the biometric save runs in the activity scope
    // — a composition switch to Home can no longer cancel the prompt midway
    // (the previous Login-branch effect died with the branch, never saving).
    LaunchedEffect(Unit) {
        loginVm.state.collect { s ->
            if (s is LoginUiState.Success) {
                authorizedId = s.authorizedId
                loginVm.consumePendingRemember()?.let { (user, pass) ->
                    scope.launch {
                        saveSecurely(activity as? FragmentActivity, vault, user, pass) { vaultBanner = it }
                    }
                }
            }
        }
    }

    // T13.4: gate the warm retry loop on foreground visibility so it never
    // burns requests while backgrounded.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> loginVm.setForeground(true)
                Lifecycle.Event.ON_STOP -> loginVm.setForeground(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto sign-in effects (only valid in the Auto phase).
    if (phase == BootPhase.Auto) {
        // T15.2: keyed on the null-authorizedId condition — after a success
        // (rememberSaveable persists it) this effect cannot re-run on a
        // configuration change or process death, so no surprise second
        // biometric prompt discards a live session.
        if (authorizedId == null) {
            // Held only for a warm-recovery re-submit; the same plaintext copy
            // is already present in memory from the first auto attempt.
            val autoPass = remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                val fa = activity as? FragmentActivity
                var pass: String? = null
                if (fa != null) {
                    pass = when {
                        !vault.isGated -> vault.password
                        else -> unlockVault(fa, vault)
                    }
                    vaultBanner = vault.stateNotice
                }
                autoPass.value = pass
                val user = vault.username
                if (pass != null && user != null) {
                    loginVm.submit(user, pass, false)
                } else {
                    phase = BootPhase.Login
                }
            }
            // T15 fix: the first auto try can bounce on a dead network before
            // the warm loop connects; once warm recovers into a fresh CaptchaReady
            // nothing re-fired login() (observed on 2026-10-27 — the splash sat
            // idle with a Failure(Network) and a solved captcha). Re-submit once
            // per recovery; the ladder's own pacing and attempt cap bound retries.
            LaunchedEffect(warm, loginState) {
                val w = warm
                if (w is WarmState.CaptchaReady && !loginVm.hasLiveSession()) {
                    val user = vault.username
                    val pass = autoPass.value
                    if (user != null && pass != null &&
                        (loginState as? LoginUiState.Failure)?.error is VtopError.Network
                    ) {
                        loginVm.submit(user, pass, false)
                    }
                }
            }
        }
        // A hard rejection means the Vault no longer matches; hand the keys
        // back. Network trouble keeps the app alive (the cached content stays),
        // but T15.1: persistent Network failures (2 in a row, still no live
        // session) escape the Auto phase too — Login keeps the failure detail.
        var autoNetworkFailures by rememberSaveable { mutableStateOf(0) }
        LaunchedEffect(loginState, warmGaveUp) {
            val s = loginState
            if (s is LoginUiState.Failure && !loginVm.hasLiveSession()) {
                if (s.error is VtopError.Network) {
                    autoNetworkFailures++
                    // T15 fix: a warm-loop give-up already aggregates 5 failed
                    // connection attempts — one emission is "persistent", so it
                    // escapes immediately instead of waiting for a second
                    // foreground resume the user may never perform.
                    if (autoNetworkFailures >= 2 || warmGaveUp) phase = BootPhase.Login
                } else {
                    phase = BootPhase.Login
                }
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when {
            // T15.4: blank splash chrome until auto-login resolves — no menu
            // flash before the vault verdict is known.
            phase == BootPhase.Auto && authorizedId == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            "Checking your VTOP session…",
                            modifier = Modifier.padding(top = 16.dp, bottom = innerPadding.calculateBottomPadding()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            phase == BootPhase.Auto || (authorizedId != null && loginVm.hasLiveSession()) -> {
                val id = authorizedId ?: vault.username ?: ""
                val homeVm: HomeViewModel = viewModel(
                    factory = HomeVmFactory(loginVm.currentClient, id),
                    key = id,
                )
                val state by homeVm.state.collectAsStateWithLifecycle()
                val lastSectionTitle by homeVm.lastSectionTitle.collectAsStateWithLifecycle()
                val staleNotice by homeVm.staleNotice.collectAsStateWithLifecycle()
                Box(
                    // T18.4: screens consume the scaffold padding instead of
                    // drawing under the system bars.
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                ) {
                    HomeScreen(
                        state = state,
                        lastSectionTitle = lastSectionTitle,
                        onOpen = { homeVm.open(it) },
                        onBackToMenu = { homeVm.backToMenu() },
                        onRetry = { homeVm.retry() },
                        // T18.3: readiness claims come from the live session.
                        pill = pillOf(warm, loginVm.hasLiveSession()),
                        staleNotice = staleNotice,
                        onStaleNoticeShown = { homeVm.consumeStaleNotice() },
                        onCheckUpdate = { updater.check(force = true) },
                    )
                }
            }
            else -> {
                val state by loginVm.state.collectAsStateWithLifecycle()
                var errorDismissed by remember { mutableStateOf(false) }

                vaultBanner?.let { notice ->
                    Text(
                        notice,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Box(
                    // T18.4: same system-bar discipline on the login gate.
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                ) {
                    LoginScreen(
                        state = if (errorDismissed) LoginUiState.Idle else state,
                        warm = warm,
                        haveSavedCredentials = vault.hasCredentials,
                        savedUsername = vault.username,
                        gatedVault = vault.isGated,
                        onUnlockSaved = {
                            val fa = activity as? FragmentActivity ?: return@LoginScreen
                            if (loginVm.state.value is LoginUiState.Loading) return@LoginScreen
                            scope.launch {
                                val pass = unlockVault(fa, vault)
                                vaultBanner = vault.stateNotice
                                val user = vault.username
                                if (pass != null && user != null) loginVm.submit(user, pass, false)
                            }
                        },
                        onForgetSaved = {
                            // T8.4: explicit logout clears BOTH stores (credentials vault + session snapshot).
                            vault.clear()
                            sessionStore.clear()
                            vaultBanner = null
                        },
                        onSubmit = { user, pass, remember ->
                            errorDismissed = false
                            loginVm.submit(user, pass, remember)
                        },
                        onErrorRetry = {
                            errorDismissed = false
                            loginVm.retry()
                        },
                        onErrorEdit = {
                            errorDismissed = true
                        },
                    )
                }
            }
        }
    }

    // Update popup overlays every phase (splash, login, home).
    AutoUpdateDialog(updater)
}

// ---------------------------------------------------------------- biometric

private suspend fun FragmentActivity.promptAuth(
    title: String,
    subtitle: String,
    cipher: Cipher?,
): BiometricPrompt.AuthenticationResult? = suspendCancellableCoroutine { cont ->
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) cont.resume(null)
            }
        },
    )
    cont.invokeOnCancellation { prompt.cancelAuthentication() }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .build()
    // Some vendor builds throw IllegalArgumentException from authenticate()
    // even after areAllDevicesSupported checks (e.g. Class 2 biometrics on
    // ColorOS; the class verdict is theirs to make late). A throw here means
    // the prompt cannot be used — degrade to "no prompt" instead of crashing.
    try {
        if (cipher != null) prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        else prompt.authenticate(info)
    } catch (e: IllegalArgumentException) {
        AppLog.d("biometric: prompt refused (${e.message?.take(80)})")
        if (cont.isActive) cont.resume(null)
    }
}

// T6.4: a prompt cancel means "don't save (ask again)"; a keystore failure
// retries the cipher once and then falls back to plaintext with an explicit
// on-screen notice — never a silent downgrade.
private suspend fun saveSecurely(
    activity: FragmentActivity?,
    vault: CredentialVault,
    user: String,
    pass: String,
    setNotice: (String?) -> Unit,
) {
    // T16.2/T16.4: a process-death-recovered pending save arrives password-
    // less (passwords never enter SavedStateHandle) — never store an empty
    // secret; ask the user to sign in again instead.
    if (pass.isEmpty()) {
        setNotice("Password not restored after restart — sign in again to save it.")
        return
    }
    if (activity == null || !vault.canGate) {
        vault.savePlaintext(user, pass)
        setNotice("Saved without fingerprint protection (no device lock available).")
        return
    }
    var opened: Cipher? = null
    repeat(2) { attempt ->
        try {
            opened = vault.newSaveCipher()
        } catch (e: Exception) {
            AppLog.d("vault: save cipher attempt $attempt failed (${e.javaClass.simpleName})")
        }
    }
    val saveCipher = opened ?: run {
        vault.savePlaintext(user, pass)
        setNotice("Fingerprint protection unavailable — password saved without it.")
        return
    }
    val proven = activity.promptAuth(
        "Protect your saved password",
        "Decrypting later needs your fingerprint or device PIN",
        saveCipher,
    )
    if (proven != null) {
        vault.saveGated(user, pass, saveCipher)
        setNotice(null)
    } else {
        setNotice("Fingerprint protection cancelled — password not saved; you'll be asked again next sign-in.")
    }
}

private suspend fun unlockVault(activity: FragmentActivity, vault: CredentialVault): String? {
    if (!vault.isGated) return vault.password
    val user = vault.username ?: return null
    val cipher = vault.newUnlockCipher() ?: return null
    val proven = activity.promptAuth(
        "Unlock saved credentials",
        "Sign in as $user",
        cipher,
    ) ?: return null
    return vault.revealPassword(proven.cryptoObject?.cipher ?: cipher)
}
