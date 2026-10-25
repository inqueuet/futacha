@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.filters.SdkSuppress
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L63: with the drawer and the catalog search both open, back closes the drawer first and keeps the search. */
@SdkSuppress(minSdkVersion = 30)
class CompatDrawerBackOrderInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l63-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private val item = CatalogItem(id = "463", title = "L63", thumbnailUrl = null, fullImageUrl = null,
        threadUrl = "${boardUrl}res/463.htm", replyCount = 1)
    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = CatalogPageContent(listOf(item))
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = listOf(item)
    }
    private lateinit var storage: AndroidCompatibilityStore

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(boardKey, "L63", boardUrl, boardUrl, 0))
        }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    // The drawer's content stays composed while it is closed, off the screen to the left.
    private fun drawerShown() = rule.onAllNodes(hasText("閲覧中のスレッド") or hasText("履歴") or hasText("巡回結果"))
        .fetchSemanticsNodes().any { it.positionInRoot.x + it.size.width > 1f }

    private fun searchShown() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()

    @Test fun backClosesTheDrawerBeforeTheSearch() {
        rule.setContent { MaterialTheme { CompatibilityApp(store = storage, repository = repo, onExitApplication = {}) } }
        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-catalog-item-463", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("検索").performClick()
        rule.waitUntil(5_000) { searchShown() }
        Espresso.closeSoftKeyboard()
        rule.waitForIdle()

        rule.onRoot().performTouchInput { swipe(Offset(2f, height * 0.5f), Offset(width * 0.6f, height * 0.5f), 300) }
        rule.waitUntil(5_000) { drawerShown() }
        rule.waitForIdle()
        // The first back of a shown keyboard only hides it (the platform's own); the back looked at here comes after.
        Espresso.closeSoftKeyboard()
        rule.waitUntil(5_000) {
            rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == false
        }
        rule.waitForIdle()

        Espresso.pressBack()
        rule.waitUntil(5_000) { !drawerShown() }
        rule.waitForIdle()
        org.junit.Assert.assertTrue("The search stays open", searchShown())
    }
}
