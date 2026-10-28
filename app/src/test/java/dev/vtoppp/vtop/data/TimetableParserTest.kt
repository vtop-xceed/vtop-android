package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserTest {

    @Test
    fun parsesVenueFormatsCorrectly() {
        val (b1, r1) = TimetableParser.parseVenue("AB-330")
        assertEquals("AB", b1)
        assertEquals("330", r1)

        val (b2, r2) = TimetableParser.parseVenue("AB02-171")
        assertEquals("AB02", b2)
        assertEquals("171", r2)

        val (b3, r3) = TimetableParser.parseVenue("SJT 711")
        assertEquals("SJT", b3)
        assertEquals("711", r3)

        val (b4, r4) = TimetableParser.parseVenue("TT213")
        assertEquals("TT", b4)
        assertEquals("213", r4)
    }

    @Test
    fun parsesDayWiseGridTable() {
        val html = """
            <html>
            <body>
                <table>
                    <thead>
                        <tr>
                            <th>DAY</th>
                            <th>09:00 - 10:30</th>
                            <th>11:00 - 13:10</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>MON</td>
                            <td>CSE2005 - Operating Systems - A11 - SJT-401</td>
                            <td>-</td>
                        </tr>
                        <tr>
                            <td>WED</td>
                            <td>-</td>
                            <td>STS200 - Soft Skills - B11 - AB-330</td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val entries = TimetableParser.parse(html, "24BCY10147")
        assertEquals(2, entries.size)

        val monEntry = entries.find { it.dayOfWeek == 1 }!!
        assertEquals("CSE2005", monEntry.courseCode)
        assertEquals("09:00", monEntry.startTime)
        assertEquals("10:30", monEntry.endTime)
        assertEquals("SJT", monEntry.roomBlock)
        assertEquals("401", monEntry.roomNumber)

        val wedEntry = entries.find { it.dayOfWeek == 3 }!!
        assertEquals("STS200", wedEntry.courseCode)
        assertEquals("11:00", wedEntry.startTime)
        assertEquals("13:10", wedEntry.endTime)
        assertEquals("AB", wedEntry.roomBlock)
        assertEquals("330", wedEntry.roomNumber)
    }

    @Test
    fun parsesCourseListTableWithSlotExpansion() {
        val html = """
            <html>
            <body>
                <table class="table">
                    <tr>
                        <td>1</td>
                        <td>CSE3001</td>
                        <td>System Design</td>
                        <td>Embedded Theory</td>
                        <td>3 0 0 0 3</td>
                        <td>A11</td>
                        <td>AB02-171</td>
                        <td>DR. SURESH</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val entries = TimetableParser.parse(html, "24BCY10147")
        assertFalse(entries.isEmpty())
        val first = entries[0]
        assertEquals("CSE3001", first.courseCode)
        assertEquals("System Design", first.courseTitle)
        assertEquals("AB02", first.roomBlock)
        assertEquals("171", first.roomNumber)
        assertEquals("A11", first.activeSlot)
    }

    @Test
    fun parsesVtopMatrixGridWithTwoRowHeaders() {
        val html = """
            <html>
            <body>
                <table>
                    <tr><td>Sl.No</td><td>Class</td><td>Course</td><td>L T P J C</td><td>Cat</td><td>Option</td><td>Id</td><td>Slot/Venue</td></tr>
                    <tr><td>1</td><td>Gen</td><td>CSE3001 - Database Systems (Lecture)</td><td>3 0 0 0 3</td><td>PC</td><td>Reg</td><td>123</td><td>B11+B12+B13 - AR-202</td></tr>
                </table>
                <table>
                    <tr><td>THEORY</td><td>Start</td><td>10:05</td><td>11:40</td></tr>
                    <tr><td>End</td><td>11:35</td><td>13:10</td></tr>
                    <tr><td>MON</td><td>THEORY</td><td>B11-CSE3001-LTP-AR-202-ALL</td><td>-</td></tr>
                </table>
            </body>
            </html>
        """.trimIndent()
        val entries = TimetableParser.parse(html, "24BCY10147", "BL20262701")
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("CSE3001", e.courseCode)
        assertEquals("Database Systems", e.courseTitle)
        assertEquals("10:05", e.startTime)
        assertEquals("11:35", e.endTime)
        assertEquals("AR", e.roomBlock)
        assertEquals("202", e.roomNumber)
        assertEquals("B11", e.activeSlot)
        assertEquals(listOf("B11", "B12", "B13"), e.slots)
    }

    @Test
    fun handlesEmptyOrMalformedHtmlGracefully() {
        val entries = TimetableParser.parse("", "24BCY10147")
        assertTrue(entries.isEmpty())
    }
}
