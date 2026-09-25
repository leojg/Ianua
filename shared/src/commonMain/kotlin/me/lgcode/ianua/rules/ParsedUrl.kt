package me.lgcode.ianua.rules

/** Minimal URL split, enough for matching; common code has no java.net. */
internal data class ParsedUrl(val scheme: String, val host: String, val path: String) {
    companion object {
        fun parse(url: String): ParsedUrl? {
            val schemeEnd = url.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = url.substring(0, schemeEnd).lowercase()
            val rest = url.substring(schemeEnd + 3)
            val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, authorityEnd).substringAfterLast('@')
            val host = authority.substringBefore(':').lowercase()
            if (host.isEmpty()) return null
            val afterAuthority = rest.substring(authorityEnd)
            val path = afterAuthority.substringBefore('?').substringBefore('#').ifEmpty { "/" }
            return ParsedUrl(scheme, host, path)
        }
    }
}
