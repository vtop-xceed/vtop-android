package dev.vtoppp.vtop.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class AgendaRow(
    val startIso: String,
    val endIso: String,
    val dayCount: Int,
    val title: String,
    val subtitle: String,
    val category: String,
    val isPast: Boolean,
    val isToday: Boolean,
)

object CalendarAgenda {
    fun collapse(events: List<CalendarDayEvent>, todayIso: String): List<AgendaRow> {
        val sorted = events.sortedBy { it.date }
        val rows = mutableListOf<AgendaRow>()
        var runStart: LocalDate? = null
        var runEnd: LocalDate? = null
        var last: CalendarDayEvent? = null

        fun flush() {
            val s = runStart ?: return
            val e = runEnd ?: return
            val l = last ?: return
            rows.add(
                AgendaRow(
                    startIso = s.toString(),
                    endIso = e.toString(),
                    dayCount = ChronoUnit.DAYS.between(s, e).toInt() + 1,
                    title = l.title,
                    subtitle = l.subtitle,
                    category = l.eventCategory,
                    isPast = e.toString() < todayIso,
                    isToday = s.toString() <= todayIso && todayIso <= e.toString(),
                )
            )
            runStart = null; runEnd = null; last = null
        }

        for (event in sorted) {
            val d = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: continue
            val p = last
            val canMerge = p != null &&
                p.title == event.title && p.subtitle == event.subtitle &&
                p.eventCategory == event.eventCategory &&
                runEnd?.let { ChronoUnit.DAYS.between(it, d) == 1L } == true
            if (canMerge) {
                runEnd = d
                last = event
            } else {
                flush()
                runStart = d
                runEnd = d
                last = event
            }
        }
        flush()
        return rows
    }

    fun humanCategory(category: String): String = when (category.uppercase()) {
        "HOLIDAY" -> "Holiday"
        "EXAM" -> "Exam"
        "INSTRUCTIONAL" -> "Class"
        "REMOTE" -> "Remote"
        "NO_CLASS" -> "Holiday"
        "INFO" -> "Event"
        else -> category.lowercase().replaceFirstChar { it.uppercase() }
    }

    // Now/future special rows: todays continuing run, then upcoming ones
    fun upcoming(events: List<CalendarDayEvent>, todayIso: String, limit: Int = 5): List<AgendaRow> =
        collapse(events, todayIso)
            .filter { row ->
                val c = row.category.uppercase()
                row.endIso >= todayIso &&
                    (c == "HOLIDAY" || c == "EXAM" || c == "REMOTE" || c == "NO_CLASS" || c == "INFO")
            }
            .take(limit)

    // Next special ROW (not day) strictly after today's ongoing run
    fun nextSpecial(events: List<CalendarDayEvent>, todayIso: String): AgendaRow? =
        collapse(events, todayIso).firstOrNull { row ->
            val c = row.category.uppercase()
            row.startIso > todayIso && (c == "HOLIDAY" || c == "EXAM" || c == "REMOTE" || c == "NO_CLASS")
        }

    fun daysBetween(fromIso: String, toIso: String): Long? =
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(fromIso), LocalDate.parse(toIso)) }.getOrNull()
}