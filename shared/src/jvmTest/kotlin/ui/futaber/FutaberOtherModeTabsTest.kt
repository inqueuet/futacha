package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** D-B4 (J1): the tabs of the other modes, listed read only in the manage panel. */
class FutaberOtherModeTabsTest {
    private val may = BoardSummary("may-b", "二次元裏", "", "https://may.2chan.net/b/futaba.php", "")
    private fun tab(id: String, favorite: Boolean = false, board: String = "https://may.2chan.net/b/"): CompatTab {
        val url = "${board}res/$id.htm"
        return CompatTab(compatTabKey(url), url, url, compatBoardKey(board), "二次元裏", id, "題$id", favorite = favorite, replyCount = 3, insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L)
    }

    @Test
    fun ownAndStarredTabsAreLeftOutAndTheRestOpenHere() {
        val own = setOf(tab("1").key)
        val rows = futaberOtherModeOpenTabs(listOf(tab("1"), tab("2"), tab("3", favorite = true), tab("4", board = "https://img.2chan.net/b/")), listOf(may), own)
        assertEquals(listOf("2", "4"), rows.map { it.tab.threadNo })
        // A registered board opens here; one that is not stays addable but has no thread to open yet.
        assertEquals("2", rows[0].ref?.threadId)
        assertNull(rows[1].ref)
    }
}

/** D-B4 (J2): a star another mode takes off is taken off here. */
class FutaberSharedStarChangesTest {
    private fun tab(id: String, favorite: Boolean): CompatTab {
        val url = "https://may.2chan.net/b/res/$id.htm"
        return CompatTab(compatTabKey(url), url, url, compatBoardKey("https://may.2chan.net/b/"), "二次元裏", id, "題$id", favorite = favorite, insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L)
    }

    @Test
    fun aStarSeenAndThenGoneIsUnstarredButAStoreNotLoadedYetRemovesNothing() {
        val a = tab("1", true).key
        val b = tab("2", true).key
        val mirrored = setOf(a, b)
        // Nothing seen yet and no tabs: nothing is removed.
        assertEquals(emptySet(), futaberSharedStarChanges(emptySet(), emptyList(), mirrored).unstarred)
        val first = futaberSharedStarChanges(emptySet(), listOf(tab("1", true), tab("2", true)), mirrored)
        assertEquals(setOf(a, b), first.seen)
        // Another mode took the star off 1 and closed 2.
        val next = futaberSharedStarChanges(first.seen, listOf(tab("1", false)), mirrored)
        assertEquals(setOf(a, b), next.unstarred)
        assertEquals(emptySet(), next.seen)
        // A star this mode took off itself (no longer mirrored) is not followed.
        assertEquals(emptySet(), futaberSharedStarChanges(first.seen, emptyList(), emptySet()).unstarred)
    }
}
