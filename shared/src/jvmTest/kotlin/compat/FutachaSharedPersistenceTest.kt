package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
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

    @Test fun backgroundReplyCountUpdateDoesNotResurrectClosedTabOrRevertFavoriteOrReadCount() = runBlocking {
        val root = Files.createTempDirectory("futacha-background-tabs").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/"
            store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", url, "")))
            val boardKey = compatBoardKey(url)
            val now = System.currentTimeMillis()
            fun tab(no: String) = CompatTab(compatTabKey("${url}res/$no.htm"), "${url}res/$no.htm", "${url}res/$no.htm",
                boardKey, "板", no, "スレ$no", replyCount = 3, insertedAtEpochMillis = now, contentUpdatedAtEpochMillis = now)
            val closing = tab("100")
            val reading = tab("200")
            store.openTab(closing, CompatHistoryEntry(closing.canonicalUrl, closing.originalUrl, boardKey, "板", "100",
                "スレ100", replyCount = 3, contentUpdatedAtEpochMillis = now))
            store.openTab(reading, CompatHistoryEntry(reading.canonicalUrl, reading.originalUrl, boardKey, "板", "200",
                "スレ200", replyCount = 3, contentUpdatedAtEpochMillis = now))

            val catalogRequested = CompletableDeferred<Unit>()
            val releaseCatalog = CompletableDeferred<Unit>()
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> {
                    catalogRequested.complete(Unit)
                    releaseCatalog.await()
                    return listOf("100", "200").map { no ->
                        CatalogItem(no, "${url}res/$no.htm", "スレ$no", null, null, replyCount = 10)
                    }
                }
            }
            val refresh = async {
                refreshCompatTabsInBackground(store, repository, nowEpochMillis = now, checkExistence = false)
            }
            catalogRequested.await()
            // The user acts while the catalog request is in flight.
            store.closeTabs(setOf(closing.key), now + 1)
            val current = store.tabs.first().single { it.key == reading.key }
            store.updateTab(current.copy(favorite = true, checkedReplyCount = 3))
            store.upsertHistory(store.history.first().single { it.canonicalUrl == reading.canonicalUrl }
                .copy(title = "新しいタイトル"))
            releaseCatalog.complete(Unit)
            val result = refresh.await()

            assertEquals(1, result.updatedTabs)
            val tabs = store.tabs.first()
            assertNull(tabs.firstOrNull { it.key == closing.key }, "closed tab must not come back")
            val updated = tabs.single { it.key == reading.key }
            assertTrue(updated.favorite)
            assertEquals(3, updated.checkedReplyCount)
            assertEquals(10, updated.replyCount)
            val history = store.history.first().single { it.canonicalUrl == reading.canonicalUrl }
            assertEquals("新しいタイトル", history.title)
            assertEquals(10, history.replyCount)
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun updateTabIfPresentNeverCreatesTabsAndKeepsIdentityAndAnchor() = runBlocking {
        val root = Files.createTempDirectory("futacha-tab-if-present").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/res/300.htm"
            val now = System.currentTimeMillis()
            val tab = CompatTab(compatTabKey(url), url, url, "b", "板", "300", "スレ", insertedAtEpochMillis = now,
                contentUpdatedAtEpochMillis = now)
            assertFalse(store.updateTabIfPresent(tab.key) { it.copy(replyCount = 5) })
            assertTrue(store.tabs.first().isEmpty())

            store.openTab(tab)
            store.updateScrollAnchor(tab.key, ScrollAnchor(postNo = "310", offsetPx = 12))
            assertTrue(store.updateTabIfPresent(tab.key) {
                it.copy(replyCount = 5, key = "other", scrollAnchor = ScrollAnchor())
            })
            val stored = store.tabs.first().single()
            assertEquals(tab.key, stored.key)
            assertEquals(5, stored.replyCount)
            assertEquals("310", stored.scrollAnchor.postNo)
            assertFalse(store.updateTabIfPresent(tab.key) { null })
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun existenceProbesAreBoundedAndATimeoutIsNotTreatedAsDead() = runBlocking {
        val root = Files.createTempDirectory("futacha-probe-budget").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/"
            store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", url, "")))
            listOf("100", "200").forEach { no ->
                store.openTab(CompatTab(compatTabKey("${url}res/$no.htm"), "${url}res/$no.htm", "${url}res/$no.htm",
                    compatBoardKey(url), "板", no, "スレ$no", insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L))
            }
            // A captive portal or a stalled server: the probe never answers.
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun probeThreadGone(threadUrl: String): Boolean = awaitCancellation()
            }

            val result = withTimeout(5_000L) {
                refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L,
                    checkUpdates = false, checkExistence = true,
                    existenceProbeTimeoutMillis = 50L, existenceBudgetMillis = 1_000L)
            }

            assertEquals(0, result.deadTabs)
            assertEquals(2, result.failures)
            assertTrue(store.tabs.first().none { it.isDead })
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun runBudgetStopsSlowPhasesAndReturnsInTime() = runBlocking {
        val root = Files.createTempDirectory("futacha-run-budget").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/"
            store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", url, "")))
            store.openTab(CompatTab(compatTabKey("${url}res/1.htm"), "${url}res/1.htm", "${url}res/1.htm",
                compatBoardKey(url), "板", "1", "スレ", insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L))
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> = awaitCancellation()
                override suspend fun probeThreadGone(threadUrl: String): Boolean = awaitCancellation()
            }

            val result = withTimeout(3_000L) {
                refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L,
                    checkUpdates = true, checkExistence = true, budgetMillis = 200L)
            }

            assertTrue(result.failures >= 1)
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun savePreferencesWritesAndRemovesKeysInOneChangeAndSurvivesReopening() = runBlocking {
        val root = Files.createTempDirectory("futacha-save-preferences").toFile()
        var store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.savePreference("compat.test.removed", "old")
            store.savePreferences(mapOf("compat.test.a" to "1", "compat.test.b" to "2", "compat.test.removed" to null))
            store.close()
            store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize()
            val preferences = store.preferences.first()
            assertEquals("1", preferences["compat.test.a"])
            assertEquals("2", preferences["compat.test.b"])
            assertFalse("compat.test.removed" in preferences)
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun unrelatedPreferenceDoesNotEnforceTheSnapshotQuota() = runBlocking {
        val root = Files.createTempDirectory("futacha-quota-scope").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/res/1.htm"
            val tab = CompatTab(compatTabKey(url), url, url, "b", "板", "1", "スレ", insertedAtEpochMillis = 1L,
                contentUpdatedAtEpochMillis = 1L)
            store.openTab(tab)
            assertTrue(store.saveThreadSnapshot(CompatThreadSnapshot(tabKey = tab.key, revision = 1L,
                fetchedAtEpochMillis = 1L,
                posts = listOf(CompatPostSnapshot(position = 0, postNo = "1", timestamp = "", messageHtml = "x".repeat(2_000))))))
            val usageBefore = store.threadSnapshotCacheUsageBytes()
            assertTrue(usageBefore > 0)

            // Saving any other preference must not re-encode or evict snapshots.
            store.savePreference("compat.test.other", "1")
            store.savePreferences(mapOf("compat.test.batch" to "2"))
            assertEquals(usageBefore, store.threadSnapshotCacheUsageBytes())
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun legacyImageHashPreferencesMoveToTheCacheAndSettingsBeyond4096Survive() = runBlocking {
        val root = Files.createTempDirectory("futacha-phash-migration").toFile()
        var store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            // As an older version left it: more hash rows than the preference cap,
            // and a real setting whose key sorts after them.
            val legacy = (0 until 4_200).associate {
                compatImagePhashCachePreferenceKey("https://img/$it.jpg") to "0123456789abcdef"
            }
            store.savePreferences(legacy + ("compat.thread.threadExtractSoudaneNum" to "5"))
            store.close()

            store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize()
            val preferences = store.preferences.first()
            assertEquals("5", preferences["compat.thread.threadExtractSoudaneNum"])
            assertTrue(preferences.keys.none(::isCompatImagePhashCacheKey))
            val sampleKey = compatImagePhashCachePreferenceKey("https://img/7.jpg")
            assertEquals(mapOf(sampleKey to "0123456789abcdef"), store.loadImagePhashes(listOf(sampleKey)))
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun imageHashCacheRoundTripsAcrossReopenWithoutTouchingPreferences() = runBlocking {
        val root = Files.createTempDirectory("futacha-phash-cache").toFile()
        var store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val key = compatImagePhashCachePreferenceKey("https://img/a.jpg")
            store.saveImagePhashes(mapOf(key to "fedcba9876543210", "not-a-hash-key" to "fedcba9876543210"))
            store.close()
            store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize()

            assertEquals(mapOf(key to "fedcba9876543210"), store.loadImagePhashes(listOf(key, "not-a-hash-key")))
            assertTrue(store.preferences.first().keys.none(::isCompatImagePhashCacheKey))
        } finally { store.close(); root.deleteRecursively() }
    }
}
