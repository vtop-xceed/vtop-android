package dev.vtoppp.vtop.data

import org.jsoup.Jsoup
import java.util.Locale

data class CalendarDayEvent(
    val date: String, // ISO YYYY-MM-DD
    val dayNumber: Int,
    val monthYear: String,
    val dayOfWeek: String,
    val title: String,
    val subtitle: String = "",
    val eventCategory: String, // "INSTRUCTIONAL", "HOLIDAY", "EXAM", "REMOTE", "NO_CLASS", "INFO"
    val isHoliday: Boolean = false,
    val isInstructional: Boolean = false,
    val isExam: Boolean = false,
)

object AcademicCalendarParser {

    private val MONTH_MAP = mapOf(
        "JANUARY" to "01", "JAN" to "01",
        "FEBRUARY" to "02", "FEB" to "02",
        "MARCH" to "03", "MAR" to "03",
        "APRIL" to "04", "APR" to "04",
        "MAY" to "05",
        "JUNE" to "06", "JUN" to "06",
        "JULY" to "07", "JUL" to "07",
        "AUGUST" to "08", "AUG" to "08",
        "SEPTEMBER" to "09", "SEP" to "09",
        "OCTOBER" to "10", "OCT" to "10",
        "NOVEMBER" to "11", "NOV" to "11",
        "DECEMBER" to "12", "DEC" to "12",
    )

    private val DAYS_OF_WEEK = listOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
    )

    /**
     * Extracts available month dates from getDateForSemesterPreview or getListForSemester HTML.
     * Looks for `processViewCalendar('01-OCT-2026')` patterns.
     */
    fun extractMonthDates(html: String): List<String> {
        val regex = Regex("""processViewCalendar\s*\(\s*['"]([^'"]+)['"]\s*\)""", RegexOption.IGNORE_CASE)
        val list = mutableListOf<String>()
        for (m in regex.findAll(html)) {
            val dateStr = m.groupValues[1].trim()
            if (dateStr.isNotBlank() && !list.contains(dateStr)) {
                list.add(dateStr)
            }
        }
        return list
    }

    /**
     * Parses the VTOP calendar matrix HTML for a given month into structured [CalendarDayEvent]s.
     */
    fun parse(html: String, fallbackDate: String = ""): List<CalendarDayEvent> {
        val doc = Jsoup.parse(html)
        val h4Text = doc.select("table.calendar-table h4, h4").firstOrNull()?.text()?.trim() ?: ""

        var monthNum = "10"
        var yearStr = "2026"
        var displayMonthYear = "OCTOBER 2026"

        if (h4Text.isNotBlank()) {
            displayMonthYear = h4Text
            val parts = h4Text.split(" ").filter { it.isNotBlank() }
            if (parts.isNotEmpty()) {
                val mName = parts[0].uppercase(Locale.US)
                monthNum = MONTH_MAP[mName] ?: "10"
            }
            if (parts.size > 1) {
                yearStr = parts[1].filter { it.isDigit() }.ifBlank { "2026" }
            }
        } else if (fallbackDate.isNotBlank()) {
            val parts = fallbackDate.split("-")
            if (parts.size >= 3) {
                val mName = parts[1].uppercase(Locale.US)
                monthNum = MONTH_MAP[mName] ?: "10"
                yearStr = parts[2].filter { it.isDigit() }.ifBlank { "2026" }
                displayMonthYear = "$mName $yearStr"
            }
        }

        val table = doc.select("table.calendar-table, table").firstOrNull() ?: return emptyList()
        val rows = table.select("tr")
        if (rows.isEmpty()) return emptyList()

        val events = mutableListOf<CalendarDayEvent>()

        for (row in rows) {
            val cells = row.select("td")
            if (cells.isEmpty()) continue

            for (colIdx in cells.indices) {
                val cell = cells[colIdx]
                val spans = cell.select("span").map { it.text().trim() }.filter { it.isNotBlank() }
                if (spans.isEmpty()) continue

                val dayNum = spans[0].toIntOrNull() ?: continue
                val isoDate = String.format(Locale.US, "%s-%s-%02d", yearStr, monthNum, dayNum)
                val dow = if (colIdx < DAYS_OF_WEEK.size) DAYS_OF_WEEK[colIdx] else ""

                val rawNotes = mutableListOf<String>()
                for (s in spans.drop(1)) {
                    val clean = s.trim('(', ')', ' ', '\t', '\n')
                    if (clean.isBlank()) continue
                    // Skip irrelevant cohort notes if other General notes are present
                    if (clean.contains("Freshers", ignoreCase = true) ||
                        clean.contains("BArch", ignoreCase = true) ||
                        clean.contains("BBA", ignoreCase = true) ||
                        clean.contains("PG_MCA", ignoreCase = true)
                    ) {
                        continue
                    }
                    if (!rawNotes.contains(clean)) {
                        rawNotes.add(clean)
                    }
                }

                val fullText = rawNotes.joinToString(" ").lowercase(Locale.US)
                val isHoliday = fullText.contains("holiday") ||
                    fullText.contains("break") ||
                    fullText.contains("dussehra") ||
                    fullText.contains("jayanti") ||
                    fullText.contains("diwali")

                val isExam = fullText.contains("assessment test") ||
                    fullText.contains("exam") ||
                    fullText.contains("fat") ||
                    fullText.contains("cat") ||
                    fullText.contains("tee") ||
                    fullText.contains("term end")

                val isInstructional = fullText.contains("instructional day") && !fullText.contains("no instructional")

                val category = when {
                    isHoliday -> "HOLIDAY"
                    isExam -> "EXAM"
                    isInstructional -> "INSTRUCTIONAL"
                    fullText.contains("remote") -> "REMOTE"
                    fullText.contains("no instructional") -> "NO_CLASS"
                    else -> if (rawNotes.isEmpty()) "INSTRUCTIONAL" else "INFO"
                }

                val title = when {
                    isHoliday -> rawNotes.firstOrNull { n ->
                        val lower = n.lowercase(Locale.US)
                        lower.contains("dussehra") || lower.contains("jayanti") || lower.contains("diwali") || lower.contains("holiday") || lower.contains("break")
                    } ?: "Holiday"
                    isExam -> rawNotes.firstOrNull { n ->
                        val lower = n.lowercase(Locale.US)
                        lower.contains("exam") || lower.contains("cat") || lower.contains("fat") || lower.contains("tee") || lower.contains("assessment") || lower.contains("term end")
                    } ?: "Exam"
                    isInstructional -> "Instructional Day"
                    fullText.contains("remote") -> "Remote Learning"
                    fullText.contains("no instructional") -> "No Class"
                    else -> rawNotes.firstOrNull() ?: "Instructional Day"
                }

                val subtitle = rawNotes.firstOrNull { it != title }.orEmpty()

                events.add(
                    CalendarDayEvent(
                        date = isoDate,
                        dayNumber = dayNum,
                        monthYear = displayMonthYear,
                        dayOfWeek = dow,
                        title = title,
                        subtitle = subtitle,
                        eventCategory = category,
                        isHoliday = isHoliday,
                        isInstructional = isInstructional,
                        isExam = isExam,
                    ),
                )
            }
        }

        return events.sortedBy { it.date }
    }
}
