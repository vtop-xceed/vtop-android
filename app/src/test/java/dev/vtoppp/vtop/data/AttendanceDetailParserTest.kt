package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceDetailParserTest {

    private val html = """
        <table><tr><td>Class Group</td><td>Course Detail</td><td>Class Detail</td><td>Faculty Detail</td><td>Registered Date &amp; Time</td><td>Attendance Date / Type</td><td>Attendance Status</td><td>Debar Status</td></tr>
        <tr><td colspan="8">summary row here</td></tr></table>
        <table><tr><td>Sl.No.</td><td>Date</td><td>Slot</td><td>Day / Time</td><td>Status</td></tr>
        <tr><td>1</td><td>12-10-2026</td><td>B11</td><td>MON / 10:05-11:35</td><td><span>Absent</span></td></tr>
        <tr><td>2</td><td>10-10-2026</td><td>B13</td><td>FRI / 10:05-11:35</td><td><span>Present</span></td></tr>
        <tr><td>3</td><td>09-10-2026</td><td>B12</td><td>WED / 10:05-11:35</td><td><span>On Duty</span></td></tr>
        </table>
    """.trimIndent()

    @Test
    fun parsesSessionsWithIsoDates() {
        val out = AttendanceDetailParser.parse(html, "REG", "SEM", "CSE3001")
        assertEquals(3, out.size)
        assertEquals("2026-10-12", out[0].date)
        assertEquals("B11", out[0].slot)
        assertEquals("Absent", out[0].status)
        assertEquals("Present", out[1].status)
        assertEquals("On Duty", out[2].status)
    }

    @Test
    fun ignoresSummaryTable() {
        val out = AttendanceDetailParser.parse(html, "REG", "SEM", "CSE3001")
        assertTrue(out.all { it.date.startsWith("2026-") })
    }

    @Test
    fun blankInput_yieldsNothing() {
        assertTrue(AttendanceDetailParser.parse("", "REG", "SEM", "X").isEmpty())
    }
}
