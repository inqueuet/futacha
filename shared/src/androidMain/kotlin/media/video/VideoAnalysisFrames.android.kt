@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.valoser.futacha.shared.media.video

import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.valoser.futacha.shared.media.analysis.AnalysisFrame
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*

/** One sequential decoder, with bounded output; no per-frame thumbnail seeking. */
internal actual suspend fun decodeDeviceVideoFrames(
    path: String, info: VideoEditInfo, request: VideoAnalysisRequest, consume: suspend (AnalysisFrame) -> Unit
): Unit = withContext(AppDispatchers.io) {
    val probe = MediaExtractor()
    val mime = try {
        probe.setDataSource(path)
        val video = (0 until probe.trackCount).mapNotNull { probe.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
            .filter { it.startsWith("video/") }
        require(video.size == 1) { "映像トラックが1つの動画を選択してください" }
        video.single()
    } finally { probe.release() }
    // Analysis needs CPU-readable images. Prefer available platform software decoders;
    // do not infer capability from device models or ship another codec library.
    val candidates = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, false, false)
        .distinctBy { it.name }.sortedByDescending { it.softwareOnly }.take(4)
    check(candidates.isNotEmpty()) { "この動画の解析用デコーダーがありません" }
    var failure: Exception? = null
    for (candidate in candidates) {
        currentCoroutineContext().ensureActive()
        var delivered = false
        try {
            decodeAndroidAnalysisFrames(path, info, request, candidate.name) {
                delivered = true; consume(it)
            }
            check(delivered) { "デコーダーから解析用画像を読み取れません" }
            return@withContext
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) {
            // A consumer may already have accumulated a trajectory. Never replay frames
            // into it after a late decoder failure or an exception in that consumer.
            if (delivered) throw e
            failure?.let(e::addSuppressed); failure = e
        }
    }
    throw checkNotNull(failure)
}

private suspend fun decodeAndroidAnalysisFrames(
    path: String, info: VideoEditInfo, request: VideoAnalysisRequest, codecName: String,
    consume: suspend (AnalysisFrame) -> Unit
) {
    val coroutine = currentCoroutineContext()
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    var started = false
    try {
        extractor.setDataSource(path)
        val tracks = (0 until extractor.trackCount).filter {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        }
        require(tracks.size == 1) { "映像トラックが1つの動画を選択してください" }
        val track = tracks.single()
        val format = extractor.getTrackFormat(track)
        val rotation = ((format.integerOr(MediaFormat.KEY_ROTATION, 0) % 360) + 360) % 360
        require(rotation == info.rotationDegrees) { "動画の回転情報が変わりました" }
        extractor.selectTrack(track)
        // Read only sample positions/timestamps, not their full payloads or metadata tags.
        val timestamps = ArrayList<Long>()
        while (extractor.sampleTime >= 0) {
            coroutine.ensureActive()
            require(timestamps.size < info.frames.size) { "解析用のフレーム数が元動画と一致しません" }
            timestamps += extractor.sampleTime
            if (!extractor.advance()) break
        }
        val timeline = VideoDecodeTimeline(timestamps, info.frames)
        val first = timeline.decodedTime(request.firstIndex)
        val end = if (request.endIndex < info.frames.size) timeline.decodedTime(request.endIndex) else Long.MAX_VALUE
        extractor.seekTo(first, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        format.setInteger(MediaFormat.KEY_ROTATION, 0)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        val decoder = MediaCodec.createByCodecName(codecName)
        codec = decoder
        decoder.configure(format, null, null, 0)
        decoder.start(); started = true
        val output = MediaCodec.BufferInfo()
        var inputEnded = false
        var lastActivity = SystemClock.elapsedRealtime()
        while (true) {
            coroutine.ensureActive()
            if (!inputEnded) {
                val index = decoder.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = requireNotNull(decoder.getInputBuffer(index)) { "動画の入力バッファを取得できません" }
                    buffer.clear()
                    val count = extractor.readSampleData(buffer, 0)
                    if (count < 0) {
                        decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEnded = true
                    } else {
                        require(count <= buffer.capacity()) { "動画のフレームが大きすぎます" }
                        require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "暗号化された動画は解析できません" }
                        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0)
                            MediaCodec.BUFFER_FLAG_PARTIAL_FRAME else 0
                        decoder.queueInputBuffer(index, 0, count, extractor.sampleTime, flags)
                        extractor.advance()
                    }
                    lastActivity = SystemClock.elapsedRealtime()
                }
            }
            val index = decoder.dequeueOutputBuffer(output, 10_000)
            var frame: AnalysisFrame? = null
            var done = false
            if (index >= 0) {
                lastActivity = SystemClock.elapsedRealtime()
                try {
                    if (output.size > 0 && output.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val time = output.presentationTimeUs
                        if (time >= end) done = true
                        else if (time >= first) {
                            val image = requireNotNull(decoder.getOutputImage(index)) { "このデコーダーは解析用画像を出力できません" }
                            frame = image.use {
                                sampleAndroidAnalysisImage(it, timeline.originalTime(time), info, request, decoder.outputFormat) { coroutine.ensureActive() }
                            }
                        }
                    }
                    if (output.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                } finally { decoder.releaseOutputBuffer(index, false) }
            }
            // Native buffers are released before inference, which can legitimately take seconds.
            frame?.let { consume(it); lastActivity = SystemClock.elapsedRealtime() }
            if (done) break
            check(SystemClock.elapsedRealtime() - lastActivity < 15_000) { "動画のデコードが停止しました" }
        }
    } finally {
        try { codec?.let { if (started) runCatching { it.stop() }; it.release() } }
        finally { extractor.release() }
    }
}

private fun MediaFormat.integerOr(key: String, default: Int): Int = if (containsKey(key)) getInteger(key) else default

private fun sampleAndroidAnalysisImage(
    image: Image, timeUs: Long, info: VideoEditInfo, request: VideoAnalysisRequest,
    format: MediaFormat, check: () -> Unit
): AnalysisFrame {
    require(image.format == ImageFormat.YUV_420_888 && image.planes.size == 3) { "この画像形式の自動解析にはSDR変換が必要です" }
    val crop = image.cropRect
    val rotation = info.rotationDegrees
    val width = if (rotation % 180 == 0) crop.width() else crop.height()
    val height = if (rotation % 180 == 0) crop.height() else crop.width()
    require(width == info.width && height == info.height) { "解析用画像の寸法が元動画と一致しません" }
    val standard = format.integerOr(MediaFormat.KEY_COLOR_STANDARD, 0).takeIf { it != 0 } ?: MediaFormat.COLOR_STANDARD_BT601_NTSC
    require(standard in setOf(MediaFormat.COLOR_STANDARD_BT601_NTSC, MediaFormat.COLOR_STANDARD_BT601_PAL, MediaFormat.COLOR_STANDARD_BT709)) {
        "この動画の色空間の自動解析にはSDR変換が必要です"
    }
    val planes = image.planes.map { p ->
        val buffer = p.buffer
        AnalysisBytePlane(buffer.limit(), p.rowStride, p.pixelStride, buffer.position()) { buffer.get(it) }
    }
    val geometry = AnalysisRasterGeometry(image.width, image.height, crop.left, crop.top, crop.width(), crop.height(), rotation, request.maximumEdge)
    return sampleYuvAnalysisFrame(timeUs, geometry, planes[0], planes[1], planes[2],
        format.integerOr(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED) == MediaFormat.COLOR_RANGE_FULL,
        standard == MediaFormat.COLOR_STANDARD_BT709, request.includeRgb, check)
}
