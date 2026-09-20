package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.video.model.MosaicBounds

/** Exact preprocessing and output contracts for the pinned toshikari models. */
internal enum class DetectorModel(val artifact: AnalysisModel, val edge: Int, val classes: Int, val targets: List<Int>) {
    REAL(AnalysisModel.NUDE_NET, 320, 18, listOf(4, 14)),
    ANIME(AnalysisModel.ANIME_CENSOR, 640, 3, listOf(1, 2));

    val candidates get() = edge * edge / 64 + edge * edge / 256 + edge * edge / 1024
    fun label(index: Int): String = when {
        this == REAL && index in listOf(1, 12) -> "顔"
        (this == REAL && index == 14) || (this == ANIME && index == 1) -> "男性器候補"
        else -> "女性器候補"
    }
}

internal data class AnalysisFrame(val timeUs: Long, val width: Int, val height: Int, val rgb: ByteArray, val gray: ByteArray = ByteArray(0)) {
    init {
        require(timeUs >= 0 && width > 0 && height > 0 && width.toLong() * height <= 4_194_304)
        require(rgb.size == width * height * 3 || rgb.isEmpty())
        require(gray.size == width * height || gray.isEmpty())
        require(rgb.isNotEmpty() || gray.isNotEmpty())
    }
}

internal data class Detection(val bounds: MosaicBounds, val label: String, val score: Float)
internal data class DetectionWindow(val left: Int, val top: Int, val width: Int, val height: Int)

/** Model-specific geometry. Anime uses the upstream fixed-square resize option; NudeNet pads bottom/right. */
internal class DetectionTransform(val model: DetectorModel, val window: DetectionWindow, val frameWidth: Int, val frameHeight: Int) {
    init {
        require(frameWidth > 0 && frameHeight > 0 && window.left >= 0 && window.top >= 0)
        require(window.width > 0 && window.height > 0 && window.width <= frameWidth - window.left && window.height <= frameHeight - window.top)
    }
    val scaleX = model.edge.toFloat() / if (model == DetectorModel.REAL) maxOf(window.width, window.height) else window.width
    val scaleY = model.edge.toFloat() / if (model == DetectorModel.REAL) maxOf(window.width, window.height) else window.height
    fun bounds(cx: Float, cy: Float, w: Float, h: Float): MosaicBounds? {
        if (!cx.isFinite() || !cy.isFinite() || !w.isFinite() || !h.isFinite() || w <= 0 || h <= 0) return null
        val left = ((cx - w / 2) / scaleX).coerceIn(0f, window.width.toFloat()) + window.left
        val right = ((cx + w / 2) / scaleX).coerceIn(0f, window.width.toFloat()) + window.left
        val top = ((cy - h / 2) / scaleY).coerceIn(0f, window.height.toFloat()) + window.top
        val bottom = ((cy + h / 2) / scaleY).coerceIn(0f, window.height.toFloat()) + window.top
        if (right <= left || bottom <= top) return null
        return MosaicBounds((left + right) / (2 * frameWidth), (top + bottom) / (2 * frameHeight), (right - left) / frameWidth, (bottom - top) / frameHeight).constrained()
    }
}

internal fun intersectionOverUnion(a: MosaicBounds, b: MosaicBounds): Float {
    val width = (minOf(a.centerX + a.width / 2, b.centerX + b.width / 2) - maxOf(a.centerX - a.width / 2, b.centerX - b.width / 2)).coerceAtLeast(0f)
    val height = (minOf(a.centerY + a.height / 2, b.centerY + b.height / 2) - maxOf(a.centerY - a.height / 2, b.centerY - b.height / 2)).coerceAtLeast(0f)
    val intersection = width * height
    return intersection / (a.width * a.height + b.width * b.height - intersection).coerceAtLeast(0.000001f)
}

internal fun suppressDetections(input: List<Detection>, threshold: Float = 0.5f, check: () -> Unit = {}): List<Detection> {
    val result = ArrayList<Detection>()
    for (candidate in input.sortedByDescending { it.score }) {
        check()
        if (result.none { it.label == candidate.label && intersectionOverUnion(it.bounds, candidate.bounds) > threshold }) result += candidate
    }
    return result
}

internal fun decodeDetections(output: FloatArray, model: DetectorModel, transform: DetectionTransform, threshold: Float, faces: Boolean, check: () -> Unit = {}): List<Detection> {
    require(threshold.isFinite() && threshold in .05f.. .5f)
    val candidates = (model.edge / 8).let { it * it } + (model.edge / 16).let { it * it } + (model.edge / 32).let { it * it }
    require(output.size == (4 + model.classes) * candidates)
    val classes = if (faces && model == DetectorModel.REAL) model.targets + listOf(1, 12) else model.targets
    val found = ArrayList<Detection>()
    for (i in 0 until candidates) {
        if (i % 256 == 0) check()
        for (c in classes) {
            val score = output.get((4 + c) * candidates + i)
            check(score.isFinite() && score in 0f..1f) { "モデルの検出スコアが不正です。" }
            if (score < threshold) continue
            val box = transform.bounds(output.get(i), output.get(candidates + i), output.get(2 * candidates + i), output.get(3 * candidates + i)) ?: continue
            found += Detection(box, model.label(c), score)
        }
    }
    return suppressDetections(found, check = check)
}

internal fun prepareDetectionInput(frame: AnalysisFrame, transform: DetectionTransform, input: FloatArray, check: () -> Unit) {
    val model = transform.model
    val plane = model.edge * model.edge
    require(input.size == plane * 3 && frame.rgb.size == frame.width * frame.height * 3)
    require(frame.width == transform.frameWidth && frame.height == transform.frameHeight)
    val win = transform.window
    for (y in 0 until model.edge) {
        if (y % 32 == 0) check()
        val fy = (y + 0.5f) / transform.scaleY - 0.5f
        for (x in 0 until model.edge) {
            val fx = (x + 0.5f) / transform.scaleX - 0.5f
            val i = y * model.edge + x
            if (fx >= win.width || fy >= win.height) {
                input[i] = 0f; input[plane + i] = 0f; input[2 * plane + i] = 0f
            } else {
                val sx = fx.coerceIn(0f, win.width - 1f); val sy = fy.coerceIn(0f, win.height - 1f)
                val x0 = sx.toInt(); val y0 = sy.toInt()
                val x1 = minOf(x0 + 1, win.width - 1); val y1 = minOf(y0 + 1, win.height - 1)
                val dx = sx - x0; val dy = sy - y0
                val a = ((win.top + y0) * frame.width + win.left + x0) * 3
                val b = ((win.top + y0) * frame.width + win.left + x1) * 3
                val c = ((win.top + y1) * frame.width + win.left + x0) * 3
                val d = ((win.top + y1) * frame.width + win.left + x1) * 3
                for (channel in 0..2) {
                    val top = (frame.rgb[a + channel].toInt() and 255) * (1 - dx) + (frame.rgb[b + channel].toInt() and 255) * dx
                    val bottom = (frame.rgb[c + channel].toInt() and 255) * (1 - dx) + (frame.rgb[d + channel].toInt() and 255) * dx
                    input[channel * plane + i] = (top * (1 - dy) + bottom * dy) / 255f
                }
            }
        }
    }
}
