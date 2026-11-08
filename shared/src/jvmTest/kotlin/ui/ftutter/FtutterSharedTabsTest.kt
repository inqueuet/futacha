package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.futaber.futaberApplySharedTabs
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-B5: this mode's strip as shared tabs, leaving the other modes' selected tab alone. */
class FtutterSharedTabsTest {
    private val may = BoardSummary("may-b", "二次元裏", "", "https://may.2chan.net/b/futaba.php", "")
    private val other = BoardSummary("x", "別", "", "https://example.org/x/futaba.php", "")
    private fun entry(id: String, posts: Int) = ThreadHistoryEntry(
        threadId = id, boardId = "may-b", title = "題$id", titleImageUrl = "", boardName = "二次元裏",
        boardUrl = "https://may.2chan.net/b/res/$id.htm", lastVisitedEpochMillis = 1, replyCount = posts
    )

    @Test
    fun theStripKeepsItsOrderAndSkipsThreadsOfBoardsThatAreNotOfficial() {
        val plan = ftutterSharedTabsPlan(
            listOf("https://may.2chan.net/b/res/2.htm", "https://example.org/x/res/5.htm", "https://may.2chan.net/b/res/1.htm"),
            listOf(may, other), listOf(entry("1", 6), entry("2", 3)), 100L
        )
        assertEquals(listOf("2", "1"), plan.tabs.map { it.threadNo })
        assertEquals(listOf(0, 1), plan.views.map { it.ordinal })
        assertEquals(setOf("ftutter"), plan.views.map { it.profile }.toSet())
        assertEquals(listOf(2, 5), plan.tabs.map { it.replyCount })
        assertTrue(plan.favoriteKeys.isEmpty())
    }

    @Test
    fun applyingMarksOnlyThisModesViewsAndDoesNotMoveTheSelection() = runBlocking {
        val root = Files.createTempDirectory("ftutter-shared-tabs").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.importModernBoards(listOf(may))
            val plan = ftutterSharedTabsPlan(listOf("https://may.2chan.net/b/res/1.htm"), listOf(may), listOf(entry("1", 6)), 100L)
            val before = store.workspace.first().activeTabKey
            futaberApplySharedTabs(store, plan, listOf(may), emptySet(), FTUTTER_TAB_PROFILE)
            assertEquals(1, store.loadTabViews("ftutter").size)
            assertTrue(store.loadTabViews("futaber").isEmpty())
            assertEquals(before, store.workspace.first().activeTabKey)
            assertEquals(compatTabKey("https://may.2chan.net/b/res/1.htm"), store.tabs.first().single().key)
            assertEquals(compatBoardKey("https://may.2chan.net/b/"), store.tabs.first().single().boardKey)
        } finally { store.close(); root.deleteRecursively() }
    }
}
