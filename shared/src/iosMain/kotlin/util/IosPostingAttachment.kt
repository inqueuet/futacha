@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.ui.compat.compressCompatPostImage
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import platform.AVFoundation.*
import platform.Foundation.*
import platform.CoreMedia.CMTimeGetSeconds
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Normalize camera formats before either posting form validates the attachment. */
internal suspend fun normalizeIosPostingAttachment(attachment: ImageData, maxBytes: Long): ImageData {
    require(maxBytes in 1..Int.MAX_VALUE.toLong())
    return when (attachment.fileName.substringAfterLast('.', "").lowercase()) {
        "heic", "heif" -> compressCompatPostImage(attachment, maxBytes.toInt()).getOrThrow()
        "mov", "m4v" -> exportPostingVideoAsMp4(attachment, maxBytes)
        else -> attachment
    }
}

private suspend fun exportPostingVideoAsMp4(attachment: ImageData, maxBytes: Long): ImageData =
    withContext(AppDispatchers.io) {
        require(attachment.bytes.isNotEmpty() && attachment.bytes.size <= maxBytes)
        val base = NSTemporaryDirectory() + "futacha-post-" + NSUUID().UUIDString
        val source = "$base.mov"
        val output = "$base.mp4"
        try {
            val data = attachment.bytes.usePinned {
                NSData.create(bytes = it.addressOf(0), length = attachment.bytes.size.toULong())
            }
            check(data.writeToFile(source, atomically = true)) { "動画を読み込めませんでした" }
            val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(source), options = null)
            val exporter = AVAssetExportSession.exportSessionWithAsset(asset, AVAssetExportPresetHighestQuality)
                ?: error("動画をMP4に変換できませんでした")
            check(AVFileTypeMPEG4 in exporter.supportedFileTypes) { "動画をMP4に変換できませんでした" }
            exporter.outputURL = NSURL.fileURLWithPath(output)
            exporter.outputFileType = AVFileTypeMPEG4
            exporter.shouldOptimizeForNetworkUse = true
            exporter.fileLengthLimit = maxBytes
            try {
                withTimeout(60_000) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        continuation.invokeOnCancellation { exporter.cancelExport() }
                        exporter.exportAsynchronouslyWithCompletionHandler {
                            if (exporter.status == AVAssetExportSessionStatusCompleted) continuation.resume(Unit)
                            else continuation.resumeWithException(IllegalStateException("動画をMP4に変換できませんでした"))
                        }
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                throw IllegalStateException("動画の変換がタイムアウトしました", timeout)
            }
            // A size-limited export must never silently attach only a prefix.
            val originalDuration = CMTimeGetSeconds(asset.duration)
            val outputDuration = CMTimeGetSeconds(
                AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(output), options = null).duration
            )
            check(originalDuration.isFinite() && originalDuration > 0 &&
                outputDuration.isFinite() && outputDuration + 0.05 >= originalDuration) {
                "動画全体をサイズ上限内で変換できませんでした"
            }
            val loaded = loadPickedMediaFromUrl(
                NSURL.fileURLWithPath(output), isVideo = true, fallbackFileName = "video.mp4", maxBytes = maxBytes
            ) ?: error("動画のサイズが上限を超えているか、読み込めませんでした")
            loaded.copy(fileName = attachment.fileName.substringBeforeLast('.') + ".mp4")
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(source, null)
            NSFileManager.defaultManager.removeItemAtPath(output, null)
        }
    }
