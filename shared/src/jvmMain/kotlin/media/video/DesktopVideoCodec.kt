package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.edit.EditRaster
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.Frame
import java.awt.image.BufferedImage
import kotlinx.coroutines.*
import org.bytedeco.ffmpeg.global.avutil.*

internal fun desktopGrabber(path: String): FFmpegFrameGrabber = FFmpegFrameGrabber(path).apply {
    av_log_set_level(AV_LOG_ERROR)
    setOption("threads", "2")
    try {
        start()
        require(imageWidth in 1..2048 && imageHeight in 1..2048) { "動画編集は長辺2048ピクセル以下に対応しています" }
    } catch (failure: Throwable) {
        runCatching { close() }
        throw failure
    }
}

internal fun desktopRotation(grabber: FFmpegFrameGrabber): Int {
    val raw = -grabber.displayRotation
    val normalized = ((kotlin.math.round(raw).toInt() % 360) + 360) % 360
    require(normalized in setOf(0, 90, 180, 270)) { "動画の回転情報に対応していません" }
    return normalized
}

internal fun desktopRaster(frame: Frame, converter: Java2DFrameConverter, rotation: Int): EditRaster {
    val image = converter.convert(frame)
    val w = image.width; val h = image.height
    val source = image.getRGB(0, 0, w, h, null, 0, w)
    if (rotation == 0) return EditRaster(w, h, source)
    val width = if (rotation % 180 == 0) w else h
    val height = if (rotation % 180 == 0) h else w
    val pixels = IntArray(source.size)
    for (y in 0 until h) for (x in 0 until w) {
        val dx = when (rotation) { 90 -> h - 1 - y; 180 -> w - 1 - x; else -> y }
        val dy = when (rotation) { 90 -> x; 180 -> h - 1 - y; else -> w - 1 - x }
        pixels[dy * width + dx] = source[y * w + x]
    }
    return EditRaster(width, height, pixels)
}

internal fun EditRaster.bufferedImage(): BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR).also {
    it.setRGB(0, 0, width, height, argb, 0, width)
}

internal suspend fun inspectDesktopVideo(path: String): VideoEditInfo = withContext(Dispatchers.IO) {
    desktopGrabber(path).use { grabber ->
        val rotation = desktopRotation(grabber)
        val codec = grabber.formatContext.streams(grabber.videoStream).codecpar()
        val hdr = codec.color_trc() in setOf(AVCOL_TRC_SMPTE2084, AVCOL_TRC_ARIB_STD_B67)
        val timestamps = mutableListOf<Long>()
        while (true) {
            currentCoroutineContext().ensureActive()
            val frame = grabber.grabImage() ?: break
            require(timestamps.size < com.valoser.futacha.shared.media.video.model.VideoFrameIndex.MAX_FRAMES) { "動画が長すぎます" }
            timestamps += frame.timestamp
        }
        val stream = grabber.formatContext.streams(grabber.videoStream)
        val duration = if (stream.duration() > 0 && stream.duration() != AV_NOPTS_VALUE)
            av_rescale_q(stream.duration(), stream.time_base(), av_get_time_base_q()) else grabber.lengthInTime
        VideoEditInfo(if (rotation % 180 == 0) grabber.imageWidth else grabber.imageHeight,
            if (rotation % 180 == 0) grabber.imageHeight else grabber.imageWidth,
            buildVideoFrameIndex(timestamps, duration + (timestamps.firstOrNull() ?: 0)),
            grabber.audioChannels > 0, hdr, rotation)
    }
}
