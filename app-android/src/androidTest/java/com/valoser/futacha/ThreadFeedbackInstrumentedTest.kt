@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class ThreadFeedbackInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val db = "thread_feedback_test.db"
    private val board = "https://may.2chan.net/b/"
    private val url = "${board}res/95.htm"
    private val calls = mutableListOf<String>()

    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { store.initialize(); store.savePreference("compat.commonUsedVersion", "1.0") }
        loader = ImageLoader.Builder(rule.activity).components { add(TutorialImageFetcherFactory()) }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    private fun waitTag(tag: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun openThread(related: Boolean = false) {
        val item = CatalogItem(id = "95", threadUrl = url, title = "FEEDBACK",
            thumbnailUrl = null, fullImageUrl = null, replyCount = 2)
        runBlocking {
            store.upsertBoard(CompatBoard(compatBoardKey(board), "mayb", board, board, 0))
            store.savePreference("compat.control.controlPostTapBehavior", if (related) "related" else "legacy")
        }
        val repo = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = listOf(item)
            override suspend fun getThreadByUrl(threadUrl: String) = ThreadPage("95", "mayb", null, null, listOf(
                Post("95", author = null, subject = null, timestamp = "", messageHtml = "PARENT", imageUrl = null, thumbnailUrl = null),
                Post("96", author = null, subject = null, timestamp = "", messageHtml = "UNRELATED", imageUrl = null, thumbnailUrl = null),
                Post("97", author = null, subject = null, timestamp = "", messageHtml = "&gt;No.95<br>CHILD", imageUrl = null,
                    thumbnailUrl = null, quoteReferences = listOf(QuoteReference(">>95", listOf("95"))))))
            override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
                calls.add(postId)
                error("simulated offline")
            }
        }
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                CompatibilityApp(store = store, repository = repo, imageLoader = loader, onExitApplication = {})
            }
        } }
        rule.onNodeWithText(board).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("FEEDBACK").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("FEEDBACK").performClick()
        waitTag("compat-thread-post-97")
    }

    @Test fun compatibilityPreviewIdentifiesThePostAndDelFailureStillRegistersOnlyThatPostNg() {
        openThread()
        rule.onNodeWithText("PARENT").performTouchInput { longClick() }
        waitTag("post-action-preview")
        rule.onNode(hasText("PARENT") and hasAnyAncestor(hasTestTag("post-action-preview"))).assertIsDisplayed()
        rule.onNodeWithText("del").performClick()
        rule.onNodeWithText("このレスをNGにも登録").assertIsDisplayed()
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("送信する").performClick()
        rule.waitUntil(5_000) { calls.size == 1 }
        rule.waitUntil(5_000) { runBlocking { store.ngRules.first() }.any { it.kind == CompatNgKind.THREAD_POST_NO && it.normalizedValue == "95" } }
        assertEquals(listOf("95"), calls)
        rule.onNodeWithText("削除依頼を送信できませんでした。\nこのレスをNGに登録しました。").assertExists()
    }

    @Test fun relatedTapShowsParentAndChildAndHeaderLongPressOpensTheMenu() {
        openThread(related = true)
        rule.onNodeWithText("PARENT").performTouchInput { click() }
        waitTag("compat-quote-popup")
        rule.onNode(hasText("PARENT") and hasAnyAncestor(hasTestTag("compat-quote-popup"))).assertExists()
        rule.onNode(hasText("CHILD", substring = true) and hasAnyAncestor(hasTestTag("compat-quote-popup"))).assertExists()
        rule.onNode(hasText("UNRELATED") and hasAnyAncestor(hasTestTag("compat-quote-popup"))).assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        waitTag("compat-thread-post-97")
        rule.onNodeWithTag("compat-thread-post-97").performTouchInput { longClick(androidx.compose.ui.geometry.Offset(width / 2f, 3f)) }
        waitTag("post-action-preview")
        rule.onNode(hasText("No.97") and hasAnyAncestor(hasTestTag("post-action-preview"))).assertExists()
    }

    @Test fun modernActionSheetIncludesScrollableBodyAndExplicitDelConfirmation() {
        var sent = 0
        rule.setContent { MaterialTheme {
            ThreadPostActionSheet(Post("55", author = null, subject = null, timestamp = "",
                messageHtml = "SELECTED BODY<br>" + "Long line<br>".repeat(30), imageUrl = null, thumbnailUrl = null),
                onDismiss = {}, onQuote = {}, onNgRegister = {}, onSaidane = {}, onDelRequest = { sent++ }, onDelete = {})
        } }
        waitTag("post-action-preview")
        rule.onNodeWithText("SELECTED BODY", substring = true).assertExists()
        rule.onNodeWithText("DEL 依頼").performClick()
        assertEquals(0, sent)
        rule.onNodeWithText("通報する").performClick()
        assertEquals(1, sent)
    }

    @Test fun modernRelatedTapAndLongPressKeepUrlLinksWorking() {
        var related = 0; var menus = 0; var links = 0
        val preferences = mutableStateOf(mapOf("compat.control.controlPostTapBehavior" to "related"))
        rule.setContent { MaterialTheme {
            val features = remember { FutachaSharedFeatures(store, preferences, null, null, null, null, "1", {}) }
            CompositionLocalProvider(LocalFutachaSharedFeatures provides features, LocalFutachaImageLoader provides loader) {
                Column {
                    for (body in listOf("BODY TARGET", "https://example.org/")) {
                        ThreadPostCard(Post(body, author = null, subject = null, timestamp = "", messageHtml = body,
                            imageUrl = null, thumbnailUrl = null), isOp = false, posterIdLabel = null,
                            posterIdValue = null, saidaneLabelOverride = null, onQuoteClick = {},
                            onUrlClick = { links++ }, onLongPress = { menus++ }, onRelatedClick = { related++ })
                    }
                }
            }
        } }
        rule.onNodeWithText("BODY TARGET").performTouchInput { click() }
        assertEquals(1, related)
        rule.onNodeWithText("BODY TARGET").performTouchInput { longClick() }
        assertEquals(1, menus)
        rule.onNodeWithText("https://example.org/").performTouchInput { click() }
        assertEquals(1, links)
        assertEquals(1, related)
        rule.runOnIdle { preferences.value = mapOf("compat.control.controlPostTapBehavior" to "legacy") }
        rule.onNodeWithText("BODY TARGET").performTouchInput { click() }
        assertEquals(1, related)
    }

    @Test fun packagedTutorialImageDecodesWithoutANetworkFetcher() = runBlocking {
        val result = loader.execute(ImageRequest.Builder(rule.activity)
            .data("https://www.example.com/b/thumb/1762576973515s.jpg").build())
        assertTrue(result.toString(), result is SuccessResult)
        assertTrue((result as SuccessResult).image.width > 1)
    }

    @Test fun settingsExplainLayoutsAndStorageAndPersistTheTapChoice() {
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                var path by remember { mutableStateOf("catalog") }
                val preferences by store.preferences.collectAsState(emptyMap())
                Column {
                    TextButton(onClick = { path = "storage" }) { Text("TEST STORAGE") }
                    TextButton(onClick = { path = "control" }) { Text("TEST CONTROL") }
                    CompatSettingsScreen(path, store, preferences, fileSystem = null,
                        onNavigate = { path = it }, onBack = {}, onOpenSavedThreads = {})
                }
            }
        } }
        rule.onNodeWithText("グリッド：格子状").assertIsDisplayed()
        rule.onNodeWithText("リスト：縦一列").assertIsDisplayed()
        rule.onNodeWithText("TEST STORAGE").performClick()
        waitTag("storage-usage-guide")
        rule.onNodeWithText("容量の内訳と整理").assertIsDisplayed()
        rule.onNodeWithText("TEST CONTROL").performClick()
        rule.onNodeWithTag("compat-settings-list-control").performScrollToNode(hasText("レスのタップ操作"))
        rule.onNodeWithText("レスのタップ操作").performClick()
        rule.onNodeWithText("タップで関連レス・長押しでメニュー").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.control.controlPostTapBehavior") } == "related"
        }
    }
}
