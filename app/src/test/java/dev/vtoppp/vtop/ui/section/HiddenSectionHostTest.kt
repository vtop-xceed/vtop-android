package dev.vtoppp.vtop.ui.section

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenSectionHostTest {

    @Test
    fun parseOffMainMatchesParse() = runBlocking {
        val html = listOf(
            "<h2>Time Table</h2>",
            "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>",
            "<button data-vtop-act=\"0\">Save</button>",
        ).joinToString("")
        assertEquals(SectionParser.parse(html), SectionParser.parseOffMain(html))
    }

    @Test
    fun parseOffMainBlankHtmlIsEmpty() = runBlocking {
        assertTrue(SectionParser.parseOffMain("   \n\t  ").isEmpty())
    }
}
