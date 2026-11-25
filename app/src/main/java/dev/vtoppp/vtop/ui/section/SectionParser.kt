package dev.vtoppp.vtop.ui.section

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

object SectionParser {

    private enum class Kind {
        SKIP,
        HEADING,
        LABEL,
        SELECT,
        FIELD,
        ACTION,
        TABLE,
        INLINE,
        BLOCK,
    }

    private val skipTags = setOf(
        "script", "style", "noscript", "meta", "link", "option", "head", "title",
    )

    private val inlineTags = setOf(
        "span", "i", "b", "strong", "em", "u", "s", "small", "font", "br", "a",
        "abbr", "cite", "code", "kbd", "mark", "q", "sub", "sup", "time", "del", "ins",
    )

    private val whitespace = Regex("[\\s\\u00A0\\u2007\\u202F\\u200B]+")

    fun parse(html: String): List<SectionBlock> {
        if (html.isBlank()) return emptyList()
        return try {
            val document = Jsoup.parse(html)
            val root = document.body() ?: document
            val blocks = mutableListOf<SectionBlock>()
            visitChildren(root, blocks)
            blocks
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Same output as [parse], with the jsoup walk moved off the main thread
     *: snapshot scraping can emit every ~250 ms during JS activity.
     */
    suspend fun parseOffMain(html: String): List<SectionBlock> =
        withContext(Dispatchers.Default) { parse(html) }

    private fun visitChildren(parent: Element, blocks: MutableList<SectionBlock>) {
        val pending = StringBuilder()

        fun flush() {
            if (pending.isEmpty()) return
            val text = normalize(pending.toString())
            pending.setLength(0)
            if (text.isNotEmpty()) blocks += SectionBlock.Text(text)
        }

        for (node in parent.childNodes()) {
            when (node) {
                is TextNode -> pending.append(' ').append(node.wholeText)
                is Element -> {
                    if (node.normalName() == "br") {
                        pending.append(' ')
                        continue
                    }
                    when (kindOf(node)) {
                        Kind.SKIP -> Unit
                        Kind.INLINE -> pending.append(' ').append(node.text())
                        Kind.HEADING -> {
                            flush()
                            emitHeading(node)?.let(blocks::add)
                        }
                        Kind.LABEL -> {
                            flush()
                            emitLabel(node)?.let(blocks::add)
                        }
                        Kind.SELECT -> {
                            flush()
                            emitSelect(node)?.let(blocks::add)
                        }
                        Kind.FIELD -> {
                            flush()
                            emitField(node)?.let(blocks::add)
                        }
                        Kind.ACTION -> {
                            flush()
                            emitAction(node)?.let(blocks::add)
                        }
                        Kind.TABLE -> {
                            flush()
                            emitTable(node)?.let(blocks::add)
                        }
                        Kind.BLOCK -> {
                            flush()
                            visitChildren(node, blocks)
                        }
                    }
                }
                else -> Unit
            }
        }
        flush()
    }

    private fun isHidden(element: Element): Boolean =
        element.hasClass("modal") || element.hasClass("offcanvas")

    private fun kindOf(element: Element): Kind {
        val tag = element.normalName()
        if (tag in skipTags || isHidden(element)) return Kind.SKIP
        if (tag == "br") return Kind.INLINE
        if (headingLevel(element) != null) return Kind.HEADING
        return when (tag) {
            "table" -> Kind.TABLE
            "select" -> Kind.SELECT
            "label" -> Kind.LABEL
            "button" -> Kind.ACTION
            "input" -> when (element.attr("type").trim().lowercase().ifEmpty { "text" }) {
                "hidden" -> Kind.SKIP
                "button", "submit" -> Kind.ACTION
                "text", "date", "number", "password" -> Kind.FIELD
                else -> Kind.SKIP
            }
            else -> when {
                element.hasAttr("onclick") -> Kind.ACTION
                tag in inlineTags -> if (isInlineOnly(element)) Kind.INLINE else Kind.BLOCK
                else -> Kind.BLOCK
            }
        }
    }

    // Non-recursive structural check: avoids O(depth * N) repeated subtree scans on nested inline tags.
    private fun isInlineOnly(element: Element): Boolean {
        if (element.normalName() !in inlineTags || element.hasAttr("onclick")) return false
        for (descendant in element.allElements) {
            if (descendant === element) continue
            val tag = descendant.normalName()
            if (tag in skipTags || isHidden(descendant) || tag == "br") continue
            if (headingLevel(descendant) != null || descendant.hasAttr("onclick")) return false
            when (tag) {
                "table", "select", "label", "button" -> return false
                "input" -> when (descendant.attr("type").trim().lowercase().ifEmpty { "text" }) {
                    "button", "submit", "text", "date", "number", "password" -> return false
                }
                else -> if (tag !in inlineTags) return false
            }
        }
        return true
    }

    private fun headingLevel(element: Element): Int? {
        headingLevelOf(element.normalName())?.let { return it }
        val cls = element.attr("class")
        if (cls.isEmpty()) return null
        // Scan whitespace-delimited tokens without allocating a LinkedHashSet via element.classNames().
        var i = 0
        val len = cls.length
        while (i < len) {
            while (i < len && cls[i].isWhitespace()) i++
            val start = i
            while (i < len && !cls[i].isWhitespace()) i++
            if (i - start == 2 && (cls[start] == 'h' || cls[start] == 'H')) {
                val d = cls[start + 1]
                if (d in '1'..'6') return d - '0'
            }
        }
        return null
    }

    private fun headingLevelOf(name: String): Int? =
        if (name.length == 2 && name[0] == 'h' && name[1] in '1'..'6') name[1] - '0' else null

    private fun emitHeading(element: Element): SectionBlock.Heading? {
        val text = normalize(element.text())
        if (text.isEmpty()) return null
        return SectionBlock.Heading(text, headingLevel(element) ?: return null)
    }

    private fun emitLabel(element: Element): SectionBlock.Label? {
        val text = normalize(element.text())
        if (text.isEmpty()) return null
        return SectionBlock.Label(text)
    }

    private fun emitSelect(element: Element): SectionBlock.Select? {
        val optionElements = element.getElementsByTag("option")
        val options = ArrayList<SectionBlock.Select.Option>(optionElements.size)
        var selected: String? = null
        for (option in optionElements) {
            val label = normalize(option.text())
            val hasValue = option.hasAttr("value")
            val value = if (hasValue) option.attr("value") else label
            if (value.isNotEmpty() || label.isNotEmpty()) {
                options += SectionBlock.Select.Option(value, label)
            }
            if (selected == null && option.hasAttr("selected")) {
                selected = value
            }
        }
        if (options.isEmpty()) return null
        return SectionBlock.Select(actionIndex(element), options, selected ?: "")
    }

    private fun emitField(element: Element): SectionBlock.Field? {
        val label = firstNonBlank(
            element.attr("aria-label"),
            element.attr("name"),
            element.attr("placeholder"),
        )
        val value = element.attr("value").trim()
        if (label.isEmpty() && value.isEmpty()) return null
        return SectionBlock.Field(label, value)
    }

    private fun emitAction(element: Element): SectionBlock.Action? {
        val label = if (element.normalName() == "input") {
            firstNonBlank(
                element.attr("value"),
                element.attr("aria-label"),
                element.attr("title"),
                element.attr("name"),
            )
        } else {
            firstNonBlank(
                normalize(element.text()),
                element.attr("aria-label"),
                element.attr("title"),
                element.attr("value"),
                element.attr("name"),
            )
        }
        if (label.isEmpty()) return null
        return SectionBlock.Action(actionIndex(element), label)
    }

    private fun emitTable(table: Element): SectionBlock.Table? {
        val rows = mutableListOf<Element>()
        for (child in table.children()) {
            when (child.normalName()) {
                "thead", "tbody", "tfoot" -> {
                    for (tr in child.children()) {
                        if (tr.normalName() == "tr") rows += tr
                    }
                }
                "tr" -> rows += child
            }
        }
        if (rows.isEmpty()) return null

        val headerRow = rows.firstOrNull { row ->
            row.children().any { it.normalName() == "th" }
        } ?: rows.firstOrNull { row ->
            row.parent()?.normalName() == "thead"
        }

        val headers = headerRow?.let(::rowCells) ?: emptyList()
        val dataRows = ArrayList<List<String>>(rows.size)
        for (row in rows) {
            if (row === headerRow) continue
            val cells = rowCells(row)
            if (cells.any { it.isNotEmpty() }) dataRows += cells
        }

        if (headers.all { it.isEmpty() } && dataRows.isEmpty()) return null
        return SectionBlock.Table(headers, dataRows)
    }

    private fun rowCells(row: Element): List<String> {
        val children = row.children()
        val out = ArrayList<String>(children.size)
        for (cell in children) {
            val tag = cell.normalName()
            if (tag == "td" || tag == "th") out += cellText(cell)
        }
        return out
    }

    private fun cellText(cell: Element): String {
        if (cell.childrenSize() == 0 || cell.getElementsByTag("table").isEmpty()) {
            return normalize(cell.text())
        }
        val clone = cell.clone()
        clone.getElementsByTag("table").remove()
        return normalize(clone.text())
    }

    private fun actionIndex(element: Element): Int? =
        element.attr("data-vtop-act").trim().toIntOrNull()

    private fun firstNonBlank(vararg values: String): String {
        for (raw in values) {
            val clean = normalize(raw)
            if (clean.isNotEmpty()) return clean
        }
        return ""
    }

    private fun normalize(raw: String): String =
        raw.replace(whitespace, " ").trim()
}
