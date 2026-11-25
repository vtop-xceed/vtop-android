package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AutoUpdateState
import dev.vtoppp.vtop.data.AutoUpdater
import dev.vtoppp.vtop.data.ReleaseInfo
import dev.vtoppp.vtop.ui.theme.AppDimens
import dev.vtoppp.vtop.ui.theme.AppModalBottomSheet
import dev.vtoppp.vtop.ui.theme.Motion
import java.io.File

/**
 * Expressive "What's New" bottom sheet that displays release notes
 * and live animations while the new APK downloads in the background.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhatsNewSheet(
    release: ReleaseInfo,
    updater: AutoUpdater,
    onDismiss: () -> Unit,
) {
    val state = updater.state

    AppModalBottomSheet(onDismissRequest = onDismiss) {
        // Big "What's New" Header
        Text(
            text = "What's New",
            style = MaterialTheme.typography.headlineLargeEmphasized.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Release ${release.tag} · ${release.title}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = AppDimens.CardPadding),
        )

        // Animated Download / Status Card
        DownloadAnimationCard(
            release = release,
            state = state,
            onDownload = { updater.startDownload(release) },
            onInstall = { file -> updater.install(release, file) },
        )

        Spacer(Modifier.height(AppDimens.SectionGap))

        Text(
            text = "Release Highlights",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(AppDimens.CompactGap))

        // Scrollable Release Notes
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.CardPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                val rawNotes = release.notes?.trim()
                if (rawNotes.isNullOrBlank()) {
                    Text(
                        text = "• State machine optimizations and concurrency fixes\n• UI responsiveness improvements\n• Bug fixes and battery efficiency enhancements",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp,
                    )
                } else {
                    Text(
                        text = rawNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(AppDimens.SectionGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ItemGap, Alignment.End),
        ) {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text("Close")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadAnimationCard(
    release: ReleaseInfo,
    state: AutoUpdateState,
    onDownload: () -> Unit,
    onInstall: (File) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is AutoUpdateState.Downloading -> {
                    // Pulsing animation while downloading
                    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.92f,
                        targetValue = 1.08f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "pulseScale",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .scale(pulseScale)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_update),
                                contentDescription = "Downloading",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Downloading update…",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val total = state.total
                            val receivedMb = state.received.toDouble() / (1024.0 * 1024.0)
                            val totalMb = (total ?: 0L).toDouble() / (1024.0 * 1024.0)
                            val progressText = if (total != null && total > 0) {
                                val percent = ((state.received.toDouble() / total.toDouble()) * 100).toInt()
                                "%.1f / %.1f MB (%d%%)".format(receivedMb, totalMb, percent)
                            } else {
                                "%.1f MB received".format(receivedMb)
                            }
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    val total = state.total
                    if (total != null && total > 0) {
                        val fraction = (state.received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = tween(Motion.Content),
                            label = "fractionAnim",
                        )
                        LinearWavyProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                        )
                    } else {
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                        )
                    }
                }

                is AutoUpdateState.Ready -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ready to Install",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "APK verified and ready.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { onInstall(state.file) },
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("Install")
                        }
                    }
                }

                is AutoUpdateState.Failed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Download failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = state.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = onDownload, shapes = ButtonDefaults.shapes()) {
                            Text("Retry")
                        }
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New Version Available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Size: ${(release.apkSize.toDouble() / (1024.0 * 1024.0)).toInt()} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = onDownload, shapes = ButtonDefaults.shapes()) {
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}
