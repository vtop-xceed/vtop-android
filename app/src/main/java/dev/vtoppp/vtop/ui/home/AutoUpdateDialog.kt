package dev.vtoppp.vtop.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.data.AutoUpdateState
import dev.vtoppp.vtop.data.AutoUpdater
import dev.vtoppp.vtop.data.ReleaseInfo

/**
 * The single popup driving the whole update flow:
 *
 *  - [AutoUpdateState.Available] — "new release" card; [Update] starts the
 *    download (the auto-check toggle lives here so the option stays discoverable
 *    exactly when an update exists);
 *  - [AutoUpdateState.Downloading] — Android loading animation + megabyte counter;
 *  - [AutoUpdateState.Ready] — final instruction card: pressing [Update] hands the
 *    downloaded APK to the system installer;
 *  - [AutoUpdateState.Failed]/[UpToDate]/[Checking] — short status cards with Retry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AutoUpdateDialog(
    updater: AutoUpdater,
) {
    val state = updater.state
    var autoCheck by remember { mutableStateOf(updater.autoCheckEnabled) }

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
                    AutoCheckRow(autoCheck) {
                        autoCheck = it
                        updater.autoCheckEnabled = it
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { updater.startDownload(s.release) }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { updater.dismiss(s.release) }) { Text("Later") }
            },
        )

        is AutoUpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val total = s.total
                    if (total != null && total > 0) {
                        val fraction = (s.received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        LinearWavyProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                        )
                    } else {
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                        )
                    }
                    Text(
                        text = s.total?.let { total ->
                            "Loading " + s.received.megabytes() + " / " +
                                total.megabytes() + " MB…"
                        } ?: "Loading " + s.received.megabytes() + "…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {},
        )

        is AutoUpdateState.Ready -> AlertDialog(
            onDismissRequest = { updater.reset() },
            title = { Text("Update ready to install") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateBody(
                        title = "v" + s.release.version + " downloaded",
                        tagLine = "Press Update and confirm the system installation prompt.",
                    )
                    Text(
                        "If you're upgrading across major versions, Android may ask you to uninstall the previous release first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { updater.install(s.release, s.file) }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = updater::reset) { Text("Later") }
            },
        )

        is AutoUpdateState.Failed -> AlertDialog(
            onDismissRequest = updater::reset,
            title = { Text("Update check failed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateBody(title = s.reason, tagLine = null)
                    AutoCheckRow(autoCheck) {
                        autoCheck = it
                        updater.autoCheckEnabled = it
                    }
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
                    AutoCheckRow(autoCheck) {
                        autoCheck = it
                        updater.autoCheckEnabled = it
                    }
                }
            },
            confirmButton = { TextButton(onClick = updater::reset) { Text("OK") } },
        )
    }
}

@Composable
private fun AutoCheckRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(
            "Check for updates at startup",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
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
    if (apkSize > 0) apkName + " · " + apkSize.megabytes() + " MB" else apkName

private fun Long.megabytes(): String {
    val mb = toDouble() / (1024.0 * 1024.0)
    return if (mb >= 10.0) mb.toInt().toString() else "%.1f".format(mb)
}