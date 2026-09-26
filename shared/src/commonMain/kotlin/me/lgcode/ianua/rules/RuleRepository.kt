package me.lgcode.ianua.rules

import me.lgcode.ianua.generated.BundledRules

/** Persists the last fetched pack text per site id. */
interface RuleStore {
    suspend fun load(id: String): String?
    suspend fun save(id: String, text: String)
}

/** Returns the response body, or null on any failure. */
fun interface RuleFetcher {
    suspend fun fetch(url: String): String?
}

sealed interface RefreshResult {
    data class Updated(val pack: RulePack) : RefreshResult
    data object UpToDate : RefreshResult
    data object FetchFailed : RefreshResult
    data class Rejected(val reason: String) : RefreshResult
}

/**
 * Bundled pack → last-known-good fetched pack → remote refresh (ADR-0003). A fetched pack
 * is used only if it is valid and strictly newer than what we already have.
 */
class RuleRepository(
    private val id: String,
    private val store: RuleStore,
    private val fetcher: RuleFetcher,
    private val bundledText: String = BundledRules.all.getValue(id),
) {
    val bundled: RulePack = (RulePackParser.parse(bundledText) as? ParseResult.Ok)?.pack
        ?: error("bundled rule pack '$id' is invalid")

    suspend fun current(): RulePack {
        val cached = store.load(id)?.let { RulePackParser.parse(it) as? ParseResult.Ok }?.pack
        return if (cached != null && cached.id == id && cached.version > bundled.version) cached else bundled
    }

    suspend fun refresh(): RefreshResult {
        val text = fetcher.fetch(remoteUrl(id)) ?: return RefreshResult.FetchFailed
        val fetched = when (val parsed = RulePackParser.parse(text)) {
            is ParseResult.Invalid -> return RefreshResult.Rejected(parsed.reason)
            is ParseResult.Ok -> parsed.pack
        }
        if (fetched.id != id) return RefreshResult.Rejected("id ${fetched.id} != $id")
        if (fetched.version <= current().version) return RefreshResult.UpToDate
        store.save(id, text)
        return RefreshResult.Updated(fetched)
    }

    companion object {
        const val YOUTUBE = "youtube"

        /** Every pack shipped in the build: one per file in `rules/` (ADR-0006). */
        val bundledIds: List<String> get() = BundledRules.all.keys.sorted()
        fun remoteUrl(id: String) = "https://raw.githubusercontent.com/leojg/Ianua/master/rules/$id.json"
    }
}
