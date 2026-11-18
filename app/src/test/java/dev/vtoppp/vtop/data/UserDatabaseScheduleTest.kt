package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
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

    private fun at(time: LocalTime): LocalDateTime = wednesday.atTime(time)

    @Test
    fun stampsTheRunningClassOnly() {
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

        // The 09:00 class is over: not running.
        assertFalse(today.classes[0].isOngoing)
        // The 11:00 class is running right now.
        assertTrue(today.classes[1].isOngoing)
        // The 14:00 class is upcoming: not highlighted.
        assertFalse(today.classes[2].isOngoing)
    }

    @Test
    fun clearsTheRunningClassTheMomentItEnds() {
        val days = UserDatabase.buildScheduleDays(
            entries = listOf(
                entry(3, "11:00 AM", "13:10 PM", "STS200"),
                entry(3, "14:00 PM", "15:30 PM", "CSE3001"),
            ),
            referenceDate = wednesday,
            referenceTime = LocalTime.of(13, 15),
        )

        val today = day(days, "Today")
        assertFalse(today.classes.any { it.isOngoing })
    }

    @Test
    fun staysOnTodayWithinThreeHoursOfTheLastClass() {
        val entries = listOf(
            entry(3, "09:00 AM", "10:30 AM", "CSE2005"),
            entry(4, "11:00 AM", "12:00 PM", "CSE2006"),
        )

        // 13:00 same day: the last class ended at 10:30, under 3 hours ago.
        val days = UserDatabase.buildScheduleDays(
            entries = entries,
            referenceDate = wednesday,
            referenceTime = LocalTime.of(13, 0),
        )
        assertEquals(
            days.indexOfFirst { it.dayName == "Today" },
            UserDatabase.nearestClassDayIndex(days, at(LocalTime.of(13, 0))),
        )

        // 13:29: still inside the grace window.
        assertEquals(
            days.indexOfFirst { it.dayName == "Today" },
            UserDatabase.nearestClassDayIndex(days, at(LocalTime.of(13, 29))),
        )

        // 13:30: the grace is over, so tomorrow takes over.
        assertEquals(
            days.indexOfFirst { it.dayName == "Tomorrow" },
            UserDatabase.nearestClassDayIndex(days, at(LocalTime.of(13, 30))),
        )

        // 16:00: well past the grace.
        val late = UserDatabase.buildScheduleDays(
            entries = entries,
            referenceDate = wednesday,
            referenceTime = LocalTime.of(16, 0),
        )
        assertEquals(
            late.indexOfFirst { it.dayName == "Tomorrow" },
            UserDatabase.nearestClassDayIndex(late, at(LocalTime.of(16, 0))),
        )

        assertEquals(0, UserDatabase.nearestClassDayIndex(emptyList(), at(LocalTime.of(16, 0))))
    }

    @Test
    fun opensOnTheNextDayWithClassesWhenTodayHasNone() {
        // Only Thursday has classes; opening the app on Wednesday must not sit
        // on an empty page.
        val days = UserDatabase.buildScheduleDays(
            entries = listOf(
                entry(4, "11:00 AM", "12:00 PM", "CSE2006"),
            ),
            referenceDate = wednesday,
            referenceTime = LocalTime.of(11, 0),
        )

        assertEquals(
            days.indexOfFirst { it.dayName == "Tomorrow" },
            UserDatabase.nearestClassDayIndex(days, at(LocalTime.of(11, 0))),
        )
    }

    @Test
    fun opensOnTodayWhenClassesAreStillToCome() {
        val days = UserDatabase.buildScheduleDays(
            entries = listOf(
                entry(3, "09:00 AM", "10:30 AM", "CSE2005"),
                entry(3, "14:00 PM", "15:30 PM", "CSE3001"),
            ),
            referenceDate = wednesday,
            referenceTime = LocalTime.of(8, 0),
        )

        assertEquals(
            days.indexOfFirst { it.dayName == "Today" },
            UserDatabase.nearestClassDayIndex(days, at(LocalTime.of(8, 0))),
        )
    }

    @Test
    fun skipsDaysWithoutClassesAfterTheGrace() {
        // Only Friday (offset 2) has classes; Thursday is empty.
        val days = UserDatabase.buildScheduleDays(
            entries = listOf(
                entry(3, "09:00 AM", "10:30 AM", "CSE2005"),
                entry(5, "09:00 AM", "10:30 AM", "CSE2007"),
            ),
            referenceDate = wednesday,
            referenceTime = LocalTime.of(16, 0),
        )

        val friday = days.indexOfFirst { it.dayName == "Friday" }
        assertEquals(friday, UserDatabase.nearestClassDayIndex(days, at(LocalTime.of(16, 0))))
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
}
