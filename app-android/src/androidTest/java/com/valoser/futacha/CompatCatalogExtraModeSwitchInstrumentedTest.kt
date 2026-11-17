@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.compat.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L41: a load of the board's own order that ends after "そ順" was chosen is not shown under "そ順". */
class CompatCatalogExtraModeSwitchInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l41-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private val state = AppStateStore(BaseInMemoryPlatformStateStorage())
    private val normalLoad = CompletableDeferred<Unit>()
    private val soLoad = CompletableDeferred<Unit>()
    private lateinit var storage: AndroidCompatibilityStore

    private fun item(id: String, title: String) = CatalogItem(id = id, title = title, thumbnailUrl = null, fullImageUrl = null,
        threadUrl = "${boardUrl}res/$id.htm", replyCount = 1)

    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) =
            if (mode == CatalogMode.So) {
                soLoad.await(); listOf(item("412", "SO-ITEM"))
            } else {
                normalLoad.await(); listOf(item("411", "NORMAL-ITEM"))
            }
    }

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(boardKey, "L41", boardUrl, boardUrl, 0))
        }
    }

    @After fun cleanup() {
        normalLoad.complete(Unit); soLoad.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        state.close()
        rule.activity.deleteDatabase(db)
    }

    @Test fun theOldOrdersResultIsNotShownUnderTheNewOne() {
        rule.setContent { MaterialTheme {
            CompatibilityApp(store = storage, repository = repo, stateStore = state, onExitApplication = {})
        } }
        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-toolbar-command-sort", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // The board's own order is being read; "そ順" is chosen meanwhile.
        rule.onNodeWithTag("compat-toolbar-command-sort").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-catalog-sort-extra-SO", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("compat-catalog-sort-extra-SO").performClick()
        val key = compatCatalogExtraModeKey(boardKey)
        rule.waitUntil(5_000) { runBlocking { storage.loadPreference(key) } == "SO" }

        normalLoad.complete(Unit)
        // The board's own order is saved once its load ends.
        rule.waitUntil(5_000) { runBlocking { CompatCatalogSort.entries.any { storage.loadCatalogSnapshot(boardKey, it) != null } } }
        rule.waitForIdle()
        rule.onAllNodesWithTag("compat-catalog-item-411", useUnmergedTree = true).assertCountEquals(0)

        soLoad.complete(Unit)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-catalog-item-412", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithTag("compat-catalog-item-411", useUnmergedTree = true).assertCountEquals(0)
    }
}
