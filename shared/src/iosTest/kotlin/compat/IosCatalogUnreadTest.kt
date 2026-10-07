package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class IosCatalogUnreadTest {
    @Test fun catalogRefreshPublishesUnreadAndPersistsWithoutReadingOrCreatingTabs() = runBlocking {
        val fs = createFileSystem()
        fs.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fs)
            store.initialize()
            val board = "https://may.2chan.net/b/"
            val key = compatBoardKey(board)
            store.upsertBoard(CompatBoard(key, "may", board, board, 0))
            val url = "${board}res/123.htm"
            val before = CompatTab(compatTabKey(url), url, url, key, "may", "123", "title",
                replyCount = 10, checkedReplyCount = 10, favorite = true,
                insertedAtEpochMillis = 10, contentUpdatedAtEpochMillis = 100,
                scrollAnchor = ScrollAnchor("128", 32, 5, 2))
            store.openTab(before, null)
            val initial = store.tabs.first().single()
            val history = store.history.first()
            val snapshot = CompatCatalogSnapshot(key, CompatCatalogSort.CATALOG, 1, 200,
                listOf(CatalogItem("123", url, "title", null, null, replyCount = 15)))
            assertTrue(store.saveCatalogSnapshot(snapshot))
            val after = initial.copy(replyCount = 15, contentUpdatedAtEpochMillis = 200)
            assertEquals(after, store.tabs.first().single())
            assertEquals(5, after.unreadCount)
            assertEquals(history, store.history.first())
            assertTrue(store.saveCatalogSnapshot(snapshot.copy(revision = 2, fetchedAtEpochMillis = 199,
                items = snapshot.items.map { it.copy(replyCount = 30) })))
            assertEquals(after, store.tabs.first().single())
            val reopened = IosCompatibilityStore(fs)
            reopened.initialize()
            assertEquals(after, reopened.tabs.first().single())
        } finally { fs.deleteRecursively("compatibility").getOrThrow() }
    }
}
