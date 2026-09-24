package com.valoser.futacha.shared.ui.compat

import kotlin.math.abs

/** The target APK's SwipeDismiss helper uses the same 200 ms value for both phases. */
internal const val COMPAT_DRAWER_SWIPE_ANIMATION_MILLIS = 200
internal const val COMPAT_DRAWER_SWIPE_DISTANCE_FRACTION = 0.5f
internal const val COMPAT_DRAWER_SWIPE_MIN_FLING_MULTIPLIER = 16f
internal const val COMPAT_DRAWER_SCRIM_MAX_ALPHA = 0.32f

/** One scrim is shared by the preview and settled drawer states (#38). */
internal fun compatDrawerScrimAlpha(visibleFraction: Float): Float =
    COMPAT_DRAWER_SCRIM_MAX_ALPHA * visibleFraction.coerceIn(0f, 1f)

internal fun compatDrawerSwipeAlpha(rawDistancePx: Float, widthPx: Float): Float {
    if (!rawDistancePx.isFinite() || !widthPx.isFinite() || widthPx <= 0f) return 1f
    return (1f - (abs(rawDistancePx) * 2f / widthPx)).coerceIn(0f, 1f)
}

internal fun shouldDismissCompatDrawerSwipe(
    rawDistancePx: Float,
    widthPx: Float,
    xVelocityPxPerSecond: Float,
    yVelocityPxPerSecond: Float,
    minFlingVelocityPxPerSecond: Float,
    maxFlingVelocityPxPerSecond: Float,
    swiping: Boolean
): Boolean {
    if (!swiping || !rawDistancePx.isFinite() || !widthPx.isFinite() || widthPx <= 0f) return false
    if (abs(rawDistancePx) > widthPx * COMPAT_DRAWER_SWIPE_DISTANCE_FRACTION) return true

    val absoluteXVelocity = abs(xVelocityPxPerSecond)
    val absoluteYVelocity = abs(yVelocityPxPerSecond)
    val directionMatches = rawDistancePx != 0f &&
        xVelocityPxPerSecond != 0f &&
        rawDistancePx * xVelocityPxPerSecond > 0f
    return minFlingVelocityPxPerSecond <= absoluteXVelocity &&
        absoluteXVelocity <= maxFlingVelocityPxPerSecond &&
        absoluteYVelocity < absoluteXVelocity &&
        directionMatches
}

/**
 * Visible drawer width for the scrim. Material's open anchor is 0 and its
 * closed anchor is the negative drawer width, so deriving the scrim from the
 * same offset keeps the dimmed area attached to the moving sheet during both
 * open and close animations (#38). An edge-swipe preview wins while closed.
 */
internal fun compatDrawerVisibleWidthPx(
    previewOffsetPx: Float,
    isClosed: Boolean,
    isOpen: Boolean,
    currentOffset: Float,
    widthPx: Float
): Float = when {
    previewOffsetPx > 0f && isClosed -> previewOffsetPx
    !currentOffset.isNaN() -> (widthPx + currentOffset).coerceIn(0f, widthPx)
    isOpen -> widthPx
    else -> 0f
}
