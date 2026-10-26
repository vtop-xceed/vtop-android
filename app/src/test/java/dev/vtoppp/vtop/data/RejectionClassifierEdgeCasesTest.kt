package dev.vtoppp.vtop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T20.2 edge cases beyond [RejectionClassifierTest]: the SWAL-object form,
 * (+)-decode corruption, length bounds (3-200) and first-match-wins order.
 * "account is locked" intentionally stays CredentialsRejected —
 * [VtopError.RateLimited] is reserved for an empty captcha endpoint, and a
 * locked account is an office-hours unlock, not an automatic retry.
 */
class RejectionClassifierEdgeCasesTest {

    @Test
    fun swalObjectTextKeyClassifiesAndUrlDecodes() {
        val page = """
            <script>
                swal({title: "Care!", text: "Invalid%20User%20Id%20or%20Password", icon: "error"});
            </script>
        """.trimIndent()

        val err = RejectionClassifier.classify(page)

        assertTrue(err is VtopError.CredentialsRejected)
        assertEquals("Invalid User Id or Password", err?.message)
    }

    @Test
    fun swalObjectHtmlKeyIsCollected() {
        val page = """<script>swal({ title:"Care!", html:"<b>Login failed</b>"});</script>"""

        val err = RejectionClassifier.classify(page)

        assertTrue(err is VtopError.CredentialsRejected)
        assertEquals("<b>Login failed</b>", err?.message)
    }

    @Test
    fun accountLockedMapsToCredentialsRejected() {
        val page = """<span class="text-danger">Account is locked. Contact office.</span>"""

        val err = RejectionClassifier.classify(page)

        assertTrue(err is VtopError.CredentialsRejected)
        assertEquals("Account is locked. Contact office.", err?.message)
    }

    @Test
    fun rateLimitedPhraseIsNeverInjected() {
        val page = """<script>swal("Rate limit reached");</script>"""

        assertNull(RejectionClassifier.classify(page))
    }

    @Test
    fun plusDecodeCorruptionStillMatchesPhrase() {
        // URL-encoded spaces can arrive as +, so the classifier treats +
        // as space and the phrase still matches.
        val page = """<script>swal("Invalid+Login+Id");</script>"""

        val err = RejectionClassifier.classify(page)

        assertTrue(err is VtopError.CredentialsRejected)
        assertEquals("Invalid Login Id", err?.message)
    }

    @Test
    fun underThreeCharsIsDropped() {
        val page = """<script>swal("in");</script>"""
        assertEquals(emptyList<String>(), RejectionClassifier.messages(page))
        assertNull(RejectionClassifier.classify(page))
    }

    @Test
    fun overTwoHundredCharsIsDropped() {
        val tail = "Invalid UserId ".repeat(15) // > 200 chars
        val page = """<script>swal("$tail");</script>"""
        assertNull(RejectionClassifier.classify(page))
    }

    @Test
    fun firstMessageWinsOverLaterNonMatches() {
        val page = """
            <script>swal("Invalid UserId");</script>
            <span class="text-danger">Something unrelated fell through</span>
        """.trimIndent()

        val err = RejectionClassifier.classify(page)

        assertTrue(err is VtopError.CredentialsRejected)
        assertEquals("Invalid UserId", err?.message)
    }

    @Test
    fun firstNonMatchingMessageDoesNotBlockLaterMatch() {
        val page = """
            <script>swal("Filed Portrait render check");</script>
            <script>swal("Incorrect Password");</script>
        """.trimIndent()

        val err = RejectionClassifier.classify(page)

        assertTrue(err is VtopError.CredentialsRejected)
        assertEquals("Incorrect Password", err?.message)
    }
}
