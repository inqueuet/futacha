@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import org.junit.*
import org.junit.Assert.*

/** Local repository only; no posting, downloads, or playback. */
class FtutterThreadMemoryInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("memory", "Memory board", "test", "https://may.2chan.net/b/", "")
    private val url = "${board.url}res/100.htm"
    private val old = ThreadPage("100", null, null, null, listOf(post("100")))
    private val fresh = old.copy(posts = old.posts + post("101"))
    private val entry = ThreadHistoryEntry("100", board.id, "Memory thread", "", board.name, board.url, 1, 1)
    private var fetches = 0
    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            fetches++; return ThreadPageContent(fresh)
        }
    }
    private lateinit var loader: ImageLoader
    private val client = HttpClient(MockEngine { respond("") })
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "memory-body-$id", imageUrl = null, thumbnailUrl = null)
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        loader.shutdown(); client.close()
    }
    @Composable private fun Screen(memory: FtutterThreadMemory, history: ThreadHistoryEntry) {
        CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
            FtutterTheme(null, FtutterBrightness.LIGHT) {
                FtutterThreadScreen(board, FtutterThreadRef(board.id, "100", entry.title), repo, client,
                    emptyMap(), parseFtutterDesign(emptyMap()), history, emptySet(), null,
                    { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, {},
                    initialPage = memory.page(url), refreshOnOpen = false)
            }
        }
    }
    @Test fun reopeningAnUpdatedTabFetchesTheNewPostsEvenWhenTabRefreshIsOff() {
        val memory = FtutterThreadMemory().apply { put(url, old) }
        var shown by mutableStateOf(true)
        var history by mutableStateOf(entry)
        rule.setContent { if (shown) Screen(memory, history) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("memory-body-100").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, fetches)
        rule.runOnUiThread { shown = false }; rule.waitForIdle()
        rule.runOnUiThread { history = entry.copy(replyCount = 2); shown = true }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("memory-body-101").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, fetches)
    }
    @Test fun reopeningAKnownFallenThreadRetainsPostsAndOffersArchiveRetrieval() {
        val memory = FtutterThreadMemory().apply { put(url, old) }
        rule.setContent { Screen(memory, entry.copy(isAutoRefreshDisabled = true, threadStatus = ThreadStatus(isDead = true))) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("memory-body-100").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("過去ログを取得").assertIsDisplayed()
        rule.onNode(hasText("スレッドは落ちています") and hasAnyAncestor(hasTestTag("ftutter-end-band")))
            .assertIsDisplayed()
        assertEquals(0, fetches)
    }
}
