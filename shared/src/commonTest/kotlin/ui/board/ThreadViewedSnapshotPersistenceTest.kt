package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadViewedSnapshot
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadViewedSnapshotPersistenceTest {
    private fun entry() = ThreadHistoryEntry("100", "may", "題名", "", "板", "https://may.2chan.net/b/", 1, 500)
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "本文",
        imageUrl = null, thumbnailUrl = null)

    @Test fun aRealViewSurvivesNewMetadataAndStoreReopening() = runBlocking {
        val storage = FakePlatformStateStorage()
        var store = AppStateStore(storage)
        try {
            store.prependOrReplaceHistoryEntry(entry())
            recordForegroundThreadView(store, "may", "100", listOf(post("100"), post("104")), 10)
            store.upsertHistoryEntry(entry().copy(replyCount = 900, lastConfirmedAliveEpochMillis = 100, lastVisitedEpochMillis = 100))
            assertEquals(ThreadViewedSnapshot(2, "104", 10), store.history.first().single().lastViewedSnapshot)
            store.close()
            store = AppStateStore(storage)
            assertEquals(2, knownViewedPostCount(store.history.first().single()))
        } finally { store.close() }
    }

    @Test fun backgroundAndDeletedHistoryDoNotAcquireAReadRecord() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        try {
            store.prependOrReplaceHistoryEntry(entry())
            recordForegroundThreadView(store, "may", "100", listOf(post("100")), 10) { false }
            assertNull(store.history.first().single().lastViewedSnapshot)
            store.clearHistory()
            recordForegroundThreadView(store, "may", "100", listOf(post("100")), 11)
            assertEquals(emptyList(), store.history.first())
        } finally { store.close() }
    }

    @Test fun compatibilityBoardKeysMatchTheSameCanonicalBoardWithoutMixingBoards() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        try {
            store.prependOrReplaceHistoryEntry(entry())
            recordForegroundThreadView(store, "compat-key", "100", listOf(post("100"), post("104")), 10,
                boardUrl = "https://may.2chan.net/b/")
            assertEquals("104", store.history.first().single().lastViewedSnapshot?.lastPostId)
            recordForegroundThreadView(store, "another", "100", listOf(post("999")), 20,
                boardUrl = "https://img.2chan.net/b/")
            assertEquals("104", store.history.first().single().lastViewedSnapshot?.lastPostId)
        } finally { store.close() }
    }

    @Test fun anOlderShorterViewDoesNotMoveTheReadBoundaryBackwards() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        try {
            store.prependOrReplaceHistoryEntry(entry())
            recordForegroundThreadView(store, "may", "100", listOf(post("100"), post("104")), 10)
            recordForegroundThreadView(store, "may", "100", listOf(post("100")), 20)
            assertEquals(ThreadViewedSnapshot(2, "104", 10), store.history.first().single().lastViewedSnapshot)
        } finally { store.close() }
    }

    @Test fun partialFirstResponsesDoNotTurnOldPostsIntoNewOnTheInitialLoad() {
        val tracker = ThreadNewPostTracker()
        tracker.onPostsLoaded(listOf(post("100")), complete = false)
        tracker.onPostsLoaded(listOf(post("100"), post("101")), complete = false)
        assertEquals(emptySet(), tracker.newPostIds)
        tracker.onPostsLoaded(listOf(post("100"), post("101"), post("102")))
        assertEquals(emptySet(), tracker.newPostIds)
        tracker.onPostsLoaded(listOf(post("100"), post("101"), post("102"), post("103")))
        assertEquals(setOf("103"), tracker.newPostIds)
    }
}
