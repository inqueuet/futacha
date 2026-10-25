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

/** M75: the viewed-record effect of all four modes must survive a history write failure. */
class ThreadViewedObservationFailureTest {
    private fun entry() = ThreadHistoryEntry("100", "may", "題名", "", "板", "https://may.2chan.net/b/", 1, 500)
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "本文",
        imageUrl = null, thumbnailUrl = null)

    @Test fun aFailedHistoryWriteIsReportedWithoutLeavingTheEffectAndARetryRecordsTheView() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)
        try {
            store.prependOrReplaceHistoryEntry(entry())
            storage.failHistoryUpdate = true
            // Before M75 this threw out of the LaunchedEffect and crashed the app.
            observeForegroundThreadView(store, "may", "100", listOf(post("100"), post("104")), 10,
                isVisible = { true }, boardUrl = null)
            assertNull(store.history.first().single().lastViewedSnapshot, "the failed write was rolled back")
            storage.failHistoryUpdate = false
            observeForegroundThreadView(store, "may", "100", listOf(post("100"), post("104")), 20,
                isVisible = { true }, boardUrl = null)
            assertEquals(ThreadViewedSnapshot(2, "104", 20), store.history.first().single().lastViewedSnapshot)
        } finally { store.close() }
    }
}
