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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import dev.vtoppp.vtop.ui.theme.AppPalette
import dev.vtoppp.vtop.ui.theme.Motion
import dev.vtoppp.vtop.ui.theme.StatusColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val ready = state as? HomeUiState.Ready
    var showAccountDialog by remember { mutableStateOf(false) }
    var showTestingDashboard by rememberSaveable { mutableStateOf(true) }

    if (ready != null && showTestingDashboard) {
        TestingDashboardScreen(
            authorizedId = ready.authorizedId,
            onOpenService = onOpen,
            onSwitchToClassic = { showTestingDashboard = false },
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
            sections = ready.sections,
        )
        return
    }

    //: system back inside a section view returns to the menu instead of
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
                if (state is HomeUiState.Ready) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary,
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "VTOP",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 0.5.sp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "STUDENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        letterSpacing = 0.5.sp,
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 1.dp),
                            ) {
                                HeaderStatusDot(pill)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = when (pill.kind) {
                                        PillKind.Ready -> "Session active"
                                        PillKind.Busy -> "Syncing…"
                                        PillKind.Error -> "Offline"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = when (state) {
                            is HomeUiState.SectionReady -> state.title
                            is HomeUiState.Loading -> lastSectionTitle ?: "Loading…"
                            is HomeUiState.Failure -> lastSectionTitle ?: "VTOP"
                            else -> "VTOP"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                if (
                    state is HomeUiState.SectionReady ||
                    state is HomeUiState.Failure ||
                    (state is HomeUiState.Loading && lastSectionTitle != null)
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
                if (state !is HomeUiState.Ready) {
                    SessionPill(pill)
                }
                if (ready != null) {
                    Surface(
                        onClick = { showTestingDashboard = true },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.height(36.dp).padding(end = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "✨ New UI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Surface(
                    onClick = onCheckUpdate,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.height(36.dp).padding(end = if (ready != null) 4.dp else 12.dp),
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
                if (ready != null) {
                    Surface(
                        onClick = { showAccountDialog = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.padding(end = 12.dp, start = 4.dp).size(38.dp),
                        shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                ready.authorizedId.firstOrNull()?.uppercase() ?: "V",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            },
        )

        // Google Account profile popup dialog
        if (showAccountDialog && ready != null) {
            AccountDialog(
                initial = ready.authorizedId.firstOrNull()?.uppercase() ?: "V",
                title = ready.authorizedId,
                subtitle = "Student Account · Signed in",
                currentPalette = currentPalette,
                onPaletteChange = onPaletteChange,
                onLockApp = {
                    showAccountDialog = false
                    onLockApp()
                },
                onSignOut = {
                    showAccountDialog = false
                    onSignOut()
                },
                onDismiss = { showAccountDialog = false },
            )
        }

        if (staleNotice != null) {
            //: the notice is READ here, not consumed the same frame it
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
        PillKind.Ready -> StatusColors.Success
        PillKind.Busy -> StatusColors.Warning
        PillKind.Error -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 5.dp)
            // Smooth the width jumps as labels change mid-flight.
            .animateContentSize(tween(Motion.Content)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    // Pulsing dot: animation runs below the recomposition layer via
    // graphicsLayer; the branch only recomposes when the state kind changes.
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
private fun HeaderStatusDot(pill: PillState) {
    val dotColor = when (pill.kind) {
        PillKind.Ready -> StatusColors.Success
        PillKind.Busy -> StatusColors.Warning
        PillKind.Error -> MaterialTheme.colorScheme.error
    }
    PulseDot(pulse = pill.pulse, color = dotColor, size = 7.dp)
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

@Composable
private fun MenuList(
    sections: List<Pair<String, List<MenuItem>>>,
    onOpen: (MenuItem) -> Unit,
) {
    if (sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No menu items available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { sections.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal scrollable tabs matching each heading
        SecondaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            divider = {},
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(pagerState.currentPage, matchContentSize = false),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            sections.forEachIndexed { index, (sectionName, _) ->
                val selected = pagerState.currentPage == index
                Tab(
                    selected = selected,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = sectionName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }

        // Horizontal Pager: swipe left and right between category pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().weight(1f),
        ) { pageIndex ->
            val (sectionName, items) = sections[pageIndex]

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header-$sectionName") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sectionName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = "${items.size} services",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                items(items, key = { "${it.section}-${it.label}" }) { item ->
                    Card(
                        onClick = { onOpen(item) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(categoryIcon(item.section, item.label)),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun categoryIcon(sectionName: String, label: String): Int {
    val text = "$sectionName $label".lowercase()
    return when {
        "academic" in text || "timetable" in text || "attendance" in text || "course" in text || "curriculum" in text -> R.drawable.ic_school
        "exam" in text || "grade" in text || "mark" in text || "coe" in text -> R.drawable.ic_grade
        "payment" in text || "finance" in text || "fee" in text || "receipt" in text || "bank" in text -> R.drawable.ic_payments
        "hostel" in text || "mess" in text || "room" in text -> R.drawable.ic_home
        "profile" in text || "student" in text || "credential" in text || "spot-light" in text -> R.drawable.ic_person
        else -> R.drawable.ic_launcher_foreground
    }
}
