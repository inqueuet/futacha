package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.NetworkException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HistoryRefreshSupportTest {
    @Test
    fun historyFlushRetryDelayIsBoundedWithoutOverflow() {
        assertEquals(5_000L, calculateHistoryFlushRetryDelay(Long.MAX_VALUE, 5_000L, Int.MAX_VALUE))
        assertEquals(0L, calculateHistoryFlushRetryDelay(-1L, 5_000L, 3))
        assertEquals(800L, calculateHistoryFlushRetryDelay(100L, 5_000L, 3))
    }

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
    fun selectHistoryRefreshWindow_skipsIneligibleEntriesWithoutConsumingLimit() {
        val history = listOf(
            historyEntry(threadId = "1"),
            historyEntry(threadId = "2", isAutoRefreshDisabled = true),
            historyEntry(threadId = "3"),
            historyEntry(threadId = "4"),
            historyEntry(threadId = "5")
        )

        val selection = selectHistoryRefreshWindow(
            history = history,
            maxThreadsPerRun = 3,
            cursor = 1,
            isEligible = { !it.isAutoRefreshDisabled }
        )

        assertEquals(listOf("3", "4", "5"), selection.entries.map { it.threadId })
        assertEquals(0, selection.nextCursor)
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

    private fun historyEntry(
        threadId: String,
        isAutoRefreshDisabled: Boolean = false
    ): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = "b",
            title = "title-$threadId",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://may.2chan.net/b/res/$threadId.htm",
            lastVisitedEpochMillis = 1L,
            replyCount = 1,
            isAutoRefreshDisabled = isAutoRefreshDisabled
        )
    }
}

class HistoryRefreshAutoSaveBudgetTest {
    @Test
    fun mediaBudgetSubtractsLockWaitAndRespectsTheRunDeadline() {
        // No wait: the per-thread timeout minus the finalize reserve, as before.
        assertEquals(70_000L, historyAutoSaveMediaBudgetMillis(1_000L, 1_000L, 90_000L, null))
        // 50 s waiting for the storage lock leaves only 20 s of media work.
        assertEquals(20_000L, historyAutoSaveMediaBudgetMillis(1_000L, 51_000L, 90_000L, null))
        // The run cancels unfinished saves at its deadline.
        assertEquals(10_000L, historyAutoSaveMediaBudgetMillis(1_000L, 1_000L, 90_000L, autoSaveDeadline = 31_000L))
        // Too late for media: still publishes the text promptly.
        assertEquals(1_000L, historyAutoSaveMediaBudgetMillis(1_000L, 80_000L, 90_000L, null))
    }

    @Test
    fun continuationStopsOnlyWhenItMadeNoProgress() {
        val now = 10_000_000L
        val partial = autoSaveGeneration(incomplete = 5)
        assertFalse(shouldContinueAutoSaveGeneration(autoSaveGeneration(incomplete = 0), now))
        assertTrue(shouldContinueAutoSaveGeneration(partial, now))
        // Progress (5 -> 3) keeps continuing.
        val progressed = resolveAutoSaveContinuationStalledAtMillis(partial, 5, 3, now)
        assertEquals(0L, progressed)
        assertTrue(shouldContinueAutoSaveGeneration(autoSaveGeneration(3, progressed), now))
        // No progress (3 -> 3) marks the generation stalled.
        val stalledAt = resolveAutoSaveContinuationStalledAtMillis(autoSaveGeneration(3), 3, 3, now)
        assertEquals(now, stalledAt)
        assertFalse(shouldContinueAutoSaveGeneration(autoSaveGeneration(3, stalledAt), now + 60_000L))
        // Completed: nothing left to continue.
        assertEquals(0L, resolveAutoSaveContinuationStalledAtMillis(autoSaveGeneration(3), 3, 0, now))
    }

    @Test
    fun newReplySaveKeepsAStallUnlessMissingMediaDecreased() {
        val now = 10_000_000L
        val stalled = autoSaveGeneration(incomplete = 3, stalledAt = now - 1_000L)
        // G4-4: a save for new replies no longer resets the stall (2 saves per reply).
        assertEquals(now - 1_000L, resolveAutoSaveContinuationStalledAtMillis(stalled, null, 3, now))
        assertEquals(now - 1_000L, resolveAutoSaveContinuationStalledAtMillis(stalled, null, 4, now))
        assertEquals(0L, resolveAutoSaveContinuationStalledAtMillis(stalled, null, 2, now))
        // Not stalled before: the new generation may be continued once.
        assertEquals(0L, resolveAutoSaveContinuationStalledAtMillis(autoSaveGeneration(3), null, 3, now))
        assertEquals(0L, resolveAutoSaveContinuationStalledAtMillis(null, null, 3, now))
    }

    @Test
    fun stalledContinuationIsRetriedAfterTheRetryInterval() {
        val stalledAt = 10_000_000L
        val stalled = autoSaveGeneration(incomplete = 3, stalledAt = stalledAt)
        assertFalse(shouldContinueAutoSaveGeneration(stalled, stalledAt + AUTO_SAVE_CONTINUATION_RETRY_MILLIS - 1L))
        assertTrue(shouldContinueAutoSaveGeneration(stalled, stalledAt + AUTO_SAVE_CONTINUATION_RETRY_MILLIS))
        // A clock moved back does not keep the stall forever.
        assertTrue(shouldContinueAutoSaveGeneration(stalled, stalledAt - 1L))
        // A new-reply save after the interval that still leaves as much missing stalls again from now.
        val later = stalledAt + AUTO_SAVE_CONTINUATION_RETRY_MILLIS
        assertEquals(later, resolveAutoSaveContinuationStalledAtMillis(stalled, null, 3, later))
    }

    @Test
    fun stallMarkSurvivesTheIndexJson() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val stalled = autoSaveGeneration(incomplete = 3, stalledAt = 42L)
        val decoded = json.decodeFromString(SavedThread.serializer(), json.encodeToString(SavedThread.serializer(), stalled))
        assertEquals(42L, decoded.autoSaveContinuationStalledAtMillis)
        // Indexes written before the field existed read as not stalled.
        val legacy = json.encodeToString(SavedThread.serializer(), autoSaveGeneration(incomplete = 3))
        assertFalse("autoSaveContinuationStalledAtMillis" in legacy)
        assertEquals(0L, json.decodeFromString(SavedThread.serializer(), legacy).autoSaveContinuationStalledAtMillis)
    }

    private fun autoSaveGeneration(incomplete: Int, stalledAt: Long = 0L) = SavedThread(
        threadId = "reg-1",
        boardId = "b",
        boardName = "b",
        title = "t",
        thumbnailPath = null,
        savedAt = 1L,
        postCount = 1,
        imageCount = 0,
        videoCount = 0,
        totalSize = 0L,
        status = if (incomplete > 0) SaveStatus.PARTIAL else SaveStatus.COMPLETED,
        incompleteMediaCount = incomplete,
        autoSaveContinuationStalledAtMillis = stalledAt
    )
}
