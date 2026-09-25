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
        }
        return null
    }

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

    private val HOST = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")
}
