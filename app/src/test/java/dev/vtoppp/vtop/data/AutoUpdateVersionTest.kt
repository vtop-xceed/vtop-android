package dev.vtoppp.vtop.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateVersionTest {
    @Test
    fun `same version is not newer`() {
        assertFalse(AutoUpdater.isNewer("0.1.2", "0.1.2"))
        assertFalse(AutoUpdater.isNewer("v0.1.2", "0.1.2"))
    }

    @Test
    fun `higher remote is newer`() {
        assertTrue(AutoUpdater.isNewer("v0.1.3", "0.1.2"))
        assertTrue(AutoUpdater.isNewer("0.2", "0.1.9"))
        assertTrue(AutoUpdater.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun `lower remote is not newer`() {
        assertFalse(AutoUpdater.isNewer("0.1.1", "0.1.2"))
        assertFalse(AutoUpdater.isNewer("0.1", "0.1.1"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertTrue(AutoUpdater.isNewer("v0.2", "0.1"))
        assertFalse(AutoUpdater.isNewer("v0.1", "0.1.0"))
    }

    @Test
    fun `non numeric tails are ignored`() {
        assertTrue(AutoUpdater.isNewer("0.2.0-all", "0.1.9"))
    }
}
