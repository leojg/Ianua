package me.lgcode.ianua.rules

import kotlinx.serialization.Serializable

/**
 * A site's detection rules. Data only, never code (ADR-0003); the JSON lives in `rules/`.
 *
 * [version] must increase with every change: clients never replace a pack with an older one.
 */
@Serializable
data class RulePack(
    val schemaVersion: Int,
    val id: String,
    val version: Long,
    val web: WebRules? = null,
    val android: AndroidRules? = null,
    val block: BlockRules? = null,
)

@Serializable
data class WebRules(
    val hosts: List<String>,
    /** Regexes over the URL path. Each must have exactly one capture group: the video id. */
    val gatedPaths: List<String>,
    /** Where *Continue* leads; `{id}` is replaced by the captured video id. */
    val continueUrl: String,
    /** CSS selectors for elements to hide. Selectors only; Ianua writes the CSS itself. */
    val hideSelectors: List<String> = emptyList(),
)

@Serializable
data class AndroidRules(
    val packages: List<String>,
    /** The screen is gated when any of these rules matches. */
    val gatedScreens: List<ScreenRule>,
    /** Browsers whose address bar is matched against the web rules (ADR-0005). */
    val browsers: List<BrowserRule> = emptyList(),
)

@Serializable
data class BrowserRule(
    @kotlinx.serialization.SerialName("package") val packageName: String,
    val urlBarViewIds: List<String>,
)

/** Matches when any visible node has one of the listed view ids or content descriptions. */
@Serializable
data class ScreenRule(
    val anyViewId: List<String> = emptyList(),
    val anyContentDescription: List<String> = emptyList(),
)

/** Platforms that are short-form in their entirety: blocked outright (ADR-0006). */
@Serializable
data class BlockRules(val platforms: List<BlockPlatform>)

@Serializable
data class BlockPlatform(
    /** Shown on the block screen: "<name> is blocked". */
    val name: String,
    /** Each matches itself and every subdomain. */
    val domains: List<String> = emptyList(),
    val androidPackages: List<String> = emptyList(),
)
