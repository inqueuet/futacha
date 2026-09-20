package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.edit.EditRaster
import com.valoser.futacha.shared.media.video.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/** Same bottom-origin sampling grid and max-overlap rule as the native video shaders. */
internal suspend fun renderVideoPreview(input: EditRaster, document: MosaicDocument, timeUs: Long): EditRaster {
    val regions = document.regions.filter { it.activeAt(timeUs) }
    val bounds = regions.map { it.boundsAt(timeUs) }
    val masks = regions.map { it.maskAt(timeUs)?.dilated(it.maskMargin) }
    val w = input.width; val h = input.height
    val result = input.argb.copyOf()
    fun sample(x: Float, y: Float): Int {
        val sx = (x - .5f).coerceIn(0f, w - 1f); val sy = (y - .5f).coerceIn(0f, h - 1f)
        val x0 = floor(sx).toInt(); val y0 = floor(sy).toInt()
        val fx = sx - x0; val fy = sy - y0
        val colors = intArrayOf(input.argb[y0 * w + x0], input.argb[y0 * w + min(x0 + 1, w - 1)],
            input.argb[min(y0 + 1, h - 1) * w + x0], input.argb[min(y0 + 1, h - 1) * w + min(x0 + 1, w - 1)])
        fun c(shift: Int) = (((colors[0] ushr shift and 255) * (1 - fx) + (colors[1] ushr shift and 255) * fx) * (1 - fy) +
            ((colors[2] ushr shift and 255) * (1 - fx) + (colors[3] ushr shift and 255) * fx) * fy).roundToInt()
        return (255 shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }
    for (y in 0 until h) {
        if (y % 32 == 0) currentCoroutineContext().ensureActive()
        for (x in 0 until w) {
            val px = (x + .5f) / w; val py = (y + .5f) / h
            var block = 0f; var darkness = 0f
            for (i in regions.indices) {
                val r = regions[i]; val b = bounds[i]; val mask = masks[i]
                val contains = if (mask == null) b.contains(px, py, r.shape) else
                    mask.contains((px - b.centerX + b.width / 2) / b.width, (py - b.centerY + b.height / 2) / b.height)
                if (contains) { block = max(block, r.blockFraction); darkness = max(darkness, if (r.style == MosaicStyle.BLACK) 1f else r.darkness) }
            }
            if (block == 0f) continue
            val cell = min(w, h) * block
            val color = sample((floor((x + .5f) / cell) + .5f) * cell, h - (floor((h - y - .5f) / cell) + .5f) * cell)
            fun dark(shift: Int) = ((color ushr shift and 255) * (1 - darkness)).roundToInt()
            result[y * w + x] = (255 shl 24) or (dark(16) shl 16) or (dark(8) shl 8) or dark(0)
        }
    }
    return EditRaster(w, h, result)
}
