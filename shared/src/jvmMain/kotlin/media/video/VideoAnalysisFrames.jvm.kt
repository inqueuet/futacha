package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.analysis.AnalysisFrame
import org.bytedeco.javacv.Java2DFrameConverter
import kotlinx.coroutines.*

internal actual suspend fun decodeDeviceVideoFrames(path: String, info: VideoEditInfo, request: VideoAnalysisRequest, consume: suspend (AnalysisFrame) -> Unit) = withContext(Dispatchers.IO) {
    desktopGrabber(path).use { grabber -> Java2DFrameConverter().use { converter ->
        var index = 0
        while (index < request.endIndex) {
            ensureActive()
            val frame = grabber.grabImage() ?: error("解析中にフレームが欠落しました")
            check(frame.timestamp == info.frames.timeAt(index)) { "動画のフレーム時刻が一致しません" }
            if (index++ < request.firstIndex) continue
            val raster = desktopRaster(frame, converter, info.rotationDegrees)
            val geometry = AnalysisRasterGeometry(raster.width, raster.height, maximumEdge = request.maximumEdge)
            val gray = ByteArray(geometry.width * geometry.height)
            val rgb = if (request.includeRgb) ByteArray(gray.size * 3) else ByteArray(0)
            for (y in 0 until geometry.height) {
                ensureActive()
                for (x in 0 until geometry.width) {
                    val color = raster.argb[geometry.sourceY(x, y) * raster.width + geometry.sourceX(x, y)]
                    val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
                    val i = y * geometry.width + x
                    gray[i] = ((77 * r + 150 * g + 29 * b + 128) ushr 8).toByte()
                    if (request.includeRgb) { rgb[i * 3] = r.toByte(); rgb[i * 3 + 1] = g.toByte(); rgb[i * 3 + 2] = b.toByte() }
                }
            }
            consume(AnalysisFrame(frame.timestamp, geometry.width, geometry.height, rgb, gray))
        }
    } }
}
