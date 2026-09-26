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
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.url.URLSearchParams

/** blocked.html?p=<name>: hard block for whole-app short-video platforms (ADR-0006). No way through. */
fun blockedMain() {
    val name = URLSearchParams(window.location.search).get("p")
        ?.takeIf { it.length in 1..40 }
        ?: "This site"
    document.title = "Ianua: $name is blocked"
    // kotlinx-html escapes text, so the name from the URL cannot inject markup.
    document.body!!.append {
        div("gate") {
            div("door") { +"⛔" }
            h1 { +"$name is blocked" }
            p { +"Ianua blocks apps and sites that are nothing but short videos." }
            div("actions") {
                button(type = ButtonType.button, classes = "primary") {
                    id = "back"
                    +GateCopy.GO_BACK
                    onClickFunction = { window.history.back() }
                }
            }
        }
    }
    val back = document.getElementById("back") as HTMLButtonElement
    // A blocked site opened in a fresh tab has nowhere to go back to.
    if (window.history.length <= 1) back.remove() else back.focus()
}
