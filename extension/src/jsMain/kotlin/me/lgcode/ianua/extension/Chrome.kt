@file:Suppress("unused")

package me.lgcode.ianua.extension

import kotlin.js.Promise

// Hand-written bindings for the few chrome.* APIs Ianua uses (ADR-0002). Promise-returning
// forms are the MV3 ones. Add members here as they become needed; keep them minimal.

external val chrome: Chrome

external interface Chrome {
    val runtime: Runtime
    val storage: Storage
    val tabs: Tabs
    val webNavigation: WebNavigation
    val alarms: Alarms
    val declarativeNetRequest: DeclarativeNetRequest
    val permissions: Permissions
}

external interface Permissions {
    fun contains(permissions: dynamic): kotlin.js.Promise<Boolean>
}

external interface ChromeEvent<T> {
    fun addListener(callback: T)
}

external interface Runtime {
    val id: String
    fun getURL(path: String): String
    fun getManifest(): dynamic
    val onInstalled: ChromeEvent<(details: dynamic) -> Unit>
    val onStartup: ChromeEvent<() -> Unit>
    val onMessage: ChromeEvent<(message: dynamic, sender: dynamic, sendResponse: (dynamic) -> Unit) -> Any?>
}

external interface Storage {
    val local: StorageArea
    val onChanged: ChromeEvent<(changes: dynamic, areaName: String) -> Unit>
}

external interface StorageArea {
    fun get(keys: Array<String>): Promise<dynamic>
    fun set(items: dynamic): Promise<Unit>
}

external interface Tabs {
    fun update(tabId: Int, updateProperties: dynamic): Promise<dynamic>
    fun sendMessage(tabId: Int, message: dynamic): Promise<dynamic>
}

external interface WebNavigation {
    val onHistoryStateUpdated: ChromeEvent<(details: NavigationDetails) -> Unit>
}

external interface NavigationDetails {
    val tabId: Int
    val frameId: Int
    val url: String
}

external interface Alarms {
    fun create(name: String, alarmInfo: dynamic)
    fun get(name: String): Promise<dynamic>
    val onAlarm: ChromeEvent<(alarm: dynamic) -> Unit>
}

external interface DeclarativeNetRequest {
    fun getDynamicRules(): Promise<Array<dynamic>>
    fun updateDynamicRules(options: dynamic): Promise<Unit>
}

/** A JS object literal, for the `dynamic` parameters above. */
fun jso(build: dynamic.() -> Unit): dynamic {
    val o: dynamic = js("({})")
    build(o)
    return o
}
