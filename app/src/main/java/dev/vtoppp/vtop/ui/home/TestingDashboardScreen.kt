package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.toPath
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AgendaRow
import dev.vtoppp.vtop.data.CalendarAgenda
import dev.vtoppp.vtop.data.CalendarDayEvent
import dev.vtoppp.vtop.data.ClassScheduleItem
import dev.vtoppp.vtop.data.DaySchedule
import dev.vtoppp.vtop.data.PillKind
import dev.vtoppp.vtop.data.PillState
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.data.UserProfile
import dev.vtoppp.vtop.ui.theme.AppPalette
import dev.vtoppp.vtop.ui.theme.Motion
import dev.vtoppp.vtop.ui.theme.StatusColors
import kotlin.math.absoluteValue

data class SubjectAttendance(
    val courseCode: String,
    val courseTitle: String,
    val percentage: Int,
    val attendedClasses: Int,
    val totalClasses: Int,
    val safeBunks: Int,
    val examTarget: String = "CAT-1",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TestingDashboardScreen(
    authorizedId: String,
    onOpenService: (MenuItem) -> Unit,
    pill: PillState = PillState(PillKind.Ready, "Connected"),
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
    sections: List<Pair<String, List<MenuItem>>> = emptyList(),
    userProfile: UserProfile? = null,
) {
    var githubPingDone by remember { mutableStateOf(false) }
    var githubPingSuccess by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(400)
        val ok = pingGitHubRelease()
        githubPingSuccess = ok
        githubPingDone = true
    }
    val liveProfile = userProfile ?: UserDatabase.profileFlow.collectAsState().value
    val liveCalendarEvents by UserDatabase.calendarEventsFlow.collectAsState()

    val storedTimetable by UserDatabase.timetableFlow.collectAsState()
    val liveSessions by UserDatabase.attendanceSessionsFlow.collectAsState()
    val scheduleDays = remember(storedTimetable, liveSessions) {
        UserDatabase.buildScheduleDays(storedTimetable, liveSessions)
    }

    val horizontalPagerState = rememberPagerState(
        initialPage = 1.coerceAtMost((scheduleDays.size - 1).coerceAtLeast(0)),
        pageCount = { scheduleDays.size },
    )

    val liveAttendance by UserDatabase.attendanceFlow.collectAsState()
    val attendanceSubjects = remember(liveAttendance) {
        if (liveAttendance.isNotEmpty()) {
            liveAttendance.map { entry ->
                // ponytail: calculate safe bunks to maintain 75% attendance threshold
                val safeBunks = ((entry.attendedClasses - 0.75 * entry.totalClasses) / 0.75).toInt().coerceAtLeast(0)
                SubjectAttendance(
                    courseCode = entry.courseCode,
                    courseTitle = entry.courseTitle,
                    percentage = entry.attendancePercentage.toInt(),
                    attendedClasses = entry.attendedClasses,
                    totalClasses = entry.totalClasses,
                    safeBunks = safeBunks,
                    examTarget = "CAT-1",
                )
            }
        } else {
            listOf(
                SubjectAttendance("CSE3001", "Database Systems", 92, 33, 36, 8, "CAT-1"),
                SubjectAttendance("CSE2004", "Computation & Compiler", 95, 34, 36, 9, "CAT-1"),
                SubjectAttendance("CSE3003", "Operating System", 98, 35, 36, 10, "CAT-1"),
                SubjectAttendance("CSE3015", "AWS Cloud", 98, 33, 34, 10, "CAT-1"),
                SubjectAttendance("CSE2006", "Java Programming", 96, 23, 24, 6, "CAT-1"),
                SubjectAttendance("MAT2005", "Transform Techniques", 91, 20, 22, 4, "CAT-1"),
            )
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedNavTab by rememberSaveable { mutableIntStateOf(0) }
    var showGradesPage by rememberSaveable { mutableStateOf(false) }
    var isNavigatingService by remember { mutableStateOf(false) }

    LaunchedEffect(isNavigatingService) {
        if (isNavigatingService) {
            delay(3000L)
            isNavigatingService = false
        }
    }

    val isSyncing by UserDatabase.isSyncingFlow.collectAsState()
    val isProcessing = pill.kind == PillKind.Busy || !githubPingDone || isNavigatingService || isSyncing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(), // Directly beneath system top navbar (time --- signal battery)
    ) {
        BackHandler(enabled = selectedNavTab != 0 || showGradesPage) {
            if (showGradesPage) showGradesPage = false else selectedNavTab = 0
        }
        AnimatedContent(
            targetState = when {
                selectedNavTab == 1 -> "calendar"
                showGradesPage -> "grades"
                selectedNavTab == 2 -> "marks"
                selectedNavTab == 3 -> "settings"
                else -> "timeline"
            },
            transitionSpec = {
                fadeIn(tween(Motion.Content)) togetherWith fadeOut(tween(Motion.Quick))
            },
            label = "navContent",
        ) { contentKey ->
        when (contentKey) {
            "calendar" -> AcademicCalendarPage(events = liveCalendarEvents, onRefresh = onRefresh)
            "grades" -> GradeListPage(
                onBack = { showGradesPage = false },
                onRefresh = onRefresh,
            )
            "marks" -> MarksGradesPage(
                authorizedId = authorizedId,
                onOpenGrades = { showGradesPage = true },
                onRefresh = onRefresh,
                marksNotice = marksNotice,
                onMarksNoticeShown = onMarksNoticeShown,
            )
            "settings" -> SettingsPage(
                authorizedId = authorizedId,
                userProfile = userProfile,
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
                onCheckUpdate = onCheckUpdate,
                onLockApp = onLockApp,
                onSignOut = onSignOut,
                onRefresh = onRefresh,
            )
            else -> Column(modifier = Modifier.fillMaxSize()) {
            // Squiggly loading line only appears when processing networks or in transitions
            SyncingBar(visible = isProcessing)

            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState(),
                isRefreshing = isSyncing,
                onRefresh = onRefresh,
                indicator = {},
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header: Greeting on Left + Organic Question Card on Right
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: Greeting with First Name & Lastname (65% width)
                        Column(modifier = Modifier.weight(0.65f)) {
                            val greeting = SessionGreeting.greeting
                            val fontIndex = SessionGreeting.fontIndex
                            val typography = GreetingFontStyles[fontIndex.coerceIn(GreetingFontStyles.indices)]

                            val parsedName = remember(liveProfile?.name, authorizedId) {
                                parseDisplayName(liveProfile?.name, authorizedId)
                            }
                            val displayName = parsedName.firstName

                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = typography.fontFamily,
                                fontWeight = typography.greetingWeight,
                                letterSpacing = typography.letterSpacing,
                            )
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedNavTab = 3 }
                                    .padding(top = 1.dp),
                            ) {
                                val textMeasurer = rememberTextMeasurer()
                                val density = LocalDensity.current
                                val maxWidthPx = constraints.maxWidth
                                val spacerPx = with(density) { 6.dp.roundToPx() }

                                val baseFirstStyle = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = typography.nameWeight,
                                    fontFamily = typography.fontFamily,
                                    letterSpacing = typography.letterSpacing,
                                )
                                val secondaryStyle = MaterialTheme.typography.bodyMedium.copy(
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
                                    isBusy = pill.kind == PillKind.Busy,
                                    isError = pill.kind == PillKind.Error,
                                    label = pill.label.ifBlank { "Bridging VTOP…" },
                                    readyLabel = "VTOP bridged",
                                    onClick = onBridgeRetry,
                                )

                                // 2. GitHub Release Update Checker Pill
                                FluidStatusPill(
                                    isReady = githubPingDone,
                                    isBusy = !githubPingDone,
                                    isError = !githubPingSuccess,
                                    label = "Pinging GitHub…",
                                    readyLabel = "Up to date",
                                    readyIcon = R.drawable.ic_check,
                                    onClick = {
                                        if (githubPingDone) {
                                            scope.launch {
                                                githubPingDone = false
                                                val ok = pingGitHubRelease()
                                                githubPingSuccess = ok
                                                githubPingDone = true
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Right: Poll with question text above the shape (35% width)
                        OrganicQuestionCard(modifier = Modifier.weight(0.35f))
                    }
                }

                // Timeline Header: Title with Day & Date directly next to it
                item {
                    val currentDay = scheduleDays.getOrNull(horizontalPagerState.currentPage)
                        ?: DaySchedule(dayName = "No timetable", dateLabel = "cached", classes = emptyList())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Timeline",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(10.dp))
                        val isToday = currentDay.dayName == "Today"
                        val badgeColor by animateColorAsState(
                            targetValue = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(Motion.Color),
                            label = "dayBadge",
                        )
                        val badgeContent by animateColorAsState(
                            targetValue = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            animationSpec = tween(Motion.Color),
                            label = "dayBadgeContent",
                        )
                        val badgeContentDim by animateColorAsState(
                            targetValue = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(Motion.Color),
                            label = "dayBadgeDim",
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeColor,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                Text(
                                    text = currentDay.dayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeContent,
                                )
                                Text(
                                    text = " · ${currentDay.dateLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeContentDim,
                                )
                                AnimatedVisibility(
                                    visible = currentDay.classes.isNotEmpty(),
                                    enter = fadeIn(tween(Motion.Quick)) + scaleIn(tween(Motion.Content)),
                                    exit = fadeOut(tween(Motion.Fast)) + scaleOut(tween(Motion.Fast)),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.padding(start = 12.dp),
                                    ) {
                                        Text(
                                            text = "${currentDay.classes.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Carousel: Horizontal swipe between days with layered card deck overshadowing neighbors
                if (scheduleDays.isNotEmpty()) {
                    item {
                        DayCarouselSchedule(
                            days = scheduleDays,
                            pagerState = horizontalPagerState,
                        )
                    }
                }

                // Attendance Section: subject cycle card under the Timeline
                item {
                    AttendanceSection(
                        subjects = attendanceSubjects,
                        onOpenAttendance = {
                            isNavigatingService = true
                            val attendanceItem = sections.flatMap { it.second }.find {
                                it.label.contains("Attendance", ignoreCase = true)
                            } ?: MenuItem("Academics", "Attendance", "academics/common/Attendance")
                            onOpenService(attendanceItem)
                        },
                    )
                }


            }
            }
        }
        }
        }

        // Sticky floating bottom navigation bar
        // ponytail: sticky bottom container with soft gradient scrim prevents navbar from floating adrift
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(bottom = 12.dp, top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            FloatingDashboardNavBar(
                selectedTab = selectedNavTab,
                onTabSelected = { tab ->
                    selectedNavTab = tab
                    when (tab) {
                        0 -> scope.launch { listState.animateScrollToItem(0) }
                        else -> Unit
                    }
                },
            )
    }
}


}
/**
 * Organic shape card holding an intriguing campus question.
 * Maintains its 1:1 aspect ratio so the shape is never distorted, covering the question background.
 * Shows only the question in the box; user can tap it to cycle questions and shapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingDashboardNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 28.dp)
            .height(64.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val items = listOf(
                Pair(R.drawable.ic_home, "Home"),
                Pair(R.drawable.ic_calendar, "Academic Calendar"),
                Pair(R.drawable.ic_grade, "Marks"),
                Pair(R.drawable.ic_settings, "Settings"),
            )

            items.forEachIndexed { index, (iconRes, label) ->
                val isSelected = selectedTab == index
                val animBg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "tab_bg_$index",
                )
                val animTint by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tab_tint_$index",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "tab_icon_$index",
                )

                Surface(
                    onClick = { onTabSelected(index) },
                    shape = CircleShape,
                    color = animBg,
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = label,
                            tint = animTint,
                            modifier = Modifier.size(24.dp).graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                        )
                    }
                }
            }
        }
    }
}


// ponytail: fast lightweight ping to GitHub releases API; checks connectivity and updates
internal suspend fun pingGitHubRelease(): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val client = OkHttpClient.Builder()
            .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://api.github.com/repos/vtop-xceed/vtop-android/releases/latest")
            .header("User-Agent", "vtop-android")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            response.isSuccessful || response.code == 404
        }
    }.getOrDefault(true)
}

/**
 * Small fluid pill showing bridge status: every ongoing stage stays expanded with its
 * label; on success or full failure the result label stays visible for 3 seconds,
 * then collapses to a green or red dot. Tapping the dot re-expands the label for 4s.
 */
@Composable
internal fun FluidStatusPill(
    isReady: Boolean,
    label: String,
    readyLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    isError: Boolean = false,
    readyIcon: Int = R.drawable.ic_check,
) {
    var userToggled by remember { mutableStateOf(false) }
    // Terminal states (ready or failure) keep the result label visible for 3s,
    // then collapse to a colored dot. Ongoing stages always stay expanded.
    var terminalVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isReady, isError) {
        if (isReady || isError) {
            terminalVisible = true
            delay(3000L)
            terminalVisible = false
        } else {
            terminalVisible = false
        }
    }

    LaunchedEffect(userToggled) {
        if (userToggled) {
            delay(4000L)
            userToggled = false
        }
    }

    val showExpanded = (!isReady && !isError) || terminalVisible || userToggled

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            isReady && !userToggled -> StatusColors.Success
            isReady && userToggled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(Motion.Color, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
        label = "pillBg",
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.onErrorContainer
            isReady && !userToggled -> Color.White
            isReady && userToggled -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(Motion.Color, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
        label = "pillContent",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                onClickLabel = if (isReady) readyLabel else label,
            ) {
                if (isReady || isError) {
                    userToggled = !userToggled
                }
                onClick()
            },
    ) {
        AnimatedContent(
            targetState = showExpanded,
            transitionSpec = {
                fadeIn(tween(Motion.Quick)) + scaleIn(tween(Motion.Quick)) togetherWith
                    fadeOut(tween(Motion.Fast)) + scaleOut(tween(Motion.Fast))
            },
            label = "pillContent",
        ) { expanded ->
        if (expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                if (isReady) {
                    Icon(
                        painter = painterResource(readyIcon),
                        contentDescription = readyLabel,
                        tint = contentColor,
                        modifier = Modifier.size(10.dp),
                    )
                } else if (isBusy || !isReady) {
                    val transition = rememberInfiniteTransition(label = "pillPulse")
                    val alpha by transition.animateFloat(
                        initialValue = Motion.PulseMinAlpha,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(Motion.PulseMs, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "pillPulseAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer { this.alpha = alpha }
                            .clip(CircleShape)
                            .background(if (isError) MaterialTheme.colorScheme.error else StatusColors.Warning),
                    )
                }

                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isReady) readyLabel else label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        } else {
            // Settled dot: green for ready, red for failure (background is the dot).
            Box(modifier = Modifier.size(8.dp))
        }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
internal fun OrganicQuestionCard(
    modifier: Modifier = Modifier,
) {
    var questionIndex by remember { mutableIntStateOf(SessionGreeting.questionIndex) }
    var shapeIndex by remember { mutableIntStateOf(SessionGreeting.shapeIndex) }
    var previousShapeIndex by remember { mutableIntStateOf(shapeIndex) }
    val currentQuestion = IntriguingQuestions[questionIndex.coerceIn(IntriguingQuestions.indices)]
    val morphProgress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(shapeIndex) {
        if (shapeIndex == previousShapeIndex) return@LaunchedEffect
        morphProgress.snapTo(0f)
        morphProgress.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        )
        previousShapeIndex = shapeIndex
    }
    val morph = remember(previousShapeIndex, shapeIndex) {
        androidx.graphics.shapes.Morph(
            QuestionCardShapes[previousShapeIndex.coerceIn(QuestionCardShapes.indices)],
            QuestionCardShapes[shapeIndex.coerceIn(QuestionCardShapes.indices)],
        )
    }
    val rawPath = remember { androidx.compose.ui.graphics.Path() }
    val shapeColor = MaterialTheme.colorScheme.secondaryContainer
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "questionPress",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize(align = Alignment.CenterEnd, unbounded = true)
                .requiredSize(118.dp)
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .drawBehind {
                    rawPath.rewind()
                    morph.toPath(morphProgress.value, rawPath)
                    val matrix = androidx.compose.ui.graphics.Matrix()
                    matrix.scale(size.width, size.height)
                    rawPath.transform(matrix)
                    val bounds = rawPath.getBounds()
                    rawPath.translate(
                        androidx.compose.ui.geometry.Offset(
                            size.width / 2f - bounds.center.x,
                            size.height / 2f - bounds.center.y,
                        ),
                    )
                    drawPath(rawPath, shapeColor)
                }
                .clickable(interactionSource = interactionSource, indication = null) {
                    // user presses it if it feels intriguing
                    questionIndex = (questionIndex + 1) % IntriguingQuestions.size
                    shapeIndex = (shapeIndex + 1) % QuestionCardShapes.size
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentQuestion,
                    transitionSpec = {
                        (slideInVertically(tween(Motion.Quick)) { it / 3 } + fadeIn(tween(Motion.Quick))) togetherWith
                            (slideOutVertically(tween(Motion.Fast)) { -it / 3 } + fadeOut(tween(Motion.Fast)))
                    },
                    label = "questionText",
                ) { question ->
                    Text(
                        text = question,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

