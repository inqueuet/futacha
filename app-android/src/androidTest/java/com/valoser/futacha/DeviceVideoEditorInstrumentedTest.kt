@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import org.hamcrest.Matchers.equalTo
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.analysis.*
import com.valoser.futacha.shared.media.video.inspectDeviceVideo
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.shared.ui.media.*
import com.valoser.futacha.testing.video.VideoEditFixtures
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath

class DeviceVideoEditorInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = mutableStateOf(MediaFeatureSettings.Disabled)
    private val gate = MediaFeatureGate()
    private var launches = 0
    private var cancelFirst = false
    private var models: ModelStore? = null
    private val modelDirectory = File(context.cacheDir, "video-ui-models")
    private val sourceFile = File(context.cacheDir, "compat_post_preview/device-video-ui.mp4")
    private val beforeDirectories = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("device-video-") }.toSet()
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitUntil(10_000) { context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("device-video-") && it !in beforeDirectories } }
        sourceFile.delete()
        runBlocking { models?.closeAndAwait() }
        modelDirectory.deleteRecursively()
    }
    private fun show(fixture: String = "portrait") {
        sourceFile.parentFile!!.mkdirs(); sourceFile.writeBytes(VideoEditFixtures.bytes(fixture))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", sourceFile)
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launches++
                Handler(Looper.getMainLooper()).post {
                    if (cancelFirst && launches == 1) dispatchResult(code, Activity.RESULT_CANCELED, null)
                    else dispatchResult(code, Activity.RESULT_OK, Intent().setData(uri))
                }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val fs = com.valoser.futacha.shared.util.createFileSystem(context)
        val store = com.valoser.futacha.shared.state.createAppStateStore(context)
        rule.runOnUiThread { rule.activity.setContent {
            SideEffect { gate.update(settings.value) }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner,
                LocalAnalysisModelStore provides models,
                LocalMediaFeatureGate provides gate, LocalMediaFeatureSettings provides settings.value,
                LocalMediaFeatureUpdater provides { transform -> settings.value = transform(settings.value) }) {
                MaterialTheme { DeviceVideoEditingHost(fs, store) { Column {
                    DeviceVideoEditorSettings(); DeviceVideoEditorMenuItem {}
                } } }
            }
        } }
    }
    @Test fun offHidesMenuAndIndependentToggleSurvivesCancelledSelection() {
        cancelFirst = true; show()
        rule.onNodeWithTag("device-video-editor-menu").assertDoesNotExist()
        rule.onNodeWithTag("video-editor-settings-toggle").performClick()
        rule.onNodeWithTag("device-video-editor-menu").performClick()
        rule.waitUntil { launches == 1 }
        rule.onNodeWithTag("video-editor").assertDoesNotExist()
        rule.onNodeWithTag("device-video-editor-menu").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("video-editor-add").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(settings.value.promptDisplayEnabled); assertFalse(settings.value.imageEditorEnabled)
        rule.runOnUiThread { settings.value = settings.value.copy(videoEditorEnabled = false, promptDisplayEnabled = true) }
        rule.onNodeWithTag("video-editor").assertDoesNotExist()
        rule.onNodeWithTag("device-video-editor-menu").assertDoesNotExist()
        assertTrue(settings.value.promptDisplayEnabled)
    }
    @Test fun deviceUriStreamsToEditorAndBlackVideoCanBeReviewed() {
        settings.value = MediaFeatureSettings(videoEditorEnabled = true); show()
        rule.onNodeWithTag("device-video-editor-menu").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("video-editor-add").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("video-editor-add").performScrollTo().performClick()
        rule.onNodeWithTag("video-region-controls").performScrollTo()
        rule.onNodeWithText("黒塗り").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        val initialTime = rule.onNodeWithTag("video-editor-time").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        rule.onNodeWithTag("video-editor-play-pause").performScrollTo().performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("再生中").fetchSemanticsNodes().isNotEmpty() &&
                rule.onNodeWithTag("video-editor-time").fetchSemanticsNode().config[SemanticsProperties.Text].first().text != initialTime
        }
        rule.onNodeWithTag("video-editor-export").assertIsNotEnabled()
        // Freeze the native surface, then inspect real playback pixels (Compose capture omits it).
        val area = IntArray(4)
        onView(withClassName(equalTo("androidx.media3.ui.PlayerView"))).check { view, failure ->
            if (failure != null) throw failure
            val native = view.javaClass.getMethod("getPlayer").invoke(view)
            native.javaClass.getMethod("pause").invoke(native)
            val position = IntArray(2); view.getLocationOnScreen(position)
            area[0] = position[0]; area[1] = position[1]; area[2] = view.width; area[3] = view.height
        }
        val info = runBlocking { inspectDeviceVideo(sourceFile.absolutePath) }
        val scale = minOf(area[2].toFloat() / info.width, area[3].toFloat() / info.height)
        var pixelDiagnostic = ""
        try { rule.waitUntil(5_000) {
            val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            try {
                val cx = area[0] + area[2] / 2; val cy = area[1] + area[3] / 2
                val covered = shot.getPixel(cx, cy)
                val outside = shot.getPixel((cx - info.width * scale / 4).toInt(), (cy - info.height * scale / 4).toInt())
                pixelDiagnostic = "area=${area.toList()} center=$covered outside=$outside"
                listOf(0, 8, 16).all { (covered ushr it and 255) < 20 } &&
                    listOf(0, 8, 16).any { (outside ushr it and 255) > 70 }
            } finally { shot.recycle() }
        } } catch (failure: Throwable) {
            val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            try { File(context.getExternalFilesDir(null), "editing-playback-failure.png").outputStream().use {
                shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } } finally { shot.recycle() }
            throw AssertionError(pixelDiagnostic, failure)
        }
        rule.onNodeWithTag("video-editor-play-pause").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("video-editor-canvas").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("video-editor-playback").assertDoesNotExist()
        rule.onNodeWithTag("video-editor-export").performClick()
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("video-editor-save").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("video-editor-error").assertDoesNotExist()
        rule.onNodeWithTag("video-editor-save").assertExists()
        assertArrayEquals(VideoEditFixtures.bytes("portrait"), sourceFile.readBytes())
        assertFalse(settings.value.promptDisplayEnabled)
    }
    @Test fun videoDetectionTrackingContoursAndCorrectionRequireReviewBeforeExport() {
        settings.value = MediaFeatureSettings(videoEditorEnabled = true)
        val bytes = mapOf(AnalysisModel.NUDE_NET to InferenceFixtures.detector(),
            AnalysisModel.MOBILE_SAM_ENCODER to InferenceFixtures.samEncoder(), AnalysisModel.MOBILE_SAM_DECODER to InferenceFixtures.samDecoder())
        val specs = AnalysisModels.all.map { spec -> bytes[spec.id]?.let { data ->
            val hash = data.toByteString().sha256().hex()
            spec.copy(bytes = data.size.toLong(), sha256 = hash,
                distribution = ModelDistribution("https://model.test/${spec.id}.onnx", data.size.toLong(), hash))
        } ?: spec }
        models = ModelStore(gate, { modelDirectory.absolutePath.toPath() },
            downloader = { ModelDownloader { _, _ -> error("Unexpected model download") } }, models = specs)
        show("variable")
        val info = runBlocking { inspectDeviceVideo(sourceFile.absolutePath) }
        rule.onNodeWithTag("device-video-editor-menu").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("video-editor-detect").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("video-editor-timeline").performSemanticsAction(SemanticsActions.SetProgress) { it(info.frames.timeAt(5).toFloat()) }
        rule.onNodeWithText("解析はこのコマまで").performScrollTo().performClick()
        rule.onNodeWithTag("video-editor-timeline").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(info.frames.timeAt(0).toFloat()) }
        rule.onNodeWithTag("video-editor-detect").performScrollTo().performClick()
        rule.onNodeWithText("顔も検出する（実写モデル）").performScrollTo().assertIsOff()
        rule.onNodeWithTag("video-detection-start").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("video-editor-error").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("video-editor-region-1").assertDoesNotExist()
        runBlocking { for ((id, data) in bytes) models!!.import(id, gate.permit(MediaFeature.VIDEO_EDITOR)!!) { Buffer().write(data) } }
        rule.onNodeWithTag("video-editor-detect").performScrollTo().performClick()
        rule.onNodeWithText("輪郭に沿って配置する").performScrollTo().performClick()
        rule.onNodeWithTag("video-detection-start").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("video-review-confirm").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("video-editor-error").assertDoesNotExist()
        rule.onNodeWithTag("video-editor-export").assertIsNotEnabled()
        rule.onNodeWithTag("video-editor-region-2").performScrollTo().performClick()
        rule.onNodeWithTag("video-contour-margin").assertExists()
        rule.onNodeWithTag("video-review-confirm").performScrollTo().performClick()
        rule.onNodeWithTag("video-editor-export").assertIsEnabled()
        rule.onNodeWithTag("video-track-forward").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("video-review-confirm").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("video-editor-export").assertIsNotEnabled()
        rule.onNodeWithTag("video-contour-frame").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("video-editor-progress").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("video-review-confirm").performScrollTo().performClick()
        rule.onNodeWithTag("video-contour-tool-erase").performScrollTo().performClick()
        rule.onNodeWithTag("video-editor-canvas").performTouchInput { click(center) }
        rule.onNodeWithTag("video-editor-export").assertIsNotEnabled()
        rule.onNodeWithTag("video-region-controls").performScrollTo()
        rule.onNodeWithText("黒塗り").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        rule.onNodeWithTag("video-review-confirm").performScrollTo().performClick()
        rule.onNodeWithTag("video-editor-export").performClick()
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("video-editor-save").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("video-editor-error").assertDoesNotExist()
        assertArrayEquals(VideoEditFixtures.bytes("variable"), sourceFile.readBytes())
        assertFalse(settings.value.promptDisplayEnabled); assertFalse(settings.value.imageEditorEnabled)
    }
}
