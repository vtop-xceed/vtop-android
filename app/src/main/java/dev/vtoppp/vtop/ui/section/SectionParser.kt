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

    private val hiddenClasses = setOf("modal", "offcanvas")

    private val inlineTags = setOf(
        "span", "i", "b", "strong", "em", "u", "s", "small", "font", "br", "a",
        "abbr", "cite", "code", "kbd", "mark", "q", "sub", "sup", "time", "del", "ins",
    )

    private val structuralKinds = setOf(
        Kind.HEADING, Kind.LABEL, Kind.SELECT, Kind.FIELD, Kind.ACTION, Kind.TABLE, Kind.BLOCK,
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
     * (T17.3): snapshot scraping can emit every ~250 ms during JS activity.
     */
    suspend fun parseOffMain(html: String): List<SectionBlock> =
        withContext(Dispatchers.Default) { parse(html) }

    private fun visitChildren(parent: Element, blocks: MutableList<SectionBlock>) {
        val pending = StringBuilder()

        fun flush() {
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

    private fun kindOf(element: Element): Kind {
        val tag = element.normalName()
        if (tag in skipTags) return Kind.SKIP
        if (hiddenClasses.any(element::hasClass)) return Kind.SKIP
        if (tag == "br") return Kind.INLINE
        if (headingLevel(element) != null) return Kind.HEADING
        if (tag == "table") return Kind.TABLE
        if (tag == "select") return Kind.SELECT
        if (tag == "label") return Kind.LABEL
        if (tag == "button") return Kind.ACTION
        if (tag == "input") {
            return when (element.attr("type").trim().lowercase().ifEmpty { "text" }) {
                "hidden" -> Kind.SKIP
                "button", "submit" -> Kind.ACTION
                "text", "date", "number", "password" -> Kind.FIELD
                else -> Kind.SKIP
            }
        }
        if (element.hasAttr("onclick")) return Kind.ACTION
        if (tag in inlineTags) {
            return if (isInlineOnly(element)) Kind.INLINE else Kind.BLOCK
        }
        return Kind.BLOCK
    }

    private fun isInlineOnly(element: Element): Boolean {
        if (element.normalName() !in inlineTags) return false
        if (element.hasAttr("onclick")) return false
        return element.allElements.none { descendant ->
            descendant !== element && kindOf(descendant) in structuralKinds
        }
    }

    private fun headingLevel(element: Element): Int? {
        headingLevelOf(element.normalName())?.let { return it }
        for (className in element.classNames()) {
            headingLevelOf(className.lowercase())?.let { return it }
        }
        return null
    }

    private fun headingLevelOf(name: String): Int? {
        if (name.length != 2 || name[0] != 'h') return null
        return when (name[1]) {
            in '1'..'6' -> name[1] - '0'
            else -> null
        }
    }

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
        val options = element.select("option").mapNotNull { option ->
            val label = normalize(option.text())
            val value = if (option.hasAttr("value")) option.attr("value") else label
            if (value.isEmpty() && label.isEmpty()) {
                null
            } else {
                SectionBlock.Select.Option(value, label)
            }
        }
        if (options.isEmpty()) return null
        val selectedOption = element.select("option[selected]").firstOrNull()
        val selected = when {
            selectedOption == null -> ""
            selectedOption.hasAttr("value") -> selectedOption.attr("value")
            else -> normalize(selectedOption.text())
        }
        return SectionBlock.Select(actionIndex(element), options, selected)
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
                "thead", "tbody", "tfoot" ->
                    child.children().filter { it.normalName() == "tr" }.forEach { rows += it }
                "tr" -> rows += child
            }
        }
        if (rows.isEmpty()) return null

        val headerRow = rows.firstOrNull { row ->
            row.children().any { it.normalName() == "th" }
        } ?: rows.firstOrNull { row ->
            row.parent()?.normalName() == "thead"
        }

        val headers = headerRow?.children()
            ?.filter { it.normalName() == "th" || it.normalName() == "td" }
            ?.map(::cellText)
            ?: emptyList()

        val dataRows = rows.asSequence()
            .filter { it !== headerRow }
            .map { row ->
                row.children()
                    .filter { it.normalName() == "td" || it.normalName() == "th" }
                    .map(::cellText)
            }
            .filter { row -> row.any { it.isNotEmpty() } }
            .toList()

        if (headers.all { it.isEmpty() } && dataRows.isEmpty()) return null
        return SectionBlock.Table(headers, dataRows)
    }

    private fun cellText(cell: Element): String {
        val clone = cell.clone()
        clone.select("table").remove()
        return normalize(clone.text())
    }

    private fun actionIndex(element: Element): Int? =
        element.attr("data-vtop-act").trim().toIntOrNull()

    private fun firstNonBlank(vararg values: String): String =
        values.map(::normalize).firstOrNull { it.isNotEmpty() } ?: ""

    private fun normalize(raw: String): String =
        raw.replace(whitespace, " ").trim()
}
