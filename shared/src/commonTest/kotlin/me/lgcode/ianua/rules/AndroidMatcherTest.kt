package me.lgcode.ianua.rules

import me.lgcode.ianua.generated.AndroidFixtures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMatcherTest {
    private val matcher = youtube().let { AndroidMatcher(it.android!!, WebMatcher(it.web!!)) }

    private fun fixture(name: String) = ScreenSnapshot.fromJson(AndroidFixtures.all.getValue(name))

    private fun gated(name: String) = fixture(name).let { matcher.isGatedScreen(it.packageName, it.root) }

    @Test
    fun shortsPlayerIsGated() {
        assertTrue(gated("youtube_shorts_player"))
    }

    @Test
    fun homeFeedWithShortsShelfIsNotGated() {
        assertFalse(gated("youtube_home"))
    }

    @Test
    fun invisibleShortsContainerIsNotGated() {
        assertFalse(gated("youtube_watch_with_hidden_reel"))
    }

    @Test
    fun otherPackagesAreNeverGated() {
        val shorts = fixture("youtube_shorts_player")
        assertFalse(matcher.isGatedScreen("com.example.other", shorts.root))
    }

    @Test
    fun snapshotRoundTripsThroughJson() {
        val shorts = fixture("youtube_shorts_player")
        val copy = ScreenSnapshot(shorts.packageName, NodeSnapshot.copyOf(shorts.root, className = { (it as NodeSnapshot).className }))
        assertTrue(ScreenSnapshot.fromJson(copy.toJson()) == shorts)
    }

    @Test
    fun shortsUrlInABrowserIsGated() {
        assertTrue(gated("brave_youtube_shorts"))
    }

    @Test
    fun shortsUrlBeingTypedIsNotGated() {
        assertFalse(gated("brave_typing_shorts_url"))
    }

    @Test
    fun regularVideoInABrowserIsNotGated() {
        assertFalse(gated("brave_youtube_watch"))
    }

    @Test
    fun browsersAreWatchedOnlyWithWebRules() {
        assertTrue("com.brave.browser" in matcher.packages)
        assertFalse("com.brave.browser" in AndroidMatcher(youtube().android!!).packages)
    }

    @Test
    fun unlistedBrowserIsIgnored() {
        val shorts = fixture("brave_youtube_shorts")
        assertFalse(matcher.isGatedScreen("com.example.browser", shorts.root))
    }

    @Test
    fun dumpsKeepTextOnlyForAllowedIds() {
        val shorts = fixture("brave_youtube_shorts")
        val stripped = NodeSnapshot.copyOf(shorts.root)
        assertTrue(stripped.findByViewId("com.brave.browser:id/url_bar").single().text == null)
        val kept = NodeSnapshot.copyOf(shorts.root, keepTextOf = setOf("com.brave.browser:id/url_bar"))
        assertTrue(kept.findByViewId("com.brave.browser:id/url_bar").single().text == "m.youtube.com/shorts/abcDEF12345")
    }

    @Test
    fun stopsAtTheNodeBudget() {
        val wide = NodeSnapshot(children = List(AndroidMatcher.MAX_NODES) { NodeSnapshot() } +
            NodeSnapshot(viewId = "com.google.android.youtube:id/reel_recycler"))
        assertFalse(matcher.isGatedScreen("com.google.android.youtube", wide))
    }
}
