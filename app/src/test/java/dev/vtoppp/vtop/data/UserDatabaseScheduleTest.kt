package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class UserDatabaseScheduleTest {

    @Test
    fun buildsDynamicScheduleDaysWithOngoingAndClosest() {
        val testWednesday = LocalDate.of(2026, 10, 28) // Wednesday (dayOfWeek = 3)
        val currentTime = LocalTime.of(11, 30) // 11:30 AM

        val entries = listOf(
            TimetableEntry(
                regNo = "24BCY10147",
                dayOfWeek = 3, // Wednesday
                startTime = "09:00 AM",
                endTime = "10:30 AM",
                roomBlock = "SJT",
                roomNumber = "401",
                courseCode = "CSE2005",
                courseTitle = "Operating Systems",
                slots = listOf("A11"),
                activeSlot = "A11",
            ),
            TimetableEntry(
                regNo = "24BCY10147",
                dayOfWeek = 3, // Wednesday
                startTime = "11:00 AM",
                endTime = "13:10 PM",
                roomBlock = "AB",
                roomNumber = "330",
                courseCode = "STS200",
                courseTitle = "Soft Skills",
                slots = listOf("A11", "B11"),
                activeSlot = "B11",
            ),
            TimetableEntry(
                regNo = "24BCY10147",
                dayOfWeek = 3, // Wednesday
                startTime = "14:00 PM",
                endTime = "15:30 PM",
                roomBlock = "AB02",
                roomNumber = "171",
                courseCode = "CSE3001",
                courseTitle = "System Design",
                slots = listOf("A14"),
                activeSlot = "A14",
            ),
        )

        val days = UserDatabase.buildScheduleDays(
            entries = entries,
            referenceDate = testWednesday,
            referenceTime = currentTime,
        )

        assertEquals(7, days.size)

        // Day 1 is Today (Wednesday)
        val today = days.find { it.dayName == "Today" }!!
        assertEquals(3, today.classes.size)

        val pastClass = today.classes[0]
        assertFalse(pastClass.isOngoing)
        assertFalse(pastClass.isClosest)

        // 11:00 AM - 13:10 PM should be marked ONGOING at 11:30 AM
        val runningClass = today.classes[1]
        assertTrue(runningClass.isOngoing)
        assertFalse(runningClass.isClosest)

        // 14:00 PM is upcoming, not closest because one is already ongoing
        val upcomingClass = today.classes[2]
        assertFalse(upcomingClass.isOngoing)
        assertFalse(upcomingClass.isClosest)
    }

    @Test
    fun marksClosestWhenNoClassIsOngoing() {
        val testWednesday = LocalDate.of(2026, 10, 28) // Wednesday
        val morningTime = LocalTime.of(8, 30) // 08:30 AM (before first class at 09:00 AM)

        val entries = listOf(
            TimetableEntry(
                regNo = "24BCY10147",
                dayOfWeek = 3,
                startTime = "09:00 AM",
                endTime = "10:30 AM",
                roomBlock = "SJT",
                roomNumber = "401",
                courseCode = "CSE2005",
                courseTitle = "Operating Systems",
            ),
            TimetableEntry(
                regNo = "24BCY10147",
                dayOfWeek = 3,
                startTime = "11:00 AM",
                endTime = "13:10 PM",
                roomBlock = "AB",
                roomNumber = "330",
                courseCode = "STS200",
                courseTitle = "Soft Skills",
            ),
        )

        val days = UserDatabase.buildScheduleDays(
            entries = entries,
            referenceDate = testWednesday,
            referenceTime = morningTime,
        )

        val today = days.find { it.dayName == "Today" }!!
        // The first class at 09:00 AM should be marked isClosest
        assertTrue(today.classes[0].isClosest)
        assertFalse(today.classes[0].isOngoing)
        assertFalse(today.classes[1].isClosest)
    }
}
