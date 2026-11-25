package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AttendanceEntry
import dev.vtoppp.vtop.data.AttendanceSession
import dev.vtoppp.vtop.data.SemesterAttended
import dev.vtoppp.vtop.data.TimetableEntry
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.ui.theme.AppDimens
import dev.vtoppp.vtop.ui.theme.AppDivider
import dev.vtoppp.vtop.ui.theme.AppPageHeader
import dev.vtoppp.vtop.ui.theme.Motion
import dev.vtoppp.vtop.ui.theme.StatusColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SESSION_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
private val REFRESH_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val REFRESH_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttendancePage(
    entries: List<AttendanceEntry>,
    sessions: List<AttendanceSession>,
    timetable: List<TimetableEntry> = emptyList(),
    faculties: Map<String, String>,
    onBack: () -> Unit,
    semesterOptions: List<SemesterAttended> = emptyList(),
    selectedSemesterId: String? = null,
    onSemesterChange: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadCourseDetails: (AttendanceEntry) -> Unit = {},
    loadingCourseKeys: Set<String> = emptySet(),
) {
    val isSyncing by UserDatabase.isSyncingFlow.collectAsStateWithLifecycle()
    val courseSessionsByKey = remember(entries, sessions) {
        val byCourse = sessions.groupBy { it.courseCode.uppercase() }
        entries.associate { entry ->
            val codeKey = entry.courseCode.uppercase()
            val key = "${entry.semesterSubId}_$codeKey"
            val filtered = byCourse[codeKey].orEmpty().filter {
                it.semesterSubId.isBlank() || entry.semesterSubId.isBlank() || it.semesterSubId == entry.semesterSubId
            }
            key to filtered
        }
    }
    val lastUpdatedMs = remember(entries, sessions, selectedSemesterId) {
        val entryMax = entries.maxOfOrNull { it.updatedAtMs } ?: 0L
        val semId = selectedSemesterId ?: entries.firstOrNull()?.semesterSubId.orEmpty()
        val sessionMax = if (semId.isNotBlank()) {
            sessions.filter { it.semesterSubId == semId }.maxOfOrNull { it.updatedAtMs } ?: 0L
        } else 0L
        maxOf(entryMax, sessionMax)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SyncingBar(visible = isSyncing)
        PullToRefreshBox(
            state = rememberPullToRefreshState(),
            isRefreshing = isSyncing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicator = {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.ScreenHorizontal)
                    .padding(top = AppDimens.ScreenTop, bottom = AppDimens.ScreenBottom),
            ) {
                AppPageHeader(
                    title = "Attendance",
                    onBack = onBack,
                    trailing = {
                        Text(
                            text = formatLastRefreshTimestamp(lastUpdatedMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
                            maxLines = 1,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                if (semesterOptions.isNotEmpty()) {
                    Spacer(Modifier.height(AppDimens.CompactGap))
                    SemesterFilterRow(
                        options = semesterOptions,
                        selectedId = selectedSemesterId,
                        onSelect = onSemesterChange,
                    )
                    Spacer(Modifier.height(AppDimens.CompactGap))
                } else {
                    Spacer(Modifier.height(AppDimens.ItemGap))
                }
                if (entries.isEmpty()) {
                    Text(
                        text = "Nothing cached yet — pull down to sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.CompactGap)) {
                    entries.forEach { entry ->
                        val courseKey = "${entry.semesterSubId}_${entry.courseCode.uppercase()}"
                        val courseSessions = courseSessionsByKey[courseKey].orEmpty()
                        val faculty = faculties[entry.courseCode]?.takeIf { it.isNotBlank() } ?: entry.facultyName
                        AttendanceDetailCard(
                            entry = entry,
                            faculty = faculty,
                            sessions = courseSessions,
                            isLoadingDetails = courseKey in loadingCourseKeys,
                            onExpandLoad = { onLoadCourseDetails(entry) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AttendanceDetailCard(
    entry: AttendanceEntry,
    faculty: String,
    sessions: List<AttendanceSession>,
    isLoadingDetails: Boolean = false,
    onExpandLoad: () -> Unit = {},
) {
    var expanded by rememberSaveable(entry.semesterSubId, entry.courseCode) { mutableStateOf(false) }
    LaunchedEffect(expanded, entry.semesterSubId, entry.courseCode) {
        if (expanded && sessions.isEmpty()) {
            onExpandLoad()
        }
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevronRotation",
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f
    val isAmoled = isDark && surfaceColor == Color.Black
    val percentage = entry.attendancePercentage.toInt()

    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.35f else 0.18f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Main row: Left details take all remaining width with ellipsis; Right side holds the prominent stat block & chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Left Column: Course code, Slot, Title, Faculty - all truncating cleanly with ...
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = AppDimens.CompactPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = entry.courseCode,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (entry.slot.isNotBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = entry.slot,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Text(
                        text = entry.courseTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (faculty.isNotBlank()) {
                        Text(
                            text = faculty,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(Modifier.height(15.dp))
                    }
                }

                // Right Side: Uniform morphing stat block & expand chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.CompactGap),
                ) {
                    AttendanceStatBadge(
                        percentage = percentage,
                        attendedClasses = entry.attendedClasses,
                        totalClasses = entry.totalClasses,
                        modifier = Modifier.width(76.dp),
                        compact = true,
                    )

                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = if (expanded) "Collapse history" else "Expand history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            // Expanded class history list
            if (expanded) {
                AppDivider(modifier = Modifier.padding(top = 10.dp, bottom = AppDimens.CompactGap))

                if (sessions.isEmpty()) {
                    Text(
                        text = if (isLoadingDetails) "Loading class history…" else "No class records cached for this course.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    val sortedSessions = remember(sessions) {
                        sessions.sortedByDescending { it.date }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        sortedSessions.forEach { session ->
                            ClassHistoryRow(session = session, isDark = isDark, isAmoled = isAmoled)
                        }
                    }
                }
            }
        }
    }
}

/** Shared morphing attendance badge used in both [AttendancePage] and [AttendanceSection]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AttendanceStatBadge(
    percentage: Int,
    attendedClasses: Int,
    totalClasses: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val tone = StatusColors.attendanceBadge(percentage)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val statBg by animateColorAsState(targetValue = tone.container, animationSpec = effects, label = "statBgMorph")
    val statBorder by animateColorAsState(targetValue = tone.border, animationSpec = effects, label = "statBorderMorph")
    val statFg by animateColorAsState(targetValue = tone.content, animationSpec = effects, label = "statFgMorph")
    val badgeCornerRadius by animateDpAsState(
        targetValue = if (percentage < 75) 12.dp else 16.dp,
        animationSpec = spatial,
        label = "badgeShapeMorph",
    )

    Surface(
        shape = RoundedCornerShape(badgeCornerRadius),
        color = statBg,
        border = BorderStroke(1.dp, statBorder),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 6.dp else 8.dp,
                    vertical = if (compact) 7.dp else 6.dp,
                ),
        ) {
            AnimatedContent(
                targetState = percentage,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn(tween(Motion.Fast)))
                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(Motion.Fast)))
                },
                label = "percentageMorph",
            ) { targetPct ->
                Text(
                    text = "$targetPct%",
                    style = if (compact) {
                        MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp)
                    } else {
                        MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
                    },
                    fontWeight = FontWeight.Black,
                    color = statFg,
                    textAlign = TextAlign.Center,
                )
            }
            if (!compact) Spacer(Modifier.height(2.dp))
            Text(
                text = "$attendedClasses/$totalClasses",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 10.sp else 10.5.sp),
                fontWeight = FontWeight.Bold,
                color = statFg.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ClassHistoryRow(
    session: AttendanceSession,
    isDark: Boolean,
    isAmoled: Boolean,
) {
    val isPresent = session.status.contains("present", ignoreCase = true)
    val isAbsent = session.status.contains("absent", ignoreCase = true)
    val isOnDuty = session.status.contains("on duty", ignoreCase = true) || session.status.contains("od", ignoreCase = true)

    val statusTone = when {
        isPresent -> StatusColors.attendanceTone(100, isDark, isAmoled)
        isAbsent -> StatusColors.attendanceTone(0, isDark, isAmoled)
        isOnDuty -> StatusColors.infoTone(isDark, isAmoled)
        else -> StatusColors.attendanceTone(75, isDark, isAmoled)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.CompactGap),
        ) {
            Text(
                text = formatSessionDate(session.date),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (session.slot.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = session.slot,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = statusTone.container,
        ) {
            Text(
                text = session.status,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = statusTone.content,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

private fun formatSessionDate(dateIso: String): String {
    return runCatching {
        SESSION_DATE_FORMATTER.format(LocalDate.parse(dateIso))
    }.getOrDefault(dateIso)
}

internal fun formatLastRefreshTimestamp(updatedAtMs: Long): String {
    if (updatedAtMs <= 0L) return "Not synced"
    val zone = ZoneId.systemDefault()
    val updated = Instant.ofEpochMilli(updatedAtMs).atZone(zone)
    val fmt = if (updated.toLocalDate() == LocalDate.now(zone)) REFRESH_TIME_FORMATTER else REFRESH_DATE_TIME_FORMATTER
    return "Updated ${fmt.format(updated)}"
}
