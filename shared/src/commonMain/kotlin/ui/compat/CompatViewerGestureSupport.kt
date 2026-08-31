package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

internal const val COMPAT_VIEWER_DISMISS_DISTANCE_FRACTION = 0.2f
internal const val COMPAT_VIEWER_DISMISS_VELOCITY_MULTIPLIER = 2f
internal const val COMPAT_VIEWER_RUBBER_BAND_RESISTANCE = 2f
internal const val COMPAT_VIEWER_RESET_SPRING_STIFFNESS = 1500f
internal const val COMPAT_VIEWER_MAX_ZOOM = 6f
internal const val COMPAT_VIEWER_ZOOM_GESTURE_THRESHOLD = 1.05f
internal const val COMPAT_VIEWER_PAGE_SWIPE_FRACTION = 0.2f

/**
 * Transform state owned by the viewer screen rather than a pager item.
 * HorizontalPager is allowed to dispose and recreate off-screen pages; keeping
 * this state in the item made a decoded-source replacement appear to snap the
 * image back to the centre.
 */
internal data class CompatViewerTransform(
    val scale: Float = 1f,
    val translation: Offset = Offset.Zero
)

/**
 * Updates the translation for a scale/pan gesture while keeping the content
 * under the gesture centroid in place.  The previous implementation added
 * the focal correction to the old translation directly; after the first pan
 * that made every subsequent zoom use the wrong coordinate space and the
 * image appeared to snap back toward the viewport centre.
 */
internal fun compatViewerZoomTranslation(
    currentX: Float,
    currentY: Float,
    panX: Float,
    panY: Float,
    centroidX: Float,
    centroidY: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    oldScale: Float,
    newScale: Float
): Pair<Float, Float> {
    if (!oldScale.isFinite() || oldScale <= 0f ||
        !newScale.isFinite() || newScale <= 0f
    ) {
        return 0f to 0f
    }
    val scaleFactor = newScale / oldScale
    val centroidFromCenterX = centroidX - viewportWidthPx / 2f
    val centroidFromCenterY = centroidY - viewportHeightPx / 2f
    val proposedX = currentX * scaleFactor + panX + centroidFromCenterX * (1f - scaleFactor)
    val proposedY = currentY * scaleFactor + panY + centroidFromCenterY * (1f - scaleFactor)
    return clampCompatViewerZoomOffset(proposedX, viewportWidthPx, newScale) to
        clampCompatViewerZoomOffset(proposedY, viewportHeightPx, newScale)
}

internal fun compatViewerSwipeTarget(
    currentPage: Int,
    dragDistancePx: Float,
    viewportWidthPx: Float,
    pageCount: Int
): Int? {
    if (!dragDistancePx.isFinite() || !viewportWidthPx.isFinite() || viewportWidthPx <= 0f) return null
    if (pageCount <= 1 || kotlin.math.abs(dragDistancePx) < viewportWidthPx * COMPAT_VIEWER_PAGE_SWIPE_FRACTION) {
        return null
    }
    val direction = if (dragDistancePx < 0f) 1 else -1
    return (currentPage + direction).takeIf { it in 0 until pageCount }
}

internal fun dampCompatViewerVerticalDrag(deltaPx: Float): Float =
    deltaPx / COMPAT_VIEWER_RUBBER_BAND_RESISTANCE

internal fun renderCompatViewerVerticalOffset(
    rawOffsetPx: Float,
    dismissalAnimating: Boolean
): Float = if (dismissalAnimating) rawOffsetPx else dampCompatViewerVerticalDrag(rawOffsetPx)

internal fun shouldDismissCompatViewer(
    offsetPx: Float,
    velocityPxPerSecond: Float,
    viewportHeightPx: Float
): Boolean {
    if (!viewportHeightPx.isFinite() || viewportHeightPx <= 0f) return false
    return abs(offsetPx) >= viewportHeightPx * COMPAT_VIEWER_DISMISS_DISTANCE_FRACTION ||
        abs(velocityPxPerSecond) >= viewportHeightPx * COMPAT_VIEWER_DISMISS_VELOCITY_MULTIPLIER
}

internal fun clampCompatViewerZoomOffset(
    proposedPx: Float,
    viewportExtentPx: Float,
    scale: Float
): Float {
    if (!proposedPx.isFinite() || !viewportExtentPx.isFinite() || viewportExtentPx <= 0f) return 0f
    val maximum = (viewportExtentPx * (scale.coerceAtLeast(1f) - 1f) / 2f).coerceAtLeast(0f)
    return proposedPx.coerceIn(-maximum, maximum)
}
