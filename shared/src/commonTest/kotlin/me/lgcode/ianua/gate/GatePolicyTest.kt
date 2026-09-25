package me.lgcode.ianua.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GatePolicyTest {
    private var now = 1_000L
    private val policy = GatePolicy(clock = { now })

    @Test
    fun enteringShortsShowsTheGate() {
        assertEquals(GateDecision.SHOW_GATE, policy.onScreen(gated = true))
        assertEquals(GateDecision.NONE, policy.onScreen(gated = true))
    }

    @Test
    fun regularScreensDoNothing() {
        assertEquals(GateDecision.NONE, policy.onScreen(gated = false))
        assertIs<GatePolicy.State.Idle>(policy.state)
    }

    @Test
    fun continueIsLockedDuringTheCountdown() {
        policy.onScreen(gated = true)
        now += GateDefaults.COUNTDOWN_MS - 1
        assertEquals(1, policy.remainingCountdownMs())
        assertFalse(policy.onContinue())
        assertIs<GatePolicy.State.Gating>(policy.state)
        now += 1
        assertTrue(policy.onContinue())
        assertIs<GatePolicy.State.Allowed>(policy.state)
    }

    @Test
    fun allowanceExpiresAndTheGateReturns() {
        allow()
        now += GateDefaults.ALLOWANCE_MS - 1
        assertEquals(GateDecision.NONE, policy.onScreen(gated = true))
        now += 1
        assertEquals(GateDecision.SHOW_GATE, policy.onScreen(gated = true))
    }

    @Test
    fun leavingShortsEndsTheAllowanceAfterTheGracePeriod() {
        allow()
        assertEquals(GateDecision.NONE, policy.onScreen(gated = false))
        now += GateDefaults.LEAVE_GRACE_MS
        assertEquals(GateDecision.NONE, policy.onScreen(gated = false))
        assertIs<GatePolicy.State.Idle>(policy.state)
        assertEquals(GateDecision.SHOW_GATE, policy.onScreen(gated = true))
    }

    @Test
    fun aBriefFlickerDoesNotEndTheAllowance() {
        allow()
        now += 1_000
        policy.onScreen(gated = false)
        now += 1_000
        assertEquals(GateDecision.NONE, policy.onScreen(gated = true))
        assertIs<GatePolicy.State.Allowed>(policy.state)
    }

    @Test
    fun leavingWhileGatedHidesTheGate() {
        policy.onScreen(gated = true)
        assertEquals(GateDecision.HIDE_GATE, policy.onScreen(gated = false))
        assertIs<GatePolicy.State.Idle>(policy.state)
    }

    @Test
    fun goBackResets() {
        policy.onScreen(gated = true)
        policy.onGoBack()
        assertIs<GatePolicy.State.Idle>(policy.state)
        assertEquals(0, policy.remainingCountdownMs())
    }

    private fun allow() {
        policy.onScreen(gated = true)
        now += GateDefaults.COUNTDOWN_MS
        assertTrue(policy.onContinue())
    }
}
