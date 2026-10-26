package dev.vtoppp.vtop.ui.section

sealed interface SectionBlock {
    data class Heading(val text: String, val level: Int) : SectionBlock
    data class Label(val text: String) : SectionBlock
    data class Text(val text: String) : SectionBlock
    data class Field(val label: String, val value: String) : SectionBlock
    data class Select(
        val actionIndex: Int?,
        val options: List<Option>,
        val selected: String,
    ) : SectionBlock {
        data class Option(val value: String, val label: String)
    }
    data class Action(val actionIndex: Int?, val label: String) : SectionBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : SectionBlock
}
