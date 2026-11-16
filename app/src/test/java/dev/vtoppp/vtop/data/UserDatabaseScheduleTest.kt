package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class UserDatabaseScheduleTest {

    private val wednesday = LocalDate.of(2026, 10, 28)

    private fun entry(day: Int, start: String, end: String, code: String) = TimetableEntry(
        regNo = "24BCY10147",
        dayOfWeek = day,
        startTime = start,
        endTime = end,
        roomBlock = "AB02",
        roomNumber = "320",
        courseCode = code,
        courseTitle = code,
    )

    private fun day(days: List<DaySchedule>, name: String): DaySchedule =
        days.first { it.dayName == name }

    @Test
    fun stampsTheRunningClassAndTheNextOneUp() {
        val days = UserDatabase.buildScheduleDays(
            entries = listOf(
                entry(3, "09:00 AM", "10:30 AM", "CSE2005"),
                entry(3, "11:00 AM", "13:10 PM", "STS200"),
                entry(3, "14:00 PM", "15:30 PM", "CSE3001"),
            ),
            referenceDate = wednesday,
            referenceTime = LocalTime.of(11, 30),
        )

        assertEquals(7, days.size)
        val today = day(days, "Today")
        assertEquals(3, today.classes.size)

        // The 09:00 class is over: neither running nor next up.
        assertFalse(today.classes[0].isOngoing)
        assertFalse(today.classes[0].isClosest)

        // The 11:00 class is running right now.
        assertTrue(today.classes[1].isOngoing)
        assertFalse(today.classes[1].isClosest)

        // The 14:00 class is next up even while another one runs.
        assertFalse(today.classes[2].isOngoing)
        assertTrue(today.classes[2].isClosest)
    }

    @Test
    fun promotesTheNextClassTheMomentTheRunningOneEnds() {
        val days = UserDatabase.buildScheduleDays(
            entries = listOf(
                entry(3, "11:00 AM", "13:10 PM", "STS200"),
                entry(3, "14:00 PM", "15:30 PM", "CSE3001"),
            ),
            referenceDate = wednesday,
            referenceTime = LocalTime.of(13, 15),
        )

        val today = day(days, "Today")
        assertFalse(today.classes[0].isOngoing)
        assertTrue(today.classes[1].isClosest)
    }

    @Test
    fun marksNextClassFromTheFollowingDayWhenTodayIsDone() {
        val days = UserDatabase.buildScheduleDays(
            entries = listOf(
                entry(3, "09:00 AM", "10:30 AM", "CSE2005"),
                entry(4, "11:00 AM", "12:00 PM", "CSE2006"),
            ),
            referenceDate = wednesday,
            referenceTime = LocalTime.of(16, 0),
        )

        val today = day(days, "Today")
        assertTrue(today.classes.none { it.isOngoing || it.isClosest })

        // Tomorrow's first class is the closest one ahead.
        assertTrue(day(days, "Tomorrow").classes[0].isClosest)
    }

    @Test
    fun marksClosestWhenNoClassIsOngoing() {
        val days = UserDatabase.buildScheduleDays(
            entries = listOf(
                entry(3, "09:00 AM", "10:30 AM", "CSE2005"),
                entry(3, "11:00 AM", "13:10 PM", "STS200"),
            ),
            referenceDate = wednesday,
            referenceTime = LocalTime.of(8, 30),
        )

        val today = day(days, "Today")
        assertTrue(today.classes[0].isClosest)
        assertFalse(today.classes[0].isOngoing)
        assertFalse(today.classes[1].isClosest)
    }

    @Test
    fun ordersClassesByClockTimeNotAlphabetically() {
        val days = UserDatabase.buildScheduleDays(
            entries = listOf(
                entry(3, "1:00 PM", "2:00 PM", "LATE"),
                entry(3, "12:00 PM", "12:50 PM", "NOON"),
            ),
            referenceDate = wednesday,
            referenceTime = LocalTime.of(8, 30),
        )

        val today = day(days, "Today")
        assertEquals("12:00 PM", today.classes[0].startTime)
        assertEquals("1:00 PM", today.classes[1].startTime)
    }

    @Test
    fun opensOnTheDayHoldingTheNextClass() {
        val entries = listOf(
            entry(3, "09:00 AM", "10:30 AM", "CSE2005"),
            entry(4, "11:00 AM", "12:00 PM", "CSE2006"),
        )

        // 16:00 on Wednesday: today is done, so tomorrow holds the next class.
        val late = UserDatabase.buildScheduleDays(
            entries = entries,
            referenceDate = wednesday,
            referenceTime = LocalTime.of(16, 0),
        )
        assertEquals(late.indexOfFirst { it.dayName == "Tomorrow" }, UserDatabase.nearestClassDayIndex(late))

        // 08:00 on Wednesday: today still holds the next class.
        val early = UserDatabase.buildScheduleDays(
            entries = entries,
            referenceDate = wednesday,
            referenceTime = LocalTime.of(8, 0),
        )
        assertEquals(early.indexOfFirst { it.dayName == "Today" }, UserDatabase.nearestClassDayIndex(early))

        assertEquals(0, UserDatabase.nearestClassDayIndex(emptyList()))
    }
}
