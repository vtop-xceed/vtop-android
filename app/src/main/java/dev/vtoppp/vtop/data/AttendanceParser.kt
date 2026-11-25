package dev.vtoppp.vtop.data

import org.jsoup.Jsoup

object AttendanceParser {

    private val COURSE_CODE_REGEX = Regex("""\b([A-Z]{3,4}\d{3,4}[A-Z]?)\b""")
    private val DASH_SPLIT_REGEX = Regex("""\s*[-–—]\s*""")
    private val SLOT_REGEX = Regex("""\b([A-Z]\d{1,2}(?:\+[A-Z]\d{1,2})*)\b""")
    private val CALL_TOKEN_SPLIT_REGEX = Regex("""&#39;|'|,""")
    private val IDENT_TOKEN_REGEX = Regex("""[A-Za-z0-9_\-]+""")
    private val PERCENT_REGEX = Regex("""(\d+(?:\.\d+)?)%""")
    private val INT_CELL_REGEX = Regex("""^\d{1,3}$""")

    /**
     * Parses attendance HTML (e.g. returned by processViewStudentAttendance)
     * into a list of [AttendanceEntry].
     */
    fun parse(html: String, regNo: String, semesterSubId: String = ""): List<AttendanceEntry> {
        if (html.isBlank()) return emptyList()

        val results = mutableListOf<AttendanceEntry>()
        val doc = Jsoup.parse(html)

        val rows = doc.select("table#AttendanceDetailDataTable tbody tr, table tbody tr, table tr")
        for (row in rows) {
            val cells = row.select("td")
            if (cells.size < 5) continue

            val rowText = row.text()
            // Find course code anywhere in the row (e.g. CSE2004, MAT1001, STS200)
            val codeMatch = COURSE_CODE_REGEX.find(rowText) ?: continue
            val courseCode = codeMatch.groupValues[1]

            // Extract cell text once per cell to avoid repeated Jsoup DOM walks.
            val cellTexts = cells.map { it.text().trim() }

            // Find course detail cell (contains course code and title)
            val courseText = cellTexts.firstOrNull { it.contains(courseCode) }
            var courseTitle = ""
            var courseType = ""
            if (courseText != null) {
                val parts = courseText.split(DASH_SPLIT_REGEX)
                if (parts.size > 1) {
                    courseTitle = parts[1].trim()
                }
                if (parts.size > 2) {
                    courseType = parts[2].trim()
                }
            }

            // Find slot (e.g. C11+C12+C13, A11, L1+L2)
            val slotMatch = SLOT_REGEX.find(rowText)
            val slot = slotMatch?.groupValues?.get(1).orEmpty()

            var courseId = ""
            var courseTypeA = ""
            val raw = row.html()
            val callIdx = raw.indexOf("callStudentAttendanceDetailDisplay")
            if (callIdx >= 0) {
                val endIdx = raw.indexOf(");", callIdx)
                val callEnd = (if (endIdx > 0) endIdx else callIdx + 160).coerceAtMost(raw.length)
                val call = raw.substring(callIdx, callEnd)
                val tokens = CALL_TOKEN_SPLIT_REGEX
                    .split(call)
                    .map { it.trim() }
                    .filter { IDENT_TOKEN_REGEX.matches(it) }
                if (tokens.size >= 2) {
                    courseId = tokens[tokens.size - 2]
                    courseTypeA = tokens[tokens.size - 1]
                }
            }

            // Find faculty (from Faculty column or cell with hyphen)
            val facultyText = cellTexts.getOrNull(4)?.takeIf { '-' in it }
                ?: cellTexts.firstOrNull { t ->
                    '-' in t && '%' !in t && !t.contains("BL20") &&
                        !t.contains("Lecture") && !t.contains("Regular") && !t.contains("Project")
                }
            val facultyName = facultyText?.substringBefore('-')?.trim().orEmpty()

            // Find percentage cell (e.g. "95%")
            var percent = 0.0
            val pctText = cellTexts.firstOrNull { '%' in it }
            if (pctText != null) {
                val numStr = PERCENT_REGEX.find(pctText)?.groupValues?.get(1)
                percent = numStr?.toDoubleOrNull() ?: 0.0
            }

            // Extract last two positive integer counts for attended and total
            var attended = 0
            var total = 0
            var intCount = 0
            for (t in cellTexts) {
                if ('%' !in t && INT_CELL_REGEX.matches(t)) {
                    val v = t.toInt()
                    if (v > 0) {
                        attended = total
                        total = v
                        intCount++
                    }
                }
            }
            if (intCount < 2) {
                attended = 0
                total = 0
            } else if (attended > total) {
                val tmp = attended
                attended = total
                total = tmp
            }

            results.add(
                AttendanceEntry(
                    regNo = regNo,
                    semesterSubId = semesterSubId,
                    courseCode = courseCode,
                    courseTitle = courseTitle.ifBlank { "Course $courseCode" },
                    courseType = courseType,
                    slot = slot,
                    facultyName = facultyName,
                    courseId = courseId,
                    courseTypeA = courseTypeA,
                    attendedClasses = attended,
                    totalClasses = total,
                    attendancePercentage = percent,
                ),
            )
        }

        return results
    }
}
