package me.lgcode.ianua.rules

/** What Ianua does with a screen, URL or app. */
sealed interface Verdict {
    data object None : Verdict
    /** The Shorts door: prompt, countdown, allowance. */
    data object Gate : Verdict
    /** Hard block (ADR-0006). */
    data class Block(val platform: BlockPlatform) : Verdict
}

/**
 * All active rule packs, combined. Each platform module asks this one object whether to gate,
 * block, or do nothing.
 *
 * Gate beats block: a `block` entry naming a domain or package that a gate pack covers is
 * dropped, so no rule update can hard-block YouTube (ADR-0001, ADR-0006).
 */
class Packs(val all: List<RulePack>) {
    /** Gate packs with web rules, by pack id. */
    val web: Map<String, WebMatcher> = all.mapNotNull { p -> p.web?.let { p.id to WebMatcher(it) } }.toMap()

    private val android: List<AndroidMatcher> =
        all.mapNotNull { p -> p.android?.let { AndroidMatcher(it, p.web?.let(::WebMatcher)) } }

    private val gatedHosts: Set<String> = all.flatMap { it.web?.hosts.orEmpty() }.toSet()
    private val gatedPackages: Set<String> = all.flatMap { it.android?.packages.orEmpty() }.toSet()

    /** Address bars of every browser any pack lists. */
    private val browsers: Map<String, List<String>> = all
        .flatMap { it.android?.browsers.orEmpty() }
        .groupBy({ it.packageName }, { it.urlBarViewIds })
        .mapValues { (_, ids) -> ids.flatten().distinct() }

    val block = BlockMatcher(
        all.flatMap { it.block?.platforms.orEmpty() }.map { p ->
            p.copy(
                domains = p.domains.filterNot { d -> gatedHosts.any { it == d || it.endsWith(".$d") } },
                androidPackages = p.androidPackages.filterNot { it in gatedPackages || it in browsers },
            )
        }.filter { it.domains.isNotEmpty() || it.androidPackages.isNotEmpty() },
    )

    /** Address-bar view ids, for debug dumps (the only text they keep). */
    val urlBarIds: Set<String> = browsers.values.flatten().toSet()

    /** Every package the Android service must receive events from. */
    val androidPackages: Set<String> = android.flatMap { it.packages }.toSet() + browsers.keys + block.packages

    fun version(): String = all.sortedBy { it.id }.joinToString(" · ") { "${it.id} v${it.version}" }

    // --- web ---

    data class GatedVideo(val packId: String, val videoId: String)

    fun gatedVideo(url: String): GatedVideo? =
        web.firstNotNullOfOrNull { (id, m) -> m.gatedVideoId(url)?.let { GatedVideo(id, it) } }

    fun hideCss(): String = web.values.joinToString("\n") { it.hideCss() }.trim()

    // --- android ---

    /** Blocked apps are decided from the package alone, before any screen content is read. */
    fun isBlockedPackage(packageName: String): BlockPlatform? = block.platformForPackage(packageName)

    fun verdict(packageName: String, root: ScreenNode): Verdict {
        block.platformForPackage(packageName)?.let { return Verdict.Block(it) }
        browsers[packageName]?.let { urlBars ->
            val url = addressBarText(root, urlBars)
            url?.let(block::platformForAddressBar)?.let { return Verdict.Block(it) }
        }
        return if (android.any { it.isGatedScreen(packageName, root) }) Verdict.Gate else Verdict.None
    }
}
