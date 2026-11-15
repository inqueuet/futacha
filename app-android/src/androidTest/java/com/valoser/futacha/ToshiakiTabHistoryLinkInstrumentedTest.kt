@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Review 2026-11-11: としあき(仮)'s optional tab/history link switches and the search clear buttons on real screens. */
class ToshiakiTabHistoryLinkInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val db = "toshiaki_tab_history_link.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val board = CompatBoard(compatBoardKey(boardUrl), "mayb", boardUrl, boardUrl, 0)
    private val threadUrl = "${boardUrl}res/9101.htm"
    private val title = "連動確認のスレ"

    @Before fun prepare() {
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize(); store.upsertBoard(board)
            store.openTab(CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, board.key, board.name, "9101", title,
                insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1))
            store.upsertHistory(CompatHistoryEntry(threadUrl, threadUrl, board.key, board.name, "9101", title,
                contentUpdatedAtEpochMillis = 1))
        }
        loader = ImageLoader.Builder(rule.activity).build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown(); runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }

    private fun show(repository: BoardRepository? = null, deepLink: String? = null) = rule.setContent {
        CompositionLocalProvider(LocalFutachaImageLoader provides loader) { MaterialTheme {
            CompatibilityApp(store = store, repository = repository, initialThreadDeepLink = deepLink, onExitApplication = {})
        } }
    }
    private fun hasHistory() = runBlocking { store.history.first().any { it.canonicalUrl == threadUrl } }
    private fun hasTab() = runBlocking { store.tabs.first().any { it.canonicalUrl == threadUrl } }
    private fun closeTabFromDrawer() {
        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithContentDescription("開いているタブ").performClick()
        rule.onNodeWithText(title).performTouchInput { longClick() }
        rule.onNodeWithText("削除する").performClick()
        rule.waitUntil(5_000) { !hasTab() }
    }
    private fun deleteHistoryFromDrawer() {
        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithContentDescription("履歴").performClick()
        rule.onAllNodesWithText(title)[0].performTouchInput { longClick() }
        rule.onNodeWithText("削除する").performClick()
        rule.waitUntil(5_000) { !hasHistory() }
    }

    @Test fun byDefaultClosingATabKeepsItsHistory() {
        show(); closeTabFromDrawer()
        Thread.sleep(1_000); rule.waitForIdle()
        assertTrue(hasHistory())
    }

    @Test fun withTheSwitchClosingATabDeletesItsHistory() {
        runBlocking { store.savePreference(COMPAT_TAB_CLOSE_DELETES_HISTORY_STORAGE_KEY, "ON") }
        show(); closeTabFromDrawer()
        rule.waitUntil(5_000) { !hasHistory() }
    }

    @Test fun byDefaultDeletingHistoryKeepsTheTab() {
        show(); deleteHistoryFromDrawer()
        Thread.sleep(1_000); rule.waitForIdle()
        assertTrue(hasTab())
    }

    @Test fun withTheSwitchDeletingHistoryClosesTheTab() {
        runBlocking { store.savePreference(COMPAT_HISTORY_DELETE_CLOSES_TAB_STORAGE_KEY, "ON") }
        show(); deleteHistoryFromDrawer()
        rule.waitUntil(5_000) { !hasTab() }
    }

    @Test fun theCatalogSearchClearButtonEmptiesTheField() {
        val item = com.valoser.futacha.shared.model.CatalogItem(id = "9101", threadUrl = threadUrl, title = "CLEAR-ME",
            thumbnailUrl = null, fullImageUrl = null, replyCount = 1)
        runBlocking { store.saveCatalogSnapshot(CompatCatalogSnapshot(board.key, CompatCatalogSort.CATALOG, 1, 1, listOf(item))) }
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(board: String, mode: com.valoser.futacha.shared.model.CatalogMode,
                settings: com.valoser.futacha.shared.model.CatalogFetchSettings) = listOf(item)
        }
        show(repository)
        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("スレッド検索").performClick()
        rule.onNodeWithText("検索文字").performTextInput("CLEAR")
        rule.onNodeWithTag("compat-catalog-search-clear").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("compat-catalog-search-clear").assertCountEquals(0)
        rule.onNodeWithText("検索文字").assertIsDisplayed()
    }

    @Test fun theThreadSearchClearButtonEmptiesTheField() {
        val page = ThreadPage(threadId = "9101", boardTitle = "mayb", expiresAtLabel = null, deletedNotice = null,
            posts = listOf(Post("1", 0, "としあき", null, "08/06 12:00", messageHtml = "aaa", imageUrl = null, thumbnailUrl = null)))
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = page
        }
        show(repository, threadUrl)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("aaa").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("レス検索").performClick()
        rule.onNodeWithTag("compat-thread-search-field").performTextInput("aa")
        rule.onNodeWithTag("compat-thread-search-clear").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals("", rule.onNodeWithTag("compat-thread-search-field").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        rule.onAllNodesWithTag("compat-thread-search-clear").assertCountEquals(0)
    }
}
