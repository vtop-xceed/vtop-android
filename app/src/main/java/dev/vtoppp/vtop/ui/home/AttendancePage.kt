package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AttendanceEntry
import dev.vtoppp.vtop.data.AttendanceSession
import dev.vtoppp.vtop.data.SemesterAttended
import dev.vtoppp.vtop.data.TimetableEntry
import dev.vtoppp.vtop.ui.theme.AttendanceGreen
import dev.vtoppp.vtop.ui.theme.Motion
import java.time.LocalDate

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
) {
    val sessionsByCourse = remember(sessions) {
        sessions.groupBy { it.courseCode.uppercase() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 12.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Attendance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (semesterOptions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SemesterFilterRow(
                options = semesterOptions,
                selectedId = selectedSemesterId,
                onSelect = onSemesterChange,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (entries.isEmpty()) {
            Text(
                text = "Nothing cached yet — pull down to sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                val courseSessions = sessionsByCourse[entry.courseCode.uppercase()].orEmpty()
                    .filter { it.semesterSubId.isBlank() || entry.semesterSubId.isBlank() || it.semesterSubId == entry.semesterSubId }
                val faculty = faculties[entry.courseCode]?.takeIf { it.isNotBlank() } ?: entry.facultyName
                AttendanceDetailCard(
                    entry = entry,
                    faculty = faculty,
                    sessions = courseSessions,
                )
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
) {
    var expanded by rememberSaveable(entry.courseCode) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chevronRotation",
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f
    val isAmoled = isDark && surfaceColor == Color.Black

    val percentage = entry.attendancePercentage.toInt()
    val isDanger = percentage < 75
    val isWarning = percentage in 75..79
    val targetStatBg = when {
        isDanger -> when {
            !isDark -> Color(0xFFFEE2E2)
            isAmoled -> Color(0xFF260A0A)
            else -> Color(0xFF381414)
        }
        isWarning -> when {
            !isDark -> Color(0xFFFEF3C7)
            isAmoled -> Color(0xFF261904)
            else -> Color(0xFF382808)
        }
        else -> when {
            !isDark -> Color(0xFFDCFCE7)
            isAmoled -> Color(0xFF072111)
            else -> Color(0xFF0F2E1B)
        }
    }
    val targetStatBorder = when {
        isDanger -> Color(0xFFEF4444).copy(alpha = if (isAmoled) 0.65f else 0.45f)
        isWarning -> Color(0xFFF59E0B).copy(alpha = if (isAmoled) 0.65f else 0.45f)
        else -> AttendanceGreen.copy(alpha = if (isAmoled) 0.60f else 0.45f)
    }
    val targetStatFg = when {
        isDanger -> if (!isDark) Color(0xFFB91C1C) else Color(0xFFF87171)
        isWarning -> if (!isDark) Color(0xFFB45309) else Color(0xFFFBBF24)
        else -> if (!isDark) Color(0xFF15803D) else Color(0xFF4ADE80)
    }

    // Morphing animations for badge colors, shape, and card transitions
    val statBg by animateColorAsState(
        targetValue = targetStatBg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statBgMorph",
    )
    val statBorder by animateColorAsState(
        targetValue = targetStatBorder,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statBorderMorph",
    )
    val statFg by animateColorAsState(
        targetValue = targetStatFg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statFgMorph",
    )
    val badgeCornerRadius by animateDpAsState(
        targetValue = if (isDanger) 10.dp else 14.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "badgeShapeMorph",
    )

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.35f else 0.18f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
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
                        .padding(end = 12.dp),
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
                                shape = RoundedCornerShape(6.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(badgeCornerRadius),
                        color = statBg,
                        border = BorderStroke(1.dp, statBorder),
                        modifier = Modifier.width(76.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 7.dp),
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
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                    fontWeight = FontWeight.Black,
                                    color = statFg,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Text(
                                text = "${entry.attendedClasses}/${entry.totalClasses}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = statFg.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

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
                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.35f else 0.2f),
                )

                if (sessions.isEmpty()) {
                    Text(
                        text = "No class records cached for this course.",
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

@Composable
private fun ClassHistoryRow(
    session: AttendanceSession,
    isDark: Boolean,
    isAmoled: Boolean,
) {
    val isPresent = session.status.contains("present", ignoreCase = true)
    val isAbsent = session.status.contains("absent", ignoreCase = true)
    val isOnDuty = session.status.contains("on duty", ignoreCase = true) || session.status.contains("od", ignoreCase = true)

    val (statusBg, statusFg) = when {
        isPresent -> {
            val bg = if (!isDark) Color(0xFFDCFCE7) else if (isAmoled) Color(0xFF072111) else Color(0xFF0F2E1B)
            val fg = if (!isDark) Color(0xFF15803D) else Color(0xFF4ADE80)
            bg to fg
        }
        isAbsent -> {
            val bg = if (!isDark) Color(0xFFFEE2E2) else if (isAmoled) Color(0xFF260A0A) else Color(0xFF381414)
            val fg = if (!isDark) Color(0xFFB91C1C) else Color(0xFFF87171)
            bg to fg
        }
        isOnDuty -> {
            val bg = if (!isDark) Color(0xFFDBEAFE) else if (isAmoled) Color(0xFF081C36) else Color(0xFF102544)
            val fg = if (!isDark) Color(0xFF1D4ED8) else Color(0xFF60A5FA)
            bg to fg
        }
        else -> {
            val bg = if (!isDark) Color(0xFFFEF3C7) else if (isAmoled) Color(0xFF261904) else Color(0xFF382808)
            val fg = if (!isDark) Color(0xFFB45309) else Color(0xFFFBBF24)
            bg to fg
        }
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formatSessionDate(session.date),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (session.slot.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
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
            shape = RoundedCornerShape(6.dp),
            color = statusBg,
        ) {
            Text(
                text = session.status,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = statusFg,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

private fun formatSessionDate(dateIso: String): String {
    return runCatching {
        val date = LocalDate.parse(dateIso)
        val dayName = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val monthName = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "$dayName, ${date.dayOfMonth} $monthName"
    }.getOrDefault(dateIso)
}
