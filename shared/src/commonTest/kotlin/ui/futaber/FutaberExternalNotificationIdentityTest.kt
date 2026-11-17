package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ui.compat.CompatExternalWatcher
import com.valoser.futacha.shared.ui.compat.CompatExternalWatcherEntry
import com.valoser.futacha.shared.ui.compat.CompatExternalWatcherSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Exercises the production load/remove boundary; the fake only implements the provider's opaque-key storage. */
@OptIn(ExperimentalCoroutinesApi::class)
class FutaberExternalNotificationIdentityTest {
    private fun entry(key: String, keyword: String = "猫", url: String = "https://may.2chan.net/b/res/1234.htm") =
        CompatExternalWatcherEntry(key, true, "共通通知", 12, url,
            extractedKeyword = keyword, boardName = "may", insertedAtEpochMillis = 100, updatedAtEpochMillis = 200)

    private class Provider(entries: List<CompatExternalWatcherEntry>) : CompatExternalWatcher {
        var rows = entries
        val deleted = mutableListOf<String>()
        var clears = 0
        var failDelete = false
        override suspend fun load() = Result.success(CompatExternalWatcherSnapshot(true, true, entries = rows))
        override suspend fun delete(key: String): Result<Unit> {
            deleted += key
            if (failDelete) return Result.failure(IllegalStateException("削除失敗"))
            rows = rows.filterNot { it.key == key }
            return Result.success(Unit)
        }
        override suspend fun deleteAll(): Result<Unit> { clears++; rows = emptyList(); return Result.success(Unit) }
        override fun openManager() = Result.success(Unit)
    }

    @Test fun differentKeywordsAtOneCanonicalUrlDeleteOnlyTheChosenProviderKey() = runTest {
        val provider = Provider(listOf(entry("first"), entry("second", "犬", "http://may.2chan.net/b/res/1234.htm?x=1"),
            entry("unrelated", url = "https://may.2chan.net/b/res/5678.htm")))
        val state = FutaberExternalWatcher(provider, this)
        state.reload = { launch { state.load(true) }; Unit }
        state.load(true)
        val first = state.results!!.first()
        assertEquals(state.results!![1].history.canonicalUrl, first.history.canonicalUrl)
        assertEquals(listOf("猫", "犬", "猫"), state.results!!.map { it.keyword })
        state.remove(first)
        runCurrent()
        assertEquals(listOf("first"), provider.deleted)
        assertEquals(listOf("second", "unrelated"), provider.rows.map { it.key })
        assertEquals(listOf("犬", "猫"), state.results!!.map { it.keyword })
        assertEquals(0, provider.clears)
    }

    @Test fun otherwiseIdenticalProviderRowsKeepTheirOwnIdentityAndDeletionKey() = runTest {
        val provider = Provider(listOf(entry("opaque:one"), entry("opaque:two")))
        val state = FutaberExternalWatcher(provider, this)
        state.load(true)
        val rows = state.results!!
        assertNotEquals(rows[0], rows[1], "Distinct provider IDs must survive conversion even if all visible fields match")
        state.remove(rows[0])
        runCurrent()
        assertEquals(listOf("opaque:one"), provider.deleted)
        assertEquals(listOf("opaque:two"), provider.rows.map { it.key })
        assertEquals(0, provider.clears)
    }

    @Test fun providerKeyCannotBeReplacedByTheNormalizedUrlOrAStaleUrlMapping() = runTest {
        val provider = Provider(listOf(entry("opaque:first")))
        val state = FutaberExternalWatcher(provider, this)
        state.load(true)
        val old = state.results!!.single()
        provider.rows = listOf(entry("opaque:new", "犬"))
        state.load(true)
        state.remove(old)
        runCurrent()
        assertEquals(listOf("opaque:first"), provider.deleted)
        assertEquals(listOf("opaque:new"), provider.rows.map { it.key })
        assertEquals(0, provider.clears)
    }

    @Test fun failedSingleDeletionKeepsEveryProviderRowAndReportsFailure() = runTest {
        val provider = Provider(listOf(entry("first"), entry("second", "犬"))).also { it.failDelete = true }
        val state = FutaberExternalWatcher(provider, this)
        state.load(true)
        state.remove(state.results!!.first())
        runCurrent()
        assertEquals(listOf("first"), provider.deleted)
        assertEquals(2, provider.rows.size)
        assertEquals("削除失敗", state.status)
        assertEquals(0, provider.clears)
        val old = state.results!!.first()
        state.load(false)
        assertNull(state.results)
        state.remove(old)
        runCurrent()
        assertEquals(listOf("first"), provider.deleted)
    }

    @Test fun externalIdentityDoesNotAlterTheStoredInternalResultJson() {
        val result = assertNotNull(futaberWatchResultFromExternal(entry("provider-key-that-must-not-be-persisted")))
        val encoded = Json { encodeDefaults = true }.encodeToString(result)
        assertFalse(encoded.contains("provider-key-that-must-not-be-persisted"))
        assertFalse(encoded.contains("externalEntryKey"))
        assertTrue(encoded.contains("共通通知"))
    }
}
