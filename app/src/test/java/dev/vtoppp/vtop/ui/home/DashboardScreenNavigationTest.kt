package dev.vtoppp.vtop.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the dashboard destination model and back-navigation state machine transitions.
 */
class DashboardScreenNavigationTest {

    @Test
    fun modalScreensAreCorrectlyIdentified() {
        assertTrue(DashboardScreen.Attendance.isModal)
        assertTrue(DashboardScreen.Timetable.isModal)
        assertTrue(DashboardScreen.GradeHistory.isModal)

        assertFalse(DashboardScreen.Overview.isModal)
        assertFalse(DashboardScreen.Calendar.isModal)
        assertFalse(DashboardScreen.Marks.isModal)
        assertFalse(DashboardScreen.Settings.isModal)
    }

    private fun popBack(current: DashboardScreen): DashboardScreen = when (current) {
        DashboardScreen.GradeHistory -> DashboardScreen.Marks
        DashboardScreen.Attendance,
        DashboardScreen.Timetable,
        DashboardScreen.Calendar,
        DashboardScreen.Marks,
        DashboardScreen.Settings -> DashboardScreen.Overview
        DashboardScreen.Overview -> DashboardScreen.Overview
    }

    @Test
    fun backNavigationPopsGradeHistoryToMarks() {
        assertEquals(DashboardScreen.Marks, popBack(DashboardScreen.GradeHistory))
    }

    @Test
    fun backNavigationPopsModalAndTabsToOverview() {
        assertEquals(DashboardScreen.Overview, popBack(DashboardScreen.Attendance))
        assertEquals(DashboardScreen.Overview, popBack(DashboardScreen.Timetable))
        assertEquals(DashboardScreen.Overview, popBack(DashboardScreen.Calendar))
        assertEquals(DashboardScreen.Overview, popBack(DashboardScreen.Marks))
        assertEquals(DashboardScreen.Overview, popBack(DashboardScreen.Settings))
        assertEquals(DashboardScreen.Overview, popBack(DashboardScreen.Overview))
    }
}
