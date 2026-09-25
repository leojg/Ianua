package me.lgcode.ianua.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.lgcode.ianua.rules.RefreshResult
import me.lgcode.ianua.rules.RuleFetcher
import me.lgcode.ianua.rules.RulePack
import me.lgcode.ianua.rules.RuleRepository
import me.lgcode.ianua.rules.RuleStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The rule pack in effect, observable so the running service picks up refreshes. */
class Rules(context: Context) {
    private val repository = RuleRepository(
        RuleRepository.YOUTUBE,
        FileRuleStore(File(context.filesDir, "rules")),
        HttpRuleFetcher,
    )

    private val _current = MutableStateFlow(repository.bundled)
    val current: StateFlow<RulePack> = _current.asStateFlow()

    /** Replaces the bundled pack with the cached one, if that is newer. */
    suspend fun load() {
        _current.value = repository.current()
    }

    suspend fun refresh(): RefreshResult = repository.refresh().also {
        if (it is RefreshResult.Updated) _current.value = it.pack
    }
}

private class FileRuleStore(private val dir: File) : RuleStore {
    override suspend fun load(id: String): String? = withContext(Dispatchers.IO) {
        File(dir, "$id.json").takeIf { it.isFile }?.readText()
    }

    override suspend fun save(id: String, text: String) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        // Write-then-rename, so a crash never leaves a half-written pack behind.
        val tmp = File(dir, "$id.json.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(File(dir, "$id.json"))) { "could not replace $id.json" }
    }
}

private object HttpRuleFetcher : RuleFetcher {
    private const val MAX_BYTES = 256 * 1024

    override suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.useCaches = false
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val bytes = connection.inputStream.use { it.readNBytesCompat(MAX_BYTES + 1) }
                if (bytes.size > MAX_BYTES) null else bytes.decodeToString()
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    // InputStream.readNBytes needs API 33.
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
