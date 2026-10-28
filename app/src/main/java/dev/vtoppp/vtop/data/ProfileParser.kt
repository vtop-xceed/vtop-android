package dev.vtoppp.vtop.data

import org.jsoup.Jsoup

object ProfileParser {
    /**
     * Parses the VTOP `studentsRecord/StudentProfileAllView` HTML or dashboard HTML
     * to extract student name, registration number, programme, branch, school, etc.
     */
    fun parse(html: String, fallbackRegNo: String): UserProfile {
        if (html.isBlank()) {
            return UserProfile(regNo = fallbackRegNo, name = fallbackRegNo)
        }

        try {
            val doc = Jsoup.parse(html)
            val textMap = mutableMapOf<String, String>()

            // 1. Check for columnar tables with tr.tableHeader / tr.tableContent (e.g. StudentGradeHistory)
            for (table in doc.select("table")) {
                val headerRow = table.selectFirst("tr.tableHeader")
                val valueRow = table.selectFirst("tr.tableContent")
                if (headerRow != null && valueRow != null) {
                    val headerCells = headerRow.select("td, th").map { it.text().trim().replace(":", "").lowercase() }
                    val valueCells = valueRow.select("td, th").map { it.text().trim() }
                    for (idx in 0 until minOf(headerCells.size, valueCells.size)) {
                        val k = headerCells[idx]
                        val v = valueCells[idx]
                        if (k.isNotBlank() && v.isNotBlank()) {
                            textMap[k] = v
                        }
                    }
                }
            }

            // 2. Check key-value structures inside table cells (e.g. StudentProfileAllView)
            val rows = doc.select("tr:not(.tableHeader):not(.tableContent)")
            for (row in rows) {
                val cells = row.select("td, th")
                var i = 0
                while (i + 1 < cells.size) {
                    val key = cells[i].text().trim().replace(":", "").lowercase()
                    val value = cells[i + 1].text().trim()
                    if (key.isNotBlank() && value.isNotBlank() && !textMap.containsKey(key)) {
                        textMap[key] = value
                    }
                    i += 2
                }
            }

            fun findValue(vararg searchKeys: String): String {
                for (searchKey in searchKeys) {
                    val exact = textMap[searchKey.lowercase()]
                    if (!exact.isNullOrBlank()) return exact
                }
                for ((k, v) in textMap) {
                    for (searchKey in searchKeys) {
                        if (k.contains(searchKey, ignoreCase = true) && v.isNotBlank()) {
                            return v
                        }
                    }
                }
                return ""
            }

            val regNo = findValue(
                "registration number",
                "register number",
                "registration no",
                "register no",
                "reg. no",
                "reg no",
                "reg. number",
                "reg number",
                "application number",
                "application no",
                "appl no",
            ).ifBlank { fallbackRegNo }
            val name = findValue("student name", "student's name", "name")
                .ifBlank {
                    // Try dashboard header regex: e.g. "NAME (STUDENT)" or "REGNO (STUDENT)"
                    Regex("""([A-Z0-9\s]{3,40})\s*\((?:STUDENT|EMPLOYEE)\)""").find(html)?.groupValues?.get(1)?.trim()
                        ?: fallbackRegNo
                }
            val programme = findValue("programme and branch", "programme", "program", "degree", "course")
            val branch = findValue("branch", "specialization", "department")
            val school = findValue("school")
            val email = findValue("email", "student email", "email id")
            val mobile = findValue("mobile", "mobile no", "mobile number", "phone")

            return UserProfile(
                regNo = regNo,
                name = name,
                programme = programme,
                branch = branch,
                school = school,
                email = email,
                mobile = mobile,
                updatedAtMs = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            AppLog.d("profile-parser: parse failed: ${e.message}")
            return UserProfile(regNo = fallbackRegNo, name = fallbackRegNo)
        }
    }
}
