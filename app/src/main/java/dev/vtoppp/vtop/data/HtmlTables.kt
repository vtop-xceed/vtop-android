package dev.vtoppp.vtop.data

/** Shared regex tokenizers for VTOP HTML tables; every parser sanitizes cells the same way. */
internal object HtmlTables {

    data class Row(val tag: String, val body: String)

    private val rowRegex = Regex(
        """(<tr[^>]*>)(.*?)</tr>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val cellRegex = Regex(
        """<t[dh][^>]*>(.*?)</t[dh]>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val tagStripRegex = Regex("""<[^>]+>""")

    /** Rows between `<tr>` and `</tr>`; [Row.tag] is the opening tag, [Row.body] the content. */
    fun rows(html: String): List<Row> =
        rowRegex.findAll(html).map { Row(tag = it.groupValues[1], body = it.groupValues[2]) }.toList()

    /** Cells inside a row body, tag-stripped and whitespace/nbsp/entity normalized. */
    fun cells(row: String): List<String> =
        cellRegex.findAll(row).map { sanitize(it.groupValues[1]) }.toList()

    fun sanitize(raw: String): String {
        var s = if ('<' in raw) tagStripRegex.replace(raw, " ") else raw
        if ('&' in s) {
            s = s.replace("&nbsp;", " ").replace("&amp;", "&")
        }
        return s.trim()
    }
}
