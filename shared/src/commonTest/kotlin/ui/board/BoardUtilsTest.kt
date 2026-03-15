package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BoardUtilsTest {
    @Test
    fun buildHistoryEntryFromPageAt_updatesExistingEntryAndPreservesExistingImage() {
        val board = BoardSummary(
            id = "b",
            name = "二次元裏",
            category = "",
            url = "https://may.2chan.net/b/futaba.php",
            description = "",
            pinned = false
        )
        val existing = ThreadHistoryEntry(
            threadId = "123",
            boardId = "b",
            title = "old",
            titleImageUrl = "https://example.com/existing.jpg",
            boardName = "old-board",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            lastVisitedEpochMillis = 1L,
            replyCount = 1
        )
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "123",
                    author = "name",
                    subject = "new title",
                    timestamp = "24/01/01(月)00:00:00",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = "https://example.com/new.jpg"
                )
            )
        )

        val updated = buildHistoryEntryFromPageAt(
            page = page,
            history = listOf(existing),
            threadId = "123",
            threadTitle = null,
            board = board,
            timestampMillis = 999L
        )

        assertEquals("body", updated.title)
        assertEquals("https://example.com/existing.jpg", updated.titleImageUrl)
        assertEquals("二次元裏", updated.boardName)
        assertEquals(999L, updated.lastVisitedEpochMillis)
        assertEquals(1, updated.replyCount)
    }

    @Test
    fun buildHistoryEntryFromPageAt_createsNewEntryWithOverrideUrl() {
        val board = BoardSummary(
            id = "img",
            name = "二次元画像",
            category = "",
            url = "https://dec.2chan.net/50/futaba.php",
            description = "",
            pinned = false
        )
        val page = ThreadPage(
            threadId = "555",
            boardTitle = null,
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "555",
                    author = "name",
                    subject = null,
                    timestamp = "24/01/01(月)00:00:00",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = "https://example.com/thumb.jpg"
                )
            )
        )

        val created = buildHistoryEntryFromPageAt(
            page = page,
            history = emptyList(),
            threadId = "555",
            threadTitle = "fallback",
            board = board,
            overrideThreadUrl = "https://dec.2chan.net/50/res/555.htm",
            timestampMillis = 1234L
        )

        assertEquals("body", created.title)
        assertEquals("https://example.com/thumb.jpg", created.titleImageUrl)
        assertEquals("https://dec.2chan.net/50/res/555.htm", created.boardUrl)
        assertEquals(1234L, created.lastVisitedEpochMillis)
    }

    @Test
    fun resolveEffectiveBoardUrl_returnsFallbackForBlankAndRootUrl() {
        assertEquals(
            "https://may.2chan.net/b/futaba.php",
            resolveEffectiveBoardUrl(
                threadUrlOverride = "   ",
                fallbackBoardUrl = "https://may.2chan.net/b/futaba.php"
            )
        )
        assertEquals(
            "https://may.2chan.net/b/futaba.php",
            resolveEffectiveBoardUrl(
                threadUrlOverride = "https://may.2chan.net/",
                fallbackBoardUrl = "https://may.2chan.net/b/futaba.php"
            )
        )
    }

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
