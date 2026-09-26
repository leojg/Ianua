package me.lgcode.ianua.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.lgcode.ianua.gate.GateDecision
import me.lgcode.ianua.gate.GateDefaults
import me.lgcode.ianua.gate.GatePolicy
import me.lgcode.ianua.ianua
import me.lgcode.ianua.rules.BlockPlatform
import me.lgcode.ianua.rules.Packs
import me.lgcode.ianua.rules.Verdict
import me.lgcode.ianua.ui.BlockScreen
import me.lgcode.ianua.ui.GateScreen

/**
 * Puts the door in front of Shorts in the gated apps (ADR-0004) and listed browsers (ADR-0005),
 * and hard-blocks whole-app short-video platforms (ADR-0006). Screen content is only
 * evaluated in memory; nothing is stored or sent. Blocked apps are recognised by package name
 * alone, without reading their screen.
 */
class IanuaAccessibilityService : AccessibilityService() {
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private val policy = GatePolicy(clock = SystemClock::elapsedRealtime)
    private val evaluateRunnable = Runnable { evaluate() }
    private val dismissBlockRunnable = Runnable { dismissBlock() }

    private var packs: Packs? = null
    private var enabled = true
    private var gate: ComposeOverlay? = null
    private var blockCard: ComposeOverlay? = null
    private val audio by lazy { GateAudio(this) }

    // Escalation when browser back does not leave a blocked page.
    private var lastBrowserBlockAt = 0L
    private var browserBlockStreak = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = ianua
        scope.launch {
            app.rules.load()
            combine(app.rules.current, app.settings.enabled) { p, on -> p to on }.collect { (p, on) ->
                packs = p
                enabled = on
                narrowToWatchedPackages()
                if (!on) reset()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled) return
        val packs = packs ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in packs.androidPackages) return
        packs.isBlockedPackage(packageName)?.let { platform ->
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) blockApp(platform)
            return
        }
        // Content-changed events arrive in bursts while scrolling; evaluate once per burst.
        handler.removeCallbacks(evaluateRunnable)
        handler.postDelayed(evaluateRunnable, EVALUATE_DELAY_MS)
    }

    private fun evaluate() {
        val packs = packs ?: return
        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return
        val screen = NodeInfoScreenNode(root)
        DebugDump.maybeDump(this, packageName, screen, keepTextOf = packs.urlBarIds)
        val verdict = packs.verdict(packageName, screen)
        if (verdict is Verdict.Block) blockInBrowser(verdict.platform)
        when (policy.onScreen(verdict == Verdict.Gate)) {
            GateDecision.SHOW_GATE -> showGate()
            GateDecision.HIDE_GATE -> hideGate()
            GateDecision.NONE -> Unit
        }
    }

    // --- the Shorts door ---

    private fun showGate() {
        if (gate != null) return
        audio.silence()
        gate = ComposeOverlay(this) {
            GateScreen(remainingMs = policy::remainingCountdownMs, onGoBack = ::goBack, onContinue = ::proceed)
        }.also { it.show() }
    }

    private fun hideGate() {
        gate?.dismiss()
        gate = null
        audio.restore()
    }

    private fun goBack() {
        policy.onGoBack()
        hideGate()
        performGlobalAction(GLOBAL_ACTION_BACK)
        // If back did not leave Shorts, the next event gates again.
        handler.postDelayed(evaluateRunnable, EVALUATE_DELAY_MS * 4)
    }

    private fun proceed() {
        if (!policy.onContinue()) return
        hideGate()
        // A single looping Short may produce no events; re-check when the allowance ends.
        handler.postDelayed(evaluateRunnable, GateDefaults.ALLOWANCE_MS + EVALUATE_DELAY_MS)
    }

    // --- the hard block ---

    private fun blockApp(platform: BlockPlatform) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        showBlockCard(platform)
    }

    private fun blockInBrowser(platform: BlockPlatform) {
        val now = SystemClock.elapsedRealtime()
        browserBlockStreak = if (now - lastBrowserBlockAt < BROWSER_RETRY_WINDOW_MS) browserBlockStreak + 1 else 1
        lastBrowserBlockAt = now
        // Back normally leaves the page; if it keeps coming back (a redirect, a fresh tab), leave the browser.
        performGlobalAction(if (browserBlockStreak > MAX_BROWSER_BACKS) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
        showBlockCard(platform)
    }

    private fun showBlockCard(platform: BlockPlatform) {
        handler.removeCallbacks(dismissBlockRunnable)
        handler.postDelayed(dismissBlockRunnable, BLOCK_CARD_MS)
        if (blockCard?.isShowing == true) return
        blockCard = ComposeOverlay(this) { BlockScreen(platform.name, onOk = ::dismissBlock) }.also { it.show() }
    }

    private fun dismissBlock() {
        handler.removeCallbacks(dismissBlockRunnable)
        blockCard?.dismiss()
        blockCard = null
    }

    private fun reset() {
        policy.onGoBack()
        hideGate()
        dismissBlock()
    }

    /** Receive events only from the packages the current rule packs cover. */
    private fun narrowToWatchedPackages() {
        val packages = packs?.androidPackages ?: return
        serviceInfo = serviceInfo?.apply { packageNames = packages.toTypedArray() } ?: return
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideGate()
        dismissBlock()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val EVALUATE_DELAY_MS = 120L
        const val BLOCK_CARD_MS = 4_000L
        const val BROWSER_RETRY_WINDOW_MS = 3_000L
        const val MAX_BROWSER_BACKS = 2
    }
}
