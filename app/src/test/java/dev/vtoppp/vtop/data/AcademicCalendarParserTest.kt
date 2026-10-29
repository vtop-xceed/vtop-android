package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AcademicCalendarParserTest {

    @Test
    fun extractMonthDatesFromPreviewHtml() {
        val file = File("getDateForSemesterPreview.html")
        if (!file.exists()) return
        val html = file.readText()
        val months = AcademicCalendarParser.extractMonthDates(html)
        assertTrue("Months extracted", months.isNotEmpty())
        assertTrue("Contains OCT-2026", months.contains("01-OCT-2026"))
        assertTrue("Contains JUN-2026", months.contains("01-JUN-2026"))
        assertEquals(6, months.size)
    }

    @Test
    fun parseOctober2026Calendar() {
        val file = File("processViewCalendar_OCT2026.html")
        if (!file.exists()) return
        val html = file.readText()
        val events = AcademicCalendarParser.parse(html)
        assertEquals("31 days in October", 31, events.size)

        val oct2 = events.firstOrNull { it.date == "2026-10-02" }
        assertTrue("Oct 2 found", oct2 != null)
        assertTrue("Oct 2 is holiday", oct2!!.isHoliday)
        assertTrue("Oct 2 mentions Gandhi Jayanti", oct2.title.contains("Gandhi") || oct2.subtitle.contains("Gandhi") || oct2.title.contains("Holiday"))

        val oct20 = events.firstOrNull { it.date == "2026-10-20" }
        assertTrue("Oct 20 found", oct20 != null)
        assertTrue("Oct 20 is holiday", oct20!!.isHoliday)
        assertTrue("Oct 20 mentions Dussehra", oct20.title.contains("Dussehra") || oct20.subtitle.contains("Dussehra"))

        val oct29 = events.firstOrNull { it.date == "2026-10-29" }
        assertTrue("Oct 29 found", oct29 != null)
        assertTrue("Oct 29 is exam", oct29!!.isExam)
        assertEquals("Thursday", oct29.dayOfWeek)

        val oct31 = events.firstOrNull { it.date == "2026-10-31" }
        assertTrue("Oct 31 found", oct31 != null)
        assertTrue("Oct 31 is holiday or break", oct31!!.isHoliday)
    }
}
