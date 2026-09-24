package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.edit.EditRaster
import com.valoser.futacha.shared.media.edit.ImageEditDocument
import com.valoser.futacha.shared.media.edit.asMosaicBounds
import com.valoser.futacha.shared.media.edit.withContours
import com.valoser.futacha.shared.media.edit.withDetections
import kotlinx.coroutines.*
import kotlin.math.roundToInt

internal data class DetectionSettings(
    val models: Set<DetectorModel> = setOf(DetectorModel.REAL),
    val threshold: Float = .15f,
    val margin: Float = .25f,
    val faces: Boolean = false,
    val tiles: Boolean = false,
    val contours: Boolean = false
) {
    fun validated() = apply {
        require(models.isNotEmpty()) { "検出モデルを選択してください" }
        require(threshold.isFinite() && threshold in .05f.. .5f && margin.isFinite() && margin in 0f.. .75f)
    }
}

internal object ImageSensitiveAnalyser {
    /** Detection and optional contours are one transaction; the caller commits only this result. */
    suspend fun analyse(original: EditRaster, settings: DetectionSettings, snapshot: ImageEditDocument,
        store: ModelStore, gate: MediaFeatureGate, permit: MediaFeaturePermit,
        progress: (String, Float?) -> Unit): ImageEditDocument = withContext(Dispatchers.Default) {
        settings.validated()
        require(permit.feature == MediaFeature.IMAGE_EDITOR)
        if (!gate.isCurrent(permit)) throw CancellationException("画像編集は無効になりました")
        if (settings.contours) {
            for (model in listOf(AnalysisModel.MOBILE_SAM_ENCODER, AnalysisModel.MOBILE_SAM_DECODER)) {
                // Early, cheap check; MobileSamSegmenter.open verifies the content before running.
                check(store.present(model, permit)) { "モデル画面で輪郭用の2モデルを導入してください" }
            }
        }
        val detections = detect(original, settings, store, gate, permit, progress)
        val detected = snapshot.withDetections(detections)
        val added = detected.regions.filter { region -> snapshot.regions.none { it.id == region.id } }
        val result = if (settings.contours && added.isNotEmpty()) {
            val frame = imageAnalysisFrame(original, 1024)
            MobileSamSegmenter.open(store, gate, permit).use { segmenter ->
                detected.withContours(added.map { it.id }, segmenter.segment(frame, added.map { it.bounds.asMosaicBounds() }, progress))
            }
        } else detected
        currentCoroutineContext().ensureActive()
        if (!gate.isCurrent(permit)) throw CancellationException("画像編集は無効になりました")
        result
    }

    suspend fun detect(original: EditRaster, settings: DetectionSettings, store: ModelStore,
        gate: MediaFeatureGate, permit: MediaFeaturePermit, progress: (String, Float?) -> Unit): List<Detection> = withContext(Dispatchers.Default) {
        settings.validated()
        require(permit.feature == MediaFeature.IMAGE_EDITOR)
        fun checkPermit() { if (!gate.isCurrent(permit)) throw CancellationException("画像編集は無効になりました") }
        checkPermit()
        val frame = imageAnalysisFrame(original, if (settings.tiles) 960 else 640)
        val detections = mutableListOf<Detection>()
        for ((index, model) in settings.models.withIndex()) {
            currentCoroutineContext().ensureActive(); checkPermit()
            val title = AnalysisModels.all.first { it.id == model.artifact }.title
            progress("${title}で解析しています", index.toFloat() / settings.models.size)
            SensitiveDetector.open(model, store, gate, permit).use { detector ->
                detections += detector.detect(frame, settings.threshold, settings.faces, settings.tiles)
            }
        }
        val context = currentCoroutineContext()
        val result = suppressDetections(detections, check = { context.ensureActive(); checkPermit() })
        check(result.size <= 16) { "候補が16か所を超えました。結果は反映していません。しきい値やモデルを変更してください" }
        result.map { it.copy(bounds = it.bounds.copy(width = it.bounds.width * (1 + settings.margin * 2),
            height = it.bounds.height * (1 + settings.margin * 2)).constrained()) }
            .also { context.ensureActive(); checkPermit() }
    }
}

/** Downsample without another full-size bitmap. Composite transparency on gray 127 like toshikari.
 * Bilinear interpolation of composited channels is equivalent to premultiplied-alpha resampling. */
internal suspend fun imageAnalysisFrame(original: EditRaster, maximumEdge: Int): AnalysisFrame {
    require(maximumEdge in 1..1024)
    val scale = minOf(1f, maximumEdge.toFloat() / maxOf(original.width, original.height))
    val width = (original.width * scale).roundToInt().coerceAtLeast(1)
    val height = (original.height * scale).roundToInt().coerceAtLeast(1)
    val rgb = ByteArray(width * height * 3)
    val context = currentCoroutineContext()
    fun channel(x: Int, y: Int, shift: Int): Float {
        val pixel = original.argb[y * original.width + x]
        val alpha = pixel ushr 24
        return (((pixel ushr shift and 255) * alpha + 127 * (255 - alpha)) / 255f)
    }
    for (y in 0 until height) {
        if (y % 16 == 0) context.ensureActive()
        val sy = ((y + .5f) * original.height / height - .5f).coerceIn(0f, original.height - 1f)
        val y0 = sy.toInt(); val y1 = minOf(y0 + 1, original.height - 1); val dy = sy - y0
        for (x in 0 until width) {
            val sx = ((x + .5f) * original.width / width - .5f).coerceIn(0f, original.width - 1f)
            val x0 = sx.toInt(); val x1 = minOf(x0 + 1, original.width - 1); val dx = sx - x0
            for (c in 0..2) {
                val shift = 16 - 8 * c
                val top = channel(x0, y0, shift) * (1 - dx) + channel(x1, y0, shift) * dx
                val bottom = channel(x0, y1, shift) * (1 - dx) + channel(x1, y1, shift) * dx
                rgb[(y * width + x) * 3 + c] = (top * (1 - dy) + bottom * dy).roundToInt().coerceIn(0, 255).toByte()
            }
        }
    }
    return AnalysisFrame(0, width, height, rgb)
}
