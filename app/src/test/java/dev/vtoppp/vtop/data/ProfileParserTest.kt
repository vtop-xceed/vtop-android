package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileParserTest {

    @Test
    fun parsesStandardVtopProfileTable() {
        val html = """
            <html>
            <body>
                <table class="table">
                    <tr>
                        <td>Registration Number</td>
                        <td>24BCY10147</td>
                        <td>Student Name</td>
                        <td>ADITYA VERMA</td>
                    </tr>
                    <tr>
                        <td>Programme</td>
                        <td>Bachelor of Technology</td>
                        <td>Branch</td>
                        <td>Computer Science and Engineering</td>
                    </tr>
                    <tr>
                        <td>School</td>
                        <td>School of Computer Science and Engineering</td>
                        <td>Email ID</td>
                        <td>aditya.verma2024@vitstudent.ac.in</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val profile = ProfileParser.parse(html, "FALLBACK_ID")

        assertEquals("24BCY10147", profile.regNo)
        assertEquals("ADITYA VERMA", profile.name)
        assertEquals("Aditya", profile.firstName)
        assertEquals("Bachelor of Technology", profile.programme)
        assertEquals("Computer Science and Engineering", profile.branch)
        assertEquals("B.Tech CSE", profile.formattedBranch)
        assertEquals("School of Computer Science and Engineering", profile.school)
        assertEquals("aditya.verma2024@vitstudent.ac.in", profile.email)
    }

    @Test
    fun handlesAlternativeDegreeAndBranchFormats() {
        val html = """
            <table>
                <tr>
                    <td>Reg. No:</td><td>21BCE0001</td>
                    <td>Name:</td><td>PRIYA SUNDARAM</td>
                </tr>
                <tr>
                    <td>Degree:</td><td>Master of Technology</td>
                    <td>Department:</td><td>Electronics and Communication</td>
                </tr>
            </table>
        """.trimIndent()

        val profile = ProfileParser.parse(html, "21BCE0001")

        assertEquals("21BCE0001", profile.regNo)
        assertEquals("PRIYA SUNDARAM", profile.name)
        assertEquals("Priya", profile.firstName)
        assertEquals("M.Tech ECE", profile.formattedBranch)
    }

    @Test
    fun fallsBackGracefullyOnEmptyOrMalformedHtml() {
        val profile = ProfileParser.parse("", "24BCY10147")

        assertEquals("24BCY10147", profile.regNo)
        assertEquals("24BCY10147", profile.name)
        assertEquals("24BCY10147", profile.firstName)
        assertEquals("Student", profile.formattedBranch)
    }
}
