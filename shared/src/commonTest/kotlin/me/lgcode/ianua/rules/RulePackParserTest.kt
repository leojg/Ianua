package me.lgcode.ianua.rules

import me.lgcode.ianua.generated.BundledRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RulePackParserTest {
    private fun pack(web: String = VALID_WEB, schema: Int = 1, version: Long = 1) =
        """{"schemaVersion":$schema,"id":"t","version":$version,"web":$web}"""

    @Test
    fun everyBundledPackIsValid() {
        assertTrue(BundledRules.all.isNotEmpty())
        BundledRules.all.forEach { (id, text) ->
            val result = RulePackParser.parse(text)
            assertIs<ParseResult.Ok>(result, "$id: $result")
            assertEquals(id, result.pack.id, "file name must match the pack id")
        }
    }

    @Test
    fun acceptsMinimalPack() {
        assertIs<ParseResult.Ok>(RulePackParser.parse(pack()))
    }

    @Test
    fun ignoresUnknownKeys() {
        assertIs<ParseResult.Ok>(RulePackParser.parse(pack().dropLast(1) + ""","future":true}"""))
    }

    @Test
    fun rejectsMalformedJson() {
        assertIs<ParseResult.Invalid>(RulePackParser.parse("{not json"))
    }

    @Test
    fun rejectsUnknownSchemaVersion() {
        assertIs<ParseResult.Invalid>(RulePackParser.parse(pack(schema = 2)))
    }

    @Test
    fun rejectsNonPositiveVersion() {
        assertIs<ParseResult.Invalid>(RulePackParser.parse(pack(version = 0)))
    }

    @Test
    fun rejectsSelectorsThatCouldEscapeTheRule() {
        listOf(
            "a} body{display:none",
            "a;",
            "@import 'x'",
            "a[style*='url(']",
            "</style><script>",
            "a /* c */",
            "a\\7b",
        ).forEach { bad ->
            val web = VALID_WEB.replace("\"ytd-x\"", "\"${bad.replace("\\", "\\\\")}\"")
            assertIs<ParseResult.Invalid>(RulePackParser.parse(pack(web)), bad)
        }
    }

    @Test
    fun gatedPathNeedsExactlyOneCaptureGroup() {
        assertIs<ParseResult.Invalid>(RulePackParser.parse(pack(VALID_WEB.replace("([a-z]+)", "[a-z]+"))))
        assertIs<ParseResult.Invalid>(RulePackParser.parse(pack(VALID_WEB.replace("([a-z]+)", "([a-z])([a-z]+)"))))
        assertIs<ParseResult.Ok>(RulePackParser.parse(pack(VALID_WEB.replace("([a-z]+)", "(?:x)?([a-z]+)"))))
    }

    @Test
    fun continueUrlMustBeHttpsWithId() {
        assertIs<ParseResult.Invalid>(RulePackParser.parse(pack(VALID_WEB.replace("https://", "http://"))))
        assertIs<ParseResult.Invalid>(RulePackParser.parse(pack(VALID_WEB.replace("{id}", "x"))))
    }

    @Test
    fun countsCaptureGroups() {
        assertEquals(1, RulePackParser.captureGroupCount("^/shorts/([A-Za-z0-9_-]{5,})"))
        assertEquals(0, RulePackParser.captureGroupCount("\\(x\\)"))
        assertEquals(0, RulePackParser.captureGroupCount("[(]"))
        assertEquals(1, RulePackParser.captureGroupCount("(?:a)(b)"))
    }

    private companion object {
        const val VALID_WEB =
            """{"hosts":["example.com"],"gatedPaths":["^/s/([a-z]+)"],"continueUrl":"https://example.com/w?v={id}","hideSelectors":["ytd-x"]}"""
    }
}
