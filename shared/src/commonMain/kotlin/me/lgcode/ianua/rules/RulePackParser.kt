package me.lgcode.ianua.rules

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface ParseResult {
    data class Ok(val pack: RulePack) : ParseResult
    data class Invalid(val reason: String) : ParseResult
}

object RulePackParser {
    const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    // Anything that could end our `selector { display: none }` rule early or pull in
    // external resources. Rule packs come from the network, so this is a security boundary.
    private val forbiddenInSelector = listOf("{", "}", ";", "@", "<", "/*", "*/", "url(", "\\")

    fun parse(text: String): ParseResult {
        val pack = try {
            json.decodeFromString(RulePack.serializer(), text)
        } catch (e: SerializationException) {
            return ParseResult.Invalid("malformed: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return ParseResult.Invalid("malformed: ${e.message}")
        }
        return validate(pack)?.let { ParseResult.Invalid(it) } ?: ParseResult.Ok(pack)
    }

    private fun validate(pack: RulePack): String? {
        if (pack.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return "unsupported schemaVersion ${pack.schemaVersion}"
        }
        if (pack.id.isBlank()) return "blank id"
        if (pack.version <= 0) return "version must be positive"
        pack.web?.let { web ->
            if (web.hosts.isEmpty()) return "web.hosts is empty"
            if (web.hosts.any { !it.matches(HOST) }) return "web.hosts contains an invalid host"
            if (web.gatedPaths.isEmpty()) return "web.gatedPaths is empty"
            for (path in web.gatedPaths) {
                try {
                    Regex(path)
                } catch (e: Throwable) {
                    return "web.gatedPaths: invalid regex $path"
                }
                if (captureGroupCount(path) != 1) return "web.gatedPaths: $path needs exactly one capture group"
            }
            if (!web.continueUrl.startsWith("https://") || "{id}" !in web.continueUrl) {
                return "web.continueUrl must be https and contain {id}"
            }
            for (selector in web.hideSelectors) {
                if (selector.isBlank() || forbiddenInSelector.any { it in selector }) {
                    return "web.hideSelectors: rejected selector $selector"
                }
            }
        }
        pack.android?.let { android ->
            if (android.packages.isEmpty()) return "android.packages is empty"
            if (android.gatedScreens.isEmpty()) return "android.gatedScreens is empty"
            if (android.gatedScreens.any { it.anyViewId.isEmpty() && it.anyContentDescription.isEmpty() }) {
                return "android.gatedScreens contains an empty rule"
            }
            if (android.browsers.isNotEmpty() && pack.web == null) {
                return "android.browsers needs web rules to match URLs against"
            }
            if (android.browsers.any { it.packageName.isBlank() || it.urlBarViewIds.isEmpty() }) {
                return "android.browsers needs a package and at least one urlBarViewId each"
            }
        }
        pack.block?.let { block ->
            if (block.platforms.isEmpty()) return "block.platforms is empty"
            for (platform in block.platforms) {
                if (!isValidName(platform.name)) return "block.platforms: invalid name ${platform.name}"
                if (platform.domains.isEmpty() && platform.androidPackages.isEmpty()) {
                    return "block.platforms: ${platform.name} has no domains or packages"
                }
                if (platform.domains.any { !it.matches(HOST) }) return "block.platforms: ${platform.name} has an invalid domain"
                if (platform.androidPackages.any { !it.matches(PACKAGE) }) {
                    return "block.platforms: ${platform.name} has an invalid package"
                }
            }
        }
        return null
    }

    /** Shown on a page and passed in a URL: letters, digits, spaces and a little punctuation. */
    private fun isValidName(name: String): Boolean =
        name.length in 1..40 && name.first().isLetterOrDigit() &&
            name.all { it.isLetterOrDigit() || it in " .'&-" }

    /** Counts capturing groups: `(` not escaped and not followed by `?`. */
    internal fun captureGroupCount(regex: String): Int {
        var count = 0
        var i = 0
        var inClass = false
        while (i < regex.length) {
            when (val c = regex[i]) {
                '\\' -> i++
                '[' -> inClass = true
                ']' -> inClass = false
                '(' -> if (!inClass && regex.getOrNull(i + 1) != '?') count++
                else -> Unit
            }
            i++
        }
        return count
    }

    // At least two labels, so a bare public suffix like "com" can never be blocked.
    private val HOST = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")
    private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
}
