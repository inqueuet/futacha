package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class FutachaSharedPersistenceTest {
    @Test fun settingsNgTabsAndUndoSurviveReopeningAndApplyToModernContent() = runBlocking {
        val root = Files.createTempDirectory("futacha-shared-parity").toFile()
        val fs = JvmFileSystem(root)
        val url = "https://may.2chan.net/b/"
        val threadUrl = "${url}res/123.htm"
        val boardKey = compatBoardKey(url)
        val tabKey = compatTabKey(threadUrl)
        val now = System.currentTimeMillis()
        var store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            store.importModernBoards(listOf(BoardSummary("modern-board", "共通の板", "", url, "")))
            val tab = CompatTab(tabKey, threadUrl, threadUrl, boardKey, "共通の板", "123", "共通スレ",
                favorite = true, insertedAtEpochMillis = now, contentUpdatedAtEpochMillis = now)
            store.openTab(tab)
            store.savePreference("compat.thread.threadExtractSoudaneNum", "5")
            store.upsertNgRule(CompatNgRule("shared-rule", CompatNgKind.THREAD_IGNORE, tabKey, "隠す", now))
            store.close()
            store = DesktopCompatibilityStore(fs)
            store.initialize()
            val page = ThreadPage("123", "板", null, null, listOf(
                Post("123", 0, null, null, "", messageHtml = "通常", imageUrl = null, thumbnailUrl = null),
                Post("124", 1, null, null, "", messageHtml = "隠す", imageUrl = null, thumbnailUrl = null)))
            assertEquals(listOf("123"), projectFutachaThread(page, page, FutachaThreadProjection(tabKey, boardKey),
                store.ngRules.first(), store.preferences.first()).posts.map { it.id })
            assertEquals("5", store.preferences.first()["compat.thread.threadExtractSoudaneNum"])
            val historyEntry = store.tabs.first().single().toFutachaHistoryEntry()
            assertEquals("123", historyEntry.threadId)
            assertEquals("共通スレ", historyEntry.title)
            val batch = assertNotNull(store.closeTabs(setOf(tabKey), now + 1))
            assertTrue(store.tabs.first().isEmpty())
            store.restoreClosedTabs(batch)
            assertTrue(store.tabs.first().single().favorite)
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun modernCatalogUsesSharedRequestSizeAndKeepsOrdinaryRequestsWhenUnset() = runBlocking {
        val root = Files.createTempDirectory("futacha-catalog-parity").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            var ordinaryRequests = 0
            var requested: CatalogFetchSettings? = null
            val delegate = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
                    ordinaryRequests++; return CatalogPageContent(emptyList())
                }
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
                    requested = settings; return emptyList()
                }
                override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): CatalogPageContent =
                    CatalogPageContent(getCatalogWithSettings(board, mode, settings))
            }
            val repository = FutachaSharedBoardRepository(delegate, store, null)
            repository.getCatalogPage("https://may.2chan.net/b/", CatalogMode.Many)
            assertEquals(1, ordinaryRequests)
            assertNull(requested)
            store.savePreference("compat.catalog.catalogThreadSize", "600")
            repository.getCatalogPage("https://may.2chan.net/b/", CatalogMode.Many)
            assertNotNull(requested)
            assertEquals(1, ordinaryRequests)
            assertEquals(com.valoser.futacha.shared.ui.compat.compatCatalogFetchSettings(600), requested)
        } finally { store.close(); root.deleteRecursively() }
    }
}
