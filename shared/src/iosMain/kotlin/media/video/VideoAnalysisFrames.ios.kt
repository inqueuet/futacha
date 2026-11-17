@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.analysis.AnalysisFrame
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.CoreMedia.*
import platform.CoreVideo.*
import platform.Foundation.NSURL
import kotlin.math.*
import kotlin.time.TimeSource

/** AVAssetReader decodes only each request's timeRange, so separate readers cost no rescans. */
internal actual suspend fun decodeDeviceVideoFrameChunks(
    path: String, info: VideoEditInfo, requests: List<VideoAnalysisRequest>, consume: suspend (Int, AnalysisFrame) -> Unit
) {
    requests.forEachIndexed { chunk, request -> decodeDeviceVideoFrames(path, info, request) { consume(chunk, it) } }
}

internal actual suspend fun decodeDeviceVideoFrames(
    path: String, info: VideoEditInfo, request: VideoAnalysisRequest, consume: suspend (AnalysisFrame) -> Unit
): Unit = withContext(AppDispatchers.io) {
    coroutineScope {
        val coroutine = currentCoroutineContext()
        val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path),
            mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true))
        val tracks = asset.tracksWithMediaType(AVMediaTypeVideo).filterIsInstance<AVAssetTrack>()
        require(tracks.size == 1) { "この動画から解析用フレームを読み取れません" }
        val track = tracks.single()
        val transform = track.preferredTransform
        val mirrored = transform.useContents { a * d - b * c < 0 }
        val angle = transform.useContents { atan2(if (mirrored) -b else b, if (mirrored) -a else a) * 180 / PI }
        val rotation = ((angle.roundToInt() % 360) + 360) % 360
        require(rotation in setOf(0, 90, 180, 270)) { "この動画の回転情報に対応していません" }
        // Sampling supports the eight orthogonal orientations, including mirrored camera input.
        require(transform.useContents {
            abs(abs(a) + abs(b) - 1.0) < .0001 && abs(abs(c) + abs(d) - 1.0) < .0001 && abs(a * c + b * d) < .0001
        }) { "この動画の変形情報は自動解析できません" }
        val reader = AVAssetReader(asset, null)
        val output = AVAssetReaderTrackOutput(track, mapOf(cfVideoString(kCVPixelBufferPixelFormatTypeKey) to kCVPixelFormatType_32BGRA.toInt()))
        output.alwaysCopiesSampleData = false
        require(reader.canAddOutput(output)) { "この動画から解析用フレームを読み取れません" }
        reader.addOutput(output)
        val first = request.frames.timeAt(request.firstIndex)
        val end = if (request.endIndex < request.frames.size) request.frames.timeAt(request.endIndex) else request.frames.durationUs
        // The native timescale may place a boundary a fraction of a microsecond before its
        // rounded editor timestamp. Read across that rounding boundary, then filter actual PTS.
        val decodeStart = (first - 1).coerceAtLeast(0)
        val decodeEnd = if (end < Long.MAX_VALUE) end + 1 else end
        reader.timeRange = CMTimeRangeMake(CMTimeMake(decodeStart, 1_000_000), CMTimeMake(decodeEnd - decodeStart, 1_000_000))
        val context = videoRenderingContext()
        val colorSpace = requireNotNull(CGColorSpaceCreateWithName(kCGColorSpaceSRGB))
        try {
            check(reader.startReading()) { reader.error?.localizedDescription ?: "動画の読み取りを開始できません" }
            while (true) {
                coroutine.ensureActive()
                val waiting = TimeSource.Monotonic.markNow()
                val sample = autoreleasepool { output.copyNextSampleBuffer() }
                if (sample == null) {
                    coroutine.ensureActive()
                    check(waiting.elapsedNow().inWholeMilliseconds < 15_000) { "動画のデコードが停止しました" }
                    break
                }
                val frame = try {
                    coroutine.ensureActive()
                    check(waiting.elapsedNow().inWholeMilliseconds < 15_000) { "動画のデコードが停止しました" }
                    if (CMSampleBufferGetNumSamples(sample) == 0L) null else autoreleasepool {
                        val seconds = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample))
                        require(seconds.isFinite() && seconds >= 0 && seconds < Long.MAX_VALUE / 1_000_000.0)
                        val timeUs = (seconds * 1_000_000).roundToLong()
                        if (timeUs < first || timeUs >= end) null else
                            sampleIosAnalysisFrame(requireNotNull(CMSampleBufferGetImageBuffer(sample)),
                                timeUs, info, request, rotation, mirrored, context, colorSpace) { coroutine.ensureActive() }
                    }
                } finally { CFRelease(sample) }
                frame?.let { consume(it) }
            }
            coroutine.ensureActive()
            check(reader.status == AVAssetReaderStatusCompleted) { reader.error?.localizedDescription ?: "動画の読み取りが完了しませんでした" }
        } finally {
            // AVAssetReader.h forbids cancelReading concurrently with copyNextSampleBuffer.
            // Native reads finish on this worker before cancellation releases the input lease.
            reader.cancelReading()
            CGColorSpaceRelease(colorSpace)
        }
    }
}

private fun sampleIosAnalysisFrame(
    pixel: CVPixelBufferRef, timeUs: Long, info: VideoEditInfo, request: VideoAnalysisRequest,
    rotation: Int, mirrored: Boolean, context: CIContext, colorSpace: CGColorSpaceRef, check: () -> Unit
): AnalysisFrame = memScoped {
    require(CVPixelBufferGetPixelFormatType(pixel) == kCVPixelFormatType_32BGRA)
    val width = CVPixelBufferGetWidth(pixel).toLong(); val height = CVPixelBufferGetHeight(pixel).toLong()
    require(width in 1..32768 && height in 1..32768)
    val uprightW = if (rotation % 180 == 0) width else height
    val uprightH = if (rotation % 180 == 0) height else width
    require(uprightW == info.width.toLong() && uprightH == info.height.toLong()) { "解析用画像の寸法が元動画と一致しません" }
    // BGRA values retain the video's color primaries/transfer attachments. Convert them
    // to the same sRGB model input used for images, rather than discarding that metadata.
    val originalGeometry = AnalysisRasterGeometry(width.toInt(), height.toInt(), rotation = rotation, maximumEdge = request.maximumEdge)
    val targetWidth = if (rotation % 180 == 0) originalGeometry.width else originalGeometry.height
    val targetHeight = if (rotation % 180 == 0) originalGeometry.height else originalGeometry.width
    val output = alloc<CVPixelBufferRefVar>()
    kotlin.check(CVPixelBufferCreate(null, targetWidth.toULong(), targetHeight.toULong(), kCVPixelFormatType_32BGRA, null, output.ptr) == kCVReturnSuccess)
    val converted = requireNotNull(output.value)
    try {
        val image = CIImage.imageWithCVPixelBuffer(pixel).imageByApplyingTransform(
            CGAffineTransformMakeScale(targetWidth.toDouble() / width, targetHeight.toDouble() / height))
        context.render(image, toCVPixelBuffer = converted, bounds = CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()), colorSpace = colorSpace)
        kotlin.check(CVPixelBufferLockBaseAddress(converted, kCVPixelBufferLock_ReadOnly) == kCVReturnSuccess)
        try {
            val stride = CVPixelBufferGetBytesPerRow(converted).toInt()
            val base = requireNotNull(CVPixelBufferGetBaseAddress(converted)) { "解析用画像を読み取れません" }.reinterpret<ByteVar>()
            val plane = AnalysisBytePlane(stride * targetHeight, stride, 4) { base[it] }
            val geometry = AnalysisRasterGeometry(targetWidth, targetHeight, rotation = rotation,
                maximumEdge = request.maximumEdge, mirrorX = mirrored)
            sampleBgraAnalysisFrame(timeUs, geometry, plane, request.includeRgb, check)
        } finally { CVPixelBufferUnlockBaseAddress(converted, kCVPixelBufferLock_ReadOnly) }
    } finally { CVPixelBufferRelease(converted) }
}
