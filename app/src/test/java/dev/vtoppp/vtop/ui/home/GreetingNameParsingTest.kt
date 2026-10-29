package dev.vtoppp.vtop.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM unit check for greeting name parsing: first name extraction,
 * secondary middle/last name handling, uppercase registration IDs, and fallbacks.
 */
class GreetingNameParsingTest {

    @Test
    fun multiWordName_splitsIntoFirstAndSecondary() {
        val parsed = parseDisplayName("MOHAMMED RAYYAN A")
        assertEquals("Mohammed", parsed.firstName)
        assertEquals("Rayyan A", parsed.secondaryName)
    }

    @Test
    fun twoWordName_splitsCleanly() {
        val parsed = parseDisplayName("SHREYANSH JAISWAL")
        assertEquals("Shreyansh", parsed.firstName)
        assertEquals("Jaiswal", parsed.secondaryName)
    }

    @Test
    fun singleWordName_hasNoSecondaryName() {
        val parsed = parseDisplayName("Aditya")
        assertEquals("Aditya", parsed.firstName)
        assertNull(parsed.secondaryName)
    }

    @Test
    fun regNumber_keptAsSingleIdWithoutSecondary() {
        val parsed = parseDisplayName("24BCY10147")
        assertEquals("24BCY10147", parsed.firstName)
        assertNull(parsed.secondaryName)
    }

    @Test
    fun blankOrNull_fallsBackToAuthorizedIdOrStudent() {
        val withId = parseDisplayName("", fallbackId = "24BCY10147")
        assertEquals("24BCY10147", withId.firstName)
        assertNull(withId.secondaryName)

        val nullWithEmptyFallback = parseDisplayName(null, fallbackId = "")
        assertEquals("Student", nullWithEmptyFallback.firstName)
        assertNull(nullWithEmptyFallback.secondaryName)
    }
}
