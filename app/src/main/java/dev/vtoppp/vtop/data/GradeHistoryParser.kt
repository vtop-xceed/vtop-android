package dev.vtoppp.vtop.data

object GradeHistoryParser {

    private val rowRegex = Regex("""(<tr[^>]*>)([\s\S]*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val cellRegex = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val tagStripRegex = Regex("""<[^>]+>""")
    private val courseCodeRegex = Regex("""^[A-Z]{3,4}\d{3,4}[A-Z]?$""", RegexOption.IGNORE_CASE)
    private val examMonthRegex = Regex("""(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[\s-]*[12][0-9]{3}""", RegexOption.IGNORE_CASE)
    private val examMonthLooseRegex = Regex("""(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC|\d{4}|SEM)""", RegexOption.IGNORE_CASE)
    private val gradeTokenRegex = Regex("""^(?:[SABCDEFPNW]|PASS|FAIL)$""", RegexOption.IGNORE_CASE)
    private val numberRegex = Regex("""^\d+(?:\.\d+)?$""")

    /**
     * Parses VTOP StudentGradeHistory HTML.
     * Returns a Pair of [AcademicSummary] (CGPA, credits, grade counts) and List<[GradeHistoryEntry]>.
     * The authoritative "CGPA Details" box is preferred when present; the course list is the
     * top-level table only (level-1 attempt breakdowns are excluded).
     */
    fun parse(html: String, regNo: String): Pair<AcademicSummary?, List<GradeHistoryEntry>> {
        if (html.isBlank()) return null to emptyList()

        fun cellsOf(row: String): List<String> =
            cellRegex.findAll(row).map { cellMatch ->
                tagStripRegex.replace(cellMatch.groupValues[1], " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .trim()
            }.toList()

        // 1. Authoritative summary: walk rows; the row whose cells start with
        //    "Credits Registered" is the box header; the next all-numeric row holds
        //    [Credits Registered, Credits Earned, CGPA, S, A, B, C, D, E, F, N].
        var creditsRegistered = 0.0
        var creditsEarned = 0.0
        var curriculumCredits = 0.0
        var cgpa = 0.0
        var sCount = 0
        var aCount = 0
        var bCount = 0
        var cCount = 0
        var dCount = 0
        var eCount = 0
        var fCount = 0
        var boxFound = false

        val rows = rowRegex.findAll(html).toList()
        var boxHeaderSeen = false
        for (m in rows) {
            val tag = m.groupValues[1]
            val cells = cellsOf(m.groupValues[2])
            if (cells.isEmpty()) continue
            if (tag.contains("level1", ignoreCase = true)) continue
            if (cells[0].equals("Total Credits", ignoreCase = true)) {
                curriculumCredits = cells.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                continue
            }
            if (!boxFound && cells.any { it.contains("Credits Registered", ignoreCase = true) }) {
                boxHeaderSeen = true
                continue
            }
            if (boxHeaderSeen && !boxFound && cells[0].toDoubleOrNull() != null && cells.size >= 11) {
                creditsRegistered = cells[0].toDoubleOrNull() ?: 0.0
                creditsEarned = cells[1].toDoubleOrNull() ?: 0.0
                cgpa = cells[2].toDoubleOrNull() ?: 0.0
                sCount = cells[3].toIntOrNull() ?: 0
                aCount = cells[4].toIntOrNull() ?: 0
                bCount = cells[5].toIntOrNull() ?: 0
                cCount = cells[6].toIntOrNull() ?: 0
                dCount = cells[7].toIntOrNull() ?: 0
                eCount = cells[8].toIntOrNull() ?: 0
                fCount = cells[9].toIntOrNull() ?: 0
                boxFound = true
                break
            }
        }

        // 2. Course rows from the top-level table only (level-1 attempt breakdowns skipped).
        val courseEntries = mutableListOf<GradeHistoryEntry>()
        for (m in rows) {
            val tag = m.groupValues[1]
            if (tag.contains("level1", ignoreCase = true)) continue
            val cells = cellsOf(m.groupValues[2])
            if (cells.size < 4) continue

            val codeIdx = cells.indexOfFirst {
                it.matches(courseCodeRegex)
            }
            if (codeIdx < 0) continue
            val courseCode = cells[codeIdx].uppercase()

            val examIdx = cells.indices.firstOrNull { idx ->
                idx > codeIdx && cells[idx].contains(Regex("""(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[\\s-]*[12][0-9]{3}""", RegexOption.IGNORE_CASE))
            } ?: -1

            val gradeIdx: Int
            val creditIdx: Int
            if (examIdx >= 2) {
                // Anchored: [code, ..., credits, grade, examMonth, ...]
                gradeIdx = examIdx - 1
                creditIdx = examIdx - 2
            } else {
                gradeIdx = cells.indices.firstOrNull { idx ->
                    idx > codeIdx && cells[idx].matches(Regex("""^(?:[SABCDEFPNW]|PASS|FAIL)$""", RegexOption.IGNORE_CASE)) && cells[idx].length <= 4
                } ?: -1
                if (gradeIdx < 0) continue
                creditIdx = cells.indices.firstOrNull { idx ->
                    idx > codeIdx && idx != gradeIdx && cells[idx].matches(Regex("""^\d+(?:\.\d+)?$""")) && (cells[idx].toDoubleOrNull() ?: 0.0) in 0.5..20.0
                } ?: -1
            }
            val grade = cells[gradeIdx].uppercase()
            val credits = if (creditIdx >= 0) cells[creditIdx].toDoubleOrNull() ?: 0.0 else 0.0

            val titleCandidate = cells.getOrNull(codeIdx + 1)?.takeIf {
                it != cells.getOrNull(creditIdx) && it != cells.getOrNull(gradeIdx) && it.length > 2
            }.orEmpty()
            val courseTitle = if (titleCandidate.isNotBlank()) titleCandidate else "Course $courseCode"

            val typeCandidate = cells.firstOrNull {
                it.contains("Theory", ignoreCase = true) || it.contains("Lab", ignoreCase = true) ||
                    it.contains("Soft", ignoreCase = true) || it.contains("Project", ignoreCase = true)
            }.orEmpty()

            val examMonth = cells.firstOrNull {
                it.contains(examMonthLooseRegex) &&
                    it != courseCode && it != courseTitle && it != typeCandidate
            }.orEmpty()

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

        // 3. Fallbacks if the CGPA box wasn't found: legacy inline "CGPA :" text, then rows.
        if (!boxFound) {
            val legacyCgpa = extractDecimal(html, """(?:CGPA|Cumulative\s+Grade\s+Point\s+Average)""")
            creditsRegistered = extractDecimal(html, """Credits?\s+Registered""")
            creditsEarned = extractDecimal(html, """Credits?\s+Earned""")
            cgpa = legacyCgpa
            sCount = courseEntries.count { it.grade == "S" }
            aCount = courseEntries.count { it.grade == "A" }
            bCount = courseEntries.count { it.grade == "B" }
            cCount = courseEntries.count { it.grade == "C" }
            dCount = courseEntries.count { it.grade == "D" }
            eCount = courseEntries.count { it.grade == "E" }
            fCount = courseEntries.count { it.grade == "F" }
            if (creditsEarned <= 0.0 && courseEntries.isNotEmpty()) {
                creditsEarned = courseEntries
                    .filter { it.grade !in listOf("F", "N", "FAIL", "W") }
                    .sumOf { it.credits }
            }
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
        }

        val summary = if (courseEntries.isNotEmpty() || cgpa > 0.0) {
            AcademicSummary(
                regNo = regNo,
                cgpa = cgpa,
                creditsEarned = creditsEarned,
                creditsRegistered = if (creditsRegistered > 0.0) creditsRegistered else courseEntries.sumOf { it.credits },
                curriculumCredits = curriculumCredits,
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
