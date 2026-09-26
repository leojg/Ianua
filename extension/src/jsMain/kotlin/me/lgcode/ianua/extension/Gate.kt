package me.lgcode.ianua.extension

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.p
import me.lgcode.ianua.gate.GateCopy
import me.lgcode.ianua.gate.GatePolicy
import me.lgcode.ianua.rules.RuleRepository
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.url.URLSearchParams
import kotlin.js.Date

private const val FALLBACK_URL = "https://www.youtube.com/"

/** gate.html?v=<id>: the door. *Continue* unlocks after the countdown and opens the regular player. */
fun gateMain() {
    launch {
        val params = URLSearchParams(window.location.search)
        val id = params.get("v")
        val state = loadState()
        // `s` names the gate pack; gate URLs from v0.1 have none and were always YouTube.
        val matcher = state.packs.web[params.get("s") ?: RuleRepository.YOUTUBE]
        if (!isSafeVideoId(id) || matcher == null) {
            window.location.replace(FALLBACK_URL)
            return@launch
        }
        val destination = matcher.continueUrl(id!!)
        if (!state.enabled) {
            // Reached through history after Ianua was switched off.
            window.location.replace(destination)
            return@launch
        }
        render(GatePolicy(clock = { Date.now().toLong() }).apply { startGate() }, destination)
    }
}

private fun render(policy: GatePolicy, destination: String) {
    document.title = "Ianua"
    document.body!!.append {
        div("gate") {
            div("door") { +"🚪" }
            h1 { +GateCopy.TITLE }
            p { +GateCopy.MESSAGE }
            div("actions") {
                button(type = ButtonType.button, classes = "primary") {
                    id = "back"
                    +GateCopy.GO_BACK
                    onClickFunction = {
                        policy.onGoBack()
                        if (window.history.length > 1) window.history.back() else window.location.replace(FALLBACK_URL)
                    }
                }
                button(type = ButtonType.button, classes = "secondary") {
                    id = "continue"
                    disabled = true
                    onClickFunction = {
                        if (policy.onContinue()) window.location.replace(destination)
                    }
                }
            }
        }
    }
    val back = document.getElementById("back") as HTMLButtonElement
    val proceed = document.getElementById("continue") as HTMLButtonElement
    back.focus()

    var timer = 0
    fun tick() {
        if (policy.canContinue()) {
            proceed.disabled = false
            proceed.textContent = GateCopy.CONTINUE
            window.clearInterval(timer)
        } else {
            val seconds = (policy.remainingCountdownMs() + 999) / 1000
            proceed.textContent = GateCopy.waiting(seconds)
        }
    }
    tick()
    timer = window.setInterval({ tick() }, 200)
}
