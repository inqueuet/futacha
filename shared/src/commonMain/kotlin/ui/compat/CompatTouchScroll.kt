package com.valoser.futacha.shared.ui.compat

import kotlinx.coroutines.launch

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

/** タップかどうか：指が動いた距離がタッチの遊び（touchSlop）を超えたらドラッグ。参考アプリ は10px。 */
internal fun compatTouchScrollMoved(offset: androidx.compose.ui.geometry.Offset, touchSlopPx: Float): Boolean =
    offset.getDistance() > touchSlopPx

/**
 * Page-by-tap scrolling that adds up quick taps: a second tap while the first page is still animating continues from
 * the distance left, instead of cancelling it (two quick taps moved about 1.3 pages; audit 2026-11-17 L2).
 */
internal class CompatTapPager {
    var remaining = 0f
        private set
    private var job: kotlinx.coroutines.Job? = null
    private var generation = 0L

    fun page(scope: kotlinx.coroutines.CoroutineScope, state: androidx.compose.foundation.gestures.ScrollableState, delta: Float) {
        remaining += delta
        val turn = ++generation
        job?.cancel()
        job = scope.launch {
            try {
                val target = remaining
                var previous = 0f
                state.scroll {
                    androidx.compose.animation.core.animate(0f, target) { value, _ ->
                        val used = scrollBy(value - previous)
                        previous = value
                        remaining -= used
                    }
                }
            } finally {
                // A replacement tap inherits the unfinished distance; a drag/cancel discards it.
                if (turn == generation) remaining = 0f
            }
        }
    }
}
