package dev.vtoppp.vtop.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.data.AutoUpdateState
import dev.vtoppp.vtop.data.AutoUpdater
import dev.vtoppp.vtop.data.ReleaseInfo

/**
 * The single popup driving the whole update flow:
 *
 *  - [AutoUpdateState.Available] — "new release" card; [Download] opens the
 *    expressive [WhatsNewSheet] with release notes and live download animations;
 *  - [AutoUpdateState.Downloading] — surfaced inside [WhatsNewSheet];
 *  - [AutoUpdateState.Ready] — hands the downloaded APK to the system installer;
 *  - [AutoUpdateState.Failed]/[UpToDate]/[Checking] — short status cards with Retry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AutoUpdateDialog(
    updater: AutoUpdater,
) {
    val state = updater.state
    var activeRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showWhatsNew by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            is AutoUpdateState.Available -> activeRelease = state.release
            is AutoUpdateState.Ready -> activeRelease = state.release
            is AutoUpdateState.Idle -> showWhatsNew = false
            else -> Unit
        }
    }

    if (showWhatsNew && activeRelease != null) {
        WhatsNewSheet(
            release = activeRelease!!,
            updater = updater,
            onDismiss = { showWhatsNew = false },
        )
        return
    }

    when (val s = state) {
        is AutoUpdateState.Idle -> Unit
        is AutoUpdateState.Checking -> AlertDialog(
            onDismissRequest = updater::reset,
            title = { Text("Checking for updates…") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                    Text("Pinging GitHub Releases")
                }
            },
            confirmButton = {},
        )

        is AutoUpdateState.Available -> AlertDialog(
            onDismissRequest = { updater.dismiss(s.release) },
            title = { Text("Update available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateBody(
                        title = s.release.title,
                        tagLine = "v" + s.release.version + " · " + s.release.apkNameSizeLine(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeRelease = s.release
                        showWhatsNew = true
                        updater.startDownload(s.release)
                    },
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { updater.dismiss(s.release) }) { Text("Later") }
            },
        )

        is AutoUpdateState.Downloading -> {
            val rel = activeRelease
            if (rel != null) {
                WhatsNewSheet(
                    release = rel,
                    updater = updater,
                    onDismiss = updater::reset,
                )
            }
        }

        is AutoUpdateState.Ready -> {
            val rel = activeRelease ?: s.release
            WhatsNewSheet(
                release = rel,
                updater = updater,
                onDismiss = updater::reset,
            )
        }

        is AutoUpdateState.Failed -> AlertDialog(
            onDismissRequest = updater::reset,
            title = { Text("Update check failed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateBody(title = s.reason, tagLine = null)
                }
            },
            confirmButton = {
                TextButton(onClick = { updater.check(force = true) }) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = updater::reset) { Text("Close") }
            },
        )

        is AutoUpdateState.UpToDate -> AlertDialog(
            onDismissRequest = updater::reset,
            title = { Text("Up to date") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateBody(
                        title = "You're running the latest release (v" + s.currentVersion + ").",
                        tagLine = null,
                    )
                }
            },
            confirmButton = { TextButton(onClick = updater::reset) { Text("OK") } },
        )
    }
}

@Composable
private fun UpdateBody(title: String, tagLine: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (tagLine != null) {
            Text(
                tagLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ReleaseInfo.apkNameSizeLine(): String =
    if (apkSize > 0) apkName + " · " + (apkSize.toDouble() / (1024.0 * 1024.0)).toInt() + " MB" else apkName
