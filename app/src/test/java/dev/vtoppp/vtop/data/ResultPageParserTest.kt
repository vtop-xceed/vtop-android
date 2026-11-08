package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultPageParserTest {

    @Test
    fun extractsGpaWithTwoDecimals() {
        val html = """<tr align="center"><span style="font-size: 18px; font-weight: bold;">GPA : 8.22</span></tr>"""
        assertEquals(8.22, ResultPageParser.parseGpa(html)!!, 0.001)
    }

    @Test
    fun extractsGpaWithSingleDecimal() {
        val html = """<span style="font-size: 18px;">GPA : 8.5</span>"""
        assertEquals(8.5, ResultPageParser.parseGpa(html)!!, 0.001)
    }

    @Test
    fun returnsNullWhenSemesterNotGraded() {
        val html = """<span style="font-size: 20px; color: green;"></span>GPA/CGPA details"""
        assertNull(ResultPageParser.parseGpa(html))
    }

    @Test
    fun returnsNullOnBlankInput() {
        assertNull(ResultPageParser.parseGpa(""))
    }
}
