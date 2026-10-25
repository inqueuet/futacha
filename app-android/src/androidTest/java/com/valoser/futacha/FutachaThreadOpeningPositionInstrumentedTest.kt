@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.image.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*

/** Review controls on the real screen. Local deferred pages, no image requests, network, posts or speech. */
class FutachaThreadOpeningPositionInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("opening-position", "開く位置", "test", "https://may.2chan.net/b/", "")
    private fun id(thread: String, index: Int) = "${thread.toLong() + index}"
    private fun page(thread: String) = ThreadPage(thread, "開く位置", "local-end-$thread", null,
        (0 until 80).map { index -> Post(id(thread, index), author = null, subject = null, timestamp = "",
            messageHtml = "thread-$thread row-$index<br>local fixture line two<br>local fixture line three",
            imageUrl = null, thumbnailUrl = null) })
    private var opened by mutableStateOf("830000")
    private var history by mutableStateOf(emptyList<ThreadHistoryEntry>())
    private var fetchGate = CompletableDeferred<Unit>().apply { complete(Unit) }
    private data class Position(val index: Int, val offset: Int, val postId: String?)
    private val positions = ConcurrentHashMap<String, Position>()
    private lateinit var loader: ImageLoader
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            fetchGate.await(); return ThreadPageContent(page(threadId))
        }
        override suspend fun getThreadContentByUrl(threadUrl: String): ThreadPageContent {
            val thread = threadUrl.substringAfterLast('/').substringBefore('.')
            fetchGate.await(); return ThreadPageContent(page(thread))
        }
    }
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() {
        fetchGate.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        loader.shutdown()
    }
    private fun entry(thread: String = opened) = ThreadHistoryEntry(thread, board.id, "開く位置", "", board.name,
        "${board.url}res/$thread.htm", 1, 900)
    private fun open() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                MaterialTheme {
                    val thread = opened
                    // The real destination uses the same key to separate thread state.
                    key(board.id, thread) {
                        ThreadScreen(board, history, thread, "開く位置", 900, onBack = {}, repository = repository,
                            preferencesState = ScreenPreferencesState("test", threadDisplayMode = ThreadDisplayMode.Flat),
                            onScrollPositionPersist = { target, index, offset, postId -> positions[target] = Position(index, offset, postId) },
                            onScrollPositionPersistImmediately = { target, index, offset, postId -> positions[target] = Position(index, offset, postId) })
                    }
                }
            }
        }
    }
    private fun awaitPosition(thread: String, accepts: (Position) -> Boolean = { true }): Position {
        rule.waitUntil(15_000) { positions[thread]?.let(accepts) == true }
        return positions.getValue(thread)
    }
    private fun assertStaysAtTop(thread: String) {
        awaitPosition(thread) { it.index == 0 }
        val until = android.os.SystemClock.uptimeMillis() + 600
        do {
            rule.waitForIdle()
            assertEquals(0, positions.getValue(thread).index)
            rule.onAllNodesWithText("thread-$thread row-0", substring = true).onFirst().assertIsDisplayed()
            android.os.SystemClock.sleep(10)
        } while (android.os.SystemClock.uptimeMillis() < until)
    }
    @Test fun aFirstOpenStaysAtTheTopAfterTheDeferredPageArrives() {
        fetchGate = CompletableDeferred()
        open()
        rule.onAllNodesWithText("thread-830000 row-0", substring = true).assertCountEquals(0)
        fetchGate.complete(Unit)
        assertStaysAtTop("830000")
    }
    @Test fun aHistoryCountWithoutASavedPositionDoesNotOpenAtTheBottom() {
        history = listOf(entry()) // 900 known posts, default position 0/0 and no post identity.
        open(); assertStaysAtTop("830000")
    }
    @Test fun aSavedMiddlePostWinsOverAStaleLargeIndex() {
        history = listOf(entry().copy(lastReadItemIndex = 9_000, lastReadPostId = id("830000", 20)))
        open()
        val saved = awaitPosition("830000") { it.postId == id("830000", 20) }
        assertTrue(saved.index in 20..25) // Header items precede the post rows.
        rule.onAllNodesWithText("thread-830000 row-20", substring = true).onFirst().assertIsDisplayed()
        val until = android.os.SystemClock.uptimeMillis() + 600
        do {
            rule.waitForIdle(); assertEquals(saved, positions.getValue("830000"))
            android.os.SystemClock.sleep(10)
        } while (android.os.SystemClock.uptimeMillis() < until)
    }
    @Test fun aNewThreadStartsAtTheTopAfterLeavingAnotherThreadAtItsBottom() {
        open(); assertStaysAtTop("830000")
        rule.onNodeWithContentDescription("最下部").performClick()
        rule.waitUntil(10_000) { positions["830000"]?.index?.let { it > 60 } == true }
        rule.onNodeWithText("local-end-830000").assertIsDisplayed()
        rule.runOnUiThread { opened = "840000" }
        assertStaysAtTop("840000")
    }
}
