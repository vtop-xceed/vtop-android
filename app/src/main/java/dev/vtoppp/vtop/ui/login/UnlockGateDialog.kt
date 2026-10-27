package dev.vtoppp.vtop.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Boot ask for the ungated (no Class 3 biometric) fallback vault: the saved
 * secret must be retyped once per app open before auto-login proceeds.
 * [verify] compares the entry against the stored password; a mismatch keeps
 * the dialog open with an error, [onDismiss] drops the user to the sign-in
 * gate (the "Unlock" route stays available there).
 */
@Composable
fun UnlockGateDialog(
    username: String?,
    verify: (String) -> Boolean,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val confirm: () -> Unit = {
        if (verify(pass)) {
            onVerified()
        } else {
            wrong = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome back") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the VTOP password saved for " +
                        (username ?: "this app") + " to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = {
                        pass = it
                        wrong = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("VTOP password") },
                    singleLine = true,
                    isError = wrong,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    supportingText = if (wrong) {
                        { Text("Wrong password — try again.") }
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = pass.isNotEmpty(), onClick = confirm) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}
