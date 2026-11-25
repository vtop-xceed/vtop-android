package dev.vtoppp.vtop.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vtoppp.vtop.data.SemesterAttended
import dev.vtoppp.vtop.data.TimetableEntry
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.ui.theme.AmoledCoursePalettes
import dev.vtoppp.vtop.ui.theme.AppDimens
import dev.vtoppp.vtop.ui.theme.AppDivider
import dev.vtoppp.vtop.ui.theme.AppPageHeader
import dev.vtoppp.vtop.ui.theme.AppSectionLabel
import dev.vtoppp.vtop.ui.theme.CoursePalette
import dev.vtoppp.vtop.ui.theme.DarkCoursePalettes
import dev.vtoppp.vtop.ui.theme.LightCoursePalettes

private val VENUE_REGEX = Regex("""^(AB\d*|LC|AR|CR|SJT|TT|MB|CB|SMV)[\s-]*\d{2,4}[A-Za-z]?$""", RegexOption.IGNORE_CASE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimetableDetailsPage(
    timetable: List<TimetableEntry>,
    faculties: Map<String, String>,
    onBack: () -> Unit,
    semesterOptions: List<SemesterAttended> = emptyList(),
    selectedSemesterId: String? = null,
    onSemesterChange: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val isSyncing by UserDatabase.isSyncingFlow.collectAsStateWithLifecycle()
    val uiData = remember(timetable) { buildTimetableUiData(timetable) }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f
    val isAmoled = isDark && surfaceColor == Color.Black

    val coursePalettes = remember(isDark, isAmoled) {
        when {
            !isDark -> LightCoursePalettes
            isAmoled -> AmoledCoursePalettes
            else -> DarkCoursePalettes
        }
    }
    val defaultPalette = coursePalettes[0]
    val courseColorMap = remember(uiData.distinctCodes, coursePalettes) {
        uiData.distinctCodes.mapIndexed { index, code ->
            code to coursePalettes[index % coursePalettes.size]
        }.toMap()
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
                    title = "Timetable",
                    onBack = onBack,
                    trailing = {
                        Text(
                            text = formatLastRefreshTimestamp(uiData.lastUpdatedMs),
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
                if (timetable.isEmpty()) {
                    Text(
                        text = "Nothing cached yet — pull down to sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }

                AppSectionLabel("Subjects & faculty")
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.largeIncreased,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.35f else 0.18f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        uiData.courses.forEachIndexed { index, course ->
                            val faculty = faculties[course.codeKey] ?: faculties[course.code] ?: "—"
                            val palette = courseColorMap[course.codeKey] ?: defaultPalette
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
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
                                Spacer(Modifier.width(AppDimens.CompactPadding))
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
                                Spacer(Modifier.width(AppDimens.CompactGap))
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.widthIn(max = 130.dp),
                                ) {
                                    if (course.slots.isNotBlank()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
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
                                            shape = MaterialTheme.shapes.small,
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
                            if (index < uiData.courses.lastIndex) {
                                AppDivider()
                            }
                        }
                    }
                }

                Spacer(Modifier.height(AppDimens.SectionGap))
                AppSectionLabel("Weekly grid")
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.largeIncreased,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.35f else 0.18f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Fixed sticky DAY column on the left
                        Column(modifier = Modifier.width(52.dp)) {
                            GridHead("DAY", 52.dp)
                            (1..6).forEach { dayOfWeek ->
                                DayCell(dayName(dayOfWeek), isAmoled)
                            }
                        }

                        // Horizontally scrollable timetable grid
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            Row(modifier = Modifier.height(36.dp)) {
                                uiData.morningColumns.forEach { time ->
                                    GridTimeHead(
                                        startTime = time,
                                        endTime = uiData.endTimeByStart[time].orEmpty(),
                                        width = 92.dp,
                                    )
                                }
                                if (uiData.hasLunchSplit) {
                                    LunchHead(28.dp)
                                }
                                uiData.afternoonColumns.forEach { time ->
                                    GridTimeHead(
                                        startTime = time,
                                        endTime = uiData.endTimeByStart[time].orEmpty(),
                                        width = 92.dp,
                                    )
                                }
                            }
                            (1..6).forEach { dayOfWeek ->
                                Row {
                                    uiData.morningColumns.forEach { time ->
                                        val cell = uiData.byDayAndTime["$dayOfWeek|$time"]
                                        GridCell(cell, cell?.let { courseColorMap[it.codeKey] } ?: defaultPalette, isDark, isAmoled)
                                    }
                                    if (uiData.hasLunchSplit) {
                                        LunchCell(28.dp)
                                    }
                                    uiData.afternoonColumns.forEach { time ->
                                        val cell = uiData.byDayAndTime["$dayOfWeek|$time"]
                                        GridCell(cell, cell?.let { courseColorMap[it.codeKey] } ?: defaultPalette, isDark, isAmoled)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildTimetableUiData(timetable: List<TimetableEntry>): TimetableUiData {
    var lastUpdatedMs = 0L
    val starts = LinkedHashSet<String>(timetable.size)
    val endTimeByStart = HashMap<String, String>(StandardEndTimes)
    val seenExplicitEnd = HashSet<String>()
    val byDayAndTime = HashMap<String, GridCellUi>(timetable.size)

    for (entry in timetable) {
        if (entry.updatedAtMs > lastUpdatedMs) lastUpdatedMs = entry.updatedAtMs
        val normStart = normalizeTime(entry.startTime)
        if (normStart.isNotBlank()) {
            starts.add(normStart)
            if (entry.endTime.isNotBlank() && seenExplicitEnd.add(normStart)) {
                endTimeByStart[normStart] = normalizeTime(entry.endTime)
            }
        }
        val codeKey = entry.courseCode.trim().uppercase()
        byDayAndTime["${entry.dayOfWeek}|$normStart"] = GridCellUi(
            code = entry.courseCode,
            codeKey = codeKey,
            slotLabel = entry.activeSlot.ifBlank { entry.slots.firstOrNull().orEmpty() },
            venue = cleanVenue(entry.roomBlock, entry.roomNumber),
        )
    }

    val courses = timetable.groupBy { it.courseCode }.map { (code, entries) ->
        CourseRow(
            code = code,
            codeKey = code.trim().uppercase(),
            title = entries.firstOrNull { it.courseTitle.isNotBlank() }?.courseTitle.orEmpty(),
            slots = entries.flatMap { it.slots }.distinct().joinToString(" + "),
            venue = entries.asSequence().map { cleanVenue(it.roomBlock, it.roomNumber) }
                .firstOrNull { it.isNotBlank() }.orEmpty(),
        )
    }.sortedBy { it.code }

    val distinctCodes = courses.map { it.codeKey }.distinct().sorted()

    // ponytail: always show all standard VIT periods from 08:30 to 18:00 (ends 19:30) even when empty
    val timeColumns = (StandardTimeSlots + starts).distinct().sortedBy { parseTime(it) }
    val morningColumns = timeColumns.filter { parseTime(it) < 13 * 60 }
    val afternoonColumns = timeColumns.filter { parseTime(it) >= 13 * 60 }

    return TimetableUiData(
        lastUpdatedMs = lastUpdatedMs,
        courses = courses,
        distinctCodes = distinctCodes,
        morningColumns = morningColumns,
        afternoonColumns = afternoonColumns,
        hasLunchSplit = morningColumns.isNotEmpty() && afternoonColumns.isNotEmpty(),
        endTimeByStart = endTimeByStart,
        byDayAndTime = byDayAndTime,
    )
}

private fun parseTime(time: String): Int {
    val clean = time.trim().uppercase()
    val isPm = clean.endsWith("PM")
    val isAm = clean.endsWith("AM")
    val digitsOnly = clean.replace("AM", "").replace("PM", "").trim()
    val parts = digitsOnly.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 2) return Int.MAX_VALUE
    var hours = parts[0]
    val minutes = parts[1]
    if (isPm && hours < 12) hours += 12
    if (isAm && hours == 12) hours = 0
    return hours * 60 + minutes
}

private fun normalizeTime(time: String): String {
    val mins = parseTime(time)
    if (mins == Int.MAX_VALUE) return time.trim()
    val h = mins / 60
    val m = mins % 60
    return "%02d:%02d".format(h, m)
}

private fun cleanVenue(block: String, room: String): String {
    val b = block.trim()
    val r = room.trim()
    val raw = if (b.isNotBlank() && r.isNotBlank()) "$b-$r" else b.ifBlank { r }
    if (raw.length > 12 || !isRealVenue(raw)) return ""
    return raw
}

private fun isRealVenue(text: String): Boolean {
    val t = text.trim()
    if (t.length > 12) return false
    return t.matches(VENUE_REGEX)
}

private fun dayName(dayOfWeek: Int) = when (dayOfWeek) {
    1 -> "MON"; 2 -> "TUE"; 3 -> "WED"; 4 -> "THU"; 5 -> "FRI"; 6 -> "SAT"; else -> "SUN"
}

private val StandardTimeSlots = listOf(
    "08:30",
    "10:05",
    "11:40",
    "13:15",
    "14:50",
    "16:25",
    "18:00",
)

private val StandardEndTimes = mapOf(
    "08:00" to "08:50",
    "08:30" to "10:00",
    "09:00" to "09:50",
    "10:05" to "11:35",
    "11:00" to "12:30",
    "11:40" to "13:10",
    "13:15" to "14:45",
    "14:00" to "14:50",
    "14:50" to "16:20",
    "15:00" to "15:50",
    "16:00" to "16:50",
    "16:25" to "17:55",
    "18:00" to "19:30",
)

@Composable
private fun GridHead(label: String, width: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(36.dp)
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
private fun GridTimeHead(startTime: String, endTime: String, width: Dp) {
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
private fun LunchHead(width: Dp) {
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
private fun LunchCell(width: Dp) {
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
    val cellShape = MaterialTheme.shapes.small
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(52.dp)
            .height(72.dp)
            .padding(2.dp)
            .clip(cellShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.25f else 0.15f), cellShape),
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
    cell: GridCellUi?,
    palette: CoursePalette,
    isDark: Boolean,
    isAmoled: Boolean,
) {
    val cellShape = MaterialTheme.shapes.small
    if (cell == null) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .height(72.dp)
                .padding(2.dp)
                .clip(cellShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = if (isAmoled) 0.15f else 0.45f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAmoled) 0.25f else 0.15f), cellShape),
        )
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(92.dp)
            .height(72.dp)
            .padding(2.dp)
            .clip(cellShape)
            .background(palette.container)
            .border(
                1.dp,
                palette.border.copy(alpha = if (isAmoled) 0.7f else if (!isDark) 0.4f else 0.5f),
                cellShape,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = cell.slotLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.ExtraBold,
                color = palette.onContainer,
                maxLines = 1,
            )
            Text(
                text = cell.code,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Black,
                color = palette.onContainer.copy(alpha = 0.95f),
                maxLines = 1,
            )
            if (cell.venue.isNotBlank()) {
                Text(
                    text = cell.venue,
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
    val codeKey: String,
    val title: String,
    val slots: String,
    val venue: String,
)

private data class GridCellUi(
    val code: String,
    val codeKey: String,
    val slotLabel: String,
    val venue: String,
)

private data class TimetableUiData(
    val lastUpdatedMs: Long,
    val courses: List<CourseRow>,
    val distinctCodes: List<String>,
    val morningColumns: List<String>,
    val afternoonColumns: List<String>,
    val hasLunchSplit: Boolean,
    val endTimeByStart: Map<String, String>,
    val byDayAndTime: Map<String, GridCellUi>,
)
