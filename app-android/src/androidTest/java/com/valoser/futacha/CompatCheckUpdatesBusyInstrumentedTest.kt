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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L40: "更新の確認" from the catalog runs one check of all the tabs at a time, and tells when it ends. */
class CompatCheckUpdatesBusyInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l40-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private val threadUrl = "${boardUrl}res/401.htm"
    private val item = CatalogItem(id = "401", title = "L40", thumbnailUrl = null, fullImageUrl = null,
        threadUrl = threadUrl, replyCount = 1)
    private val hold = AtomicBoolean(false)
    private val checks = AtomicInteger(0)
    private val gate = CompletableDeferred<Unit>()
    private lateinit var storage: AndroidCompatibilityStore

    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
            if (hold.get()) { checks.incrementAndGet(); gate.await() }
            return listOf(item)
        }
    }

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(boardKey, "L40", boardUrl, boardUrl, 0))
            storage.openTab(CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, boardKey, "L40", "401", "L40",
                insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1))
        }
    }

    @After fun cleanup() {
        gate.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    private fun checkFromTheOtherMenu() {
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().isNotEmpty() }
        val overflows = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        rule.onAllNodesWithContentDescription("その他")[overflows.lastIndex].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("更新の確認").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("更新の確認").performClick()
        rule.waitForIdle()
    }

    @Test fun aSecondCheckWhileOneRunsIsToldNotStarted() {
        rule.setContent { MaterialTheme { CompatibilityApp(store = storage, repository = repo, onExitApplication = {}) } }
        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("compat-catalog-item-401", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        hold.set(true)

        checkFromTheOtherMenu()
        rule.waitUntil(5_000) { checks.get() == 1 }
        checkFromTheOtherMenu()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("全タブの更新確認中です").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(500)
        assertEquals("One check of all the tabs at a time", 1, checks.get())

        gate.complete(Unit)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("全タブの更新確認が終わりました", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
