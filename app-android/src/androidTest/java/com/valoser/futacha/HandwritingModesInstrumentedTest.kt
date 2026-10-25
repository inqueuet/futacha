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
    // L39: the person's copy is held until the test releases it.
    private val copyStarted = java.util.concurrent.CountDownLatch(1)
    private val copyRelease = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val copyFolder by lazy { File(rule.activity.cacheDir, "l39-drawing-copies").apply { deleteRecursively(); mkdirs() } }

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
        // L69: a drawing another test left on the real files would be offered before anything could be drawn.
        clearCompatDrawingRecovery(rule.activity)
    }

    @After fun cleanup() {
        copyRelease.complete(Unit)
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

    /** L65: on としあき(仮)'s drawing screen a swipe from the left edge does not open the drawer, which lost the drawing. */
    @Test fun toshiakiDrawingScreenDoesNotOpenTheDrawerFromTheLeftEdge() {
        val url = "https://may.2chan.net/b/"
        runBlocking { store.upsertBoard(CompatBoard(compatBoardKey(url), "検証板", url, url, 0)) }
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            CompatibilityApp(store = store, repository = FakeBoardRepository(), fileSystem = remember { createFileSystem(rule.activity) }, onExitApplication = {})
        } } }
        rule.onNodeWithText(url).performClick()
        rule.onNodeWithContentDescription("スレ立て").performClick()
        rule.onNodeWithContentDescription("手書き").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        // A stroke first, which leaving the screen would lose.
        rule.onNodeWithTag("compat-drawing-canvas").performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.5f), Offset(width * 0.7f, height * 0.5f), 200)
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("元に戻す").assertIsEnabled()
        // The drawer's content stays composed while it is closed, off the screen to the left.
        fun drawerShown() = rule.onAllNodes(hasText("閲覧中のスレッド") or hasText("履歴") or hasText("巡回結果"))
            .fetchSemanticsNodes().any { it.positionInRoot.x + it.size.width > 1f }
        assertFalse(drawerShown())
        rule.onRoot().performTouchInput {
            swipe(Offset(2f, height * 0.5f), Offset(width * 0.6f, height * 0.5f), 300)
        }
        rule.waitForIdle()
        assertFalse("The drawer stays closed", drawerShown())
        rule.onNodeWithTag("compat-drawing-canvas").assertIsDisplayed()
        rule.onNodeWithContentDescription("元に戻す").assertIsEnabled()
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

    /** L22: a failed handwriting copy is explained inside the post dialog and stays there (not a 4-second background notice). */
    @Test fun ftutterHandwritingCopyFailureStaysInsideThePostDialog() {
        ftutterPost(handwriting = true, onHandwritingSaved = { "手書き画像のコピーを保存できませんでした（添付はそのままです）" })
        rule.onNodeWithTag("ftutter-post-menu").performClick()
        rule.onNodeWithTag("ftutter-post-handwriting").performClick()
        drawAndSave("ftutter-copy-failure")
        rule.onNodeWithTag("ftutter-post-error").performScrollTo().assertTextContains("手書き画像のコピーを保存できませんでした", substring = true)
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithTag("ftutter-post-error").assertExists()
        rule.onNodeWithText("drawing_", substring = true).assertExists()
        rule.onNodeWithTag("ftutter-post-detach").performScrollTo().performClick()
        rule.onAllNodesWithTag("ftutter-post-error").assertCountEquals(0)
    }

    /** L70: ヘルプ on the drawing screen; true when it showed the 手書き section alone, false for the whole reference help. */
    private fun drawingHelpIsTheHandwritingSection(): Boolean {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNode(hasText("ヘルプ") and hasAnyAncestor(isPopup())).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-drawing-help").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("help-report-bug").fetchSemanticsNodes().isNotEmpty()
        }
        val section = rule.onAllNodesWithTag("compat-drawing-help").fetchSemanticsNodes().isNotEmpty()
        if (section) {
            rule.onNodeWithText("ヘルプ（手書き）").assertIsDisplayed()
            rule.onAllNodesWithTag("help-report-bug").assertCountEquals(0)
        }
        // Back to the drawing (the system back: the post screen behind may have its own "戻る").
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isNotEmpty() }
        return section
    }

    @Test fun futachaDrawingHelpIsTheHandwritingSection() {
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            ProvideFutachaSharedFeatures(store, null, FakeBoardRepository(), null, null, "test") {
                FutachaPostToolbar(LocalFutachaSharedFeatures.current!!, "https://may.2chan.net/b/", "本文", {}, "TEST",
                    { saved.add(it) }, {}, {}, {}, {}, {}, {}, true,
                    attachmentPickerPreference = AttachmentPickerPreference.ALWAYS_ASK, preferredFileManagerPackage = null)
            }
        } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("手書き").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("手書き").performClick()
        assertTrue(drawingHelpIsTheHandwritingSection())
    }

    @Test fun toshiakiDrawingHelpIsStillTheWholeReferenceHelp() {
        val url = "https://may.2chan.net/b/"
        runBlocking { store.upsertBoard(CompatBoard(compatBoardKey(url), "検証板", url, url, 0)) }
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            CompatibilityApp(store = store, repository = FakeBoardRepository(), fileSystem = remember { createFileSystem(rule.activity) }, onExitApplication = {})
        } } }
        rule.onNodeWithText(url).performClick()
        rule.onNodeWithContentDescription("スレ立て").performClick()
        rule.onNodeWithContentDescription("手書き").performClick()
        assertFalse(drawingHelpIsTheHandwritingSection())
    }

    @Test fun futaberDrawingHelpIsTheHandwritingSection() {
        rule.setContent { FutaberTheme(FutaberThemeMode.Light) {
            val state = rememberFutaberPostToolsState()
            TextButton(onClick = { state.sheetOpen = true }) { Text("添付を開く") }
            FutaberPostToolsHost(state, FutaberPostTools(), true, "本文", {}, "TEST", {}, { saved.add(it) })
        } }
        rule.onNodeWithText("添付を開く").performClick()
        rule.onNodeWithTag("futaber-action-tool-drawing").performClick()
        assertTrue(drawingHelpIsTheHandwritingSection())
    }

    @Test fun ftutterDrawingHelpIsTheHandwritingSection() {
        ftutterPost(handwriting = true)
        rule.onNodeWithTag("ftutter-post-menu").performClick()
        rule.onNodeWithTag("ftutter-post-handwriting").performClick()
        assertTrue(drawingHelpIsTheHandwritingSection())
    }

    /** A file system whose writes to a chosen folder (the person's copy) wait for [copyRelease]. */
    private fun heldCopies(): com.valoser.futacha.shared.util.FileSystem {
        val real = createFileSystem(rule.activity)
        return object : com.valoser.futacha.shared.util.FileSystem by real {
            override suspend fun writeBytes(
                base: com.valoser.futacha.shared.model.SaveLocation, relativePath: String, bytes: ByteArray
            ): Result<Unit> {
                copyStarted.countDown()
                copyRelease.await()
                return real.writeBytes(base, relativePath, bytes)
            }
        }
    }

    private fun setDrawingCopyFolder() = runBlocking {
        store.savePreference(compatPreferenceStorageKey("storage", "dummyDrawingDir"), copyFolder.absolutePath)
    }

    /** L39: the drawing is attached and the drawing screen closes while the person's copy is still being written. */
    private fun assertTheCopyIsWrittenAfterwards() {
        assertTrue("The copy was started", copyStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))
        rule.onAllNodesWithTag("compat-drawing-canvas").assertCountEquals(0)
        copyRelease.complete(Unit)
        rule.waitUntil(5_000) { copyFolder.listFiles().orEmpty().isNotEmpty() }
    }

    @Test fun futachaAttachesHandwritingWithoutWaitingForTheCopy() {
        setDrawingCopyFolder()
        val fileSystem = heldCopies()
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            ProvideFutachaSharedFeatures(store, null, FakeBoardRepository(), fileSystem, null, "test") {
                FutachaPostToolbar(LocalFutachaSharedFeatures.current!!, "https://may.2chan.net/b/", "本文", {}, "TEST",
                    { saved.add(it) }, {}, {}, {}, {}, {}, {}, true,
                    attachmentPickerPreference = AttachmentPickerPreference.ALWAYS_ASK, preferredFileManagerPackage = null)
            }
        } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("手書き").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("手書き").performClick()
        drawAndSave("futacha-copy-held")
        rule.runOnIdle { assertTrue(saved.single().isHandwriting) }
        assertTheCopyIsWrittenAfterwards()
    }

    @Test fun toshiakiAttachesHandwritingWithoutWaitingForTheCopy() {
        val url = "https://may.2chan.net/b/"
        runBlocking { store.upsertBoard(CompatBoard(compatBoardKey(url), "検証板", url, url, 0)) }
        setDrawingCopyFolder()
        val fileSystem = heldCopies()
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            CompatibilityApp(store = store, repository = FakeBoardRepository(), fileSystem = fileSystem, onExitApplication = {})
        } } }
        rule.onNodeWithText(url).performClick()
        rule.onNodeWithContentDescription("スレ立て").performClick()
        rule.onNodeWithContentDescription("手書き").performClick()
        drawAndSave("toshiaki-copy-held")
        rule.onNodeWithTag("compat-post-attachment-preview").assertExists()
        assertTheCopyIsWrittenAfterwards()
    }

    @Test fun futaberAttachesHandwritingWithoutWaitingForTheCopy() {
        val fileSystem = heldCopies()
        rule.setContent { FutaberTheme(FutaberThemeMode.Light) {
            val state = rememberFutaberPostToolsState()
            TextButton(onClick = { state.sheetOpen = true }) { Text("添付を開く") }
            FutaberPostToolsHost(state, FutaberPostTools(fileSystem = fileSystem,
                drawingCopyLocation = com.valoser.futacha.shared.model.SaveLocation.Path(copyFolder.absolutePath)),
                true, "本文", {}, "TEST", {}, { saved.add(it) })
        } }
        rule.onNodeWithText("添付を開く").performClick()
        rule.onNodeWithTag("futaber-action-tool-drawing").performClick()
        drawAndSave("futaber-copy-held")
        rule.runOnIdle { assertTrue(saved.single().isHandwriting) }
        assertTheCopyIsWrittenAfterwards()
    }

    @Test fun ftutterAttachesHandwritingWithoutWaitingForTheCopy() {
        ftutterPost(handwriting = true, onHandwritingSaved = {
            copyStarted.countDown()
            copyRelease.await()
            null
        })
        rule.onNodeWithTag("ftutter-post-menu").performClick()
        rule.onNodeWithTag("ftutter-post-handwriting").performClick()
        drawAndSave("ftutter-copy-held")
        rule.onNodeWithText("drawing_", substring = true).assertExists()
        assertTrue("The copy was started", copyStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))
        copyRelease.complete(Unit)
    }

    private fun ftutterPost(handwriting: Boolean, img: Boolean = false,
        onHandwritingSaved: suspend (com.valoser.futacha.shared.util.ImageData) -> String? = { null }) {
        val preferences = if (handwriting) mapOf(FtutterExtension.HANDWRITING.key to "true") else emptyMap()
        rule.setContent { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterPostDialog(FtutterPostTarget.Reply("test", "123"),
                if (img) "https://img.2chan.net/b/" else "https://may.2chan.net/b/", "検証板",
                FtutterDraft(comment = "残す本文"), null, "TEST", false,
                BoardPostingCapabilities(1_000_000, setOf("png"), replyAttachmentsAllowed = !img),
                AttachmentPickerPreference.ALWAYS_ASK, null, { "test" }, null, {}, { _, _ -> },
                onSend = { saved.add(checkNotNull(it.image)); Result.success("456") }, onSent = {}, onDismiss = {},
                handwritingEnabled = FtutterExtension.HANDWRITING in parseFtutterExtensions(preferences),
                onHandwritingSaved = onHandwritingSaved)
        } }
    }
}
