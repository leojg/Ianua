package me.lgcode.ianua.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.lgcode.ianua.gate.GatePolicy
import me.lgcode.ianua.ui.GateScreen
import me.lgcode.ianua.ui.IanuaTheme

/**
 * Full-screen gate drawn by the accessibility service. TYPE_ACCESSIBILITY_OVERLAY needs no
 * SYSTEM_ALERT_WINDOW permission. Not focusable, so the gated app keeps the active window and
 * the service keeps seeing its screen underneath.
 */
class GateOverlay(
    private val service: AccessibilityService,
    private val policy: GatePolicy,
    private val onGoBack: () -> Unit,
    private val onContinue: () -> Unit,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val owner = OverlayOwner()
    private var view: ComposeView? = null

    fun show() {
        owner.start()
        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                IanuaTheme {
                    GateScreen(
                        remainingMs = policy::remainingCountdownMs,
                        onGoBack = onGoBack,
                        onContinue = onContinue,
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        windowManager.addView(composeView, params)
        view = composeView
    }

    fun dismiss() {
        view?.let(windowManager::removeView)
        view = null
        owner.stop()
    }

    /** Compose needs a lifecycle and saved-state owner; a service window has neither. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
