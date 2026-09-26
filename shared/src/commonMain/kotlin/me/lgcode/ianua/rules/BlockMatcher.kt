package me.lgcode.ianua.rules

/** Which blocked platform, if any, a URL, address bar or Android package belongs to (ADR-0006). */
class BlockMatcher(val platforms: List<BlockPlatform>) {
    private val byPackage = platforms.flatMap { p -> p.androidPackages.map { it to p } }.toMap()

    val packages: Set<String> get() = byPackage.keys

    fun platformForHost(host: String): BlockPlatform? {
        val h = host.lowercase().trimEnd('.')
        return platforms.firstOrNull { p -> p.domains.any { h == it || h.endsWith(".$it") } }
    }

    fun platformForUrl(url: String): BlockPlatform? {
        val parsed = ParsedUrl.parse(url) ?: return null
        if (parsed.scheme != "https" && parsed.scheme != "http") return null
        return platformForHost(parsed.host)
    }

    fun platformForAddressBar(text: String): BlockPlatform? = addressBarUrl(text)?.let(::platformForUrl)

    fun platformForPackage(packageName: String): BlockPlatform? = byPackage[packageName]
}
