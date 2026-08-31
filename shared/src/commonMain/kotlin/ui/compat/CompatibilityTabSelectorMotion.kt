package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

internal const val COMPAT_SELECTOR_CLOSE_DURATION_MILLIS = 700
internal const val COMPAT_SELECTOR_CLOSE_REGION_BOTTOM = 0.9f

/**
 * Axis lock used by the compatibility thread pager.  The old implementation
 * committed a pager gesture after only a few pixels and treated a nearly
 * diagonal drag as horizontal, which made ordinary reading gestures switch
 * tabs or cancel halfway through.
 */
internal enum class CompatPagerGestureAxis { UNDECIDED, HORIZONTAL, VERTICAL, REJECTED }

internal const val COMPAT_PAGER_DIRECTION_RATIO = 1.25f

/**
 * The modal drawer owns the left-edge gesture.  A thread pager must not start
 * competing with it when several tabs are open; otherwise a swipe from the
 * edge changes to the previous tab before Material's drawer recognizer can
 * claim the gesture (GitHub issue #24).
 */
// The reference drawer starts at the physical left edge and becomes visible
// after a short first-column movement.  48dp was too wide on xxhdpi devices:
// the recognizer then began in the second catalog column and needed a visibly
// longer swipe than the legacy APK (#24, #25).
internal const val COMPAT_DRAWER_EDGE_GESTURE_WIDTH_DP = 24
internal const val COMPAT_DRAWER_SWIPE_TRIGGER_DP = 24

internal fun compatPagerShouldDeferToDrawer(
    downX: Float,
    drawerEdgeWidthPx: Float
): Boolean = downX.isFinite() &&
    drawerEdgeWidthPx.isFinite() &&
    downX >= 0f &&
    drawerEdgeWidthPx > 0f &&
    downX <= drawerEdgeWidthPx

/**
 * Material3's anchored drawer accepts a horizontal drag that starts anywhere
 * in its content.  The legacy app only begins opening from the left edge, so
 * keep that recognizer narrow and make the drawer visible after a short
 * first-column travel instead of after the second catalog column.
 */
internal fun compatDrawerSwipeShouldOpen(
    startX: Float,
    totalDx: Float,
    totalDy: Float,
    edgeWidthPx: Float,
    triggerPx: Float
): Boolean = startX.isFinite() &&
    totalDx.isFinite() &&
    totalDy.isFinite() &&
    edgeWidthPx.isFinite() &&
    triggerPx.isFinite() &&
    startX >= 0f &&
    edgeWidthPx > 0f &&
    triggerPx > 0f &&
    startX <= edgeWidthPx &&
    totalDx >= triggerPx &&
    totalDx >= kotlin.math.abs(totalDy) * COMPAT_PAGER_DIRECTION_RATIO

internal fun compatPagerGestureAxis(
    totalDx: Float,
    totalDy: Float,
    touchSlopPx: Float
): CompatPagerGestureAxis {
    val slop = touchSlopPx.coerceAtLeast(0f)
    if (maxOf(kotlin.math.abs(totalDx), kotlin.math.abs(totalDy)) < slop) {
        return CompatPagerGestureAxis.UNDECIDED
    }
    val dx = kotlin.math.abs(totalDx)
    val dy = kotlin.math.abs(totalDy)
    return when {
        dx >= dy * COMPAT_PAGER_DIRECTION_RATIO -> CompatPagerGestureAxis.HORIZONTAL
        dy >= dx * COMPAT_PAGER_DIRECTION_RATIO -> CompatPagerGestureAxis.VERTICAL
        // A diagonal gesture must not leak into either the pager or the
        // thread's vertical refresh/scroll recognizer (#31).
        else -> CompatPagerGestureAxis.REJECTED
    }
}

internal data class CompatSelectorCloseTransform(
    val scale: Float,
    val rotationDegrees: Float
)

internal data class CompatSelectorPreviewOffset(val x: Int, val y: Int)

/**
 * The drag preview uses root/window coordinates. Popup's alignment overload is
 * anchored to the selector row, which adds the row's Y position a second time
 * and pushes the preview behind the navigation bar. Pin the popup surface to
 * the window origin so its shadow and preview share the same coordinate space.
 */
internal object CompatSelectorWindowPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
}

internal fun compatSelectorPreviewOffset(
    itemLeftInRoot: Float,
    pointerYInRoot: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    previewWidth: Int,
    previewHeight: Int
): CompatSelectorPreviewOffset {
    val maxX = (viewportWidth - previewWidth).coerceAtLeast(0)
    val maxY = (viewportHeight - previewHeight).coerceAtLeast(0)
    return CompatSelectorPreviewOffset(
        x = itemLeftInRoot.toInt().coerceIn(0, maxX),
        y = pointerYInRoot.toInt().coerceIn(0, maxY)
    )
}

/**
 * Mirrors setupDrag(RectF(0, 0, 1, .9)) in the target. The vertical coordinate is
 * divided by the selector item's top-on-screen rather than the display height.
 */
internal fun isCompatSelectorCloseDrop(
    screenX: Float,
    screenY: Float,
    itemTopOnScreen: Float,
    displayWidth: Float,
    minimumTravelPx: Float = 0f
): Boolean = itemTopOnScreen > 0f &&
    screenX >= 0f && screenX <= displayWidth &&
    screenY >= 0f && screenY / itemTopOnScreen < COMPAT_SELECTOR_CLOSE_REGION_BOTTOM
    && (minimumTravelPx <= 0f || screenY <= itemTopOnScreen - minimumTravelPx)

/** Target CanvasBasicView alphaAdd; the base black alpha is applied by the renderer. */
internal fun compatSelectorShadowAlphaAdd(
    screenY: Float,
    itemTopOnScreen: Float
): Float {
    if (itemTopOnScreen <= 0f) return 0f
    val normalized = (screenY / itemTopOnScreen).coerceIn(0f, 1f)
    return (1f - ((normalized - COMPAT_SELECTOR_CLOSE_REGION_BOTTOM) /
        (1f - COMPAT_SELECTOR_CLOSE_REGION_BOTTOM))).coerceIn(0f, 1f)
}

internal fun compatSelectorCloseTransform(progress: Float): CompatSelectorCloseTransform {
    val value = progress.coerceIn(0f, 1f)
    return CompatSelectorCloseTransform(
        scale = 1.1f + (0.1f - 1.1f) * value,
        rotationDegrees = -270f * value
    )
}
