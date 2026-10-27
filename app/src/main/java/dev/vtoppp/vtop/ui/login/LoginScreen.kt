package dev.vtoppp.vtop.ui.login

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
    var username by rememberSaveable { mutableStateOf(dev.vtoppp.vtop.Creds.USER) }
    var password by rememberSaveable { mutableStateOf(dev.vtoppp.vtop.Creds.PASS) }
    var typingNew by rememberSaveable { mutableStateOf(false) }
    var remember by rememberSaveable { mutableStateOf(true) }
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
                    showForm = true; typingNew = true; username = ""; password = ""
                },
                onForget = {
                    onForgetSaved()
                    showForm = true; typingNew = true; username = ""; password = ""
                },
            )
            else -> SigninForm(
                username = username,
                password = password,
                remember = remember,
                state = state,
                warm = warm,
                onUsername = { username = it.trim() },
                onPassword = { password = it },
                onRemember = { remember = it },
                onSubmit = {
                    onSubmit(username, password, remember); password = ""
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SigninForm(
    username: String,
    password: String,
    remember: Boolean,
    state: LoginUiState,
    warm: WarmState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onRemember: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    Text("Sign in to VTOP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    WarmStatusLine(warm)
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(
        value = username,
        onValueChange = onUsername,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("REG no. / User ID") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        shape = RoundedCornerShape(16.dp),
    )
    Spacer(Modifier.height(12.dp))
    var showPassword by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = password,
        onValueChange = onPassword,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = if (showPassword) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (showPassword) KeyboardType.Text else KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        shape = RoundedCornerShape(16.dp),
        trailingIcon = {
            IconButton(onClick = {
                showPassword = !showPassword
            }) {
                val eyeIcon = if (showPassword) {
                    dev.vtoppp.vtop.R.drawable.ic_visibility_off
                } else {
                    dev.vtoppp.vtop.R.drawable.ic_visibility
                }
                Icon(
                    painterResource(eyeIcon),
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = remember, onCheckedChange = onRemember)
        Text("Remember me in this app", style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(12.dp))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        val loading = state as? LoginUiState.Loading
        if (loading != null) {
            LoadingIndicator(modifier = Modifier.size(48.dp))
            Text(loading.line, style = MaterialTheme.typography.bodyMedium)
            ActivityLog()
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(24.dp),
        enabled = username.isNotBlank() && password.isNotBlank() && state !is LoginUiState.Loading,
    ) {
        Text("Log in")
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WarmStatusLine(warm: WarmState) {
    val (text, color) = when (warm) {
        is WarmState.Idle -> return
        is WarmState.Working -> warm.step to MaterialTheme.colorScheme.onSurfaceVariant
        is WarmState.CaptchaReady -> "Captcha solved · sign in in one tap" to MaterialTheme.colorScheme.primary
        is WarmState.Failed -> warm.message to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (warm is WarmState.Working) {
            LoadingIndicator(modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Recent live activity lines from [AppLog], so the steps of login stay visible. */
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Welcome back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                savedUsername?.let { "$it is saved in this app." } ?: "A saved account exists in this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onOtherAccount,
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text("Use another account")
            }
            TextButton(onClick = onForget) {
                Text(
                    "Forget saved account",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(errorTitle(error), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        detail?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onEdit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Edit credentials")
            }
            Button(onClick = onRetry) { Text("Try again") }
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
