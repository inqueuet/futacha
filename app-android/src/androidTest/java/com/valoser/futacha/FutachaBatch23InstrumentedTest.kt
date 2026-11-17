@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")

package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.*
import com.valoser.futacha.shared.util.createFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * ふたちゃバッチ2・3: レス長押しの3項目、メディアのみの保存、下スワイプで閉じる、
 * 未登録板の「板を追加して開く」、「確認して落ちたスレを閉じる」。実サーバーへは送らない。
 */
class FutachaBatch23InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val database = "futacha_batch23_ui_test.db"
    private val boardUrl = "https://may.2chan.net/b/"

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun page(count: Int = 2) = ThreadPage("1001", "操作確認", null, null, (1..count).map { index ->
        Post("${1000 + index}", index - 1, "としあき", null, "10/12 12:00",
            messageHtml = "POST-$index-LINE-1<br>POST-$index-LINE-2",
            imageUrl = "${boardUrl}src/17000000000$index.jpg", thumbnailUrl = "${boardUrl}thumb/17000000000${index}s.jpg")
    })

    private fun dumpTexts(): String = rule.onAllNodes(hasText("", substring = true), useUnmergedTree = true)
        .fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString("|")
        }.joinToString(" / ")

    private fun waitOrDump(timeoutMs: Long, label: String, condition: () -> Boolean) {
        try { rule.waitUntil(timeoutMs, condition) }
        catch (e: androidx.compose.ui.test.ComposeTimeoutException) { throw AssertionError("$label: ${dumpTexts()}", e) }
    }

    private val pageRepository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = page()
        override suspend fun getThreadByUrl(threadUrl: String) = page()
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page())
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page())
    }

    private fun host(
        repository: BoardRepository = FakeBoardRepository(),
        httpClient: HttpClient? = null,
        withFileSystem: Boolean = false,
        content: @Composable () -> Unit
    ) {
        val fs = if (withFileSystem) createFileSystem(rule.activity) else null
        rule.setContent { FutachaTheme(ThemeMode.Light, ThemePalette.Current) {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                ProvideFutachaSharedFeatures(store, httpClient, repository, fs, null, "test") { content() }
            }
        } }
    }

    private fun openThread(onClipboard: (androidx.compose.ui.platform.ClipboardManager) -> Unit = {}) {
        host(pageRepository) {
            onClipboard(LocalClipboardManager.current)
            ThreadScreen(board = BoardSummary("batch23", "操作確認", "test", boardUrl, ""), history = emptyList(),
                threadId = "1001", threadTitle = "操作確認", initialReplyCount = 2, repository = pageRepository,
                preferencesState = ScreenPreferencesState("test"), onBack = {})
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("POST-1-LINE-1", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("POST-1-LINE-1", substring = true).onFirst().performTouchInput { longClick() }
        waitOrDump(5_000, "sheet") { rule.onAllNodesWithText("削除 (本人)").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun postActionSheetAddsThreeRowsAfterDeleteAndCopiesChosenItemsToTheClipboard() {
        var clipboard: androidx.compose.ui.platform.ClipboardManager? = null
        openThread { clipboard = it }
        listOf("引用", "そうだね", "削除 (本人)").forEach { rule.onNodeWithText(it).assertExists() }
        val deleteTop = rule.onNodeWithText("削除 (本人)").fetchSemanticsNode().boundsInRoot.top
        listOf("クイック引用返信", "項目を選んでコピー", "語句を選んでGoogle検索").forEach { label ->
            val top = rule.onNodeWithText(label).assertExists().fetchSemanticsNode().boundsInRoot.top
            assertTrue("$label は「削除 (本人)」の後", top > deleteTop)
        }
        rule.onNodeWithText("項目を選んでコピー").performScrollTo().performClick()
        rule.onNodeWithText("クリップボードにコピー").assertIsNotEnabled()
        rule.onNodeWithTag("futacha-post-item-0").performClick()
        rule.onNodeWithText("No: No.1001").assertExists()
        rule.onNodeWithText("クリップボードにコピー").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("No.1001", clipboard?.getText()?.text) }
        rule.onNodeWithText("クリップボードにコピーしました").assertIsDisplayed()
        rule.onAllNodesWithText("閉じる").onLast().performClick()
        // The search picker lists body words; cancelling does not open a browser.
        rule.onAllNodesWithText("POST-1-LINE-1", substring = true).onFirst().performTouchInput { longClick() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("語句を選んでGoogle検索").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("語句を選んでGoogle検索").performScrollTo().performClick()
        rule.onNodeWithText("No.1001 の語句で検索").assertIsDisplayed()
        rule.onNodeWithText("Google検索").assertIsNotEnabled()
        rule.onAllNodesWithText("POST", substring = true).onFirst().assertExists()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onAllNodesWithText("No.1001 の語句で検索").assertCountEquals(0)
    }

    @Test fun quickQuoteOpensTheReplyFormWithTheQuotedBody() {
        openThread()
        rule.onNodeWithText("クイック引用返信").performScrollTo().performClick()
        val expected = ">POST-1-LINE-1\n>POST-1-LINE-2\n"
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasSetTextAction() and hasText(expected)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff3366cc.toInt()) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test fun mediaOnlySaveWritesZipAndFolderAndIsOfferedOnlyWhenRequested() {
        val bytes = jpegBytes()
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            synchronized(requests) { requests += request.url.toString() }
            if (request.url.host == "may.2chan.net" && request.url.encodedPath.contains("/src/")) {
                respond(bytes, headers = headersOf("Content-Type", "image/jpeg"))
            } else respond("missing", HttpStatusCode.NotFound)
        })
        val saveDir = File(rule.activity.getExternalFilesDir(null), "batch23-save-${System.nanoTime()}").apply { mkdirs() }
        runBlocking { store.savePreference("compat.storage.dummyDownloadDir", saveDir.absolutePath) }
        var offer by mutableStateOf(false)
        var round by mutableIntStateOf(0)
        var features: FutachaSharedFeatures? = null
        try {
            host(httpClient = client, withFileSystem = true) {
                features = LocalFutachaSharedFeatures.current
                key(round, offer) {
                    FutachaPageSaveDialog(requireNotNull(LocalFutachaSharedFeatures.current), page(), compatBoardKey(boardUrl),
                        "may", boardUrl, "保存テスト", onDismiss = {}, offerMediaOnly = offer)
                }
            }
            rule.waitUntil(10_000) { features?.preferences?.get("compat.storage.dummyDownloadDir") != null }
            // Other callers (offerMediaOnly = false) keep the old list.
            rule.onNodeWithText("HTMLのみ").assertExists()
            rule.onAllNodesWithText("メディアのみ（ZIP）").assertCountEquals(0)
            rule.runOnIdle { offer = true }
            val htmlTop = rule.onNodeWithText("HTMLと全メディア").fetchSemanticsNode().boundsInRoot.top
            assertTrue(rule.onNodeWithText("メディアのみ（ZIP）").fetchSemanticsNode().boundsInRoot.top > htmlTop)
            rule.onNodeWithText("メディアのみ（ZIP）").performClick()
            rule.waitUntil(20_000) {
                rule.onAllNodesWithText("成功2件 / 失敗0件", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            val zips = saveDir.walkTopDown().filter { it.isFile && it.name.endsWith(".zip") }.toList()
            assertEquals(zips.toString(), 1, zips.size)
            assertTrue(zips.single().length() > bytes.size * 2)
            rule.runOnIdle { round++ }
            rule.onNodeWithText("メディアのみ（フォルダ）").performClick()
            rule.waitUntil(20_000) {
                rule.onAllNodesWithText("メディアを保存しました 成功2件 / 失敗0件", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            val images = saveDir.walkTopDown().filter { it.isFile && it.name.endsWith(".jpg") }.toList()
            assertEquals(images.toString(), setOf("170000000001.jpg", "170000000002.jpg"), images.map { it.name }.toSet())
            assertEquals(1, images.map { it.parentFile }.distinct().size)
        } finally {
            runBlocking { store.savePreference("compat.storage.dummyDownloadDir", "") }
            saveDir.deleteRecursively()
            client.close()
        }
    }

    /** L74: a cancelled media-only folder save says キャンセルしました and how many files it saved. */
    @Test fun aCancelledFolderSaveTellsHowManyFilesItSaved() {
        val bytes = jpegBytes()
        val second = kotlinx.coroutines.CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("170000000002.jpg")) { second.complete(Unit); kotlinx.coroutines.awaitCancellation() }
            if (request.url.host == "may.2chan.net" && request.url.encodedPath.contains("/src/")) {
                respond(bytes, headers = headersOf("Content-Type", "image/jpeg"))
            } else respond("missing", HttpStatusCode.NotFound)
        })
        val saveDir = File(rule.activity.getExternalFilesDir(null), "batch23-cancel-${System.nanoTime()}").apply { mkdirs() }
        runBlocking { store.savePreference("compat.storage.dummyDownloadDir", saveDir.absolutePath) }
        var features: FutachaSharedFeatures? = null
        try {
            host(httpClient = client, withFileSystem = true) {
                features = LocalFutachaSharedFeatures.current
                FutachaPageSaveDialog(requireNotNull(LocalFutachaSharedFeatures.current), page(), compatBoardKey(boardUrl),
                    "may", boardUrl, "保存テスト", onDismiss = {}, offerMediaOnly = true)
            }
            rule.waitUntil(10_000) { features?.preferences?.get("compat.storage.dummyDownloadDir") != null }
            rule.onNodeWithText("メディアのみ（フォルダ）").performClick()
            rule.waitUntil(20_000) { second.isCompleted }
            rule.onNodeWithText("キャンセル").performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodesWithText("キャンセルしました", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("1件のメディアをここまで保存しました", substring = true).assertExists()
            assertEquals(1, saveDir.walkTopDown().count { it.isFile && it.name.endsWith(".jpg") })
        } finally {
            runBlocking { store.savePreference("compat.storage.dummyDownloadDir", "") }
            saveDir.deleteRecursively()
            client.close()
        }
    }

    private fun swipeDownDismissCount(saved: String?): Int {
        saved?.let { runBlocking { store.savePreference("compat.control.controlViewerSwipeClose", it) } }
        var dismissed = 0
        var features: FutachaSharedFeatures? = null
        host {
            features = LocalFutachaSharedFeatures.current
            ThreadMediaPreviewDialogFrame("preview", onDismiss = { dismissed++ }, onNavigateNext = {}, onNavigatePrevious = {}) {
                Box(Modifier.fillMaxSize().testTag("batch23-preview-body"))
            }
        }
        if (saved != null) {
            rule.waitUntil(10_000) { features?.preferences?.get("compat.control.controlViewerSwipeClose") == saved }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("batch23-preview-body").performTouchInput { swipeDown() }
        rule.waitForIdle()
        return dismissed
    }

    @Test fun previewKeepsOpenOnSwipeDownWhenTheSettingIsNotSaved() { assertEquals(0, swipeDownDismissCount(null)) }

    @Test fun previewKeepsOpenOnSwipeDownWhenTheSettingIsOff() { assertEquals(0, swipeDownDismissCount("OFF")) }

    @Test fun previewClosesOnSwipeDownWhenTheSettingIsExplicitlyOn() { assertEquals(1, swipeDownDismissCount("ON")) }

    private fun tab(no: String, favorite: Boolean = false): CompatTab {
        val url = "${boardUrl}res/$no.htm"
        return CompatTab(key = compatTabKey(url), canonicalUrl = url, originalUrl = url, boardKey = compatBoardKey(boardUrl),
            boardName = "may", threadNo = no, title = "タブ$no", favorite = favorite,
            insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = 1_000L)
    }

    @Test fun checkedDeadTabRowAsksTheServerClosesGoneTabsAndKeepsFavorites() {
        runBlocking {
            store.upsertBoard(CompatBoard(compatBoardKey(boardUrl), "may", boardUrl, boardUrl, 0))
            listOf(tab("1"), tab("2"), tab("3", favorite = true)).forEach { store.openTab(it) }
        }
        val probed = mutableListOf<String>()
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun probeThreadGone(threadUrl: String): Boolean {
                synchronized(probed) { probed += threadUrl }
                return threadUrl.endsWith("/2.htm") || threadUrl.endsWith("/3.htm")
            }
        }
        host(repository) {
            FutachaTabsDialog(requireNotNull(LocalFutachaSharedFeatures.current), repository, {}, {})
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("タブ1").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("整理").onFirst().performClick()
        rule.onNodeWithText("落ちたスレを閉じる").assertExists()
        val deadTop = rule.onNodeWithText("落ちたスレを閉じる").fetchSemanticsNode().boundsInRoot.top
        val checked = rule.onNodeWithText("確認して落ちたスレを閉じる").assertExists()
        assertTrue(checked.fetchSemanticsNode().boundsInRoot.top > deadTop)
        checked.performClick()
        rule.waitUntil(15_000) { runBlocking { store.tabs.first().map { it.threadNo }.toSet() } == setOf("1", "3") }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("落ちたスレを1件閉じました").fetchSemanticsNodes().isNotEmpty() }
        // The favorite is protected by default, so it is not even asked about.
        assertTrue(probed.none { it.endsWith("/3.htm") })
        // One "元に戻す" (the list's own), not a second one in the host's snackbar (2026-10-13 全体検証 3.1).
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText("元に戻す").fetchSemanticsNodes().size)
        assertTrue(rule.onAllNodesWithTag("futacha-closed-tabs-undo").fetchSemanticsNodes().isEmpty())
        // 元に戻す restores the closed tab.
        rule.onNodeWithTag("futacha-tabs-undo").assertIsEnabled().performClick()
        rule.waitUntil(10_000) { runBlocking { store.tabs.first().size } == 3 }
        // Restored once, the list's button is disabled again.
        rule.waitUntil(5_000) { rule.onNodeWithTag("futacha-tabs-undo").fetchSemanticsNode().config
            .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
    }

    /**
     * A tab closed elsewhere (the thread's close) is offered by the host's snackbar, and the tab list offers the same
     * pending batch. Restored from the list, the snackbar's offer goes away and the batch is not restored a second time.
     */
    @Test fun aBatchOfferedByTheSnackbarAndTheListIsRestoredOnce() {
        runBlocking {
            store.upsertBoard(CompatBoard(compatBoardKey(boardUrl), "may", boardUrl, boardUrl, 0))
            listOf(tab("1"), tab("2")).forEach { store.openTab(it) }
            // The snackbar stays long enough not to go away on its own during the test.
            store.savePreference("compat.control.controlCloseToastDuration", "30000")
        }
        val features = java.util.concurrent.atomic.AtomicReference<FutachaSharedFeatures?>(null)
        val showList = mutableStateOf(false)
        host {
            features.set(LocalFutachaSharedFeatures.current)
            if (showList.value) FutachaTabsDialog(requireNotNull(LocalFutachaSharedFeatures.current), null, {}, {})
        }
        rule.waitUntil(10_000) { features.get()?.preferences?.get("compat.control.controlCloseToastDuration") == "30000" }
        val batch = requireNotNull(runBlocking { store.closeTabs(setOf(tab("2").key), System.currentTimeMillis()) })
        val onClosed = requireNotNull(features.get()).onTabsClosed
        rule.runOnUiThread { onClosed(batch) }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futacha-closed-tabs-undo").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnUiThread { showList.value = true }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("futacha-tabs-undo").fetchSemanticsNodes().singleOrNull()?.config
                ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
        }
        rule.onNodeWithTag("futacha-tabs-undo").performClick()
        rule.waitUntil(10_000) { runBlocking { store.tabs.first().map { it.threadNo }.toSet() } == setOf("1", "2") }
        // The snackbar no longer offers the batch that was restored.
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futacha-closed-tabs-undo").fetchSemanticsNodes().isEmpty() }
        assertEquals(2, runBlocking { store.tabs.first().size })
    }

    @Test fun unregisteredHistoryBoardCanBeAddedAndOpened() {
        val appStore = createAppStateStore(rule.activity.applicationContext)
        val previousBoards = runBlocking { appStore.boards.first() }
        val previousHistory = runBlocking { appStore.history.first() }
        val tutorial = previousBoards.firstOrNull { it.url.contains("example.com") }
            ?: BoardSummary("tutorial", "チュートリアル", "test", "https://www.example.com/tutorial/futaba.php", "")
        val entry = ThreadHistoryEntry(threadId = "1001", boardId = "unregistered-may", title = "未登録板テストスレ",
            titleImageUrl = "", boardName = "二次元裏テスト", boardUrl = "${boardUrl}res/1001.htm",
            lastVisitedEpochMillis = System.currentTimeMillis(), replyCount = 2)
        try {
            runBlocking {
                appStore.setBoards(listOf(tutorial))
                appStore.setHistory(listOf(entry))
            }
            rule.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                    FutachaApp(stateStore = appStore, sharedRepository = pageRepository)
                }
            }
            rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("メニュー").fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithContentDescription("履歴を開く").onFirst().performClick()
            rule.waitUntil(10_000) {
                runCatching { rule.onAllNodesWithText("未登録板テストスレ").onFirst().assertIsDisplayed() }.isSuccess
            }
            rule.waitForIdle()
            rule.onAllNodesWithText("未登録板テストスレ").onFirst().performClick()
            waitOrDump(10_000, "dialog") { rule.onAllNodesWithText("スレッドを開けませんでした").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("OK").assertExists()
            rule.onNodeWithText("板を追加して開く").performClick()
            rule.waitUntil(15_000) {
                runBlocking { appStore.boards.first() }.any { it.name == "二次元裏テスト" && it.url.contains("may.2chan.net/b") }
            }
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText("POST-1-LINE-1", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onAllNodesWithText("スレッドを開けませんでした").assertCountEquals(0)
        } finally {
            rule.runOnUiThread { rule.activity.setContent {} }
            runBlocking {
                appStore.setBoards(previousBoards)
                appStore.setHistory(previousHistory)
            }
        }
    }
}
