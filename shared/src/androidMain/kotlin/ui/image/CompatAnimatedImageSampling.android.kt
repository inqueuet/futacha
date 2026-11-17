package com.valoser.futacha.shared.ui.image

import coil3.request.Options
import coil3.size.Dimension
import com.github.penfeizhou.animation.FrameAnimationDrawable

/**
 * The requested decode size for an animated frame decoder, or null when the
 * request leaves both sides open (Size.ORIGINAL). A single open side follows
 * the source aspect ratio.
 */
internal fun compatAnimatedTargetSize(
    requestWidth: Int?,
    requestHeight: Int?,
    sourceWidth: Int,
    sourceHeight: Int
): Pair<Int, Int>? {
    if (sourceWidth <= 0 || sourceHeight <= 0) return null
    val width = requestWidth?.takeIf { it > 0 }
    val height = requestHeight?.takeIf { it > 0 }
    if (width == null && height == null) return null
    val targetWidth = width
        ?: (sourceWidth.toLong() * height!! / sourceHeight).toInt().coerceAtLeast(1)
    val targetHeight = height
        ?: (sourceHeight.toLong() * width!! / sourceWidth).toInt().coerceAtLeast(1)
    return targetWidth to targetHeight
}

/**
 * Makes the frame decoder sample down to the request before the first frame
 * is rendered. Without it every frame buffer is allocated at the source
 * resolution (a 4096x4096 canvas is 64 MB per frame) until the drawable gets
 * display bounds. Returns whether the frames are sampled.
 */
internal fun FrameAnimationDrawable<*>.applyCompatRequestedSize(options: Options): Boolean {
    val requestWidth = (options.size.width as? Dimension.Pixels)?.px
    val requestHeight = (options.size.height as? Dimension.Pixels)?.px
    if (requestWidth == null && requestHeight == null) return false
    val decoder = frameSeqDecoder
    // Parses the header on the decoder's worker thread; decode() already runs
    // off the main thread, and the drawable needs the bounds for layout anyway.
    val sourceBounds = decoder.bounds
    val (targetWidth, targetHeight) = compatAnimatedTargetSize(
        requestWidth = requestWidth,
        requestHeight = requestHeight,
        sourceWidth = sourceBounds.width(),
        sourceHeight = sourceBounds.height()
    ) ?: return false
    return decoder.setDesiredSize(targetWidth, targetHeight) > 1
}
