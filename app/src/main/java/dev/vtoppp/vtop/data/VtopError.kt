package dev.vtoppp.vtop.data

sealed class VtopError : Exception() {
    data object CaptchaRejected : VtopError() { override val message: String get() = "CAPTCHA_REJECTED" }
    data object RateLimited : VtopError() { override val message: String get() = "RATE_LIMITED" }
    data object SessionExpired : VtopError() { override val message: String get() = "SESSION_EXPIRED" }
    data class CredentialsRejected(override val message: String = "CREDENTIALS_REJECTED") : VtopError()
    data class Blocked(override val message: String = "WAF_BLOCKED") : VtopError()
    data object Network : VtopError() { override val message: String get() = "NETWORK" }
    data class Unknown(override val message: String) : VtopError()
}
