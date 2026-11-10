package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The closed-tab record shared by the 4 modes (plan B6, §1-4) on the desktop store: 20 records for 24 hours (before,
 * every close dropped the earlier record and the record itself went after 7 seconds), each mode's marks, a restore
 * done once, and [CompatibilityStore.openSharedTab].
 */
class SharedClosedTabRecordTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private fun url(no: Int) = "${boardUrl}res/$no.htm"
    private fun key(no: Int) = compatTabKey(url(no))
    private fun tab(no: Int, title: String = "スレ$no", favorite: Boolean = false) = CompatTab(
        key(no), url(no), url(no), compatBoardKey(boardUrl), "may", "$no", title,
        replyCount = 1, favorite = favorite, insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong()
    )
    private val futaber = ExperienceProfile.FUTABER.persistedValue
    private val ftutter = ExperienceProfile.FTUTTER.persistedValue
    private val futacha = ExperienceProfile.FUTACHA.persistedValue
    private val now = System.currentTimeMillis()

    private fun withRoot(block: suspend (File) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("closed-record").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }

    private suspend fun open(root: File): DesktopCompatibilityStore = DesktopCompatibilityStore(JvmFileSystem(root)).also {
        it.initialize()
        if (it.boards.first().isEmpty()) it.importModernBoards(listOf(BoardSummary("b", "may", "", boardUrl, "")))
    }

    @Test fun twentyRecordsAreKeptForADayAndOnlyTheNoticeEndsAfterSevenSeconds() = withRoot { root ->
        val store = open(root)
        try {
            (1..25).forEach { no ->
                store.openTab(tab(no))
                assertNotNull(store.closeTabs(setOf(key(no)), now + no, closedBy = futacha))
            }
            assertEquals(20, store.countClosedTabBatches(futacha, now + 30), "the 20 newest records")
            assertEquals(key(25), store.loadPendingClosedTabs(now + 30)?.tabs?.single()?.tab?.key)
            assertNull(store.loadPendingClosedTabs(now + 25 + COMPAT_UNDO_NOTICE_MILLIS), "the notice ends after 7 seconds")
            val popped = store.popClosedTabBatch(futacha, now + 60_000)
            assertEquals(key(25), popped?.tabs?.single()?.tab?.key, "the record outlives the notice")
            assertTrue(store.tabs.first().any { it.key == key(25) })
            assertEquals(19, store.countClosedTabBatches(futacha, now + 60_000))
            assertEquals(0, store.countClosedTabBatches(futacha, now + 26 + COMPAT_CLOSED_BATCH_KEEP_MILLIS), "gone after 24 hours")
        } finally { store.close() }
    }

    @Test fun theRecordAndTheMarksSurviveARestartAndComeBackInOrder() = withRoot { root ->
        val first = open(root)
        try {
            (1..3).forEach { first.openTab(tab(it)) }
            first.replaceTabViews(futaber, (1..3).map { CompatSharedTabView(key(it), futaber, it - 1, 5L) })
            first.closeTabs(setOf(key(2)), now, closedBy = futaber)
            assertEquals(listOf(key(1), key(3)), first.loadTabViews(futaber).map { it.tabKey })
        } finally { first.close() }
        val second = open(root)
        try {
            assertEquals(1, second.countClosedTabBatches(futaber, now + 1), "kept across a restart")
            assertNotNull(second.popClosedTabBatch(futaber, now + 1))
            assertEquals(listOf(key(1), key(2), key(3)), second.loadTabViews(futaber).map { it.tabKey }, "back at its place")
            assertEquals(0, second.countClosedTabBatches(futaber, now + 1))
        } finally { second.close() }
    }

    @Test fun aModeBringsBackOnlyWhatItClosedOrShowed() = withRoot { root ->
        val store = open(root)
        try {
            (1..2).forEach { store.openTab(tab(it)) }
            store.replaceTabViews(ftutter, listOf(CompatSharedTabView(key(2), ftutter, 0, 5L)))
            store.closeTabs(setOf(key(1)), now, closedBy = futacha)
            assertNull(store.popClosedTabBatch(futaber, now + 1), "ふたばー風 neither closed nor showed it")
            store.closeTabs(setOf(key(2)), now + 1, closedBy = futacha)
            assertEquals(key(2), store.popClosedTabBatch(ftutter, now + 2)?.tabs?.single()?.tab?.key, "ふたったー風 showed it")
            assertEquals(key(1), store.popClosedTabBatch(futacha, now + 2)?.tabs?.single()?.tab?.key)
        } finally { store.close() }
    }

    /** Plan B9 (M2): ふたちゃ's and としあき(仮)'s undo restore the record, so the other modes' marks come back too. */
    @Test fun futachaAndToshiakiUndoBringBackTheOtherModesMarks() = withRoot { root ->
        val store = open(root)
        try {
            (1..3).forEach { store.openTab(tab(it, favorite = it == 2)) }
            store.replaceTabViews(futaber, (1..3).map { CompatSharedTabView(key(it), futaber, it - 1, 5L) })
            store.replaceTabViews(ftutter, listOf(CompatSharedTabView(key(2), ftutter, 0, 5L)))
            for (closer in listOf(futacha, ExperienceProfile.TOSHIAKI_COMPAT.persistedValue)) {
                val batch = assertNotNull(store.closeTabs(setOf(key(2)), now, closedBy = closer))
                assertEquals(listOf(key(1), key(3)), store.loadTabViews(futaber).map { it.tabKey })
                assertTrue(store.loadTabViews(ftutter).isEmpty())
                store.restoreClosedTabs(batch)
                assertTrue(store.tabs.first().single { it.key == key(2) }.favorite, "$closer: the star came back")
                assertEquals(listOf(key(1), key(2), key(3)), store.loadTabViews(futaber).map { it.tabKey }, "$closer: ふたばー風's place")
                assertEquals(listOf(key(2)), store.loadTabViews(ftutter).map { it.tabKey }, "$closer: ふたったー風's mark")
            }
        } finally { store.close() }
    }

    @Test fun aRestoreIsDoneOnceAndDoesNotOverwriteATabOpenAgain() = withRoot { root ->
        val store = open(root)
        try {
            store.openTab(tab(1, favorite = true))
            val batch = assertNotNull(store.closeTabs(setOf(key(1)), now, closedBy = futacha))
            store.openTab(tab(1, title = "開き直した"))
            store.restoreClosedTabs(batch)
            val restored = store.tabs.first().single { it.key == key(1) }
            assertEquals("開き直した", restored.title, "the tab open again keeps its row")
            assertTrue(restored.favorite, "the star comes back")
            store.closeTabs(setOf(key(1)), now + 1, closedBy = futacha)
            store.restoreClosedTabs(batch)
            assertFalse(store.tabs.first().any { it.key == key(1) }, "a record already brought back does nothing")
        } finally { store.close() }
    }

    @Test fun aTabFtutterClosedComesBackWithoutItsStar() = withRoot { root ->
        val store = open(root)
        try {
            store.openTab(tab(1, favorite = true))
            store.openTab(tab(2, favorite = true))
            store.closeTabs(setOf(key(1)), now, closedBy = ftutter)
            store.closeTabs(setOf(key(2)), now + 1, closedBy = futacha)
            store.popClosedTabBatch(futacha, now + 2)
            store.popClosedTabBatch(ftutter, now + 2)
            val tabs = store.tabs.first().associateBy { it.key }
            assertFalse(tabs.getValue(key(1)).favorite, "the original's protection does not come back")
            assertTrue(tabs.getValue(key(2)).favorite, "another mode's close keeps the star")
        } finally { store.close() }
    }

    @Test fun openingASharedTabKeepsTheStarAndAddsTheMarkAndTheBoard() = withRoot { root ->
        val store = DesktopCompatibilityStore(JvmFileSystem(root)).also { it.initialize() }
        try {
            val board = CompatBoard(compatBoardKey(boardUrl), "may", boardUrl, boardUrl, 0)
            assertTrue(store.openSharedTab(tab(1, favorite = true), futaber, board = board), "G2: the board is added")
            assertTrue(store.openSharedTab(tab(2), futaber, CompatTabPlacement.FIRST, select = true))
            assertTrue(store.openSharedTab(tab(1, title = "新しい題").copy(favorite = false, insertedAtEpochMillis = 99), ftutter))
            val one = store.tabs.first().single { it.key == key(1) }
            assertTrue(one.favorite)
            assertEquals(1L, one.insertedAtEpochMillis)
            assertEquals(listOf(key(2), key(1)), store.loadTabViews(futaber).map { it.tabKey })
            assertEquals(listOf(key(1)), store.loadTabViews(ftutter).map { it.tabKey })
            assertEquals(key(2), store.workspace.first().activeTabKey)
            assertEquals(3, store.tabViews.first().size, "the flow follows the views")
            store.closeTabs(setOf(key(1)), now)
            assertEquals(listOf(key(2)), store.tabViews.first().map { it.tabKey }, "a view goes with its tab")
        } finally { store.close() }
    }
}
