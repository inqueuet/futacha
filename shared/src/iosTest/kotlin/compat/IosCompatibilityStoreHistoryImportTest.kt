package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosCompatibilityStoreHistoryImportTest {
    private val boardUrl = "https://may.2chan.net/b/"

    @Test
    fun largeModernHistoryImportsOnlyTheNewestEntriesAndIsStableOnRepeat(): Unit = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.upsertBoard(
                CompatBoard(
                    key = compatBoardKey(boardUrl),
                    name = "虹裏",
                    canonicalUrl = boardUrl,
                    originalUrl = boardUrl,
                    sortOrder = 0
                )
            )
            // Shuffled so the newest entries are not simply the first ones.
            val modern = (0 until 20_000).map { index ->
                ThreadHistoryEntry(
                    threadId = (100_000 + index).toString(),
                    title = "スレ$index",
                    titleImageUrl = "",
                    boardName = "虹裏",
                    boardUrl = boardUrl,
                    lastVisitedEpochMillis = 10_000L + index,
                    replyCount = 1
                )
            }.shuffled()

            val imported = store.importModernHistory(modern)
            val history = store.history.first()
            assertEquals(history.size, imported)
            assertTrue(history.size in 1..200)
            assertEquals(
                (119_999 downTo 119_999 - history.size + 1).map { "${boardUrl}res/$it.htm" },
                history.map(CompatHistoryEntry::canonicalUrl)
            )

            // Nothing older than the kept entries churns the store again.
            assertEquals(0, store.importModernHistory(modern))
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }
}
