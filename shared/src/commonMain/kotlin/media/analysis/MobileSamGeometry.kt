package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.video.model.MosaicBounds
import com.valoser.futacha.shared.media.video.model.MosaicMask
import kotlin.math.roundToInt

internal data class ContourResult(val mask: MosaicMask?, val uncertain: Boolean)

internal fun MosaicBounds.validateContourBounds() {
    require(listOf(centerX, centerY, width, height).all(Float::isFinite))
    require(width > 0f && width <= 1f && height > 0f && height <= 1f)
    require(centerX - width / 2 >= -.000001f && centerX + width / 2 <= 1.000001f &&
        centerY - height / 2 >= -.000001f && centerY + height / 2 <= 1.000001f)
}

internal fun mobileSamDimensions(frame: AnalysisFrame): Pair<Int, Int> {
    val scale = 1024f / maxOf(frame.width, frame.height)
    return (frame.width * scale).roundToInt().coerceAtLeast(1) to
        (frame.height * scale).roundToInt().coerceAtLeast(1)
}

/** Exported encoder performs normalization and bottom/right padding. Input is unpadded HWC RGB. */
internal fun prepareMobileSamInput(frame: AnalysisFrame, width: Int, height: Int,
    tensor: InferenceTensor, check: () -> Unit) {
    require(width in 1..1024 && height in 1..1024 && maxOf(width, height) == 1024)
    require(tensor.shape == listOf(height.toLong(), width.toLong(), 3L))
    require(frame.rgb.size == frame.width * frame.height * 3)
    val row = FloatArray(width * 3)
    val left = IntArray(width); val right = IntArray(width); val fraction = FloatArray(width)
    for (x in 0 until width) {
        val sx = ((x + .5f) * frame.width / width - .5f).coerceIn(0f, frame.width - 1f)
        left[x] = sx.toInt() * 3; right[x] = minOf(sx.toInt() + 1, frame.width - 1) * 3
        fraction[x] = sx - sx.toInt()
    }
    for (y in 0 until height) {
        if (y % 16 == 0) check()
        val sy = ((y + .5f) * frame.height / height - .5f).coerceIn(0f, frame.height - 1f)
        val top = sy.toInt() * frame.width * 3
        val bottom = minOf(sy.toInt() + 1, frame.height - 1) * frame.width * 3
        val fy = sy - sy.toInt()
        for (x in 0 until width) for (c in 0..2) {
            val fx = fraction[x]
            fun sample(offset: Int) = (frame.rgb[offset].toInt() and 255).toFloat()
            row[x * 3 + c] = (sample(top + left[x] + c) * (1 - fx) + sample(top + right[x] + c) * fx) * (1 - fy) +
                (sample(bottom + left[x] + c) * (1 - fx) + sample(bottom + right[x] + c) * fx) * fy
        }
        tensor.writeSlice(row, y * row.size)
    }
    check()
}

/** A whole-body/background segment, empty mask or malformed output keeps the original rectangle. */
internal fun selectMobileSamMask(bounds: MosaicBounds, width: Int, height: Int,
    scores: FloatArray, masks: FloatArray, check: () -> Unit = {}): ContourResult {
    require(width in 1..1024 && height in 1..1024)
    bounds.validateContourBounds()
    require(scores.size == 4 && masks.size == 4 * 256 * 256)
    val left = bounds.centerX - bounds.width / 2; val top = bounds.centerY - bounds.height / 2
    var best: MosaicMask? = null; var bestScore = -1f
    for (candidate in 0..3) {
        check()
        val score = scores[candidate]
        if (!score.isFinite() || score !in .5f..1f || score <= bestScore) continue
        var total = 0; var inBox = 0; var finite = true
        for (y in 0 until (height + 3) / 4) {
            if (y % 16 == 0) check()
            for (x in 0 until (width + 3) / 4) {
                val logit = masks[candidate * 256 * 256 + y * 256 + x]
                if (!logit.isFinite()) finite = false
                if (logit > 0f) {
                    total++
                    val u = (x + .5f) * 4 / width; val v = (y + .5f) * 4 / height
                    if (u in left..left + bounds.width && v in top..top + bounds.height) inBox++
                }
            }
        }
        if (!finite || total == 0 || inBox.toFloat() / total < .5f) continue
        val mask = MosaicMask.from { x, y ->
            val sx = ((left + (x + .5f) / MosaicMask.EDGE * bounds.width) * width / 4).toInt().coerceIn(0, 255)
            val sy = ((top + (y + .5f) / MosaicMask.EDGE * bounds.height) * height / 4).toInt().coerceIn(0, 255)
            masks[candidate * 256 * 256 + sy * 256 + sx] > 0f
        }
        if (mask.coverage !in .02f.. .95f) continue
        best = mask; bestScore = score
    }
    check()
    return ContourResult(best, best == null || bestScore < .85f)
}
