package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RejectionClassifierTest {

    @Test
    fun textDangerSpanClassifiesAsCredentialsRejected() {
        val page = """
            <form id="vtopLoginForm" action="/vtop/login" method="post">
                <input type="text" name="username">
                <input type="password" name="password">
            </form>
            <span class="text-danger text-center fw-bold fs-5 d-block">Invalid LoginId/Password</span>
        """.trimIndent()

        val err = RejectionClassifier.classify(page)

        assertTrue(err is VtopError.CredentialsRejected)
        assertEquals("Invalid LoginId/Password", err?.message)
    }

    @Test
    fun formOnlyPageWithoutMessageReturnsNull() {
        val page = """
            <form id="vtopLoginForm" action="/vtop/login" method="post">
                <input type="text" name="username" value="STUDENT">
                <input type="password" name="password">
                <input type="text" name="captchaStr">
            </form>
        """.trimIndent()

        assertNull(RejectionClassifier.classify(page))
    }

    @Test
    fun urlEncodedSimpleSwalClassifiesAsCredentialsRejected() {
        val page = """<script>swal("Invalid%20UserId%20or%20Password");</script>"""

        val err = RejectionClassifier.classify(page)

        assertTrue(err is VtopError.CredentialsRejected)
        assertEquals("Invalid UserId or Password", err?.message)
    }

    @Test
    fun swalTextWithoutCredentialPhraseReturnsNull() {
        val page = """<script>swal({title:"Session Expired"});</script>"""

        assertNull(RejectionClassifier.classify(page))
    }
}
