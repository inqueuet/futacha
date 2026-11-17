package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.analysis.AnalysisFrame

/** Decoded planes can have padding, interleaved chroma and a nonzero buffer position. */
internal class AnalysisBytePlane(
    val size: Int, val rowStride: Int, val pixelStride: Int, val base: Int = 0,
    private val read: (Int) -> Byte
) {
    init { require(size >= 0 && base in 0..size && rowStride > 0 && pixelStride > 0) }
    fun requireRectangle(lastX: Int, lastY: Int, channels: Int = 1) {
        require(lastX >= 0 && lastY >= 0 && channels in 1..pixelStride)
        val rowEnd = lastX.toLong() * pixelStride + channels
        require(rowEnd <= rowStride && base.toLong() + lastY.toLong() * rowStride + rowEnd <= size) {
            "動画の画像バッファが不正です"
        }
    }
    fun at(x: Int, y: Int, channel: Int = 0): Int = read(base + y * rowStride + x * pixelStride + channel).toInt() and 255
}

/** Maps reduced upright coordinates back to the cropped, unrotated decoder raster. */
internal class AnalysisRasterGeometry(
    sourceWidth: Int, sourceHeight: Int,
    val left: Int = 0, val top: Int = 0,
    val cropWidth: Int = sourceWidth, val cropHeight: Int = sourceHeight,
    val rotation: Int = 0, maximumEdge: Int = 640,
    private val mirrorX: Boolean = false
) {
    init {
        require(sourceWidth in 1..32768 && sourceHeight in 1..32768)
        require(left >= 0 && top >= 0 && cropWidth > 0 && cropHeight > 0)
        require(cropWidth <= sourceWidth - left && cropHeight <= sourceHeight - top)
        require(rotation in setOf(0, 90, 180, 270) && maximumEdge in 1..1024)
    }
    private val uprightWidth = if (rotation % 180 == 0) cropWidth else cropHeight
    private val uprightHeight = if (rotation % 180 == 0) cropHeight else cropWidth
    private val scale = minOf(1.0, maximumEdge.toDouble() / maxOf(uprightWidth, uprightHeight))
    val width = (uprightWidth * scale).toInt().coerceAtLeast(1)
    val height = (uprightHeight * scale).toInt().coerceAtLeast(1)
    private val xs = IntArray(width) { ((it + .5) * uprightWidth / width).toInt().coerceAtMost(uprightWidth - 1) }
    private val ys = IntArray(height) { ((it + .5) * uprightHeight / height).toInt().coerceAtMost(uprightHeight - 1) }
    fun sourceX(x: Int, y: Int): Int {
        val raw = when (rotation) { 90 -> ys[y]; 180 -> cropWidth - 1 - xs[x]; 270 -> cropWidth - 1 - ys[y]; else -> xs[x] }
        return left + if (mirrorX) cropWidth - 1 - raw else raw
    }
    fun sourceY(x: Int, y: Int): Int = top + when (rotation) { 90 -> cropHeight - 1 - xs[x]; 180 -> cropHeight - 1 - ys[y]; 270 -> xs[x]; else -> ys[y] }
}

private inline fun sampleAnalysisRaster(
    timeUs: Long, geometry: AnalysisRasterGeometry, includeRgb: Boolean, check: () -> Unit,
    pixel: (Int, Int) -> Int
): AnalysisFrame {
    val rgb = if (includeRgb) ByteArray(geometry.width * geometry.height * 3) else ByteArray(0)
    val gray = ByteArray(geometry.width * geometry.height)
    for (y in 0 until geometry.height) {
        if (y % 16 == 0) check()
        for (x in 0 until geometry.width) {
            val value = pixel(geometry.sourceX(x, y), geometry.sourceY(x, y))
            val i = y * geometry.width + x
            gray[i] = (value ushr 24).toByte()
            if (includeRgb) {
                rgb[i * 3] = (value ushr 16).toByte()
                rgb[i * 3 + 1] = (value ushr 8).toByte()
                rgb[i * 3 + 2] = value.toByte()
            }
        }
    }
    check()
    return AnalysisFrame(timeUs, geometry.width, geometry.height, rgb, gray)
}

internal fun sampleBgraAnalysisFrame(
    timeUs: Long, geometry: AnalysisRasterGeometry, plane: AnalysisBytePlane,
    includeRgb: Boolean, check: () -> Unit = {}
): AnalysisFrame {
    require(plane.pixelStride == 4)
    plane.requireRectangle(geometry.left + geometry.cropWidth - 1, geometry.top + geometry.cropHeight - 1, 4)
    return sampleAnalysisRaster(timeUs, geometry, includeRgb, check) { x, y ->
        val r = plane.at(x, y, 2); val g = plane.at(x, y, 1); val b = plane.at(x, y)
        val gray = (77 * r + 150 * g + 29 * b + 128) ushr 8
        (gray shl 24) or (r shl 16) or (g shl 8) or b
    }
}

internal fun sampleYuvAnalysisFrame(
    timeUs: Long, geometry: AnalysisRasterGeometry, yPlane: AnalysisBytePlane,
    uPlane: AnalysisBytePlane, vPlane: AnalysisBytePlane,
    fullRange: Boolean, bt709: Boolean, includeRgb: Boolean, check: () -> Unit = {}
): AnalysisFrame {
    val maxX = geometry.left + geometry.cropWidth - 1; val maxY = geometry.top + geometry.cropHeight - 1
    yPlane.requireRectangle(maxX, maxY)
    if (includeRgb) { uPlane.requireRectangle(maxX / 2, maxY / 2); vPlane.requireRectangle(maxX / 2, maxY / 2) }
    val chroma = if (fullRange) 1f else 255f / 224f
    val rv = (if (bt709) 1.5748f else 1.402f) * chroma
    val gu = (if (bt709) .187324f else .344136f) * chroma
    val gv = (if (bt709) .468124f else .714136f) * chroma
    val bu = (if (bt709) 1.8556f else 1.772f) * chroma
    return sampleAnalysisRaster(timeUs, geometry, includeRgb, check) { x, y ->
        val yy = yPlane.at(x, y)
        val luma = if (fullRange) yy.toFloat() else (yy - 16) * 1.164384f
        val gray = luma.toInt().coerceIn(0, 255) shl 24
        if (!includeRgb) gray else {
            val u = uPlane.at(x / 2, y / 2) - 128; val v = vPlane.at(x / 2, y / 2) - 128
            val r = (luma + rv * v).toInt().coerceIn(0, 255)
            val g = (luma - gu * u - gv * v).toInt().coerceIn(0, 255)
            val b = (luma + bu * u).toInt().coerceIn(0, 255)
            gray or (r shl 16) or (g shl 8) or b
        }
    }
}
