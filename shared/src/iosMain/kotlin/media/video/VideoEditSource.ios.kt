@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.video.model.VideoFrameIndex
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.CoreFoundation.CFStringCompare
import platform.CoreFoundation.kCFCompareEqualTo
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGSizeApplyAffineTransform
import platform.CoreMedia.*
import platform.CoreVideo.*
import platform.Foundation.NSURL
import kotlin.math.*

internal actual suspend fun inspectDeviceVideo(path: String): VideoEditInfo = withContext(AppDispatchers.io) {
    require(path.startsWith('/')) { "端末内の動画を選択してください" }
    val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path),
        options = mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true))
    val tracks = asset.tracksWithMediaType(AVMediaTypeVideo).filterIsInstance<AVAssetTrack>()
    require(tracks.size == 1) { "この動画を読み取れません。MP4・MOVの映像トラックが1つの動画を選択してください" }
    val track = tracks.single()
    val size = CGSizeApplyAffineTransform(track.naturalSize, track.preferredTransform)
    val width = size.useContents { abs(this.width).roundToInt() }
    val height = size.useContents { abs(this.height).roundToInt() }
    val degrees = track.preferredTransform.useContents { atan2(b, a) * 180 / PI }
    val rotation = ((degrees.roundToInt() % 360) + 360) % 360
    require(rotation in setOf(0, 90, 180, 270)) { "この動画の回転情報に対応していません" }
    val reader = AVAssetReader(asset, error = null)
    val output = AVAssetReaderTrackOutput(track, outputSettings = null)
    output.alwaysCopiesSampleData = false
    require(reader.canAddOutput(output)) { "動画を読み取れません" }
    reader.addOutput(output)
    check(reader.startReading()) { reader.error?.localizedDescription ?: "動画を読み取れません" }
    val timestamps = ArrayList<Long>()
    var hdr = false
    var sampleEndUs = 0L
    try {
        while (true) {
            currentCoroutineContext().ensureActive()
            // Scan every sample of a clip up to 1GB: drain AVFoundation's
            // autoreleased objects per sample, as VideoAnalysisFrames does.
            val sample = autoreleasepool { output.copyNextSampleBuffer() } ?: break
            try { autoreleasepool {
                val count = CMSampleBufferGetNumSamples(sample)
                if (count == 0L) return@autoreleasepool // AVFoundation may emit marker-only buffers.
                require(timestamps.size.toLong() + count <= VideoFrameIndex.MAX_FRAMES) { "この動画は長すぎるため編集できません" }
                CMFormatDescriptionGetExtension(CMSampleBufferGetFormatDescription(sample), kCMFormatDescriptionExtension_TransferFunction)?.let {
                    hdr = hdr || CFStringCompare(it.reinterpret(), kCMFormatDescriptionTransferFunction_SMPTE_ST_2084_PQ, 0u) == kCFCompareEqualTo ||
                        CFStringCompare(it.reinterpret(), kCMFormatDescriptionTransferFunction_ITU_R_2100_HLG, 0u) == kCFCompareEqualTo
                }
                memScoped {
                    val timing = alloc<CMSampleTimingInfo>()
                    for (index in 0 until count) {
                        check(CMSampleBufferGetSampleTimingInfo(sample, index, timing.ptr) == 0) { "動画の時刻を読み取れません" }
                        val seconds = CMTimeGetSeconds(timing.presentationTimeStamp.readValue())
                        require(seconds.isFinite() && seconds >= 0 && seconds < Long.MAX_VALUE / 1_000_000.0) { "動画のフレーム時刻が不正です" }
                        timestamps += (seconds * 1_000_000).roundToLong()
                        val end = CMTimeGetSeconds(CMTimeAdd(timing.presentationTimeStamp.readValue(), timing.duration.readValue()))
                        if (end.isFinite() && end > seconds && end < Long.MAX_VALUE / 1_000_000.0) {
                            sampleEndUs = maxOf(sampleEndUs, (end * 1_000_000).roundToLong())
                        }
                    }
                }
            } } finally { CFRelease(sample) }
        }
        check(reader.status == AVAssetReaderStatusCompleted) { reader.error?.localizedDescription ?: "動画の読み取りが完了しませんでした" }
        val rangeEnd = track.timeRange.useContents { CMTimeAdd(start.readValue(), duration.readValue()) }
        val seconds = CMTimeGetSeconds(rangeEnd)
        // Track timeRange may be rounded to the movie's millisecond timescale. Samples retain
        // the video timescale and the actual final-frame duration, including VFR held frames.
        // Compressed AVAssetReader samples can retain a B-frame edit-list offset that its
        // decoded output and AVAssetImageGenerator have already removed. Probe one decoded
        // frame to put the index on the same timeline as preview and export (no full decode).
        val offset = firstDecodedVideoTimestamp(asset, track) - requireNotNull(timestamps.minOrNull())
        val adjusted = timestamps.map { it + offset }
        val durationUs = if (sampleEndUs > requireNotNull(timestamps.maxOrNull())) sampleEndUs + offset
            else if (seconds.isFinite() && seconds >= 0 && seconds < Long.MAX_VALUE / 1_000_000.0) (seconds * 1_000_000).roundToLong() else 0L
        VideoEditInfo(width, height, buildVideoFrameIndex(adjusted, durationUs),
            asset.tracksWithMediaType(AVMediaTypeAudio).isNotEmpty(), hdr, rotation)
    } finally { reader.cancelReading() }
}

private fun firstDecodedVideoTimestamp(asset: AVURLAsset, track: AVAssetTrack): Long {
    val reader = AVAssetReader(asset, null)
    val output = AVAssetReaderTrackOutput(track, mapOf(cfVideoString(kCVPixelBufferPixelFormatTypeKey) to kCVPixelFormatType_32BGRA.toInt()))
    output.alwaysCopiesSampleData = false; reader.addOutput(output)
    check(reader.startReading()) { "動画の先頭フレームを読み取れません" }
    try {
        val sample = requireNotNull(output.copyNextSampleBuffer()) { "動画の先頭フレームを読み取れません" }
        try {
            val seconds = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample))
            require(seconds.isFinite() && seconds >= 0)
            return (seconds * 1_000_000).roundToLong()
        } finally { CFRelease(sample) }
    } finally { reader.cancelReading() }
}
