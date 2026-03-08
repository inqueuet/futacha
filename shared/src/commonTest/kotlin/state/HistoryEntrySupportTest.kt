package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryEntrySupportTest {
    @Test
    fun mergeAppStateHistoryEntry_keepsExistingReadState_whenIncomingIsOlder() {
        val existing = historyEntry(
            lastVisitedEpochMillis = 200L,
            replyCount = 20,
            lastReadItemIndex = 10,
            lastReadItemOffset = 30,
            hasAutoSave = false
        )
        val incoming = historyEntry(
            boardId = "",
            title = "",
            titleImageUrl = "",
            boardName = "",
            boardUrl = "",
            lastVisitedEpochMillis = 100L,
            replyCount = 15,
            lastReadItemIndex = 2,
            lastReadItemOffset = 4,
            hasAutoSave = true
        )

        val merged = mergeAppStateHistoryEntry(existing, incoming)

        assertEquals("b", merged.boardId)
        assertEquals("title", merged.title)
        assertEquals("thumb", merged.titleImageUrl)
        assertEquals("board", merged.boardName)
        assertEquals("https://may.2chan.net/b/futaba.php", merged.boardUrl)
        assertEquals(200L, merged.lastVisitedEpochMillis)
        assertEquals(20, merged.replyCount)
        assertEquals(10, merged.lastReadItemIndex)
        assertEquals(30, merged.lastReadItemOffset)
        assertTrue(merged.hasAutoSave)
    }

    @Test
    fun mergeAppStateHistoryEntry_usesIncomingReadState_whenIncomingIsNewer() {
        val existing = historyEntry(lastVisitedEpochMillis = 100L, lastReadItemIndex = 1, lastReadItemOffset = 2)
        val incoming = historyEntry(
            lastVisitedEpochMillis = 200L,
            replyCount = 99,
            lastReadItemIndex = 5,
            lastReadItemOffset = 8
        )

        val merged = mergeAppStateHistoryEntry(existing, incoming)

        assertEquals(200L, merged.lastVisitedEpochMillis)
        assertEquals(99, merged.replyCount)
        assertEquals(5, merged.lastReadItemIndex)
        assertEquals(8, merged.lastReadItemOffset)
    }

    @Test
    fun historyEntryIdentity_prefersBoardId_thenNormalizedBoardUrl_thenThreadId() {
        assertEquals(
            "b::123",
            historyEntryIdentity("123", "b", "https://may.2chan.net/b/futaba.php")
        )
        assertEquals(
            "https://may.2chan.net/b::123",
            historyEntryIdentity("123", "", "https://MAY.2CHAN.NET/b/res/123.htm?foo=1")
        )
        assertEquals(
            "123",
            historyEntryIdentity("123", "", "")
        )
    }

    @Test
    fun matchesHistoryEntryIdentity_matchesByBoardIdOrNormalizedBoardUrl() {
        val byBoardId = historyEntry(boardId = "b", boardUrl = "https://may.2chan.net/b/futaba.php")
        assertTrue(matchesHistoryEntryIdentity(byBoardId, "123", "b", "https://other.invalid"))
        assertFalse(matchesHistoryEntryIdentity(byBoardId, "123", "img", "https://may.2chan.net/b/futaba.php"))

        val byUrl = historyEntry(boardId = "", boardUrl = "https://may.2chan.net/b/res/123.htm")
        assertTrue(matchesHistoryEntryIdentity(byUrl, "123", "", "https://MAY.2CHAN.NET/b/futaba.php"))
        assertFalse(matchesHistoryEntryIdentity(byUrl, "123", "", "https://may.2chan.net/img/futaba.php"))
    }

    @Test
    fun normalizeHistoryBoardUrlForIdentity_stripsQueryAndResPath() {
        assertEquals(
            "https://may.2chan.net/b",
            normalizeHistoryBoardUrlForIdentity("https://may.2chan.net/b/res/123.htm?foo=1")
        )
        assertEquals(
            "https://may.2chan.net/b",
            normalizeHistoryBoardUrlForIdentity("https://MAY.2CHAN.NET/b/")
        )
    }

    private fun historyEntry(
        threadId: String = "123",
        boardId: String = "b",
        title: String = "title",
        titleImageUrl: String = "thumb",
        boardName: String = "board",
        boardUrl: String = "https://may.2chan.net/b/futaba.php",
        lastVisitedEpochMillis: Long = 100L,
        replyCount: Int = 10,
        lastReadItemIndex: Int = 3,
        lastReadItemOffset: Int = 7,
        hasAutoSave: Boolean = false
    ): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = boardId,
            title = title,
            titleImageUrl = titleImageUrl,
            boardName = boardName,
            boardUrl = boardUrl,
            lastVisitedEpochMillis = lastVisitedEpochMillis,
            replyCount = replyCount,
            lastReadItemIndex = lastReadItemIndex,
            lastReadItemOffset = lastReadItemOffset,
            hasAutoSave = hasAutoSave
        )
    }
}
