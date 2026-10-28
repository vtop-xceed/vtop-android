package dev.vtoppp.vtop.data

object GradeHistoryParser {

    /**
     * Parses VTOP StudentGradeHistory HTML.
     * Returns a Pair of [AcademicSummary] (CGPA, credits, grade counts) and List<[GradeHistoryEntry]>.
     */
    fun parse(html: String, regNo: String): Pair<AcademicSummary?, List<GradeHistoryEntry>> {
        if (html.isBlank()) return null to emptyList()

        val courseEntries = mutableListOf<GradeHistoryEntry>()

        // 1. Extract CGPA, Credits Earned, Credits Registered using regex patterns
        var cgpa = extractDecimal(html, """(?:CGPA|Cumulative\s+Grade\s+Point\s+Average)""")
        var creditsEarned = extractDecimal(html, """Credits?\s+Earned""")
        val creditsReg = extractDecimal(html, """Credits?\s+Registered""")

        // 2. Extract course rows from HTML <tr> elements
        val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cellRegex = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tagStripRegex = Regex("""<[^>]+>""")

        for (rowMatch in rowRegex.findAll(html)) {
            val rowContent = rowMatch.groupValues[1]
            val cells = cellRegex.findAll(rowContent).map { cellMatch ->
                tagStripRegex.replace(cellMatch.groupValues[1], " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .trim()
            }.toList()

            if (cells.size < 4) continue

            // Find course code cell (e.g. CSE1001, BCSE101E, STS200)
            val codeIdx = cells.indexOfFirst {
                it.matches(Regex("""^[A-Z]{3,4}\d{3,4}[A-Z]?$""", RegexOption.IGNORE_CASE))
            }
            if (codeIdx < 0) continue

            val courseCode = cells[codeIdx].uppercase()

            // Find grade cell (S, A, B, C, D, E, F, N, P, W, PASS, FAIL) after course code
            val gradeIdx = cells.indices.firstOrNull { idx ->
                idx > codeIdx && cells[idx].matches(Regex("""^(?:[SABCDEFPNW]|PASS|FAIL)$""", RegexOption.IGNORE_CASE)) && cells[idx].length <= 4
            } ?: -1
            if (gradeIdx < 0) continue
            val grade = cells[gradeIdx].uppercase()

            // Find credit cell (decimal or integer, usually 1.0 - 5.0) after course code
            val creditIdx = cells.indices.firstOrNull { idx ->
                idx > codeIdx && idx != gradeIdx && cells[idx].matches(Regex("""^\d+(?:\.\d+)?$""")) && (cells[idx].toDoubleOrNull() ?: 0.0) in 0.5..20.0
            } ?: -1
            val credits = if (creditIdx >= 0) cells[creditIdx].toDoubleOrNull() ?: 0.0 else 0.0

            // Title is typically adjacent to course code
            val titleCandidate = cells.getOrNull(codeIdx + 1)?.takeIf {
                it != cells.getOrNull(creditIdx) && it != cells.getOrNull(gradeIdx) && it.length > 2
            }.orEmpty()
            val courseTitle = if (titleCandidate.isNotBlank()) titleCandidate else "Course $courseCode"

            // Course type (e.g. Embedded Theory and Lab, Theory Only, Lab Only, Soft Skill)
            val typeCandidate = cells.firstOrNull {
                it.contains("Theory", ignoreCase = true) || it.contains("Lab", ignoreCase = true) ||
                    it.contains("Soft", ignoreCase = true) || it.contains("Project", ignoreCase = true)
            }.orEmpty()

            // Exam Month (e.g. NOV/2023, MAY/2024, WINSEM2023-24, FALL SEM)
            val examMonth = cells.firstOrNull {
                it.contains(Regex("""(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC|\d{4}|SEM)""", RegexOption.IGNORE_CASE)) &&
                    it != courseCode && it != courseTitle && it != typeCandidate
            }.orEmpty()

            // Distribution (e.g. Program Core, University Core, Discipline Elective, Open Elective)
            val distribution = cells.firstOrNull {
                it.contains("Core", ignoreCase = true) || it.contains("Elective", ignoreCase = true)
            }.orEmpty()

            courseEntries.add(
                GradeHistoryEntry(
                    regNo = regNo,
                    courseCode = courseCode,
                    courseTitle = courseTitle,
                    courseType = typeCandidate,
                    credits = credits,
                    grade = grade,
                    examMonth = examMonth,
                    courseDistribution = distribution,
                )
            )
        }

        // 3. Compute counts from entries
        val sCount = courseEntries.count { it.grade == "S" }
        val aCount = courseEntries.count { it.grade == "A" }
        val bCount = courseEntries.count { it.grade == "B" }
        val cCount = courseEntries.count { it.grade == "C" }
        val dCount = courseEntries.count { it.grade == "D" }
        val eCount = courseEntries.count { it.grade == "E" }
        val fCount = courseEntries.count { it.grade == "F" }

        // If creditsEarned was not explicitly in HTML text, sum credits of passing grades
        if (creditsEarned <= 0.0 && courseEntries.isNotEmpty()) {
            creditsEarned = courseEntries
                .filter { it.grade !in listOf("F", "N", "FAIL", "W") }
                .sumOf { it.credits }
        }

        // If CGPA was not in HTML text, calculate weighted GPA from VIT 10-point scale
        if (cgpa <= 0.0 && courseEntries.isNotEmpty()) {
            var totalQualityPoints = 0.0
            var gradedCredits = 0.0
            for (entry in courseEntries) {
                val point = when (entry.grade) {
                    "S" -> 10.0
                    "A" -> 9.0
                    "B" -> 8.0
                    "C" -> 7.0
                    "D" -> 6.0
                    "E" -> 5.0
                    else -> 0.0
                }
                if (entry.grade in listOf("S", "A", "B", "C", "D", "E", "F")) {
                    totalQualityPoints += entry.credits * point
                    gradedCredits += entry.credits
                }
            }
            if (gradedCredits > 0.0) {
                cgpa = (totalQualityPoints / gradedCredits * 100.0).toLong() / 100.0
            }
        }

        val summary = if (courseEntries.isNotEmpty() || cgpa > 0.0) {
            AcademicSummary(
                regNo = regNo,
                cgpa = cgpa,
                creditsEarned = creditsEarned,
                creditsRegistered = if (creditsReg > 0.0) creditsReg else courseEntries.sumOf { it.credits },
                sCount = sCount,
                aCount = aCount,
                bCount = bCount,
                cCount = cCount,
                dCount = dCount,
                eCount = eCount,
                fCount = fCount,
            )
        } else {
            null
        }

        return summary to courseEntries
    }

    private fun extractDecimal(html: String, prefixPattern: String): Double {
        val regex = Regex("""$prefixPattern[^<0-9]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
        val match = regex.find(html) ?: return 0.0
        return match.groupValues[1].toDoubleOrNull() ?: 0.0
    }
}
