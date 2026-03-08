package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryScrollSupportTest {
    @Test
    fun shouldSkipHistoryScrollUpdate_skipsTinyOffsetChangesInSameItem() {
        val existing = historyEntry(
            lastVisitedEpochMillis = 10_000L,
            lastReadItemIndex = 5,
            lastReadItemOffset = 100
        )

        assertTrue(
            shouldSkipHistoryScrollUpdate(
                existingEntry = existing,
                index = 5,
                offset = 110,
                nowMillis = 11_000L
            )
        )
    }

    @Test
    fun shouldSkipHistoryScrollUpdate_skipsFrequentNearbyScrollUpdates() {
        val existing = historyEntry(
            lastVisitedEpochMillis = 10_000L,
            lastReadItemIndex = 5,
            lastReadItemOffset = 100
        )

        assertTrue(
            shouldSkipHistoryScrollUpdate(
                existingEntry = existing,
                index = 7,
                offset = 150,
                nowMillis = 11_000L
            )
        )
        assertFalse(
            shouldSkipHistoryScrollUpdate(
                existingEntry = existing,
                index = 9,
                offset = 400,
                nowMillis = 11_000L
            )
        )
    }

    @Test
    fun applyHistoryScrollUpdate_updatesVisitedTimeOnlyWhenNeeded() {
        val existing = historyEntry(
            lastVisitedEpochMillis = 10_000L,
            lastReadItemIndex = 5,
            lastReadItemOffset = 100
        )

        val noVisitedUpdate = applyHistoryScrollUpdate(
            entry = existing,
            index = 5,
            offset = 110,
            nowMillis = 20_000L
        )
        assertEquals(10_000L, noVisitedUpdate.lastVisitedEpochMillis)

        val visitedUpdate = applyHistoryScrollUpdate(
            entry = existing,
            index = 6,
            offset = 100,
            nowMillis = 20_000L
        )
        assertEquals(20_000L, visitedUpdate.lastVisitedEpochMillis)
    }

    @Test
    fun buildNewHistoryScrollEntry_createsHistoryEntryAtCurrentPosition() {
        val created = buildNewHistoryScrollEntry(
            threadId = "123",
            index = 4,
            offset = 88,
            boardId = "b",
            title = "title",
            titleImageUrl = "thumb",
            boardName = "board",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            replyCount = 42,
            nowMillis = 99L
        )

        assertEquals("123", created.threadId)
        assertEquals(4, created.lastReadItemIndex)
        assertEquals(88, created.lastReadItemOffset)
        assertEquals(99L, created.lastVisitedEpochMillis)
        assertEquals(42, created.replyCount)
    }

    @Test
    fun buildHistoryScrollJobKey_prefersBoardId_thenBoardUrl_thenThreadId() {
        assertEquals("b::123", buildHistoryScrollJobKey("123", "b", "https://may.2chan.net/b/futaba.php"))
        assertEquals("https://may.2chan.net/b::123", buildHistoryScrollJobKey("123", "", "https://may.2chan.net/b/"))
        assertEquals("123", buildHistoryScrollJobKey("123", "", ""))
    }

    private fun historyEntry(
        lastVisitedEpochMillis: Long,
        lastReadItemIndex: Int,
        lastReadItemOffset: Int
    ): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = "123",
            boardId = "b",
            title = "title",
            titleImageUrl = "thumb",
            boardName = "board",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            lastVisitedEpochMillis = lastVisitedEpochMillis,
            replyCount = 10,
            lastReadItemIndex = lastReadItemIndex,
            lastReadItemOffset = lastReadItemOffset
        )
    }
}
