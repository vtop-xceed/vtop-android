package dev.vtoppp.vtop.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AttendanceParser
import dev.vtoppp.vtop.data.DaySchedule
import dev.vtoppp.vtop.data.PillKind
import dev.vtoppp.vtop.data.PillState
import dev.vtoppp.vtop.data.SessionCache
import dev.vtoppp.vtop.data.TimetableEntry
import dev.vtoppp.vtop.data.TimetableParser
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.data.UserProfile
import dev.vtoppp.vtop.data.sharedLightweightHttpClient
import dev.vtoppp.vtop.ui.theme.AppDimens
import dev.vtoppp.vtop.ui.theme.AppPalette
import dev.vtoppp.vtop.ui.theme.StatusColors
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

data class SubjectAttendance(
        val courseCode: String,
        val courseTitle: String,
        val percentage: Int,
        val attendedClasses: Int,
        val totalClasses: Int,
        val safeBunks: Int,
        val examTarget: String = "CAT-1",
        val todaySlot: String = "",
        val todayStartTime: String = "",
        val todayEndTime: String = "",
        val isOngoing: Boolean = false,
        val isNext: Boolean = false,
        val isToday: Boolean = false,
        val todayStatus: String? = null,
)

private class TimedTimetableEntry(
        val entry: TimetableEntry,
        val start: LocalTime?,
        val end: LocalTime?,
)

private val githubPingHttpClient by lazy {
        sharedLightweightHttpClient(connectTimeoutSec = 4, readTimeoutSec = 4)
}

enum class DashboardScreen(val isModal: Boolean) {
    Overview(false),
    Calendar(false),
    Marks(false),
    Settings(false),
    Attendance(true),
    Timetable(true),
    GradeHistory(true),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainDashboardScreen(
        authorizedId: String,
        onOpenService: (MenuItem) -> Unit,
        pill: PillState = PillState(PillKind.Ready, "Connected"),
        onCheckUpdate: () -> Unit = {},
        onSignOut: () -> Unit = {},
        onBridgeRetry: () -> Unit = {},
        onRefresh: () -> Unit = {},
        marksNotice: String? = null,
        onMarksNoticeShown: () -> Unit = {},
        currentPalette: AppPalette = AppPalette.Material,
        onPaletteChange: (AppPalette) -> Unit = {},
        themeMode: dev.vtoppp.vtop.ui.theme.ThemeMode = dev.vtoppp.vtop.ui.theme.ThemeMode.DARK,
        onThemeModeChange: (dev.vtoppp.vtop.ui.theme.ThemeMode) -> Unit = {},
        blurEnabled: Boolean = true,
        onBlurEnabledChange: (Boolean) -> Unit = {},
        lockEnabled: Boolean = true,
        pinSet: Boolean = false,
        onAddPin: () -> Unit = {},
        onLockEnabledChange: (Boolean) -> Unit = {},
        autoUpdateEnabled: Boolean = true,
        onAutoUpdateEnabledChange: (Boolean) -> Unit = {},
        sections: List<Pair<String, List<MenuItem>>> = emptyList(),
        userProfile: UserProfile? = null,
        client: dev.vtoppp.vtop.data.VtopClient? = null,
        onRequestNotificationPermission: () -> Unit = {},
        isRebuilding: Boolean = false,
        rebuildStep: String = "",
        rebuildProgress: Float = 0f,
        rebuildLogs: List<String> = emptyList(),
        onPurgeAndRebuild: () -> Unit = {},
        onEnsureOverview: () -> Unit = {},
        onRefreshOverview: () -> Unit = onRefresh,
        onEnsureAttendance: (String?) -> Unit = {},
        onRefreshAttendance: (String?) -> Unit = { onRefresh() },
        onLoadCourseAttendanceDetails: (dev.vtoppp.vtop.data.AttendanceEntry) -> Unit = {},
        loadingAttendanceCourseKeys: Set<String> = emptySet(),
        onEnsureTimetable: (String?) -> Unit = {},
        onRefreshTimetable: (String?) -> Unit = { onRefresh() },
        onEnsureCalendar: () -> Unit = {},
        onRefreshCalendar: () -> Unit = onRefresh,
        onEnsureMarks: (String?) -> Unit = {},
        onRefreshMarks: (String?) -> Unit = { onRefresh() },
) {
    var githubPingDone by remember { mutableStateOf(false) }
    var githubPingSuccess by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(400)
        val ok = pingGitHubRelease()
        githubPingSuccess = ok
        githubPingDone = true
    }
    val liveProfile = userProfile ?: UserDatabase.profileFlow.collectAsStateWithLifecycle().value
    val liveCalendarEvents by UserDatabase.calendarEventsFlow.collectAsStateWithLifecycle()

    val storedTimetable by UserDatabase.timetableFlow.collectAsStateWithLifecycle()
    val liveSessions by UserDatabase.attendanceSessionsFlow.collectAsStateWithLifecycle()
    // The highlight has to follow the clock, so the clock is state, not a
    // value captured once when the timetable loads.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(60_000)
                now = LocalDateTime.now()
            }
        }
    }

    val scheduleDays =
            remember(storedTimetable, liveSessions, now) {
                UserDatabase.buildScheduleDays(
                        storedTimetable,
                        liveSessions,
                        now.toLocalDate(),
                        now.toLocalTime(),
                )
            }

    // Follow the day that holds the next class, but never mid-swipe.
    val classDayIndex =
            remember(scheduleDays) { UserDatabase.nearestClassDayIndex(scheduleDays, now) }
    val horizontalPagerState =
            rememberPagerState(
                    initialPage = classDayIndex,
                    pageCount = { scheduleDays.size },
            )
    LaunchedEffect(classDayIndex) {
        if (scheduleDays.isEmpty()) return@LaunchedEffect
        if (horizontalPagerState.currentPage != classDayIndex &&
                        !horizontalPagerState.isScrollInProgress
        ) {
            horizontalPagerState.scrollToPage(classDayIndex)
        }
    }

    val liveAttendance by UserDatabase.attendanceFlow.collectAsStateWithLifecycle()
    val semesters by UserDatabase.semestersFlow.collectAsStateWithLifecycle()
    val dbRevision by UserDatabase.dbVersionFlow.collectAsStateWithLifecycle()
    val derivedLatestSemesterId =
            remember(semesters) { semesters.maxOfOrNull { it.semesterSubId } }
    val latestSemesterId by
            produceState(
                    initialValue = derivedLatestSemesterId,
                    derivedLatestSemesterId,
                    authorizedId,
                    dbRevision,
            ) {
                value =
                        derivedLatestSemesterId
                                ?: withContext(Dispatchers.IO) {
                                    UserDatabase.getLatestSemesterId(authorizedId)
                                }
            }

    LaunchedEffect(isRebuilding) {
        if (!isRebuilding) onEnsureOverview()
    }

    var attendanceSemesterId by rememberSaveable { mutableStateOf<String?>(null) }
    var timetableSemesterId by rememberSaveable { mutableStateOf<String?>(null) }

    val historicalAttendance by
            produceState<List<dev.vtoppp.vtop.data.AttendanceEntry>>(
                    initialValue = emptyList(),
                    attendanceSemesterId,
                    latestSemesterId,
                    authorizedId,
                    dbRevision,
            ) {
                val selected = attendanceSemesterId
                value =
                        if (selected != null && selected != latestSemesterId) {
                            withContext(Dispatchers.IO) {
                                UserDatabase.loadAttendance(authorizedId, selected)
                            }
                        } else {
                            emptyList()
                        }
            }
    val pageAttendance =
            if (attendanceSemesterId == null || attendanceSemesterId == latestSemesterId) {
                liveAttendance
            } else {
                historicalAttendance
            }

    val historicalTimetable by
            produceState<List<TimetableEntry>>(
                    initialValue = emptyList(),
                    timetableSemesterId,
                    latestSemesterId,
                    authorizedId,
                    dbRevision,
            ) {
                val selected = timetableSemesterId
                value =
                        if (selected != null && selected != latestSemesterId) {
                            withContext(Dispatchers.IO) {
                                UserDatabase.loadTimetable(authorizedId, selected)
                            }
                        } else {
                            emptyList()
                        }
            }
    val pageTimetable =
            if (timetableSemesterId == null || timetableSemesterId == latestSemesterId) {
                storedTimetable
            } else {
                historicalTimetable
            }

    // Static attendance/timetable/session indexing memoized independently of the 60s clock tick.
    val (baseSubjects, baseByCode) =
            remember(liveAttendance) {
                val list =
                        liveAttendance.map { entry ->
                            // ponytail: calculate safe bunks to maintain 75% attendance threshold
                            val safeBunks =
                                    ((entry.attendedClasses - 0.75 * entry.totalClasses) / 0.75)
                                            .toInt()
                                            .coerceAtLeast(0)
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
                val byCode = HashMap<String, SubjectAttendance>(list.size * 2)
                for (subject in list) {
                    byCode.putIfAbsent(subject.courseCode.trim().uppercase(), subject)
                }
                list to byCode
            }

    val timetableByDay =
            remember(storedTimetable) {
                storedTimetable
                        .groupBy { it.dayOfWeek }
                        .mapValues { (_, items) ->
                            items
                                    .map {
                                        TimedTimetableEntry(
                                                entry = it,
                                                start = UserDatabase.parseTime(it.startTime),
                                                end = UserDatabase.parseTime(it.endTime),
                                        )
                                    }
                                    .sortedBy { it.start?.toSecondOfDay() ?: Int.MAX_VALUE }
                        }
            }

    val sessionStatusIndex =
            remember(liveSessions) {
                HashMap<Pair<String, String>, String>(liveSessions.size * 2).apply {
                    for (s in liveSessions) {
                        putIfAbsent(s.courseCode.trim().uppercase() to s.date, s.status)
                    }
                }
            }

    val (attendanceSubjects, attendanceFocusIndex) =
            remember(baseSubjects, baseByCode, timetableByDay, sessionStatusIndex, now) {
                val todayDate = now.toLocalDate()
                val todayDayOfWeek = todayDate.dayOfWeek.value // 1 = Monday .. 7 = Sunday
                val todayTime = now.toLocalTime()
                val todayClasses = timetableByDay[todayDayOfWeek].orEmpty()

                val runningClass =
                        todayClasses
                                .firstOrNull { c ->
                                    c.start != null &&
                                            c.end != null &&
                                            !todayTime.isBefore(c.start) &&
                                            todayTime.isBefore(c.end)
                                }
                                ?.entry

                val nextClass =
                        if (runningClass == null) {
                            todayClasses
                                    .firstOrNull { c ->
                                        c.start != null && todayTime.isBefore(c.start)
                                    }
                                    ?.entry
                        } else null

                val mostRecentClass =
                        if (runningClass == null && nextClass == null) {
                            todayClasses
                                    .lastOrNull { c ->
                                        c.end != null && !todayTime.isBefore(c.end)
                                    }
                                    ?.entry
                        } else null

                val runningCode = runningClass?.courseCode?.trim()?.uppercase()
                val nextCode = nextClass?.courseCode?.trim()?.uppercase()
                val todaySubjects = ArrayList<SubjectAttendance>(todayClasses.size)
                val seenCodes = HashSet<String>(todayClasses.size * 2)
                val dateIso = todayDate.toString()

                for (timed in todayClasses) {
                    val classEntry = timed.entry
                    val codeClean = classEntry.courseCode.trim().uppercase()
                    if (!seenCodes.add(codeClean)) continue
                    val base =
                            baseByCode[codeClean]
                                    ?: SubjectAttendance(
                                            courseCode = classEntry.courseCode,
                                            courseTitle = classEntry.courseTitle,
                                            percentage = 100,
                                            attendedClasses = 1,
                                            totalClasses = 1,
                                            safeBunks = 0,
                                    )
                    val isRunning = codeClean == runningCode
                    val isNextUp = codeClean == nextCode
                    val activeSlotItem =
                            when {
                                isRunning -> runningClass!!
                                isNextUp -> nextClass!!
                                else -> classEntry
                            }
                    todaySubjects.add(
                            base.copy(
                                    todaySlot =
                                            activeSlotItem.activeSlot.ifBlank {
                                                activeSlotItem.slots.firstOrNull().orEmpty()
                                            },
                                    todayStartTime = activeSlotItem.startTime,
                                    todayEndTime = activeSlotItem.endTime,
                                    isOngoing = isRunning,
                                    isNext = isNextUp,
                                    isToday = true,
                                    todayStatus = sessionStatusIndex[codeClean to dateIso],
                            )
                    )
                }

                val nonTodaySubjects =
                        baseSubjects
                                .filter { it.courseCode.trim().uppercase() !in seenCodes }
                                .map { it.copy(isToday = false) }
                val ordered = todaySubjects + nonTodaySubjects

                val focusCode = (runningClass ?: nextClass ?: mostRecentClass)?.courseCode?.trim()
                val focusIdx =
                        if (focusCode != null) {
                            ordered
                                    .indexOfFirst {
                                        it.courseCode.trim().equals(focusCode, ignoreCase = true)
                                    }
                                    .coerceAtLeast(0)
                        } else 0

                ordered to focusIdx
            }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedNavTab by rememberSaveable { mutableIntStateOf(0) }
    var showGradesPage by rememberSaveable { mutableStateOf(false) }
    var showAttendancePage by rememberSaveable { mutableStateOf(false) }
    var showTimetablePage by rememberSaveable { mutableStateOf(false) }

    val baseFaculties =
            remember(liveAttendance) {
                buildMap {
                    for (entry in liveAttendance) {
                        if (entry.facultyName.isNotBlank()) {
                            put(entry.courseCode, entry.facultyName)
                        }
                    }
                }
            }
    val faculties by
            produceState(
                    initialValue = baseFaculties,
                    baseFaculties,
                    latestSemesterId,
                    authorizedId,
                    storedTimetable.size,
            ) {
                value =
                        withContext(Dispatchers.IO) {
                            val map = baseFaculties.toMutableMap()
                            try {
                                val semId =
                                        latestSemesterId
                                                ?: UserDatabase.getLatestSemesterId(authorizedId)
                                                ?: "BL20262705"
                                listOf(
                                                "processViewTimeTable_$semId",
                                                "processViewTimeTable_BL20262705",
                                                "processViewTimeTable_BL20262701",
                                        )
                                        .distinct()
                                        .forEach { name ->
                                            SessionCache.load(name)?.let { cached ->
                                                TimetableParser.parseFacultyMap(cached.first)
                                                        .forEach { (code, fac) ->
                                                            if (map[code].isNullOrBlank() &&
                                                                            fac.isNotBlank()
                                                            ) {
                                                                map[code] = fac
                                                            }
                                                        }
                                            }
                                        }
                                listOf(
                                                "processViewStudentAttendance_$semId",
                                                "processViewStudentAttendance_BL20262705",
                                                "processViewStudentAttendance_BL20262701",
                                        )
                                        .distinct()
                                        .forEach { name ->
                                            SessionCache.load(name)?.let { cached ->
                                                AttendanceParser.parse(cached.first, authorizedId)
                                                        .forEach { entry ->
                                                            if (map[entry.courseCode].isNullOrBlank() &&
                                                                            entry.facultyName
                                                                                    .isNotBlank()
                                                            ) {
                                                                map[entry.courseCode] =
                                                                        entry.facultyName
                                                            }
                                                        }
                                            }
                                        }
                            } catch (_: Exception) {}
                            map
                        }
            }

    val isSyncing by UserDatabase.isSyncingFlow.collectAsStateWithLifecycle()
    val isProcessing = pill.kind == PillKind.Busy || !githubPingDone || isSyncing

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .statusBarsPadding(), // Directly beneath system top navbar (time ---
            // signal battery)
            ) {
        BackHandler(
                enabled =
                        selectedNavTab != 0 ||
                                showGradesPage ||
                                showAttendancePage ||
                                showTimetablePage
        ) {
            when {
                showGradesPage -> showGradesPage = false
                showAttendancePage -> showAttendancePage = false
                showTimetablePage -> showTimetablePage = false
                else -> selectedNavTab = 0
            }
        }
        val enterEffect = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exitEffect = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
                targetState =
                        when {
                            showAttendancePage -> "attendance"
                            showTimetablePage -> "timetable"
                            selectedNavTab == 1 -> "calendar"
                            showGradesPage -> "grades"
                            selectedNavTab == 2 -> "marks"
                            selectedNavTab == 3 -> "settings"
                            else -> "timeline"
                        },
                transitionSpec = {
                    fadeIn(enterEffect) togetherWith fadeOut(exitEffect)
                },
                label = "navContent",
        ) { contentKey ->
            if (contentKey != "settings" && isRebuilding) {
                RebuildingScreen(
                        step = rebuildStep,
                        progress = rebuildProgress,
                        onViewLogs = {
                            selectedNavTab = 3
                            showAttendancePage = false
                            showTimetablePage = false
                            showGradesPage = false
                        },
                )
            } else
                    when (contentKey) {
                        "calendar" -> {
                                LaunchedEffect(pill.kind == PillKind.Ready) { onEnsureCalendar() }
                                AcademicCalendarPage(
                                        events = liveCalendarEvents,
                                        onRefresh = onRefreshCalendar
                                )
                        }
                        "attendance" -> {
                                val activeAttSem = attendanceSemesterId ?: latestSemesterId
                                LaunchedEffect(activeAttSem, pill.kind == PillKind.Ready) {
                                        onEnsureAttendance(activeAttSem)
                                }
                                AttendancePage(
                                        entries = pageAttendance,
                                        sessions = liveSessions,
                                        timetable = pageTimetable,
                                        faculties = faculties,
                                        onBack = { showAttendancePage = false },
                                        semesterOptions = semesters,
                                        selectedSemesterId = activeAttSem,
                                        onSemesterChange = { attendanceSemesterId = it },
                                        onRefresh = { onRefreshAttendance(activeAttSem) },
                                        onLoadCourseDetails = onLoadCourseAttendanceDetails,
                                        loadingCourseKeys = loadingAttendanceCourseKeys,
                                )
                        }
                        "timetable" -> {
                                val activeTtSem = timetableSemesterId ?: latestSemesterId
                                LaunchedEffect(activeTtSem, pill.kind == PillKind.Ready) {
                                        onEnsureTimetable(activeTtSem)
                                }
                                TimetableDetailsPage(
                                        timetable = pageTimetable,
                                        faculties = faculties,
                                        onBack = { showTimetablePage = false },
                                        semesterOptions = semesters,
                                        selectedSemesterId = activeTtSem,
                                        onSemesterChange = { timetableSemesterId = it },
                                        onRefresh = { onRefreshTimetable(activeTtSem) },
                                )
                        }
                        "grades" -> {
                                LaunchedEffect(pill.kind == PillKind.Ready) { onEnsureMarks(null) }
                                GradeHistoryPage(
                                        onBack = { showGradesPage = false },
                                        onRefresh = { onRefreshMarks(null) },
                                )
                        }
                        "marks" -> {
                                LaunchedEffect(pill.kind == PillKind.Ready) { onEnsureMarks(null) }
                                MarksGradesPage(
                                        authorizedId = authorizedId,
                                        onOpenGrades = { showGradesPage = true },
                                        onRefresh = { semId -> onRefreshMarks(semId) },
                                        marksNotice = marksNotice,
                                        onMarksNoticeShown = onMarksNoticeShown,
                                        onEnsureSemesterMarks = { semId -> onEnsureMarks(semId) },
                                )
                        }
                        "settings" ->
                                SettingsPage(
                                        authorizedId = authorizedId,
                                        userProfile = userProfile,
                                        currentPalette = currentPalette,
                                        onPaletteChange = onPaletteChange,
                                        themeMode = themeMode,
                                        onThemeModeChange = onThemeModeChange,
                                        blurEnabled = blurEnabled,
                                        onBlurEnabledChange = onBlurEnabledChange,
                                        lockEnabled = lockEnabled,
                                        pinSet = pinSet,
                                        onAddPin = onAddPin,
                                        onLockEnabledChange = onLockEnabledChange,
                                        autoUpdateEnabled = autoUpdateEnabled,
                                        onAutoUpdateEnabledChange = onAutoUpdateEnabledChange,
                                        onCheckUpdate = onCheckUpdate,
                                        onSignOut = onSignOut,
                                        onRefresh = onRefreshOverview,
                                        client = client,
                                        onRequestNotificationPermission =
                                                onRequestNotificationPermission,
                                        isRebuilding = isRebuilding,
                                        rebuildStep = rebuildStep,
                                        rebuildProgress = rebuildProgress,
                                        rebuildLogs = rebuildLogs,
                                        onPurgeAndRebuild = onPurgeAndRebuild,
                                )
                        else ->
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Squiggly loading line only appears when processing networks
                                    // or in transitions
                                    SyncingBar(visible = isProcessing)

                                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                                            state =
                                                    androidx.compose.material3.pulltorefresh
                                                            .rememberPullToRefreshState(),
                                            isRefreshing = isSyncing,
                                            onRefresh = onRefreshOverview,
                                            indicator = {},
                                    ) {
                                        LazyColumn(
                                                state = listState,
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding =
                                                        PaddingValues(
                                                                top = AppDimens.ScreenTop,
                                                                bottom = AppDimens.FloatingNavClearance,
                                                        ),
                                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            // Header: Greeting on Left + Organic Question Card on
                                            // Right
                                            item {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(
                                                                                start = AppDimens.ScreenHorizontal,
                                                                                end = AppDimens.ScreenHorizontal,
                                                                                top = 6.dp,
                                                                                bottom = 4.dp
                                                                        ),
                                                        horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                ) {
                                                    GreetingCard(
                                                            greeting = SessionGreeting.greeting,
                                                            typography =
                                                                    GreetingFontStyles[
                                                                            SessionGreeting
                                                                                    .fontIndex
                                                                                    .coerceIn(
                                                                                            GreetingFontStyles
                                                                                                    .indices
                                                                                    )],
                                                            parsedName =
                                                                    remember(
                                                                            liveProfile?.name,
                                                                            authorizedId
                                                                    ) {
                                                                        parseDisplayName(
                                                                                liveProfile?.name,
                                                                                authorizedId
                                                                        )
                                                                    },
                                                            pill = pill,
                                                            githubPingDone = githubPingDone,
                                                            githubPingSuccess = githubPingSuccess,
                                                            onBridgeRetry = onBridgeRetry,
                                                            onGithubPing = {
                                                                scope.launch {
                                                                    githubPingDone = false
                                                                    val ok = pingGitHubRelease()
                                                                    githubPingSuccess = ok
                                                                    githubPingDone = true
                                                                }
                                                            },
                                                            onSettingsClick = {
                                                                selectedNavTab = 3
                                                            },
                                                            modifier = Modifier.weight(0.65f),
                                                    )

                                                    Spacer(Modifier.width(AppDimens.ItemGap))

                                                    // Right: Poll with question text above the
                                                    // shape (35% width)
                                                    OrganicQuestionCard(
                                                            modifier = Modifier.weight(0.35f)
                                                    )
                                                }
                                            }

                                            // Timeline Header: Title with Day & Date directly next
                                            // to it
                                            item {
                                                val currentDay =
                                                        scheduleDays.getOrNull(
                                                                horizontalPagerState.currentPage
                                                        )
                                                                ?: DaySchedule(
                                                                        dayName = "No timetable",
                                                                        dateLabel = "cached",
                                                                        classes = emptyList()
                                                                )
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(
                                                                                horizontal = AppDimens.ScreenHorizontal,
                                                                                vertical = 2.dp
                                                                        ),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                            text = "Timeline",
                                                            style =
                                                                    MaterialTheme.typography
                                                                            .titleLargeEmphasized,
                                                            fontWeight = FontWeight.Bold,
                                                            color =
                                                                    MaterialTheme.colorScheme
                                                                            .onSurface,
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                    val isToday = currentDay.dayName == "Today"
                                                    // "Today · Tue, Nov 15" for near days, plain
                                                    // "Tue, Nov 17" otherwise
                                                    val relativeDay =
                                                            currentDay.dayName == "Today" ||
                                                                    currentDay.dayName ==
                                                                            "Tomorrow" ||
                                                                    currentDay.dayName ==
                                                                            "Yesterday"
                                                    val badgeEffects =
                                                            MaterialTheme.motionScheme
                                                                    .defaultEffectsSpec<Color>()
                                                    val badgeColor by
                                                            animateColorAsState(
                                                                    targetValue =
                                                                            if (isToday)
                                                                                    MaterialTheme
                                                                                            .colorScheme
                                                                                            .primaryContainer
                                                                            else
                                                                                    MaterialTheme
                                                                                            .colorScheme
                                                                                            .surfaceContainerHigh,
                                                                    animationSpec = badgeEffects,
                                                                    label = "dayBadge",
                                                            )
                                                    val badgeContent by
                                                            animateColorAsState(
                                                                    targetValue =
                                                                            if (isToday)
                                                                                    MaterialTheme
                                                                                            .colorScheme
                                                                                            .onPrimaryContainer
                                                                            else
                                                                                    MaterialTheme
                                                                                            .colorScheme
                                                                                            .onSurface,
                                                                    animationSpec = badgeEffects,
                                                                    label = "dayBadgeContent",
                                                            )
                                                    val badgeContentDim by
                                                            animateColorAsState(
                                                                    targetValue =
                                                                            if (isToday)
                                                                                    MaterialTheme
                                                                                            .colorScheme
                                                                                            .onPrimaryContainer
                                                                                            .copy(
                                                                                                    alpha =
                                                                                                            0.85f
                                                                                            )
                                                                            else
                                                                                    MaterialTheme
                                                                                            .colorScheme
                                                                                            .onSurfaceVariant,
                                                                    animationSpec = badgeEffects,
                                                                    label = "dayBadgeDim",
                                                            )
                                                    Surface(
                                                            shape = MaterialTheme.shapes.medium,
                                                            color = badgeColor,
                                                    ) {
                                                        Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                start = 10.dp,
                                                                                end = 4.dp,
                                                                                top = 4.dp,
                                                                                bottom = 4.dp
                                                                        ),
                                                        ) {
                                                            if (relativeDay) {
                                                                Text(
                                                                        text = currentDay.dayName,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelMedium,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color = badgeContent,
                                                                )
                                                                Text(
                                                                        text =
                                                                                " · ${currentDay.dateLabel}",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelMedium,
                                                                        fontWeight =
                                                                                FontWeight.SemiBold,
                                                                        color = badgeContentDim,
                                                                )
                                                            } else {
                                                                Text(
                                                                        text = currentDay.dateLabel,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelMedium,
                                                                        fontWeight =
                                                                                FontWeight.SemiBold,
                                                                        color = badgeContentDim,
                                                                )
                                                            }
                                                            AnimatedVisibility(
                                                                    visible =
                                                                            currentDay.classes
                                                                                    .isNotEmpty(),
                                                                    enter =
                                                                            fadeIn(
                                                                                    MaterialTheme
                                                                                            .motionScheme
                                                                                            .fastEffectsSpec()
                                                                            ) +
                                                                                    scaleIn(
                                                                                            MaterialTheme
                                                                                                    .motionScheme
                                                                                                    .defaultSpatialSpec()
                                                                                    ),
                                                                    exit =
                                                                            fadeOut(
                                                                                    MaterialTheme
                                                                                            .motionScheme
                                                                                            .fastEffectsSpec()
                                                                            ) +
                                                                                    scaleOut(
                                                                                            MaterialTheme
                                                                                                    .motionScheme
                                                                                                    .fastEffectsSpec()
                                                                                    ),
                                                            ) {
                                                                Surface(
                                                                        shape =
                                                                                MaterialTheme.shapes
                                                                                        .small,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .tertiaryContainer,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        start =
                                                                                                AppDimens.ItemGap
                                                                                ),
                                                                ) {
                                                                    Text(
                                                                            text =
                                                                                    "${currentDay.classes.size}",
                                                                            style =
                                                                                    MaterialTheme
                                                                                            .typography
                                                                                            .labelMedium,
                                                                            fontWeight =
                                                                                    FontWeight.Bold,
                                                                            color =
                                                                                    MaterialTheme
                                                                                            .colorScheme
                                                                                            .onTertiaryContainer,
                                                                            modifier =
                                                                                    Modifier.padding(
                                                                                            horizontal =
                                                                                                    AppDimens.CompactGap,
                                                                                            vertical =
                                                                                                    1.dp
                                                                                    ),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    FilledTonalIconButton(
                                                            onClick = { showTimetablePage = true },
                                                            shapes = IconButtonDefaults.shapes(),
                                                            colors =
                                                                    IconButtonDefaults
                                                                            .filledTonalIconButtonColors(
                                                                                    containerColor =
                                                                                            MaterialTheme
                                                                                                    .colorScheme
                                                                                                    .surfaceContainerHigh,
                                                                                    contentColor =
                                                                                            MaterialTheme
                                                                                                    .colorScheme
                                                                                                    .primary,
                                                                            ),
                                                            modifier = Modifier.size(36.dp),
                                                    ) {
                                                        Text(
                                                                text = "↗",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium
                                                                                .copy(
                                                                                        fontSize =
                                                                                                15.sp
                                                                                ),
                                                                fontWeight = FontWeight.Bold,
                                                        )
                                                    }
                                                }
                                            }

                                            // Carousel: Horizontal swipe between days with layered
                                            // card deck overshadowing neighbors
                                            if (scheduleDays.isNotEmpty()) {
                                                item {
                                                    DayCarouselSchedule(
                                                            days = scheduleDays,
                                                            pagerState = horizontalPagerState,
                                                    )
                                                }
                                            }

                                            // Attendance Section: subject cycle card under the
                                            // Timeline
                                            item {
                                                AttendanceSection(
                                                        subjects = attendanceSubjects,
                                                        initialIndex = attendanceFocusIndex,
                                                        onOpenAttendance = {
                                                            showAttendancePage = true
                                                        },
                                                )
                                            }
                                        }
                                    }
                                }
                    }
        }

        // Sticky floating bottom navigation bar: hidden on modal subpages (Timetable, Attendance,
        // Grade History)
        // ponytail: sticky bottom container with soft gradient scrim prevents navbar from floating
        // adrift
        val hideBottomBar = showTimetablePage || showAttendancePage || showGradesPage
        AnimatedVisibility(
                visible = !hideBottomBar,
                enter =
                        fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                slideInVertically(
                                        MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
                                ) { it },
                exit =
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                slideOutVertically(
                                        MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
                                ) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            Brush.verticalGradient(
                                                    colors =
                                                            listOf(
                                                                    Color.Transparent,
                                                                    MaterialTheme.colorScheme
                                                                            .surface.copy(
                                                                            alpha = 0.85f
                                                                    ),
                                                                    MaterialTheme.colorScheme
                                                                            .surface,
                                                            ),
                                            ),
                                    )
                                    .padding(bottom = AppDimens.ItemGap, top = AppDimens.CompactGap),
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RebuildingScreen(
        step: String,
        progress: Float,
        onViewLogs: () -> Unit,
        modifier: Modifier = Modifier,
) {
    Box(
            modifier =
                    modifier.fillMaxSize()
                            .padding(AppDimens.SheetHorizontal)
                            .padding(bottom = AppDimens.FloatingNavClearance),
            contentAlignment = Alignment.Center,
    ) {
        Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(horizontal = AppDimens.SheetHorizontal, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
            ) {
                CircularWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                        text = "Rebuilding VTOP database…",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AppDimens.CompactGap))
                Text(
                        text = step.ifBlank { "Synchronizing database records…" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))
                LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(8.dp)
                                        .clip(MaterialTheme.shapes.extraSmall),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(AppDimens.CompactGap))
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                            text = "Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(AppDimens.SheetBottom))
                FilledTonalButton(
                        onClick = onViewLogs,
                        shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                            painter = painterResource(R.drawable.ic_code),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(AppDimens.CompactGap))
                    Text("View live logs in Settings")
                }
            }
        }
    }
}

/**
 * Organic shape card holding an intriguing campus question. Maintains its 1:1 aspect ratio so the
 * shape is never distorted, covering the question background. Shows only the question in the box;
 * user can tap it to cycle questions and shapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingDashboardNavBar(
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        modifier: Modifier = Modifier,
) {
    Surface(
            modifier = modifier.padding(horizontal = 28.dp).height(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            border =
                    BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    ),
    ) {
        Row(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(horizontal = AppDimens.CompactGap, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
        ) {
            val items =
                    listOf(
                            Pair(R.drawable.ic_home, "Home"),
                            Pair(R.drawable.ic_calendar, "Academic Calendar"),
                            Pair(R.drawable.ic_grade, "Marks"),
                            Pair(R.drawable.ic_settings, "Settings"),
                    )

            items.forEachIndexed { index, (iconRes, label) ->
                val isSelected = selectedTab == index
                val iconScale by
                        animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1f,
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                label = "tab_icon_$index",
                        )

                IconToggleButton(
                        checked = isSelected,
                        onCheckedChange = { onTabSelected(index) },
                        shapes = IconButtonDefaults.toggleableShapes(),
                        colors =
                                IconButtonDefaults.iconToggleButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor =
                                                MaterialTheme.colorScheme.primaryContainer,
                                        checkedContentColor =
                                                MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        modifier = Modifier.height(48.dp).weight(1f).padding(horizontal = 4.dp),
                ) {
                    Icon(
                            painter = painterResource(iconRes),
                            contentDescription = label,
                            modifier =
                                    Modifier.size(24.dp).graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                    )
                }
            }
        }
    }
}

// ponytail: fast lightweight ping to GitHub releases API; checks connectivity and updates
internal suspend fun pingGitHubRelease(): Boolean =
        withContext(Dispatchers.IO) {
            if (!UserDatabase.isOnline()) return@withContext false
            runCatching {
                        val request =
                                Request.Builder()
                                        .url(
                                                "https://api.github.com/repos/vtop-xceed/vtop-android/releases/latest"
                                        )
                                        .header("User-Agent", "vtop-android")
                                        .header("Accept", "application/vnd.github+json")
                                        .build()
                        githubPingHttpClient.newCall(request).execute().use { response ->
                            response.isSuccessful || response.code == 404
                        }
                    }
                    .getOrDefault(false)
        }

/**
 * Small fluid pill showing bridge status: every ongoing stage stays expanded with its label; on
 * success or full failure the result label stays visible for 3 seconds, then collapses to a green
 * or red dot. Tapping the dot re-expands the label for 4s.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FluidStatusPill(
        isReady: Boolean,
        label: String,
        readyLabel: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
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
    val pillColorEffects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()

    val backgroundColor by
            animateColorAsState(
                    targetValue =
                            when {
                                isError -> MaterialTheme.colorScheme.errorContainer
                                isReady && !userToggled -> StatusColors.Success
                                isReady && userToggled -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                    animationSpec = pillColorEffects,
                    label = "pillBg",
            )

    val contentColor by
            animateColorAsState(
                    targetValue =
                            when {
                                isError -> MaterialTheme.colorScheme.onErrorContainer
                                isReady && !userToggled -> Color.White
                                isReady && userToggled ->
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    animationSpec = pillColorEffects,
                    label = "pillContent",
            )

    val pillFadeEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val pillScaleSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    Box(
            contentAlignment = Alignment.Center,
            modifier =
                    modifier.animateContentSize(
                                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
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
                    fadeIn(pillFadeEffects) +
                            scaleIn(pillScaleSpatial) togetherWith
                            fadeOut(pillFadeEffects) +
                            scaleOut(pillFadeEffects)
                },
                label = "pillContent",
        ) { expanded ->
            if (expanded) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                                Modifier.height(20.dp).padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    MorphingStatusIcon(
                            isReady = isReady,
                            isError = isError,
                            contentColor = contentColor,
                            readyLabel = readyLabel,
                            readyIcon = readyIcon,
                            modifier = Modifier.size(10.dp),
                    )

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun OrganicQuestionCard(
        modifier: Modifier = Modifier,
) {
    var questionIndex by remember { mutableIntStateOf(SessionGreeting.questionIndex) }
    var shapeIndex by remember { mutableIntStateOf(SessionGreeting.shapeIndex) }
    var previousShapeIndex by remember { mutableIntStateOf(shapeIndex) }
    val currentQuestion = IntriguingQuestions[questionIndex.coerceIn(IntriguingQuestions.indices)]
    val morphProgress = remember { androidx.compose.animation.core.Animatable(1f) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(shapeIndex) {
        if (shapeIndex == previousShapeIndex) return@LaunchedEffect
        morphProgress.snapTo(0f)
        morphProgress.animateTo(1f, spatialSpec)
        previousShapeIndex = shapeIndex
    }
    val morph =
            remember(previousShapeIndex, shapeIndex) {
                androidx.graphics.shapes.Morph(
                        QuestionCardShapes[previousShapeIndex.coerceIn(QuestionCardShapes.indices)],
                        QuestionCardShapes[shapeIndex.coerceIn(QuestionCardShapes.indices)],
                )
            }
    val rawPath = remember { androidx.compose.ui.graphics.Path() }
    val shapeColor = MaterialTheme.colorScheme.secondaryContainer
    val interactionSource = remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by
            animateFloatAsState(
                    targetValue = if (isPressed) 0.94f else 1f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "questionPress",
            )

    Box(
            modifier = modifier,
            contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
                modifier =
                        Modifier.wrapContentSize(align = Alignment.CenterEnd, unbounded = true)
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
                                .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                ) {
                                    // user presses it if it feels intriguing
                                    questionIndex = (questionIndex + 1) % IntriguingQuestions.size
                                    shapeIndex = (shapeIndex + 1) % QuestionCardShapes.size
                                },
                contentAlignment = Alignment.Center,
        ) {
            val questionSlideSpatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
            val questionFadeEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            Box(
                    modifier = Modifier.fillMaxSize().padding(AppDimens.CompactPadding),
                    contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                        targetState = currentQuestion,
                        transitionSpec = {
                            (slideInVertically(questionSlideSpatial) { it / 3 } +
                                    fadeIn(questionFadeEffects)) togetherWith
                                    (slideOutVertically(questionSlideSpatial) { -it / 3 } +
                                            fadeOut(questionFadeEffects))
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
