package dev.vtoppp.vtop.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AttendanceEntry
import dev.vtoppp.vtop.data.AttendanceSession
import dev.vtoppp.vtop.data.TimetableEntry
import dev.vtoppp.vtop.ui.theme.StatusColors
import java.time.LocalDate

@Composable
internal fun AttendancePage(
    entries: List<AttendanceEntry>,
    sessions: List<AttendanceSession>,
    timetable: List<TimetableEntry>,
    faculties: Map<String, String>,
    onBack: () -> Unit,
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
                Text(
                    text = "🟢 present · 🔵 OD · 🔴 absent · 🟡 not posted · ⚫ no class",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
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
                val faculty = faculties[entry.courseCode]?.takeIf { it.isNotBlank() } ?: entry.facultyName
                val scheduledDays = remember(timetable, courseSessions, entry.courseCode) {
                    val fromTt = timetable
                        .filter { it.courseCode.equals(entry.courseCode, ignoreCase = true) }
                        .map { it.dayOfWeek }
                        .toSet()
                    if (fromTt.isNotEmpty()) fromTt else {
                        courseSessions.mapNotNull {
                            runCatching { LocalDate.parse(it.date).dayOfWeek.value }.getOrNull()
                        }.toSet()
                    }
                }
                AttendanceDetailCard(
                    entry = entry,
                    faculty = faculty,
                    scheduledDays = scheduledDays,
                    sessions = courseSessions,
                )
            }
        }
    }
}

@Composable
private fun AttendanceDetailCard(
    entry: AttendanceEntry,
    faculty: String,
    scheduledDays: Set<Int>,
    sessions: List<AttendanceSession>,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f
    val isAmoled = isDark && surfaceColor == Color.Black

    val percentage = entry.attendancePercentage.toInt()
    val isDanger = percentage < 75
    val isWarning = percentage in 75..79
    val (statBg, statBorder, statFg) = when {
        isDanger -> when {
            !isDark -> Triple(Color(0xFFFEE2E2), Color(0xFFEF4444).copy(alpha = 0.45f), Color(0xFFB91C1C))
            isAmoled -> Triple(Color(0xFF260A0A), Color(0xFFEF4444).copy(alpha = 0.65f), Color(0xFFF87171))
            else -> Triple(Color(0xFF381414), Color(0xFFEF4444).copy(alpha = 0.5f), Color(0xFFF87171))
        }
        isWarning -> when {
            !isDark -> Triple(Color(0xFFFEF3C7), Color(0xFFF59E0B).copy(alpha = 0.45f), Color(0xFFB45309))
            isAmoled -> Triple(Color(0xFF261904), Color(0xFFF59E0B).copy(alpha = 0.65f), Color(0xFFFBBF24))
            else -> Triple(Color(0xFF382808), Color(0xFFF59E0B).copy(alpha = 0.5f), Color(0xFFFBBF24))
        }
        else -> when {
            !isDark -> Triple(Color(0xFFDCFCE7), Color(0xFF22C55E).copy(alpha = 0.45f), Color(0xFF15803D))
            isAmoled -> Triple(Color(0xFF072111), Color(0xFF22C55E).copy(alpha = 0.60f), Color(0xFF4ADE80))
            else -> Triple(Color(0xFF0F2E1B), Color(0xFF22C55E).copy(alpha = 0.4f), Color(0xFF4ADE80))
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.35f else 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = entry.courseCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
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
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statBg,
                    border = BorderStroke(1.dp, statBorder),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.Black,
                            color = statFg,
                        )
                        Text(
                            text = "· ${entry.attendedClasses}/${entry.totalClasses}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = statFg.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            Text(
                text = entry.courseTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (faculty.isNotBlank()) {
                Text(
                    text = faculty,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            } else {
                Spacer(Modifier.height(7.dp))
            }
            WeekAttendancePills(
                courseCode = entry.courseCode,
                scheduledDays = scheduledDays,
                sessions = sessions,
            )
        }
    }
}

private val WeekInitials = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun WeekAttendancePills(
    courseCode: String,
    scheduledDays: Set<Int>,
    sessions: List<AttendanceSession>,
) {
    val today = remember { LocalDate.now() }
    val todayDayOfWeek = today.dayOfWeek.value
    val monday = remember(today) { today.minusDays((todayDayOfWeek - 1).toLong()) }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f
    val isAmoled = isDark && surfaceColor == Color.Black

    val outerPillColor = when {
        !isDark -> MaterialTheme.colorScheme.surfaceContainer
        isAmoled -> Color.Black
        else -> Color(0xFF101012)
    }
    val outerPillBorder = when {
        !isDark -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        isAmoled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
        else -> Color(0xFF232328)
    }

    Surface(
        shape = CircleShape,
        color = outerPillColor,
        border = BorderStroke(1.dp, outerPillBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..7).forEach { dayIndex ->
                val hasClass = scheduledDays.contains(dayIndex)
                val dateForDay = monday.plusDays((dayIndex - 1).toLong()).toString()
                val sessionForDate = sessions.firstOrNull { it.date == dateForDay }
                val session = sessionForDate ?: sessions.filter {
                    runCatching { LocalDate.parse(it.date).dayOfWeek.value == dayIndex }.getOrDefault(false)
                }.maxByOrNull { it.date }

                val state = when {
                    !hasClass -> PillState.NO_CLASS
                    session == null -> PillState.NOT_POSTED
                    session.status.contains("present", ignoreCase = true) -> PillState.PRESENT
                    session.status.contains("absent", ignoreCase = true) -> PillState.ABSENT
                    session.status.contains("on duty", ignoreCase = true) || session.status.contains("od", ignoreCase = true) -> PillState.ON_DUTY
                    else -> PillState.NOT_POSTED
                }

                val isToday = dayIndex == todayDayOfWeek
                val (bg, border, fg) = when (state) {
                    PillState.NO_CLASS -> when {
                        !isDark -> Triple(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.outline,
                        )
                        isAmoled -> Triple(
                            Color.Black,
                            Color(0xFF1F1F24),
                            Color(0xFF6B7280),
                        )
                        else -> Triple(
                            Color(0xFF151518),
                            Color(0xFF1F1F24),
                            Color(0xFF505058),
                        )
                    }
                    PillState.NOT_POSTED -> when {
                        !isDark -> Triple(
                            Color(0xFFFEF3C7),
                            if (isToday) Color(0xFFD97706) else Color(0xFFF59E0B).copy(alpha = 0.4f),
                            Color(0xFFB45309),
                        )
                        isAmoled -> Triple(
                            Color(0xFF221803),
                            if (isToday) Color(0xFFFBBF24) else Color(0xFFF59E0B).copy(alpha = 0.6f),
                            Color(0xFFFBBF24),
                        )
                        else -> Triple(
                            Color(0xFF382B05),
                            if (isToday) Color(0xFFFBBF24) else Color(0xFFF59E0B).copy(alpha = 0.5f),
                            Color(0xFFFBBF24),
                        )
                    }
                    PillState.PRESENT -> when {
                        !isDark -> Triple(
                            Color(0xFFDCFCE7),
                            if (isToday) Color(0xFF16A34A) else Color(0xFF22C55E).copy(alpha = 0.4f),
                            Color(0xFF15803D),
                        )
                        isAmoled -> Triple(
                            Color(0xFF071F0F),
                            if (isToday) Color(0xFF4ADE80) else Color(0xFF22C55E).copy(alpha = 0.6f),
                            Color(0xFF4ADE80),
                        )
                        else -> Triple(
                            Color(0xFF0F3617),
                            if (isToday) Color(0xFF4ADE80) else Color(0xFF22C55E).copy(alpha = 0.5f),
                            Color(0xFF4ADE80),
                        )
                    }
                    PillState.ON_DUTY -> when {
                        !isDark -> Triple(
                            Color(0xFFDBEAFE),
                            if (isToday) Color(0xFF2563EB) else Color(0xFF3B82F6).copy(alpha = 0.4f),
                            Color(0xFF1D4ED8),
                        )
                        isAmoled -> Triple(
                            Color(0xFF071B33),
                            if (isToday) Color(0xFF60A5FA) else Color(0xFF3B82F6).copy(alpha = 0.6f),
                            Color(0xFF60A5FA),
                        )
                        else -> Triple(
                            Color(0xFF0C2B4E),
                            if (isToday) Color(0xFF60A5FA) else Color(0xFF3B82F6).copy(alpha = 0.5f),
                            Color(0xFF60A5FA),
                        )
                    }
                    PillState.ABSENT -> when {
                        !isDark -> Triple(
                            Color(0xFFFEE2E2),
                            if (isToday) Color(0xFFDC2626) else Color(0xFFEF4444).copy(alpha = 0.4f),
                            Color(0xFFB91C1C),
                        )
                        isAmoled -> Triple(
                            Color(0xFF270909),
                            if (isToday) Color(0xFFF87171) else Color(0xFFEF4444).copy(alpha = 0.6f),
                            Color(0xFFF87171),
                        )
                        else -> Triple(
                            Color(0xFF3E1212),
                            if (isToday) Color(0xFFF87171) else Color(0xFFEF4444).copy(alpha = 0.5f),
                            Color(0xFFF87171),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .border(
                            width = if (isToday && state != PillState.NO_CLASS) 1.5.dp else 1.dp,
                            color = border,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = WeekInitials[dayIndex - 1],
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = fg,
                    )
                }
            }
        }
    }
}

private enum class PillState {
    NO_CLASS,
    NOT_POSTED,
    PRESENT,
    ON_DUTY,
    ABSENT,
}
