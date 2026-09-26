package me.lgcode.ianua.extension

import kotlinx.serialization.json.Json
import me.lgcode.ianua.rules.Packs
import me.lgcode.ianua.rules.ParseResult
import me.lgcode.ianua.rules.RuleFetcher
import me.lgcode.ianua.rules.RulePack
import me.lgcode.ianua.rules.RulePackParser
import me.lgcode.ianua.rules.RuleRepository
import me.lgcode.ianua.rules.RuleStore

/** chrome.storage.local keys. The background writes, every other context reads. */
object Keys {
    const val ENABLED = "enabled"
    /** Last fetched text of a pack, written by [RuleRepository.refresh]. */
    const val FETCHED_PREFIX = "rules."
    fun fetched(id: String) = "$FETCHED_PREFIX$id"
    /** The pack in effect (bundled or fetched), re-serialized, for contexts without a repository. */
    fun effective(id: String) = "effective.$id"
}

data class ExtensionState(val enabled: Boolean, val packs: Packs)

private val json = Json { encodeDefaults = true }

suspend fun loadState(): ExtensionState {
    val ids = RuleRepository.bundledIds
    val items = chrome.storage.local.get((ids.map(Keys::effective) + Keys.ENABLED).toTypedArray()).await()
    val enabled = items[Keys.ENABLED] as? Boolean ?: true
    val packs = ids.map { id ->
        (items[Keys.effective(id)] as? String)?.let { RulePackParser.parse(it) as? ParseResult.Ok }?.pack
            ?: repository(id).bundled
    }
    return ExtensionState(enabled, Packs(packs))
}

suspend fun saveEffectiveRules(pack: RulePack) {
    chrome.storage.local.set(jso { this[Keys.effective(pack.id)] = json.encodeToString(RulePack.serializer(), pack) }).await()
}

suspend fun setEnabled(enabled: Boolean) {
    chrome.storage.local.set(jso { this[Keys.ENABLED] = enabled }).await()
}

fun repository(id: String) = RuleRepository(id, ChromeRuleStore, FetchRuleFetcher)

private object ChromeRuleStore : RuleStore {
    override suspend fun load(id: String): String? {
        // Assign before indexing: see the compiler pitfall on `await`.
        val items = chrome.storage.local.get(arrayOf(Keys.fetched(id))).await()
        return items[Keys.fetched(id)] as? String
    }

    override suspend fun save(id: String, text: String) {
        chrome.storage.local.set(jso { this[Keys.fetched(id)] = text }).await()
    }
}

private object FetchRuleFetcher : RuleFetcher {
    override suspend fun fetch(url: String): String? = try {
        // `fetch` exists in the service worker too, where `window` does not.
        val fetchFn: dynamic = js("globalThis.fetch")
        val response: dynamic = (fetchFn(url, jso { cache = "no-cache" }) as kotlin.js.Promise<dynamic>).await()
        if (response.ok as Boolean) (response.text() as kotlin.js.Promise<String>).await() else null
    } catch (e: Throwable) {
        null
    }
}

/** Video ids are interpolated into URLs; accept only YouTube's id alphabet. */
fun isSafeVideoId(id: String?): Boolean = id != null && id.length in 5..64 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
