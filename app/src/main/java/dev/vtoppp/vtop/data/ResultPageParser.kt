package dev.vtoppp.vtop.data

object ResultPageParser {

    // Matches the GPA line on the exam-result page: e.g. <span>GPA : 8.22</span>
    private val gpaRegex = Regex("""GPA\s*:\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    fun parseGpa(html: String): Double? {
        if (html.isBlank()) return null
        return gpaRegex.find(html)?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
