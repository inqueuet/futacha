package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FutaberBoardEditSupportTest {
    private fun board(id: String, url: String) = BoardSummary(id, "板$id", "", url, "")
    private val boards = listOf(
        board("a", "https://may.2chan.net/b/futaba.php"),
        board("b", "https://img.2chan.net/b/futaba.php"),
        board("c", "https://dat.2chan.net/b/futaba.php")
    )

    @Test
    fun anAddressAloneIsEnoughAndNamesTheBoardAfterIt() {
        val state = futaberAddBoardState("", "https://zip.2chan.net/1/", boards)
        assertTrue(state.canSubmit)
        assertEquals("zip.2chan.net/1", state.resolvedName)
        assertNull(state.helperText)
        assertEquals("私の板", futaberAddBoardState(" 私の板 ", "https://zip.2chan.net/1/", boards).resolvedName)
    }

    @Test
    fun emptyOrBadAddressesAreRejectedWithAReason() {
        assertFalse(futaberAddBoardState("", "", boards).canSubmit)
        assertNull(futaberAddBoardState("", "", boards).helperText)
        val noScheme = futaberAddBoardState("", "zip.2chan.net/1/", boards)
        assertFalse(noScheme.canSubmit)
        assertNotNull(noScheme.helperText)
        assertFalse(futaberAddBoardState("", "https://", boards).canSubmit)
    }

    @Test
    fun aBoardAlreadyRegisteredIsRejectedEvenWithADifferentSpelling() {
        val duplicate = futaberAddBoardState("", "https://may.2chan.net/b/", boards)
        assertFalse(duplicate.canSubmit)
        assertEquals("同じURLの板が既に登録されています", duplicate.helperText)
        assertSame(boards, futaberAddBoard(boards, "", "https://MAY.2chan.net/b/"))
    }

    @Test
    fun addingAppendsOneBoardWithAFreshIdAndNormalisedUrl() {
        val result = futaberAddBoard(boards, "", "https://zip.2chan.net/1/")
        assertEquals(boards.size + 1, result.size)
        assertEquals(boards, result.take(boards.size))
        val added = result.last()
        assertEquals("https://zip.2chan.net/1/futaba.php", added.url)
        assertEquals("zip.2chan.net/1", added.name)
        assertFalse(added.pinned)
        assertEquals(result.size, result.map { it.id }.toSet().size)
    }

    @Test
    fun invalidInputLeavesTheLatestListUntouched() {
        assertSame(boards, futaberAddBoard(boards, "", "not a url"))
        assertSame(boards, futaberAddBoard(boards, "", "  "))
    }

    @Test
    fun deletingRemovesOnlyThatBoard() {
        assertEquals(listOf("a", "c"), futaberDeleteBoard(boards, "b").map { it.id })
        assertEquals(boards, futaberDeleteBoard(boards, "missing"))
    }

    @Test
    fun movingStepsOneSlotAndStopsAtTheEnds() {
        assertEquals(listOf("b", "a", "c"), futaberMoveBoard(boards, "b", up = true).map { it.id })
        assertEquals(listOf("a", "c", "b"), futaberMoveBoard(boards, "b", up = false).map { it.id })
        assertEquals(boards, futaberMoveBoard(boards, "a", up = true))
        assertEquals(boards, futaberMoveBoard(boards, "c", up = false))
        // A board deleted while the list was shown is not resurrected.
        assertEquals(boards, futaberMoveBoard(boards, "gone", up = true))
    }
}
