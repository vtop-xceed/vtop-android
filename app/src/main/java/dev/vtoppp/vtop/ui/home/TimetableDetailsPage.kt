package dev.vtoppp.vtop.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.SemesterAttended
import dev.vtoppp.vtop.data.TimetableEntry
import dev.vtoppp.vtop.ui.theme.AmoledCoursePalettes
import dev.vtoppp.vtop.ui.theme.CoursePalette
import dev.vtoppp.vtop.ui.theme.DarkCoursePalettes
import dev.vtoppp.vtop.ui.theme.LightCoursePalettes

@Composable
internal fun TimetableDetailsPage(
    timetable: List<TimetableEntry>,
    faculties: Map<String, String>,
    onBack: () -> Unit,
    semesterOptions: List<SemesterAttended> = emptyList(),
    selectedSemesterId: String? = null,
    onSemesterChange: (String) -> Unit = {},
) {
    val courses = remember(timetable) {
        timetable.groupBy { it.courseCode }.map { (code, entries) ->
            CourseRow(
                code = code,
                title = entries.firstOrNull { it.courseTitle.isNotBlank() }?.courseTitle.orEmpty(),
                slots = entries.flatMap { it.slots }.distinct().joinToString(" + "),
                venue = entries.firstOrNull { it.roomNumber.isNotBlank() }
                    ?.let { "${it.roomBlock}-${it.roomNumber}" }.orEmpty(),
            )
        }.sortedBy { it.code }
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f
    val isAmoled = isDark && surfaceColor == Color.Black

    val distinctCodes = remember(courses) {
        courses.map { it.code.trim().uppercase() }.distinct().sorted()
    }
    val coursePalettes = remember(isDark, isAmoled) {
        when {
            !isDark -> LightCoursePalettes
            isAmoled -> AmoledCoursePalettes
            else -> DarkCoursePalettes
        }
    }
    val courseColorMap = remember(distinctCodes, coursePalettes) {
        distinctCodes.mapIndexed { index, code ->
            code to coursePalettes[index % coursePalettes.size]
        }.toMap()
    }
    val timeColumns = remember(timetable) {
        val allStarts = (listOf("08:30") + timetable.map { it.startTime }).distinct()
        allStarts.sortedBy { parseTime(it) }
    }
    val morningColumns = remember(timeColumns) {
        timeColumns.filter { parseTime(it) < 13 * 60 }
    }
    val afternoonColumns = remember(timeColumns) {
        timeColumns.filter { parseTime(it) >= 13 * 60 }
    }
    val hasLunchSplit = remember(morningColumns, afternoonColumns) {
        morningColumns.isNotEmpty() && afternoonColumns.isNotEmpty()
    }
    val byDayAndTime = remember(timetable) {
        timetable.associateBy { "${it.dayOfWeek}|${it.startTime}" }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
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
            Text(
                text = "Timetable",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (semesterOptions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SemesterFilterRow(
                options = semesterOptions,
                selectedId = selectedSemesterId,
                onSelect = onSemesterChange,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (timetable.isEmpty()) {
            Text(
                text = "Nothing cached yet — pull down to sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        SectionTitle("Subjects & faculty")
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.35f else 0.18f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                courses.forEachIndexed { index, course ->
                    val faculty = faculties[course.code]?.takeIf { it.isNotBlank() } ?: "—"
                    val defaultPalette = if (!isDark) LightCoursePalettes[0] else if (isAmoled) AmoledCoursePalettes[0] else DarkCoursePalettes[0]
                    val palette = courseColorMap[course.code.trim().uppercase()] ?: defaultPalette
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.container,
                            border = BorderStroke(1.dp, palette.border.copy(alpha = if (isAmoled) 0.7f else if (!isDark) 0.4f else 0.5f)),
                            modifier = Modifier
                                .width(74.dp)
                                .height(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = course.code,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Black,
                                    color = palette.onContainer,
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = course.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = faculty,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            if (course.slots.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    Text(
                                        text = course.slots,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1,
                                    )
                                }
                            }
                            if (course.venue.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ) {
                                    Text(
                                        text = course.venue,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    if (index < courses.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Weekly grid")
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.35f else 0.18f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Row(modifier = Modifier.height(36.dp)) {
                    GridHead("DAY", 52.dp)
                    morningColumns.forEach { time ->
                        GridTimeHead(
                            startTime = time,
                            endTime = getEndTimeForStart(time, timetable),
                            width = 92.dp,
                        )
                    }
                    if (hasLunchSplit) {
                        LunchHead(28.dp)
                    }
                    afternoonColumns.forEach { time ->
                        GridTimeHead(
                            startTime = time,
                            endTime = getEndTimeForStart(time, timetable),
                            width = 92.dp,
                        )
                    }
                }
                (1..6).forEach { dayOfWeek ->
                    Row {
                        DayCell(dayName(dayOfWeek), isAmoled)
                        morningColumns.forEach { time ->
                            GridCell(byDayAndTime["$dayOfWeek|$time"], courseColorMap, isDark, isAmoled)
                        }
                        if (hasLunchSplit) {
                            LunchCell(28.dp)
                        }
                        afternoonColumns.forEach { time ->
                            GridCell(byDayAndTime["$dayOfWeek|$time"], courseColorMap, isDark, isAmoled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun parseTime(time: String): Int {
    val parts = time.split(":").mapNotNull { it.toIntOrNull() }
    return if (parts.size == 2) parts[0] * 60 + parts[1] else Int.MAX_VALUE
}

private fun dayName(dayOfWeek: Int) = when (dayOfWeek) {
    1 -> "MON"; 2 -> "TUE"; 3 -> "WED"; 4 -> "THU"; 5 -> "FRI"; 6 -> "SAT"; else -> "SUN"
}

private val StandardEndTimes = mapOf(
    "08:30" to "10:00",
    "10:05" to "11:35",
    "11:40" to "13:10",
    "13:15" to "14:45",
    "14:50" to "16:20",
    "16:25" to "17:55",
    "18:00" to "19:30",
)

private fun getEndTimeForStart(startTime: String, timetable: List<TimetableEntry>): String {
    return timetable.firstOrNull { it.startTime == startTime && it.endTime.isNotBlank() }?.endTime
        ?: StandardEndTimes[startTime].orEmpty()
}

@Composable
private fun GridHead(label: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun GridTimeHead(startTime: String, endTime: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = startTime,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (endTime.isNotBlank()) {
                Text(
                    text = endTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LunchHead(width: androidx.compose.ui.unit.Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Text(
            text = "L",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LunchCell(width: androidx.compose.ui.unit.Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun DayCell(label: String, isAmoled: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(52.dp)
            .height(72.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.25f else 0.15f), RoundedCornerShape(6.dp)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GridCell(
    entry: TimetableEntry?,
    courseColorMap: Map<String, CoursePalette>,
    isDark: Boolean,
    isAmoled: Boolean,
) {
    if (entry == null) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .height(72.dp)
                .padding(2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = if (isAmoled) 0.15f else 0.45f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.25f else 0.15f), RoundedCornerShape(6.dp)),
        )
        return
    }
    val defaultPalette = if (!isDark) LightCoursePalettes[0] else if (isAmoled) AmoledCoursePalettes[0] else DarkCoursePalettes[0]
    val palette = courseColorMap[entry.courseCode.trim().uppercase()] ?: defaultPalette
    val venue = listOf(entry.roomBlock, entry.roomNumber).filter { it.isNotBlank() }.joinToString("-")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(92.dp)
            .height(72.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.container)
            .border(
                1.dp,
                palette.border.copy(alpha = if (isAmoled) 0.7f else if (!isDark) 0.4f else 0.5f),
                RoundedCornerShape(6.dp),
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = entry.activeSlot.ifBlank { entry.slots.firstOrNull().orEmpty() },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.ExtraBold,
                color = palette.onContainer,
                maxLines = 1,
            )
            Text(
                text = entry.courseCode,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Black,
                color = palette.onContainer.copy(alpha = 0.95f),
                maxLines = 1,
            )
            if (venue.isNotBlank()) {
                Text(
                    text = venue,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    fontWeight = FontWeight.Medium,
                    color = palette.onContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
    }
}

private data class CourseRow(
    val code: String,
    val title: String,
    val slots: String,
    val venue: String,
)
