package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.IosCompatibilityStore
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosCompatExternalWatcherTest {
    @Test
    fun inAppWatcherListsAndDeletesPersistedCompatibilityHistory() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val boardUrl = "https://may.2chan.net/b/"
            val firstUrl = "https://may.2chan.net/b/res/123456.htm"
            val secondUrl = "https://may.2chan.net/b/res/123457.htm"
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.upsertBoard(
                CompatBoard(
                    key = "compat_board_may",
                    name = "虹裏",
                    canonicalUrl = boardUrl,
                    originalUrl = boardUrl,
                    sortOrder = 0
                )
            )
            store.upsertHistory(history(firstUrl, "123456", "古い巡回結果", 1_000L))
            store.upsertHistory(history(secondUrl, "123457", "新しい巡回結果", 2_000L))
            val watcher = IosCompatExternalWatcher(store)

            val initial = watcher.load().getOrThrow()
            assertTrue(initial.installed)
            assertTrue(initial.available)
            assertEquals(listOf(secondUrl, firstUrl), initial.entries.map(CompatExternalWatcherEntry::threadUrl))

            watcher.delete(firstUrl).getOrThrow()
            assertEquals(listOf(secondUrl), watcher.load().getOrThrow().entries.map(CompatExternalWatcherEntry::threadUrl))

            watcher.deleteAll().getOrThrow()
            assertTrue(watcher.load().getOrThrow().entries.isEmpty())
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    private fun history(url: String, threadNo: String, title: String, updatedAt: Long): CompatHistoryEntry =
        CompatHistoryEntry(
            canonicalUrl = url,
            originalUrl = url,
            boardKey = "compat_board_may",
            boardName = "虹裏",
            threadNo = threadNo,
            title = title,
            replyCount = 3,
            contentUpdatedAtEpochMillis = updatedAt
        )
}
