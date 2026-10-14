@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")

package com.valoser.futacha

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.material3.Text
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.LocalFutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.ProvideFutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.compat.FUTACHA_DRAFT_PERSIST_STORAGE_KEY
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * ふたちゃの「下書きを端末に保存」（機能そろえ バッチ4、既定OFF）。返信フォームに本文と画像（クリップボードからの貼り付け）を
 * 入れてスレッドを離れ、戻ると復元され、送信に成功すると記録と添付ファイルが消える。OFFでは何も書かない。
 * スレッドと送信は模擬リポジトリ（may板のアドレスで中身は模擬）で、実サーバーへは何も送らない。
 */
class FutachaDraftPersistenceInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val database = "futacha_draft_persist_ui_test.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val tabKey = compatTabKey("${boardUrl}res/1001.htm")
    private val replies = AtomicInteger(0)
    private var shown by mutableStateOf(true)
    private var oldClip: ClipData? = null
    private val clipboard get() = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clipFile get() = File(rule.activity.cacheDir, "compat_post_preview/draft-test.png")

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
        loader = ImageLoader(rule.activity)
        rule.runOnUiThread { oldClip = clipboard.primaryClip }
    }

    @After fun cleanup() {
        rule.runOnUiThread {
            rule.activity.setContent {}
            oldClip?.let { clipboard.setPrimaryClip(it) } ?: clipboard.clearPrimaryClip()
        }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
        clipFile.delete()
    }

    private fun page() = ThreadPage("1001", "下書きの確認", null, null, (1..2).map { index ->
        Post("${1000 + index}", index - 1, "としあき", null, "10/12 12:00",
            messageHtml = "DRAFT-POST-$index", imageUrl = null, thumbnailUrl = null)
    })

    private val pageRepository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = page()
        override suspend fun getThreadByUrl(threadUrl: String) = page()
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page())
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page())
        override suspend fun replyToThread(
            board: String, threadId: String, name: String, email: String, subject: String, comment: String,
            password: String, imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean
        ): String? {
            replies.incrementAndGet()
            return "1003"
        }
    }

    private fun host(preferences: Map<String, String>) {
        // The send confirmation is turned off so the test presses "送信する" once.
        runBlocking { store.savePreferences(preferences + ("compat.control.controlPostConfirm" to "OFF")) }
        val fs = createFileSystem(rule.activity)
        rule.setContent { FutachaTheme(ThemeMode.Light, ThemePalette.Current) {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                ProvideFutachaSharedFeatures(store, null, pageRepository, fs, null, "test") {
                    if (shown) thread() else Box {}
                }
            }
        } }
    }

    @Composable private fun thread() {
        ThreadScreen(board = BoardSummary("draft", "may/b", "test", boardUrl, ""), history = emptyList(),
            threadId = "1001", threadTitle = "下書きの確認", initialReplyCount = 2, repository = pageRepository,
            preferencesState = ScreenPreferencesState("test"), onBack = {})
    }

    private fun waitForThread() {
        rule.waitUntil(15_000) { rule.onAllNodesWithText("DRAFT-POST-1", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openReply() {
        rule.onAllNodesWithContentDescription("返信").onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction() and hasText("コメント")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun commentText(): String = rule.onAllNodes(hasSetTextAction() and hasText("コメント")).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun pasteImage() {
        clipFile.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        clipFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(rule.activity, "${rule.activity.packageName}.fileprovider", clipFile)
        rule.runOnUiThread { clipboard.setPrimaryClip(ClipData.newUri(rule.activity.contentResolver, "draft image", uri)) }
        rule.onAllNodesWithContentDescription("投稿のその他").onFirst().performClick()
        rule.onNodeWithText("画像を貼り付け").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("thread-form-attachment-preview").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun leaveAndReturn() {
        rule.runOnIdle { shown = false }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("DRAFT-POST-1", substring = true).fetchSemanticsNodes().isEmpty() }
    }

    private fun tabExists(): Boolean = runBlocking { store.tabs.first() }.any { it.key == tabKey }

    @Test fun withTheSwitchOnTheTextAndThePictureComeBackAndGoAwayAfterTheReplyIsSent() {
        host(mapOf(FUTACHA_DRAFT_PERSIST_STORAGE_KEY to "ON"))
        waitForThread()
        // The tab the screen keeps for the thread is what the draft belongs to.
        rule.waitUntil(10_000) { tabExists() }
        openReply()
        rule.onNode(hasSetTextAction() and hasText("コメント")).performTextInput("下書きの本文")
        pasteImage()
        leaveAndReturn()
        rule.waitUntil(10_000) {
            runBlocking { store.loadDraft(tabKey) }?.let { it.comment == "下書きの本文" && it.attachmentUri != null } == true
        }
        val locator = runBlocking { store.loadDraft(tabKey) }!!.attachmentUri!!
        val fs = createFileSystem(rule.activity)
        assertTrue(runBlocking { fs.exists(locator) })

        rule.runOnIdle { shown = true }
        waitForThread()
        openReply()
        rule.waitUntil(10_000) { commentText() == "下書きの本文" }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("thread-form-attachment-preview").fetchSemanticsNodes().isNotEmpty() }

        rule.onNode(hasSetTextAction() and hasText("削除キー")).performTextInput("delkey")
        rule.onAllNodesWithContentDescription("送信する").onFirst().performClick()
        rule.waitUntil(15_000) { replies.get() == 1 }
        rule.waitUntil(15_000) { runBlocking { store.loadDraft(tabKey) } == null }
        rule.waitUntil(10_000) { !runBlocking { fs.exists(locator) } }

        // Back on the thread again, the form is empty.
        leaveAndReturn()
        rule.runOnIdle { shown = true }
        waitForThread()
        openReply()
        Thread.sleep(1_000)
        rule.waitForIdle()
        assertEquals("", commentText())
        assertTrue(rule.onAllNodesWithTag("thread-form-attachment-preview").fetchSemanticsNodes().isEmpty())
    }

    @Test fun theSwitchIsOnFutachasControlPageAndOffByDefault() {
        val fs = createFileSystem(rule.activity)
        var features: com.valoser.futacha.shared.ui.board.FutachaSharedFeatures? = null
        rule.setContent { FutachaTheme(ThemeMode.Light, ThemePalette.Current) {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                ProvideFutachaSharedFeatures(store, null, pageRepository, fs, null, "test") {
                    features = LocalFutachaSharedFeatures.current
                    Text("CONTROL-HOST")
                }
            }
        } }
        rule.waitUntil(5_000) { features != null }
        // What ふたちゃ's settings → 操作 → 「コントロール」 does (SharedSettingsLink).
        val openSettings: (String) -> Unit = checkNotNull(features).openSettings
        rule.runOnUiThread { openSettings("control") }
        rule.waitUntil(10_000) { rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("下書きを端末に保存"))
        rule.onNodeWithText("下書きを端末に保存").assertExists()
        assertNull(runBlocking { store.loadPreference(FUTACHA_DRAFT_PERSIST_STORAGE_KEY) })
        rule.onNodeWithText("下書きを端末に保存").performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadPreference(FUTACHA_DRAFT_PERSIST_STORAGE_KEY) } == "ON" }
    }

    @Test fun withTheSwitchOffNothingIsKept() {
        host(emptyMap())
        waitForThread()
        rule.waitUntil(10_000) { tabExists() }
        openReply()
        rule.onNode(hasSetTextAction() and hasText("コメント")).performTextInput("残らない本文")
        // Longer than the debounce of the switch-on case.
        Thread.sleep(2_500)
        leaveAndReturn()
        Thread.sleep(1_500)
        assertNull(runBlocking { store.loadDraft(tabKey) })
        rule.runOnIdle { shown = true }
        waitForThread()
        openReply()
        Thread.sleep(1_000)
        rule.waitForIdle()
        assertFalse(commentText().contains("残らない本文"))
        assertEquals(0, replies.get())
    }
}
