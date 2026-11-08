package com.valoser.futacha

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたばー風モードの機能そろえ B2（手書き・書き込みツール）と B3（下書きの添付）。
 * チュートリアル板の同梱データ（模擬リポジトリ）だけを使い、実サーバーへは何も送らない（書き込み・あぷ小・回線情報は押さない）。
 * 「返信に添付できない板」は、模擬リポジトリで読む板（アドレスに example.com を含む）を、img板と同じ添付の制限で登録して作る。
 */
private const val B23_TUTORIAL_URL = "https://www.example.com/t/futaba.php"
/** Read through the bundled mock repository (the address names example.com); its posting limits are img's (no files in replies). */
private const val B23_IMG_LIKE_URL = "https://img.2chan.net/b/futaba.php?mock=example.com"
private const val B23_IMG_LIKE_ID = "zz-b23img"
private const val B23_IMG_LIKE_NAME = "返信添付不可の模擬板"

class FutaberParityB23InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    private val keys = listOf(
        "compat.futaber.ext.postTools",
        "compat.futaber.ext.draftAttachment",
        "compat.futaber.drafts",
        "compat.futaber.lastBoardId",
        "compat.futaber.manageCategory",
        "compat.futaber.tabs",
        "compat.futaber.seen"
    )

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        // L69: a drawing another test left on the real files would be offered before anything could be drawn.
        clearCompatDrawingRecovery(app)
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(keys.associateWith { null } + FUTABER_EXTENSIONS_OFF)
            app.appStateStore.updateBoards { boards ->
                val withTutorial = if (boards.none { it.url == B23_TUTORIAL_URL }) {
                    listOf(BoardSummary("t", "チュートリアル＠ふたちゃ", "チュートリアル", B23_TUTORIAL_URL, "チュートリアル")) + boards
                } else boards
                withTutorial.filterNot { it.id == B23_IMG_LIKE_ID }
            }
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            app.appStateStore.updateBoards { boards -> boards.filterNot { it.id == B23_IMG_LIKE_ID } }
            app.compatibilityStore.savePreferences(keys.associateWith { null })
        }
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

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun preference(key: String): String? = runBlocking { app.compatibilityStore.loadPreference(key) }

    private fun turnOn(vararg extensionKeys: String) = runBlocking {
        app.compatibilityStore.savePreferences(extensionKeys.associate { it to "ON" })
    }

    /** Board list → the board named [boardName] → its first catalog thread → the write screen of a reply. */
    private fun openReplyOn(boardName: String) {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.waitUntil(20_000) {
            rule.onAllNodes(hasTestTag("futaber-drawer-board") and hasText(boardName, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasTestTag("futaber-drawer-board") and hasText(boardName, substring = true)).performScrollTo().performClick()
        waitForTag("futaber-catalog-item")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-write").performScrollTo().performClick()
        waitForTag("futaber-post-comment")
    }

    /**
     * L-B2 (V05): the thread has no "←" any more. Its action is the title row's accessibility action
     * 「カタログへ戻る」 (the same callback the button had); the right swipe and the system's back do the same.
     */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    private fun backToCatalog() {
        rule.onNodeWithTag("futaber-thread-title").performCustomAccessibilityActionWithLabel("カタログへ戻る")
    }

    @Test fun aReplyOnABoardWithoutReplyFilesGetsTheDrawingFromATapWithTheExtensionOff() {
        runBlocking {
            app.appStateStore.updateBoards { boards ->
                listOf(BoardSummary(B23_IMG_LIKE_ID, B23_IMG_LIKE_NAME, "", B23_IMG_LIKE_URL, "")) + boards
            }
        }
        openReplyOn(B23_IMG_LIKE_NAME)
        // The extension is off: the button has no long press of its own.
        rule.onNodeWithTag("futaber-post-attach").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        // L-B8 (M37): the tap opens the camera's black menu: the original's three, then 手書き (needed on this board). The
        // three say why when pressed (the picker would only be refused on this board) and pick nothing.
        rule.onNodeWithTag("futaber-post-attach").performClick()
        waitForTag("futaber-callout-recent")
        rule.onNodeWithTag("futaber-callout-file").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("この板の返信には画像・動画を添付できません", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("閉じる").performClick()
        rule.waitUntil(10_000) { absent("futaber-post-tool-message") }
        rule.onNodeWithTag("futaber-post-attach").performClick()
        waitForTag("futaber-callout-recent")
        assertTrue(absent("futaber-callout-tool-ups"))
        rule.onNodeWithTag("futaber-callout-tool-drawing").performScrollTo().performClick()
        waitForTag("compat-drawing-canvas")
        rule.onNodeWithTag("compat-drawing-canvas").performTouchInput {
            down(center); moveBy(androidx.compose.ui.geometry.Offset(40f, 10f)); up()
        }
        rule.onNode(hasContentDescription("その他") and hasAnyAncestor(hasTestTag("futaber-post-drawing"))).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("保存する").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("保存する").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("添付画像として保存します", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("保存する").performClick()
        // The drawing is attached (a board that refuses ordinary files takes it), and the screen is back.
        waitForTag("futaber-post-attachment")
        assertTrue(absent("futaber-post-drawing"))
        assertTrue(absent("futaber-post-error"))
        rule.onNodeWithTag("futaber-post-close").performClick()
        rule.waitUntil(10_000) { absent("futaber-post-screen") }
    }

    /** L-B8 (M37): the tools that the long press opened before are at the end of the camera's black menu. */
    @Test fun theToolsOpenFromALongPressOnlyWithTheExtensionAndInsertTheDeviceLine() {
        openReplyOn("チュートリアル")
        // Off: no long press, and the camera's menu has only the original's three (no tools; its 「カメラ」 is taken out).
        rule.onNodeWithTag("futaber-post-attach").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        rule.onNodeWithTag("futaber-post-attach").performClick()
        waitForTag("futaber-callout-recent")
        listOf("recent", "file", "library").forEach { rule.onNodeWithTag("futaber-callout-$it").assertExists() }
        assertTrue(absent("futaber-callout-camera"))
        assertTrue(absent("futaber-callout-tool-device-info"))
        rule.onNodeWithTag("futaber-callout-outside").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 40f)) }
        rule.waitUntil(10_000) { absent("futaber-callout") }
        rule.onNodeWithTag("futaber-post-close").performClick()
        rule.waitUntil(10_000) { absent("futaber-post-screen") }

        turnOn("compat.futaber.ext.postTools")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-write").performScrollTo().performClick()
        waitForTag("futaber-post-comment")
        rule.onNodeWithTag("futaber-post-comment").performTextInput("本文")
        rule.onNodeWithTag("futaber-post-attach").performClick()
        waitForTag("futaber-callout-recent")
        listOf("recent", "file", "library", "tool-drawing", "tool-ups", "tool-voice", "tool-network-info", "tool-device-info").forEach {
            rule.onNodeWithTag("futaber-callout-$it").assertExists()
        }
        // The device line goes under what was written, naming this app (nothing is sent).
        rule.onNodeWithTag("futaber-callout-tool-device-info").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("ふたちゃ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("本文", substring = true).assertExists()
        assertTrue(absent("futaber-callout"))
        rule.onNodeWithTag("futaber-post-close").performClick()
        rule.waitUntil(10_000) { absent("futaber-post-screen") }
    }

    @Test fun withTheExtensionTheDraftKeepsItsAttachmentAndClearingDeletesTheFile() {
        turnOn("compat.futaber.ext.draftAttachment")
        openReplyOn("チュートリアル")
        rule.onNodeWithTag("futaber-post-comment").performTextInput("添付つき下書き")
        val clipboard = app.getSystemService(android.content.ClipboardManager::class.java)
        val oldClip = run {
            var clip: android.content.ClipData? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { clip = clipboard.primaryClip }
            clip
        }
        val file = java.io.File(app.cacheDir, "compat_post_preview/futaber-b23.png")
        try {
            file.parentFile!!.mkdirs()
            val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.GREEN)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                clipboard.setPrimaryClip(android.content.ClipData.newUri(app.contentResolver, "test image", uri))
            }
            rule.waitForIdle(); Thread.sleep(500)
            rule.onNodeWithTag("futaber-post-paste").performScrollTo().performClick()
            waitForTag("futaber-post-attachment")
            // The draft notes the file; the file is in the private storage.
            rule.waitUntil(15_000) { preference("compat.futaber.drafts")?.contains("attachmentLocator") == true }
            val locator = Regex("\"attachmentLocator\":\"([^\"]+)\"").find(preference("compat.futaber.drafts")!!)!!.groupValues[1]
            val fs = createFileSystem(app)
            assertTrue(runBlocking { fs.exists(locator) })

            // Closed; writing another post (a new thread) lets go of the file in memory, and its draft keeps no file.
            rule.onNodeWithTag("futaber-post-close").performClick()
            rule.waitUntil(10_000) { absent("futaber-post-screen") }
            backToCatalog()
            waitForTag("futaber-create-thread")
            rule.onNodeWithTag("futaber-create-thread").performClick()
            waitForTag("futaber-post-comment")
            assertTrue(absent("futaber-post-attachment"))
            rule.onNodeWithTag("futaber-post-close").performClick()
            rule.waitUntil(10_000) { absent("futaber-post-screen") }
            // Back to the reply: the attachment comes back from the draft with the text.
            rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
            waitForTag("futaber-post")
            rule.onNodeWithTag("futaber-thread-menu").performClick()
            rule.onNodeWithTag("futaber-menu-write").performScrollTo().performClick()
            waitForTag("futaber-post-attachment")
            rule.onNodeWithText("添付つき下書き").assertExists()
            assertTrue(runBlocking { fs.exists(locator) })

            // Clearing removes the text, the attachment, the draft and the file.
            rule.onNodeWithTag("futaber-post-clear").performClick()
            rule.waitUntil(15_000) { preference("compat.futaber.drafts")?.contains("attachmentLocator") != true }
            rule.waitUntil(10_000) { !runBlocking { fs.exists(locator) } }
            // The text goes with the next save of the draft (a moment after typing stops).
            rule.waitUntil(10_000) { !preference("compat.futaber.drafts").orEmpty().contains("添付つき下書き") }
            rule.onNodeWithTag("futaber-post-close").performClick()
        } finally {
            file.delete()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                oldClip?.let { clipboard.setPrimaryClip(it) } ?: clipboard.clearPrimaryClip()
            }
        }
    }

    @Test fun turningTheExtensionOffDeletesTheKeptFiles() {
        turnOn("compat.futaber.ext.draftAttachment")
        openReplyOn("チュートリアル")
        rule.onNodeWithTag("futaber-post-comment").performTextInput("残す本文")
        val clipboard = app.getSystemService(android.content.ClipboardManager::class.java)
        val file = java.io.File(app.cacheDir, "compat_post_preview/futaber-b23-off.png")
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
            rule.waitUntil(15_000) { preference("compat.futaber.drafts")?.contains("attachmentLocator") == true }
            val locator = Regex("\"attachmentLocator\":\"([^\"]+)\"").find(preference("compat.futaber.drafts")!!)!!.groupValues[1]
            val fs = createFileSystem(app)
            rule.onNodeWithTag("futaber-post-close").performClick()
            rule.waitUntil(10_000) { absent("futaber-post-screen") }
            runBlocking { app.compatibilityStore.savePreference("compat.futaber.ext.draftAttachment", "OFF") }
            rule.waitUntil(15_000) { preference("compat.futaber.drafts")?.contains("attachmentLocator") != true }
            rule.waitUntil(10_000) { !runBlocking { fs.exists(locator) } }
            // The text of the draft stays.
            assertTrue(preference("compat.futaber.drafts").orEmpty().contains("残す本文"))
        } finally {
            file.delete()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { clipboard.clearPrimaryClip() }
        }
    }
}
