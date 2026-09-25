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
import me.lgcode.ianua.rules.AndroidMatcher
import me.lgcode.ianua.rules.WebMatcher

/**
 * Watches the gated apps' screens (ADR-0004) and listed browsers' address bars (ADR-0005),
 * and puts the gate in front of Shorts. Screen content is only evaluated in memory; nothing
 * is stored or sent.
 */
class IanuaAccessibilityService : AccessibilityService() {
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private val policy = GatePolicy(clock = SystemClock::elapsedRealtime)
    private val evaluateRunnable = Runnable { evaluate() }

    private var matcher: AndroidMatcher? = null
    private var urlBarIds: Set<String> = emptySet()
    private var enabled = true
    private var overlay: GateOverlay? = null
    private val audio by lazy { GateAudio(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = ianua
        scope.launch {
            app.rules.load()
            combine(app.rules.current, app.settings.enabled) { pack, on -> pack to on }.collect { (pack, on) ->
                matcher = pack.android?.let { AndroidMatcher(it, pack.web?.let(::WebMatcher)) }
                urlBarIds = pack.android?.browsers.orEmpty().flatMap { it.urlBarViewIds }.toSet()
                enabled = on
                narrowToGatedPackages()
                if (!on) reset()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled) return
        val packages = matcher?.packages ?: return
        if (event.packageName?.toString() !in packages) return
        // Content-changed events arrive in bursts while scrolling; evaluate once per burst.
        handler.removeCallbacks(evaluateRunnable)
        handler.postDelayed(evaluateRunnable, EVALUATE_DELAY_MS)
    }

    private fun evaluate() {
        val matcher = matcher ?: return
        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return
        val screen = NodeInfoScreenNode(root)
        DebugDump.maybeDump(this, packageName, screen, keepTextOf = urlBarIds)
        when (policy.onScreen(matcher.isGatedScreen(packageName, screen))) {
            GateDecision.SHOW_GATE -> showGate()
            GateDecision.HIDE_GATE -> hideGate()
            GateDecision.NONE -> Unit
        }
    }

    private fun showGate() {
        if (overlay != null) return
        audio.silence()
        overlay = GateOverlay(this, policy, onGoBack = ::goBack, onContinue = ::proceed).also { it.show() }
    }

    private fun hideGate() {
        overlay?.dismiss()
        overlay = null
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

    private fun reset() {
        policy.onGoBack()
        hideGate()
    }

    /** Receive events only from the packages the current rule pack gates. */
    private fun narrowToGatedPackages() {
        val packages = matcher?.packages ?: return
        serviceInfo = serviceInfo?.apply { packageNames = packages.toTypedArray() } ?: return
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideGate()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val EVALUATE_DELAY_MS = 120L
    }
}
