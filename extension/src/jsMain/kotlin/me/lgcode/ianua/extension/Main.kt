package me.lgcode.ianua.extension

import kotlinx.browser.window

/** Every extension context loads the same bundle; pick the entry point for this one. */
fun main() {
    val inServiceWorker = js("typeof ServiceWorkerGlobalScope !== 'undefined' && self instanceof ServiceWorkerGlobalScope") as Boolean
    if (inServiceWorker) return backgroundMain()
    val location = window.location
    when {
        location.protocol != "chrome-extension:" -> contentMain()
        location.pathname.endsWith("/gate.html") -> gateMain()
        location.pathname.endsWith("/popup.html") -> popupMain()
    }
}
