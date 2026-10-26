package dev.vtoppp.vtop.data

/**
 * T20.2: the swal-object form must surface EVERY field, not just `title:` —
 * a non-overlapping first match previously swallowed `text:`/`html:` keys and
 * missed real `swal({title:"Care!", text:"Invalid ..."})` rejections.
 */
object RejectionClassifier {

    /** Body head of `swal({ ... })`; capped to keep regexes bounded. */
    private val SWAL_OBJECT = Regex("""swal\s*\(\s*\{[^}]{0,500}""")

    /** Any message-bearing field inside a captured swal object body. */
    private val SWAL_FIELD = Regex("""\b(?:title|text|html)\s*:\s*["']([^"']{3,200})["']""")

    /** swal("message") / swal('message') shorthand. */
    private val SWAL_SIMPLE = Regex("""swal\s*\(\s*["']([^"']{3,200})["']""")

    /** Inline server rejection inside the re-issued login form. */
    private val SPAN_DANGER = Regex("""text-danger[^>]*>\s*([^<>\r\n]{5,200})<""")

    val credentialPhrases: List<String> = listOf(
        "invalid credentials", "invalid credential", "incorrect credentials",
        "invalid userid", "invalid user id", "invalid username", "invalid login id",
        "incorrect userid", "incorrect user id", "incorrect username",
        "wrong userid", "wrong user id", "wrong username",
        "invalid password", "incorrect password", "wrong password",
        "authentication failed", "login failed", "account is locked",
        "user id or password", "userid or password",
        "invalid loginid", "invalid loginid password",
    )

    /** Every message-bearing swal field + text-danger span on [page]. */
    fun messages(page: String): List<String> {
        val out = ArrayList<String>()
        SWAL_OBJECT.findAll(page).forEach { objectMatch ->
            SWAL_FIELD.findAll(objectMatch.value)
                .map { it.groupValues[1] }
                .forEach { collect(out, it) }
        }
        SWAL_SIMPLE.findAll(page).forEach { collect(out, it.groupValues[1]) }
        SPAN_DANGER.findAll(page).forEach { collect(out, it.groupValues[1]) }
        return out
    }

    /** [VtopError.CredentialsRejected] with the first matching message, else null. */
    fun classify(page: String): VtopError? {
        for (message in messages(page)) {
            val normalized = message.lowercase().replace('/', ' ')
            if (credentialPhrases.any { normalized.contains(it) }) {
                return VtopError.CredentialsRejected(message)
            }
        }
        return null
    }

    private fun collect(out: MutableList<String>, raw: String) {
        val t = raw.trim()
        out += try {
            java.net.URLDecoder.decode(t.replace('+', ' '), "UTF-8")
        } catch (_: Exception) {
            t
        }
    }
}
