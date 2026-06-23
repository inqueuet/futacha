package com.valoser.futacha

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
}
