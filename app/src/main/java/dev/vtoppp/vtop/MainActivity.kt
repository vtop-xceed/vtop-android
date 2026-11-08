package dev.vtoppp.vtop

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
import dev.vtoppp.vtop.data.UsagePing
import dev.vtoppp.vtop.data.CredentialVault
import dev.vtoppp.vtop.data.SessionCache
import dev.vtoppp.vtop.data.SessionStore
import dev.vtoppp.vtop.data.VtopError
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.data.WarmState
import dev.vtoppp.vtop.data.pillOf
import dev.vtoppp.vtop.ui.home.AutoUpdateDialog
import dev.vtoppp.vtop.ui.home.HomeScreen
import dev.vtoppp.vtop.ui.home.HomeViewModel
import dev.vtoppp.vtop.ui.login.LockScreen
import dev.vtoppp.vtop.ui.login.LoginScreen
import dev.vtoppp.vtop.ui.login.LoginUiState
import dev.vtoppp.vtop.ui.login.LoginViewModel
import dev.vtoppp.vtop.ui.theme.AppPalette
import dev.vtoppp.vtop.ui.theme.Motion
import dev.vtoppp.vtop.ui.theme.ThemePreferences
import dev.vtoppp.vtop.ui.theme.VtopTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
        UserDatabase.init(this)
        AppLog.d("app: onCreate")
        setContent {
            var currentPalette by remember {
                mutableStateOf(ThemePreferences.getPalette(this))
            }
            var themeMode by remember {
                mutableStateOf(ThemePreferences.getThemeMode(this))
            }
            var blurEnabled by remember {
                mutableStateOf(ThemePreferences.getBlurEnabled(this))
            }
            var lockEnabled by remember {
                mutableStateOf(ThemePreferences.getLockEnabled(this))
            }
            val isDark = when (themeMode) {
                dev.vtoppp.vtop.ui.theme.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                dev.vtoppp.vtop.ui.theme.ThemeMode.LIGHT -> false
                else -> true
            }
            val decorView = window.decorView
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, decorView)
            LaunchedEffect(isDark) {
                insetsController.isAppearanceLightStatusBars = !isDark
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }

            VtopTheme(palette = currentPalette, themeMode = themeMode, blurEnabled = blurEnabled) {
                Root(
                    vault = vault,
                    autoCaptcha = autoCaptcha,
                    sessionStore = sessionStore,
                    currentPalette = currentPalette,
                    onPaletteChange = { newPalette ->
                        currentPalette = newPalette
                        ThemePreferences.setPalette(this, newPalette)
                    },
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        ThemePreferences.setThemeMode(this, mode)
                    },
                    blurEnabled = blurEnabled,
                    onBlurEnabledChange = { enabled ->
                        blurEnabled = enabled
                        ThemePreferences.setBlurEnabled(this, enabled)
                    },
                    lockEnabled = lockEnabled,
                    onLockEnabledChange = { enabled ->
                        lockEnabled = enabled
                        ThemePreferences.setLockEnabled(this, enabled)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
    }
}

/** Boot phase: straight into the cached app (saved profile) or the login gate. */
enum class BootPhase { Auto, Login }

@Composable
private fun Root(
    vault: CredentialVault,
    autoCaptcha: AutoCaptchaClient,
    sessionStore: SessionStore,
    currentPalette: AppPalette,
    onPaletteChange: (AppPalette) -> Unit,
    themeMode: dev.vtoppp.vtop.ui.theme.ThemeMode,
    onThemeModeChange: (dev.vtoppp.vtop.ui.theme.ThemeMode) -> Unit,
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    lockEnabled: Boolean,
    onLockEnabledChange: (Boolean) -> Unit,
) {
    val activity = LocalContext.current
    val scope = rememberCoroutineScope()

    val loginVm: LoginViewModel = viewModel(factory = LoginVmFactory(vault, autoCaptcha, sessionStore))
    var authorizedId by rememberSaveable { mutableStateOf<String?>(null) }
    var phase by rememberSaveable {
        mutableStateOf(if (vault.hasCredentials) BootPhase.Auto else BootPhase.Login)
    }

    // App Lock: if saved credentials exist, user must unlock using fingerprint scan or lockscreen PIN
    var isUnlocked by rememberSaveable { mutableStateOf(!vault.hasCredentials || !ThemePreferences.getLockEnabled(activity)) }
    LaunchedEffect(vault.hasCredentials, lockEnabled) {
        if (!vault.hasCredentials || !lockEnabled) isUnlocked = true
    }
    var isAuthenticating by remember { mutableStateOf(false) }
    var isBackgrounded by remember { mutableStateOf(false) }
    var backgroundedAt by remember { mutableStateOf(0L) }

    val loginState by loginVm.state.collectAsStateWithLifecycle()
    val warm by loginVm.warm.collectAsStateWithLifecycle()
    // Offline entry: a fresh seeded snapshot carries the id the Room/SQLite
    // caches are keyed under, so the cached dashboard can open without net.
    val cachedOfflineId = remember { sessionStore.loadFresh()?.authorizedId }
    // fix: the warm give-up must be visible to composition even when no
    // NEW LoginUiState arrives (a stale Failure already occupies _state).
    val warmGaveUp by loginVm.warmGaveUp.collectAsStateWithLifecycle()

    var vaultBanner by remember { mutableStateOf<String?>(null) }

    // Self-update gate: silent startup ping (opt-out remembered), then the
    // popup drives download with a loading animation and the final Update tap.
    val updater = remember { AutoUpdater(activity.applicationContext) }
    var autoUpdateEnabled by remember { mutableStateOf(updater.autoCheckEnabled) }
    LaunchedEffect(Unit) { updater.autoCheck() }
    LaunchedEffect(Unit) { UsagePing.ping(activity.applicationContext) }

    // Once user signs in with user ID and password, securely save credentials and grant access
    LaunchedEffect(Unit) {
        loginVm.state.collect { s ->
            if (s is LoginUiState.Success) {
                authorizedId = s.authorizedId
                loginVm.consumePendingRemember()?.let { (user, pass) ->
                    if (user.isNotBlank() && pass.isNotBlank()) {
                        vault.saveCredentials(user, pass)
                        isUnlocked = true
                        phase = BootPhase.Auto
                        AppLog.d("vault: saved credentials; app lock enabled")
                    }
                }
            }
        }
    }

    // Lifecycle observer: gate warm loop and re-lock app when returning from background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    loginVm.setForeground(true)
                    // If returning after being away in background, lock the app (respect disabled lock toggle)
                    if (vault.hasCredentials && lockEnabled && isBackgrounded && System.currentTimeMillis() - backgroundedAt > 2000L) {
                        isUnlocked = false
                    }
                    isBackgrounded = false
                }
                Lifecycle.Event.ON_STOP -> {
                    loginVm.setForeground(false)
                    if (!isAuthenticating && vault.hasCredentials) {
                        isBackgrounded = true
                        backgroundedAt = System.currentTimeMillis()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Automatically prompt for fingerprint or lockscreen PIN when app is opened in locked state
    LaunchedEffect(isUnlocked, vault.hasCredentials) {
        if (!isUnlocked && vault.hasCredentials) {
            val fa = activity as? FragmentActivity ?: return@LaunchedEffect
            if (isAuthenticating) return@LaunchedEffect
            isAuthenticating = true
            try {
                val res = fa.promptDeviceAuth(
                    title = "Unlock VTOP",
                    subtitle = "Use fingerprint or lockscreen PIN to access",
                )
                when (res) {
                    DeviceAuthResult.Success, DeviceAuthResult.NotAvailable -> {
                        isUnlocked = true
                        vaultBanner = null
                        if (phase != BootPhase.Auto) {
                            phase = BootPhase.Auto
                        }
                    }
                    DeviceAuthResult.Cancelled -> {
                        // User dismissed prompt; remain on LockScreen
                    }
                    is DeviceAuthResult.Failed -> {
                        vaultBanner = res.error
                    }
                }
            } finally {
                isAuthenticating = false
            }
        }
    }

    // Auto sign-in effects (only run when unlocked and in Auto phase).
    if (phase == BootPhase.Auto && isUnlocked) {
        if (authorizedId == null) {
            val autoPass = remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                val user = vault.username
                val pass = vault.password
                if (user != null && pass != null) {
                    autoPass.value = pass
                    loginVm.submit(user, pass, false)
                } else {
                    vaultBanner = vault.stateNotice
                    phase = BootPhase.Login
                }
            }
            // Recover on network ready
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
        var autoNetworkFailures by rememberSaveable { mutableStateOf(0) }
        LaunchedEffect(loginState, warmGaveUp) {
            val s = loginState
            if (s is LoginUiState.Failure && !loginVm.hasLiveSession() && authorizedId == null) {
                if (s.error is VtopError.Network || s.error is VtopError.Unknown) {
                    // Offline: open the app against the cache instead of the login wall;
                    // the bridge keeps retrying in the background and flips the pill.
                    val offlineId = cachedOfflineId
                    if (offlineId != null) {
                        AppLog.d("offline: opening cached app with seeded identity")
                        authorizedId = offlineId
                        phase = BootPhase.Auto
                        return@LaunchedEffect
                    }
                    autoNetworkFailures++
                    if (autoNetworkFailures >= 2 || warmGaveUp) phase = BootPhase.Login
                } else {
                    phase = BootPhase.Login
                }
            }
        }
    }

    val branch = when {
        vault.hasCredentials && !isUnlocked -> "lock"
        phase == BootPhase.Auto && authorizedId == null -> "auto"
        phase != BootPhase.Login && (phase == BootPhase.Auto || (authorizedId != null && loginVm.hasLiveSession())) -> "home"
        else -> "login"
    }
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        AnimatedContent(
            targetState = branch,
            transitionSpec = {
                fadeIn(animationSpec = androidx.compose.animation.core.tween(Motion.Content)) togetherWith
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(Motion.Quick))
            },
            label = "rootBranch",
        ) { target ->
        when (target) {
            // App Lock screen: shown when user has saved credentials but app is locked
            "lock" -> {
                LockScreen(
                    savedUsername = vault.username,
                    banner = vaultBanner,
                    onUnlock = {
                        val fa = activity as? FragmentActivity ?: return@LockScreen
                        if (isAuthenticating) return@LockScreen
                        scope.launch {
                            isAuthenticating = true
                            try {
                                val res = fa.promptDeviceAuth(
                                    title = "Unlock VTOP",
                                    subtitle = "Use fingerprint or lockscreen PIN to access",
                                )
                                when (res) {
                                    DeviceAuthResult.Success, DeviceAuthResult.NotAvailable -> {
                                        isUnlocked = true
                                        vaultBanner = null
                                        if (phase != BootPhase.Auto) {
                                            phase = BootPhase.Auto
                                        }
                                    }
                                    DeviceAuthResult.Cancelled -> {}
                                    is DeviceAuthResult.Failed -> {
                                        vaultBanner = res.error
                                    }
                                }
                            } finally {
                                isAuthenticating = false
                            }
                        }
                    },
                    onSwitchAccount = {
                        vault.clear()
                        sessionStore.clear()
                        isUnlocked = true
                        phase = BootPhase.Login
                        vaultBanner = null
                    },
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                )
            }
            "auto" -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            LoadingIndicator(
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Checking your VTOP session…",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            "home" -> {
                val id = authorizedId ?: vault.username ?: ""
                val homeVm: HomeViewModel = viewModel(
                    factory = HomeVmFactory(loginVm.currentClient, id),
                    key = id,
                )
                val state by homeVm.state.collectAsStateWithLifecycle()
                val lastSectionTitle by homeVm.lastSectionTitle.collectAsStateWithLifecycle()
                val staleNotice by homeVm.staleNotice.collectAsStateWithLifecycle()
                val marksNotice by homeVm.marksNotice.collectAsStateWithLifecycle()
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()),
                ) {
                    HomeScreen(
                        state = state,
                        lastSectionTitle = lastSectionTitle,
                        onOpen = { homeVm.open(it) },
                        onBackToMenu = { homeVm.backToMenu() },
                        onRetry = { homeVm.retry() },
                        pill = pillOf(warm, loginVm.hasLiveSession()),
                        staleNotice = staleNotice,
                        onStaleNoticeShown = { homeVm.consumeStaleNotice() },
                        onCheckUpdate = { updater.check(force = true) },
                        onLockApp = { isUnlocked = false },
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        blurEnabled = blurEnabled,
                        onBlurEnabledChange = onBlurEnabledChange,
                        lockEnabled = lockEnabled,
                        onLockEnabledChange = onLockEnabledChange,
                        autoUpdateEnabled = autoUpdateEnabled,
                        onAutoUpdateEnabledChange = { enabled ->
                            autoUpdateEnabled = enabled
                            updater.autoCheckEnabled = enabled
                        },
                        onBridgeRetry = { loginVm.rebridge() },
                        onRefresh = { homeVm.refreshMarksAndGrades() },
                        marksNotice = marksNotice,
                        onMarksNoticeShown = { homeVm.consumeMarksNotice() },
                        onSignOut = {
                            vault.clear()
                            sessionStore.clear()
                            authorizedId = null
                            loginVm.reset()
                            isUnlocked = true
                            phase = BootPhase.Login
                            vaultBanner = null
                        },
                        currentPalette = currentPalette,
                        onPaletteChange = onPaletteChange,
                    )
                    AutoUpdateDialog(updater)
                }
            }
            else -> {
                val state by loginVm.state.collectAsStateWithLifecycle()
                var errorDismissed by remember { mutableStateOf(false) }

                AnimatedVisibility(
                    visible = vaultBanner != null,
                    enter = Motion.BannerEnter,
                    exit = Motion.BannerExit,
                ) {
                    vaultBanner?.let { notice ->
                        Text(
                            notice,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()),
                ) {
                    LoginScreen(
                        state = if (errorDismissed) LoginUiState.Idle else state,
                        warm = warm,
                        haveSavedCredentials = vault.hasCredentials,
                        savedUsername = vault.username,
                        onUnlockSaved = {
                            val fa = activity as? FragmentActivity ?: return@LoginScreen
                            if (loginVm.state.value is LoginUiState.Loading) return@LoginScreen
                            scope.launch {
                                val res = fa.promptDeviceAuth(
                                    title = "Unlock VTOP",
                                    subtitle = "Use fingerprint or lockscreen PIN to access",
                                )
                                if (res is DeviceAuthResult.Success || res is DeviceAuthResult.NotAvailable) {
                                    isUnlocked = true
                                    vaultBanner = null
                                    val user = vault.username
                                    val pass = vault.password
                                    if (user != null && pass != null) loginVm.submit(user, pass, false)
                                }
                            }
                        },
                        onForgetSaved = {
                            vault.clear()
                            sessionStore.clear()
                            vaultBanner = null
                            isUnlocked = true
                            phase = BootPhase.Login
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
    }
}

// ---------------------------------------------------------------- device auth (fingerprint / lockscreen PIN)

sealed interface DeviceAuthResult {
    data object Success : DeviceAuthResult
    data object Cancelled : DeviceAuthResult
    data class Failed(val error: String) : DeviceAuthResult
    data object NotAvailable : DeviceAuthResult
}

private suspend fun FragmentActivity.promptDeviceAuth(
    title: String,
    subtitle: String,
): DeviceAuthResult = suspendCancellableCoroutine { cont ->
    val bm = BiometricManager.from(this)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val canAuth = bm.canAuthenticate(authenticators)
    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    val isSecure = km?.isDeviceSecure == true

    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS && !isSecure) {
        cont.resume(DeviceAuthResult.NotAvailable)
        return@suspendCancellableCoroutine
    }

    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(DeviceAuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        cont.resume(DeviceAuthResult.Cancelled)
                    } else {
                        cont.resume(DeviceAuthResult.Failed("Auth error $errorCode: $errString"))
                    }
                }
            }
        },
    )
    cont.invokeOnCancellation { prompt.cancelAuthentication() }

    val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setAllowedAuthenticators(authenticators).build()

    try {
        prompt.authenticate(info)
    } catch (e: Exception) {
        AppLog.d("biometric prompt failed: ${e.message}")
        if (cont.isActive) cont.resume(DeviceAuthResult.Failed(e.message ?: "Authentication failed"))
    }
}

