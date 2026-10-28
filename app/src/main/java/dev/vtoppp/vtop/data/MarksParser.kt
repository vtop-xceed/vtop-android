package dev.vtoppp.vtop.data

object MarksParser {

    /**
     * Extracts available semester options from StudentMarkView.html.
     * Returns list of Pair(semesterSubId, semesterName), e.g. ("BL20242501", "Fall Semester 2024-25").
     */
    fun extractSemesters(html: String): List<Pair<String, String>> {
        val optionRegex = Regex("""<option\s+value="([^"]+)"[^>]*>(.*?)</option>""", RegexOption.IGNORE_CASE)
        val list = mutableListOf<Pair<String, String>>()
        for (match in optionRegex.findAll(html)) {
            val value = match.groupValues[1].trim()
            val text = match.groupValues[2].replace("&amp;", "&").trim()
            if (value.isNotEmpty() && !text.contains("Choose", ignoreCase = true)) {
                list.add(value to text)
            }
        }
        return list
    }

    /**
     * Returns the latest/top semesterSubId from the HTML dropdown.
     */
    fun extractLatestSemester(html: String): String {
        return extractSemesters(html).firstOrNull()?.first.orEmpty()
    }

    /**
     * Parses marks from either direct StudentMarkView HTML or doStudentMarkView AJAX response HTML.
     */
    fun parse(html: String, regNo: String, defaultSemesterSubId: String = ""): List<MarkEntry> {
        if (html.isBlank()) return emptyList()

        val results = mutableListOf<MarkEntry>()
        val semId = defaultSemesterSubId.ifBlank { extractLatestSemester(html) }

        val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cellRegex = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tagStripRegex = Regex("""<[^>]+>""")

        var currentCourseCode = ""
        var currentCourseTitle = ""
        var currentCourseType = ""
        val currentFaculty = ""
        var currentSlot = ""

        val compPattern = Regex(
            """\b(?:CAT[- ]?[12]|DA[- ]?[123]|Digital\s+Assignment(?:\s+[123])?|Quiz(?:\s+[123])?|FAT|Final\s+Assessment|Lab\s+Internal|Assignment(?:\s+[123])?|Assessment|Mid\s+Term|Term\s+End|Project|Quiz)\b""",
            RegexOption.IGNORE_CASE,
        )

        for (rowMatch in rowRegex.findAll(html)) {
            val rowContent = rowMatch.groupValues[1]
            val cells = cellRegex.findAll(rowContent).map { cellMatch ->
                tagStripRegex.replace(cellMatch.groupValues[1], " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .trim()
            }.toList()

            if (cells.isEmpty()) continue

            // Check if this row introduces a new course
            val codeCandidate = cells.firstOrNull { it.matches(Regex("""^[A-Z]{3,4}\s*\d{3,4}[A-Z]?$""", RegexOption.IGNORE_CASE)) }
            if (codeCandidate != null) {
                currentCourseCode = codeCandidate.replace("\\s+".toRegex(), "").uppercase()
                val idx = cells.indexOf(codeCandidate)
                currentCourseTitle = cells.getOrNull(idx + 1)?.takeIf { it.length > 2 }.orEmpty()
                currentCourseType = cells.firstOrNull {
                    it.contains("Theory", ignoreCase = true) || it.contains("Lab", ignoreCase = true)
                }.orEmpty()
                currentSlot = cells.firstOrNull {
                    it.matches(Regex("""^[A-Z]\d{1,2}(?:\+[A-Z]\d{1,2})*$"""))
                }.orEmpty()
            }

            // Check if this row is an evaluation component row
            val compCell = cells.firstOrNull { compPattern.containsMatchIn(it) }
            if (compCell != null) {
                val componentName = compCell.trim()

                // Extract numeric values from the remaining cells (max marks, weightage, scored, percentage)
                val numbers = cells.filter { it != compCell }
                    .mapNotNull { it.replace("%", "").toDoubleOrNull() }

                var maxMarks = 0.0
                var weightage = 0.0
                var scored = 0.0
                var percentage = 0.0

                if (numbers.isNotEmpty()) {
                    if (numbers.size >= 4) {
                        maxMarks = numbers[0]
                        weightage = numbers[1]
                        scored = numbers[2]
                        percentage = numbers[3]
                    } else if (numbers.size == 3) {
                        maxMarks = numbers[0]
                        weightage = numbers[1]
                        scored = numbers[2]
                        if (maxMarks > 0) percentage = (scored / maxMarks) * 100.0
                    } else if (numbers.size == 2) {
                        maxMarks = numbers[0]
                        scored = numbers[1]
                        if (maxMarks > 0) percentage = (scored / maxMarks) * 100.0
                    } else {
                        scored = numbers[0]
                    }
                }

                results.add(
                    MarkEntry(
                        regNo = regNo,
                        semesterSubId = semId,
                        courseCode = currentCourseCode.ifBlank { "COURSE" },
                        courseTitle = currentCourseTitle,
                        courseType = currentCourseType,
                        facultyName = currentFaculty,
                        slot = currentSlot,
                        componentName = componentName,
                        maxMarks = maxMarks,
                        weightagePercent = weightage,
                        scoredMarks = scored,
                        scoredPercentage = (percentage * 10.0).toLong() / 10.0,
                    )
                )
            }
        }

        return results
    }
}
