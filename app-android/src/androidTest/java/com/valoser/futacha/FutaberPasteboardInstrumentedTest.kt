package com.valoser.futacha

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたばー風の書き込み画面の「カナ」と「ペーストボード」（futaberにある機能、docs/thread-feedback-1444136530-2026-10-27.md）。
 * チュートリアル板の書き込み画面だけを使い、送信はしない。
 */
class FutaberPasteboardInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    private val futaberKeys = listOf(
        "compat.futaber.lastBoardId",
        "compat.futaber.manageCategory",
        "compat.futaber.drafts",
        "compat.futaber.seen",
        "compat.futaber.pasteboard"
    )

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(futaberKeys.associateWith { null })
            // 「書き込む」 is one of this app's extras: keep it in the menu instead of 「その他の機能」 (F-B2).
            app.compatibilityStore.savePreferences(mapOf("compat.futaber.foldExtras" to "OFF"))
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking { app.compatibilityStore.savePreferences((futaberKeys + "compat.futaber.foldExtras").associateWith { null }) }
        switchProfile(originalProfile)
    }

    private fun switchProfile(target: ExperienceProfile) {
        val store = app.experienceProfileStore
        val current = store.readActiveProfile()
        if (current == target) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(current, target)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitGone(tag: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openWriting() {
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-catalog-item")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-write").performScrollTo().performClick()
        waitForTag("futaber-post-comment")
    }

    /** The keyboard slides in after typing and moves the writing's buttons: taps wait until it has. */
    private fun settle() {
        rule.waitForIdle()
        Thread.sleep(800)
        rule.waitForIdle()
    }

    private fun items(): Int = rule.onAllNodesWithTag("futaber-pasteboard-item").fetchSemanticsNodes().size

    /** 編集 → 追加 → [action] (本文を保存／添付画像を保存), then out of the edit mode. */
    private fun keep(action: String) {
        rule.onNodeWithTag("futaber-pasteboard-edit").performClick()
        rule.onNodeWithTag("futaber-pasteboard-add").performClick()
        waitForTag(action)
        rule.onNodeWithTag(action).performClick()
        waitGone(action)
        rule.onNodeWithTag("futaber-pasteboard-edit").performClick()
    }

    @Test fun kanaTurnsTheBodyIntoHalfWidthKatakanaAsTheOriginalDoes() {
        openWriting()
        rule.onNodeWithTag("futaber-post-comment").performTextInput("すけべしまる")
        settle()
        rule.onNodeWithTag("futaber-post-kana").performClick()
        waitForText("ｽｹﾍﾞｼﾏﾙ")
        rule.onNodeWithTag("futaber-post-clear").performClick()
        rule.onNodeWithTag("futaber-post-close").performClick()
        waitGone("futaber-post-screen")
    }

    @Test fun thePasteboardKeepsATextAndAPictureAndPutsThemBackIntoTheWriting() {
        openWriting()
        // Empty at first, with the original's explanation.
        rule.onNodeWithTag("futaber-post-pasteboard").performClick()
        settle()
        waitForTag("futaber-pasteboard")
        waitForTag("futaber-pasteboard-empty")
        rule.onNodeWithTag("futaber-pasteboard-close").performClick()
        settle()
        waitGone("futaber-pasteboard")

        // Keep the body, clear it, and put it back with one tap.
        rule.onNodeWithTag("futaber-post-comment").performTextInput("定型のテスト")
        settle()
        rule.onNodeWithTag("futaber-post-pasteboard").performClick()
        settle()
        waitForTag("futaber-pasteboard")
        // L-B8 (M39): keeping is the edit mode's 「追加」, as in the original's table.
        keep("futaber-action-pasteboard-keep-text")
        rule.waitUntil(10_000) { items() == 1 }
        rule.onNodeWithTag("futaber-pasteboard-close").performClick()
        settle()
        rule.onNodeWithTag("futaber-post-clear").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("定型のテスト").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-post-pasteboard").performClick()
        settle()
        waitForTag("futaber-pasteboard-item")
        rule.onAllNodesWithTag("futaber-pasteboard-item")[0].performClick()
        settle()
        waitGone("futaber-pasteboard")
        waitForText("定型のテスト")

        // Keep an attached picture, take the attachment off, and attach it again from the board.
        val clipboard = app.getSystemService(android.content.ClipboardManager::class.java)
        var oldClip: android.content.ClipData? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { oldClip = clipboard.primaryClip }
        val file = java.io.File(app.cacheDir, "compat_post_preview/futaber-pasteboard-test.png")
        try {
            file.parentFile!!.mkdirs()
            val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                clipboard.setPrimaryClip(android.content.ClipData.newUri(app.contentResolver, "test image", uri))
            }
            rule.waitForIdle(); Thread.sleep(500)
            rule.onNodeWithTag("futaber-post-paste").performScrollTo().performClick()
            waitForTag("futaber-post-attachment")
            rule.onNodeWithTag("futaber-post-pasteboard").performClick()
        settle()
            waitForTag("futaber-pasteboard")
            keep("futaber-action-pasteboard-keep-image")
            rule.waitUntil(10_000) { items() == 2 }
            rule.onNodeWithTag("futaber-pasteboard-close").performClick()
        settle()
            rule.onNodeWithTag("futaber-post-attachment-remove").performClick()
            waitGone("futaber-post-attachment")
            rule.onNodeWithTag("futaber-post-pasteboard").performClick()
        settle()
            rule.waitUntil(10_000) { items() == 2 }
            // The newest (the picture) is at the top.
            rule.onAllNodesWithTag("futaber-pasteboard-item")[0].performClick()
        settle()
            waitGone("futaber-pasteboard")
            waitForTag("futaber-post-attachment")
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                oldClip?.let { clipboard.setPrimaryClip(it) } ?: clipboard.clearPrimaryClip()
            }
            file.delete()
        }

        // 編集 removes rows; the list (and its picture) goes.
        rule.onNodeWithTag("futaber-post-pasteboard").performClick()
        settle()
        waitForTag("futaber-pasteboard")
        // L-B8 (M39): the rows are chosen with their circles, then 削除 at the bottom.
        rule.onNodeWithTag("futaber-pasteboard-edit").performClick()
        rule.onAllNodesWithTag("futaber-pasteboard-item")[0].performClick()
        rule.onNodeWithTag("futaber-pasteboard-delete").performClick()
        rule.waitUntil(10_000) { items() == 1 }
        rule.onNodeWithTag("futaber-pasteboard-select-all").performClick()
        rule.onNodeWithTag("futaber-pasteboard-delete").performClick()
        waitForTag("futaber-pasteboard-empty")
        val stored = runBlocking { app.compatibilityStore.loadPreference("compat.futaber.pasteboard") }
        assertTrue("The pasteboard still lists rows: $stored", stored == null || stored == "[]")
        rule.onNodeWithTag("futaber-pasteboard-close").performClick()
        settle()
        // Nothing was sent: the writing closes with its draft.
        rule.onNodeWithTag("futaber-post-clear").performClick()
        rule.onNodeWithTag("futaber-post-close").performClick()
        waitGone("futaber-post-screen")
    }

    /**
     * L-B8 (M39): the original's table. 編集 shows the selection circles, the grips and the bar 削除／移動／追加／すべて選択／
     * 新規フォルダ; a folder keeps rows and opens with a tap, ‹ goes back; the stored list names the folder (an optional field).
     */
    @Test fun foldersAndTheEditModeAreTheOriginalsTable() {
        openWriting()
        rule.onNodeWithTag("futaber-post-comment").performTextInput("フォルダの定型")
        settle()
        rule.onNodeWithTag("futaber-post-pasteboard").performClick()
        settle()
        waitForTag("futaber-pasteboard")
        // Not editing: no circles, no bar.
        assertTrue(rule.onAllNodesWithTag("futaber-pasteboard-select", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("futaber-pasteboard-delete").fetchSemanticsNodes().isEmpty())
        keep("futaber-action-pasteboard-keep-text")
        rule.waitUntil(10_000) { items() == 1 }
        rule.onNodeWithTag("futaber-pasteboard-edit").performClick()
        listOf("delete", "move", "add", "select-all", "new-folder").forEach { waitForTag("futaber-pasteboard-$it") }
        // The circle and the grip are drawn inside the row (its one node for a screen reader says chosen or not).
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-pasteboard-select", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-pasteboard-grip", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // 新規フォルダ: the name in the alert.
        rule.onNodeWithTag("futaber-pasteboard-new-folder").performClick()
        waitForTag("futaber-alert-field")
        rule.onNodeWithTag("futaber-alert-field").performTextInput("定型文")
        rule.onNodeWithTag("futaber-alert-folder-create").performClick()
        waitForTag("futaber-pasteboard-folder")
        // 移動: the row into the folder.
        rule.onAllNodesWithTag("futaber-pasteboard-item")[0].performClick()
        rule.onNodeWithTag("futaber-pasteboard-move").performClick()
        waitForTag("futaber-action-pasteboard-move-top")
        val intoFolder = androidx.compose.ui.test.SemanticsMatcher("a folder of the move sheet") { node ->
            val tag = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag).orEmpty()
            tag.startsWith("futaber-action-pasteboard-move-") && tag != "futaber-action-pasteboard-move-top"
        }
        rule.onAllNodes(intoFolder).onFirst().performClick()
        rule.waitUntil(10_000) { items() == 0 }
        rule.waitUntil(10_000) {
            runBlocking { app.compatibilityStore.loadPreference("compat.futaber.pasteboard") }?.contains("\"folder\"") == true
        }
        rule.onNodeWithTag("futaber-pasteboard-edit").performClick()
        // The folder opens; its row puts the text into the writing.
        rule.onNodeWithTag("futaber-pasteboard-folder").performClick()
        rule.waitUntil(10_000) { items() == 1 }
        rule.onNodeWithTag("futaber-pasteboard-title").assertTextContains("定型文")
        rule.onNodeWithTag("futaber-pasteboard-back").performClick()
        waitForTag("futaber-pasteboard-folder")
        // Removing the folder removes what is in it.
        rule.onNodeWithTag("futaber-pasteboard-edit").performClick()
        rule.onNodeWithTag("futaber-pasteboard-folder").performClick()
        rule.onNodeWithTag("futaber-pasteboard-delete").performClick()
        waitForTag("futaber-pasteboard-empty")
        rule.waitUntil(10_000) {
            runBlocking { app.compatibilityStore.loadPreference("compat.futaber.pasteboard") }.let { it == null || it == "[]" }
        }
        rule.onNodeWithTag("futaber-pasteboard-close").performClick()
        settle()
        rule.onNodeWithTag("futaber-post-clear").performClick()
        rule.onNodeWithTag("futaber-post-close").performClick()
        waitGone("futaber-post-screen")
    }
}
