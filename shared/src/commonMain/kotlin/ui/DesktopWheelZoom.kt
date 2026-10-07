package com.valoser.futacha.shared.ui

import kotlin.math.pow

/** One wheel notch zooms by this factor (Ctrl + wheel and the plain wheel behave alike). */
internal const val DESKTOP_WHEEL_ZOOM_STEP = 1.1f
private const val DESKTOP_WHEEL_MAX_NOTCHES = 10f

internal data class DesktopWheelZoomResult(
    val scale: Float,
    val translationX: Float,
    val translationY: Float
)

/** Scrolling up (negative delta) zooms in. A trackpad's fractional deltas zoom proportionally. */
internal fun desktopWheelZoomFactor(scrollDeltaY: Float): Float {
    if (!scrollDeltaY.isFinite() || scrollDeltaY == 0f) return 1f
    val notches = scrollDeltaY.coerceIn(-DESKTOP_WHEEL_MAX_NOTCHES, DESKTOP_WHEEL_MAX_NOTCHES)
    return DESKTOP_WHEEL_ZOOM_STEP.pow(-notches)
}

/**
 * The transform after one wheel event, keeping the content under the pointer in place. Positions
 * are relative to the viewport, the transform origin is its centre (a graphics layer's default),
 * and the translation is clamped so the image never leaves the viewport. Returns null when the
 * event changes nothing (no delta, already at the limit, or an unusable viewport).
 */
internal fun applyDesktopWheelZoom(
    scale: Float,
    translationX: Float,
    translationY: Float,
    scrollDeltaY: Float,
    pointerX: Float,
    pointerY: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    maxScale: Float,
    fitThreshold: Float
): DesktopWheelZoomResult? {
    if (!viewportWidthPx.isFinite() || !viewportHeightPx.isFinite() ||
        viewportWidthPx <= 0f || viewportHeightPx <= 0f
    ) {
        return null
    }
    val factor = desktopWheelZoomFactor(scrollDeltaY)
    if (factor == 1f) return null
    val oldScale = scale.takeIf { it.isFinite() && it >= 1f } ?: 1f
    var newScale = (oldScale * factor).coerceIn(1f, maxScale)
    if (newScale <= fitThreshold) newScale = 1f
    if (newScale == oldScale) return null
    if (newScale == 1f) return DesktopWheelZoomResult(1f, 0f, 0f)
    val effectiveFactor = newScale / oldScale
    val fromCenterX = (if (pointerX.isFinite()) pointerX else viewportWidthPx / 2f) - viewportWidthPx / 2f
    val fromCenterY = (if (pointerY.isFinite()) pointerY else viewportHeightPx / 2f) - viewportHeightPx / 2f
    val oldX = translationX.takeIf { it.isFinite() } ?: 0f
    val oldY = translationY.takeIf { it.isFinite() } ?: 0f
    val maxX = viewportWidthPx * (newScale - 1f) / 2f
    val maxY = viewportHeightPx * (newScale - 1f) / 2f
    return DesktopWheelZoomResult(
        scale = newScale,
        translationX = (oldX * effectiveFactor + fromCenterX * (1f - effectiveFactor)).coerceIn(-maxX, maxX),
        translationY = (oldY * effectiveFactor + fromCenterY * (1f - effectiveFactor)).coerceIn(-maxY, maxY)
    )
}
