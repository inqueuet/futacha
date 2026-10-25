package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * "このスレッドだけ" NG outlives its tab across restarts as on Android, and goes with its board
 * when that board is deleted, like the board's own NG (L86).
 */
class DesktopCompatibilityThreadScopedNgTest {
    private val mayUrl = "https://may.2chan.net/b/"
    private val imgUrl = "https://img.2chan.net/b/"
    private val boards = listOf(
        BoardSummary("may", "may", "", mayUrl, ""),
        BoardSummary("img", "img", "", imgUrl, "")
    )
    private var clock = 1_000L

    @Test
    fun aThreadScopedRuleOutlivesItsClosedTabAcrossRestarts(): Unit = runBlocking {
        val root = Files.createTempDirectory("compat-thread-ng-restart").toFile()
        val fs = JvmFileSystem(root)
        var store = DesktopCompatibilityStore(fs, nowMillis = { ++clock })
        try {
            store.initialize()
            store.importModernBoards(boards)
            val closed = tab(mayUrl, "100")
            val open = tab(mayUrl, "101")
            store.openTab(closed)
            store.openTab(open)
            assertTrue(store.upsertNgRule(rule("word", CompatNgKind.THREAD_WORD, closed.key)))
            assertTrue(store.upsertNgRule(rule("id", CompatNgKind.THREAD_POSTER_ID, closed.key)))
            assertTrue(store.upsertNgRule(rule("image", CompatNgKind.THREAD_IMAGE_PHASH, closed.key)))
            assertTrue(store.upsertNgRule(rule("open", CompatNgKind.THREAD_WORD, open.key)))
            assertNotNull(store.closeTabs(setOf(closed.key), nowEpochMillis = ++clock))
            store.close()

            store = DesktopCompatibilityStore(fs, nowMillis = { clock + 60_000L })
            store.initialize()
            assertEquals(setOf("word", "id", "image", "open"), store.ngRules.first().mapTo(mutableSetOf(), CompatNgRule::id))
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test
    fun deletingABoardRemovesTheThreadScopesOfItsTabsHistoryAndClosedTabs(): Unit = runBlocking {
        val root = Files.createTempDirectory("compat-thread-ng-board-delete").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root), nowMillis = { ++clock })
        try {
            store.initialize()
            store.importModernBoards(boards)
            val open = tab(mayUrl, "200")
            val inHistory = tab(mayUrl, "201")
            val justClosed = tab(mayUrl, "202")
            val otherBoard = tab(imgUrl, "300")
            store.openTab(open)
            store.openTab(inHistory, history(inHistory))
            store.openTab(justClosed)
            store.openTab(otherBoard)
            assertTrue(store.upsertNgRule(rule("open", CompatNgKind.THREAD_WORD, open.key)))
            assertTrue(store.upsertNgRule(rule("history", CompatNgKind.THREAD_POST_NO, inHistory.key)))
            assertTrue(store.upsertNgRule(rule("closed", CompatNgKind.THREAD_IMAGE, justClosed.key)))
            assertTrue(store.upsertNgRule(rule("other", CompatNgKind.THREAD_WORD, otherBoard.key)))
            assertTrue(store.upsertNgRule(rule("board", CompatNgKind.CATALOG_WORD, compatBoardKey(mayUrl))))
            assertTrue(store.upsertNgRule(rule("everywhere", CompatNgKind.THREAD_WORD, "*")))
            // The history row stays after its tab closes; the second close replaces the pending batch.
            assertNotNull(store.closeTabs(setOf(inHistory.key), nowEpochMillis = ++clock))
            assertNotNull(store.closeTabs(setOf(justClosed.key), nowEpochMillis = ++clock))

            store.deleteBoard(compatBoardKey(mayUrl))
            assertEquals(setOf("other", "everywhere"), store.ngRules.first().mapTo(mutableSetOf(), CompatNgRule::id))
        } finally { store.close(); root.deleteRecursively() }
    }

    private fun tab(boardUrl: String, threadNo: String): CompatTab {
        val url = "${boardUrl}res/$threadNo.htm"
        return CompatTab(
            key = compatTabKey(url),
            canonicalUrl = url,
            originalUrl = url,
            boardKey = compatBoardKey(boardUrl),
            boardName = "板",
            threadNo = threadNo,
            title = "スレ$threadNo",
            insertedAtEpochMillis = ++clock,
            contentUpdatedAtEpochMillis = clock
        )
    }

    private fun history(tab: CompatTab) = CompatHistoryEntry(
        canonicalUrl = tab.canonicalUrl,
        originalUrl = tab.originalUrl,
        boardKey = tab.boardKey,
        boardName = tab.boardName,
        threadNo = tab.threadNo,
        title = tab.title,
        contentUpdatedAtEpochMillis = tab.contentUpdatedAtEpochMillis
    )

    private fun rule(id: String, kind: CompatNgKind, scopeKey: String) = CompatNgRule(
        id = id,
        kind = kind,
        scopeKey = scopeKey,
        normalizedValue = if (kind == CompatNgKind.THREAD_POST_NO) "5" else "荒らし",
        createdAtEpochMillis = ++clock,
        imageUrl = if (kind == CompatNgKind.THREAD_IMAGE_PHASH) "0123456789abcdef" else null
    )
}
