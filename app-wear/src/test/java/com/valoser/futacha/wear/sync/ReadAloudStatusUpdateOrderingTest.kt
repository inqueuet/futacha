package com.valoser.futacha.wear.sync

import com.valoser.futacha.shared.watch.WatchReadAloudPlaybackState
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import com.valoser.futacha.shared.watch.WatchReadAloudStatusUpdate
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchThreadSummary
import com.valoser.futacha.shared.watch.withReadAloudStatusUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAloudStatusUpdateOrderingTest {
    private val now = 1_000_000_000L
    private val speaking = WatchReadAloudStatusUpdate(status(WatchReadAloudPlaybackState.Speaking, now - 2_000L), now - 2_000L)
    private val stopped = WatchReadAloudStatusUpdate(status = null, updatedAtMillis = now - 1_000L)

    @Test
    fun olderUpdateArrivingAfterANewerStopIsDropped() {
        val ordering = ReadAloudStatusUpdateOrdering()
        val afterStop = ordering.apply(snapshot(), stopped, now)!!
        assertNull(afterStop.threads.single().readAloudStatus)
        // Control: without ordering the late "speaking" copy resurrects the stopped status.
        assertNotNull(afterStop.withReadAloudStatusUpdate(speaking, now).threads.single().readAloudStatus)
        assertNull(ordering.apply(afterStop, speaking, now))
    }

    @Test
    fun newerUpdatesAndDuplicateDeliveriesStillApply() {
        val ordering = ReadAloudStatusUpdateOrdering()
        val afterSpeaking = ordering.apply(snapshot(), speaking, now)!!
        assertEquals(WatchReadAloudPlaybackState.Speaking, afterSpeaking.threads.single().readAloudStatus?.state)
        // The same update also arrives as a Data Layer item.
        assertEquals(afterSpeaking, ordering.apply(afterSpeaking, speaking, now))
        val paused = WatchReadAloudStatusUpdate(status(WatchReadAloudPlaybackState.Paused, now - 500L), now - 500L)
        assertEquals(
            WatchReadAloudPlaybackState.Paused,
            ordering.apply(afterSpeaking, paused, now)!!.threads.single().readAloudStatus?.state
        )
        assertNull(ordering.apply(afterSpeaking, stopped, now))
    }

    @Test
    fun implausiblyFutureWatermarkDoesNotBlockLaterUpdates() {
        val ordering = ReadAloudStatusUpdateOrdering()
        val future = now + 60L * 60_000L
        ordering.apply(snapshot(), WatchReadAloudStatusUpdate(status = null, updatedAtMillis = future), now - 60L * 60_000L)
        // The phone clock was corrected back; the watch clock is now far behind the old watermark.
        assertNotNull(ordering.apply(snapshot(), speaking, now))
    }

    @Test
    fun sequencedUpdatesAreOrderedWithoutAnyClock() {
        val ordering = ReadAloudStatusUpdateOrdering()
        // The phone clock is wildly off (hours behind and jumping back) but the sequence decides.
        val speakingSeq = WatchReadAloudStatusUpdate(status(WatchReadAloudPlaybackState.Speaking, 1_000L), 1_000L, sessionId = 7L, sequence = 1L)
        val stoppedSeq = WatchReadAloudStatusUpdate(status = null, updatedAtMillis = 10L, sessionId = 7L, sequence = 2L)
        val afterStop = ordering.apply(snapshot(), stoppedSeq, now)!!
        assertNull(afterStop.threads.single().readAloudStatus)
        assertNull(ordering.apply(afterStop, speakingSeq, now))
        // Duplicate delivery of the newest update still applies.
        assertNotNull(ordering.apply(afterStop, stoppedSeq, now))
    }

    @Test
    fun newPhoneSessionSupersedesTheOldNumbering() {
        val ordering = ReadAloudStatusUpdateOrdering()
        val old = WatchReadAloudStatusUpdate(status = null, updatedAtMillis = now - 1_000L, sessionId = 7L, sequence = 50L)
        ordering.apply(snapshot(), old, now)
        val restarted = WatchReadAloudStatusUpdate(
            status(WatchReadAloudPlaybackState.Speaking, now - 500L), now - 500L, sessionId = 8L, sequence = 1L
        )
        assertNotNull(ordering.apply(snapshot(), restarted, now))
        // Within the new session the numbering restarts at 1 and is enforced again.
        val next = WatchReadAloudStatusUpdate(null, now, sessionId = 8L, sequence = 2L)
        assertNotNull(ordering.apply(snapshot(), next, now))
        assertNull(ordering.apply(snapshot(), restarted, now))
    }

    @Test
    fun legacyUpdatesWithoutSequenceKeepTheTimestampOrdering() {
        val ordering = ReadAloudStatusUpdateOrdering()
        assertNotNull(ordering.apply(snapshot(), stopped, now))
        assertNull(ordering.apply(snapshot(), speaking, now))
    }

    private fun status(state: WatchReadAloudPlaybackState, updatedAt: Long) = WatchReadAloudStatus(
        boardId = "b",
        boardUrl = "https://may.2chan.net/b/",
        threadId = "1",
        state = state,
        postId = "1",
        currentIndex = 0,
        totalPosts = 3,
        updatedAtMillis = updatedAt
    )

    private fun snapshot() = WatchSnapshot(
        generatedAtMillis = now - 10_000L,
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
                previewPosts = emptyList()
            )
        ),
        watchWords = emptyList(),
        unreadTotal = 0,
        watchMatchTotal = 0
    )
}
