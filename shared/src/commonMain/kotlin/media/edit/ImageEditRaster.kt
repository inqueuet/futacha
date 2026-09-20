package com.valoser.futacha.shared.media.edit

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

internal const val IMAGE_EDIT_MAX_EDGE = 2048
internal data class EditRaster(val width: Int, val height: Int, val argb: IntArray) {
    init { require(width in 1..IMAGE_EDIT_MAX_EDGE && height in 1..IMAGE_EDIT_MAX_EDGE && argb.size == width * height) }
}
internal expect fun configureImageEditDecode(builder: ImageRequest.Builder): ImageRequest.Builder
internal expect fun imageEditPixels(image: Image): EditRaster
internal expect fun imageEditBitmap(raster: EditRaster): ImageBitmap
internal expect fun encodeImageEditJpeg(raster: EditRaster): ByteArray

/** Used for both preview and export. The input pixels are immutable. */
internal suspend fun renderImageEdit(original: EditRaster, document: ImageEditDocument): EditRaster {
    document.validated()
    val context = currentCoroutineContext()
    val w = original.width; val h = original.height
    val output = original.argb.copyOf()
    val mask = ByteArray(output.size)
    for (stroke in document.strokes) {
        val radius = max(0.5f, stroke.diameter * min(w, h) / 2)
        for (i in stroke.points.indices) {
            context.ensureActive()
            val a = stroke.points[max(0, i - 1)]; val b = stroke.points[i]
            val ax = a.x * w; val ay = a.y * h; val bx = b.x * w; val by = b.y * h
            val dx = bx - ax; val dy = by - ay; val length2 = dx * dx + dy * dy
            val x0 = floor(min(ax, bx) - radius).toInt().coerceIn(0, w)
            val x1 = ceil(max(ax, bx) + radius).toInt().coerceIn(0, w)
            val y0 = floor(min(ay, by) - radius).toInt().coerceIn(0, h)
            val y1 = ceil(max(ay, by) + radius).toInt().coerceIn(0, h)
            for (y in y0 until y1) {
                if (y % 32 == 0) context.ensureActive()
                for (x in x0 until x1) {
                    val t = if (length2 == 0f) 0f else (((x + .5f - ax) * dx + (y + .5f - ay) * dy) / length2).coerceIn(0f, 1f)
                    val px = x + .5f - ax - t * dx; val py = y + .5f - ay - t * dy
                    if (px * px + py * py <= radius * radius) mask[y * w + x] = if (stroke.erase) 0 else 1
                }
            }
        }
    }
    fun sample(x: Int, y: Int, fraction: Float): Int {
        val block = (min(w, h) * fraction).roundToInt().coerceAtLeast(1)
        val sx = (x / block * block + block / 2).coerceAtMost(w - 1)
        val sy = (y / block * block + block / 2).coerceAtMost(h - 1)
        return opaqueOnWhite(original.argb[sy * w + sx])
    }
    for (y in 0 until h) {
        if (y % 32 == 0) context.ensureActive()
        for (x in 0 until w) {
            val i = y * w + x
            output[i] = opaqueOnWhite(output[i])
            if (mask[i].toInt() != 0) output[i] = blend(output[i], sample(x, y, document.brushBlockFraction), document.brushOpacity)
        }
    }
    fun visit(region: EditRegion, edit: (Int, Int, Int) -> Int) {
        val b = region.bounds
        val contour = region.contour?.dilated(region.contourMargin)
        // Cover edge pixels conservatively; no gaps when two adjacent regions meet.
        val x0 = floor(b.left * w).toInt().coerceIn(0, w)
        val x1 = ceil((b.left + b.width) * w).toInt().coerceIn(0, w)
        val y0 = floor(b.top * h).toInt().coerceIn(0, h)
        val y1 = ceil((b.top + b.height) * h).toInt().coerceIn(0, h)
        for (y in y0 until y1) {
            if (y % 32 == 0) context.ensureActive()
            for (x in x0 until x1) {
                if (contour == null || contour.intersects((x.toFloat() / w - b.left) / b.width,
                    (y.toFloat() / h - b.top) / b.height, ((x + 1f) / w - b.left) / b.width,
                    ((y + 1f) / h - b.top) / b.height)) output[y * w + x] = edit(x, y, output[y * w + x])
            }
        }
    }
    for (region in document.regions.sortedBy { it.blockFraction }) {
        if (region.style == EditStyle.MOSAIC) visit(region) { x, y, _ -> sample(x, y, region.blockFraction) }
    }
    // Apply covers last: a lighter overlapping mosaic must never uncover black paint.
    for (region in document.regions) {
        val darkness = if (region.style == EditStyle.BLACK) 1f else region.darkness
        if (darkness > 0f) visit(region) { _, _, color -> blend(color, 0xff000000.toInt(), darkness) }
    }
    context.ensureActive()
    return EditRaster(w, h, output)
}

private fun opaqueOnWhite(argb: Int): Int = blend(0xffffffff.toInt(), argb, ((argb ushr 24) and 255) / 255f)
private fun blend(from: Int, to: Int, alpha: Float): Int {
    fun channel(shift: Int): Int = ((((from ushr shift) and 255) * (1 - alpha) + ((to ushr shift) and 255) * alpha).roundToInt()).coerceIn(0, 255)
    return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
