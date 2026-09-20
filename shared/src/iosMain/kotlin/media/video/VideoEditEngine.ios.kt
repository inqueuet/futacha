@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.valoser.futacha.shared.media.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.valoser.futacha.shared.media.video.model.MosaicDocument
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreFoundation.*
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.CoreMedia.*
import platform.CoreVideo.*
import platform.VideoToolbox.*
import kotlin.concurrent.AtomicReference
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.math.roundToLong
import kotlin.time.TimeSource

internal actual suspend fun previewDeviceVideo(path: String, info: VideoEditInfo, timeUs: Long, document: MosaicDocument): ImageBitmap = withContext(AppDispatchers.io) {
    val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path), null)
    val generator = AVAssetImageGenerator(asset)
    generator.appliesPreferredTrackTransform = true
    generator.maximumSize = CGSizeMake(960.0, 960.0)
    generator.requestedTimeToleranceBefore = CMTimeMake(1, 1_000_000); generator.requestedTimeToleranceAfter = CMTimeMake(1, 1_000_000)
    val image = requireNotNull(generator.copyCGImageAtTime(CMTimeMake(timeUs, 1_000_000), null, null)) { "動画のコマを読み取れません" }
    try {
        currentCoroutineContext().ensureActive()
        val input = CIImage.imageWithCGImage(image)
        val rendered = VideoMosaicKernel().apply(input, document, timeUs)
        val context = videoRenderingContext()
        val cg = requireNotNull(context.createCGImage(rendered, rendered.extent)) { "プレビューを描画できません" }
        try {
            val data = requireNotNull(UIImagePNGRepresentation(UIImage.imageWithCGImage(cg)))
            val bytes = ByteArray(data.length.toInt()).also { b -> b.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
            val skia = org.jetbrains.skia.Image.makeFromEncoded(bytes)
            try { skia.toComposeImageBitmap() } finally { skia.close() }
        } finally { CGImageRelease(cg) }
    } finally { CGImageRelease(image); generator.cancelAllCGImageGeneration() }
}

internal actual suspend fun exportDeviceVideo(context: Any?, path: String, info: VideoEditInfo,
    document: MosaicDocument, output: String, onProgress: (Float) -> Unit): Unit = withContext(AppDispatchers.io) {
    validateVideoEditDocument(document, info)
    val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path), mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true))
    val track = asset.tracksWithMediaType(AVMediaTypeVideo).filterIsInstance<AVAssetTrack>().single()
    val audioTracks = asset.tracksWithMediaType(AVMediaTypeAudio).filterIsInstance<AVAssetTrack>()
    require(audioTracks.size <= 1) { "音声トラックが複数ある動画はまだ編集できません" }
    val reader = AVAssetReader(asset, null)
    val videoRead = AVAssetReaderTrackOutput(track, mapOf(cfVideoString(kCVPixelBufferPixelFormatTypeKey) to kCVPixelFormatType_32BGRA.toInt()))
    videoRead.alwaysCopiesSampleData = false
    reader.addOutput(videoRead)
    val audioRead = audioTracks.singleOrNull()?.let { AVAssetReaderTrackOutput(it, null).also { a -> a.alwaysCopiesSampleData = false; reader.addOutput(a) } }
    val writer = AVAssetWriter(NSURL.fileURLWithPath(output), AVFileTypeMPEG4, null)
    val ci = videoRenderingContext()
    val kernel = VideoMosaicKernel()
    val encoder = createVideoEncoder(info)
    val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceITUR_709)
    var pendingAudio: CMSampleBufferRef? = null
    var pendingVideo: CMSampleBufferRef? = null
    var completed = false
    fun nextVideo(): CMSampleBufferRef? {
        val sample = videoRead.copyNextSampleBuffer() ?: return null
        return try {
            autoreleasepool { renderVideoSample(sample, track, info, document, kernel, ci, colorSpace, encoder) }
        } finally { CFRelease(sample) }
    }
    try {
        check(reader.startReading()) { reader.error?.localizedDescription ?: "動画を開けません" }
        pendingVideo = requireNotNull(nextVideo()) { "動画に映像がありません" }
        // Supply compressed video and its actual format. The implicit writer encoder loses
        // VFR's final duration; VideoToolbox lets us retain the original presentation timeline.
        val videoWrite = AVAssetWriterInput(AVMediaTypeVideo, null, CMSampleBufferGetFormatDescription(pendingVideo))
        videoWrite.expectsMediaDataInRealTime = false; videoWrite.mediaTimeScale = 1_000_000
        require(writer.canAddInput(videoWrite)) { "この解像度で動画を書き出せません" }; writer.addInput(videoWrite)
        pendingAudio = audioRead?.copyNextSampleBuffer()
        val audioWrite = pendingAudio?.let { sample ->
            val format = requireNotNull(CMSampleBufferGetFormatDescription(sample))
            require(CMFormatDescriptionGetMediaSubType(format) == kAudioFormatMPEG4AAC) { "現在iOSではAAC音声の動画に対応しています" }
            AVAssetWriterInput(AVMediaTypeAudio, null, format).also {
                it.expectsMediaDataInRealTime = false
                require(writer.canAddInput(it)) { "この音声を書き出せません" }; writer.addInput(it)
            }
        }
        require(!info.hasAudio || audioWrite != null) { "音声を読み取れません" }
        check(writer.startWriting()) { writer.error?.localizedDescription ?: "動画の書き出しを開始できません" }
        writer.startSessionAtSourceTime(kCMTimeZero.readValue())
        var videoDone = false; var audioDone = audioWrite == null
        var progressTime = TimeSource.Monotonic.markNow()
        var idleSince = TimeSource.Monotonic.markNow()
        while (!videoDone || !audioDone) {
            currentCoroutineContext().ensureActive()
            check(writer.status != AVAssetWriterStatusFailed && reader.status != AVAssetReaderStatusFailed) { writer.error?.localizedDescription ?: reader.error?.localizedDescription ?: "動画の書き出しに失敗しました" }
            var advanced = false
            if (!videoDone && videoWrite.readyForMoreMediaData) {
                val sample = pendingVideo
                if (sample == null) {
                    appendVideoEndMarker(videoWrite, info.frames.durationUs)
                    videoWrite.markAsFinished(); videoDone = true
                } else {
                    try {
                        check(videoWrite.appendSampleBuffer(sample)) { writer.error?.localizedDescription ?: "映像を書き出せません" }
                        if (progressTime.elapsedNow().inWholeMilliseconds >= 100) {
                            val time = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample)) * 1_000_000
                            onProgress((time / info.frames.durationUs).toFloat().coerceIn(0f, 1f)); progressTime = TimeSource.Monotonic.markNow()
                        }
                    } finally { CFRelease(sample); pendingVideo = null }
                    currentCoroutineContext().ensureActive()
                    pendingVideo = nextVideo()
                }
                advanced = true
            }
            if (!audioDone && audioWrite?.readyForMoreMediaData == true) {
                val sample = pendingAudio
                if (sample == null) { audioWrite.markAsFinished(); audioDone = true }
                else {
                    try { if (CMSampleBufferGetNumSamples(sample) > 0) check(audioWrite.appendSampleBuffer(sample)) { writer.error?.localizedDescription ?: "音声を書き出せません" } }
                    finally { CFRelease(sample); pendingAudio = null }
                    pendingAudio = audioRead?.copyNextSampleBuffer()
                }
                advanced = true
            }
            if (advanced) idleSince = TimeSource.Monotonic.markNow()
            else { check(idleSince.elapsedNow().inWholeSeconds < 30) { "動画の書き出しが停止しました" }; delay(5) }
        }
        check(reader.status == AVAssetReaderStatusCompleted) { reader.error?.localizedDescription ?: "動画の読み取りが未完了です" }
        val finished = CompletableDeferred<Unit>()
        writer.finishWritingWithCompletionHandler { finished.complete(Unit) }
        withTimeout(30_000) { finished.await() }
        check(writer.status == AVAssetWriterStatusCompleted) { writer.error?.localizedDescription ?: "動画を確定できません" }
        currentCoroutineContext().ensureActive(); completed = true; onProgress(1f)
    } finally {
        pendingVideo?.let { CFRelease(it) }; pendingAudio?.let { CFRelease(it) }; reader.cancelReading()
        if (!completed) writer.cancelWriting()
        VTCompressionSessionInvalidate(encoder); CFRelease(encoder)
        CGColorSpaceRelease(colorSpace)
    }
}

private fun renderVideoSample(sample: CMSampleBufferRef, track: AVAssetTrack, info: VideoEditInfo,
    document: MosaicDocument, kernel: VideoMosaicKernel, context: CIContext, colorSpace: CGColorSpaceRef?,
    encoder: VTCompressionSessionRef): CMSampleBufferRef = memScoped {
    val original = requireNotNull(CMSampleBufferGetImageBuffer(sample))
    // Track transforms use top-left coordinates; Core Image uses bottom-left coordinates.
    // Conjugate the linear transform by a vertical flip, then normalize the translated extent.
    val transform = track.preferredTransform.useContents { CGAffineTransformMake(a, -b, -c, d, 0.0, 0.0) }
    var image = CIImage.imageWithCVPixelBuffer(original).imageByApplyingTransform(transform)
    val origin = image.extent.useContents { origin.x to origin.y }
    image = image.imageByApplyingTransform(CGAffineTransformMakeTranslation(-origin.first, -origin.second))
    val time = (CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample)) * 1_000_000).roundToLong()
    val filtered = kernel.apply(image, document, time)
    val buffer = alloc<CVPixelBufferRefVar>()
    check(CVPixelBufferCreate(null, info.width.toULong(), info.height.toULong(), kCVPixelFormatType_32BGRA, null, buffer.ptr) == 0)
    val pixel = requireNotNull(buffer.value)
    try {
        CVBufferSetAttachment(pixel, kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_709_2, kCVAttachmentMode_ShouldPropagate)
        CVBufferSetAttachment(pixel, kCVImageBufferTransferFunctionKey, kCVImageBufferTransferFunction_ITU_R_709_2, kCVAttachmentMode_ShouldPropagate)
        CVBufferSetAttachment(pixel, kCVImageBufferYCbCrMatrixKey, kCVImageBufferYCbCrMatrix_ITU_R_709_2, kCVAttachmentMode_ShouldPropagate)
        context.render(filtered, toCVPixelBuffer = pixel, bounds = CGRectMake(0.0, 0.0, info.width.toDouble(), info.height.toDouble()), colorSpace = colorSpace)
        encodeVideoFrame(encoder, pixel, time, info.frames.endAfter(time) - time)
    } finally { CVPixelBufferRelease(pixel) }
}

private fun createVideoEncoder(info: VideoEditInfo): VTCompressionSessionRef = memScoped {
    val result = alloc<VTCompressionSessionRefVar>()
    check(VTCompressionSessionCreate(null, info.width, info.height, kCMVideoCodecType_H264, null, null, null, null, null, result.ptr) == 0) { "この解像度で動画を書き出せません" }
    val session = requireNotNull(result.value)
    try {
        check(VTSessionSetProperty(session, kVTCompressionPropertyKey_AllowFrameReordering, kCFBooleanFalse) == 0)
        // Rendering and compression must agree on color space, including SD-sized clips.
        check(VTSessionSetProperty(session, kVTCompressionPropertyKey_ColorPrimaries, kCVImageBufferColorPrimaries_ITU_R_709_2) == 0)
        check(VTSessionSetProperty(session, kVTCompressionPropertyKey_TransferFunction, kCVImageBufferTransferFunction_ITU_R_709_2) == 0)
        check(VTSessionSetProperty(session, kVTCompressionPropertyKey_YCbCrMatrix, kCVImageBufferYCbCrMatrix_ITU_R_709_2) == 0)
        check(VTCompressionSessionPrepareToEncodeFrames(session) == 0)
        session
    } catch (failure: Throwable) { VTCompressionSessionInvalidate(session); CFRelease(session); throw failure }
}

private data class EncodedVideoFrame(val status: Int, val flags: UInt, val sample: CMSampleBufferRef?)

/** Complete one frame before advancing: bounded native buffers and predictable cancellation. */
private fun encodeVideoFrame(encoder: VTCompressionSessionRef, pixel: CVPixelBufferRef, timeUs: Long, durationUs: Long): CMSampleBufferRef {
    val result = AtomicReference<EncodedVideoFrame?>(null)
    return try {
        val status = VTCompressionSessionEncodeFrameWithOutputHandler(encoder, pixel, CMTimeMake(timeUs, 1_000_000),
            CMTimeMake(durationUs, 1_000_000), null, null) { error, flags, sample ->
            sample?.let { CFRetain(it) }
            result.value = EncodedVideoFrame(error, flags, sample)
        }
        val completed = VTCompressionSessionCompleteFrames(encoder, kCMTimeInvalid.readValue())
        check(status == 0) { "動画のフレームを圧縮できません" }
        check(completed == 0) { "動画のフレームを確定できません" }
        val frame = requireNotNull(result.value) { "動画のフレームが返りません" }
        check(frame.status == 0 && frame.flags and kVTEncodeInfo_FrameDropped == 0u) { "動画のフレームが欠落しました" }
        // Reordering is disabled, so DTS equals PTS. Do not expose the encoder's internal
        // one-frame decode delay as a video/audio offset in the MP4 edit list.
        memScoped {
            val timing = alloc<CMSampleTimingInfo>()
            timing.presentationTimeStamp.value = timeUs; timing.presentationTimeStamp.timescale = 1_000_000
            timing.presentationTimeStamp.flags = 1u; timing.presentationTimeStamp.epoch = 0
            timing.decodeTimeStamp.value = timeUs; timing.decodeTimeStamp.timescale = 1_000_000
            timing.decodeTimeStamp.flags = 1u; timing.decodeTimeStamp.epoch = 0
            timing.duration.value = durationUs; timing.duration.timescale = 1_000_000
            timing.duration.flags = 1u; timing.duration.epoch = 0
            val copy = alloc<CMSampleBufferRefVar>()
            check(CMSampleBufferCreateCopyWithNewTiming(null, requireNotNull(frame.sample), 1, timing.ptr, copy.ptr) == 0)
            requireNotNull(copy.value)
        }
    } finally { result.value?.sample?.let { CFRelease(it) } }
}

internal fun cfVideoString(value: CFStringRef?): String = memScoped {
    val bytes = allocArray<ByteVar>(256)
    check(CFStringGetCString(value, bytes, 256, kCFStringEncodingUTF8))
    bytes.toKString()
}

internal fun videoRenderingContext(): CIContext = CIContext.contextWithOptions(
    if (platform.Metal.MTLCreateSystemDefaultDevice() == null) mapOf(kCIContextUseSoftwareRenderer to true) else null
)

/** Mark only the video track end; audio can legitimately extend beyond the last video frame. */
private fun appendVideoEndMarker(writer: AVAssetWriterInput, endUs: Long) = memScoped {
    val timing = alloc<CMSampleTimingInfo>()
    timing.duration.value = 0; timing.duration.timescale = 1_000_000; timing.duration.flags = 1u; timing.duration.epoch = 0
    timing.presentationTimeStamp.value = endUs; timing.presentationTimeStamp.timescale = 1_000_000
    timing.presentationTimeStamp.flags = 1u; timing.presentationTimeStamp.epoch = 0
    timing.decodeTimeStamp.value = endUs; timing.decodeTimeStamp.timescale = 1_000_000
    timing.decodeTimeStamp.flags = 1u; timing.decodeTimeStamp.epoch = 0
    val marker = alloc<CMSampleBufferRefVar>()
    check(CMSampleBufferCreateReady(null, null, null, 0, 1, timing.ptr, 0, null, marker.ptr) == 0)
    try {
        CMSetAttachment(marker.value, kCMSampleBufferAttachmentKey_EndsPreviousSampleDuration, kCFBooleanTrue, kCMAttachmentMode_ShouldPropagate)
        check(writer.appendSampleBuffer(marker.value)) { "最後のフレーム時刻を確定できません" }
    } finally { marker.value?.let { CFRelease(it) } }
}
