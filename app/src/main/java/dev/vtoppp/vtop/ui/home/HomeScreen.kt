package dev.vtoppp.vtop.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.PillKind
import dev.vtoppp.vtop.data.PillState
import dev.vtoppp.vtop.data.VtopError
import dev.vtoppp.vtop.ui.section.SectionScreen
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpen: (MenuItem) -> Unit,
    onBackToMenu: () -> Unit,
    lastSectionTitle: String? = null,
    onRetry: () -> Unit = {},
    pill: PillState = PillState(PillKind.Busy, "Connecting…", pulse = true),
    staleNotice: String? = null,
    onStaleNoticeShown: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
) {
    val ready = state as? HomeUiState.Ready

    // T18.1: system back inside a section view returns to the menu instead of
    // exiting the app mid-fetch.
    BackHandler(
        enabled = state is HomeUiState.SectionReady || state is HomeUiState.Loading || state is HomeUiState.Failure,
        onBack = onBackToMenu,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    when (state) {
                        is HomeUiState.SectionReady -> state.title
                        is HomeUiState.Ready -> "VTOP Console"
                        is HomeUiState.Loading -> lastSectionTitle ?: "Loading…"
                        is HomeUiState.Failure -> lastSectionTitle ?: "VTOP Console"
                        HomeUiState.Idle -> "Loading…"
                    },
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                if (
                    state is HomeUiState.SectionReady ||
                    state is HomeUiState.Failure ||
                    (state is HomeUiState.Loading && lastSectionTitle != null)
                ) {
                    TextButton(onClick = onBackToMenu) { Text("Menu") }
                }
            },
            actions = {
                SessionPill(pill)
                TextButton(onClick = onCheckUpdate) { Text("Update") }
                ready?.let {
                    Text(
                        it.authorizedId,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
            },
        )

        if (staleNotice != null) {
            // T18.2: the notice is READ here, not consumed the same frame it
            // appears — it stays ~5s so the reason (offline, re-authing) is
            // actually perceivable, then auto-dismisses.
            LaunchedEffect(staleNotice) {
                delay(5_000)
                onStaleNoticeShown()
            }
        }

        // One shared banner surface: an explicit refresh-failure notice wins
        // over the plain cached-age badge, animating in/out via layout.
        val bannerText = staleNotice
            ?: (state as? HomeUiState.SectionReady)?.takeIf { it.fromCache }?.let {
                "cached · ${it.cacheAge ?: "offline copy"}"
            }
        AnimatedVisibility(
            visible = bannerText != null,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
        ) {
            Text(
                text = bannerText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(vertical = 6.dp, horizontal = 16.dp),
            )
        }

        when (state) {
            is HomeUiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LoadingIndicator(modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = lastSectionTitle?.let { "Loading $it…" } ?: "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is HomeUiState.SectionReady -> SectionScreen(
                html = state.html,
                cookies = state.cookies,
                modifier = Modifier.fillMaxSize(),
            )
            is HomeUiState.Ready -> MenuList(sections = state.sections, onOpen = onOpen)
            is HomeUiState.Failure -> FailureContent(state = state, onRetry = onRetry)
            HomeUiState.Idle -> Unit
        }
    }
}

/** Self-verifying small status chip; colors map each state to a theme container. */
@Composable
private fun SessionPill(pill: PillState) {
    val (container, content) = when (pill.kind) {
        PillKind.Ready -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        PillKind.Busy -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        PillKind.Error -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
    val dotColor = when (pill.kind) {
        PillKind.Ready -> Color(0xFF2E7D32)
        PillKind.Busy -> Color(0xFFB26A00)
        PillKind.Error -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            // Smooth the width jumps as labels change mid-flight.
            .animateContentSize(tween(180)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    // Pulsing dot: animation runs below the recomposition layer via
    // graphicsLayer; the branch only recomposes when the state kind changes.
    Box(
        modifier = if (pill.pulse) {
            val transition = rememberInfiniteTransition(label = "pillDot")
            val alpha by transition.animateFloat(
                initialValue = PULSE_ALPHA_MIN,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(PULSE_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pillDotAlpha",
            )
            Modifier
                .size(8.dp)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .background(dotColor)
        } else {
            Modifier
                .size(8.dp)
                 .clip(CircleShape)
                 .background(dotColor)
        },
    )
    Spacer(Modifier.width(6.dp))
    Text(
        pill.label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        modifier = Modifier.widthIn(max = 160.dp),
    )
    }
}

private const val PULSE_MS = 700
private const val PULSE_ALPHA_MIN = 0.3f

@Composable
private fun FailureContent(
    state: HomeUiState.Failure,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = failureMessage(state.error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

private fun failureMessage(error: VtopError): String = when (error) {
    VtopError.SessionExpired -> "Session expired — sign in again"
    VtopError.Network -> "Check your internet connection"
    else -> "Could not load this section"
}

@Composable
private fun MenuList(
    sections: List<Pair<String, List<MenuItem>>>,
    onOpen: (MenuItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sections.forEach { (sectionName, items) ->
            item(key = "h-$sectionName") {
                Text(
                    sectionName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 2.dp),
                )
            }
            items(items, key = { "${it.section}-${it.label}" }) { item ->
                Card(
                    onClick = { onOpen(item) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}
