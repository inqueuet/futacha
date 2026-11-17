@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.compat.COMPAT_CATALOG_ENTRY_REFRESH_KEY
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class CompatCatalogEntryRefreshInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "catalog-entry-${System.nanoTime()}.db"
    private val url = "https://may.2chan.net/b/"
    private lateinit var store: AndroidCompatibilityStore
    private val calls = AtomicInteger()
    private val items = (1..100).map { id -> CatalogItem(id = id.toString(), title = "Entry $id", thumbnailUrl = null, fullImageUrl = null, threadUrl = "${url}res/$id.htm", replyCount = 1) }
    private val repo = object : BoardRepository by FakeBoardRepository() {
        private fun page(): CatalogPageContent { calls.incrementAndGet(); return CatalogPageContent(items) }
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = page()
        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = page()
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = page().items
    }
    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize()
            store.savePreference("compat.commonUsedVersion", "9999.0")
            store.upsertBoard(CompatBoard(compatBoardKey(url), "EntryBoard", url, url, 0))
            store.savePreference("compat.catalog.catalogOpenWithReload", "ON")
            store.savePreference("compat.catalog.$COMPAT_CATALOG_ENTRY_REFRESH_KEY", "ON")
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    @Test fun boardOpeningRefreshesButThreadReturnKeepsTheCatalogAndItsPosition() {
        rule.setContent { MaterialTheme { CompatibilityApp(store, repository = repo, onExitApplication = {}) } }
        rule.onNodeWithText(url).performClick()
        rule.waitUntil(15_000) { calls.get() == 1 && rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-catalog-grid").performScrollToIndex(35)
        rule.onNodeWithTag("compat-catalog-item-36", useUnmergedTree = true).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isEmpty() }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        assertEquals(1, calls.get())
        rule.onNodeWithTag("compat-catalog-item-36", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText(url).performClick()
        rule.waitUntil(10_000) { calls.get() == 2 }
        rule.onNodeWithTag("compat-catalog-item-1", useUnmergedTree = true).assertIsDisplayed()
    }
}
