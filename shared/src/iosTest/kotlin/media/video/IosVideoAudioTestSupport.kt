@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.video

import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.AVFAudio.*
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.*
import platform.Foundation.NSURL

internal fun decodedVideoTestAudio(path: String): Pair<Long, ByteArray> {
    val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path), mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true))
    val track = asset.tracksWithMediaType(AVMediaTypeAudio).filterIsInstance<AVAssetTrack>().singleOrNull() ?: return 0L to byteArrayOf()
    val reader = AVAssetReader(asset, null)
    // Compare the complete audio track, independent of the movie's estimated video duration.
    reader.timeRange = track.timeRange
    val output = AVAssetReaderTrackOutput(track, mapOf(AVFormatIDKey to platform.CoreAudioTypes.kAudioFormatLinearPCM.toInt(),
        AVLinearPCMBitDepthKey to 16, AVLinearPCMIsFloatKey to false, AVLinearPCMIsBigEndianKey to false, AVLinearPCMIsNonInterleaved to false))
    reader.addOutput(output); check(reader.startReading())
    val chunks = mutableListOf<ByteArray>(); var start: Long? = null
    try {
        while (true) {
            val sample = output.copyNextSampleBuffer() ?: break
            try {
                if (CMSampleBufferGetNumSamples(sample) == 0L) continue
                if (start == null) start = (CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample)) * 1_000_000).toLong()
                val data = requireNotNull(CMSampleBufferGetDataBuffer(sample))
                val bytes = ByteArray(CMBlockBufferGetDataLength(data).toInt())
                if (bytes.isNotEmpty()) bytes.usePinned { check(CMBlockBufferCopyDataBytes(data, 0uL, bytes.size.toULong(), it.addressOf(0)) == 0) }
                chunks += bytes
            } finally { CFRelease(sample) }
        }
        check(reader.status == AVAssetReaderStatusCompleted) { reader.error.toString() }
    } finally { reader.cancelReading() }
    val result = ByteArray(chunks.sumOf { it.size }); var offset = 0
    for (chunk in chunks) { chunk.copyInto(result, offset); offset += chunk.size }
    return (start ?: 0) to result
}
