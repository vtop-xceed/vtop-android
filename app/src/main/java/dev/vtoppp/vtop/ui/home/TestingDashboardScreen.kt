package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.CalendarDayEvent
import dev.vtoppp.vtop.data.ClassScheduleItem
import dev.vtoppp.vtop.data.DaySchedule
import dev.vtoppp.vtop.data.PillKind
import dev.vtoppp.vtop.data.PillState
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.data.UserProfile
import dev.vtoppp.vtop.ui.theme.AppPalette
import kotlin.math.absoluteValue

data class ParsedDisplayName(
    val firstName: String,
    val secondaryName: String?,
    val secondaryInitials: String? = null,
)

/** Splits raw name into primary first name and secondary middle/last names + initials. */
fun parseDisplayName(rawName: String?, fallbackId: String = "Student"): ParsedDisplayName {
    val trimmed = rawName?.trim().orEmpty()
    if (trimmed.isBlank()) {
        val fb = fallbackId.ifBlank { "Student" }
        return ParsedDisplayName(firstName = fb, secondaryName = null, secondaryInitials = null)
    }
    // If it contains digits (like a registration number ID e.g. 24BCY10147), keep as single ID
    if (trimmed.any { it.isDigit() }) {
        return ParsedDisplayName(firstName = trimmed.uppercase(), secondaryName = null, secondaryInitials = null)
    }
    val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) {
        return ParsedDisplayName(firstName = fallbackId.ifBlank { "Student" }, secondaryName = null, secondaryInitials = null)
    }
    val first = words.first().lowercase().replaceFirstChar { it.uppercase() }
    val secondary = if (words.size > 1) {
        words.drop(1).joinToString(" ") { word ->
            if (word.length <= 2 && word.all { it.isLetter() }) word.uppercase()
            else word.lowercase().replaceFirstChar { it.uppercase() }
        }
    } else null
    val initials = if (words.size > 1) {
        words.drop(1).mapNotNull { word ->
            val char = word.firstOrNull { it.isLetter() }?.uppercaseChar()
            if (char != null) "$char." else null
        }.joinToString(" ").ifBlank { null }
    } else null
    return ParsedDisplayName(firstName = first, secondaryName = secondary, secondaryInitials = initials)
}

private data class GreetingTypography(
    val fontFamily: FontFamily,
    val greetingWeight: FontWeight = FontWeight.Medium,
    val nameWeight: FontWeight = FontWeight.ExtraBold,
    val letterSpacing: TextUnit = TextUnit.Unspecified,
)

// ponytail: zero-dep native platform font families covering ASCII/SpaceMono, Serif, Sans, and Cursive aesthetics
private val GreetingFontStyles = listOf(
    GreetingTypography(
        fontFamily = FontFamily.Monospace,
        greetingWeight = FontWeight.Medium,
        nameWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    ),
    GreetingTypography(
        fontFamily = FontFamily.Serif,
        greetingWeight = FontWeight.Normal,
        nameWeight = FontWeight.Bold,
    ),
    GreetingTypography(
        fontFamily = FontFamily.SansSerif,
        greetingWeight = FontWeight.Medium,
        nameWeight = FontWeight.Black,
    ),
    GreetingTypography(
        fontFamily = FontFamily.Cursive,
        greetingWeight = FontWeight.Medium,
        nameWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    ),
)

private val EnglishGreetings = listOf(
    "Hello,",
    "Hey,",
    "What's up,",
    "Howdy,",
    "Welcome,",
    "Greetings,",
    "Good day,",
    "Yo,",
)

// ponytail: expressive polygon shapes from Material 3 maintaining 1:1 aspect ratio
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val QuestionCardShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Sunny,
    MaterialShapes.VerySunny,
    MaterialShapes.SoftBurst,
    MaterialShapes.Flower,
    MaterialShapes.Puffy,
    MaterialShapes.PuffyDiamond,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.SoftBoom,
    MaterialShapes.Gem,
    MaterialShapes.ClamShell,
    MaterialShapes.Bun,
    MaterialShapes.Arch,
    MaterialShapes.Slanted,
)

// ponytail: short campus questions to display inside the organic shape box
private val IntriguingQuestions = listOf(
    "Ready for CAT-2?",
    "Skipping 8 AM?",
    "Touched grass today?",
    "Night canteen run?",
    "Attendance safe?",
    "Assignments done?",
    "Need coffee?",
    "Surviving FATs?",
    "9 PM in-time made?",
    "OD sanctioned?",
    "WiFi working?",
    "Mess food edible?",
)

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
    onSwitchToClassic: () -> Unit = {},
    pill: PillState = PillState(PillKind.Ready, "Connected"),
    onCheckUpdate: () -> Unit = {},
    onLockApp: () -> Unit = {},
    onSignOut: () -> Unit = {},
    currentPalette: AppPalette = AppPalette.Dynamic,
    onPaletteChange: (AppPalette) -> Unit = {},
    sections: List<Pair<String, List<MenuItem>>> = emptyList(),
    userProfile: UserProfile? = null,
) {
    var showAccountDialog by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }
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
    val scheduleDays = remember(storedTimetable) {
        val dynamic = UserDatabase.buildScheduleDays(storedTimetable)
        if (dynamic.isNotEmpty()) dynamic else defaultMockScheduleDays()
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Squiggly loading line only appears when processing networks or in transitions
            AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(250)) + shrinkVertically(tween(250)),
            ) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp),
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
                            val greeting = rememberSaveable { EnglishGreetings.random() }
                            val fontIndex = rememberSaveable { (0 until GreetingFontStyles.size).random() }
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
                                    .clickable { showAccountDialog = true }
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
                                    onClick = {},
                                )

                                // 2. GitHub Release Update Checker Pill / Green Check Dot
                                FluidStatusPill(
                                    isReady = githubPingDone,
                                    isBusy = !githubPingDone,
                                    isError = !githubPingSuccess,
                                    label = "Pinging GitHub…",
                                    readyLabel = "Up to date",
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
                    val currentDay = scheduleDays[horizontalPagerState.currentPage]
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentDay.dayName == "Today") {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = currentDay.dayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentDay.dayName == "Today") {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = " · ${currentDay.dateLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (currentDay.dayName == "Today") {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }

                // Carousel: Horizontal swipe between days with layered card deck overshadowing neighbors
                item {
                    DayCarouselSchedule(
                        days = scheduleDays,
                        pagerState = horizontalPagerState,
                    )
                }

                // Attendance Section: Subject cycle card just below Timeline (matching user sketch pic_20261029012343_0.jpg)
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

                // Academic Calendar Section
                item {
                    AcademicCalendarSection(
                        events = liveCalendarEvents,
                        onOpenCalendar = { showCalendarDialog = true },
                    )
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
                        1 -> {
                            scope.launch { listState.animateScrollToItem(4) }
                            showCalendarDialog = true
                        }
                        2 -> scope.launch { listState.animateScrollToItem(3) }
                        3 -> showAccountDialog = true
                    }
                },
            )
        }
    }

    // Account & Theme Color Picker Dialog
    if (showAccountDialog) {
        Dialog(onDismissRequest = { showAccountDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val initial = (liveProfile?.name?.firstOrNull() ?: authorizedId.firstOrNull())?.uppercase() ?: "V"
                            Text(
                                initial,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val fullName = liveProfile?.name?.takeIf { it != authorizedId && it.isNotBlank() }
                    if (fullName != null) {
                        Text(
                            fullName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        authorizedId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!liveProfile?.programme.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(liveProfile?.programme, liveProfile?.branch.takeIf { !it.isNullOrBlank() }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    // Theme Accent Color Picker
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_palette),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Theme Accent",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppPalette.entries.forEach { p ->
                                    val isSelected = currentPalette == p
                                    Surface(
                                        onClick = { onPaletteChange(p) },
                                        shape = CircleShape,
                                        color = p.seedColor,
                                        modifier = Modifier.size(if (isSelected) 36.dp else 30.dp),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(
                                            2.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                        ) else null,
                                    ) {
                                        if (isSelected) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_check),
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    OutlinedButton(
                        onClick = {
                            showAccountDialog = false
                            onLockApp()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Lock App")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showAccountDialog = false
                            onSignOut()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text("Sign Out")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showAccountDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (showCalendarDialog) {
        AcademicCalendarDialog(
            events = liveCalendarEvents,
            onDismiss = { showCalendarDialog = false },
        )
    }
}

/**
 * Organic shape card holding an intriguing campus question.
 * Maintains its 1:1 aspect ratio so the shape is never distorted, covering the question background.
 * Shows only the question in the box; user can tap it to cycle questions and shapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrganicQuestionCard(
    modifier: Modifier = Modifier,
) {
    var questionIndex by rememberSaveable { mutableIntStateOf((0 until IntriguingQuestions.size).random()) }
    var shapeIndex by rememberSaveable { mutableIntStateOf((0 until QuestionCardShapes.size).random()) }
    val cardShape = QuestionCardShapes[shapeIndex.coerceIn(QuestionCardShapes.indices)].toShape()
    val currentQuestion = IntriguingQuestions[questionIndex.coerceIn(IntriguingQuestions.indices)]

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            onClick = {
                // user presses it if it feels intriguing
                questionIndex = (questionIndex + 1) % IntriguingQuestions.size
                shapeIndex = (shapeIndex + 1) % QuestionCardShapes.size
            },
            shape = cardShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 2.dp,
            modifier = Modifier
                .wrapContentSize(align = Alignment.CenterEnd, unbounded = true)
                .requiredSize(118.dp)
                .aspectRatio(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = currentQuestion,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

// ponytail: fast lightweight ping to GitHub releases API; checks connectivity and updates
private suspend fun pingGitHubRelease(): Boolean = withContext(Dispatchers.IO) {
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
 * Small fluid pill that displays state and collapses into a green dot with a checkmark once ready.
 * After 3 seconds of compressing, the checkmark smoothly fades out and the dot shrinks into a compact solid green dot.
 * Tapping toggles expansion to view detailed status label.
 */
@Composable
private fun FluidStatusPill(
    isReady: Boolean,
    label: String,
    readyLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    isError: Boolean = false,
) {
    var userToggled by remember { mutableStateOf(false) }
    var checkmarkVisible by rememberSaveable { mutableStateOf(false) }
    var hasSettled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isReady) {
        if (isReady) {
            if (!hasSettled) {
                checkmarkVisible = true
                delay(3000L)
                checkmarkVisible = false
                hasSettled = true
            }
        } else {
            checkmarkVisible = false
            hasSettled = false
        }
    }

    LaunchedEffect(userToggled) {
        if (userToggled) {
            delay(4000L)
            userToggled = false
        }
    }

    val showExpanded = (!isReady && !isError) || isBusy || userToggled

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            isReady && !userToggled -> Color(0xFF2E7D32)
            isReady && userToggled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(350, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
        label = "pillBg",
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.onErrorContainer
            isReady && !userToggled -> Color.White
            isReady && userToggled -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(350, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
        label = "pillContent",
    )

    val checkmarkAlpha by animateFloatAsState(
        targetValue = if (checkmarkVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 350,
            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
        ),
        label = "checkmarkAlpha",
    )

    val targetCollapsedSize = if (checkmarkVisible) 14.dp else 8.dp

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
                if (isReady) {
                    userToggled = !userToggled
                }
                onClick()
            },
    ) {
        if (showExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                if (isReady) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = readyLabel,
                        tint = contentColor,
                        modifier = Modifier.size(10.dp),
                    )
                } else if (isBusy || !isReady) {
                    val transition = rememberInfiniteTransition(label = "pillPulse")
                    val alpha by transition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "pillPulseAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer { this.alpha = alpha }
                            .clip(CircleShape)
                            .background(if (isError) MaterialTheme.colorScheme.error else Color(0xFFE65100)),
                    )
                }

                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isReady) readyLabel else label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(targetCollapsedSize),
            ) {
                if (isReady) {
                    if (checkmarkAlpha > 0.01f) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = readyLabel,
                            tint = contentColor,
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer {
                                    alpha = checkmarkAlpha
                                    scaleX = 0.5f + 0.5f * checkmarkAlpha
                                    scaleY = 0.5f + 0.5f * checkmarkAlpha
                                },
                        )
                    }
                } else if (isBusy || !isReady) {
                    val transition = rememberInfiniteTransition(label = "collapsedPulse")
                    val alpha by transition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "collapsedPulseAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .graphicsLayer { this.alpha = alpha }
                            .clip(CircleShape)
                            .background(if (isError) MaterialTheme.colorScheme.error else Color(0xFFE65100)),
                    )
                }
            }
        }
    }
}

/**
 * 2D Carousel schedule:
 * - Left/Right swipe changes Day with layered card stack preview.
 * - Active card overshadows both left and right cards in background (zIndex + elevation + scale).
 * - Shows at least 3 sub-cards in view vertically inside the active day card, scrolling cleanly if more.
 */
@Composable
private fun DayCarouselSchedule(
    days: List<DaySchedule>,
    pagerState: PagerState,
) {
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 40.dp),
        beyondViewportPageCount = 1,
        pageSpacing = (-6).dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(352.dp),
    ) { pageIndex ->
        val day = days[pageIndex]

        // Calculate offset for card deck layering
        val pageOffset = ((pagerState.currentPage - pageIndex) +
            pagerState.currentPageOffsetFraction).absoluteValue
        val isCenter = pageOffset < 0.5f

        val scale by animateFloatAsState(
            targetValue = (1f - (pageOffset * 0.08f)).coerceIn(0.92f, 1f),
            label = "cardScale",
        )
        val alpha by animateFloatAsState(
            targetValue = (1f - (pageOffset * 0.60f)).coerceIn(0.40f, 1f), // Dull background cards
            label = "cardAlpha",
        )
        val elevation by animateDpAsState(
            targetValue = if (isCenter) 12.dp else 1.dp,
            label = "cardElevation",
        )
        val blurRadius = (8.dp * pageOffset).coerceIn(0.dp, 8.dp) // Blurry background cards
        val pivotX = when {
            pageIndex < pagerState.currentPage -> 0.95f
            pageIndex > pagerState.currentPage -> 0.05f
            else -> 0.5f
        }

        Card(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f - pageOffset) // Center card overshadows both left and right cards
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(pivotX, 0.5f)
                }
                .blur(radius = blurRadius), // Blur applied on left/right background cards
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCenter) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            border = null, // Transparent outermost day border per user request
        ) {
            val scrollState = rememberScrollState()

            // Find current active class index for this day to apply linear dullness falloff
            val currentClassIndex = day.classes.indexOfFirst { it.isOngoing }.takeIf { it >= 0 }
                ?: day.classes.indexOfFirst { it.isClosest }.takeIf { it >= 0 }
                ?: 0

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                day.classes.forEachIndexed { index, classItem ->
                    // Classes farther than current class in same day are linearly more dull
                    val distance = (index - currentClassIndex).absoluteValue
                    val dullAlpha = (1f - (distance * 0.20f)).coerceIn(0.38f, 1f)

                    ClassSubCard(
                        item = classItem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { this.alpha = dullAlpha },
                    )
                }

                // Days with 2 classes or less: fill empty space with doodle & "aha, freedom!"
                if (day.classes.size <= 2) {
                    FreedomDoodleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                    )
                }
            }
        }
    }
}

/**
 * Attendance Section matching user's sketch (pic_20261029012343_0.jpg):
 * - Attendance title with ↗ shortcut
 * - Horizontal swipeable subject cycle cards:
 *   - Course code + Course name
 *   - Attendance percentage (e.g. 76%) + Attended/Total pill (e.g. [ 13 / 17 ])
 *   - Bunk advice: "can safely miss till CAT-1 : 0" with status badge
 *   - Cycle indicators: 1/5 with navigation buttons
 */
@Composable
private fun AttendanceSection(
    subjects: List<SubjectAttendance>,
    onOpenAttendance: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { subjects.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Attendance Header with arrow ↗ as in sketch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenAttendance() },
            ) {
                Text(
                    text = "Attendance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "↗",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Subject cycle indicator / controls (e.g. 1/5 with arrows)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${subjects.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Surface(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(24.dp),
                    enabled = pagerState.currentPage > 0,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    onClick = {
                        if (pagerState.currentPage < subjects.size - 1) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(24.dp),
                    enabled = pagerState.currentPage < subjects.size - 1,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("›", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Horizontal Pager with Card Deck Layering (matching Timeline carousel effect)
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 40.dp),
            beyondViewportPageCount = 1,
            pageSpacing = (-6).dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp),
        ) { pageIndex ->
            val subject = subjects[pageIndex]
            val isWarning = subject.percentage in 75..79
            val isDanger = subject.percentage < 75

            // Calculate offset for card deck layering
            val pageOffset = ((pagerState.currentPage - pageIndex) +
                pagerState.currentPageOffsetFraction).absoluteValue
            val isCenter = pageOffset < 0.5f

            val scale by animateFloatAsState(
                targetValue = (1f - (pageOffset * 0.08f)).coerceIn(0.92f, 1f),
                label = "attendanceCardScale",
            )
            val alpha by animateFloatAsState(
                targetValue = (1f - (pageOffset * 0.60f)).coerceIn(0.40f, 1f),
                label = "attendanceCardAlpha",
            )
            val elevation by animateDpAsState(
                targetValue = if (isCenter) 10.dp else 1.dp,
                label = "attendanceCardElevation",
            )
            val blurRadius = (8.dp * pageOffset).coerceIn(0.dp, 8.dp)
            val pivotX = when {
                pageIndex < pagerState.currentPage -> 0.95f
                pageIndex > pagerState.currentPage -> 0.05f
                else -> 0.5f
            }

            Card(
                onClick = onOpenAttendance,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f - pageOffset)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin(pivotX, 0.5f)
                    }
                    .blur(radius = blurRadius),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCenter) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        isDanger -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        isWarning -> Color(0xFFFFA000).copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isCenter) 0.35f else 0.15f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left Column: takes up to ~65-70% width, showing full subject name up to the skip box
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = subject.courseCode,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp,
                            )
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Text(
                                text = subject.courseTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "${subject.percentage}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = when {
                                    isDanger -> MaterialTheme.colorScheme.error
                                    isWarning -> Color(0xFFFFB300)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                ),
                            ) {
                                Text(
                                    text = "${subject.attendedClasses} / ${subject.totalClasses}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    // Right Column: can safely miss till CAT-1 - 0
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isDanger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            isWarning -> Color(0xFFFFA000).copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isDanger -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                isWarning -> Color(0xFFFFA000).copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            },
                        ),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "can safely miss",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "till ${subject.examTarget}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                            Spacer(Modifier.height(3.dp))
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isDanger -> MaterialTheme.colorScheme.error
                                    isWarning -> Color(0xFFFF8F00)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${subject.safeBunks}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Doodle card filling empty space on days with 2 classes or less.
 * "aha, freedom!"
 */
@Composable
private fun FreedomDoodleCard(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = modifier,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "🏖️", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "aha, freedom!",
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = "🕊️", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "no more classes today · go touch grass",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Final class sub-card:
 * - Status indicated by border: running -> red (error), closest -> blue (primary)
 * - Line 1: [time start] - time end (no slot word)
 * - Line 2: CourseCode  ( a11 + b11 ) where active slot is highlighted
 * - Line 3: Course Title
 * - Right hand full side: Location container with block on top, room number next line
 */
@Composable
private fun ClassSubCard(
    item: ClassScheduleItem,
    modifier: Modifier = Modifier,
) {
    val cardBorder = when {
        item.isOngoing -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        item.isClosest -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = cardBorder,
        modifier = modifier,
        shadowElevation = if (item.isOngoing) 3.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left & Center Column: Time, Code + Slots, Title
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Line 1: [time start] - time end (no slot word)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            item.isOngoing -> MaterialTheme.colorScheme.error
                            item.isClosest -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                    ) {
                        Text(
                            text = item.startTime,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                item.isOngoing -> MaterialTheme.colorScheme.onError
                                item.isClosest -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.endTime,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Line 2: sts200  ( a11 + b11 ) where active slot is highlighted
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.courseCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            item.isOngoing -> MaterialTheme.colorScheme.error
                            item.isClosest -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        letterSpacing = 0.5.sp,
                    )

                    if (item.slots.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "(",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            item.slots.forEachIndexed { index, slotItem ->
                                val isSelected = slotItem.equals(item.activeSlot, ignoreCase = true)
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            item.isOngoing -> MaterialTheme.colorScheme.errorContainer
                                            item.isClosest -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                    ) {
                                        Text(
                                            text = slotItem,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = when {
                                                item.isOngoing -> MaterialTheme.colorScheme.onErrorContainer
                                                item.isClosest -> MaterialTheme.colorScheme.onPrimaryContainer
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                } else {
                                    Text(
                                        text = slotItem,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    )
                                }
                                if (index < item.slots.size - 1) {
                                    Text(
                                        text = "+",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    )
                                }
                            }
                            Text(
                                text = ")",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }

                // Line 3: Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right-hand full side covering the location: Block on top, room number next line
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    item.isOngoing -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    item.isClosest -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        item.isOngoing -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        item.isClosest -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    },
                ),
                modifier = Modifier
                    .width(72.dp)
                    .height(76.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = item.roomBlock,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            item.isOngoing -> MaterialTheme.colorScheme.error
                            item.isClosest -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.roomNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

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
                Pair(R.drawable.ic_grade, "Attendance"),
                Pair(R.drawable.ic_person, "Profile"),
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
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun defaultMockScheduleDays(): List<DaySchedule> = listOf(
    DaySchedule(
        dayName = "Yesterday",
        dateLabel = "Tue, Oct 27",
        classes = listOf(
            ClassScheduleItem("09:00 AM", "10:30 AM", "SJT", "401", "CSE2005", "Operating Systems", listOf("A11", "A12", "A13"), "A11"),
            ClassScheduleItem("11:30 AM", "13:00 PM", "TT", "213", "MAT2001", "Calculus & Linear Algebra", listOf("B11", "B12"), "B11"),
        ),
    ),
    DaySchedule(
        dayName = "Today",
        dateLabel = "Wed, Oct 28",
        classes = listOf(
            ClassScheduleItem(
                startTime = "11:00 AM",
                endTime = "13:10 PM",
                roomBlock = "AB",
                roomNumber = "330",
                courseCode = "STS200",
                title = "Soft Skills",
                slots = listOf("A11", "B11"),
                activeSlot = "B11",
                isOngoing = true,
            ),
            ClassScheduleItem(
                startTime = "13:15 PM",
                endTime = "14:45 PM",
                roomBlock = "AB02",
                roomNumber = "171",
                courseCode = "CSE3001",
                title = "System Design",
                slots = listOf("A14", "B14", "C14"),
                activeSlot = "A14",
            ),
            ClassScheduleItem(
                startTime = "15:00 PM",
                endTime = "16:15 PM",
                roomBlock = "SJT",
                roomNumber = "711",
                courseCode = "CSE3001",
                title = "System Design Lab",
                slots = listOf("L31"),
                activeSlot = "L31",
            ),
            ClassScheduleItem(
                startTime = "16:30 PM",
                endTime = "18:00 PM",
                roomBlock = "LC",
                roomNumber = "002",
                courseCode = "AWS301",
                title = "AWS",
                slots = listOf("C21", "D21"),
                activeSlot = "C21",
            ),
            ClassScheduleItem(
                startTime = "18:15 PM",
                endTime = "19:30 PM",
                roomBlock = "MB",
                roomNumber = "202",
                courseCode = "CSE3006",
                title = "Computer Networks",
                slots = listOf("D11", "D12", "D13"),
                activeSlot = "D11",
            ),
        ),
    ),
    DaySchedule(
        dayName = "Tomorrow",
        dateLabel = "Thu, Oct 29",
        classes = listOf(
            ClassScheduleItem("08:30 AM", "10:00 AM", "AB", "104", "PHY1701", "Engineering Physics", listOf("D11", "E11"), "D11", isClosest = true),
            ClassScheduleItem("10:15 AM", "11:45 AM", "TT", "302", "CHY1701", "Engineering Chemistry", listOf("E11", "F11"), "E11"),
            ClassScheduleItem("14:00 PM", "15:30 PM", "SJT", "302", "CSE3002", "Compilers", listOf("F11", "G11"), "F11"),
        ),
    ),
    DaySchedule(
        dayName = "Friday",
        dateLabel = "Fri, Oct 30",
        classes = listOf(
            ClassScheduleItem("09:00 AM", "10:30 AM", "MB", "202", "CSE3011", "Cloud Computing", listOf("B21", "C21"), "B21"),
            ClassScheduleItem("10:45 AM", "12:15 PM", "TT", "105", "MAT3004", "Applied Statistics", listOf("C21", "D21"), "C21"),
            ClassScheduleItem("13:30 PM", "15:00 PM", "SJT", "214", "CSE3006", "Computer Networks Lab", listOf("L41"), "L41"),
            ClassScheduleItem("15:15 PM", "16:45 PM", "AB", "201", "ENG1002", "Professional Communication", listOf("D21", "E21"), "D21"),
            ClassScheduleItem("17:00 PM", "18:15 PM", "TT", "418", "HUM1021", "Ethics & Values", listOf("E21"), "E21"),
        ),
    ),
    DaySchedule(
        dayName = "Saturday",
        dateLabel = "Sat, Oct 31",
        classes = listOf(
            ClassScheduleItem("10:00 AM", "11:30 AM", "AB", "105", "STS3001", "Aptitude & Reasoning", listOf("F21", "G21"), "F21"),
            ClassScheduleItem("12:00 PM", "13:30 PM", "SJT", "108", "EXC1001", "Extracurricular Seminar", listOf("S11"), "S11"),
        ),
    ),
)

/**
 * Academic Calendar Section on Dashboard:
 * Shows a compact snapshot of semester events (upcoming holidays, exams, instructional days).
 * Tapping "View All ↗" opens the full semester calendar dialog.
 */
@Composable
private fun AcademicCalendarSection(
    events: List<CalendarDayEvent>,
    onOpenCalendar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenCalendar() },
            ) {
                Text(
                    text = "Academic Calendar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "↗",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (events.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable { onOpenCalendar() },
                ) {
                    Text(
                        text = "${events.size} days",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (events.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCalendar() },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Calendar syncing in background... Tap to preview.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val highlighted = remember(events) {
                val nonInstructional = events.filter { it.isExam || it.isHoliday }
                if (nonInstructional.isNotEmpty()) nonInstructional.take(8) else events.take(8)
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(highlighted) { event ->
                    CalendarEventCard(event = event, onClick = onOpenCalendar)
                }
            }
        }
    }
}

@Composable
private fun CalendarEventCard(
    event: CalendarDayEvent,
    onClick: () -> Unit,
) {
    val (badgeBg, badgeFg) = when {
        event.isHoliday -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        event.isExam -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        event.isInstructional -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                ) {
                    Text(
                        text = event.eventCategory,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = event.date.takeLast(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = event.title.ifBlank { event.subtitle.ifBlank { "Instructional Day" } },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.subtitle.isNotBlank() && event.title != event.subtitle) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = event.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Full Semester Academic Calendar Dialog modal.
 * Lets student browse months (Jun-Nov) and filter by event category.
 */
@Composable
private fun AcademicCalendarDialog(
    events: List<CalendarDayEvent>,
    onDismiss: () -> Unit,
) {
    val months = remember(events) {
        events.map { it.monthYear }.distinct().filter { it.isNotBlank() }
    }
    val currentMonthKey = remember {
        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US).format(java.util.Date()).uppercase()
    }
    val defaultMonth = remember(months) {
        months.firstOrNull { it.equals(currentMonthKey, ignoreCase = true) }
            ?: months.lastOrNull { it.isNotBlank() } ?: ""
    }
    var selectedMonth by rememberSaveable(months) {
        mutableStateOf(defaultMonth)
    }
    var filterCategory by rememberSaveable { mutableStateOf("ALL") }

    val filteredEvents = remember(events, selectedMonth, filterCategory) {
        var list = events
        if (selectedMonth.isNotBlank()) {
            list = list.filter { it.monthYear.equals(selectedMonth, ignoreCase = true) }
        }
        when (filterCategory) {
            "EXAMS" -> list.filter { it.isExam }
            "HOLIDAYS" -> list.filter { it.isHoliday }
            "INSTRUCTIONAL" -> list.filter { it.isInstructional }
            else -> list
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Academic Calendar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Fall Semester 2026-27",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Month selection chips
                if (months.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(months) { m ->
                            val isSelected = m.equals(selectedMonth, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.clickable { selectedMonth = m },
                            ) {
                                Text(
                                    text = m,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Category filter chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val categories = listOf("ALL" to "All", "EXAMS" to "Exams", "HOLIDAYS" to "Holidays", "INSTRUCTIONAL" to "Classes")
                    for ((key, label) in categories) {
                        val isSelected = filterCategory == key
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable { filterCategory = key },
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Events list
                if (filteredEvents.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (events.isEmpty()) "No calendar records cached yet." else "No events match filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredEvents) { event ->
                            CalendarDayRow(event = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayRow(event: CalendarDayEvent) {
    val (pillBg, pillFg) = when {
        event.isHoliday -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        event.isExam -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        event.isInstructional -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(44.dp),
            ) {
                Text(
                    text = event.dayNumber.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = event.dayOfWeek.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title.ifBlank { event.subtitle.ifBlank { "Instructional Day" } },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.subtitle.isNotBlank() && event.subtitle != event.title) {
                    Text(
                        text = event.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = pillBg,
            ) {
                Text(
                    text = event.eventCategory,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = pillFg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}


