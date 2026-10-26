package dev.vtoppp.vtop.ui.section

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T20.7: parser failure modes on hostile/simple dashboard shapes: noscript
 * and JS-fenced markup, a login-page body, valueless options, thead-only
 * tables, non-numeric action indexes and nbsp rows. Pure JVM (jsoup).
 */
class SectionParserFailureModesTest {

    @Test
    fun noscriptContentIsSkipped() {
        val html = """
            <body>
                <noscript><option>Trackers disabled</option></noscript>
                <h2>Real heading</h2>
            </body>
        """.trimIndent()

        val blocks = SectionParser.parse(html)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is SectionBlock.Heading)
    }

    @Test
    fun scriptFencedMarkupIsSkipped() {
        val html = """
            <body>
                <script>const table = "<td>ghost</td>";</script>
                <p>Visible text</p>
            </body>
        """.trimIndent()

        val blocks = SectionParser.parse(html)

        assertEquals(1, blocks.size)
        assertEquals("Visible text", (blocks[0] as SectionBlock.Text).text)
    }

    @Test
    fun loginPageBodyYieldsFieldAndActionOnly() {
        val html = """
            <body>
                <form id="vtopLoginForm" class="stdForm">
                    <input type="text" name="username" value="">
                    <input type="password" name="password" placeholder="Password">
                    <button type="submit">Sign in</button>
                </form>
            </body>
        """.trimIndent()

        val blocks = SectionParser.parse(html).filterIsInstance<SectionBlock.Field>()

        assertEquals(2, blocks.size)
    }

    @Test
    fun valuelessOptionsAreDroppedButSurvivorsEmitASelect() {
        val html = """
            <select name="semester" data-vtop-act="2">
                <option value=""></option>
                <option value="8">Autumn 2026</option>
            </select>
        """.trimIndent()

        val blocks = SectionParser.parse(html)

        val select = blocks[0] as SectionBlock.Select
        assertEquals(listOf(SectionBlock.Select.Option("8", "Autumn 2026")), select.options)
        assertEquals(2, select.actionIndex)
    }

    @Test
    fun selectWithOnlyEmptyOptionsEmitsNothing() {
        val html = """<select name="semester"><option value=""></option></select>"""

        assertEquals(emptyList<SectionBlock>(), SectionParser.parse(html))
    }

    @Test
    fun optionWithoutValueAttributeFallsBackToLabel() {
        val html = """
            <select name="semester">
                <option selected>Autumn 2026</option>
            </select>
        """.trimIndent()

        val blocks = SectionParser.parse(html)
        val select = blocks[0] as SectionBlock.Select

        assertEquals(listOf(SectionBlock.Select.Option("Autumn 2026", "Autumn 2026")), select.options)
        assertEquals("Autumn 2026", select.selected)
    }

    @Test
    fun theadOnlyTableProducesHeaderOnlyTable() {
        val html = """
            <table>
                <thead><tr><th>Course</th><th>Credits</th></tr></thead>
            </table>
        """.trimIndent()

        val blocks = SectionParser.parse(html)
        val table = blocks[0] as SectionBlock.Table

        assertEquals(listOf("Course", "Credits"), table.headers)
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun tableWithTbodyOnlyFallsBackToEmptyHeaders() {
        val html = """
            <table>
                <tbody>
                    <tr><td>CSC 101</td><td>4</td></tr>
                </tbody>
            </table>
        """.trimIndent()

        val blocks = SectionParser.parse(html)
        val table = blocks[0] as SectionBlock.Table

        assertTrue(table.headers.isEmpty())
        assertEquals(listOf(listOf("CSC 101", "4")), table.rows)
    }

    @Test
    fun emptyAllEmptyTableEmitsNothing() {
        val html = """<table><tbody><tr><td></td></tr></tbody></table>"""

        assertEquals(emptyList<SectionBlock>(), SectionParser.parse(html))
    }

    @Test
    fun nonNumericActionIndexBecomesNull() {
        val html = """<button data-vtop-act="#">View</button>"""

        val blocks = SectionParser.parse(html)
        val action = blocks[0] as SectionBlock.Action

        assertNull(action.actionIndex)
    }

    @Test
    fun nonBreakingSpaceRowsNormalize() {
        val html = """
            <table>
                <tbody><tr><td>Name&#160;Surname</td></tr></tbody>
            </table>
        """.trimIndent()

        val blocks = SectionParser.parse(html)
        val table = blocks[0] as SectionBlock.Table

        assertEquals(listOf(listOf("Name Surname")), table.rows)
    }
}
