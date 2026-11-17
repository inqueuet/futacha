package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `applyCatalogReplyCounts` commits the tabs and history of a catalog in one write. */
class DesktopCompatibilityReplyCountBatchTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private fun threadUrl(no: Int) = "${boardUrl}res/$no.htm"

    private fun tab(no: Int, replyCount: Int) = CompatTab(
        compatTabKey(threadUrl(no)), threadUrl(no), threadUrl(no), compatBoardKey(boardUrl), "板", "$no", "スレ$no",
        replyCount = replyCount, insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L
    )

    private fun historyEntry(no: Int, replyCount: Int) = CompatHistoryEntry(
        canonicalUrl = threadUrl(no), originalUrl = threadUrl(no), boardKey = compatBoardKey(boardUrl),
        boardName = "板", threadNo = "$no", title = "スレ$no", replyCount = replyCount,
        contentUpdatedAtEpochMillis = 1L
    )

    private fun update(no: Int, replyCount: Int) =
        CompatCatalogReplyCountUpdate(compatTabKey(threadUrl(no)), threadUrl(no), replyCount)

    /** Tabs 1..4 (counts 5/10/3/8, each with a history entry), plus a history-only entry 5 (count 2). */
    private suspend fun seed(store: DesktopCompatibilityStore) {
        store.initialize()
        store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", boardUrl, "")))
        listOf(1 to 5, 2 to 10, 3 to 3, 4 to 8).forEach { (no, count) ->
            store.openTab(tab(no, count), historyEntry(no, count))
        }
        store.upsertHistory(historyEntry(5, 2))
    }

    /** Counts commits of the state row with SQLite triggers on a second connection. */
    private fun installWriteCounter(root: File) {
        val db = File(root, "compatibility/compatibility.db")
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.createStatement().use { s ->
                s.execute("CREATE TABLE IF NOT EXISTS test_state_writes(n INTEGER)")
                s.execute("CREATE TRIGGER IF NOT EXISTS test_state_insert AFTER INSERT ON compat_state BEGIN INSERT INTO test_state_writes VALUES(1); END")
                s.execute("CREATE TRIGGER IF NOT EXISTS test_state_update AFTER UPDATE ON compat_state BEGIN INSERT INTO test_state_writes VALUES(1); END")
            }
        }
    }

    private fun stateWrites(root: File): Int {
        val db = File(root, "compatibility/compatibility.db")
        return DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.createStatement().use { s ->
                s.executeQuery("SELECT COUNT(*) FROM test_state_writes").use { r -> r.next(); r.getInt(1) }
            }
        }
    }

    private val updates = listOf(
        update(1, 9),   // grows: tab and history
        update(2, 4),   // older / smaller catalog: must not roll back
        update(3, 3),   // equal: no change
        update(5, 20),  // history-only entry grows, there is no tab
        update(6, 7),   // neither tab nor history exists: nothing is created
        update(1, 12)   // the same thread twice in a batch: the later, larger value wins
    )

    @Test
    fun batchedResultMatchesTheOneByOneDefaultAndWritesOnce() = runBlocking {
        val batchRoot = Files.createTempDirectory("compat-reply-batch").toFile()
        val singleRoot = Files.createTempDirectory("compat-reply-single").toFile()
        val batched = DesktopCompatibilityStore(JvmFileSystem(batchRoot))
        val single = DesktopCompatibilityStore(JvmFileSystem(singleRoot))
        try {
            seed(batched); seed(single)
            installWriteCounter(batchRoot); installWriteCounter(singleRoot)

            val batchedChanged = batched.applyCatalogReplyCounts(updates)
            val singleChanged = single.applyCatalogReplyCountsOneByOne(updates)

            // Same meaning as one by one: every update that changed a tab counts (thread 1 twice).
            assertEquals(2, batchedChanged)
            assertEquals(singleChanged, batchedChanged)
            assertEquals(single.tabs.first(), batched.tabs.first())
            assertEquals(single.history.first(), batched.history.first())
            assertEquals(
                mapOf(1 to 12, 2 to 10, 3 to 3, 4 to 8),
                batched.tabs.first().associate { it.threadNo.toInt() to it.replyCount }
            )
            assertEquals(
                mapOf(1 to 12, 2 to 10, 3 to 3, 4 to 8, 5 to 20),
                batched.history.first().associate { it.threadNo.toInt() to it.replyCount }
            )
            assertFalse(batched.tabs.first().any { it.threadNo == "6" })
            assertFalse(batched.history.first().any { it.threadNo == "6" })

            assertEquals(1, stateWrites(batchRoot))
            assertTrue(stateWrites(singleRoot) > 1, "the one-by-one default writes per row")
        } finally {
            batched.close(); single.close(); batchRoot.deleteRecursively(); singleRoot.deleteRecursively()
        }
    }

    @Test
    fun nothingToChangeWritesNothingAndKeepsReadCountsAndAnchors() = runBlocking {
        val root = Files.createTempDirectory("compat-reply-noop").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            seed(store)
            installWriteCounter(root)
            val before = store.tabs.first() to store.history.first()

            assertEquals(0, store.applyCatalogReplyCounts(emptyList()))
            assertEquals(0, store.applyCatalogReplyCounts(listOf(update(1, 5), update(2, 1), update(4, 0))))

            assertEquals(before, store.tabs.first() to store.history.first())
            assertEquals(0, stateWrites(root))
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test
    fun aDeletedHistoryEntryStaysDeletedWhileTheTabStillGrows() = runBlocking {
        val root = Files.createTempDirectory("compat-reply-tombstone").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            seed(store)
            store.deleteHistory(threadUrl(1))

            assertEquals(1, store.applyCatalogReplyCounts(listOf(update(1, 9))))

            assertEquals(9, store.tabs.first().first { it.threadNo == "1" }.replyCount)
            assertTrue(store.history.first().none { it.canonicalUrl == threadUrl(1) })
        } finally { store.close(); root.deleteRecursively() }
    }
}
