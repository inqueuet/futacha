@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ClipboardUnreadInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val boardUrl = "https://may.2chan.net/b/"
    private val url = "${boardUrl}res/123.htm"
    private val board = CompatBoard("clipboard-board", "may", boardUrl, boardUrl, 0)
    private val tab = CompatTab("clipboard-tab", url, url, board.key, "may", "123", "CLIPBOARD", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private var chosen by mutableStateOf<ImageData?>(null)
    private var oldClip: ClipData? = null
    private val clipboard get() = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    @Before fun setup() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = "clipboard_ui_test.db")
        runBlocking { store.initialize(); store.upsertBoard(board); store.openTab(tab) }
        loader = ImageLoader.Builder(rule.activity).build()
        rule.runOnUiThread { oldClip = clipboard.primaryClip }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {}; oldClip?.let { clipboard.setPrimaryClip(it) } ?: clipboard.clearPrimaryClip() }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase("clipboard_ui_test.db")
        File(rule.activity.cacheDir, "compat_post_preview/clipboard-test.png").delete()
    }
    private fun copyImage() {
        val file = File(rule.activity.cacheDir, "compat_post_preview/clipboard-test.png")
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(rule.activity, "${rule.activity.packageName}.fileprovider", file)
        rule.runOnUiThread { clipboard.setPrimaryClip(ClipData.newUri(rule.activity.contentResolver, "test image", uri)) }
    }
    private fun paste() {
        rule.onAllNodesWithContentDescription("その他")[0].performClick()
        rule.onNodeWithText("画像を貼り付け").performClick()
    }
    private fun modern(replyBoard: String = boardUrl) {
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            ThreadFormDialog(title = "返信", subtitle = null, attachmentPickerPreference = AttachmentPickerPreference.MEDIA,
                preferredFileManagerPackage = null, emailPresets = emptyList(), comment = "keep body", onCommentChange = {},
                name = "", onNameChange = {}, email = "", onEmailChange = {}, subject = "", onSubjectChange = {},
                password = "key", onPasswordChange = {}, selectedImage = chosen, onImageSelected = { chosen = it },
                onDismiss = {}, onSubmit = { error("Must not post") }, onClear = {}, isSubmitEnabled = true,
                sendDescription = "送信", boardUrl = replyBoard, isReply = true)
        } } }
    }
    @Test fun modernPasteUsesImageBytesAndTextFailureKeepsExistingAttachmentAndBody() {
        modern(); copyImage(); paste()
        rule.waitUntil(10_000) { chosen != null }
        assertEquals("clipboard.png", chosen!!.fileName)
        assertFalse(chosen!!.isHandwriting)
        val accepted = chosen
        rule.runOnUiThread { clipboard.setPrimaryClip(ClipData.newPlainText("url", "https://example.com/a.png")) }
        paste()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("コピーされた画像がありません。画像そのものをコピーしてから貼り付けてください").fetchSemanticsNodes().isNotEmpty() }
        assertSame(accepted, chosen)
        rule.onNodeWithText("keep body").assertExists()
    }
    @Test fun clipboardCannotBypassImgReplyAttachmentRestriction() {
        modern("https://img.2chan.net/b/"); copyImage(); paste()
        rule.onNodeWithText("この板の返信は通常の添付に対応していません。お手書きはお絵描きから選択できます").assertExists()
        assertNull(chosen)
    }
    @Test fun compatibilityPastePersistsDraftAndPreservesComment() {
        runBlocking { store.saveDraft(CompatReplyDraft(tab.key, comment = "keep body", deleteKey = "key", updatedAtEpochMillis = 1)) }
        val fs = createFileSystem(rule.activity)
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            CompatPostScreen(tab, board, FakeBoardRepository(), store = store, preferences = emptyMap(), appVersion = "12.3",
                fileSystem = fs, onToolbarEdit = {}, onBack = {})
        } } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("keep body").fetchSemanticsNodes().isNotEmpty() }
        copyImage(); paste()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("添付削除").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { runBlocking { store.loadDraft(tab.key)?.attachmentUri != null } }
        val draft = runBlocking { store.loadDraft(tab.key)!! }
        assertEquals("keep body", draft.comment)
        assertFalse(draft.attachmentIsHandwriting)
        assertTrue(runBlocking { fs.exists(draft.attachmentUri!!) })
        runBlocking { fs.deleteRecursively(draft.attachmentUri!!) }
    }
    @Test fun bothModesExposeReadableNewReplyBadges() {
        rule.setContent { MaterialTheme { Column {
            CatalogNewRepliesBadge(5)
            CompatThreadMetadataRow(tab.copy(replyCount = 15, checkedReplyCount = 10), {}, {})
        } } }
        rule.onAllNodesWithText("新着 5").assertCountEquals(2)
        rule.onAllNodesWithContentDescription("新着レス5件", useUnmergedTree = true).assertCountEquals(2)
    }
}
