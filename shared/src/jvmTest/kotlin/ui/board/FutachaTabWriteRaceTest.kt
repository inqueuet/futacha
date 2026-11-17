package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

/**
 * ふたちゃ wrote a whole copy of a tab taken before the write ([CompatibilityStore.updateTab]): a tab closed in between
 * came back, and a ★ or counts written in between were rolled back (docs/futaber-evolution-data-2026-10-31.md §2.4).
 * [RacingStore] lets another writer (the tab list, a background refresh) commit just before ふたちゃ's write reaches the
 * store's lock, as it can when the two run on different coroutines.
 */
class FutachaTabWriteRaceTest {
    private val url = "https://may.2chan.net/b/res/100.htm"
    private val boardKey = compatBoardKey("https://may.2chan.net/b/")
    private val tab = CompatTab(compatTabKey(url), url, url, boardKey, "may", "100", "title",
        replyCount = 10, checkedReplyCount = 10, insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 2, snapshotRevision = 7)

    /** Runs [beforeWrite] once, just before the next tab write takes the store's lock. */
    private class RacingStore(val inner: CompatibilityStore) : CompatibilityStore by inner {
        var beforeWrite: (suspend () -> Unit)? = null
        private suspend fun race() { beforeWrite?.let { beforeWrite = null; it() } }
        override suspend fun updateTab(tab: CompatTab) { race(); inner.updateTab(tab) }
        override suspend fun updateTabIfPresent(tabKey: String, transform: (CompatTab) -> CompatTab?): Boolean {
            race(); return inner.updateTabIfPresent(tabKey, transform)
        }
    }

    private fun withStore(block: suspend (DesktopCompatibilityStore, RacingStore) -> Unit) = runBlocking<Unit> {
        val root = Files.createTempDirectory("futacha-tab-race").toFile()
        try {
            val store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize()
            store.importModernBoards(listOf(BoardSummary("b", "may", "", "https://may.2chan.net/b/", "")))
            store.openTab(tab)
            block(store, RacingStore(store))
        } finally { root.deleteRecursively() }
    }

    private fun pageWrite(replyCount: Int, now: Long = 50) = FutachaThreadTabWrite(
        tabKey = tab.key, sourceUrl = url, boardKey = boardKey, boardName = "may", threadId = "100", title = "title",
        replyCount = replyCount, thumbnailUrl = null, snapshotChanged = true, snapshotRevision = 8, storedSnapshotRevision = 7,
        confirmedGone = false, confirmedStatus = CompatThreadStatusFlags(), nowEpochMillis = now
    )

    @Test fun aStarPressedAsTheTabIsClosedDoesNotBringTheTabBack() = withStore { store, racing ->
        racing.beforeWrite = { store.closeTabs(setOf(tab.key), 10) }
        toggleFutachaTabFavorite(racing, tab.key)
        assertTrue(store.tabs.first().none { it.key == tab.key }, "the closed tab came back")
    }

    @Test fun aStarPressedAsABackgroundRefreshLandsKeepsTheNewCounts() = withStore { store, racing ->
        racing.beforeWrite = { store.updateTabIfPresent(tab.key) { it.copy(replyCount = 15, isDead = true) } }
        toggleFutachaTabFavorite(racing, tab.key)
        val stored = store.tabs.first().single()
        assertTrue(stored.favorite)
        assertEquals(15, stored.replyCount, "the refreshed count was rolled back")
        assertTrue(stored.isDead, "the refreshed state was rolled back")
    }

    @Test fun aPageArrivingAsTheTabIsClosedDoesNotBringTheTabBack() = withStore { store, racing ->
        racing.beforeWrite = { store.closeTabs(setOf(tab.key), 10) }
        writeFutachaThreadTab(racing, pageWrite(replyCount = 12))
        assertTrue(store.tabs.first().none { it.key == tab.key }, "the closed tab came back")
    }

    @Test fun aPageArrivingAsTheStarIsPressedKeepsTheStar() = withStore { store, racing ->
        racing.beforeWrite = { store.updateTabIfPresent(tab.key) { it.copy(favorite = true) } }
        writeFutachaThreadTab(racing, pageWrite(replyCount = 12))
        val stored = store.tabs.first().single()
        assertEquals(12, stored.replyCount)
        assertEquals(12, stored.checkedReplyCount)
        assertTrue(stored.favorite, "the star was rolled back")
    }

    // ---- What does not change: the writes without another writer in between. ----

    @Test fun theStarStillFlipsBothWays() = withStore { store, _ ->
        toggleFutachaTabFavorite(store, tab.key)
        assertTrue(store.tabs.first().single().favorite)
        toggleFutachaTabFavorite(store, tab.key)
        assertFalse(store.tabs.first().single().favorite)
        toggleFutachaTabFavorite(store, "compat_tab_missing")
        assertEquals(1, store.tabs.first().size, "a star of a missing tab opens nothing")
    }

    @Test fun anAcceptedPageStillOpensTheTabOrUpdatesItsCountsAndState() = withStore { store, _ ->
        store.closeTabs(setOf(tab.key), 10)
        writeFutachaThreadTab(store, pageWrite(replyCount = 12, now = 60))
        val opened = store.tabs.first().single()
        assertEquals(12, opened.replyCount)
        assertEquals(12, opened.checkedReplyCount)
        assertEquals(60, opened.insertedAtEpochMillis)
        assertEquals(8, opened.snapshotRevision)
        writeFutachaThreadTab(store, pageWrite(replyCount = 20, now = 70).copy(
            confirmedGone = true, confirmedStatus = CompatThreadStatusFlags(isIsolated = true)))
        val updated = store.tabs.first().single()
        assertEquals(20, updated.replyCount)
        assertEquals(20, updated.checkedReplyCount)
        assertTrue(updated.isDead)
        assertTrue(updated.isIsolated)
        assertEquals(60, updated.insertedAtEpochMillis, "the tab keeps its place")
        // A device copy keeps the stored state and the stored revision when its text did not change.
        writeFutachaThreadTab(store, pageWrite(replyCount = 20, now = 80).copy(
            snapshotChanged = false, storedSnapshotRevision = 8, confirmedStatus = null))
        val kept = store.tabs.first().single()
        assertTrue(kept.isDead)
        assertEquals(70, kept.contentUpdatedAtEpochMillis)
        assertEquals(8, kept.snapshotRevision)
    }
}
