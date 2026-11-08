@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import coil3.ImageLoader
import com.valoser.futacha.shared.network.defaultBoardPostingCapabilities
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.*
import org.junit.Assert.*

/**
 * L-B8: ふたばー風の書き込み画面を futaber に合わせた所（道具の並び・送信の枠・カメラの黒いメニューと各選択画面・本文を下へ引いて
 * 引用モード・右スワイプで閉じる・空の「0行 / 0B」・×の長押しで戻す・全画面の書き込み設定・JPG圧縮・端末情報出力・題名の欄の
 * スイッチ）。選択画面は ActivityResult の登録を差し替えて結果を返す（本物の写真は使わない。2026-11-04にメニューの「カメラ」は外した）。何も送らない。
 */
class FutaberPostScreenInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var loader: ImageLoader
    private val launched = mutableListOf<Pair<String, Any?>>()
    /** What the stubbed picker answers: by the contract's class name and its input. */
    private var answer: (String, Any?) -> Any? = { _, _ -> null }
    private val owner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                val name = contract::class.java.simpleName
                launched += name to input
                val result = answer(name, input)
                Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, result) }
            }
        }
    }
    private val comment = mutableStateOf("")
    private val subject = mutableStateOf("")
    private var closes = 0
    private var quoteModes = 0
    private var quality: FutaberImageQuality? = null
    private val settings = mutableStateOf(FutaberPostSettings())
    private lateinit var attachment: FutaberAttachment
    private val fixtures = mutableListOf<File>()

    @Before fun prepare() {
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        fixtures.forEach { it.delete() }
    }

    private val pasteItems = mutableStateOf(listOf<FutaberPasteItem>())
    private val pasteboardLink = mutableStateOf<FutaberPasteboardLink?>(null)
    private fun memoryPasteboard(): FutaberPasteboardLink = FutaberPasteboardLink(
        items = pasteItems.value,
        onAddText = { text, folder -> pasteItems.value = futaberPasteboardWithText(pasteItems.value, text, futaberNewPasteId(), folder); pasteboardLink.value = memoryPasteboard() },
        onAddImage = { _, _ -> },
        onRemove = { item -> pasteItems.value = futaberPasteboardWithout(pasteItems.value, item.id); pasteboardLink.value = memoryPasteboard() },
        onEdit = { edit -> pasteItems.value = edit(pasteItems.value); pasteboardLink.value = memoryPasteboard() },
        loadImage = { Result.failure(IllegalStateException("no pictures")) }
    )
    private val target = mutableStateOf<FutaberPostTarget>(FutaberPostTarget.Reply("b", "1", "試験のスレ"))
    private val legacySubject = mutableStateOf(false)

    private fun show(target: FutaberPostTarget = FutaberPostTarget.Reply("b", "1", "試験のスレ"), legacySubject: Boolean = false) {
        this.target.value = target
        this.legacySubject.value = legacySubject
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalActivityResultRegistryOwner provides owner) {
                FutaberTheme(FutaberThemeMode.Light) {
                    val scope = rememberCoroutineScope()
                    attachment = remember { FutaberAttachment(scope) }
                    FutaberPostScreen(
                        target = this.target.value, boardAddress = "may.2chan.net/b", subject = subject.value, comment = comment.value,
                        onSubjectChange = { subject.value = it }, onCommentChange = { comment.value = it },
                        onEnterQuoteMode = { quoteModes++ }, attachment = attachment,
                        capabilities = defaultBoardPostingCapabilities("https://may.2chan.net/b/"),
                        pickerPreference = AttachmentPickerPreference.MEDIA, preferredFileManagerPackage = null,
                        settings = settings.value, deleteKey = "pass", onSettingsChange = { _, _, _ -> }, onDeleteKeyChange = {},
                        onSend = { Result.success(null) }, onSent = {}, onClose = { closes++ },
                        legacySubjectField = this.legacySubject.value,
                        pasteboard = pasteboardLink.value,
                        onImageQualityChange = { quality = it; settings.value = settings.value.copy(imageQuality = it) }
                    )
                }
            }
        }
        rule.waitUntil(10_000) { present("futaber-post-comment") }
        rule.waitForIdle()
    }

    private fun present(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    /** Where a node starts and ends without the clip of the scrolling tools (an item off screen keeps its place). */
    private fun left(tag: String): Float = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.x
    private fun right(tag: String): Float = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { it.positionInRoot.x + it.size.width }

    private fun pngUri(width: Int = 24, height: Int = 16): Uri {
        val file = File(rule.activity.cacheDir, "compat_post_preview/futaber-post-${System.nanoTime()}.png").also { fixtures += it }
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        for (x in 0 until width / 2) for (y in 0 until height) bitmap.setPixel(x, y, android.graphics.Color.BLUE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return FileProvider.getUriForFile(rule.activity, "${rule.activity.packageName}.fileprovider", file)
    }

    private fun waitForAttachment() {
        rule.waitUntil(10_000) { present("futaber-post-attachment") && !attachment.processing }
        rule.waitForIdle()
    }

    // ---- M36・V17 ----

    @Test fun theToolsAreTheOriginalsSixThenThisAppsAndSendIsAFrameUntilThereIsSomethingToSend() {
        show()
        val six = listOf("futaber-post-attach", "futaber-post-kana", "futaber-post-pasteboard", "futaber-post-plane", "futaber-post-clear", "futaber-post-settings")
        val lefts = six.map { bounds(it).left }
        assertEquals(lefts.sorted(), lefts)
        val density = rule.activity.resources.displayMetrics.density
        six.forEach { assertEquals(it, 44f, bounds(it).width / density, 1f) }
        // The paper plane's place is empty: nothing to press or read there.
        assertTrue(rule.onNodeWithTag("futaber-post-plane").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription) == null)
        rule.onNodeWithText("ｶﾅ", useUnmergedTree = true).assertExists()
        // This app's tools follow the gear; 送信 is the 90×30 frame on the right.
        assertTrue(left("futaber-post-paste") >= right("futaber-post-settings") - 1f)
        assertTrue(left("futaber-post-quote-mode") >= right("futaber-post-paste") - 1f)
        val frame = bounds("futaber-post-send-frame")
        assertEquals(90f, frame.width / density, 1f)
        assertEquals(30f, frame.height / density, 1f)
        // Empty: a blue frame that cannot be pressed (its middle is the bar's white); 「0行 / 0B」 over the text's bottom right.
        rule.onNodeWithTag("futaber-post-send").assertIsNotEnabled()
        fun middleOfSend(): Int {
            val map = rule.onRoot().captureToImage().toPixelMap()
            return map[frame.center.x.toInt(), frame.center.y.toInt() - (8 * density).toInt()].let {
                android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt())
            }
        }
        assertEquals(0xFFFFFFFF.toInt(), middleOfSend())
        rule.onNodeWithTag("futaber-post-counts").assertTextEquals("0行 / 0B")
        val body = bounds("futaber-post-body")
        val counts = bounds("futaber-post-counts")
        assertTrue("the counter $counts is not at the text's bottom right $body", counts.right <= body.right + 1f && counts.right > body.center.x && counts.bottom <= body.bottom + 1f && counts.top > body.center.y)
        // Typed: the blue fill, pressable.
        rule.onNodeWithTag("futaber-post-comment").performTextInput("あ")
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("futaber-post-send").assertIsEnabled(); true }.getOrDefault(false) }
        rule.waitForIdle()
        assertNotEquals(0xFFFFFFFF.toInt(), middleOfSend())
        rule.waitUntil(5_000) { rule.onAllNodesWithText("1行 / 2B").fetchSemanticsNodes().isNotEmpty() }
    }

    // ---- M37・F28 ----

    @Test fun theCameraMenuOffersTheOriginalsThreeWithoutTheCameraAndEachOpensItsPicker() {
        show()
        rule.onNodeWithTag("futaber-post-attach").performClick()
        rule.waitUntil(5_000) { present("futaber-callout-recent") }
        val ids = listOf("recent", "file", "library")
        val lefts = ids.map { bounds("futaber-callout-$it").left }
        assertEquals(lefts.sorted(), lefts)
        // 2026-11-04: the original's 「カメラ」 is taken out (the user's decision): the app takes no picture.
        assertTrue("the camera item is gone", !present("futaber-callout-camera"))
        assertEquals(listOf("最近の画像", "ファイル", "ライブラリ"), ids.map {
            rule.onNodeWithTag("futaber-callout-$it").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { t -> t.text }
        })
        assertTrue("no tools without the extension", !present("futaber-callout-tool-drawing"))
        assertTrue("the menu is over the camera", bounds("futaber-callout").bottom <= bounds("futaber-post-attach").top + 1f)

        // 最近の画像 and ライブラリ: the OS photo picker (no permission), the picture attached.
        answer = { name, _ -> if (name == "PickVisualMedia") pngUri() else null }
        rule.onNodeWithTag("futaber-callout-recent").performClick()
        waitForAttachment()
        assertEquals("PickVisualMedia", launched.last().first)
        rule.onNodeWithTag("futaber-post-attachment-remove").performClick()
        rule.waitUntil(5_000) { !present("futaber-post-attachment") }
        rule.onNodeWithTag("futaber-post-attach").performClick()
        rule.waitUntil(5_000) { present("futaber-callout-library") }
        rule.onNodeWithTag("futaber-callout-library").performClick()
        waitForAttachment()
        assertEquals("PickVisualMedia", launched.last().first)

        // ファイル: the file picker of before (this preference: GET_CONTENT).
        answer = { name, _ -> if (name == "GetContent") pngUri() else null }
        rule.onNodeWithTag("futaber-post-attachment-remove").performClick()
        rule.onNodeWithTag("futaber-post-attach").performClick()
        rule.waitUntil(5_000) { present("futaber-callout-file") }
        rule.onNodeWithTag("futaber-callout-file").performClick()
        waitForAttachment()
        assertEquals("GetContent", launched.last().first)

        // A cancelled picker attaches nothing.
        answer = { _, _ -> null }
        rule.onNodeWithTag("futaber-post-attachment-remove").performClick()
        rule.onNodeWithTag("futaber-post-attach").performClick()
        rule.waitUntil(5_000) { present("futaber-callout-library") }
        rule.onNodeWithTag("futaber-callout-library").performClick()
        rule.waitUntil(5_000) { launched.size == 4 }
        rule.waitForIdle()
        assertTrue(!present("futaber-post-attachment"))
        // F28: back from a picker (even a cancelled one) the text has the keyboard's focus again.
        rule.waitUntil(5_000) {
            rule.onNodeWithTag("futaber-post-comment").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true
        }
    }

    // ---- C43 ----

    @Test fun jpgCompressionDrawsAnAttachedPngAsJpegAndHighQualityKeepsIt() {
        show()
        answer = { name, _ -> if (name == "PickVisualMedia") pngUri() else null }
        rule.onNodeWithTag("futaber-post-attach").performClick()
        rule.waitUntil(5_000) { present("futaber-callout-library") }
        rule.onNodeWithTag("futaber-callout-library").performClick()
        waitForAttachment()
        assertTrue(attachment.image!!.fileName.endsWith(".png"))
        // 書き込み設定「画像の品質」→「JPG圧縮」.
        rule.onNodeWithTag("futaber-post-settings").performClick()
        rule.waitUntil(5_000) { present("futaber-post-settings-page") }
        rule.onNodeWithTag("futaber-post-quality-jpeg").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(FutaberImageQuality.Jpeg, quality)
        rule.onNodeWithTag("futaber-post-quality-jpeg").assertIsSelected()
        rule.onNodeWithTag("futaber-post-settings-done").performClick()
        rule.waitUntil(5_000) { !present("futaber-post-settings-page") }
        rule.onNodeWithTag("futaber-post-attachment-remove").performClick()
        rule.onNodeWithTag("futaber-post-attach").performClick()
        rule.waitUntil(5_000) { present("futaber-callout-library") }
        rule.onNodeWithTag("futaber-callout-library").performClick()
        waitForAttachment()
        val bytes = attachment.image!!.bytes
        assertTrue(attachment.image!!.fileName, attachment.image!!.fileName.endsWith(".jpg"))
        assertTrue(bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte())
        // The transparent half went onto white, not black.
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val pixel = decoded.getPixel(decoded.width - 2, decoded.height / 2)
        decoded.recycle()
        assertTrue(Integer.toHexString(pixel), android.graphics.Color.red(pixel) > 240 && android.graphics.Color.green(pixel) > 240)
    }

    // ---- G12 ----

    @Test fun pullingTheTextDownAtItsTopEntersQuoteModeAndALongTextScrollsFirst() {
        show()
        rule.onNodeWithTag("futaber-post-body").performTouchInput { swipeDown(startY = top + 40f, endY = bottom - 40f, durationMillis = 400) }
        rule.waitForIdle()
        assertEquals(1, quoteModes)
        // A long text, scrolled to its end: pulling down scrolls it back up first and does not enter quote mode.
        rule.runOnIdle { comment.value = (1..120).joinToString("\n") { "行$it" } }
        rule.waitForIdle()
        rule.onNodeWithTag("futaber-post-body").performTouchInput { repeat(3) { swipeUp(startY = bottom - 40f, endY = top + 40f, durationMillis = 200) } }
        rule.waitForIdle()
        rule.onNodeWithTag("futaber-post-body").performTouchInput { swipeDown(startY = top + 40f, endY = centerY, durationMillis = 400) }
        rule.waitForIdle()
        assertEquals("a scrolled text went into quote mode", 1, quoteModes)
    }

    // ---- G29 ----

    @Test fun aRightSwipeClosesTheWritingAndALeftOneDoesNot() {
        show()
        rule.onNodeWithTag("futaber-post-comment").performTextInput("残す")
        rule.onNodeWithTag("futaber-post-body").performTouchInput { swipeLeft(startX = right - 20f, endX = left + 20f) }
        rule.waitForIdle()
        assertEquals(0, closes)
        rule.onNodeWithTag("futaber-post-body").performTouchInput { swipeRight(startX = centerX - 100f, endX = right - 10f) }
        rule.waitForIdle()
        assertEquals(1, closes)
        assertEquals("残す", comment.value)
    }

    // ---- M42 ----

    @Test fun clearsLongPressBringsBackTheLastClearedText() {
        show()
        rule.onNodeWithTag("futaber-post-comment").performTextInput("消した本文")
        rule.onNodeWithTag("futaber-post-clear").performClick()
        rule.waitUntil(5_000) { comment.value.isEmpty() }
        rule.onNodeWithTag("futaber-post-clear").performTouchInput { longClick() }
        rule.waitUntil(5_000) { present("futaber-callout-restore-cleared") }
        rule.onNodeWithText("最後に削除したテキストを復元").assertExists()
        assertTrue("the bubble is over ×", bounds("futaber-callout").bottom <= bounds("futaber-post-clear").top + 1f)
        rule.onNodeWithTag("futaber-callout-restore-cleared").performClick()
        rule.waitUntil(5_000) { comment.value == "消した本文" }
        rule.waitUntil(5_000) { !present("futaber-callout") }
    }

    // ---- S16・C44 ----

    @Test fun thePostSettingsAreTheOriginalsFullScreenTableAndTheDeviceInfoGoesIntoTheText() {
        show()
        rule.onNodeWithTag("futaber-post-comment").performTextInput("本文")
        rule.onNodeWithTag("futaber-post-settings").performClick()
        rule.waitUntil(5_000) { present("futaber-post-settings-page") }
        // Over the whole screen, 「完了」 at the top left.
        val page = bounds("futaber-post-settings-page")
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.width, page.width, 1f)
        assertTrue(bounds("futaber-post-settings-done").left < root.width / 4f)
        val sections = listOf("writing", "quality", "other", "name").map { bounds("futaber-post-settings-section-$it").top }
        assertEquals(sections.sorted(), sections)
        listOf("futaber-post-confirm-switch", "futaber-post-deletekey", "futaber-post-quality-high", "futaber-post-quality-jpeg", "futaber-post-device-info", "futaber-post-name", "futaber-post-email")
            .forEach { assertTrue(it, present(it)) }
        rule.onNodeWithTag("futaber-post-quality-high").assertIsSelected()
        // 端末情報出力: the device line under the text, and back to the writing.
        rule.onNodeWithTag("futaber-post-device-info").performScrollTo().performClick()
        rule.waitUntil(5_000) { !present("futaber-post-settings-page") }
        assertTrue(comment.value, comment.value.startsWith("本文\n") && comment.value.contains("ふたちゃ"))
    }

    // ---- F17 ----

    @Test fun aNewThreadHasNoSubjectFieldUnlessTheOldBehaviourIsOn() {
        show(FutaberPostTarget.CreateThread("b"))
        assertTrue(!present("futaber-post-subject"))
        rule.onNodeWithText("may.2chan.net/b").assertExists()
        rule.runOnIdle { legacySubject.value = true }
        rule.waitUntil(5_000) { present("futaber-post-subject") }
    }

    // ---- pictures for the side-by-side with futaber (no assertions beyond reaching each state) ----

    private fun screencap(name: String) {
        rule.waitForIdle()
        Thread.sleep(800)
        val pfd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/futaber-lb8-$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }

    @Test fun captureTheWritingForReview() {
        pasteItems.value = futaberPasteboardWithFolder(
            futaberPasteboardWithText(futaberPasteboardWithText(emptyList(), "よく使う定型文", "a"), "ｷﾀ━━━━(ﾟ∀ﾟ)━━━━!!", "b"),
            "定型", "f"
        )
        pasteboardLink.value = memoryPasteboard()
        show()
        screencap("01-reply-empty")
        rule.onNodeWithTag("futaber-post-comment").performTextInput("9月下旬に急に体調悪化")
        screencap("02-reply-typed")
        rule.onNodeWithTag("futaber-post-attach").performClick()
        rule.waitUntil(5_000) { present("futaber-callout-recent") }
        screencap("03-camera-menu")
        rule.onNodeWithTag("futaber-callout-outside").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 40f)) }
        rule.onNodeWithTag("futaber-post-clear").performClick()
        rule.onNodeWithTag("futaber-post-clear").performTouchInput { longClick() }
        rule.waitUntil(5_000) { present("futaber-callout-restore-cleared") }
        screencap("04-clear-restore")
        rule.onNodeWithTag("futaber-callout-restore-cleared").performClick()
        rule.onNodeWithTag("futaber-post-settings").performClick()
        rule.waitUntil(5_000) { present("futaber-post-settings-page") }
        screencap("05-post-settings")
        rule.onNodeWithTag("futaber-post-settings-done").performClick()
        rule.onNodeWithTag("futaber-post-pasteboard").performClick()
        rule.waitUntil(5_000) { present("futaber-pasteboard") }
        screencap("06-pasteboard")
        rule.onNodeWithTag("futaber-pasteboard-edit").performClick()
        screencap("07-pasteboard-edit")
        rule.onNodeWithTag("futaber-pasteboard-edit").performClick()
        rule.onNodeWithTag("futaber-pasteboard-folder").performClick()
        screencap("08-pasteboard-folder")
        rule.onNodeWithTag("futaber-pasteboard-back").performClick()
        rule.onNodeWithTag("futaber-pasteboard-close").performClick()
        rule.runOnIdle { target.value = FutaberPostTarget.CreateThread("b"); comment.value = "" }
        rule.waitForIdle()
        screencap("09-create-thread")
    }
}
