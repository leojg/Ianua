package me.lgcode.ianua.rules

class WebMatcher(private val rules: WebRules) {
    private val paths = rules.gatedPaths.map(::Regex)

    fun matchesHost(url: String): Boolean = ParsedUrl.parse(url)?.host in rules.hosts

    /** The video id if [url] opens a gated page (e.g. a Short), else null. */
    fun gatedVideoId(url: String): String? {
        val parsed = ParsedUrl.parse(url) ?: return null
        if (parsed.scheme != "https" && parsed.scheme != "http") return null
        if (parsed.host !in rules.hosts) return null
        return paths.firstNotNullOfOrNull { it.find(parsed.path)?.groupValues?.get(1) }
    }

    /**
     * Like [gatedVideoId], for the text of a mobile browser's address bar, which usually
     * omits the scheme (`m.youtube.com/shorts/…`).
     */
    fun gatedVideoIdInAddressBar(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        return gatedVideoId(if ("://" in trimmed) trimmed else "https://$trimmed")
    }

    fun continueUrl(videoId: String): String = rules.continueUrl.replace("{id}", videoId)

    /**
     * One rule per selector: an unsupported selector then invalidates only its own rule,
     * not the whole stylesheet.
     */
    fun hideCss(): String = rules.hideSelectors.joinToString("\n") { "$it { display: none !important; }" }

    /**
     * RE2 regexes over the full URL for declarativeNetRequest, one per gated path. The host
     * group is non-capturing so the video id stays `\1` for `regexSubstitution`, and the
     * trailing `.*` consumes the rest: DNR replaces only the matched part, so an unmatched
     * `?feature=share` would otherwise leak into the gate URL.
     */
    fun navigationRegexFilters(): List<String> {
        val hosts = rules.hosts.joinToString("|") { it.replace(".", "\\.") }
        return rules.gatedPaths.map { path ->
            "^https?://(?:$hosts)${path.removePrefix("^").removeSuffix("$")}.*"
        }
    }
}
