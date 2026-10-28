package dev.vtoppp.vtop.data

import org.jsoup.Jsoup

object AttendanceParser {

    /**
     * Parses attendance HTML (e.g. returned by processViewStudentAttendance)
     * into a list of [AttendanceEntry].
     */
    fun parse(html: String, regNo: String, semesterSubId: String): List<AttendanceEntry> {
        if (html.isBlank()) return emptyList()

        val results = mutableListOf<AttendanceEntry>()
        val doc = Jsoup.parse(html)

        val rows = doc.select("table#AttendanceDetailDataTable tbody tr, table tbody tr, table tr")
        for (row in rows) {
            val cells = row.select("td")
            if (cells.size < 5) continue

            val rowText = row.text()
            // Find course code anywhere in the row (e.g. CSE2004, MAT1001, STS200)
            val codeMatch = Regex("""\b([A-Z]{3,4}\d{3,4}[A-Z]?)\b""").find(rowText) ?: continue
            val courseCode = codeMatch.groupValues[1]

            // Find course detail cell (contains course code and title)
            val courseCell = cells.firstOrNull { it.text().contains(courseCode) }
            var courseTitle = ""
            var courseType = ""
            if (courseCell != null) {
                val parts = courseCell.text().split(Regex("""\s*[-–—]\s*"""))
                if (parts.size > 1) {
                    courseTitle = parts[1].trim()
                }
                if (parts.size > 2) {
                    courseType = parts[2].trim()
                }
            }

            // Find slot (e.g. C11+C12+C13, A11, L1+L2)
            val slotMatch = Regex("""\b([A-Z]\d{1,2}(?:\+[A-Z]\d{1,2})*)\b""").find(rowText)
            val slot = slotMatch?.groupValues?.get(1).orEmpty()

            // Find faculty
            val facultyCell = cells.firstOrNull { cell ->
                val t = cell.text()
                t.contains("-") && (t.contains("SCAI") || t.contains("SCOPE") || t.contains("SELECT") || t.contains("SMEC") || t.contains("SASL"))
            }
            val facultyName = facultyCell?.text()?.split("-")?.firstOrNull()?.trim().orEmpty()

            // Find percentage cell (e.g. "95%")
            var percent = 0.0
            val pctCell = cells.firstOrNull { it.text().contains("%") }
            if (pctCell != null) {
                val numStr = Regex("""(\d+(?:\.\d+)?)%""").find(pctCell.text())?.groupValues?.get(1)
                percent = numStr?.toDoubleOrNull() ?: 0.0
            }

            // Extract numeric integers for attended and total
            val intCells = cells.mapNotNull { cell ->
                val t = cell.text().trim()
                if (!t.contains("%") && t.matches(Regex("""^\d{1,3}$"""))) t.toInt() else null
            }.filter { it > 0 }

            var attended = 0
            var total = 0
            if (intCells.size >= 2) {
                val candidateNumbers = intCells.takeLast(2)
                attended = candidateNumbers[0]
                total = candidateNumbers[1]
                if (attended > total) {
                    val tmp = attended
                    attended = total
                    total = tmp
                }
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
                    attendedClasses = attended,
                    totalClasses = total,
                    attendancePercentage = percent,
                ),
            )
        }

        return results
    }
}
