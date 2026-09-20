@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.useContents
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToLong

/** AVPlayer uses the export kernel and the asset's original timing/orientation, without encoding. */
internal suspend fun editedVideoPlayerItem(url: NSURL, editing: VideoEditPlayback): AVPlayerItem = withContext(AppDispatchers.io) {
    val asset = AVURLAsset.URLAssetWithURL(url, mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true))
    val kernel = VideoMosaicKernel()
    val context = videoRenderingContext()
    val lock = NSLock()
    // Keep the caller's source hold until native asset preparation has actually returned.
    val composition = suspendCoroutine<AVVideoComposition> { continuation ->
        AVVideoComposition.videoCompositionWithAsset(asset, applyingCIFiltersWithHandler = filter@{ optionalRequest ->
            val request = optionalRequest ?: return@filter
            try {
                check(editing.isActive) { "編集プレビューは終了しました" }
                val time = (CMTimeGetSeconds(request.compositionTime) * 1_000_000).roundToLong()
                // AVFoundation may request several frames concurrently; the mask atlas is mutable.
                lock.lock()
                val filtered = try { kernel.apply(request.sourceImage as platform.CoreImage.CIImage, editing.document, time) }
                finally { lock.unlock() }
                // AVFoundation's Kotlin declarations forward-declare the Core Image classes.
                request.finishWithImage(filtered as objcnames.classes.CIImage, context as objcnames.classes.CIContext)
            } catch (failure: Exception) {
                request.finishWithError(NSError.errorWithDomain("FutachaEditPreview", 1,
                    mapOf(NSLocalizedDescriptionKey to (failure.message ?: "編集プレビューを描画できません"))))
            }
        }, completionHandler = { value, failure ->
            if (value != null) continuation.resume(value)
            else continuation.resumeWithException(IllegalStateException(failure?.localizedDescription ?: "編集プレビューを準備できません"))
        })
    }
    val size = composition.renderSize.useContents { width.toInt() to height.toInt() }
    check(size == (editing.info.width to editing.info.height)) { "編集プレビューの回転・寸法が元動画と一致しません" }
    AVPlayerItem(asset = asset).apply { videoComposition = composition }
}
