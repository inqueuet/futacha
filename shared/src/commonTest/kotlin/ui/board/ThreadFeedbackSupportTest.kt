package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.test.*

class ThreadFeedbackSupportTest {
    private fun post(id: String, quotes: List<String> = emptyList()) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = "本文$id",
        imageUrl = null, thumbnailUrl = null,
        quoteReferences = quotes.map { QuoteReference(">No.$it", listOf(it)) }
    )
    private fun item(id: String) = CatalogItem(id = id, title = "題名$id", thumbnailUrl = null,
        fullImageUrl = null, threadUrl = "https://may.2chan.net/b/res/$id.htm", replyCount = 0)

    @Test fun pinMovesOnlyTheVisibleTargetAndKeepsTheOtherOrder() {
        val items = listOf(item("1"), item("2"), item("3"), item("4"))
        assertEquals(listOf("3", "1", "2", "4"), catalogWithViewedThreadFirst(items, "3").map { it.id })
    }
    @Test fun pinCannotResurrectFilteredOrMissingThreads() {
        val items = listOf(item("1"), item("3"))
        assertSame(items, catalogWithViewedThreadFirst(items, "2"))
        assertSame(items, catalogWithViewedThreadFirst(items, null))
        assertSame(items, catalogWithViewedThreadFirst(items, "1"))
        assertEquals(emptyList(), catalogWithViewedThreadFirst(emptyList(), "2"))
    }
    @Test fun boardOptInUsesCanonicalKeysAndKeepsBoardsSeparate() {
        assertEquals(viewedThreadFirstKey("https://may.2chan.net/b/"), viewedThreadFirstKey("http://may.2chan.net/b/"))
        assertNotEquals(viewedThreadFirstKey("https://may.2chan.net/b/"), viewedThreadFirstKey("https://img.2chan.net/b/"))
    }
    @Test fun firstVisitDoesNotAnnounceEveryPostAsNew() {
        assertNull(ThreadOpeningSummaryTracker(null).accept(listOf(post("1"), post("2")), emptySet()))
        assertNull(ThreadOpeningSummaryTracker(0).accept(listOf(post("1"), post("2")), emptySet()))
    }
    @Test fun openingCountsDirectRepliesOncePerPost() {
        val tracker = ThreadOpeningSummaryTracker(2)
        val posts = listOf(post("1"), post("2"), post("3", listOf("2", "2")), post("4", listOf("3")), post("5", listOf("2")))
        assertEquals(ThreadOpeningSummary(3, 2), tracker.accept(posts, setOf("2")))
        assertNull(tracker.accept(posts + post("6"), setOf("2")))
    }
    @Test fun hiddenNewPostsAreNotIncludedInEitherCount() {
        assertEquals(ThreadOpeningSummary(1, 0), ThreadOpeningSummaryTracker(1).accept(
            listOf(post("1"), post("2", listOf("1")), post("3")), setOf("1"), setOf("2")
        ))
    }
    @Test fun absentSelfRecordsAreNotPresentedAsZeroKnownReplies() {
        val summary = ThreadOpeningSummaryTracker(1).accept(listOf(post("1"), post("2")), emptySet())!!
        assertNull(summary.repliesToSelf)
        assertTrue(summary.text.contains("投稿記録なし"))
    }
    @Test fun previewArchiveAndTruncationDoNotConsumeTheOpeningNotice() {
        val tracker = ThreadOpeningSummaryTracker(2)
        val posts = listOf(post("1"), post("2"), post("3"))
        assertNull(tracker.accept(posts, emptySet(), live = false))
        assertNull(tracker.accept(posts, emptySet(), truncated = true))
        assertNull(tracker.accept(posts.take(1), emptySet(), truncated = true))
        assertEquals(1, tracker.accept(posts, emptySet())?.newPosts)
    }
    @Test fun noNewPostsDoesNotNotifyOnViewerReturnOrRecomposition() {
        val tracker = ThreadOpeningSummaryTracker(2)
        assertNull(tracker.accept(listOf(post("1"), post("2")), setOf("1")))
        assertNull(tracker.accept(listOf(post("1"), post("2"), post("3")), setOf("1")))
    }
    @Test fun statusKeepsIsolationDeletionAndGoneDistinct() {
        fun page(notice: String?) = ThreadPage("1", null, null, notice, listOf(post("1")))
        assertEquals("隔離", threadStatusText(confirmedThreadStatus(page("隔離されました"))))
        assertEquals("管理者削除", threadStatusText(confirmedThreadStatus(page("管理者により削除されました"))))
        assertEquals("削除", threadStatusText(confirmedThreadStatus(page("削除されました"))))
        assertEquals("隔離・落ち", threadStatusText(confirmedThreadStatus(page("隔離"), gone = true)))
        assertNull(confirmedThreadStatus(null))
        assertEquals("", threadStatusText(null))
        assertEquals("落ち", threadStatusText(confirmedThreadStatus(null, gone = true)))
    }
    @Test fun oldHistoryHasUnknownStatusAndUserDisabledRefreshIsNotADeadProof() {
        val old = """{"threadId":"1","title":"題名","titleImageUrl":"","boardName":"板","boardUrl":"https://may.2chan.net/b/","lastVisitedEpochMillis":0,"replyCount":2,"isAutoRefreshDisabled":true}"""
        val entry = Json.decodeFromString<ThreadHistoryEntry>(old)
        assertNull(entry.threadStatus)
        assertEquals("", threadStatusText(entry.threadStatus))
        assertEquals(entry, Json.decodeFromString<ThreadHistoryEntry>(Json.encodeToString(entry)))
    }
    @Test fun aCatalogCreatedHistoryCountIsNotAPreviousRead() {
        val entry = ThreadHistoryEntry("1", "may", "題名", "", "板", "https://may.2chan.net/b/", 1, 42)
        assertNull(knownViewedPostCount(entry))
        assertNull(knownViewedPostCount(entry.copy(lastConfirmedAliveEpochMillis = 1)))
        assertNull(knownViewedPostCount(entry.copy(lastReadPostId = "2")))
        assertEquals(42, knownViewedPostCount(entry.copy(lastViewedSnapshot = ThreadViewedSnapshot(42, "44", 1))))
    }

    @Test fun onlyExplicitGoneResponsesConfirmDeathAndUnknownMetadataPreservesStatus() {
        assertTrue(buildThreadInitialLoadFailureState(IllegalStateException(), 404).confirmedGone)
        assertTrue(buildThreadInitialLoadFailureState(IllegalStateException(), 410).confirmedGone)
        assertFalse(buildThreadInitialLoadFailureState(IllegalStateException(), 500).confirmedGone)
        assertFalse(buildThreadInitialLoadFailureState(IllegalStateException(), null).confirmedGone)
        val known = ThreadHistoryEntry("1", "may", "題名", "", "板", "https://may.2chan.net/b/", 1, 2,
            threadStatus = ThreadStatus(isIsolated = true))
        assertEquals(known.threadStatus, com.valoser.futacha.shared.state.mergeAppStateHistoryEntry(known, known.copy(threadStatus = null)).threadStatus)
    }

    @Test fun narrowTabBadgeKeepsTheFullStatusForTheListAndAccessibility() {
        val status = ThreadStatus(isDead = true, isIsolated = true)
        assertEquals("隔離", threadStatusBadgeText(status))
        assertEquals("隔離・落ち", threadStatusText(status))
        assertEquals("管削", threadStatusBadgeText(ThreadStatus(isAdminDeleted = true, isDead = true)))
        assertEquals("削除", threadStatusBadgeText(ThreadStatus(isDeleted = true)))
        assertEquals("落ち", threadStatusBadgeText(ThreadStatus(isDead = true)))
        assertEquals("", threadStatusBadgeText(null))
    }

}
