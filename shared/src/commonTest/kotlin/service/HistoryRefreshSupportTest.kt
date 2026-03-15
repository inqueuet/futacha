package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.NetworkException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HistoryRefreshSupportTest {
    @Test
    fun buildHistoryBoardKey_prefersExplicitBoardId() {
        val board = BoardSummary(
            id = "may-b",
            name = "may/b",
            category = "",
            url = "https://may.2chan.net/b/futaba.php",
            description = ""
        )
        val entry = ThreadHistoryEntry(
            threadId = "123",
            boardId = "manual-id",
            title = "title",
            titleImageUrl = "",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b/res/123.htm",
            lastVisitedEpochMillis = 1L,
            replyCount = 10
        )

        val key = buildHistoryBoardKey(
            entry = entry,
            boardById = mapOf(board.id to board),
            boardByBaseUrl = mapOf(normalizeHistoryBoardKey(board.url)!! to board)
        )

        assertEquals("manual-id", key)
    }

    @Test
    fun buildHistoryBoardKey_prefersResolvedBoardId() {
        val board = BoardSummary(
            id = "b",
            name = "may/b",
            category = "",
            url = "https://may.2chan.net/b/futaba.php",
            description = ""
        )
        val entry = ThreadHistoryEntry(
            threadId = "123",
            boardId = "",
            title = "title",
            titleImageUrl = "",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b/res/123.htm",
            lastVisitedEpochMillis = 1L,
            replyCount = 10
        )

        val key = buildHistoryBoardKey(
            entry = entry,
            boardById = mapOf(board.id to board),
            boardByBaseUrl = mapOf(normalizeHistoryBoardKey(board.url)!! to board)
        )

        assertEquals("b", key)
    }

    @Test
    fun normalizeHistoryBoardKey_normalizesCaseAndTrailingSlash() {
        assertEquals(
            "https://may.2chan.net/b",
            normalizeHistoryBoardKey("HTTPS://MAY.2CHAN.NET/b/")
        )
    }

    @Test
    fun resolveHistoryBoardForEntry_matchesByNormalizedBoardUrl() {
        val board = BoardSummary(
            id = "img",
            name = "img",
            category = "",
            url = "https://dec.2chan.net/50/futaba.php",
            description = ""
        )
        val entry = ThreadHistoryEntry(
            threadId = "555",
            boardId = "",
            title = "title",
            titleImageUrl = "",
            boardName = "img",
            boardUrl = "https://dec.2chan.net/50/res/555.htm",
            lastVisitedEpochMillis = 1L,
            replyCount = 3
        )

        val resolved = resolveHistoryBoardForEntry(
            entry = entry,
            boardById = emptyMap(),
            boardByBaseUrl = mapOf(normalizeHistoryBoardKey(board.url)!! to board)
        )

        assertNotNull(resolved)
        assertEquals(board.id, resolved.id)
    }

    @Test
    fun resolveArchiveBaseUrl_usesFallbackWhenThreadUrlIsInvalid() {
        assertEquals(
            "https://may.2chan.net/b",
            resolveArchiveBaseUrl(
                threadUrl = "not-a-url",
                fallbackBoardUrl = "https://may.2chan.net/b"
            )
        )
    }

    @Test
    fun resolveArchiveBaseUrl_extractsBoardFromThreadUrl() {
        assertEquals(
            "https://may.2chan.net/b",
            resolveArchiveBaseUrl(
                threadUrl = "https://may.2chan.net/b/res/123456789.htm",
                fallbackBoardUrl = null
            )
        )
    }

    @Test
    fun normalizeHistoryArchiveQuery_collapsesWhitespaceAndRespectsMaxLength() {
        assertEquals("abc def", normalizeHistoryArchiveQuery("  abc   def  ", 16))
        assertEquals("abc d", normalizeHistoryArchiveQuery("abc   def", 5))
        assertEquals("", normalizeHistoryArchiveQuery("   ", 10))
    }

    @Test
    fun selectHistoryRefreshWindow_wrapsAndAdvancesCursor() {
        val history = listOf(
            historyEntry(threadId = "1"),
            historyEntry(threadId = "2"),
            historyEntry(threadId = "3"),
            historyEntry(threadId = "4")
        )

        val selection = selectHistoryRefreshWindow(
            history = history,
            maxThreadsPerRun = 2,
            cursor = 3
        )

        assertEquals(listOf("4", "1"), selection.entries.map { it.threadId })
        assertEquals(1, selection.nextCursor)
    }

    @Test
    fun selectHistoryRefreshWindow_keepsCursorWhenLimitCoversAllEntries() {
        val history = listOf(historyEntry(threadId = "1"), historyEntry(threadId = "2"))

        val selection = selectHistoryRefreshWindow(
            history = history,
            maxThreadsPerRun = 10,
            cursor = 5
        )

        assertEquals(listOf("1", "2"), selection.entries.map { it.threadId })
        assertEquals(5, selection.nextCursor)
    }

    @Test
    fun historyRefreshError_helpers_detectAbortAndNotFound() {
        assertTrue(
            isHistoryRefreshAbortSignal(
                NetworkException("Aborting history refresh due to persistent failures"),
                "Aborting history refresh due to persistent"
            )
        )
        assertFalse(
            isHistoryRefreshAbortSignal(
                IllegalStateException("other"),
                "Aborting history refresh due to persistent"
            )
        )
        assertTrue(isHistoryRefreshNotFound(NetworkException("gone", statusCode = 404)))
        assertTrue(isHistoryRefreshNotFound(NetworkException("deleted", statusCode = 410)))
        assertFalse(isHistoryRefreshNotFound(NetworkException("server", statusCode = 500)))
    }

    @Test
    fun buildHistoryRefreshError_countsStagesAndLimitsStoredErrors() {
        val details = buildList {
            repeat(12) { index ->
                add(
                    HistoryRefresher.ErrorDetail(
                        threadId = index.toString(),
                        message = "error-$index",
                        stage = if (index % 2 == 0) "thread_refresh" else "archive_lookup"
                    )
                )
            }
        }

        val error = buildHistoryRefreshError(totalThreads = 99, details = details)

        assertEquals(12, error.errorCount)
        assertEquals(99, error.totalThreads)
        assertEquals(10, error.errors.size)
        assertEquals(6, error.stageCounts["thread_refresh"])
        assertEquals(6, error.stageCounts["archive_lookup"])
    }

    private fun historyEntry(threadId: String): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = "b",
            title = "title-$threadId",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://may.2chan.net/b/res/$threadId.htm",
            lastVisitedEpochMillis = 1L,
            replyCount = 1
        )
    }
}
