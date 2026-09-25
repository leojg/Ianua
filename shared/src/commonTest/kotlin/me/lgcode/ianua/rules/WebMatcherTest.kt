package me.lgcode.ianua.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebMatcherTest {
    private val matcher = WebMatcher(youtube().web!!)

    @Test
    fun gatesShortsOnAllYouTubeHosts() {
        assertEquals("abcDEF12_-x", matcher.gatedVideoId("https://www.youtube.com/shorts/abcDEF12_-x"))
        assertEquals("abcDEF12345", matcher.gatedVideoId("https://m.youtube.com/shorts/abcDEF12345"))
        assertEquals("abcDEF12345", matcher.gatedVideoId("https://youtube.com/shorts/abcDEF12345"))
        assertEquals("abcDEF12345", matcher.gatedVideoId("https://WWW.YouTube.com:443/shorts/abcDEF12345"))
    }

    @Test
    fun ignoresQueryAndFragment() {
        assertEquals("abcDEF12345", matcher.gatedVideoId("https://www.youtube.com/shorts/abcDEF12345?feature=share#t=3"))
    }

    @Test
    fun doesNotGateRegularPages() {
        assertNull(matcher.gatedVideoId("https://www.youtube.com/watch?v=abcDEF12345"))
        assertNull(matcher.gatedVideoId("https://www.youtube.com/"))
        assertNull(matcher.gatedVideoId("https://www.youtube.com/@channel/shorts"))
        assertNull(matcher.gatedVideoId("https://www.youtube.com/results?search_query=/shorts/abcDEF12345"))
        assertNull(matcher.gatedVideoId("https://www.youtube.com/shorts/"))
    }

    @Test
    fun doesNotGateOtherHosts() {
        assertNull(matcher.gatedVideoId("https://example.com/shorts/abcDEF12345"))
        assertNull(matcher.gatedVideoId("https://youtube.com.evil.test/shorts/abcDEF12345"))
        assertNull(matcher.gatedVideoId("https://evil.test/?u=https://www.youtube.com/shorts/abcDEF12345"))
    }

    @Test
    fun readsSchemeLessAddressBars() {
        assertEquals("abcDEF12345", matcher.gatedVideoIdInAddressBar("m.youtube.com/shorts/abcDEF12345"))
        assertEquals("abcDEF12345", matcher.gatedVideoIdInAddressBar(" https://www.youtube.com/shorts/abcDEF12345 "))
        assertNull(matcher.gatedVideoIdInAddressBar("m.youtube.com/watch?v=abcDEF12345"))
        assertNull(matcher.gatedVideoIdInAddressBar("youtube shorts funny"))
        assertNull(matcher.gatedVideoIdInAddressBar(""))
    }

    @Test
    fun continueLeadsToTheRegularPlayer() {
        assertEquals("https://www.youtube.com/watch?v=abcDEF12345", matcher.continueUrl("abcDEF12345"))
    }

    @Test
    fun hideCssHasOneRulePerSelector() {
        val css = matcher.hideCss().lines()
        assertEquals(youtube().web!!.hideSelectors.size, css.size)
        assertTrue(css.all { it.endsWith("{ display: none !important; }") })
    }

    @Test
    fun navigationFilterMatchesTheSameUrlsAndCapturesTheId() {
        val filters = matcher.navigationRegexFilters().map(::Regex)
        val url = "https://m.youtube.com/shorts/abcDEF12345?feature=share"
        val match = filters.firstNotNullOf { it.find(url) }
        assertEquals("abcDEF12345", match.groupValues[1])
        assertEquals(url, match.value, "the whole URL must be consumed, or DNR keeps the rest")
        assertTrue(filters.none { it.containsMatchIn("https://www.youtube.com/watch?v=abcDEF12345") })
        assertTrue(filters.none { it.containsMatchIn("https://wwwxyoutube.com/shorts/abcDEF12345") })
    }
}

internal fun youtube(): RulePack = RuleRepository(RuleRepository.YOUTUBE, InMemoryStore(), { null }).bundled
