package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadViewedSnapshot
import com.valoser.futacha.shared.state.mergeAppStateHistoryMetadataEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadReadBaselineRegressionTest {
    private fun entry() = ThreadHistoryEntry("100", "may", "題名", "", "板", "https://may.2chan.net/b/", 1, 500,
        lastConfirmedAliveEpochMillis = 50)
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "本文",
        imageUrl = null, thumbnailUrl = null)
    private val viewed = ThreadViewedSnapshot(4, "104", 10)

    @Test fun theBackgroundReplyCountDoesNotReplaceTheLastViewedCount() {
        assertEquals(4, knownViewedPostCount(entry().copy(lastViewedSnapshot = viewed)))
    }

    @Test fun anAliveProbeAndAnOldScrollAnchorCannotEstablishAViewedCount() {
        assertNull(knownViewedPostCount(entry()))
        assertNull(knownViewedPostCount(entry().copy(lastReadPostId = "102", lastReadItemIndex = 2)))
    }

    @Test fun newerMetadataWithoutAViewMarkerKeepsTheLastForegroundRead() {
        val before = entry().copy(lastViewedSnapshot = viewed)
        val incoming = entry().copy(lastVisitedEpochMillis = 1_000, replyCount = 900)
        assertEquals(viewed, mergeAppStateHistoryMetadataEntry(before, incoming).lastViewedSnapshot)
    }

    @Test fun anOlderImportedViewCannotRollBackTheForegroundReadMarker() {
        val before = entry().copy(lastViewedSnapshot = viewed)
        val incoming = entry().copy(lastVisitedEpochMillis = 1_000, lastViewedSnapshot = viewed.copy(postCount = 2, viewedAtEpochMillis = 5))
        assertEquals(viewed, mergeAppStateHistoryMetadataEntry(before, incoming).lastViewedSnapshot)
    }

    @Test fun aShorterCompleteResponseDoesNotAnnounceLateOnAManualRefresh() {
        val tracker = ThreadOpeningSummaryTracker(4)
        assertNull(tracker.accept(listOf(post("101"), post("102")), emptySet()))
        assertNull(tracker.accept((101..105).map { post("$it") }, emptySet()))
    }

    @Test fun deletedOlderPostsDoNotHideNewRepliesFromTheOpeningSummary() {
        val tracker = ThreadOpeningSummaryTracker(4, "104")
        assertEquals(ThreadOpeningSummary(1, null), tracker.accept(listOf(post("101"), post("103"), post("105")), emptySet()))
    }

    @Test fun deletedOlderPostsDoNotHideNewRepliesFromTheNewPostBoundary() {
        val tracker = ThreadNewPostTracker(4, "104")
        tracker.onPostsLoaded(listOf(post("101"), post("103"), post("105")))
        assertEquals(setOf("105"), tracker.newPostIds)
    }
}
