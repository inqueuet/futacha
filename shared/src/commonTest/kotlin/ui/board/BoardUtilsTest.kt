package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BoardUtilsTest {
    @Test
    fun resolveRegisteredThreadNavigation_returnsTargetForRegisteredBoard() {
        val board = BoardSummary(
            id = "b",
            name = "二次元裏",
            category = "",
            url = "https://may.2chan.net/b/futaba.php",
            description = "",
            pinned = false
        )

        val navigation = resolveRegisteredThreadNavigation(
            url = "https://may.2chan.net/b/res/123456789.htm",
            registeredBoards = listOf(board)
        )

        assertNotNull(navigation)
        assertEquals(board, navigation.board)
        assertEquals("123456789", navigation.threadId)
        assertEquals("https://may.2chan.net/b/res/123456789.htm", navigation.threadUrl)
    }

    @Test
    fun resolveRegisteredThreadNavigation_matchesRegisteredBoardIgnoringScheme() {
        val board = BoardSummary(
            id = "img",
            name = "二次元画像",
            category = "",
            url = "http://dec.2chan.net/50/futaba.php",
            description = "",
            pinned = false
        )

        val navigation = resolveRegisteredThreadNavigation(
            url = "https://dec.2chan.net/50/res/987654321.htm",
            registeredBoards = listOf(board)
        )

        assertNotNull(navigation)
        assertEquals(board.id, navigation.board.id)
        assertEquals("987654321", navigation.threadId)
    }

    @Test
    fun resolveRegisteredThreadNavigation_returnsNullForUnregisteredBoard() {
        val board = BoardSummary(
            id = "b",
            name = "二次元裏",
            category = "",
            url = "https://may.2chan.net/b/futaba.php",
            description = "",
            pinned = false
        )

        val navigation = resolveRegisteredThreadNavigation(
            url = "https://may.2chan.net/img/res/123456789.htm",
            registeredBoards = listOf(board)
        )

        assertNull(navigation)
    }
}
