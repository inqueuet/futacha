@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
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
import com.valoser.futacha.shared.ui.compat.*
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L139: when editing a catalog NG fails to be written, the rule as it was stays (it was deleted first). */
class CompatCatalogNgEditFailureInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l139-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private val ruleId = compatNgRuleId(CompatNgKind.CATALOG_EXTRACT, boardKey, "けんさく")
    private lateinit var storage: AndroidCompatibilityStore
    private val item = CatalogItem(id = "139", title = "L139", thumbnailUrl = null, fullImageUrl = null,
        threadUrl = "${boardUrl}res/139.htm", replyCount = 1)
    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = CatalogPageContent(listOf(item))
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = listOf(item)
    }

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(boardKey, "L139", boardUrl, boardUrl, 0))
            storage.upsertNgRule(CompatNgRule(ruleId, CompatNgKind.CATALOG_EXTRACT, boardKey, "けんさく", 1))
        }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    private fun openTheWatchWordToEdit(store: CompatibilityStore) {
        rule.setContent { MaterialTheme { CompatibilityApp(store = store, repository = repo, onExitApplication = {}) } }
        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().isNotEmpty() }
        val overflows = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        rule.onAllNodesWithContentDescription("その他")[overflows.lastIndex].performClick()
        rule.onNodeWithText("監視ワード").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("けんさく").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("けんさく").performClick()
    }

    @Test fun aFailedEditKeepsTheRuleAsItWas() {
        val failing = object : CompatibilityStore by storage {
            override suspend fun upsertNgRule(rule: CompatNgRule): Boolean = throw IOException("L139 test: disk full")
            override suspend fun replaceNgRule(previousRuleId: String, rule: CompatNgRule): Boolean =
                throw IOException("L139 test: disk full")
        }
        openTheWatchWordToEdit(failing)
        rule.onNodeWithTag("compat-catalog-extract-edit-word", useUnmergedTree = true).performTextReplacement("あたらしい")
        rule.onNodeWithText("更新する").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("NGの更新に失敗しました", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("The rule as it was stays", runBlocking { storage.ngRules.first() }.any { it.id == ruleId })
    }
}
