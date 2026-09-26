package me.lgcode.ianua.extension

import me.lgcode.ianua.rules.Packs
import me.lgcode.ianua.rules.RuleRepository

private const val REFRESH_ALARM = "refresh-rules"
private const val REFRESH_PERIOD_MINUTES = 24 * 60

// Dynamic DNR rule id ranges: Shorts redirects, then a redirect + block pair per blocked platform.
private const val GATE_RULE_BASE = 1
private const val BLOCK_RULE_BASE = 1000

/**
 * Service worker. Owns the rule packs (bundled → fetched), mirrors them into storage for the
 * other contexts, and keeps the declarativeNetRequest rules in sync with them and the toggle.
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
        if (area != "local") return@addListener
        val keys = js("Object.keys")(changes).unsafeCast<Array<String>>()
        when {
            // A refresh saved a newer pack: republish everything.
            keys.any { it.startsWith(Keys.FETCHED_PREFIX) } -> launch { publishRules() }
            Keys.ENABLED in keys -> launch { syncDnrRules() }
        }
    }
    // In-page (SPA) navigation never produces a network request for the DNR rule to catch.
    // The content script usually gates first via the Navigation API; this is the fallback.
    chrome.webNavigation.onHistoryStateUpdated.addListener { details ->
        if (details.frameId == 0) launch { gateTabIfNeeded(details.tabId, details.url) }
    }
}

private suspend fun publishRules() {
    for (id in RuleRepository.bundledIds) saveEffectiveRules(repository(id).current())
    syncDnrRules()
}

/** Saving a newer pack triggers [publishRules] through storage.onChanged. */
private suspend fun refreshRules() {
    for (id in RuleRepository.bundledIds) {
        console.info("Ianua: rule refresh $id → ${repository(id).refresh()}")
    }
}

private suspend fun gateTabIfNeeded(tabId: Int, url: String) {
    val state = loadState()
    if (!state.enabled) return
    val video = state.packs.gatedVideo(url) ?: return
    val gate = gateUrl(video)
    try {
        chrome.tabs.sendMessage(tabId, jso { type = "gate"; this.url = gate }).await()
    } catch (e: Throwable) {
        // No content script in the tab (e.g. injected before install): navigate it ourselves.
        chrome.tabs.update(tabId, jso { this.url = gate }).await()
    }
}

fun gateUrl(video: Packs.GatedVideo): String =
    chrome.runtime.getURL("gate.html") + "?v=" + video.videoId + "&s=" + video.packId

fun blockedUrl(platformName: String): String =
    chrome.runtime.getURL("blocked.html") + "?p=" + js("encodeURIComponent")(platformName)

/** Replaces all dynamic DNR rules with those of the current packs, or none when disabled. */
private suspend fun syncDnrRules() {
    val state = loadState()
    val existing = chrome.declarativeNetRequest.getDynamicRules().await().map { it.id as Int }.toTypedArray()
    val rules = if (state.enabled) gateRules(state.packs) + blockRules(state.packs) else emptyList()
    chrome.declarativeNetRequest.updateDynamicRules(jso { removeRuleIds = existing; addRules = rules.toTypedArray() }).await()
}

private fun gateRules(packs: Packs): List<dynamic> {
    var next = GATE_RULE_BASE
    return packs.web.flatMap { (packId, matcher) ->
        matcher.navigationRegexFilters().map { filter ->
            jso {
                id = next++
                priority = 1
                action = jso {
                    type = "redirect"
                    redirect = jso { regexSubstitution = chrome.runtime.getURL("gate.html") + "?v=\\1&s=" + packId }
                }
                condition = jso {
                    regexFilter = filter
                    resourceTypes = arrayOf("main_frame")
                }
            }
        }
    }
}

/**
 * Per platform (ADR-0006): a plain block, which needs no host permission, and above it a
 * redirect to blocked.html for the domains the manifest grants host permission for. A
 * redirect without permission is not applied but still wins the match, so it would shadow
 * the block and let the site load; hence the permission check.
 */
private suspend fun blockRules(packs: Packs): List<dynamic> {
    val rules = mutableListOf<dynamic>()
    packs.block.platforms.filter { it.domains.isNotEmpty() }.forEachIndexed { index, platform ->
        val permitted = platform.domains.filter { hasHostPermission(it) }
        if (permitted.isNotEmpty()) {
            rules.add(jso {
                id = BLOCK_RULE_BASE + 2 * index
                priority = 2
                action = jso { type = "redirect"; redirect = jso { url = blockedUrl(platform.name) } }
                condition = jso { requestDomains = permitted.toTypedArray(); resourceTypes = arrayOf("main_frame") }
            })
        }
        rules.add(jso {
            id = BLOCK_RULE_BASE + 2 * index + 1
            priority = 1
            action = jso { type = "block" }
            condition = jso { requestDomains = platform.domains.toTypedArray(); resourceTypes = arrayOf("main_frame", "sub_frame") }
        })
    }
    return rules
}

private suspend fun hasHostPermission(domain: String): Boolean {
    val granted = chrome.permissions.contains(jso { origins = arrayOf("*://*.$domain/*") }).await()
    return granted
}
