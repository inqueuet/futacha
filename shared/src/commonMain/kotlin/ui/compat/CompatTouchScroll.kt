package com.valoser.futacha.shared.ui.compat

internal enum class CompatTouchScrollAction {
    PAGE_UP,
    NONE,
    PAGE_DOWN
}

/**
 * The legacy ThreadList touch-scroll option uses the upper/lower screen
 * thirds as page-up/page-down hit areas.  The center remains a normal blank
 * area so it cannot unexpectedly move the thread while reading.
 */
internal fun compatTouchScrollAction(yPx: Float, heightPx: Float): CompatTouchScrollAction {
    if (heightPx <= 0f || yPx < 0f || yPx > heightPx) return CompatTouchScrollAction.NONE
    return when {
        yPx < heightPx / 3f -> CompatTouchScrollAction.PAGE_UP
        yPx > heightPx * 2f / 3f -> CompatTouchScrollAction.PAGE_DOWN
        else -> CompatTouchScrollAction.NONE
    }
}
