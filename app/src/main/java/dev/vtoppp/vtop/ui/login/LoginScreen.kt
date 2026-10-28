package dev.vtoppp.vtop.ui.login

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AppLog
import dev.vtoppp.vtop.data.VtopError
import dev.vtoppp.vtop.data.WarmState

@Composable
fun LoginScreen(
    state: LoginUiState,
    warm: WarmState,
    haveSavedCredentials: Boolean,
    savedUsername: String?,
    gatedVault: Boolean = true,
    onUnlockSaved: () -> Unit,
    onForgetSaved: () -> Unit,
    onSubmit: (username: String, password: String, remember: Boolean) -> Unit,
    onErrorRetry: () -> Unit,
    onErrorEdit: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var typingNew by rememberSaveable { mutableStateOf(false) }
    var showForm by rememberSaveable { mutableStateOf(!haveSavedCredentials) }

    val failure = state as? LoginUiState.Failure

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            failure != null -> ErrorCard(
                error = failure.error,
                detail = failure.detail,
                onRetry = onErrorRetry,
                onEdit = { onErrorEdit(); showForm = true },
            )
            !showForm && haveSavedCredentials && !typingNew -> SavedAccountCard(
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
            else -> SigninForm(
                username = username,
                password = password,
                state = state,
                warm = warm,
                onUsername = { username = it.trim() },
                onPassword = { password = it },
                onSubmit = {
                    onSubmit(username, password, true)
                    password = ""
                },
            )
        }
    }
}

@Composable
private fun SigninForm(
    username: String,
    password: String,
    state: LoginUiState,
    warm: WarmState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Google-style monogram icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "V",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Sign in with VTOP",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "Use your Student Registration Number",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            WarmStatusLine(warm)

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsername,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Registration Number / User ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(18.dp),
            )

            Spacer(Modifier.height(14.dp))

            var showPassword by rememberSaveable { mutableStateOf(false) }
            OutlinedTextField(
                value = password,
                onValueChange = onPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (showPassword) KeyboardType.Text else KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
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

            Spacer(Modifier.height(10.dp))

            Text(
                "Encrypted on-device · Protected with Biometric / PIN App Lock",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            val loading = state as? LoginUiState.Loading
            if (loading != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                    Text(loading.line, style = MaterialTheme.typography.bodyMedium)
                }
                ActivityLog()
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                enabled = username.isNotBlank() && password.isNotBlank() && state !is LoginUiState.Loading,
            ) {
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
private fun WarmStatusLine(warm: WarmState) {
    val (text, dotColor) = when (warm) {
        is WarmState.Idle -> return
        is WarmState.Working -> warm.step to MaterialTheme.colorScheme.primary
        is WarmState.CaptchaReady -> "Connection ready · Instant sign-in" to Color(0xFF146C2E)
        is WarmState.Failed -> warm.message to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityLog() {
    val logs: List<String> by produceState(initialValue = emptyList()) {
        AppLog.lines.collect { line -> value = (value + line).takeLast(4) }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .animateContentSize(),
    ) {
        logs.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SavedAccountCard(
    savedUsername: String?,
    onUnlock: () -> Unit,
    onOtherAccount: () -> Unit,
    onForget: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
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
                "Welcome back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                savedUsername?.let { "$it is saved on this device." } ?: "A saved account exists in this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onUnlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(
                    "Unlock & Continue",
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

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onForget) {
                Text(
                    "Remove account from this device",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    error: VtopError,
    detail: String?,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    it,
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

private fun errorTitle(err: VtopError): String = when (err) {
    VtopError.Network -> "No connection"
    VtopError.CaptchaRejected -> "Captcha failed"
    is VtopError.CredentialsRejected -> "Wrong credentials"
    VtopError.RateLimited -> "Too many attempts"
    VtopError.SessionExpired -> "Session expired"
    is VtopError.Blocked -> "Blocked by VTOP"
    is VtopError.Unknown -> "Unexpected error"
}
