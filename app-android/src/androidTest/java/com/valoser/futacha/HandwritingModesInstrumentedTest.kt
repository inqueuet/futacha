@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Actual posting hosts, an isolated DB and fake send callbacks; no server mutations. */
class HandwritingModesInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val database = "handwriting_modes_test.db"
    private val saved = mutableListOf<ImageData>()

    @Before fun prepare() {
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5_000) {
            rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        rule.waitForIdle()
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun drawAndSave(mode: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-drawing-canvas").performTouchInput {
            repeat(12) { i ->
                val p = Offset(width * (0.2f + (i % 6) * 0.1f), height * (0.3f + (i / 6) * 0.4f))
                down(p)
                if (i % 2 != 0) moveTo(p + Offset(2f, 1f), delayMillis = 8)
                advanceEventTime(8); up(); advanceEventTime(8)
            }
        }
        rule.onNodeWithContentDescription("元に戻す").assertIsEnabled().performClick()
        rule.onNodeWithContentDescription("やり直す").assertIsEnabled().performClick()
        rule.waitForIdle()
        val pixels = rule.onNodeWithTag("compat-drawing-canvas").captureToImage().toPixelMap()
        repeat(12) { i ->
            val x = (pixels.width * (0.2f + (i % 6) * 0.1f)).toInt()
            val y = (pixels.height * (0.3f + (i / 6) * 0.4f)).toInt()
            assertEquals("$mode missing visible stroke $i", 0xFF800000.toInt(), pixels[x, y].toArgb())
        }
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(rule.activity.getExternalFilesDir(null), "handwriting-modes").apply { mkdirs() }
        File(dir, "$mode.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun futachaToolbarReturnsRapidHandwritingAsAnAttachment() {
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            ProvideFutachaSharedFeatures(store, null, FakeBoardRepository(), null, null, "test") {
                FutachaPostToolbar(LocalFutachaSharedFeatures.current!!, "https://may.2chan.net/b/", "本文", {}, "TEST",
                    { saved.add(it) }, {}, {}, {}, {}, {}, {}, true,
                    attachmentPickerPreference = AttachmentPickerPreference.ALWAYS_ASK, preferredFileManagerPackage = null)
            }
        } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("手書き").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("手書き").performClick()
        drawAndSave("futacha")
        rule.runOnIdle { assertTrue(saved.single().isHandwriting) }
    }

    @Test fun toshiakiPostingHostReturnsRapidHandwritingToItsDraft() {
        val url = "https://may.2chan.net/b/"
        runBlocking { store.upsertBoard(CompatBoard(compatBoardKey(url), "検証板", url, url, 0)) }
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            CompatibilityApp(store = store, repository = FakeBoardRepository(), fileSystem = remember { createFileSystem(rule.activity) }, onExitApplication = {})
        } } }
        rule.onNodeWithText(url).performClick()
        rule.onNodeWithContentDescription("スレ立て").performClick()
        rule.onNodeWithContentDescription("手書き").performClick()
        drawAndSave("toshiaki")
        rule.onNodeWithTag("compat-post-attachment-preview").assertExists()
        rule.onNodeWithTag("compat-post-attachment-file-name").assertTextContains("drawing_", substring = true)
    }

    @Test fun futaberPostingToolsReturnRapidHandwriting() {
        rule.setContent { FutaberTheme(FutaberThemeMode.Light) {
            val state = rememberFutaberPostToolsState()
            TextButton(onClick = { state.sheetOpen = true }) { Text("添付を開く") }
            FutaberPostToolsHost(state, FutaberPostTools(), true, "本文", {}, "TEST", {}, { saved.add(it) })
        } }
        rule.onNodeWithText("添付を開く").performClick()
        rule.onNodeWithTag("futaber-action-tool-drawing").performClick()
        drawAndSave("futaber")
        rule.runOnIdle { assertTrue(saved.single().isHandwriting) }
    }

    @Test fun ftutterExtensionIsOffByDefault() {
        ftutterPost(handwriting = false)
        rule.onNodeWithTag("ftutter-post-menu").performClick()
        rule.onNodeWithTag("ftutter-post-handwriting").assertDoesNotExist()
    }

    @Test fun ftutterDrawingCanBeAttachedToAnImgReplyAndSentThroughTheExistingPath() {
        ftutterPost(handwriting = true, img = true)
        rule.onNodeWithTag("ftutter-post-attach").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-post-menu").performClick()
        rule.onNodeWithTag("ftutter-post-handwriting").performClick()
        drawAndSave("ftutter-img")
        rule.onNodeWithText("drawing_", substring = true).assertExists()
        rule.onNodeWithTag("ftutter-post-attach").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-post-send").performClick()
        rule.waitUntil(5_000) { saved.isNotEmpty() }
        rule.runOnIdle { assertTrue(saved.single().isHandwriting) }
    }

    private fun ftutterPost(handwriting: Boolean, img: Boolean = false) {
        val preferences = if (handwriting) mapOf(FtutterExtension.HANDWRITING.key to "true") else emptyMap()
        rule.setContent { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterPostDialog(FtutterPostTarget.Reply("test", "123"),
                if (img) "https://img.2chan.net/b/" else "https://may.2chan.net/b/", "検証板",
                FtutterDraft(comment = "残す本文"), null, "TEST", false,
                BoardPostingCapabilities(1_000_000, setOf("png"), replyAttachmentsAllowed = !img),
                AttachmentPickerPreference.ALWAYS_ASK, null, { "test" }, null, {}, { _, _ -> },
                onSend = { saved.add(checkNotNull(it.image)); Result.success("456") }, onSent = {}, onDismiss = {},
                handwritingEnabled = FtutterExtension.HANDWRITING in parseFtutterExtensions(preferences))
        } }
    }
}
