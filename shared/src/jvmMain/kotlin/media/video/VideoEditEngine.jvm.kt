package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.video.model.MosaicDocument
import org.bytedeco.javacv.*
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import java.io.File
import kotlinx.coroutines.*

internal actual suspend fun decodeDeviceVideoPreviewFrame(
    path: String, info: VideoEditInfo, timeUs: Long, adopt: (VideoPreviewFrame) -> Unit
): Unit = withContext(Dispatchers.IO) {
    desktopGrabber(path).use { grabber -> Java2DFrameConverter().use { converter ->
        val wanted = info.frames.atOrBefore(timeUs)
        grabber.setVideoTimestamp(wanted)
        val frame = grabber.grabImage() ?: error("動画のフレームを読み取れません")
        adopt(RasterVideoPreviewFrame(timeUs, desktopRaster(frame, converter, info.rotationDegrees), wanted))
    } }
}

internal actual suspend fun exportDeviceVideo(context: Any?, path: String, info: VideoEditInfo, document: MosaicDocument, output: String, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
    require(!info.hdr) { "HDR動画の編集には対応していません" }
    val intermediate = File.createTempFile("encoded-", ".mp4", File(output).parentFile)
    try {
        desktopGrabber(path).use { grabber -> Java2DFrameConverter().use { converter ->
            FFmpegFrameRecorder(intermediate.absolutePath, info.width, info.height).use { recorder ->
                recorder.format = "mp4"
                recorder.videoCodec = AV_CODEC_ID_H264
                recorder.videoCodecName = "libopenh264"
                recorder.pixelFormat = AV_PIX_FMT_YUV420P
                recorder.frameRate = 30.0
                recorder.videoBitrate = (info.width.toLong() * info.height * 8).coerceIn(2_000_000, 40_000_000).toInt()
                recorder.maxBFrames = 0
                recorder.start()
                var index = 0
                while (true) {
                    ensureActive()
                    val frame = grabber.grabImage() ?: break
                    check(index < info.frames.size && frame.timestamp == info.frames.timeAt(index)) { "書き出し中のフレーム時刻が一致しません" }
                    val rendered = renderVideoPreview(desktopRaster(frame, converter, info.rotationDegrees), document, frame.timestamp)
                    recorder.record(converter.convert(rendered.bufferedImage()))
                    index++
                    onProgress(index.toFloat() / info.frames.size * .9f)
                }
                check(index == info.frames.size) { "書き出し中にフレームが欠落しました" }
                recorder.stop()
            }
        } }
        // Restore each original PTS and packet duration; never round VFR to the encoder's FPS.
        muxDesktopVideoTimeline(intermediate.absolutePath, path, output, info)
        ensureActive(); onProgress(1f)
    } catch (failure: Throwable) { File(output).delete(); throw failure }
    finally { intermediate.delete() }
}
