@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.audio.*
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.shared.util.AndroidFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class ThreadFeedbackImprovementsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val clients = mutableListOf<HttpClient>()
    private val database = "thread_feedback_improvements_test.db"
    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
        loader = ImageLoader.Builder(rule.activity).components { add(TutorialImageFetcherFactory()) }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        clients.forEach { it.close() }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    @Test fun bodyLongPressCopiesOnlyTheSelectedUrlAndKeepsPostLongPress() {
        val body = "https://one.example/a\nhttps://two.example/b 日本語の本文"
        var postActions = 0
        var clipboard: androidx.compose.ui.platform.ClipboardManager? = null
        rule.setContent { MaterialTheme {
            clipboard = LocalClipboardManager.current
            ThreadMessageText(body, false, emptyList(), {}, {}, onPlainLongPress = { postActions++ }, quoteLongPressOpensMenu = true)
        } }
        val node = rule.onNodeWithText(body)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        node.performTouchInput { longClick(layouts.single().getBoundingBox(body.indexOf("two.example") + 2).center) }
        rule.onNodeWithTag("body-url-menu").assertExists()
        rule.onNodeWithText("https://two.example/b").assertIsDisplayed()
        capture("body-url-menu")
        rule.onNodeWithText("URLをコピー").performClick()
        rule.runOnIdle { assertEquals("https://two.example/b", clipboard?.getText()?.text); assertEquals(0, postActions) }
        node.performTouchInput { longClick(layouts.single().getBoundingBox(body.indexOf("日本語") + 1).center) }
        rule.runOnIdle { assertEquals(1, postActions) }
        rule.onNodeWithTag("body-url-menu").assertDoesNotExist()
        capture("body-url")
    }

    @Test fun normalCatalogSearchHistorySurvivesClosingAndCanBeDeleted() {
        val client = HttpClient(MockEngine) { engine { addHandler { respond("{\"l\":[]}", HttpStatusCode.OK) } } }.also(clients::add)
        val board = BoardSummary("feedback-catalog", "テスト", "test", "https://may.2chan.net/b/", "")
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            ProvideFutachaSharedFeatures(store, client, FakeBoardRepository(), null, null, "test") {
                FutachaCatalogFeatureHost(board, CatalogMode.Catalog, CatalogUiState.Loading, FakeBoardRepository(), {}, {}, {}, {}, {}, {}) {
                    val search = LocalFutachaCatalogTools.current.first { it.label == "キャッシュ検索" }
                    TextButton(onClick = search.action) { Text("履歴テスト検索") }
                }
            }
        } } }
        rule.onNodeWithText("履歴テスト検索").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("保存する検索語")
        rule.onNodeWithText("検索", useUnmergedTree = true).performClick()
        rule.waitUntil(10000) { runBlocking { store.preferences.first()[COMPAT_CACHE_SEARCH_HISTORY_KEY] == "保存する検索語" } }
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithText("履歴テスト検索").performClick()
        rule.onNode(hasSetTextAction()).performClick()
        rule.onNodeWithContentDescription("保存する検索語 を検索履歴から削除").assertExists().performClick()
        rule.waitUntil(5000) { runBlocking { store.preferences.first()[COMPAT_CACHE_SEARCH_HISTORY_KEY].orEmpty().isEmpty() } }
        capture("cache-search-history")
    }

    @Test fun futaberBodyUrlLongPressOffersTheSameLinkActions() {
        val body = "https://two.example/b"
        rule.setContent { FutaberTheme(FutaberThemeMode.Light) {
            FutaberPostRow(1, Post("95", 0, null, null, "", messageHtml = body, imageUrl = null, thumbnailUrl = null), null, 0, { _, _ -> }, {})
        } }
        rule.onNodeWithText(body).performTouchInput { longClick() }
        rule.onNodeWithTag("body-url-menu").assertExists()
        rule.onNodeWithText("URLを共有").assertIsDisplayed()
        rule.onNodeWithText("リンクを開く").assertIsDisplayed()
        capture("futaber-body-url-menu")
        rule.onNodeWithText("閉じる").performClick()
        capture("futaber-body-url")
    }

    @Test fun archiveSearchShowsUnavailableSourceInsteadOfReportingNoMatch() {
        val client = HttpClient(MockEngine) { engine { addHandler { request ->
            if (request.url.host.endsWith("inqueuet.com")) respond("{\"results\":[]}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            else respond("down", HttpStatusCode.ServiceUnavailable)
        } } }.also(clients::add)
        rule.setContent { MaterialTheme { CompatArchiveSearchDialog(httpClient = client, archiveScope = com.valoser.futacha.shared.network.ArchiveSearchScope("may", "b"), onDismiss = {}, onSelected = {}) } }
        rule.onNode(hasSetTextAction()).performTextInput("test")
        rule.onNodeWithText("検索", useUnmergedTree = true).performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("archive-search-source-failures").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("一部の検索先に接続できず、検索を完了できませんでした").assertIsDisplayed()
        rule.onNodeWithText("一致する過去スレが見つかりません").assertDoesNotExist()
        capture("archive-source-failure")
    }

    @Test fun deletedRowsDisappearAndCanBeRestoredWithoutDroppingOtherPosts() {
        val board = BoardSummary("feedback-delete", "テスト", "test", "https://may.2chan.net/b/", "")
        val page = ThreadPage("95", board.name, null, null, listOf(
            Post("95", 0, null, null, "", messageHtml = "残す本文1", imageUrl = null, thumbnailUrl = null),
            Post("96", 1, null, null, "", messageHtml = "削除された本文", isDeleted = true, imageUrl = null, thumbnailUrl = null),
            Post("97", 2, null, null, "", messageHtml = "残す本文3", imageUrl = null, thumbnailUrl = null)))
        val repo = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
            override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page)
        }
        runBlocking { store.savePreference("compat.thread.threadAdminDeleteShow", "HIDE") }
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            ProvideFutachaSharedFeatures(store, null, repo, null, null, "test") {
                ThreadScreen(board = board, history = emptyList(), threadId = "95", threadTitle = "削除表示", initialReplyCount = 2,
                    repository = repo, preferencesState = ScreenPreferencesState("test"), onBack = {})
            }
        } } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("残す本文3").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("No.96", substring = true).assertDoesNotExist()
        rule.onNodeWithText("残す本文3").assertExists()
        runBlocking { store.savePreference("compat.thread.threadAdminDeleteShow", "ON") }
        rule.waitUntil(5000) { rule.onAllNodesWithText("削除された本文").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("残す本文3").assertExists()
        capture("deleted-row-restored")
    }

    @Test fun speechRatePersistsAcrossReopeningAndCanReturnToDefault() {
        rule.setContent { MaterialTheme {
            val preferences by store.preferences.collectAsState(emptyMap())
            var generation by remember { mutableIntStateOf(0) }
            Column {
                TextButton(onClick = { generation++ }) { Text("設定を開き直す") }
                key(generation) { CompatSettingsScreen("speech", store, preferences, null, onNavigate = {}, onBack = {}) }
            }
        } }
        // The voice list arrives asynchronously after each (re)opening and lengthens the page.
        // Wait until it is in place so a scroll-then-click does not land on a moved row.
        waitForSpeechVoices()
        rule.onNodeWithTag("speech-rate").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        rule.waitUntil(5000) { runBlocking { store.preferences.first()[SPEECH_RATE_KEY] == "2.0" } }
        rule.onNodeWithText("設定を開き直す").performClick()
        waitForSpeechVoices()
        rule.onNodeWithText("速さ：200%").assertExists()
        rule.onNodeWithText("速さ を標準に戻す").performScrollTo().performClick()
        rule.waitUntil(5000) { runBlocking { store.preferences.first()[SPEECH_RATE_KEY] == "1.0" } }
        capture("speech-settings")
    }

    /** The speech page shows an indeterminate indicator until the platform voice list has loaded. */
    private fun waitForSpeechVoices() {
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun futaberSaveRespondsImmediatelyAndRejectsRepeatedRequestsUntilFailure() {
        val release = CompletableDeferred<Unit>()
        val count = AtomicInteger()
        val client = HttpClient(MockEngine) { engine { addHandler {
            count.incrementAndGet(); release.await(); respond("down", HttpStatusCode.ServiceUnavailable)
        } } }.also(clients::add)
        val preferences = mapOf("compat.storage.dummyDownloadDir" to rule.activity.filesDir.absolutePath + "/feedback-saves")
        runBlocking { store.savePreference("compat.storage.dummyDownloadDir", preferences.getValue("compat.storage.dummyDownloadDir")) }
        val post = CompatPostSnapshot(position = 0, postNo = "95", timestamp = "now", messageHtml = "本文", imageUrl = "https://media.example.test/test.png", thumbnailUrl = "https://media.example.test/thumb.png")
        rule.setContent { FutaberTheme(FutaberThemeMode.Light) { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            FutaberViewerScreen(listOf(post), 0, "test", "95", store, preferences, client, AndroidFileSystem(rule.activity), {}, {}, {}, onClose = {})
        } } }
        rule.onNodeWithTag("futaber-viewer-save").performClick()
        if (rule.onAllNodesWithText("ファイルに保存").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("ファイルに保存").performClick()
        rule.waitUntil(10000) { count.get() > 0 }
        rule.onNodeWithTag("futaber-viewer-save-progress").assertIsDisplayed()
        rule.onNodeWithTag("futaber-viewer-save").assertIsNotEnabled()
        rule.onNodeWithTag("futaber-viewer-share").assertIsNotEnabled()
        assertEquals(1, count.get())
        capture("futaber-save-progress")
        release.complete(Unit)
        rule.waitUntil(10000) { rule.onAllNodesWithTag("futaber-viewer-save-progress").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-viewer-save").assertIsEnabled()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/feedback-improvements-$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }; pfd.close()
    }
}
