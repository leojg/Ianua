package me.lgcode.ianua.rules

import me.lgcode.ianua.generated.AndroidFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacksTest {
    private val packs = Packs(RuleRepository.bundledIds.map { bundled(it) })

    private fun fixture(name: String) = ScreenSnapshot.fromJson(AndroidFixtures.all.getValue(name))

    private fun verdict(name: String) = fixture(name).let { packs.verdict(it.packageName, it.root) }

    @Test
    fun bundlesYoutubeAndBlockedPacks() {
        assertTrue("youtube" in RuleRepository.bundledIds && "blocked" in RuleRepository.bundledIds)
    }

    @Test
    fun blocksWholeDomainsAndSubdomains() {
        for (url in listOf(
            "https://www.tiktok.com/@x/video/1",
            "https://tiktok.com/",
            "https://vm.tiktok.com/ZMabc/",
            "http://m.tiktok.com/foo",
            "https://www.douyin.com/",
            "https://likee.video/@x",
        )) {
            assertTrue(packs.block.platformForUrl(url) != null, url)
        }
        assertEquals("TikTok", packs.block.platformForUrl("https://vm.tiktok.com/ZMabc/")?.name)
    }

    @Test
    fun doesNotBlockLookalikes() {
        for (url in listOf(
            "https://nottiktok.com/",
            "https://tiktok.com.evil.test/",
            "https://evil.test/?u=https://www.tiktok.com/",
            "https://www.youtube.com/",
            "ftp://tiktok.com/",
        )) {
            assertNull(packs.block.platformForUrl(url), url)
        }
    }

    @Test
    fun readsAddressBars() {
        assertEquals("TikTok", packs.block.platformForAddressBar("www.tiktok.com/@x/video/1")?.name)
        assertNull(packs.block.platformForAddressBar("tiktok dance trends"))
    }

    @Test
    fun blocksAppsByPackageAlone() {
        assertEquals("TikTok", packs.isBlockedPackage("com.zhiliaoapp.musically")?.name)
        assertEquals("Douyin", packs.isBlockedPackage("com.ss.android.ugc.aweme")?.name)
        assertNull(packs.isBlockedPackage("com.google.android.youtube"))
        assertTrue("com.zhiliaoapp.musically" in packs.androidPackages)
    }

    @Test
    fun blockedSiteInABrowserIsBlocked() {
        val v = verdict("brave_tiktok")
        assertIs<Verdict.Block>(v)
        assertEquals("TikTok", v.platform.name)
    }

    @Test
    fun shortsInABrowserIsStillGated() {
        assertIs<Verdict.Gate>(verdict("brave_youtube_shorts"))
        assertIs<Verdict.None>(verdict("brave_youtube_watch"))
        assertIs<Verdict.Gate>(verdict("youtube_shorts_player"))
        assertIs<Verdict.None>(verdict("youtube_home"))
    }

    @Test
    fun gateBeatsBlock() {
        val hostile = RulePackParser.parse(
            """{"schemaVersion":1,"id":"hostile","version":1,"block":{"platforms":[
              {"name":"Bad","domains":["youtube.com","example.test"],
               "androidPackages":["com.google.android.youtube","com.brave.browser","com.example.bad"]}]}}""",
        ) as ParseResult.Ok
        val combined = Packs(listOf(bundled("youtube"), hostile.pack))
        assertNull(combined.block.platformForUrl("https://www.youtube.com/"))
        assertNull(combined.block.platformForUrl("https://m.youtube.com/shorts/abcDEF12345"))
        assertNull(combined.isBlockedPackage("com.google.android.youtube"))
        assertNull(combined.isBlockedPackage("com.brave.browser"), "blocking a listed browser would lock it entirely")
        assertEquals("Bad", combined.block.platformForUrl("https://example.test/")?.name)
        assertEquals("Bad", combined.isBlockedPackage("com.example.bad")?.name)
    }

    @Test
    fun bundledBlockListDoesNotOverlapGatedPlatforms() {
        val raw = bundled("blocked").block!!.platforms
        assertEquals(raw, packs.block.platforms, "an entry was silently dropped by gate-beats-block")
    }

    @Test
    fun gatedVideoNamesItsPack() {
        assertEquals(Packs.GatedVideo("youtube", "abcDEF12345"), packs.gatedVideo("https://www.youtube.com/shorts/abcDEF12345"))
        assertNull(packs.gatedVideo("https://www.tiktok.com/@x/video/1"))
    }
}

internal fun bundled(id: String): RulePack = RuleRepository(id, InMemoryStore(), { null }).bundled
