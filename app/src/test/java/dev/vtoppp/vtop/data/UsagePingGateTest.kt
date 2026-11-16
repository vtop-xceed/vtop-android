package dev.vtoppp.vtop.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePingGateTest {
    @Test
    fun `a disabled service never sends`() {
        assertFalse(UsagePing.shouldSendPing(enabled = false, lastPingDay = null, today = "2026-11-15"))
    }

    @Test
    fun `an enabled service sends once per utc day`() {
        assertTrue(UsagePing.shouldSendPing(enabled = true, lastPingDay = null, today = "2026-11-15"))
        assertFalse(UsagePing.shouldSendPing(enabled = true, lastPingDay = "2026-11-15", today = "2026-11-15"))
        assertTrue(UsagePing.shouldSendPing(enabled = true, lastPingDay = "2026-11-14", today = "2026-11-15"))
    }
}
