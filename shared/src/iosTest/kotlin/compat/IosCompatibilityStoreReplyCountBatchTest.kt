package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The batched `applyCatalogReplyCounts` gives the same rows and count as the one-by-one default. */
class IosCompatibilityStoreReplyCountBatchTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private fun threadUrl(no: Int) = "${boardUrl}res/$no.htm"
    private fun tab(no: Int, count: Int) = CompatTab(
        compatTabKey(threadUrl(no)), threadUrl(no), threadUrl(no), compatBoardKey(boardUrl), "板", "$no", "スレ$no",
        replyCount = count, insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L
    )
    private fun entry(no: Int, count: Int) = CompatHistoryEntry(
        canonicalUrl = threadUrl(no), originalUrl = threadUrl(no), boardKey = compatBoardKey(boardUrl),
        boardName = "板", threadNo = "$no", title = "スレ$no", replyCount = count, contentUpdatedAtEpochMillis = 1L
    )
    private fun update(no: Int, count: Int) =
        CompatCatalogReplyCountUpdate(compatTabKey(threadUrl(no)), threadUrl(no), count)

    private suspend fun seed(store: IosCompatibilityStore) {
        store.initialize()
        store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", boardUrl, "")))
        listOf(1 to 5, 2 to 10, 3 to 3, 4 to 8).forEach { (no, count) -> store.openTab(tab(no, count), entry(no, count)) }
        store.upsertHistory(entry(5, 2))
    }

    @Test
    fun batchMatchesOneByOneAndNeverRollsBackOrCreatesRows(): Unit = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val updates = listOf(update(1, 9), update(2, 4), update(3, 3), update(5, 20), update(6, 7), update(1, 12))

            val batched = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            seed(batched)
            val batchedChanged = batched.applyCatalogReplyCounts(updates)
            val batchedTabs = batched.tabs.first()
            val batchedHistory = batched.history.first()

            fileSystem.deleteRecursively("compatibility").getOrThrow()
            val single = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            seed(single)
            val singleChanged = single.applyCatalogReplyCountsOneByOne(updates)

            assertEquals(2, batchedChanged)
            assertEquals(singleChanged, batchedChanged)
            assertEquals(single.tabs.first(), batchedTabs)
            assertEquals(single.history.first(), batchedHistory)
            assertEquals(
                mapOf(1 to 12, 2 to 10, 3 to 3, 4 to 8),
                batchedTabs.associate { it.threadNo.toInt() to it.replyCount }
            )
            assertEquals(
                mapOf(1 to 12, 2 to 10, 3 to 3, 4 to 8, 5 to 20),
                batchedHistory.associate { it.threadNo.toInt() to it.replyCount }
            )
            assertTrue(batchedTabs.none { it.threadNo == "6" })
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun deletedHistoryStaysDeletedWhileTheTabGrows(): Unit = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            seed(store)
            store.deleteHistory(threadUrl(1))
            assertEquals(1, store.applyCatalogReplyCounts(listOf(update(1, 9))))
            assertEquals(9, store.tabs.first().first { it.threadNo == "1" }.replyCount)
            assertTrue(store.history.first().none { it.canonicalUrl == threadUrl(1) })
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }
}
