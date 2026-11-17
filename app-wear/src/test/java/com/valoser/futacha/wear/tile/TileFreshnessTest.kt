package com.valoser.futacha.wear.tile

import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_STALE_AGE_MILLIS
import com.valoser.futacha.shared.watch.WatchReadAloudPlaybackState
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchThreadSummary
import com.valoser.futacha.wear.live.ReadAloudLiveUpdateNotifier
import org.junit.Assert.assertEquals
import org.junit.Test

class TileFreshnessTest {
    private val now = 1_000_000_000L

    @Test
    fun freshSnapshotRefreshesWhenItBecomesStale() {
        val snapshot = snapshot(generatedAt = now - 10 * 60_000L)
        assertEquals(
            now - 10 * 60_000L + WATCH_SNAPSHOT_STALE_AGE_MILLIS - now + 1_000L,
            tileFreshnessIntervalMillis(snapshot, now)
        )
    }

    @Test
    fun readAloudStatusExpiryComesFirst() {
        val snapshot = snapshot(generatedAt = now, readAloudUpdatedAt = now - 5 * 60_000L)
        assertEquals(
            now - 5 * 60_000L + WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS - now + 1_000L,
            tileFreshnessIntervalMillis(snapshot, now)
        )
    }

    @Test
    fun nothingToExpireMeansNoAutomaticRefresh() {
        assertEquals(0L, tileFreshnessIntervalMillis(null, now))
        val stale = snapshot(generatedAt = now - WATCH_SNAPSHOT_STALE_AGE_MILLIS - 1L)
        assertEquals(0L, tileFreshnessIntervalMillis(stale, now))
    }

    @Test
    fun intervalIsNeverBelowTheSystemMinimum() {
        val snapshot = snapshot(generatedAt = now - WATCH_SNAPSHOT_STALE_AGE_MILLIS + 10L)
        assertEquals(TILE_MIN_FRESHNESS_INTERVAL_MILLIS, tileFreshnessIntervalMillis(snapshot, now))
    }

    @Test
    fun readAloudNotificationTimesOutWhenTheStatusExpires() {
        val status = status(updatedAt = now - 4 * 60_000L)
        assertEquals(
            WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS - 4 * 60_000L,
            ReadAloudLiveUpdateNotifier.readAloudNotificationTimeoutMillis(status, now)
        )
        // A clock ahead of the phone never extends the notification.
        assertEquals(
            WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS,
            ReadAloudLiveUpdateNotifier.readAloudNotificationTimeoutMillis(status(updatedAt = now + 60_000L), now)
        )
    }

    private fun snapshot(generatedAt: Long, readAloudUpdatedAt: Long? = null) = WatchSnapshot(
        generatedAtMillis = generatedAt,
        boards = emptyList(),
        threads = listOf(
            WatchThreadSummary(
                threadId = "1",
                boardId = "b",
                boardName = "b",
                boardUrl = "https://may.2chan.net/b/",
                title = "t",
                thumbnailUrl = null,
                replyCount = 1,
                previousReplyCount = null,
                newReplyCount = 0,
                lastVisitedEpochMillis = 0L,
                isWatchWordMatch = false,
                previewPosts = emptyList(),
                readAloudStatus = readAloudUpdatedAt?.let(::status)
            )
        ),
        watchWords = emptyList(),
        unreadTotal = 0,
        watchMatchTotal = 0
    )

    private fun status(updatedAt: Long) = WatchReadAloudStatus(
        boardId = "b",
        boardUrl = "https://may.2chan.net/b/",
        threadId = "1",
        state = WatchReadAloudPlaybackState.Speaking,
        postId = null,
        currentIndex = 0,
        totalPosts = 3,
        updatedAtMillis = updatedAt
    )
}
