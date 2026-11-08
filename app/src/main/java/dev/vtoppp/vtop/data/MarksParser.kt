package dev.vtoppp.vtop.data

object MarksParser {

    private val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val cellRegex = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val tagStripRegex = Regex("""<[^>]+>""")
    private val codeCandidateRegex = Regex("""^[A-Z]{3,4}\s*\d{3,4}[A-Z]?$""", RegexOption.IGNORE_CASE)
    private val slotRegex = Regex("""^[A-Z]\d{1,2}(?:\+[A-Z]\d{1,2})*$""")
    private val whitespaceRegex = Regex("""\s+""")

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


        var currentCourseCode = ""
        var currentCourseTitle = ""
        var currentCourseType = ""
        val currentFaculty = ""
        var currentSlot = ""

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
            val codeCandidate = cells.firstOrNull { it.matches(codeCandidateRegex) }
            if (codeCandidate != null) {
                currentCourseCode = codeCandidate.replace(whitespaceRegex, "").uppercase()
                val idx = cells.indexOf(codeCandidate)
                currentCourseTitle = cells.getOrNull(idx + 1)?.takeIf { it.length > 2 }.orEmpty()
                currentCourseType = cells.firstOrNull {
                    it.contains("Theory", ignoreCase = true) || it.contains("Lab", ignoreCase = true)
                }.orEmpty()
                currentSlot = cells.firstOrNull {
                    it.matches(slotRegex)
                }.orEmpty()
            }

            if (codeCandidate != null) continue // course header row, handled above
            if (cells[0].equals("Sl.No.", ignoreCase = true)) continue // table header row

            // Component row: [Sl.No.?, Mark Title, Max. Mark, Weightage %, Status, Scored Mark, Weightage Mark, ...]
            val componentName = cells.getOrNull(1)?.trim().orEmpty()
            if (componentName.isEmpty()) continue
            val nums = cells.drop(2).mapNotNull { it.replace("%", "").toDoubleOrNull() }
            if (nums.isEmpty()) continue

            val maxMarks = nums.getOrElse(0) { 0.0 }
            val weightage = nums.getOrElse(1) { 0.0 }
            val scored = nums.getOrElse(2) { 0.0 }
            val percentage = if (maxMarks > 0) (scored / maxMarks) * 100.0 else 0.0

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

        return results
    }
}
