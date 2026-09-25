package me.lgcode.ianua.rules

import me.lgcode.ianua.generated.AndroidFixtures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMatcherTest {
    private val matcher = AndroidMatcher(youtube().android!!)

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
        val copy = ScreenSnapshot(shorts.packageName, NodeSnapshot.copyOf(shorts.root, { (it as NodeSnapshot).className }))
        assertTrue(ScreenSnapshot.fromJson(copy.toJson()) == shorts)
    }

    @Test
    fun stopsAtTheNodeBudget() {
        val wide = NodeSnapshot(children = List(AndroidMatcher.MAX_NODES) { NodeSnapshot() } +
            NodeSnapshot(viewId = "com.google.android.youtube:id/reel_recycler"))
        assertFalse(matcher.isGatedScreen("com.google.android.youtube", wide))
    }
}
