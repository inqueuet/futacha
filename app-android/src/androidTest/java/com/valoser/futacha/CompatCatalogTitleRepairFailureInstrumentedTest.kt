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

/** L45: when repairing the title of a thread opened from the catalog fails, the opened thread and its tab stay. */
class CompatCatalogTitleRepairFailureInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l45-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private lateinit var storage: AndroidCompatibilityStore
    private val titleAvailable = java.util.concurrent.atomic.AtomicBoolean(false)
    // A catalog row whose subject is only its reply count, as some boards answer: its title is repaired after opening.
    private val item = CatalogItem(id = "845", title = "5", thumbnailUrl = null, fullImageUrl = null,
        threadUrl = "${boardUrl}res/845.htm", replyCount = 5)
    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = CatalogPageContent(listOf(item))
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = listOf(item)
        // The catalog's own lookup finds nothing; the lookup after opening (the repair) finds the title.
        override suspend fun resolveCatalogDisplayTitle(board: String, item: CatalogItem, allowFallbackHeadScan: Boolean) =
            if (titleAvailable.get()) "直した題名" else null
    }

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(boardKey, "L45", boardUrl, boardUrl, 0))
        }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    @Test fun aFailedTitleRepairLeavesTheOpenedThreadAndItsTab() {
        val failing = object : CompatibilityStore by storage {
            override suspend fun updateTabIfPresent(tabKey: String, transform: (CompatTab) -> CompatTab?): Boolean =
                throw IOException("L45 test: disk full")
        }
        rule.setContent { MaterialTheme { CompatibilityApp(failing, repository = repo, onExitApplication = {}) } }
        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-catalog-item-845", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        titleAvailable.set(true)
        rule.onNodeWithTag("compat-catalog-item-845", useUnmergedTree = true).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isEmpty() }
        // The repair runs after opening (it waits for the head lookup); give it time to fail.
        Thread.sleep(3_000)
        rule.waitForIdle()
        rule.onAllNodesWithText("スレッドを開けませんでした", substring = true).assertCountEquals(0)
        assertTrue("The opened thread keeps its tab", runBlocking { storage.tabs.first() }.any { it.threadNo == "845" })
    }
}
