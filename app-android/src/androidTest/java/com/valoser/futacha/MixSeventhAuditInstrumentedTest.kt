@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.mix.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import kotlinx.coroutines.runBlocking
import org.junit.*

/** Real compatibility navigation: normal boards leave MIX, its own thread returns to MIX. */
class MixSeventhAuditInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private val db = "mix-seventh-audit.db"
    private val may = "https://may.2chan.net/b/"
    private val img = "https://img.2chan.net/b/"
    private var boardLink by mutableStateOf<String?>(null)
    private var savedEnabled = false
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = CatalogPageContent(listOf(
            CatalogItem("501", "${board}res/501.htm", "Local row", null, null, replyCount = 1)))
        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = getCatalogPage(board, mode)
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = getCatalogPage(board, mode).items
    }
    @Before fun prepare() {
        savedEnabled = MixAvailability.enabled
        MixAvailability.enabled = true
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize()
            store.savePreference("compat.commonUsedVersion", "9999.0")
            store.upsertBoard(CompatBoard(compatBoardKey(may), "May", may, may, 0))
            store.upsertBoard(CompatBoard(compatBoardKey(img), "Img", img, img, 1))
            MixStore(store).create("Audit return MIX", listOf(may, img), MixOrderKind.ALTERNATE, 7707)
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
        MixAvailability.enabled = savedEnabled
    }
    private fun start() {
        rule.setContent { MaterialTheme {
            CompatibilityApp(store, repository, initialBoardDeepLink = boardLink,
                onBoardDeepLinkConsumed = { boardLink = null }, onExitApplication = {})
        } }
    }
    private fun openMix() {
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("まとめカタログ").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun backFromAnExternalOrdinaryBoardReturnsToTheBoardList() {
        start(); openMix()
        rule.runOnIdle { boardLink = img }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("切替").assertDoesNotExist()
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(img).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("切替").assertDoesNotExist()
        rule.onNodeWithText(img).assertIsDisplayed()
    }
    @Test fun aThreadOpenedDirectlyFromMixReturnsToTheSameMix() {
        start(); openMix()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Local row").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Local row")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isEmpty() }
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Audit return MIX").assertIsDisplayed()
    }

}
