@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.*
import org.junit.Assert.*

/** Forces a real frame between page arrival and the existing off-main row preparation.
 * No product hook, network, media request, or audio driver is used. */
class FtutterOpeningScrollInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("opening-scroll", "Opening fixture", "test", "https://may.2chan.net/b/", "")
    private val rowWorkEntered = CountDownLatch(1)
    private val releaseRows = CountDownLatch(1)
    private val fetches = AtomicInteger()
    private lateinit var loader: ImageLoader
    private val posts = object : AbstractList<Post>() {
        private val data = (100..219).map { n -> Post("$n", author = null, subject = null, timestamp = "",
            messageHtml = "opening-body-$n<br>local fixture line<br>local fixture line", imageUrl = null, thumbnailUrl = null) }
        override val size: Int get() = data.size
        override fun get(index: Int): Post {
            // Gate only the existing row worker, leaving all UI/lifecycle/repository reads free.
            if (releaseRows.count != 0L && Looper.myLooper() != Looper.getMainLooper() &&
                Thread.currentThread().stackTrace.any { it.className.endsWith(".FtutterThreadWork") }) {
                rowWorkEntered.countDown()
                check(releaseRows.await(15, TimeUnit.SECONDS)) { "Fixture did not release the bounded row worker" }
            }
            return data[index]
        }
    }
    private val page = ThreadPage("100", null, null, null, posts)
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            fetches.incrementAndGet()
            return ThreadPageContent(page)
        }
    }
    private val entry = ThreadHistoryEntry(threadId = "100", boardId = board.id, title = "Opening fixture", titleImageUrl = "",
        boardName = board.name, boardUrl = board.url, lastVisitedEpochMillis = 1L, replyCount = posts.size)
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() {
        releaseRows.countDown()
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        if (::loader.isInitialized) loader.shutdown()
    }
    private fun open(history: ThreadHistoryEntry? = null, readCount: Int? = null, cached: Boolean = false) {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterThreadScreen(board, FtutterThreadRef(board.id, "100", "Opening fixture"), repository, null,
                        emptyMap(), parseFtutterDesign(emptyMap()), history, emptySet(), readCount,
                        { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, {},
                        initialPage = if (cached) page else null, refreshOnOpen = !cached)
                }
            }
        }
        rule.waitUntil(5_000) { rowWorkEntered.count == 0L &&
            rule.onAllNodesWithTag("ftutter-end-band").fetchSemanticsNodes().isNotEmpty() }
        // A cold fetch can already have an empty-post ReadMark at index zero; an
        // initial cached page bypasses that empty view and can leave only the end key.
        val marks = rule.onAllNodesWithTag("ftutter-kokomade").fetchSemanticsNodes().size
        android.util.Log.i("AuditOpeningScroll", "before-rows cached=$cached readMarks=$marks")
        if (cached) assertEquals("The cached route must exercise only the end key, without an empty-view ReadMark", 0, marks)
        // The end band is genuinely measured while the post rows are still absent.
        rule.onNodeWithTag("ftutter-end-band").assertIsDisplayed()
        rule.onAllNodesWithTag("ftutter-post").assertCountEquals(0)
        releaseRows.countDown()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-post").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }
    @Test fun aFirstVisitStartsAtTheFirstPostAfterTheRowsArrive() {
        open()
        rule.onNodeWithText("opening-body-100", substring = true).assertIsDisplayed()
        assertEquals(1, fetches.get())
    }
    @Test fun reopeningACachedPageAtASavedZeroWithoutAPostIdStartsAtTheFirstPost() {
        open(entry.copy(lastReadItemIndex = 0, lastReadItemOffset = 0, lastReadPostId = null), cached = true)
        rule.onNodeWithText("opening-body-100", substring = true).assertIsDisplayed()
        assertEquals("The cached route must not fetch another page", 0, fetches.get())
    }
    @Test fun aSavedZeroPositionWithoutAPostIdAlsoStartsAtTheFirstPost() {
        open(entry.copy(lastReadItemIndex = 0, lastReadItemOffset = 0, lastReadPostId = null))
        rule.onNodeWithText("opening-body-100", substring = true).assertIsDisplayed()
    }
    @Test fun anAllReadCountWithoutASavedViewportDoesNotMeanJumpToTheBottom() {
        open(history = null, readCount = posts.size)
        rule.onNodeWithText("opening-body-100", substring = true).assertIsDisplayed()
    }
    @Test fun anExplicitSavedMiddlePostStillRestoresItsViewport() {
        open(entry.copy(lastReadItemIndex = 41, lastReadItemOffset = 0, lastReadPostId = "140"))
        rule.onNodeWithText("opening-body-140", substring = true).assertIsDisplayed()
    }
    @Test fun anExplicitSavedFinalPostStillRestoresTheBottom() {
        open(entry.copy(lastReadItemIndex = 120, lastReadItemOffset = 0, lastReadPostId = "219"))
        rule.onNodeWithText("opening-body-219", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("ftutter-end-band").assertIsDisplayed()
    }
    @Test fun anExplicitRefreshDoesNotReapplyTheOpeningTopAfterManualScrolling() {
        open()
        rule.onNodeWithTag("ftutter-thread-list").performScrollToIndex(61)
        rule.onNodeWithText("opening-body-160", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("ftutter-thread-tool-refresh").performClick()
        rule.waitUntil(10_000) { fetches.get() == 2 }
        rule.waitForIdle()
        rule.onNodeWithText("opening-body-160", substring = true).assertIsDisplayed()
    }
}
