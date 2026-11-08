package dev.vtoppp.vtop.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import dev.vtoppp.vtop.ui.theme.AppPalette
import dev.vtoppp.vtop.ui.theme.Motion
import dev.vtoppp.vtop.ui.theme.StatusColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onLockApp: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onBridgeRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    marksNotice: String? = null,
    onMarksNoticeShown: () -> Unit = {},
    currentPalette: AppPalette = AppPalette.Dynamic,
    onPaletteChange: (AppPalette) -> Unit = {},
    themeMode: dev.vtoppp.vtop.ui.theme.ThemeMode = dev.vtoppp.vtop.ui.theme.ThemeMode.DARK,
    onThemeModeChange: (dev.vtoppp.vtop.ui.theme.ThemeMode) -> Unit = {},
    blurEnabled: Boolean = true,
    onBlurEnabledChange: (Boolean) -> Unit = {},
    lockEnabled: Boolean = true,
    onLockEnabledChange: (Boolean) -> Unit = {},
    autoUpdateEnabled: Boolean = true,
    onAutoUpdateEnabledChange: (Boolean) -> Unit = {},
) {
    if (state is HomeUiState.Ready) {
        TestingDashboardScreen(
            authorizedId = state.authorizedId,
            onOpenService = onOpen,
            pill = pill,
            onCheckUpdate = onCheckUpdate,
            onLockApp = onLockApp,
            onSignOut = onSignOut,
            onBridgeRetry = onBridgeRetry,
            onRefresh = onRefresh,
            marksNotice = marksNotice,
            onMarksNoticeShown = onMarksNoticeShown,
            currentPalette = currentPalette,
            onPaletteChange = onPaletteChange,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            blurEnabled = blurEnabled,
            onBlurEnabledChange = onBlurEnabledChange,
            lockEnabled = lockEnabled,
            onLockEnabledChange = onLockEnabledChange,
            autoUpdateEnabled = autoUpdateEnabled,
            onAutoUpdateEnabledChange = onAutoUpdateEnabledChange,
            sections = state.sections,
        )
        return
    }

    // system back inside a section view returns to the menu instead of
    // exiting the app mid-fetch.
    BackHandler(
        enabled = state is HomeUiState.SectionReady || state is HomeUiState.Loading || state is HomeUiState.Failure,
        onBack = onBackToMenu,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
            title = {
                AnimatedContent(
                    targetState = when (state) {
                        is HomeUiState.SectionReady -> state.title
                        is HomeUiState.Loading -> lastSectionTitle ?: "Loading…"
                        is HomeUiState.Failure -> lastSectionTitle ?: "VTOP"
                        else -> "VTOP"
                    },
                    transitionSpec = {
                        fadeIn(tween(Motion.Quick)) togetherWith fadeOut(tween(Motion.Fast))
                    },
                    label = "appBarTitle",
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                val showBack = state is HomeUiState.SectionReady ||
                    state is HomeUiState.Failure ||
                    (state is HomeUiState.Loading && lastSectionTitle != null)
                AnimatedVisibility(
                    visible = showBack,
                    enter = fadeIn(tween(Motion.Quick)) + scaleIn(tween(Motion.Content)),
                    exit = fadeOut(tween(Motion.Fast)) + scaleOut(tween(Motion.Fast)),
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = onBackToMenu,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_back),
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                SessionPill(pill)
                Surface(
                    onClick = onCheckUpdate,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.height(36.dp).padding(end = 12.dp),
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_update),
                            contentDescription = "Check for updates",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Update",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
        )

        if (staleNotice != null) {
            // the notice is READ here, not consumed the same frame it
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
            enter = Motion.BannerEnter,
            exit = Motion.BannerExit,
        ) {
            Text(
                text = bannerText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(vertical = 6.dp, horizontal = 16.dp),
            )
        }

        val shellBranch = when (state) {
            is HomeUiState.Loading -> "loading"
            is HomeUiState.SectionReady -> "ready"
            is HomeUiState.Failure -> "failure"
            else -> "idle"
        }
        AnimatedContent(
            targetState = shellBranch,
            transitionSpec = {
                fadeIn(tween(Motion.Content)) togetherWith fadeOut(tween(Motion.Quick))
            },
            label = "shellBranch",
        ) { target ->
            when (target) {
                "loading" -> Column(
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
                "ready" -> (state as? HomeUiState.SectionReady)?.let {
                    SectionScreen(
                        html = it.html,
                        cookies = it.cookies,
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: Unit
                "failure" -> (state as? HomeUiState.Failure)?.let {
                    FailureContent(state = it, onRetry = onRetry)
                } ?: Unit
                else -> Unit
            }
        }
    }
}

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
    val dotColor by animateColorAsState(
        targetValue = when (pill.kind) {
            PillKind.Ready -> StatusColors.Success
            PillKind.Busy -> StatusColors.Warning
            PillKind.Error -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(Motion.Color),
        label = "pillDot",
    )
    val animatedContainer by animateColorAsState(container, tween(Motion.Color), label = "pillContainer")
    Row(
        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(animatedContainer).padding(horizontal = 10.dp, vertical = 5.dp)
            .animateContentSize(tween(Motion.Content)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(pulse = pill.pulse, color = dotColor, size = 8.dp)
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

@Composable
private fun FailureContent(
    state: HomeUiState.Failure,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
                modifier = Modifier.fillMaxWidth().padding(20.dp),
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
