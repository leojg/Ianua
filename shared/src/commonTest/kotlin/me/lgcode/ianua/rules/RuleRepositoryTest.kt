package me.lgcode.ianua.rules

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class InMemoryStore : RuleStore {
    val saved = mutableMapOf<String, String>()
    override suspend fun load(id: String) = saved[id]
    override suspend fun save(id: String, text: String) {
        saved[id] = text
    }
}

class RuleRepositoryTest {
    private val store = InMemoryStore()
    private var remote: String? = null
    private val repo = RuleRepository(RuleRepository.YOUTUBE, store, { url ->
        assertEquals("https://raw.githubusercontent.com/leojg/Ianua/master/rules/youtube.json", url)
        remote
    })
    private val bundledVersion = repo.bundled.version
    private val bundledText = me.lgcode.ianua.generated.BundledRules.all.getValue("youtube")

    private fun withVersion(version: Long) =
        bundledText.replace("\"version\": $bundledVersion", "\"version\": $version")

    @Test
    fun usesBundledPackWhenNothingIsCached() = runTest {
        assertEquals(bundledVersion, repo.current().version)
    }

    @Test
    fun newerRemotePackIsSavedAndUsed() = runTest {
        remote = withVersion(bundledVersion + 1)
        assertIs<RefreshResult.Updated>(repo.refresh())
        assertEquals(bundledVersion + 1, repo.current().version)
    }

    @Test
    fun sameOrOlderRemotePackIsIgnored() = runTest {
        remote = withVersion(bundledVersion)
        assertIs<RefreshResult.UpToDate>(repo.refresh())
        remote = withVersion(bundledVersion - 1)
        assertIs<RefreshResult.UpToDate>(repo.refresh())
        assertNull(store.saved["youtube"])
    }

    @Test
    fun invalidRemotePackIsRejectedAndLastKnownGoodKept() = runTest {
        remote = withVersion(bundledVersion + 1)
        repo.refresh()
        remote = withVersion(bundledVersion + 2).replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        assertIs<RefreshResult.Rejected>(repo.refresh())
        assertEquals(bundledVersion + 1, repo.current().version)
    }

    @Test
    fun packForAnotherSiteIsRejected() = runTest {
        remote = withVersion(bundledVersion + 1).replace("\"id\": \"youtube\"", "\"id\": \"tiktok\"")
        assertIs<RefreshResult.Rejected>(repo.refresh())
    }

    @Test
    fun fetchFailureKeepsCurrentPack() = runTest {
        remote = null
        assertIs<RefreshResult.FetchFailed>(repo.refresh())
        assertEquals(bundledVersion, repo.current().version)
    }

    @Test
    fun cachedPackOlderThanBundledIsIgnored() = runTest {
        // An app update ships a newer bundled pack than what was fetched before it.
        store.saved["youtube"] = withVersion(bundledVersion - 1)
        assertEquals(bundledVersion, repo.current().version)
    }
}
