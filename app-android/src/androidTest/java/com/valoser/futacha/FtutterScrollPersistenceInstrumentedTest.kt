@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.*
import org.junit.Assert.*

/** Real scroll/lifecycle callbacks, with the initial read marker and cached rows; no live server or audio. */
class FtutterScrollPersistenceInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("scroll-review", "fixture", "test", "https://may.2chan.net/b/", "")
    private val posts = (100..219).map { n -> Post("$n", author = null, subject = null, timestamp = "",
        messageHtml = "scroll-review-body-$n<br>line two<br>line three", imageUrl = null, thumbnailUrl = null) }
    private val page = ThreadPage("100", null, null, null, posts)
    private data class Saved(val index: Int, val offset: Int, val id: String?, val total: Int)
    private val saved = CopyOnWriteArrayList<Saved>()
    private var shown by mutableStateOf(true)
    private var entry by mutableStateOf(ThreadHistoryEntry("100", board.id, "fixture", "", board.name, board.url,
        lastVisitedEpochMillis = 1, replyCount = 120))
    private lateinit var loader: ImageLoader
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle(); loader.shutdown()
    }
    private fun open() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    if (shown) FtutterThreadScreen(board = board, ref = FtutterThreadRef(board.id, "100", "fixture"),
                        repository = FakeBoardRepository(), httpClient = null, preferences = emptyMap(), design = parseFtutterDesign(emptyMap()),
                        history = entry, selfIds = emptySet(), storedReadCount = null, savePreference = { _, _ -> }, onReadCount = {},
                        onPageLoaded = {}, onLivePageConfirmed = {}, onSavePosition = { index, offset, id, total -> saved += Saved(index, offset, id, total) },
                        onOpenThreadUrl = {}, onOpenSettings = {}, onBack = {}, onNotice = {}, initialPage = page, refreshOnOpen = false)
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-post").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }
    @Test fun settledScrollingAndBackgroundingPersistTheLatestViewport() {
        entry = entry.copy(lastReadItemIndex = 11, lastReadPostId = "110")
        open()
        rule.onNodeWithTag("ftutter-thread-list").performScrollToIndex(61)
        rule.onNodeWithText("scroll-review-body-160", substring = true).assertIsDisplayed()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        rule.waitUntil(5_000) { saved.lastOrNull()?.id == "160" }
        assertEquals("160", saved.last().id)
        rule.onNodeWithTag("ftutter-thread-list").performScrollToIndex(81)
        rule.onNodeWithText("scroll-review-body-180", substring = true).assertIsDisplayed()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        android.os.SystemClock.sleep(300)
        assertEquals("Background must flush the position even before the next debounce", "180", saved.last().id)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.runOnUiThread { shown = false }
        rule.waitForIdle()
        assertTrue(saved.isNotEmpty())
        assertEquals("180", saved.last().id)
    }
    @Test fun leavingAndReopeningRestoresFirstMiddleAndFinalViewports() {
        open()
        for ((index, marker) in listOf(0 to "100", 61 to "160", 120 to "219")) {
            rule.onNodeWithTag("ftutter-thread-list").performScrollToIndex(index)
            rule.onAllNodesWithText("scroll-review-body-$marker", substring = true).onLast().assertIsDisplayed()
            rule.runOnUiThread { shown = false }
            rule.waitForIdle()
            val last = saved.last()
            rule.runOnUiThread {
                entry = entry.copy(lastReadItemIndex = last.index, lastReadItemOffset = last.offset, lastReadPostId = last.id)
                shown = true
            }
            rule.waitUntil(10_000) { rule.onAllNodesWithText("scroll-review-body-$marker", substring = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithText("scroll-review-body-$marker", substring = true).onLast().assertIsDisplayed()
        }
    }
    @Test fun aLegacyZeroIndexHonorsItsOffsetInsteadOfOpeningAtTheEnd() {
        entry = entry.copy(lastReadItemIndex = 0, lastReadItemOffset = 140, lastReadPostId = null)
        open()
        rule.runOnUiThread { shown = false }; rule.waitForIdle()
        assertEquals("100", saved.last().id)
        assertTrue(saved.last().index <= 2)
        assertTrue("The remembered offset must not be silently ignored", saved.last().offset > 0)
    }
    @Test fun aCachedDefaultZeroOpeningRestoresTheActualBeginning() {
        open()
        rule.runOnUiThread { shown = false }; rule.waitForIdle()
        assertEquals(0, saved.last().index)
        assertEquals(0, saved.last().offset)
        assertEquals(120, saved.last().total)
    }
}
