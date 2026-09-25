package me.lgcode.ianua.extension

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLStyleElement

/**
 * Content script on YouTube, at document_start. Hides Shorts entry points and sends any
 * navigation to a Short through the gate. Hard loads are normally redirected by the DNR rule
 * before this runs; the checks here cover in-page navigation and anything the rule missed.
 */
fun contentMain() {
    val style = document.createElement("style") as HTMLStyleElement
    style.id = "ianua-hide"
    (document.head ?: document.documentElement)?.appendChild(style)

    var state: ExtensionState? = null
    var gating = false

    fun gate(url: String, cancel: () -> Boolean = { false }) {
        val s = state ?: return
        if (!s.enabled || gating) return
        val id = s.matcher?.gatedVideoId(url) ?: return
        if (!isSafeVideoId(id)) return
        gating = true
        // A cancelled navigation leaves us on the previous page: add the gate after it, so
        // *Go back* returns there. Otherwise we are on the Short: replace it.
        if (cancel()) window.location.assign(gateUrl(id)) else window.location.replace(gateUrl(id))
    }

    fun apply(newState: ExtensionState) {
        state = newState
        style.textContent = if (newState.enabled) newState.matcher?.hideCss().orEmpty() else ""
        gate(window.location.href)
    }

    launch { apply(loadState()) }
    chrome.storage.onChanged.addListener { _, area ->
        if (area == "local") launch { apply(loadState()) }
    }
    chrome.runtime.onMessage.addListener { message, _, _ ->
        if (message.type == "gate") {
            val s = state
            if (s != null && s.enabled && !gating) {
                gating = true
                window.location.replace(message.url as String)
            }
        }
        null
    }

    // The Navigation API sees YouTube's pushState navigations before they commit.
    val navigation: dynamic = window.asDynamic().navigation
    if (navigation != null && navigation != undefined) {
        navigation.addEventListener("navigate", { event: dynamic ->
            val url = event.destination?.url as? String
            if (url != null) {
                gate(url) {
                    if (event.cancelable == true) {
                        event.preventDefault()
                        true
                    } else {
                        false
                    }
                }
            }
        })
    }
}
