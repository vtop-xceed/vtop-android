package dev.vtoppp.vtop.data

import org.jsoup.Jsoup

object TimetableParser {

    data class RawCourse(
        val code: String,
        val title: String,
        val type: String,
        val slot: String,
        val venue: String,
        val faculty: String,
    )

    /**
     * Parses VTOP timetable HTML into a list of [TimetableEntry].
     * Supports both the day-wise timetable grid and the registered course list table.
     */
    fun parse(html: String, regNo: String, semesterSubId: String = ""): List<TimetableEntry> {
        if (html.isBlank()) return emptyList()

        try {
            val doc = Jsoup.parse(html)
            val courses = parseCourseListTable(doc)
            val courseMap = courses.associateBy { it.code }

            // 1. Try parsing day-wise matrix grid table if available
            val gridEntries = parseGridTable(doc, regNo, semesterSubId, courseMap)
            if (gridEntries.isNotEmpty()) {
                return gridEntries
            }

            // 2. Otherwise parse course list table and map via slot definitions
            val entries = mutableListOf<TimetableEntry>()
            for (course in courses) {
                val slots = course.slot.split("+", "-").map { it.trim() }.filter { it.isNotBlank() }
                val (block, room) = parseVenue(course.venue)

                for (s in slots) {
                    val timings = SlotTimings.getTimings(s)
                    for (t in timings) {
                        entries.add(
                            TimetableEntry(
                                regNo = regNo,
                                semesterSubId = semesterSubId,
                                dayOfWeek = t.dayOfWeek,
                                startTime = t.startTime,
                                endTime = t.endTime,
                                roomBlock = block,
                                roomNumber = room,
                                courseCode = course.code,
                                courseTitle = course.title,
                                slots = slots,
                                activeSlot = s,
                                updatedAtMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }

            return entries
        } catch (e: Exception) {
            AppLog.d("timetable-parser: parse failed: ${e.message}")
            return emptyList()
        }
    }

    /** Parses VTOP day grid table (Monday-Friday rows with time headers). */
    private fun parseGridTable(
        doc: org.jsoup.nodes.Document,
        regNo: String,
        semesterSubId: String = "",
        courseMap: Map<String, RawCourse> = emptyMap(),
    ): List<TimetableEntry> {
        val entries = mutableListOf<TimetableEntry>()
        val tables = doc.select("table")

        for (table in tables) {
            val rows = table.select("tr")
            if (rows.isEmpty()) continue

            // Check for VTOP 2-row Start/End header
            var timeHeaders = mutableListOf<Pair<String, String>>()
            val timeRegex = Regex("""\b(\d{1,2}:\d{2})\b""")
            val row0Times = rows[0].select("th, td").map { it.text().trim() }.filter { timeRegex.matches(it) }

            if (row0Times.isNotEmpty() && rows.size > 1) {
                val row1Times = rows[1].select("th, td").map { it.text().trim() }.filter { timeRegex.matches(it) }
                if (row0Times.size == row1Times.size) {
                    timeHeaders = row0Times.zip(row1Times).toMutableList()
                }
            }

            // Fallback: Check for single-row range headers e.g. "09:00 - 10:30"
            if (timeHeaders.isEmpty()) {
                val headerCells = table.select("thead tr th, tr:first-child th, tr:first-child td")
                val rangeRegex = Regex("""(\d{1,2}:\d{2}(?:\s*[AP]M)?)\s*[-–to]+\s*(\d{1,2}:\d{2}(?:\s*[AP]M)?)""", RegexOption.IGNORE_CASE)
                for (h in headerCells) {
                    val m = rangeRegex.find(h.text().trim())
                    if (m != null) {
                        timeHeaders.add(Pair(m.groupValues[1].trim(), m.groupValues[2].trim()))
                    }
                }
            }

            if (timeHeaders.isEmpty()) continue

            for (row in rows) {
                val cells = row.select("td, th")
                if (cells.size < 2) continue

                val dayName = cells[0].text().trim().uppercase()
                val dayOfWeek = when {
                    dayName.startsWith("MON") -> 1
                    dayName.startsWith("TUE") -> 2
                    dayName.startsWith("WED") -> 3
                    dayName.startsWith("THU") -> 4
                    dayName.startsWith("FRI") -> 5
                    dayName.startsWith("SAT") -> 6
                    dayName.startsWith("SUN") -> 7
                    else -> 0
                }
                if (dayOfWeek == 0) continue

                val dayCells = cells.drop(1).filter {
                    val t = it.text().trim().uppercase()
                    t != "THEORY" && t != "LAB" && t != "LUNCH"
                }

                val colCount = minOf(timeHeaders.size, dayCells.size)
                for (col in 0 until colCount) {
                    val cellText = dayCells[col].text().trim()
                    if (cellText.isBlank() || cellText == "-" || cellText.equals("NIL", ignoreCase = true)) continue

                    val (start, end) = timeHeaders[col]

                    // VTOP cell format: E11-CSE3015-LTP-AR-103-ALL
                    val vtopMatch = Regex("""^([A-Z0-9]+)-([A-Z]{3,4}\d{3,4}[A-Z]?)-([A-Z]+)-([A-Za-z0-9_-]+)-(\d+[A-Za-z]?)-ALL$""")
                        .find(cellText)
                    if (vtopMatch != null) {
                        val activeSlot = vtopMatch.groupValues[1]
                        val code = vtopMatch.groupValues[2]
                        val block = vtopMatch.groupValues[4]
                        val room = vtopMatch.groupValues[5]
                        val rawCourse = courseMap[code]
                        val title = rawCourse?.title ?: code
                        val allSlots = rawCourse?.slot?.split("+")?.map { it.trim() }?.filter { it.isNotBlank() }
                            ?: listOf(activeSlot)

                        entries.add(
                            TimetableEntry(
                                regNo = regNo,
                                semesterSubId = semesterSubId,
                                dayOfWeek = dayOfWeek,
                                startTime = start,
                                endTime = end,
                                roomBlock = block,
                                roomNumber = room,
                                courseCode = code,
                                courseTitle = title,
                                slots = allSlots,
                                activeSlot = activeSlot,
                                updatedAtMs = System.currentTimeMillis(),
                            ),
                        )
                        continue
                    }

                    // Generic cell format: "CSE2005 - Operating Systems - A11 - SJT-401"
                    val parts = cellText.split(Regex("""\s+[-–—/]\s+|\n+""")).map { it.trim() }.filter { it.isNotBlank() }
                    val code = parts.firstOrNull { it.matches(Regex("""[A-Z]{3,4}\d{3,4}[A-Z]?""")) } ?: continue
                    val slot = parts.firstOrNull { SlotTimings.isSlot(it) } ?: ""
                    val venueStr = parts.lastOrNull { isVenue(it) } ?: parts.lastOrNull { it.any { c -> c.isDigit() } } ?: "AB-101"
                    val (block, room) = parseVenue(venueStr)
                    val rawCourse = courseMap[code]
                    val title = rawCourse?.title ?: if (parts.size > 1 && parts[1] != slot) parts[1] else code
                    val allSlots = rawCourse?.slot?.split("+")?.map { it.trim() }?.filter { it.isNotBlank() }
                        ?: if (slot.isNotBlank()) listOf(slot) else emptyList()

                    entries.add(
                        TimetableEntry(
                            regNo = regNo,
                            semesterSubId = semesterSubId,
                            dayOfWeek = dayOfWeek,
                            startTime = start,
                            endTime = end,
                            roomBlock = block,
                            roomNumber = room,
                            courseCode = code,
                            courseTitle = title,
                            slots = allSlots,
                            activeSlot = slot,
                            updatedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
        return entries
    }

    /** Parses VTOP course list table (columns: Code, Title, Slot, Venue, etc.). */
    private fun parseCourseListTable(doc: org.jsoup.nodes.Document): List<RawCourse> {
        val courses = mutableListOf<RawCourse>()
        // Select specifically the registered courses table (avoiding matrix grid)
        val table = doc.select("table").firstOrNull { t ->
            val txt = t.text()
            txt.contains("Course") && !txt.contains("THEORY") && !txt.contains("Start")
        } ?: doc.select("table").firstOrNull() ?: return emptyList()

        val rows = table.select("tr")
        for (row in rows) {
            val cells = row.select("td")
            if (cells.size < 5) continue

            val rowText = row.text()
            val codeMatch = Regex("""\b([A-Z]{3,4}\d{3,4}[A-Z]?)\b""").find(rowText) ?: continue
            val code = codeMatch.groupValues[1]

            // Look for slot e.g. C11+C12+C13, A11+B11, L1+L2
            val slotMatch = Regex("""\b([A-Z0-9]+(?:\+[A-Z0-9]+)*)\b""").findAll(rowText)
                .map { it.groupValues[1] }
                .firstOrNull { s -> s.contains("+") || SlotTimings.isSlot(s) } ?: continue

            // Look for venue e.g. LC-002, AB-101, SJT-401, AR-202, AB02-171
            val venueCell = cells.firstOrNull { cell ->
                cell.text().split("-", " ").any { isVenue(it) } || isVenue(cell.text())
            }
            val venueRaw = venueCell?.text()?.trim().orEmpty()
            val venue = if (venueRaw.contains("-") && !isVenue(venueRaw)) {
                venueRaw.split("-").lastOrNull { isVenue(it) }?.trim() ?: venueRaw
            } else if (venueRaw.isNotBlank()) {
                venueRaw
            } else {
                "LC-002"
            }

            // Course title
            val codeIdx = cells.indexOfFirst { it.text().contains(code) }
            val courseCellText = if (codeIdx >= 0) cells[codeIdx].text().trim() else ""
            val title = if (courseCellText.contains("-")) {
                courseCellText.substringAfter("-").substringBefore("(").trim()
            } else {
                cells.getOrNull(codeIdx + 1)?.text()?.trim()?.ifBlank { code } ?: code
            }

            courses.add(
                RawCourse(
                    code = code,
                    title = title,
                    type = "",
                    slot = slotMatch,
                    venue = venue,
                    faculty = "",
                ),
            )
        }
        return courses
    }

    /** Extracts block and room number from a venue string like "AB-330" or "SJT 711". */
    fun parseVenue(venue: String): Pair<String, String> {
        val clean = venue.trim()
        val match = Regex("""^([A-Za-z0-9_-]+?)[\s-]*([0-9]{3,4}[A-Za-z]?)$""").find(clean)
        return if (match != null) {
            Pair(match.groupValues[1].replace("-", "").uppercase(), match.groupValues[2])
        } else {
            val letters = clean.takeWhile { it.isLetter() }.uppercase()
            val digits = clean.dropWhile { it.isLetter() }.replace("-", "").trim()
            Pair(letters.ifBlank { "AB" }, digits.ifBlank { "101" })
        }
    }

    private fun isVenue(text: String): Boolean {
        val t = text.trim()
        return (t.startsWith("AB") || t.startsWith("SJT") || t.startsWith("TT") ||
            t.startsWith("MB") || t.startsWith("LC") || t.startsWith("AR") ||
            t.startsWith("CB") || t.startsWith("SMV") || t.contains("-")) &&
            t.any { it.isDigit() }
    }
}

/** Standard VIT FFCS Slot Timing Dictionary. */
object SlotTimings {
    data class Timing(val dayOfWeek: Int, val startTime: String, val endTime: String)

    fun isSlot(text: String): Boolean {
        val t = text.trim().uppercase()
        return t.matches(Regex("""^[A-G][12]\d?$""")) ||
            t.matches(Regex("""^T[A-G][12]?$""")) ||
            t.matches(Regex("""^L\d{1,2}$""")) ||
            t.matches(Regex("""^[A-G]\d{2}$"""))
    }

    fun getTimings(slot: String): List<Timing> {
        val s = slot.trim().uppercase()
        return when (s) {
            "A1", "A11" -> listOf(
                Timing(1, "08:00 AM", "08:50 AM"), // Monday
                Timing(3, "09:00 AM", "09:50 AM"), // Wednesday
            )
            "B1", "B11" -> listOf(
                Timing(2, "08:00 AM", "08:50 AM"), // Tuesday
                Timing(4, "09:00 AM", "09:50 AM"), // Thursday
            )
            "C1", "C11" -> listOf(
                Timing(3, "08:00 AM", "08:50 AM"), // Wednesday
                Timing(5, "09:00 AM", "09:50 AM"), // Friday
            )
            "D1", "D11" -> listOf(
                Timing(4, "08:00 AM", "08:50 AM"), // Thursday
                Timing(1, "10:00 AM", "10:50 AM"), // Monday
            )
            "E1", "E11" -> listOf(
                Timing(5, "08:00 AM", "08:50 AM"), // Friday
                Timing(2, "10:00 AM", "10:50 AM"), // Tuesday
            )
            "F1", "F11" -> listOf(
                Timing(1, "09:00 AM", "09:50 AM"), // Monday
                Timing(3, "10:00 AM", "10:50 AM"), // Wednesday
            )
            "G1", "G11" -> listOf(
                Timing(2, "09:00 AM", "09:50 AM"), // Tuesday
                Timing(4, "10:00 AM", "10:50 AM"), // Thursday
            )
            "A2", "A14", "A21" -> listOf(
                Timing(1, "14:00 PM", "14:50 PM"), // Monday
                Timing(3, "15:00 PM", "15:50 PM"), // Wednesday
            )
            "B2", "B14", "B21" -> listOf(
                Timing(2, "14:00 PM", "14:50 PM"), // Tuesday
                Timing(4, "15:00 PM", "15:50 PM"), // Thursday
            )
            "C2", "C14", "C21" -> listOf(
                Timing(3, "14:00 PM", "14:50 PM"), // Wednesday
                Timing(5, "15:00 PM", "15:50 PM"), // Friday
            )
            "D2", "D14", "D21" -> listOf(
                Timing(4, "14:00 PM", "14:50 PM"), // Thursday
                Timing(1, "16:00 PM", "16:50 PM"), // Monday
            )
            "E2", "E14", "E21" -> listOf(
                Timing(5, "14:00 PM", "14:50 PM"), // Friday
                Timing(2, "16:00 PM", "16:50 PM"), // Tuesday
            )
            "L31", "L32" -> listOf(Timing(1, "14:00 PM", "15:50 PM"))
            "L33", "L34" -> listOf(Timing(1, "16:00 PM", "17:50 PM"))
            "L37", "L38" -> listOf(Timing(2, "14:00 PM", "15:50 PM"))
            "L43", "L44" -> listOf(Timing(3, "14:00 PM", "15:50 PM"))
            "L49", "L50" -> listOf(Timing(4, "14:00 PM", "15:50 PM"))
            "L55", "L56" -> listOf(Timing(5, "14:00 PM", "15:50 PM"))
            else -> listOf(
                Timing(1, "11:00 AM", "12:30 PM"),
            )
        }
    }
}
