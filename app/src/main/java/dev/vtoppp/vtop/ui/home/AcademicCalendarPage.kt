package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AgendaRow
import dev.vtoppp.vtop.data.CalendarAgenda
import dev.vtoppp.vtop.data.CalendarDayEvent
import dev.vtoppp.vtop.ui.theme.Motion

@Composable
internal fun CategoryPill(category: String) {
    val (pillBg, pillFg) = when (category.uppercase()) {
        "HOLIDAY", "NO_CLASS" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "EXAM" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "REMOTE" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "INSTRUCTIONAL" -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = RoundedCornerShape(8.dp), color = pillBg) {
        Text(
            text = CalendarAgenda.humanCategory(category),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = pillFg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// "JUNE 2026" -> "Jun '26"
internal fun shortMonthChip(m: String): String {
    val parts = m.trim().split(Regex("\\s+"))
    val mon = (parts.firstOrNull() ?: m).take(3).lowercase(java.util.Locale.US).replaceFirstChar { it.uppercase(java.util.Locale.US) }
    val yr = parts.getOrNull(1)?.takeLast(2).orEmpty()
    return if (yr.isBlank()) mon else "$mon '$yr"
}

internal fun monthDay(iso: String): String =
    runCatching {
        java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
    }.getOrDefault(iso)

internal fun rangeLabel(startIso: String, endIso: String): String {
    val s = runCatching { java.time.LocalDate.parse(startIso) }.getOrNull() ?: return startIso
    if (startIso == endIso) return monthDay(startIso)
    val e = runCatching { java.time.LocalDate.parse(endIso) }.getOrNull()
        ?: return monthDay(endIso)
    return if (s.month == e.month) {
        s.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)) + " - " + e.dayOfMonth
    } else {
        monthDay(startIso) + " - " + monthDay(endIso)
    }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Upcoming",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        row.isToday -> "NOW"
        daysUntil == 1L -> "TOMORROW"
        daysUntil > 1L -> "IN $daysUntil DAYS"
        else -> ""
    }
    val dateLabel = if (row.dayCount > 1) rangeLabel(row.startIso, row.endIso) else monthDay(row.startIso)

    Surface(
        shape = RoundedCornerShape(18.dp),
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
                )
                Spacer(Modifier.weight(1f))
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

// ponytail: single translucent fill per day, EXAM wins over HOLIDAY over REMOTE over INFO over INSTRUCTIONAL
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

@Composable
internal fun MonthCalendarCard(
    events: List<CalendarDayEvent>,
    monthKey: String,
    todayIso: String,
) {
    val first = remember(monthKey) {
        runCatching {
            val parts = monthKey.trim().split(Regex("\\s+"))
            val month = when (parts.firstOrNull()?.uppercase()) {
                "JANUARY" -> 1; "FEBRUARY" -> 2; "MARCH" -> 3; "APRIL" -> 4; "MAY" -> 5
                "JUNE" -> 6; "JULY" -> 7; "AUGUST" -> 8; "SEPTEMBER" -> 9
                "OCTOBER" -> 10; "NOVEMBER" -> 11; "DECEMBER" -> 12
                else -> null
            }
            val year = parts.getOrNull(1)?.toIntOrNull()
            if (month != null && year != null) java.time.LocalDate.of(year, month, 1) else null
        }.getOrNull()
    } ?: return

    val monthEvents = remember(events, monthKey) {
        events.filter { it.monthYear.equals(monthKey, ignoreCase = true) }
    }
    val byDay = remember(monthEvents) {
        monthEvents.groupBy { it.dayNumber }.mapValues { (_, v) -> dayEvent(v) }
    }
    val today = remember(todayIso) { runCatching { java.time.LocalDate.parse(todayIso) }.getOrNull() }

    val daysInMonth = first.lengthOfMonth()
    val startOffset = first.dayOfWeek.value % 7 // SUNDAY-first grid (matches VTOP matrix columns)
    val examColor = MaterialTheme.colorScheme.primaryContainer
    val holidayColor = MaterialTheme.colorScheme.errorContainer
    val remoteColor = MaterialTheme.colorScheme.tertiaryContainer
    val infoColor = MaterialTheme.colorScheme.tertiaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Weekday header Su Mo Tu We Th Fr Sa
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
        val totalCells = startOffset + daysInMonth
        val rowsCount = (totalCells + 6) / 7
        for (r in 0 until rowsCount) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val day = if (index >= startOffset && index < totalCells) index - startOffset + 1 else null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            val row = byDay[day]
                            val date = first.withDayOfMonth(day)
                            val isToday = today == date
                            val past = today != null && date.isBefore(today)
                            val fill by animateColorAsState(
                                targetValue = when (row?.eventCategory?.uppercase()) {
                                    "EXAM" -> examColor.copy(alpha = 0.35f)
                                    "HOLIDAY", "NO_CLASS" -> holidayColor.copy(alpha = 0.35f)
                                    "REMOTE" -> remoteColor.copy(alpha = 0.4f)
                                    "INFO" -> infoColor.copy(alpha = 0.35f)
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                                animationSpec = tween(Motion.Quick),
                                label = "tileFill",
                            )
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(fill)
                                    .then(
                                        if (isToday) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(10.dp),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (past && !isToday) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LegendDot(color = holidayColor, label = "Holiday")
            LegendDot(color = examColor, label = "Exam")
            LegendDot(color = remoteColor, label = "Remote")
            LegendDot(color = infoColor, label = "Event")
        }
    }
}

@Composable
internal fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.85f),
            modifier = Modifier.size(10.dp),
        ) {}
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Reconstructed after accidental deletion; layout matches the verified Calendar tab
@Composable
internal fun AcademicCalendarPage(
    events: List<CalendarDayEvent>,
    onRefresh: () -> Unit = {},
) {
    val isSyncing by dev.vtoppp.vtop.data.UserDatabase.isSyncingFlow.collectAsState()
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
    val todayIso = remember { java.time.LocalDate.now().toString() }


    val initialMonthIndex = remember(months, selectedMonth) {
        months.indexOfFirst { it.equals(selectedMonth, ignoreCase = true) }.coerceAtLeast(0)
    }
    val monthListState = rememberLazyListState(initialFirstVisibleItemIndex = initialMonthIndex)

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
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 84.dp),
        ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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

        Spacer(Modifier.height(12.dp))
        if (selectedMonth.isNotBlank()) {
            AnimatedContent(
                targetState = selectedMonth,
                modifier = Modifier.fillMaxWidth().animateContentSize(tween(Motion.Content)),
                transitionSpec = {
                    fadeIn(tween(Motion.Quick)) + slideInHorizontally(tween(Motion.Content)) { it / 4 } togetherWith
                        fadeOut(tween(Motion.Fast)) + slideOutHorizontally(tween(Motion.Fast)) { -it / 4 }
                },
                label = "calendarMonth",
            ) { monthKey ->
                if (monthKey.isNotBlank()) {
                    MonthCalendarCard(
                        events = events,
                        monthKey = monthKey,
                        todayIso = todayIso,
                    )
                }
            }
        }
        if (months.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                state = monthListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(months) { m ->
                    val isSelected = m.equals(selectedMonth, ignoreCase = true)
                    val chipColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = tween(Motion.Color),
                        label = "monthChip",
                    )
                    val chipContent by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(Motion.Color),
                        label = "monthChipContent",
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = chipColor,
                        modifier = Modifier.clickable { selectedMonth = m },
                    ) {
                        Text(
                            text = shortMonthChip(m),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = chipContent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        EventsSection(events = events, todayIso = todayIso)

        if (events.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(visible = events.isEmpty(), enter = fadeIn(tween(Motion.Content))) {
                Text(
                    text = "No calendar records cached yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        }
    }
    }
}
