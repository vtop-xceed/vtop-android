package dev.vtoppp.vtop.ui.login

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.VtopError
import dev.vtoppp.vtop.data.WarmState
import dev.vtoppp.vtop.ui.theme.Motion
import dev.vtoppp.vtop.ui.theme.StatusColors

/**
 * One line of truth about the background work, and whether it is still running.
 * Derived from the two states the screen already owns ([WarmState] and
 * [LoginUiState]) so the user always sees the current beat of the pipeline.
 */
private data class LoginStatus(val label: String, val busy: Boolean, val failed: Boolean)

private enum class LoginBranch { Form, Saved, Error }

/**
 * The sign-in screen: one form, one status line, one button. Copy is kept to
 * what a user needs to act, the motion carries the rest.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    warm: WarmState,
    haveSavedCredentials: Boolean,
    savedUsername: String?,
    onUnlockSaved: () -> Unit,
    onForgetSaved: () -> Unit,
    onSubmit: (username: String, password: String, remember: Boolean) -> Unit,
    onErrorRetry: () -> Unit,
    onErrorEdit: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var typingNew by rememberSaveable { mutableStateOf(false) }
    var showForm by rememberSaveable { mutableStateOf(!haveSavedCredentials) }
    var fieldFocused by remember { mutableStateOf(false) }

    val failure = state as? LoginUiState.Failure
    val branch = when {
        failure != null -> LoginBranch.Error
        !showForm && haveSavedCredentials && !typingNew -> LoginBranch.Saved
        else -> LoginBranch.Form
    }
    val status = loginStatus(warm, state)
    val animated = ValueAnimator.areAnimatorsEnabled()

    // One mover only: the IME inset. The scroll lives OUTSIDE the padded
    // viewport, so opening the keyboard can never grow the content and open
    // a second scroll range under the user's finger.
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val viewportHeight = maxHeight
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = viewportHeight)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        BrandMark(focused = fieldFocused, busy = status?.busy == true, animated = animated)

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Sign in to VTOP",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(10.dp))

        StatusLine(status)

        Spacer(Modifier.height(22.dp))

        AnimatedContent(
            targetState = branch,
            transitionSpec = {
                fadeIn(tween(Motion.Quick)) togetherWith fadeOut(tween(Motion.Fast))
            },
            label = "loginBranch",
        ) { key ->
            when (key) {
                LoginBranch.Error -> failure?.let { failure ->
                    ErrorPanel(
                        error = failure.error,
                        detail = failure.detail,
                        onRetry = onErrorRetry,
                        onEdit = { onErrorEdit(); showForm = true },
                        animated = animated,
                    )
                }
                LoginBranch.Saved -> SavedAccountPanel(
                    savedUsername = savedUsername,
                    onUnlock = onUnlockSaved,
                    onOtherAccount = {
                        showForm = true
                        typingNew = true
                        username = ""
                        password = ""
                    },
                    onForget = {
                        onForgetSaved()
                        showForm = true
                        typingNew = true
                        username = ""
                        password = ""
                    },
                )
                LoginBranch.Form -> CredentialForm(
                    username = username,
                    password = password,
                    loading = state is LoginUiState.Loading,
                    onUsername = { username = it.trim() },
                    onPassword = { password = it },
                    onFocusChange = { fieldFocused = it },
                    onSubmit = {
                        onSubmit(username, password, true)
                        password = ""
                    },
                )
            }
            }
        }
    }
    }
}

/** The brand mark pulses with focus and carries the only looping progress bar. */
@Composable
private fun BrandMark(focused: Boolean, busy: Boolean, animated: Boolean) {
    val markScale by animateFloatAsState(
        targetValue = if (focused && animated) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "markScale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = markScale
                    scaleY = markScale
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "V",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(visible = busy && animated) {
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            LinearWavyProgressIndicator(
                modifier = Modifier.width(140.dp).height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                gapSize = 0.dp,
                amplitude = 2f,
                wavelength = 40.dp,
            )
        }
    }
}

/**
 * The single status surface. One node, one sentence, announced politely so a
 * screen reader hears progress without the screen filling with text.
 */
@Composable
private fun StatusLine(status: LoginStatus?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 26.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                fadeIn(tween(Motion.Quick)) togetherWith fadeOut(tween(Motion.Fast))
            },
            label = "statusLine",
        ) { current ->
            if (current == null) {
                Spacer(Modifier.height(1.dp))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    current.failed -> MaterialTheme.colorScheme.error
                                    current.busy -> MaterialTheme.colorScheme.primary
                                    else -> StatusColors.Success
                                },
                            ),
                    )
                    Text(
                        text = current.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialForm(
    username: String,
    password: String,
    loading: Boolean,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val ready = username.isNotBlank() && password.isNotBlank() && !loading
    val ctaScale by animateFloatAsState(
        targetValue = if (ready) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "ctaScale",
    )
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsername,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.isFocused) },
            label = { Text("Registration number / user ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = RoundedCornerShape(18.dp),
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.isFocused) },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (showPassword) KeyboardType.Text else KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSubmit()
            }),
            shape = RoundedCornerShape(18.dp),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    val eyeIcon = if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                    Icon(
                        painterResource(eyeIcon),
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer {
                    scaleX = ctaScale
                    scaleY = ctaScale
                },
            shape = RoundedCornerShape(26.dp),
            enabled = ready,
        ) {
            if (loading) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LoadingIndicator(modifier = Modifier.size(22.dp))
            } else {
                Text(
                    text = "Sign in",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SavedAccountPanel(
    savedUsername: String?,
    onUnlock: () -> Unit,
    onOtherAccount: () -> Unit,
    onForget: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (savedUsername?.firstOrNull()?.uppercase() ?: "V"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = savedUsername ?: "Saved account",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onUnlock,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text(
                text = "Unlock and continue",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onOtherAccount,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text("Use another account")
        }

        Spacer(Modifier.height(4.dp))

        TextButton(onClick = onForget) {
            Text(
                text = "Remove saved account",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ErrorPanel(
    error: VtopError,
    detail: String?,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    animated: Boolean,
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error, detail) {
        if (!animated) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(
            1f,
            keyframes {
                durationMillis = 280
                0f at 0
                -8f at 60
                8f at 140
                -4f at 210
                0f at 280
            },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = shake.value.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                errorTitle(error),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            detail?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it.withoutDebugTag(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text("Edit credentials")
                }
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text("Try again")
                }
            }
        }
    }
}

/**
 * The pipeline as the user should see it: the current warm beat wins while
 * work is in flight, a settled session says so, and a failure says why.
 */
private fun loginStatus(warm: WarmState, state: LoginUiState): LoginStatus? {
    val loading = state is LoginUiState.Loading
    val step = (warm as? WarmState.Working)?.step
    return when {
        loading && step != null -> LoginStatus(phaseLabel(step), busy = true, failed = false)
        loading -> LoginStatus("Signing in", busy = true, failed = false)
        warm is WarmState.Working -> LoginStatus(phaseLabel(warm.step), busy = true, failed = false)
        warm is WarmState.CaptchaReady -> LoginStatus("Session ready", busy = false, failed = false)
        warm is WarmState.Failed -> LoginStatus(warm.message, busy = false, failed = true)
        else -> null
    }
}

/** Shortens the engine's step sentences to what fits on one status line. */
private fun phaseLabel(step: String): String {
    val s = step.trim().removeSuffix("…").trim()
    if (!s.startsWith("Retrying VTOP connection")) return s
    val wait = s.substringAfter("in ", "").trim()
    return if (wait.isEmpty()) "Retrying" else "Retrying in $wait"
}

/** Drops the trailing "[CredentialsRejected]" style debug tag from a server message. */
private fun String.withoutDebugTag(): String = replace(Regex("\\s*\\[[A-Za-z]+\\]$"), "").trim()

private fun errorTitle(err: VtopError): String = when (err) {
    VtopError.Network -> "No connection"
    VtopError.CaptchaRejected -> "Captcha failed"
    is VtopError.CredentialsRejected -> "Wrong credentials"
    VtopError.RateLimited -> "Too many attempts"
    VtopError.SessionExpired -> "Session expired"
    is VtopError.Blocked -> "Blocked by VTOP"
    is VtopError.Unknown -> "Unexpected error"
}
