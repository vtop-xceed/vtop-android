package dev.vtoppp.vtop.ui.section

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionParserTest {

    private fun fixture(name: String): String =
        javaClass.getResource("/fixtures/$name")!!.readText()

    private fun List<SectionBlock>.selects() = filterIsInstance<SectionBlock.Select>()
    private fun List<SectionBlock>.actions() = filterIsInstance<SectionBlock.Action>()
    private fun List<SectionBlock>.headings() = filterIsInstance<SectionBlock.Heading>()
    private fun List<SectionBlock>.texts() = filterIsInstance<SectionBlock.Text>()
    private fun List<SectionBlock>.tables() = filterIsInstance<SectionBlock.Table>()

    @Test
    fun timetableParsesHeadingSelectAndNote() {
        val blocks = SectionParser.parse(fixture("timetable.html"))

        assertTrue(blocks.any { it == SectionBlock.Heading("Time Table", 3) })

        val select = blocks.selects().single()
        assertEquals(7, select.options.size)
        assertTrue(select.options.any { it.value == "BL20262701" })
        assertTrue(select.options.any { it.label.contains("Fall Semester 2026-27") })
        assertEquals("", select.selected)
        assertNull(select.actionIndex)

        assertTrue(blocks.texts().any { "Only Registered Semester" in it.text })
        assertTrue(blocks.any { it is SectionBlock.Label && it.text == "Semester" })
        assertFalse(blocks.texts().any { "Enable JavaScript" in it.text })
        assertFalse(blocks.texts().any { "TESTUSER" in it.text })
    }

    @Test
    fun attendanceParsesSelectAndIgnoresHiddenModal() {
        val blocks = SectionParser.parse(fixture("StudentAttendance.html"))

        assertTrue(blocks.any { it == SectionBlock.Heading("Student Attendance Details", 3) })

        val select = blocks.selects().single()
        assertTrue(select.options.any { it.value == "BL20262701" })
        assertEquals("", select.selected)

        assertTrue(blocks.actions().isEmpty())
        assertTrue(blocks.tables().isEmpty())
        assertFalse(blocks.headings().any { it.text == "Attendance Detail" })
        assertFalse(blocks.texts().any { it.text == "Yet to post." })
        assertFalse(blocks.texts().any { "Enable JavaScript" in it.text })
    }

    @Test
    fun gradeParsesHeadingAndSemesterSelect() {
        val blocks = SectionParser.parse(fixture("StudentGradeView.html"))

        assertTrue(blocks.any { it == SectionBlock.Heading("Result - Grade View", 3) })

        val select = blocks.selects().single()
        assertTrue(select.options.size > 40)
        assertTrue(select.options.any { it.value == "BL20262701" })
        assertEquals("", select.selected)
        assertNull(select.actionIndex)

        assertTrue(blocks.texts().any { it.text == "Select Semester" })

        assertFalse(blocks.texts().any { "TESTUSER" in it.text })
    }

    @Test
    fun marksParsesHeadingLabelAndSelect() {
        val blocks = SectionParser.parse(fixture("StudentMarkView.html"))

        assertTrue(blocks.any { it == SectionBlock.Heading("Marks View", 3) })
        assertTrue(blocks.any { it is SectionBlock.Label && it.text == "Select Semester" })

        val select = blocks.selects().single()
        assertTrue(select.options.any { it.value == "BL20262701" })

        assertFalse(blocks.texts().any { "TESTUSER" in it.text })
    }

    @Test
    fun paymentsParsesNonEmptyContent() {
        val blocks = SectionParser.parse(fixture("Payments.html"))

        assertTrue(blocks.isNotEmpty())
        assertTrue(blocks.headings().any { it.text == "ONLINE PAYMENT" && it.level == 3 })
        assertTrue(blocks.texts().any { it.text.contains("no payment dues", ignoreCase = true) })
        assertFalse(blocks.headings().any { it.text == "TRANSACTION DETAILS" })
        assertTrue(blocks.tables().isEmpty())
        assertFalse(blocks.texts().any { "TESTUSER" in it.text })
    }

    @Test
    fun hiddenModalAndOffcanvasSubtreesAreIgnored() {
        val html = """
            <div>
                <h3>Visible Heading</h3>
                <div class="modal fade" id="detail">
                    <h4 class="modal-title">Attendance Detail</h4>
                    <table><tr><td>hidden</td></tr></table>
                    <button type="button">Close</button>
                </div>
                <div class="offcanvas offcanvas-end" id="drawer">
                    <p>Hidden drawer</p>
                    <button>Drawer action</button>
                </div>
                <p>Visible body</p>
            </div>
        """.trimIndent()

        val blocks = SectionParser.parse(html)

        assertTrue(blocks.any { it == SectionBlock.Heading("Visible Heading", 3) })
        assertEquals(listOf("Visible body"), blocks.texts().map { it.text })
        assertFalse(blocks.texts().any { it.text == "Attendance Detail" })
        assertFalse(blocks.texts().any { it.text == "Hidden drawer" })
        assertTrue(blocks.actions().isEmpty())
        assertTrue(blocks.tables().isEmpty())
    }

    @Test
    fun blankHtmlYieldsNoBlocks() {
        assertTrue(SectionParser.parse("").isEmpty())
        assertTrue(SectionParser.parse("   \n\t  ").isEmpty())
    }

    @Test
    fun malformedHtmlDoesNotThrow() {
        val broken = "<div><table><tr><td>a<td>b</table><select><option value='x'>X"
        assertNotNull(SectionParser.parse(broken))
        assertNotNull(SectionParser.parse("<div><p>unclosed<span>text"))
    }

    @Test
    fun actionIndexesSelectsActionsAndFieldsAreParsed() {
        val html = """
            <div>
                <select data-vtop-act="3" name="s">
                    <option value="a">A</option>
                    <option value="b" selected>B</option>
                </select>
                <button data-vtop-act="4" type="button">Go</button>
                <input type="text" name="roll" aria-label="Roll Number" value="X1">
                <input type="hidden" name="_csrf" value="secret">
            </div>
        """.trimIndent()

        val blocks = SectionParser.parse(html)

        val select = blocks.selects().single()
        assertEquals(3, select.actionIndex)
        assertEquals("b", select.selected)
        assertEquals(listOf("a", "b"), select.options.map { it.value })

        val action = blocks.actions().single()
        assertEquals(4, action.actionIndex)
        assertEquals("Go", action.label)

        val field = blocks.filterIsInstance<SectionBlock.Field>().single()
        assertEquals("Roll Number", field.label)
        assertEquals("X1", field.value)

        assertFalse(blocks.texts().any { "secret" in it.text })
        assertFalse(blocks.texts().any { "a" == it.text })
    }

    @Test
    fun inlineContainersCollapseToSingleText() {
        val blocks = SectionParser.parse("<div><span>Hello</span> <b>world</b><br><i>again</i></div>")
        assertEquals(listOf("Hello world again"), blocks.texts().map { it.text })
    }

    @Test
    fun nbspIsNormalizedAndBlankTextDropped() {
        val blocks = SectionParser.parse("<div><p>Alpha\u00A0\u00A0Beta</p><p>&nbsp;</p></div>")
        assertEquals(listOf("Alpha Beta"), blocks.texts().map { it.text })
    }

    @Test
    fun tableRowsExcludeNestedTables() {
        val html = """
            <table>
                <thead><tr><th>H1</th><th>H2</th></tr></thead>
                <tbody>
                    <tr><td>a</td><td>b</td></tr>
                    <tr><td>c<table><tr><td>nested</td></tr></table></td><td>d</td></tr>
                </tbody>
            </table>
        """.trimIndent()

        val blocks = SectionParser.parse(html)
        val table = blocks.tables().single()
        assertEquals(listOf("H1", "H2"), table.headers)
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), table.rows)
        assertFalse(blocks.texts().any { "nested" in it.text })
    }
}
