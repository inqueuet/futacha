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
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*

/** Review control: real thread/restore/list, local pages only. No posting, images, network or speech. */
class FutaberThreadOpeningPositionInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("opening-position", "開く位置", "test", "https://may.2chan.net/b/", "")
    private fun id(thread: String, index: Int) = "${thread.toLong() + index}"
    private fun page(thread: String) = ThreadPage(thread, null, null, null, (0 until 80).map { index ->
        Post(id(thread, index), author = null, subject = null, timestamp = "",
            messageHtml = "thread-$thread row-$index<br>local fixture line two<br>local fixture line three",
            imageUrl = null, thumbnailUrl = null)
    })
    private fun ref(thread: String) = FutaberThreadRef(board.id, thread, "開く位置", "", 80)
    private var opened by mutableStateOf(ref("810000"))
    private var fetchGate = CompletableDeferred<Unit>().apply { complete(Unit) }
    // Observe the real callback values without returning a shared internal type
    // through this Android test module's helper API.
    private data class ObservedPosition(val ordinal: Int, val offset: Int, val postId: String?, val total: Int)
    private val positions = ConcurrentHashMap<String, ObservedPosition>()
    private lateinit var loader: ImageLoader
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            fetchGate.await()
            return ThreadPageContent(page(threadId))
        }
    }
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() {
        fetchGate.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        loader.shutdown()
    }
    private fun open(ngWords: List<String> = emptyList()) {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FutaberTheme(FutaberThemeMode.Light) {
                    val target = opened
                    FutaberThreadScreen(board, target, repository, isTabbed = false, onToggleTab = {},
                        onOpenManage = {}, onOpenPost = {}, refreshSignal = 0, quoteMode = false, onQuoteLine = {},
                        onOpenDrawer = {}, drawerGestureEnabled = false, ngWords = ngWords, ngHeaders = emptyList(),
                        ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onPageLoaded = {},
                        onScrollSettled = { ordinal, offset, postId, total ->
                            positions[target.threadId] = ObservedPosition(ordinal, offset, postId, total)
                        }, onBack = {})
                }
            }
        }
    }
    private fun awaitPosition(thread: String, expected: Int? = null): ObservedPosition {
        rule.waitUntil(10_000) { positions[thread]?.let { expected == null || it.ordinal == expected } == true }
        return positions.getValue(thread)
    }
    private fun assertStillAt(thread: String, expected: Int) {
        val until = android.os.SystemClock.uptimeMillis() + 600
        do {
            rule.waitForIdle()
            assertEquals(expected, positions.getValue(thread).ordinal)
            android.os.SystemClock.sleep(10)
        } while (android.os.SystemClock.uptimeMillis() < until)
    }
    @Test fun aFirstOpenWithALargeSeenCountStaysAtTheTopAfterDeferredLoadAndNgEvaluation() {
        opened = ref("810000").copy(seenCount = 900)
        fetchGate = CompletableDeferred()
        open(ngWords = (0 until 512).map { "unmatched-ng-$it" })
        rule.onNodeWithTag("futaber-thread-list").assertDoesNotExist()
        fetchGate.complete(Unit)
        awaitPosition("810000", 0)
        assertStillAt("810000", 0)
        rule.onNodeWithText("thread-810000 row-0", substring = true).assertIsDisplayed()
    }
    @Test fun aSavedPostWinsOverAnOutdatedLargeIndexAndTheSeenCount() {
        opened = ref("810000").copy(seenCount = 900, resumePostId = id("810000", 20), resumeIndex = 9_000)
        open()
        val position = awaitPosition("810000", 20)
        assertEquals(id("810000", 20), position.postId)
        assertStillAt("810000", 20)
    }
    @Test fun switchingFromTheBottomToANewThreadStartsAtTheTopAndReopeningUsesItsOwnSavedPosition() {
        open(); awaitPosition("810000", 0)
        rule.onNodeWithTag("futaber-thread-bottom").performClick()
        rule.waitUntil(10_000) { positions["810000"]?.ordinal?.let { it > 60 } == true }
        val saved = positions.getValue("810000")
        rule.runOnUiThread { opened = ref("820000") }
        awaitPosition("820000", 0); assertStillAt("820000", 0)
        positions.remove("810000")
        rule.runOnUiThread {
            opened = ref("810000").copy(resumePostId = saved.postId, resumeIndex = saved.ordinal, resumeOffset = saved.offset)
        }
        val restored = awaitPosition("810000", saved.ordinal)
        assertEquals(saved.postId, restored.postId)
        assertEquals(saved.offset, restored.offset)
    }
}
