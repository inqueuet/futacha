package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** The shared closed-tab record and the marks on iOS's store (plan B6): kept in the cache rows across a relaunch. */
class IosSharedClosedTabRecordTest {
    private val board = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(board)
    private fun url(no: Int) = "${board}res/$no.htm"
    private fun key(no: Int) = compatTabKey(url(no))
    private fun tab(no: Int, favorite: Boolean = false) = CompatTab(key(no), url(no), url(no), boardKey, "may", "$no", "スレ$no",
        favorite = favorite, insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong())
    private val futaber = ExperienceProfile.FUTABER.persistedValue
    private val ftutter = ExperienceProfile.FTUTTER.persistedValue
    private val now = 1_000_000L

    @Test fun twentyRecordsAndTheMarksSurviveARelaunchAndComeBack() = runBlocking {
        val fs = createFileSystem()
        fs.deleteRecursively("compatibility").getOrThrow()
        try {
            val first = IosCompatibilityStore(fs, nowMillis = { now })
            first.initialize()
            first.upsertBoard(CompatBoard(boardKey, "may", board, board, 0))
            (1..3).forEach { first.openTab(tab(it, favorite = it == 2), null) }
            first.replaceTabViews(futaber, (1..3).map { CompatSharedTabView(key(it), futaber, it - 1, 5L) })
            first.closeTabs(setOf(key(2)), now, emptyMap(), closedBy = ftutter)
            (10..32).forEach { first.openTab(tab(it), null); first.closeTabs(setOf(key(it)), now + it, emptyMap(), closedBy = ExperienceProfile.FUTACHA.persistedValue) }
            assertEquals(listOf(key(1), key(3)), first.tabViews.first().sortedBy { it.ordinal }.map { it.tabKey })

            val second = IosCompatibilityStore(fs, nowMillis = { now + 100 })
            second.initialize()
            assertEquals(20, second.countClosedTabBatches(ExperienceProfile.FUTACHA.persistedValue, now + 100))
            // Tab 2's record is the 24th newest and went: the 20 newest are kept (23 later closes).
            assertNull(second.popClosedTabBatch(futaber, now + 100))
            assertNotNull(second.popClosedTabBatch(ExperienceProfile.FUTACHA.persistedValue, now + 100))
            assertEquals(19, second.countClosedTabBatches(ExperienceProfile.FUTACHA.persistedValue, now + 100))
        } finally { fs.deleteRecursively("compatibility").getOrThrow() }
    }

    @Test fun aRestoreBringsBackTheMarkAndATabFtutterClosedWithoutItsStar() = runBlocking {
        val fs = createFileSystem()
        fs.deleteRecursively("compatibility").getOrThrow()
        try {
            val first = IosCompatibilityStore(fs, nowMillis = { now })
            first.initialize()
            first.upsertBoard(CompatBoard(boardKey, "may", board, board, 0))
            (1..3).forEach { first.openTab(tab(it, favorite = it == 2), null) }
            first.replaceTabViews(futaber, (1..3).map { CompatSharedTabView(key(it), futaber, it - 1, 5L) })
            first.closeTabs(setOf(key(2)), now, emptyMap(), closedBy = ftutter)

            val second = IosCompatibilityStore(fs, nowMillis = { now + 60_000 })
            second.initialize()
            assertNull(second.loadPendingClosedTabs(now + 60_000), "the notice ended")
            assertNotNull(second.popClosedTabBatch(futaber, now + 60_000), "ふたばー風 showed the tab")
            assertEquals(listOf(key(1), key(2), key(3)), second.loadTabViews(futaber).map { it.tabKey })
            assertFalse(second.tabs.first().single { it.key == key(2) }.favorite)
            assertEquals(0, second.countClosedTabBatches(futaber, now + 60_000))
        } finally { fs.deleteRecursively("compatibility").getOrThrow() }
    }
}
