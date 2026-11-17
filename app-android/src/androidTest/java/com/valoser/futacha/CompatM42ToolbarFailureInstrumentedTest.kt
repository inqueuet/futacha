@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.database.sqlite.SQLiteException
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
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Actual catalog/thread hosts and retry, with only toolbar reads failing; private SQLite fixture. */
class CompatM42ToolbarFailureInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val database = "audit_m42_toolbar.db"
    private var backing: AndroidCompatibilityStore? = null
    private val fail = AtomicBoolean(true)
    private val failures = AtomicInteger()
    private val saves = AtomicInteger()
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/811.htm"
    private val catalogItems = listOf(CatalogItem(id = "811", title = "M42 catalog", threadUrl = threadUrl,
        thumbnailUrl = null, fullImageUrl = null, replyCount = 1))
    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalog(board: String, mode: CatalogMode) = catalogItems
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = catalogItems
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = CatalogPageContent(catalogItems)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        runBlocking { backing?.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    @Test fun catalogOpensWithUsableDefaultsAndRetryRestoresItsSavedToolbar() {
        verify(CompatToolbarSurface.CATALOG)
    }
    @Test fun threadOpensWithUsableDefaultsAndRetryRestoresItsSavedToolbar() {
        verify(CompatToolbarSurface.THREAD)
    }

    private fun verify(surface: CompatToolbarSurface) {
        rule.activity.deleteDatabase(database)
        val storage = AndroidCompatibilityStore(rule.activity, databaseName = database).also { backing = it }
        val boardKey = compatBoardKey(boardUrl)
        val tabKey = compatTabKey(threadUrl)
        val custom = compatToolbarMaster(surface).mapIndexed { i, item -> CompatToolbarItem(item.key, i, item.key == "privacy") }
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            // Catalog titles are otherwise truncated to the reference default of four characters.
            storage.savePreference(compatPreferenceStorageKey("catalog", "catalogGridViewTitleLength"), "30")
            storage.savePreference(compatPreferenceStorageKey("catalog", "catalogListViewTitleLength"), "30")
            storage.upsertBoard(CompatBoard(boardKey, "M42 board", boardUrl, boardUrl, 0))
            storage.saveToolbar(surface, custom)
            storage.openTab(CompatTab(tabKey, threadUrl, threadUrl, boardKey, "M42 board", "811", "M42 thread",
                insertedAtEpochMillis = 811, contentUpdatedAtEpochMillis = 811, snapshotRevision = 811))
            storage.saveThreadSnapshot(CompatThreadSnapshot(tabKey, 811, 811,
                posts = listOf(CompatPostSnapshot(position = 0, postNo = "811", timestamp = "", messageHtml = "M42 post"))))
        }
        val wrapped = object : CompatibilityStore by storage {
            override suspend fun loadToolbar(requested: CompatToolbarSurface): List<CompatToolbarItem> {
                if (requested == surface && fail.get()) {
                    failures.incrementAndGet()
                    throw SQLiteException("test toolbar disk failure")
                }
                return storage.loadToolbar(requested)
            }
            override suspend fun saveToolbar(requested: CompatToolbarSurface, items: List<CompatToolbarItem>) {
                saves.incrementAndGet()
                storage.saveToolbar(requested, items)
            }
        }
        rule.setContent { MaterialTheme {
            CompatibilityApp(store = wrapped, repository = if (surface == CompatToolbarSurface.CATALOG) repo else null,
                initialBoardDeepLink = boardUrl.takeIf { surface == CompatToolbarSurface.CATALOG },
                initialThreadDeepLink = threadUrl.takeIf { surface == CompatToolbarSurface.THREAD }, onExitApplication = {})
        } }
        waitContent(surface)
        rule.onAllNodesWithTag("compat-toolbar-loading").assertCountEquals(0)
        rule.onNodeWithTag("compat-toolbar-recovery").assertIsDisplayed()
        rule.onNodeWithTag("compat-toolbar-command-post").performScrollTo().assertIsDisplayed()
        // The default toolbar is operational, and the failure notice does not cover its buttons.
        rule.onNodeWithTag("compat-toolbar-command-search").performScrollTo().performClick()
        // Thread Back intentionally dismisses IME/focus before closing search. Use the explicit
        // close control, and do not count off-screen drawer fields as the active search field.
        rule.onNodeWithContentDescription("検索を閉じる").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("検索を閉じる").fetchSemanticsNodes().isEmpty() }
        waitContent(surface)
        assertEquals(custom, runBlocking { storage.loadToolbar(surface) })
        assertEquals(0, saves.get())
        val previousFailures = failures.get()
        rule.onNodeWithTag("compat-toolbar-retry").performClick()
        rule.waitUntil(5_000) { failures.get() > previousFailures }
        rule.waitForIdle()
        waitContent(surface)
        rule.onNodeWithTag("compat-toolbar-retry").assertIsEnabled()
        fail.set(false)
        rule.onNodeWithTag("compat-toolbar-retry").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-toolbar-recovery").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("compat-toolbar-command-privacy").assertIsDisplayed()
        rule.onAllNodesWithTag("compat-toolbar-command-post").assertCountEquals(0)
        assertEquals(custom, runBlocking { storage.loadToolbar(surface) })
        assertEquals(0, saves.get())
    }

    private fun waitContent(surface: CompatToolbarSurface) {
        rule.waitUntil(10_000) {
            if (surface == CompatToolbarSurface.CATALOG)
                rule.onAllNodesWithText("M42 catalog", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            else rule.onAllNodesWithTag("compat-thread-post-811", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
