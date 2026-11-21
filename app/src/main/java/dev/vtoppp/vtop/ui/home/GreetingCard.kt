package dev.vtoppp.vtop.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.PillKind
import dev.vtoppp.vtop.data.PillState

/** Left half of the dashboard header: greeting, name (auto-scaled to fit) and the two status pills. */
@Composable
internal fun GreetingCard(
    greeting: String,
    typography: GreetingTypography,
    parsedName: ParsedDisplayName,
    pill: PillState,
    githubPingDone: Boolean,
    githubPingSuccess: Boolean,
    onBridgeRetry: () -> Unit,
    onGithubPing: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = parsedName.firstName

    Column(modifier = modifier) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.titleSmallEmphasized.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = typography.fontFamily,
            fontWeight = typography.greetingWeight,
            letterSpacing = typography.letterSpacing,
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSettingsClick() }
                .padding(top = 1.dp),
        ) {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val maxWidthPx = constraints.maxWidth
            val spacerPx = with(density) { 6.dp.roundToPx() }

            val baseFirstStyle = MaterialTheme.typography.displayLargeEmphasized.copy(
                fontSize = 36.sp,
                fontWeight = typography.nameWeight,
                fontFamily = typography.fontFamily,
                letterSpacing = typography.letterSpacing,
            )
            val secondaryStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 18.sp,
                fontWeight = typography.greetingWeight,
                fontFamily = typography.fontFamily,
                letterSpacing = typography.letterSpacing,
            )

            // Measure first name: if it exceeds available width on wide fonts (like Monospace),
            // scale font size down so it NEVER gets ..ed
            val firstMeasure = textMeasurer.measure(
                text = displayName,
                style = baseFirstStyle,
                maxLines = 1,
            )
            val firstWidthPx = firstMeasure.size.width

            val (effectiveFirstStyle, effectiveFirstWidthPx) = if (firstWidthPx > maxWidthPx && maxWidthPx > 0) {
                val scale = (maxWidthPx.toFloat() / firstWidthPx.toFloat()).coerceIn(0.6f, 1f)
                val scaled = baseFirstStyle.copy(fontSize = baseFirstStyle.fontSize * scale)
                scaled to maxWidthPx
            } else {
                baseFirstStyle to firstWidthPx
            }

            val availableForSecondary = maxWidthPx - effectiveFirstWidthPx - spacerPx

            // If name is too long, hide full middle & last name to use initials (e.g. Mayank K.)
            val resolvedSecondary = when {
                parsedName.secondaryName == null -> null
                availableForSecondary <= 0 -> null
                else -> {
                    val fullWidth = textMeasurer.measure(
                        text = parsedName.secondaryName,
                        style = secondaryStyle,
                        maxLines = 1,
                    ).size.width
                    if (fullWidth <= availableForSecondary) {
                        parsedName.secondaryName
                    } else {
                        val initials = parsedName.secondaryInitials
                        if (initials != null) {
                            val initialsWidth = textMeasurer.measure(
                                text = initials,
                                style = secondaryStyle,
                                maxLines = 1,
                            ).size.width
                            if (initialsWidth <= availableForSecondary) {
                                initials
                            } else null
                        } else null
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = displayName,
                    style = effectiveFirstStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                if (resolvedSecondary != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = resolvedSecondary,
                        style = secondaryStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }

        // Fluid status pills below the name: VTOP session bridging & GitHub release ping
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 5.dp),
        ) {
            // 1. VTOP Session Bridging Pill / Green Check Dot
            val isVtopReady = pill.kind == PillKind.Ready
            FluidStatusPill(
                isReady = isVtopReady,
                isError = pill.kind == PillKind.Error,
                label = pill.label.ifBlank { "Bridging VTOP…" },
                readyLabel = "VTOP bridged",
                onClick = onBridgeRetry,
            )

            // 2. GitHub Release Update Checker Pill
            FluidStatusPill(
                isReady = githubPingDone,
                isError = !githubPingSuccess,
                label = "Pinging GitHub…",
                readyLabel = "Up to date",
                readyIcon = R.drawable.ic_check,
                onClick = { if (githubPingDone) onGithubPing() },
            )
        }
    }
}
