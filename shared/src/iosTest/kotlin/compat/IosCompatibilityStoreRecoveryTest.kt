package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosCompatibilityStoreRecoveryTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "https://may.2chan.net/b/res/123456.htm"

    @Test
    fun corruptCacheRowsAreDroppedInsteadOfFailingEveryLaunch() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.upsertBoard(board())
            store.upsertHistory(history())

            val database = IosCompatibilityDatabase(fileSystem)
            try {
                val payload = requireNotNull(database.readPayload())
                database.writePayload(
                    payload,
                    2_000L,
                    mapOf(
                        "thread:broken" to "{not json",
                        "catalog:compat_board_may:CATALOG:1" to "{}",
                        "closedBatch" to "[]",
                        "future:kind" to "kept"
                    )
                )
            } finally {
                database.close()
            }

            val reopened = IosCompatibilityStore(fileSystem, nowMillis = { 3_000L })
            reopened.initialize()
            assertEquals(listOf(threadUrl), reopened.history.first().map(CompatHistoryEntry::canonicalUrl))
            assertEquals(listOf("compat_board_may"), reopened.boards.first().map(CompatBoard::key))

            val check = IosCompatibilityDatabase(fileSystem)
            try {
                val remaining = check.readCacheRecords()
                assertFalse("thread:broken" in remaining.records)
                assertFalse("catalog:compat_board_may:CATALOG:1" in remaining.records)
                assertFalse("closedBatch" in remaining.records)
                assertEquals("kept", remaining.records["future:kind"])
            } finally {
                check.close()
            }

            // The next launch no longer sees the bad rows at all.
            IosCompatibilityStore(fileSystem, nowMillis = { 4_000L }).initialize()
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    @Test
    fun scrollAnchorUpdatePatchesOnlyTheTabAndKeepsHistoryDurable() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fileSystem, nowMillis = { 1_000L })
            store.initialize()
            store.upsertBoard(board())
            val otherUrl = "https://may.2chan.net/b/res/999.htm"
            store.openTab(tab(threadUrl, "123456", insertedAt = 10L), history())
            store.openTab(tab(otherUrl, "999", insertedAt = 20L), null)
            val tabsBefore = store.tabs.first()
            val historyBefore = store.history.first()

            val anchor = ScrollAnchor(postNo = "123460", offsetPx = 12, fallbackIndex = 4)
            store.updateScrollAnchor(compatTabKey(threadUrl), anchor)

            val tabsAfter = store.tabs.first()
            assertEquals(tabsBefore.map(CompatTab::key), tabsAfter.map(CompatTab::key))
            assertEquals(anchor, tabsAfter.first { it.canonicalUrl == threadUrl }.scrollAnchor)
            assertSame(
                tabsBefore.first { it.canonicalUrl == otherUrl },
                tabsAfter.first { it.canonicalUrl == otherUrl }
            )
            assertSame(historyBefore, store.history.first())

            // The durable history anchor survives a relaunch (overlay replay)
            // and is published by the next ordinary mutation (tab close).
            val relaunched = IosCompatibilityStore(fileSystem, nowMillis = { 2_000L })
            relaunched.initialize()
            assertEquals(anchor, relaunched.history.first().single().scrollAnchor)
            store.closeTabs(setOf(compatTabKey(threadUrl)), 3_000L, emptyMap())
            assertEquals(anchor, store.history.first().single().scrollAnchor)
            assertTrue(store.tabs.first().none { it.canonicalUrl == threadUrl })
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }

    private fun board() = CompatBoard(
        key = "compat_board_may",
        name = "虹裏",
        canonicalUrl = boardUrl,
        originalUrl = boardUrl,
        sortOrder = 0
    )

    private fun history() = CompatHistoryEntry(
        canonicalUrl = threadUrl,
        originalUrl = threadUrl,
        boardKey = "compat_board_may",
        boardName = "虹裏",
        threadNo = "123456",
        title = "スレ",
        replyCount = 3,
        contentUpdatedAtEpochMillis = 500L
    )

    private fun tab(url: String, threadNo: String, insertedAt: Long) = CompatTab(
        key = compatTabKey(url),
        canonicalUrl = url,
        originalUrl = url,
        boardKey = "compat_board_may",
        boardName = "虹裏",
        threadNo = threadNo,
        title = "スレ$threadNo",
        insertedAtEpochMillis = insertedAt,
        contentUpdatedAtEpochMillis = insertedAt
    )
}
