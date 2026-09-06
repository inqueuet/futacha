package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.IosCompatibilityStore
import com.valoser.futacha.shared.compat.CompatWatcherRepository
import com.valoser.futacha.shared.compat.CompatWatchMatch
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosCompatExternalWatcherTest {
    @Test
    fun inAppWatcherListsAndDeletesResultsWithoutDeletingCompatibilityHistory() = runBlocking {
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
            val now = Clock.System.now().toEpochMilliseconds()
            val first = history(firstUrl, "123456", "古い巡回結果", now - 1000)
            val second = history(secondUrl, "123457", "新しい巡回結果", now)
            store.upsertHistory(first)
            store.upsertHistory(second)
            val results = CompatWatcherRepository(store)
            results.record(CompatWatchMatch(first, true, "古い"))
            results.record(CompatWatchMatch(second, true, "新しい"))
            val watcher = IosCompatExternalWatcher(store)

            val initial = watcher.load().getOrThrow()
            assertTrue(initial.installed)
            assertTrue(initial.available)
            assertEquals(listOf(secondUrl, firstUrl), initial.entries.map(CompatExternalWatcherEntry::threadUrl))

            watcher.delete(firstUrl).getOrThrow()
            assertEquals(listOf(secondUrl), watcher.load().getOrThrow().entries.map(CompatExternalWatcherEntry::threadUrl))

            watcher.deleteAll().getOrThrow()
            assertTrue(watcher.load().getOrThrow().entries.isEmpty())
            assertEquals(2, store.history.first().size)
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
