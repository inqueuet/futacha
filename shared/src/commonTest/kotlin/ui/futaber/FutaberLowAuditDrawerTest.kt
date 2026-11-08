package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Audit B4–B6: one write for a drag, a grip that steps on its own order, unique watch-word keys. */
class FutaberLowAuditDrawerTest {
    private fun board(id: String) = BoardSummary(id = id, name = id, category = "", url = "https://may.2chan.net/$id/futaba.php", description = "")

    @Test fun aDragIsStoredAsOneOrder() {
        val stored = listOf(board("a"), board("b"), board("c"), board("d"))
        assertEquals(listOf("c", "a", "b", "d"), futaberReorderBoards(stored, listOf("c", "a", "b", "d")).map { it.id })
    }

    @Test fun aBoardStoredTwiceKeepsItsSecondCopyInPlace() {
        val stored = listOf(board("a"), board("b"), board("a"), board("c"))
        // Shown once (distinct by id): a, b, c; dragged to c, a, b.
        assertEquals(listOf("c", "a", "a", "b"), futaberReorderBoards(stored, listOf("c", "a", "b")).map { it.id })
    }

    @Test fun anIdNoLongerStoredIsSkipped() {
        val stored = listOf(board("a"), board("b"))
        assertEquals(listOf("b", "a"), futaberReorderBoards(stored, listOf("gone", "b", "a")).map { it.id })
    }

    @Test fun aFastGripDragStepsFromTheOrderItMadeNotFromTheStoredOne() {
        var ids = listOf("1", "2", "3", "4")
        var dragY = 0f
        val row = 100f
        // Three quick moves before any stored list comes back: each step is taken from the drag's own order.
        repeat(3) {
            dragY += 110f
            val (next, step) = futaberPasteboardGripStep(ids, "1", dragY, row)
            assertEquals(1, step)
            dragY -= step * row
            ids = next
        }
        assertEquals(listOf("2", "3", "4", "1"), ids)
        assertEquals(0, futaberPasteboardGripStep(ids, "1", 80f, row).second)
    }

    @Test fun aRowOnTheTopLevelMovesAmongRowsAndAFolderAmongFolders() {
        val folder = FutaberPasteItem(id = "f", folderName = "F")
        val text = FutaberPasteItem(id = "t", text = "本文")
        val rows = listOf(folder, text)
        assertEquals(listOf("t"), futaberPasteboardDragLevel(rows, text))
        assertEquals(listOf("f"), futaberPasteboardDragLevel(rows, folder))
    }

    @Test fun twoSectionsOfOneBoardGiveDifferentRowKeys() {
        val keys = listOf(futaberWatchWordRowKey(0, "may/b", 3), futaberWatchWordRowKey(1, "may/b", 3))
        assertTrue(keys.distinct().size == 2)
    }
}
