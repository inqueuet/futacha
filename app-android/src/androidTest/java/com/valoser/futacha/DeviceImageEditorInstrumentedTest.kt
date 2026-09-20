@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.graphics.Bitmap
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.edit.*
import com.valoser.futacha.shared.media.analysis.*
import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.shared.ui.media.*
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import okio.Path.Companion.toPath
import okio.ByteString.Companion.toByteString

class DeviceImageEditorInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val loader = ImageLoader.Builder(context).diskCache(null).build()
    private val gate = MediaFeatureGate()
    private val settings = mutableStateOf(MediaFeatureSettings(imageEditorEnabled = true))
    private val open = mutableStateOf(true)
    private val output = AtomicReference<ImageData?>()
    private var models: ModelStore? = null
    private var pickerOwner: ActivityResultRegistryOwner? = null
    private val modelDirectory = File(context.cacheDir, "image-model-ui-${System.nanoTime()}")
    private val acquisitions = AtomicInteger()
    private val bytes = ByteArrayOutputStream().apply {
        val bitmap = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888)
        for (y in 0 until 48) for (x in 0 until 80) bitmap.setPixel(x, y,
            if (x < 40) android.graphics.Color.RED else android.graphics.Color.GREEN)
        bitmap.compress(Bitmap.CompressFormat.PNG,100,this); bitmap.recycle()
    }.toByteArray()

    @After fun finish() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { models?.closeAndAwait() }
        modelDirectory.deleteRecursively()
        loader.shutdown()
    }
    private fun showEditor(accept: suspend (ImageData) -> Unit = { output.set(it) }) {
        rule.runOnUiThread { rule.activity.setContent {
            SideEffect { gate.update(settings.value) }
            CompositionLocalProvider(LocalFutachaImageLoader provides loader,
                LocalActivityResultRegistryOwner provides (pickerOwner ?: rule.activity),
                LocalMediaFeatureSettings provides settings.value, LocalMediaFeatureGate provides gate,
                LocalAnalysisModelStore provides models) {
                MaterialTheme { if (open.value) ImageEditorDialog(ImageEditInput(ImageData(bytes,"phone.png")),
                    onDismiss = { open.value = false }, onResult = accept) }
            }
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("image-editor-export").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { runCatching { rule.onNodeWithTag("image-editor-export").assertIsEnabled(); true }.getOrDefault(false) }
    }
    private fun prepareModels(transfer: suspend (okio.BufferedSink, ByteArray) -> Unit = { sink, bytes -> sink.write(bytes); Unit }) {
        val data = InferenceFixtures.detector()
        val hash = data.toByteString().sha256().hex()
        val spec = AnalysisModelSpec(AnalysisModel.NUDE_NET, "fixture", "fixture", "https://model.test", data.size.toLong(), hash,
            ModelDistribution("https://model.test/detector.onnx", data.size.toLong(), hash))
        models = ModelStore(gate, directory = { modelDirectory.absolutePath.toPath() },
            downloader = { ModelDownloader { _, sink -> acquisitions.incrementAndGet(); transfer(sink, data) } },
            models = AnalysisModels.all.map { if (it.id == spec.id) spec else it })
    }

    @Test fun automaticGenitalCandidatesRequireReviewAndEditsRevokeIt() {
        prepareModels(); showEditor()
        rule.onNodeWithTag("image-editor-detect").performScrollTo().performClick()
        rule.onNodeWithText("顔も検出する（実写モデル）").assertIsOff()
        rule.onNodeWithTag("image-detection-start").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("image-editor-error").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, acquisitions.get()) // Enabling or starting detection must not download a missing model.
        rule.onNodeWithTag("image-editor-models").performScrollTo().performClick()
        rule.waitUntil(10_000) { runCatching { rule.onNodeWithTag("analysis-model-download-NUDE_NET").assertIsEnabled(); true }.getOrDefault(false) }
        rule.onNodeWithTag("analysis-model-download-NUDE_NET").performScrollTo().performClick()
        rule.waitUntil(10_000) { runCatching { rule.onNodeWithTag("analysis-model-state-NUDE_NET").assertTextContains("導入済み（検証済み）"); true }.getOrDefault(false) }
        rule.onNodeWithText("閉じる").performClick()
        assertEquals(1, acquisitions.get())
        rule.onNodeWithTag("image-editor-detect").performScrollTo().performClick()
        rule.onNodeWithTag("image-detection-start").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("image-editor-confirm-review").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("image-editor-export").assertIsNotEnabled()
        rule.onNodeWithText("1: 男性器候補").assertExists()
        rule.onNodeWithText("2: 女性器候補").assertExists()
        rule.onNodeWithTag("image-editor-confirm-review").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-export").assertIsEnabled()
        // performScrollTo on a chip only scrolls its nearest (horizontal) row.
        // First reveal the entire row in the editor's vertical controls panel.
        rule.onNodeWithTag("image-editor-region-2").onParent().performScrollTo()
        rule.onNodeWithTag("image-editor-region-2").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-region-2").assertIsSelected()
        rule.onNodeWithTag("image-editor-black").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-black").assertIsSelected()
        rule.onNodeWithTag("image-editor-export").assertIsNotEnabled()
        rule.onNodeWithTag("image-editor-confirm-review").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-export").performClick()
        rule.waitUntil(10_000) { output.get() != null }
        val saved = output.get()!!.bytes
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(saved, 0, saved.size)
        val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertTrue("Edited candidate must cover image center: ${bitmap.width}x${bitmap.height}, RGB ${center.toUInt().toString(16)}",
            android.graphics.Color.red(center) < 20 && android.graphics.Color.green(center) < 20 && android.graphics.Color.blue(center) < 20)
        bitmap.recycle()
        assertEquals(1, acquisitions.get())
        assertFalse(settings.value.promptDisplayEnabled)
    }

    @Test fun modelImportCanRetryCancellationAndRejectsCorruptionWithoutReplacingInstalledModel() {
        prepareModels()
        val data = InferenceFixtures.detector()
        val valid = Uri.parse("content://com.valoser.futacha.test.attachments/model-valid.onnx")
        val corrupt = Uri.parse("content://com.valoser.futacha.test.attachments/model-corrupt.onnx")
        context.contentResolver.openOutputStream(valid)!!.use { it.write(data) }
        context.contentResolver.openOutputStream(corrupt)!!.use { it.write(data.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }) }
        var launches = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, contract.createIntent(context, input).action)
                val attempt = ++launches
                Handler(Looper.getMainLooper()).post {
                    if (attempt == 1) dispatchResult(code, Activity.RESULT_CANCELED, null)
                    else dispatchResult(code, Activity.RESULT_OK, Intent().setData(if (attempt == 2) valid else corrupt))
                }
            }
        }
        pickerOwner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        try {
            showEditor()
            rule.onNodeWithTag("image-editor-models").performScrollTo().performClick()
            fun waitReady() = rule.waitUntil(10_000) { runCatching { rule.onNodeWithTag("analysis-model-import-NUDE_NET").assertIsEnabled(); true }.getOrDefault(false) }
            waitReady()
            rule.onNodeWithTag("analysis-model-import-NUDE_NET").performScrollTo().performClick()
            waitReady()
            rule.onNodeWithTag("analysis-model-state-NUDE_NET").assertTextEquals("未導入")
            rule.onNodeWithTag("analysis-model-import-NUDE_NET").performScrollTo().performClick()
            rule.waitUntil(10_000) { runCatching { rule.onNodeWithTag("analysis-model-state-NUDE_NET").assertTextEquals("導入済み（検証済み）"); true }.getOrDefault(false) }
            waitReady()
            rule.onNodeWithTag("analysis-model-import-NUDE_NET").performScrollTo().performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("analysis-model-error").fetchSemanticsNodes().isNotEmpty() }
            waitReady()
            rule.onNodeWithTag("analysis-model-state-NUDE_NET").assertTextEquals("導入済み（検証済み）")
            assertEquals(3, launches); assertEquals(0, acquisitions.get())
            assertArrayEquals(data, modelDirectory.listFiles()!!.single().readBytes())
        } finally {
            context.contentResolver.delete(valid, null, null)
            context.contentResolver.delete(corrupt, null, null)
        }
    }

    @Test fun disablingEditorCancelsModelTransferAndKeepsNoPartialModel() {
        val started = AtomicBoolean(); val stopped = AtomicBoolean()
        prepareModels { sink, _ ->
            sink.writeUtf8("partial"); sink.emit(); started.set(true)
            try { awaitCancellation() } finally { stopped.set(true) }
        }
        showEditor()
        rule.onNodeWithTag("image-editor-models").performScrollTo().performClick()
        rule.waitUntil(10_000) { runCatching { rule.onNodeWithTag("analysis-model-download-NUDE_NET").assertIsEnabled(); true }.getOrDefault(false) }
        rule.onNodeWithTag("analysis-model-download-NUDE_NET").performScrollTo().performClick()
        rule.waitUntil(10_000) { started.get() }
        rule.runOnUiThread { settings.value = MediaFeatureSettings(videoEditorEnabled = true) }
        rule.waitUntil(10_000) { stopped.get() && modelDirectory.listFiles().orEmpty().isEmpty() }
        rule.onNodeWithTag("analysis-model-dialog").assertDoesNotExist()
        rule.onNodeWithTag("image-editor").assertDoesNotExist()
        assertNull(output.get())
    }
    @Test fun promptOffStillAllowsLocalBlackCoverAndJpegExport() {
        showEditor()
        rule.onNodeWithTag("image-editor-tool-regions").performClick()
        rule.onNodeWithTag("image-editor-add-region").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-black").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-undo").assertIsEnabled()
        rule.onNodeWithTag("image-editor-export").performClick()
        rule.waitUntil(10_000) { output.get() != null }
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(output.get()!!.bytes,0,output.get()!!.bytes.size)
        assertEquals(80, bitmap.width); assertEquals(48,bitmap.height)
        val center = bitmap.getPixel(40,24)
        assertTrue(android.graphics.Color.red(center) < 20 && android.graphics.Color.green(center) < 20)
        assertTrue(android.graphics.Color.red(bitmap.getPixel(5,5)) > 220)
        bitmap.recycle()
        assertFalse(settings.value.promptDisplayEnabled)
    }
    @Test fun undoRestoresOriginalAndTapPaintIsAnUndoableOperation() {
        showEditor()
        rule.onNodeWithTag("image-editor-canvas").performTouchInput { click(center) }
        rule.onNodeWithTag("image-editor-undo").assertIsEnabled().performClick()
        rule.onNodeWithTag("image-editor-undo").assertIsNotEnabled()
        rule.onNodeWithTag("image-editor-export").performClick()
        rule.waitUntil(10_000) { output.get() != null }
        val result = output.get()!!
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(result.bytes,0,result.bytes.size)
        assertTrue(android.graphics.Color.red(bitmap.getPixel(20,24)) > 220)
        assertTrue(android.graphics.Color.green(bitmap.getPixel(60,24)) > 220)
        bitmap.recycle()
    }
    @Test fun zoomAndPanKeepCoverCoordinatesAndDoNotCreateUndoEntries() {
        showEditor()
        rule.onNodeWithTag("image-editor-zoom-in").performClick()
        rule.onNodeWithTag("image-editor-zoom-level").assertTextEquals("表示 200％")
        rule.onNodeWithTag("image-editor-tool-view").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-canvas").performTouchInput {
            val c = center
            pinch(
                start0 = c + Offset(-20f, 0f), end0 = c + Offset(-60f, 0f),
                start1 = c + Offset(20f, 0f), end1 = c + Offset(60f, 0f)
            )
        }
        val zoom = rule.onNodeWithTag("image-editor-zoom-level").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text.filter(Char::isDigit).toInt()
        assertTrue("Pinch must enlarge the image within the supported range: $zoom", zoom in 201..800)
        rule.onNodeWithTag("image-editor-canvas").performTouchInput { swipe(center, centerRight) }
        rule.onNodeWithTag("image-editor-undo").assertIsNotEnabled()
        rule.onNodeWithTag("image-editor-tool-regions").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-add-region").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-black").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-zoom-reset").performClick()
        rule.onNodeWithTag("image-editor-zoom-level").assertTextEquals("表示 100％")
        rule.onNodeWithTag("image-editor-export").performClick()
        rule.waitUntil(10_000) { output.get() != null }
        val result = output.get()!!
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
        assertEquals(80, bitmap.width); assertEquals(48, bitmap.height)
        assertTrue(android.graphics.Color.red(bitmap.getPixel(40,24)) < 20)
        assertTrue(android.graphics.Color.green(bitmap.getPixel(40,24)) < 20)
        assertTrue(android.graphics.Color.red(bitmap.getPixel(5,5)) > 220)
        assertTrue(android.graphics.Color.green(bitmap.getPixel(75,43)) > 220)
        bitmap.recycle()
    }
    private fun prepareContourModels(includeDetector: Boolean = false): Map<AnalysisModel, ByteArray> {
        val data = mapOf(AnalysisModel.MOBILE_SAM_ENCODER to InferenceFixtures.samEncoder(),
            AnalysisModel.MOBILE_SAM_DECODER to InferenceFixtures.samDecoder()) +
            (if (includeDetector) mapOf(AnalysisModel.NUDE_NET to InferenceFixtures.detector()) else emptyMap())
        val specs = AnalysisModels.all.map { spec -> data[spec.id]?.let { bytes ->
            val hash = bytes.toByteString().sha256().hex()
            spec.copy(bytes = bytes.size.toLong(), sha256 = hash,
                distribution = ModelDistribution("https://model.test/${spec.id}.onnx", bytes.size.toLong(), hash))
        } ?: spec }
        models = ModelStore(gate, { modelDirectory.absolutePath.toPath() },
            downloader = { ModelDownloader { _, _ -> acquisitions.incrementAndGet(); error("Unexpected model download") } }, models = specs)
        return data
    }
    @Test fun contourExtractionAndManualCorrectionPreservePixelsAndRequireReview() {
        val data = prepareContourModels()
        showEditor()
        rule.onNodeWithTag("image-editor-tool-regions").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-add-region").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-black").performScrollTo().performClick()
        rule.onNodeWithTag("image-contour-extract").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("image-editor-error").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, acquisitions.get())
        runBlocking { for ((id, bytes) in data) models!!.import(id, gate.permit(MediaFeature.IMAGE_EDITOR)!!) { okio.Buffer().write(bytes) } }
        rule.onNodeWithTag("image-contour-extract").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("image-editor-confirm-review").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("image-editor-export").assertIsNotEnabled()
        rule.onNodeWithTag("image-editor-confirm-review").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-export").assertIsEnabled()
        rule.onNodeWithTag("image-contour-tool-erase").performScrollTo().performClick()
        rule.onNodeWithTag("image-contour-brush").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.2f) }
        rule.onNodeWithTag("image-editor-canvas").performTouchInput { click(center) }
        rule.onNodeWithTag("image-editor-export").assertIsNotEnabled()
        rule.onNodeWithTag("image-editor-undo").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-export").assertIsEnabled()
        rule.onNodeWithTag("image-editor-canvas").performTouchInput { click(center) }
        rule.onNodeWithTag("image-editor-confirm-review").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-export").performClick()
        rule.waitUntil(10_000) { output.get() != null }
        val result = output.get()!!
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
        assertTrue("Erased center reveals the original green pixel", android.graphics.Color.green(bitmap.getPixel(42,24)) > 180)
        assertTrue("Contour remains black outside the erased hole", android.graphics.Color.red(bitmap.getPixel(32,24)) < 30)
        assertTrue("Box corners outside the contour remain original", android.graphics.Color.red(bitmap.getPixel(24,15)) > 220)
        bitmap.recycle(); assertEquals(0, acquisitions.get())
    }
    @Test fun automaticDetectionCanPlaceContoursAndStillRequiresReview() {
        val data = prepareContourModels(includeDetector = true)
        showEditor()
        runBlocking { for ((id, bytes) in data) models!!.import(id, gate.permit(MediaFeature.IMAGE_EDITOR)!!) { okio.Buffer().write(bytes) } }
        rule.onNodeWithTag("image-editor-detect").performScrollTo().performClick()
        rule.onNodeWithText("輪郭に沿って配置する").performScrollTo().assertIsOff().performClick()
        rule.onNodeWithTag("image-detection-start").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("image-editor-confirm-review").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("image-editor-export").assertIsNotEnabled()
        rule.onNodeWithTag("image-editor-region-2").onParent().performScrollTo()
        rule.onNodeWithTag("image-editor-region-2").performScrollTo().performClick()
        rule.onNodeWithTag("image-contour-margin").assertExists()
        rule.onNodeWithTag("image-editor-black").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-confirm-review").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-export").performClick()
        rule.waitUntil(10_000) { output.get() != null }
        val result = output.get()!!
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
        assertTrue(android.graphics.Color.red(bitmap.getPixel(40,24)) < 20)
        assertTrue(android.graphics.Color.green(bitmap.getPixel(40,24)) < 20)
        assertTrue(android.graphics.Color.red(bitmap.getPixel(26,10)) > 220)
        bitmap.recycle(); assertEquals(0, acquisitions.get())
    }
    @Test fun emptyManualContourDisablesSavingAndRectangleResetCanBeUndone() {
        showEditor()
        rule.onNodeWithTag("image-editor-tool-regions").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-add-region").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-black").performScrollTo().performClick()
        rule.onNodeWithTag("image-contour-tool-add").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-canvas").performTouchInput { click(center) }
        rule.onNodeWithTag("image-contour-tool-erase").performScrollTo().performClick()
        rule.onNodeWithTag("image-contour-brush").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.2f) }
        rule.onNodeWithTag("image-editor-canvas").performTouchInput { click(center) }
        rule.onNodeWithTag("image-contour-empty").assertExists()
        rule.onNodeWithTag("image-editor-export").assertIsNotEnabled()
        rule.onNodeWithTag("image-contour-reset").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-export").assertIsEnabled()
        rule.onNodeWithTag("image-editor-undo").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-export").assertIsNotEnabled()
        rule.onNodeWithText("やり直す").performScrollTo().performClick()
        rule.onNodeWithTag("image-editor-export").assertIsEnabled().performClick()
        rule.waitUntil(10_000) { output.get() != null }
    }
    @Test fun disablingEditorCancelsLateExportWithoutPublishing() {
        val entered = AtomicBoolean(false); val cancelled = AtomicBoolean(false)
        showEditor { result ->
            entered.set(true)
            try { delay(60_000); output.set(result) } finally { cancelled.set(true) }
        }
        rule.onNodeWithTag("image-editor-export").performClick()
        rule.waitUntil(10_000) { entered.get() }
        rule.runOnUiThread { settings.value = MediaFeatureSettings(promptDisplayEnabled = true) }
        rule.waitUntil(10_000) { cancelled.get() }
        rule.onNodeWithTag("image-editor").assertDoesNotExist()
        assertNull(output.get())
    }
    @Test fun deviceMenuAndSettingsAreIndependentFromPromptDisplay() {
        settings.value = MediaFeatureSettings.Disabled
        val store = com.valoser.futacha.shared.state.createAppStateStore(context)
        val fs = com.valoser.futacha.shared.util.createFileSystem(context)
        rule.runOnUiThread { rule.activity.setContent {
            SideEffect { gate.update(settings.value) }
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalMediaFeatureGate provides gate,
                LocalMediaFeatureSettings provides settings.value,
                LocalMediaFeatureUpdater provides { transform -> settings.value = transform(settings.value) }) {
                MaterialTheme { DeviceImageEditingHost(fs, store) { Column {
                    DeviceImageEditorSettings()
                    DeviceImageEditorMenuItem {}
                } } }
            }
        } }
        rule.onNodeWithTag("device-image-editor-menu").assertDoesNotExist()
        rule.onNodeWithTag("image-editor-settings-toggle").performClick()
        rule.onNodeWithTag("device-image-editor-menu").assertExists()
        assertFalse(settings.value.promptDisplayEnabled)
        assertFalse(settings.value.videoEditorEnabled)
        rule.runOnUiThread { settings.value = settings.value.copy(promptDisplayEnabled = true) }
        rule.onNodeWithTag("image-editor-settings-toggle").performClick()
        rule.onNodeWithTag("device-image-editor-menu").assertDoesNotExist()
        assertTrue(settings.value.promptDisplayEnabled)
    }
    @Test fun androidExifRotationAndMirroringBeforeEditing(): Unit = runBlocking {
        gate.update(settings.value)
        val bitmap = Bitmap.createBitmap(80,48,Bitmap.Config.ARGB_8888)
        for (y in 0 until 48) for (x in 0 until 80) bitmap.setPixel(x,y,
            when { x<40 && y<24 -> -65536; x>=40 && y<24 -> -16711936; x>=40 -> -16776961; else -> -256 })
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) }.toByteArray()
        bitmap.recycle()
        val expected = listOf(listOf(0,1,2,3), listOf(1,0,3,2), listOf(2,3,0,1), listOf(3,2,1,0),
            listOf(0,3,2,1), listOf(3,0,1,2), listOf(2,1,0,3), listOf(1,2,3,0))
        for (orientation in 1..8) {
            val exif = byteArrayOf(69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,orientation.toByte(),0,0,0,0,0,0,0)
            val input=jpeg.copyOfRange(0,2)+byteArrayOf(-1,-31,0,(exif.size+2).toByte())+exif+jpeg.copyOfRange(2,jpeg.size)
            ImageEditSession.open(ImageEditInput(ImageData(input,"phone.jpg")),gate,loader,context).use { session ->
                val r=session.original
                assertEquals(if(orientation<=4)80 else 48,r.width)
                val actual=listOf(5 to 5,r.width-6 to 5,r.width-6 to r.height-6,5 to r.height-6).map { (x,y)->
                    val c=r.argb[y*r.width+x]; val red=c ushr 16 and 255;val green=c ushr 8 and 255
                    when { red>180&&green>180->3;red>180->0;green>180->1;else->2 }
                }
                assertEquals("EXIF $orientation",expected[orientation-1],actual)
                val saved = session.export(ImageEditDocument())
                val savedExif = ExifInterface(saved.bytes.inputStream())
                assertEquals(1, savedExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
                assertEquals(r.width, savedExif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0))
                assertEquals(r.height, savedExif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0))
                ImageEditSession.open(ImageEditInput(saved), gate, loader, context).use { reopened ->
                    assertEquals(r.width, reopened.original.width); assertEquals(r.height, reopened.original.height)
                }
            }
        }
    }

    @Test fun promptOffPreservesGenerationTagsAndEditedJpegPixels(): Unit = runBlocking {
        gate.update(settings.value)
        assertFalse(settings.value.promptDisplayEnabled)
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        for (name in listOf("a1111.jpg", "a1111.webp", "a1111-xmp.jpg")) {
            val source = assets.open("generation/$name").use { it.readBytes() }
            val before = source.copyOf()
            val originalTags = PreservedImageMetadata.scan(source)
            ImageEditSession.open(ImageEditInput(ImageData(source, name)), gate, loader, context).use { session ->
                val result = session.export(ImageEditDocument(regions = listOf(EditRegion(1,
                    bounds = EditBounds(0f, 0f, 1f, 1f), style = EditStyle.BLACK))))
                assertEquals(originalTags, PreservedImageMetadata.scan(result.bytes))
                val metadata = ImageGenerationMetadataReader().read(result.bytes.size.toLong()) { offset, count ->
                    result.bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
                }
                assertTrue(metadata.candidates.isNotEmpty())
                assertTrue(metadata.candidates.all { it.positive == "  cat 日本語 🐈" })
                val exif = ExifInterface(result.bytes.inputStream())
                if (name != "a1111-xmp.jpg") {
                    // ExifInterface.getAttribute treats UNICODE as ASCII. Read the actual
                    // tag bytes with the independent library and decode the declared encoding.
                    val comment = exif.getAttributeBytes(ExifInterface.TAG_USER_COMMENT)!!
                    assertArrayEquals("UNICODE\u0000".toByteArray(), comment.copyOfRange(0, 8))
                    val text = String(comment, 8, comment.size - 8, Charsets.UTF_16)
                    assertEquals(originalTags.fields.single { it.key == "UserComment" }.value, text)
                }
                assertFalse(exif.hasThumbnail())
                File(context.cacheDir, "image-metadata-validation-$name.jpg").writeBytes(result.bytes)
                val decoded = android.graphics.BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
                try {
                    assertEquals(8, decoded.width); assertEquals(8, decoded.height)
                    for (y in 0 until 8) for (x in 0 until 8) assertTrue((decoded.getPixel(x, y) and 0xffffff) < 0x101010)
                } finally { decoded.recycle() }
            }
            assertArrayEquals(before, source)
        }
    }
}
