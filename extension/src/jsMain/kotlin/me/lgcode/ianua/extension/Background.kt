package me.lgcode.ianua.extension

import me.lgcode.ianua.rules.RefreshResult

private const val REFRESH_ALARM = "refresh-rules"
private const val REFRESH_PERIOD_MINUTES = 24 * 60

/**
 * Service worker. Owns the rule pack (bundled → fetched), mirrors it into storage for the other
 * contexts, and keeps the declarativeNetRequest redirect in sync with it and the toggle.
 * Listeners are registered synchronously, as MV3 requires.
 */
fun backgroundMain() {
    chrome.runtime.onInstalled.addListener {
        launch {
            chrome.alarms.create(REFRESH_ALARM, jso { periodInMinutes = REFRESH_PERIOD_MINUTES; delayInMinutes = 1 })
            publishRules()
        }
    }
    chrome.runtime.onStartup.addListener { launch { publishRules() } }
    chrome.alarms.onAlarm.addListener { alarm ->
        if (alarm.name == REFRESH_ALARM) launch { refreshRules() }
    }
    chrome.storage.onChanged.addListener { changes, area ->
        if (area == "local" && changes[Keys.ENABLED] != undefined) launch { syncRedirects() }
    }
    // In-page (SPA) navigation never produces a network request for the DNR rule to catch.
    // The content script usually gates first via the Navigation API; this is the fallback.
    chrome.webNavigation.onHistoryStateUpdated.addListener { details ->
        if (details.frameId == 0) launch { gateTabIfNeeded(details.tabId, details.url) }
    }
}

private suspend fun publishRules() {
    saveEffectiveRules(youtubeRepository().current())
    syncRedirects()
}

private suspend fun refreshRules() {
    val result = youtubeRepository().refresh()
    console.info("Ianua: rule refresh → $result")
    if (result is RefreshResult.Updated) publishRules()
}

private suspend fun gateTabIfNeeded(tabId: Int, url: String) {
    val state = loadState()
    if (!state.enabled) return
    val id = state.matcher?.gatedVideoId(url) ?: return
    val gate = gateUrl(id)
    try {
        chrome.tabs.sendMessage(tabId, jso { type = "gate"; this.url = gate }).await()
    } catch (e: Throwable) {
        // No content script in the tab (e.g. injected before install): navigate it ourselves.
        chrome.tabs.update(tabId, jso { this.url = gate }).await()
    }
}

fun gateUrl(videoId: String): String = chrome.runtime.getURL("gate.html") + "?v=" + videoId

/** Replaces all dynamic DNR rules with the current pack's redirects, or none when disabled. */
private suspend fun syncRedirects() {
    val state = loadState()
    val existing = chrome.declarativeNetRequest.getDynamicRules().await().map { it.id as Int }.toTypedArray()
    val filters = if (state.enabled) state.matcher?.navigationRegexFilters().orEmpty() else emptyList()
    val rules = filters.mapIndexed { index, filter ->
        jso {
            id = index + 1
            priority = 1
            action = jso {
                type = "redirect"
                redirect = jso { regexSubstitution = chrome.runtime.getURL("gate.html") + "?v=\\1" }
            }
            condition = jso {
                regexFilter = filter
                resourceTypes = arrayOf("main_frame")
            }
        }
    }.toTypedArray()
    chrome.declarativeNetRequest.updateDynamicRules(jso { removeRuleIds = existing; addRules = rules }).await()
}
