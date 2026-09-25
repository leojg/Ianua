package me.lgcode.ianua.extension

import kotlinx.browser.document
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.js.onChangeFunction
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.span
import org.w3c.dom.HTMLInputElement

/** popup.html: the v0.1 on/off switch. */
fun popupMain() {
    launch {
        val state = loadState()
        document.body!!.append {
            div("popup") {
                h1 { +"Ianua" }
                label("switch") {
                    input(type = InputType.checkBox) {
                        id = "enabled"
                        checked = state.enabled
                        onChangeFunction = { event ->
                            val checked = (event.target as HTMLInputElement).checked
                            launch { setEnabled(checked) }
                        }
                    }
                    span { +"Gate YouTube Shorts" }
                }
                p("meta") { +"Rules v${state.pack.version}" }
            }
        }
    }
}
