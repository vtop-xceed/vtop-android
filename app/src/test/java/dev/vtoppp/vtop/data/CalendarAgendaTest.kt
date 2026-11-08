package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAgendaTest {

    private fun day(iso: String, title: String, category: String, subtitle: String = ""): CalendarDayEvent =
        CalendarDayEvent(
            date = iso,
            dayNumber = iso.takeLast(2).toInt(),
            monthYear = "OCTOBER 2026",
            dayOfWeek = "",
            title = title,
            subtitle = subtitle,
            eventCategory = category,
            isHoliday = category == "HOLIDAY",
            isInstructional = category == "INSTRUCTIONAL",
            isExam = category == "EXAM",
        )

    @Test
    fun collapse_mergesConsecutiveIdenticalDays() {
        val events = (2..7).map { day("2026-10-%02d".format(it), "Diwali Break", "HOLIDAY", "No Instructional Day - General") }
        val rows = CalendarAgenda.collapse(events, "2026-10-01")
        assertEquals(1, rows.size)
        assertEquals("2026-10-02", rows[0].startIso)
        assertEquals("2026-10-07", rows[0].endIso)
        assertEquals(6, rows[0].dayCount)
        assertTrue(rows[0].isPast.not())
    }

    @Test
    fun collapse_doesNotMergeAcrossDifferentTitlesOrDates() {
        val events = listOf(
            day("2026-10-01", "Gandhi Jayanti", "HOLIDAY"),
            day("2026-10-02", "Diwali Break", "HOLIDAY"),
            day("2026-10-04", "Diwali Break", "HOLIDAY"),
        )
        val rows = CalendarAgenda.collapse(events, "2026-10-01")
        assertEquals(3, rows.size)
        assertEquals(1, rows[2].dayCount)
        assertTrue(rows[2].dayCount == 1)
    }

    @Test
    fun collapse_marksTodayAndPast() {
        val events = listOf(day("2026-10-31", "Semester break for students only", "HOLIDAY"))
        val today = CalendarAgenda.collapse(events, "2026-10-31").single()
        assertTrue(today.isToday)
        assertFalse(today.isPast)
        val past = CalendarAgenda.collapse(events, "2026-11-02").single()
        assertTrue(past.isPast)
    }

    @Test
    fun nextSpecial_skipsInstructionalAndPast() {
        val events = listOf(
            day("2026-10-01", "Instructional Day", "INSTRUCTIONAL"),
            day("2026-10-02", "Gandhi Jayanti", "HOLIDAY"),
            day("2026-10-05", "CAT-1", "EXAM"),
        )
        val next = CalendarAgenda.nextSpecial(events, "2026-09-30")
        assertEquals("2026-10-02", next?.startIso)
        val none = CalendarAgenda.nextSpecial(events, "2026-10-05")
        assertEquals(null, none)
    }

    @Test
    fun upcoming_returnsOngoingAndFutureSpecialsOnly() {
        val events = listOf(
            day("2026-11-01", "Instructional Day", "INSTRUCTIONAL"),
            day("2026-11-07", "Diwali Break", "HOLIDAY"),
            day("2026-11-08", "Diwali Break", "HOLIDAY"),
            day("2026-11-10", "Convergence", "INFO"),
            day("2026-11-12", "Instructional Day", "INSTRUCTIONAL"),
        )
        val u = CalendarAgenda.upcoming(events, "2026-11-08")
        assertEquals(2, u.size)
        assertEquals("2026-11-07", u[0].startIso)
        assertEquals("2026-11-10", u[1].startIso)
    }

    @Test
    fun humanCategory_coversKnownCategories() {
        assertEquals("Holiday", CalendarAgenda.humanCategory("HOLIDAY"))
        assertEquals("Exam", CalendarAgenda.humanCategory("EXAM"))
        assertEquals("Class", CalendarAgenda.humanCategory("INSTRUCTIONAL"))
        assertEquals("Holiday", CalendarAgenda.humanCategory("NO_CLASS"))
    }
}
