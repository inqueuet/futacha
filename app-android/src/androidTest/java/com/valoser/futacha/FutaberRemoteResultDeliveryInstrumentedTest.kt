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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*

/**
 * L109: a そうだね sent on thread A whose answer comes after A→B→A is shown on A and reloads A; one that comes while B is
 * shown goes to the app's notice. Local pages and a gated repository only; nothing is sent.
 */
class FutaberRemoteResultDeliveryInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("delivery", "届け先", "test", "https://may.2chan.net/b/", "")
    private fun page(thread: String) = ThreadPage(thread, null, null, null, (0 until 5).map { index ->
        Post("${thread.toLong() + index}", author = null, subject = null, timestamp = "",
            messageHtml = "thread-$thread row-$index", imageUrl = null, thumbnailUrl = null)
    })
    private fun ref(thread: String) = FutaberThreadRef(board.id, thread, "届け先", "", 5)
    private var opened by mutableStateOf(ref("830000"))
    private val fetches = ConcurrentHashMap<String, AtomicInteger>()
    private val sendGate = CompletableDeferred<Unit>()
    private val notices = CopyOnWriteArrayList<String>()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var loader: ImageLoader
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            fetches.computeIfAbsent(threadId) { AtomicInteger() }.incrementAndGet()
            return ThreadPageContent(page(threadId))
        }
        override suspend fun voteSaidane(board: String, threadId: String, postId: String) { sendGate.await() }
    }
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() {
        sendGate.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        appScope.cancel(); loader.shutdown()
    }
    private fun open() {
        val tasks = FutaberAppTasks(appScope) { notices += it }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader,
                LocalFutaberAppTasks provides tasks, LocalFutaberDisplaySettings provides FutaberDisplaySettings(extQuickSaidane = true)) {
                FutaberTheme(FutaberThemeMode.Light) {
                    FutaberThreadScreen(board, opened, repository, isTabbed = false, onToggleTab = {},
                        onOpenManage = {}, onOpenPost = {}, refreshSignal = 0, quoteMode = false, onQuoteLine = {},
                        onOpenDrawer = {}, drawerGestureEnabled = false, ngWords = emptyList(), ngHeaders = emptyList(),
                        ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onPageLoaded = {},
                        onScrollSettled = { _, _, _, _ -> }, onBack = {})
                }
            }
        }
    }
    private fun awaitThread(thread: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("thread-$thread row-0", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun sendSaidane() {
        rule.onAllNodesWithText("そうだね ", substring = true)[0].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("処理中です", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun aResultArrivingAfterReturningToTheSendingThreadIsShownThereAndReloadsIt() {
        open(); awaitThread("830000")
        sendSaidane()
        rule.runOnUiThread { opened = ref("840000") }; awaitThread("840000")
        rule.runOnUiThread { opened = ref("830000") }; awaitThread("830000")
        val before = fetches.getValue("830000").get()
        sendGate.complete(Unit)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("そうだねを送信しました").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { fetches.getValue("830000").get() == before + 1 }
        assertTrue("Shown on A, not as the app's notice: $notices", notices.isEmpty())
    }

    @Test fun aResultArrivingOnAnotherThreadGoesToTheAppNoticeAndTheOtherThreadCanSend() {
        open(); awaitThread("830000")
        sendSaidane()
        rule.runOnUiThread { opened = ref("840000") }; awaitThread("840000")
        sendGate.complete(Unit)
        rule.waitUntil(5_000) { notices.contains("そうだねを送信しました") }
        rule.onAllNodesWithText("そうだねを送信しました").assertCountEquals(0)
    }
}
