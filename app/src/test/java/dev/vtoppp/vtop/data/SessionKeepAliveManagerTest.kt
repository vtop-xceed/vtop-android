package dev.vtoppp.vtop.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionKeepAliveManagerTest {

    @Test
    fun momentaryPingAlwaysHidesNotificationEvenOnFailureAndException() = runTest {
        val events = mutableListOf<String>()

        val okSuccess = SessionKeepAliveManager.runMomentaryPing(
            show = { events += "show" },
            hide = { events += "hide" },
            minDisplayMs = 0L,
        ) {
            events += "ping"
            true
        }
        assertTrue(okSuccess)
        assertEquals(listOf("show", "ping", "hide"), events)

        events.clear()
        val okFalse = SessionKeepAliveManager.runMomentaryPing(
            show = { events += "show" },
            hide = { events += "hide" },
            minDisplayMs = 0L,
        ) {
            events += "ping-fail"
            false
        }
        assertFalse(okFalse)
        assertEquals(listOf("show", "ping-fail", "hide"), events)

        events.clear()
        val okThrow = SessionKeepAliveManager.runMomentaryPing(
            show = { events += "show" },
            hide = { events += "hide" },
            minDisplayMs = 0L,
        ) {
            events += "ping-throw"
            error("boom")
        }
        assertFalse(okThrow)
        assertEquals(listOf("show", "ping-throw", "hide"), events)
    }

    @Test
    fun keepAliveWindowBoundsAreInexactUnderTwentyMinutes() {
        assertEquals(10 * 60 * 1000L, SessionKeepAliveManager.MIN_INTERVAL_MS)
        assertEquals(15 * 60 * 1000L, SessionKeepAliveManager.MAX_INTERVAL_MS)
        assertTrue(SessionKeepAliveManager.MAX_INTERVAL_MS < 20 * 60 * 1000L)
    }
}
