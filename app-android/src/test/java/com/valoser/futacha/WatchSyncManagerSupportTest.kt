package com.valoser.futacha

import com.valoser.futacha.shared.watch.WatchReadAloudPlaybackState
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchSyncManagerSupportTest {
    @Test
    fun resolveWatchRefreshRequestDecision_startsWhenNoPriorRefreshExists() {
        assertEquals(
            WatchRefreshRequestDecision.StartRefresh,
            resolveWatchRefreshRequestDecision(
                isRefreshInFlight = false,
                lastRefreshStartedElapsedMillis = 0L,
                nowElapsedMillis = 1_000L,
                minIntervalMillis = 120_000L
            )
        )
    }

    @Test
    fun resolveWatchRefreshRequestDecision_skipsWhileRefreshIsInFlight() {
        assertEquals(
            WatchRefreshRequestDecision.RequestSnapshotOnly,
            resolveWatchRefreshRequestDecision(
                isRefreshInFlight = true,
                lastRefreshStartedElapsedMillis = 1_000L,
                nowElapsedMillis = 200_000L,
                minIntervalMillis = 120_000L
            )
        )
    }

    @Test
    fun resolveWatchRefreshRequestDecision_throttlesRecentRefreshAttempts() {
        assertEquals(
            WatchRefreshRequestDecision.RequestSnapshotOnly,
            resolveWatchRefreshRequestDecision(
                isRefreshInFlight = false,
                lastRefreshStartedElapsedMillis = 1_000L,
                nowElapsedMillis = 120_999L,
                minIntervalMillis = 120_000L
            )
        )
        assertEquals(
            WatchRefreshRequestDecision.StartRefresh,
            resolveWatchRefreshRequestDecision(
                isRefreshInFlight = false,
                lastRefreshStartedElapsedMillis = 1_000L,
                nowElapsedMillis = 121_000L,
                minIntervalMillis = 120_000L
            )
        )
    }

    @Test
    fun resolveWatchRefreshRequestDecision_skipsWhenElapsedClockMovesBackward() {
        assertEquals(
            WatchRefreshRequestDecision.RequestSnapshotOnly,
            resolveWatchRefreshRequestDecision(
                isRefreshInFlight = false,
                lastRefreshStartedElapsedMillis = 2_000L,
                nowElapsedMillis = 1_000L,
                minIntervalMillis = 120_000L
            )
        )
    }

    @Test
    fun shouldLoadWatchPreviewThreadPages_requiresRequestAndExpiredSuppressionWindow() {
        assertEquals(
            false,
            shouldLoadWatchPreviewThreadPages(
                includePreviewThreadPages = false,
                previewSuppressedUntilElapsedMillis = 0L,
                nowElapsedMillis = 10_000L
            )
        )
        assertEquals(
            false,
            shouldLoadWatchPreviewThreadPages(
                includePreviewThreadPages = true,
                previewSuppressedUntilElapsedMillis = 20_000L,
                nowElapsedMillis = 19_999L
            )
        )
        assertEquals(
            true,
            shouldLoadWatchPreviewThreadPages(
                includePreviewThreadPages = true,
                previewSuppressedUntilElapsedMillis = 20_000L,
                nowElapsedMillis = 20_000L
            )
        )
    }

    @Test
    fun shouldSendWatchReadAloudStatusUpdate_sendsFirstAndClearUpdatesImmediately() {
        assertEquals(
            true,
            shouldSendWatchReadAloudStatusUpdate(
                status = readAloudStatus(state = WatchReadAloudPlaybackState.Speaking),
                lastSentStatus = null,
                lastSentElapsedMillis = 0L,
                nowElapsedMillis = 1_000L,
                minIntervalMillis = 5_000L
            )
        )
        assertEquals(
            true,
            shouldSendWatchReadAloudStatusUpdate(
                status = null,
                lastSentStatus = readAloudStatus(state = WatchReadAloudPlaybackState.Speaking),
                lastSentElapsedMillis = 1_000L,
                nowElapsedMillis = 1_100L,
                minIntervalMillis = 5_000L
            )
        )
    }

    @Test
    fun shouldSendWatchReadAloudStatusUpdate_throttlesProgressUpdates() {
        assertEquals(
            false,
            shouldSendWatchReadAloudStatusUpdate(
                status = readAloudStatus(
                    state = WatchReadAloudPlaybackState.Speaking,
                    currentIndex = 2
                ),
                lastSentStatus = readAloudStatus(
                    state = WatchReadAloudPlaybackState.Speaking,
                    currentIndex = 1
                ),
                lastSentElapsedMillis = 1_000L,
                nowElapsedMillis = 5_999L,
                minIntervalMillis = 5_000L
            )
        )
        assertEquals(
            true,
            shouldSendWatchReadAloudStatusUpdate(
                status = readAloudStatus(
                    state = WatchReadAloudPlaybackState.Speaking,
                    currentIndex = 2
                ),
                lastSentStatus = readAloudStatus(
                    state = WatchReadAloudPlaybackState.Speaking,
                    currentIndex = 1
                ),
                lastSentElapsedMillis = 1_000L,
                nowElapsedMillis = 6_000L,
                minIntervalMillis = 5_000L
            )
        )
    }

    @Test
    fun shouldSendWatchReadAloudStatusUpdate_sendsPlaybackStateChangesImmediately() {
        assertEquals(
            true,
            shouldSendWatchReadAloudStatusUpdate(
                status = readAloudStatus(state = WatchReadAloudPlaybackState.Paused),
                lastSentStatus = readAloudStatus(state = WatchReadAloudPlaybackState.Speaking),
                lastSentElapsedMillis = 1_000L,
                nowElapsedMillis = 1_100L,
                minIntervalMillis = 5_000L
            )
        )
    }

    private fun readAloudStatus(
        state: WatchReadAloudPlaybackState,
        currentIndex: Int = 1
    ): WatchReadAloudStatus = WatchReadAloudStatus(
        boardId = "b",
        boardUrl = "https://may.2chan.net/b/",
        threadId = "100",
        state = state,
        postId = currentIndex.toString(),
        currentIndex = currentIndex,
        totalPosts = 10,
        updatedAtMillis = currentIndex.toLong()
    )
}
