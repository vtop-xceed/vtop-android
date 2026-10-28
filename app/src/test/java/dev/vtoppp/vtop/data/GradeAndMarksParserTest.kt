package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeAndMarksParserTest {

    @Test
    fun extractsSemestersFromStudentMarkViewFixture() {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/StudentMarkView.html")
        assertNotNull("fixture must exist", stream)
        val html = stream!!.bufferedReader().readText()

        val semesters = MarksParser.extractSemesters(html)
        assertTrue("Should extract semesters", semesters.isNotEmpty())
        assertEquals("BL20262701", semesters.first().first)
        assertEquals("Fall Semester 2026-27", semesters.first().second)
        assertEquals("BL20262701", MarksParser.extractLatestSemester(html))
    }

    @Test
    fun parsesMarksTable() {
        val sampleMarksHtml = """
            <div id="main-section">
                <table class="table">
                    <tr>
                        <td>1</td>
                        <td>CSE2005</td>
                        <td>Operating Systems</td>
                        <td>Embedded Theory and Lab</td>
                        <td>A11+B11</td>
                    </tr>
                    <tr>
                        <td>Continuous Assessment Test - 1</td>
                        <td>50.0</td>
                        <td>15.0</td>
                        <td>42.5</td>
                        <td>85.0%</td>
                    </tr>
                    <tr>
                        <td>Digital Assignment 1</td>
                        <td>10.0</td>
                        <td>10.0</td>
                        <td>10.0</td>
                        <td>100.0%</td>
                    </tr>
                    <tr>
                        <td>2</td>
                        <td>STS200</td>
                        <td>Soft Skills</td>
                        <td>Soft Skill</td>
                        <td>C11</td>
                    </tr>
                    <tr>
                        <td>CAT-1</td>
                        <td>50.0</td>
                        <td>20.0</td>
                        <td>45.0</td>
                        <td>90.0%</td>
                    </tr>
                </table>
            </div>
        """.trimIndent()

        val marks = MarksParser.parse(sampleMarksHtml, "24BCY10147", "BL20242501")
        assertEquals(3, marks.size)

        val cat1 = marks[0]
        assertEquals("CSE2005", cat1.courseCode)
        assertEquals("Operating Systems", cat1.courseTitle)
        assertEquals("Continuous Assessment Test - 1", cat1.componentName)
        assertEquals(50.0, cat1.maxMarks, 0.01)
        assertEquals(15.0, cat1.weightagePercent, 0.01)
        assertEquals(42.5, cat1.scoredMarks, 0.01)

        val stsCat = marks[2]
        assertEquals("STS200", stsCat.courseCode)
        assertEquals("CAT-1", stsCat.componentName)
        assertEquals(45.0, stsCat.scoredMarks, 0.01)
    }

    @Test
    fun parsesGradeHistoryWithCgpaAndCourses() {
        val sampleGradeHtml = """
            <div>
                <table>
                    <tr>
                        <td>Cumulative Grade Point Average (CGPA) : 8.85</td>
                        <td>Credits Registered : 42.0</td>
                        <td>Credits Earned : 42.0</td>
                    </tr>
                </table>
                <table class="table">
                    <thead>
                        <tr>
                            <th>S.No</th>
                            <th>Course Code</th>
                            <th>Course Title</th>
                            <th>Course Type</th>
                            <th>Credit</th>
                            <th>Grade</th>
                            <th>Exam Month</th>
                            <th>Course Distribution</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>MAT1001</td>
                            <td>Calculus for Engineers</td>
                            <td>Theory Only</td>
                            <td>4.0</td>
                            <td>S</td>
                            <td>NOV/2024</td>
                            <td>Program Core</td>
                        </tr>
                        <tr>
                            <td>2</td>
                            <td>CSE1001</td>
                            <td>Problem Solving and Programming</td>
                            <td>Embedded Theory and Lab</td>
                            <td>4.0</td>
                            <td>A</td>
                            <td>NOV/2024</td>
                            <td>Program Core</td>
                        </tr>
                        <tr>
                            <td>3</td>
                            <td>ENG1001</td>
                            <td>Basic English</td>
                            <td>Theory Only</td>
                            <td>2.0</td>
                            <td>B</td>
                            <td>NOV/2024</td>
                            <td>University Core</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        """.trimIndent()

        val (summary, grades) = GradeHistoryParser.parse(sampleGradeHtml, "24BCY10147")
        assertNotNull(summary)
        assertEquals(8.85, summary!!.cgpa, 0.01)
        assertEquals(42.0, summary.creditsEarned, 0.01)
        assertEquals(1, summary.sCount)
        assertEquals(1, summary.aCount)
        assertEquals(1, summary.bCount)
        assertEquals(0, summary.cCount)

        assertEquals(3, grades.size)
        assertEquals("MAT1001", grades[0].courseCode)
        assertEquals("Calculus for Engineers", grades[0].courseTitle)
        assertEquals("S", grades[0].grade)
        assertEquals(4.0, grades[0].credits, 0.01)
    }

    @Test
    fun handlesEmptyOrMalformedHtmlGracefully() {
        val (summary, grades) = GradeHistoryParser.parse("", "24BCY10147")
        assertEquals(null, summary)
        assertTrue(grades.isEmpty())

        val marks = MarksParser.parse("<html><body>No marks yet</body></html>", "24BCY10147")
        assertTrue(marks.isEmpty())
    }

    @Test
    fun parsesStudentProfileFromColumnarTable() {
        val html = """
            <table class="customTable">
                <tr class="tableHeader">
                    <td>Reg.No.</td>
                    <td>Name</td>
                    <td>Programme and Branch</td>
                    <td>School</td>
                </tr>
                <tr class="tableContent">
                    <td>24BCY10147</td>
                    <td>MAYANK KUSHWAHA</td>
                    <td>B.Tech. - Computer Science and Engineering (Cyber Security and Digital Forensics)</td>
                    <td>SCAI</td>
                </tr>
            </table>
        """.trimIndent()

        val p = ProfileParser.parse(html, "24BCY10147")
        assertEquals("MAYANK KUSHWAHA", p.name)
        assertEquals("24BCY10147", p.regNo)
        assertEquals("B.Tech. - Computer Science and Engineering (Cyber Security and Digital Forensics)", p.programme)
        assertEquals("SCAI", p.school)
    }
}
