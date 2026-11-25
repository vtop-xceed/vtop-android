@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.vtoppp.vtop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AgendaRow
import dev.vtoppp.vtop.data.CALENDAR_SEMESTER_ID
import dev.vtoppp.vtop.data.CalendarAgenda
import dev.vtoppp.vtop.data.CalendarDayEvent
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.ui.theme.AppDimens
import dev.vtoppp.vtop.ui.theme.AppModalBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val FALLBACK_SEMESTER = "Fall Semester 2026-27"
private val WHITESPACE_REGEX = Regex("\\s+")
private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

@Composable
internal fun CategoryPill(category: String) {
    val (pillBg, pillFg) = categoryColors(category)
    Surface(shape = MaterialTheme.shapes.small, color = pillBg) {
        Text(
            text = CalendarAgenda.humanCategory(category),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = pillFg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** One place decides every category colour: tile fills, dots, pills and legend. */
@Composable
private fun categoryColors(category: String): Pair<Color, Color> = when (category.uppercase()) {
    "HOLIDAY", "NO_CLASS" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    "EXAM" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    "REMOTE" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    "INFO" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun monthDay(iso: String): String =
    runCatching {
        LocalDate.parse(iso).format(MONTH_DAY_FORMATTER)
    }.getOrDefault(iso)

internal fun rangeLabel(startIso: String, endIso: String): String {
    val s = runCatching { LocalDate.parse(startIso) }.getOrNull() ?: return startIso
    if (startIso == endIso) return monthDay(startIso)
    val e = runCatching { LocalDate.parse(endIso) }.getOrNull()
        ?: return monthDay(endIso)
    return if (s.month == e.month) {
        s.format(MONTH_DAY_FORMATTER) + " - ${e.dayOfMonth}"
    } else {
        monthDay(startIso) + " - " + monthDay(endIso)
    }
}

/** "AUGUST 2026" -> the first day of that month. */
internal fun parseMonthKey(monthKey: String): LocalDate? {
    val parts = monthKey.trim().split(WHITESPACE_REGEX)
    val month = when (parts.firstOrNull()?.uppercase(Locale.US)) {
        "JANUARY" -> 1; "FEBRUARY" -> 2; "MARCH" -> 3; "APRIL" -> 4; "MAY" -> 5
        "JUNE" -> 6; "JULY" -> 7; "AUGUST" -> 8; "SEPTEMBER" -> 9
        "OCTOBER" -> 10; "NOVEMBER" -> 11; "DECEMBER" -> 12
        else -> null
    }
    val year = parts.getOrNull(1)?.toIntOrNull()
    return if (month != null && year != null) LocalDate.of(year, month, 1) else null
}

/** Today, refreshed when the app is open across midnight. */
@Composable
private fun rememberTodayIso(): String {
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) {
        while (true) {
            val millis = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).toMillis()
            delay(millis.coerceAtLeast(1_000L) + 1_000L)
            today = LocalDate.now().toString()
        }
    }
    return today
}

@Composable
internal fun EventsSection(
    events: List<CalendarDayEvent>,
    todayIso: String,
) {
    val upcoming = remember(events, todayIso) {
        CalendarAgenda.upcoming(events, todayIso, limit = 5)
    }
    if (upcoming.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Upcoming",
            style = MaterialTheme.typography.titleLargeEmphasized,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(AppDimens.CompactGap))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(upcoming, key = { it.startIso + "|" + it.category + "|" + it.title }) { row ->
                EventCard(row = row, todayIso = todayIso)
            }
        }
    }
}

@Composable
internal fun EventCard(
    row: AgendaRow,
    todayIso: String,
) {
    val daysUntil = CalendarAgenda.daysBetween(todayIso, row.startIso) ?: 0L
    val daysLabel = when {
        row.isToday && row.dayCount > 1 -> "ONGOING"
        row.isToday -> "TODAY"
        daysUntil == 1L -> "TOMORROW"
        daysUntil > 1L -> "IN $daysUntil DAYS"
        else -> ""
    }
    val dateLabel = if (row.dayCount > 1) rangeLabel(row.startIso, row.endIso) else monthDay(row.startIso)

    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(210.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = daysLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                CategoryPill(category = row.category)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = row.title.ifBlank { "Instructional Day" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Most severe category wins when a day carries more than one event. */
internal fun dayEvent(events: List<CalendarDayEvent>): CalendarDayEvent? =
    events.minWithOrNull(
        compareBy { row ->
            when (row.eventCategory.uppercase()) {
                "EXAM" -> 0
                "HOLIDAY", "NO_CLASS" -> 1
                "REMOTE" -> 2
                "INFO" -> 3
                else -> 4
            }
        },
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AcademicCalendarPage(
    events: List<CalendarDayEvent>,
    onRefresh: () -> Unit = {},
) {
    val isSyncing by UserDatabase.isSyncingFlow.collectAsStateWithLifecycle()
    val todayIso = rememberTodayIso()
    val scope = rememberCoroutineScope()

    val months = remember(events) {
        events.map { it.monthYear }.distinct()
            .filter { it.isNotBlank() }
            .sortedBy { parseMonthKey(it) }
    }
    val eventsByDate = remember(events) {
        events.groupBy { it.date }
            .mapNotNull { (date, day) -> dayEvent(day)?.let { date to it } }
            .toMap()
    }
    val currentMonthKey = remember {
        MONTH_YEAR_FORMATTER.format(LocalDate.now()).uppercase(Locale.US)
    }
    val semesterName = remember(events) { UserDatabase.semesterName(CALENDAR_SEMESTER_ID) } ?: FALLBACK_SEMESTER

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { months.size })
    var openedOnCurrentMonth by remember { mutableStateOf(false) }
    LaunchedEffect(months.size) {
        if (months.isEmpty() || openedOnCurrentMonth) return@LaunchedEffect
        openedOnCurrentMonth = true
        val idx = months.indexOfFirst { it.equals(currentMonthKey, ignoreCase = true) }
        if (idx >= 0 && pagerState.currentPage != idx) pagerState.scrollToPage(idx)
    }

    var sheetDate by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState(),
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        indicator = {},
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SyncingBar(visible = isSyncing)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.ScreenHorizontal)
                    .padding(top = AppDimens.ScreenTop, bottom = AppDimens.FloatingNavClearance),
            ) {
                Text(
                    text = "Academic Calendar",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = semesterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Spacer(Modifier.height(AppDimens.ItemGap))

                if (months.isEmpty()) {
                    Text(
                        text = "No calendar records cached yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    // Month header outside the pager: the title and arrows stay put while
                    // the grid swipes sideways.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = months.getOrNull(pagerState.settledPage) ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage((pagerState.settledPage - 1).coerceAtLeast(0))
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = "Previous month",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(180f),
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage((pagerState.settledPage + 1).coerceAtMost(months.size - 1))
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = "Next month",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(AppDimens.CompactGap))

                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        MonthCalendarCard(
                            eventsByDate = eventsByDate,
                            monthKey = months[page],
                            todayIso = todayIso,
                            onDayClick = { iso -> sheetDate = iso },
                        )
                    }
                }

                Spacer(Modifier.height(AppDimens.SectionGap))
                EventsSection(events = events, todayIso = todayIso)
            }
        }
    }

    val openDate = sheetDate
    if (openDate != null) {
        val dayEvents = events.filter { it.date == openDate }
        AppModalBottomSheet(onDismissRequest = { sheetDate = null }) {
            Text(
                text = rangeLabel(openDate, openDate),
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(AppDimens.ItemGap))
            dayEvents.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryPill(category = e.eventCategory)
                    Spacer(Modifier.width(AppDimens.ItemGap))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = e.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (e.subtitle.isNotBlank()) {
                            Text(
                                text = e.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (dayEvents.none { it.subtitle.isNotBlank() }) {
                Text(
                    text = "No note for this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * Month grid on a rolling six-week window: the card keeps one height for every
 * month, and the spillover days from the neighbouring months fill the fifth-row
 * gap instead of leaving a blank band.
 */
@Composable
internal fun MonthCalendarCard(
    eventsByDate: Map<String, CalendarDayEvent>,
    monthKey: String,
    todayIso: String,
    onDayClick: (String) -> Unit,
) {
    val first = remember(monthKey) { parseMonthKey(monthKey) } ?: return
    val today = remember(todayIso) { runCatching { LocalDate.parse(todayIso) }.getOrNull() }
    // Sunday-first grid (VTOP's own column order): Sunday must land in column 0,
    // so the offset is dayOfWeek % 7, not dayOfWeek - 1.
    val windowStart = remember(first) { first.minusDays((first.dayOfWeek.value % 7).toLong()) }

    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(AppDimens.CompactPadding)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            for (r in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val date = windowStart.plusDays((r * 7 + c).toLong())
                        DayCell(
                            date = date,
                            inMonth = date.month == first.month && date.year == first.year,
                            event = eventsByDate[date.toString()],
                            isToday = date == today,
                            onClick = { onDayClick(date.toString()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            CalendarLegend()
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    event: CalendarDayEvent?,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = event?.eventCategory?.uppercase().orEmpty()
    val fill = when (category) {
        "HOLIDAY" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f)
        "EXAM" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        "REMOTE" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f)
        "INSTRUCTIONAL", "INFO" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
        else -> Color.Transparent
    }
    val numberColor = when {
        !inMonth && event == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
        category == "NO_CLASS" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val description = buildString {
        append(date.format(MONTH_DAY_FORMATTER))
        append(", ")
        append(
            when (category) {
                "HOLIDAY" -> "holiday"
                "EXAM" -> "exam"
                "REMOTE" -> "remote class"
                "NO_CLASS" -> "no class"
                "INFO" -> "event"
                "INSTRUCTIONAL" -> "class day"
                else -> "no entry"
            },
        )
    }
    val dayShape = MaterialTheme.shapes.medium

    Column(
        modifier = modifier
            .height(40.dp)
            .semantics { contentDescription = description }
            .clickable(enabled = event != null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(dayShape)
                .background(fill)
                .then(
                    if (isToday) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, dayShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = numberColor,
            )
            // A noted day that is neither class, holiday nor exam: badge it
            if (category == "INFO") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}

@Composable
private fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
    ) {
        LegendTile(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f), "Holiday")
        LegendTile(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f), "Exam")
        LegendTile(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f), "Remote")
        LegendTile(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), "Class")
        LegendEvent(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), "Event")
    }
}

@Composable
private fun LegendTile(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendEvent(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .padding(1.dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
