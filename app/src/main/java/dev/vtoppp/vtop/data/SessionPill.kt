package dev.vtoppp.vtop.data

/** Pill color class; the screen maps these to theme containers. */
enum class PillKind { Ready, Busy, Error }

data class PillState(
    val kind: PillKind,
    val label: String,
    /** Busy pills pulse; Ready/Error stay static. */
    val pulse: Boolean = false,
)

/**
 * Maps the client's warm-up narration to the top-bar status pill. VtopClient
 * drives `_warm` through every phase (prelogin, captcha solve, signing,
 * re-auth after expiry), so this single mapping covers all flows.
 */
fun pillOf(warm: WarmState): PillState = pillOf(warm, hasSession = true)

/**
 * T18.3 invariant: "session ready" must mean an actual live session —
 * WarmState.Idle after a clearWarm can also mean a dead one, so the pill must
 * consult [hasSession] before claiming readiness. The default keeps the
 * old single-argument call sites legible in tests.
 */
fun pillOf(warm: WarmState, hasSession: Boolean): PillState = when (warm) {
    is WarmState.Failed -> PillState(PillKind.Error, warm.message)
    is WarmState.Working -> PillState(PillKind.Busy, warm.step, pulse = true)
    // Idle/CaptchaReady settle states: readiness is decided by hasSession.
    else -> if (hasSession) {
        PillState(PillKind.Ready, "VTOP session ready")
    } else {
        PillState(PillKind.Busy, "VTOP session connecting…", pulse = true)
    }
}
