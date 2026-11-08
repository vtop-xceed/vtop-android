package dev.vtoppp.vtop.data

object AttendanceDetailParser {

    private val cellRegex = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", RegexOption.DOT_MATCHES_ALL)
    private val tagStripRegex = Regex("""<[^>]+>""")
    private val dateRegex = Regex("""(\d{2})-(\d{2})-(\d{4})""")
    private val isoDigitsRegex = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * Parses the second inner table on the processViewAttendanceDetail page:
     * Sl.No | Date | Slot | Day / Time | Status.
     */
    fun parse(html: String, regNo: String, semesterSubId: String, courseCode: String): List<AttendanceSession> {
        if (html.isBlank()) return emptyList()
        val results = mutableListOf<AttendanceSession>()
        val tables = Regex("""<table.*?</table>""", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        val detail = tables.lastOrNull { table ->
            val text = cellText(table.value)
            text.contains("Sl.No", ignoreCase = true) && text.contains("Status", ignoreCase = true)
        }
        val body = detail?.value ?: return emptyList()
        for (rowMatch in Regex("""<tr[^>]*>.*?</tr>""", RegexOption.DOT_MATCHES_ALL).findAll(body)) {
            val cells = cellRegex.findAll(rowMatch.value).map { cellText(it.value) }.toList()
            if (cells.size < 5) continue
            val slNo = cells[0].trim()
            if (!slNo.matches(Regex("""\d+"""))) continue
            val dateRaw = cells[1].trim()
            val match = dateRegex.find(dateRaw) ?: continue
            val dateIso = "${match.groupValues[3]}-${match.groupValues[2]}-${match.groupValues[1]}"
            val slot = cells[2].trim()
            val status = normalizeStatus(cells[4].trim())
            results.add(
                AttendanceSession(
                    regNo = regNo,
                    semesterSubId = semesterSubId,
                    courseCode = courseCode,
                    date = dateIso,
                    slot = slot,
                    status = status,
                )
            )
        }
        return results
    }

    private fun cellText(raw: String): String =
        tagStripRegex.replace(raw, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .trim()

    private fun normalizeStatus(raw: String): String = when {
        raw.contains("Present", ignoreCase = true) -> "Present"
        raw.contains("Absent", ignoreCase = true) -> "Absent"
        raw.contains("On Duty", ignoreCase = true) -> "On Duty"
        else -> raw
    }
}
