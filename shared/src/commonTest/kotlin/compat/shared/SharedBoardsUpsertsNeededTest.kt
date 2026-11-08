package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatBoard
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedBoardsUpsertsNeededTest {
    private fun board(key: String, order: Int, name: String = key, url: String = "https://may.2chan.net/$key/") =
        CompatBoard(key = key, name = name, canonicalUrl = url, originalUrl = url, sortOrder = order)

    @Test
    fun anUnchangedListIsNotWrittenAgain() {
        val stored = listOf(board("b", 0), board("id", 1))
        assertFalse(sharedBoardsUpsertsNeeded(stored, listOf(board("b", 0), board("id", 1))))
        assertFalse(sharedBoardsUpsertsNeeded(stored, emptyList()))
    }

    @Test
    fun aNewRenamedMovedOrReorderedBoardIsWritten() {
        val stored = listOf(board("b", 0), board("id", 1))
        assertTrue(sharedBoardsUpsertsNeeded(stored, listOf(board("b", 0), board("id", 1), board("jun", 2))))
        assertTrue(sharedBoardsUpsertsNeeded(stored, listOf(board("b", 0, name = "虹裏"), board("id", 1))))
        assertTrue(sharedBoardsUpsertsNeeded(stored, listOf(board("b", 0, url = "https://img.2chan.net/b/"), board("id", 1))))
        assertTrue(sharedBoardsUpsertsNeeded(stored, listOf(board("id", 0), board("b", 1))))
    }
}
