package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.analysis.AnalysisFrame
import com.valoser.futacha.shared.media.video.model.VideoFrameIndex
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import kotlinx.coroutines.*

internal actual suspend fun decodeDeviceVideoFrames(path: String, info: VideoEditInfo, request: VideoAnalysisRequest, consume: suspend (AnalysisFrame) -> Unit) =
    decodeDeviceVideoFrameChunks(path, info, listOf(request)) { _, frame -> consume(frame) }

/**
 * One grabber serves every request. Reverse tracking asks for many short intervals in
 * descending order; reopening and decoding from the first frame for each of them made a
 * three-minute range take hundreds of thousands of decodes. A backward request seeks
 * instead, and the first frame after the seek is matched against the frame index; when
 * the seek cannot be verified the grabber is reopened (the old, always-correct path).
 */
internal actual suspend fun decodeDeviceVideoFrameChunks(
    path: String, info: VideoEditInfo, requests: List<VideoAnalysisRequest>, consume: suspend (Int, AnalysisFrame) -> Unit
) = withContext(Dispatchers.IO) {
    if (requests.isEmpty()) return@withContext
    val cursor = DesktopAnalysisGrabberCursor(path, info.frames)
    try {
        Java2DFrameConverter().use { converter ->
            requests.forEachIndexed { chunk, request ->
                ensureActive()
                cursor.positionAt(request.firstIndex)
                while (cursor.nextIndex < request.endIndex) {
                    ensureActive()
                    val index = cursor.nextIndex
                    val frame = cursor.grabNext()
                    if (index < request.firstIndex) continue
                    consume(chunk, frame.toAnalysisFrame(converter, info, request))
                }
            }
        }
    } finally {
        cursor.close()
    }
}

private class DesktopAnalysisGrabberCursor(private val path: String, private val frames: VideoFrameIndex) : AutoCloseable {
    private var grabber: FFmpegFrameGrabber = desktopGrabber(path)
    /** Index of the frame the next [grabNext] returns. */
    var nextIndex = 0
        private set
    /** A frame grabbed while verifying a seek; it is the frame at [nextIndex]. */
    private var pending: Frame? = null

    fun grabNext(): Frame {
        val frame = pending ?: grabber.grabImage() ?: error("解析中にフレームが欠落しました")
        pending = null
        check(nextIndex < frames.size && frame.timestamp == frames.timeAt(nextIndex)) { "動画のフレーム時刻が一致しません" }
        nextIndex++
        return frame
    }

    /** Makes the cursor read [index] next, or a frame before it that is then skipped. */
    fun positionAt(index: Int) {
        if (index >= nextIndex) return
        if (index > 0 && trySeek(index)) return
        reopen()
    }

    private fun trySeek(index: Int): Boolean = runCatching {
        pending = null
        grabber.setTimestamp(frames.timeAt(index))
        val frame = grabber.grabImage() ?: return@runCatching false
        val at = indexOfTime(frame.timestamp) ?: return@runCatching false
        if (at > index) return@runCatching false
        pending = frame
        nextIndex = at
        true
    }.getOrDefault(false)

    private fun indexOfTime(timeUs: Long): Int? {
        var low = 0
        var high = frames.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = frames.timeAt(mid)
            when {
                value < timeUs -> low = mid + 1
                value > timeUs -> high = mid - 1
                else -> return mid
            }
        }
        return null
    }

    private fun reopen() {
        pending = null
        runCatching { grabber.close() }
        grabber = desktopGrabber(path)
        nextIndex = 0
    }

    override fun close() {
        pending = null
        runCatching { grabber.close() }
    }
}

private fun Frame.toAnalysisFrame(converter: Java2DFrameConverter, info: VideoEditInfo, request: VideoAnalysisRequest): AnalysisFrame {
    val raster = desktopRaster(this, converter, info.rotationDegrees)
    val geometry = AnalysisRasterGeometry(raster.width, raster.height, maximumEdge = request.maximumEdge)
    val gray = ByteArray(geometry.width * geometry.height)
    val rgb = if (request.includeRgb) ByteArray(gray.size * 3) else ByteArray(0)
    for (y in 0 until geometry.height) {
        for (x in 0 until geometry.width) {
            val color = raster.argb[geometry.sourceY(x, y) * raster.width + geometry.sourceX(x, y)]
            val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
            val i = y * geometry.width + x
            gray[i] = ((77 * r + 150 * g + 29 * b + 128) ushr 8).toByte()
            if (request.includeRgb) { rgb[i * 3] = r.toByte(); rgb[i * 3 + 1] = g.toByte(); rgb[i * 3 + 2] = b.toByte() }
        }
    }
    return AnalysisFrame(timestamp, geometry.width, geometry.height, rgb, gray)
}
