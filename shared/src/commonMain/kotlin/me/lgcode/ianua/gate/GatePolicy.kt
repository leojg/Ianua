package me.lgcode.ianua.gate

/** v0.1 is not configurable: these are the only values. */
object GateDefaults {
    const val COUNTDOWN_MS = 10_000L
    const val ALLOWANCE_MS = 5 * 60_000L

    /** How long a gated screen must stay out of view before an allowance ends early. */
    const val LEAVE_GRACE_MS = 3_000L
}

object GateCopy {
    const val TITLE = "You're about to enter Shorts"
    const val MESSAGE = "Take a breath. Is this what you came here for?"
    const val GO_BACK = "Go back"
    const val CONTINUE = "Continue"
    fun waiting(seconds: Long) = "Continue in $seconds s"
}

enum class GateDecision { SHOW_GATE, HIDE_GATE, NONE }

/**
 * The door. `Idle → Gating → Allowed(until) → Idle`.
 *
 * The web uses only the countdown: *Continue* leaves the Shorts player entirely. Android
 * feeds every screen change through [onScreen] and gets the allowance too.
 */
class GatePolicy(
    private val clock: () -> Long,
    private val countdownMs: Long = GateDefaults.COUNTDOWN_MS,
    private val allowanceMs: Long = GateDefaults.ALLOWANCE_MS,
    private val leaveGraceMs: Long = GateDefaults.LEAVE_GRACE_MS,
) {
    sealed interface State {
        data object Idle : State
        data class Gating(val since: Long) : State
        data class Allowed(val until: Long, val lastSeenGated: Long) : State
    }

    var state: State = State.Idle
        private set

    fun onScreen(gated: Boolean): GateDecision {
        val now = clock()
        return when (val s = state) {
            State.Idle -> if (gated) {
                state = State.Gating(now)
                GateDecision.SHOW_GATE
            } else {
                GateDecision.NONE
            }

            is State.Gating -> if (gated) {
                GateDecision.NONE
            } else {
                // The user left on their own, e.g. with the system back gesture.
                state = State.Idle
                GateDecision.HIDE_GATE
            }

            is State.Allowed -> when {
                gated && now >= s.until -> {
                    state = State.Gating(now)
                    GateDecision.SHOW_GATE
                }
                gated -> {
                    state = s.copy(lastSeenGated = now)
                    GateDecision.NONE
                }
                now - s.lastSeenGated >= leaveGraceMs -> {
                    state = State.Idle
                    GateDecision.NONE
                }
                else -> GateDecision.NONE
            }
        }
    }

    /** Starts gating unconditionally, e.g. when the web gate page opens. */
    fun startGate() {
        state = State.Gating(clock())
    }

    fun remainingCountdownMs(): Long {
        val s = state as? State.Gating ?: return 0
        return (s.since + countdownMs - clock()).coerceAtLeast(0)
    }

    fun canContinue(): Boolean = state is State.Gating && remainingCountdownMs() == 0L

    /** Returns false (and changes nothing) while the countdown is still running. */
    fun onContinue(): Boolean {
        if (!canContinue()) return false
        val now = clock()
        state = State.Allowed(until = now + allowanceMs, lastSeenGated = now)
        return true
    }

    fun onGoBack() {
        state = State.Idle
    }
}
