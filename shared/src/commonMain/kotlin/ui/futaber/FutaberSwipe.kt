package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A horizontal swipe on the thread or the catalog, as the original does it (checked on the device, 2026-10-27,
 * docs/thread-feedback-1444136530-2026-10-27.md): a swipe to the right started at the left edge opens the board list,
 * one started further in goes back (thread → catalog), and a swipe to the left goes forward (thread → writing,
 * catalog → the last thread).
 */
internal enum class FutaberSwipe { EdgeRight, Right, Left }

/** The original switched between the board list and going back between 14pt and 22pt from the edge. */
internal const val FUTABER_SWIPE_EDGE_DP = 20f
internal const val FUTABER_SWIPE_TRIGGER_DP = 56f

/** Null until the finger has gone far enough sideways; a move that is mostly vertical never becomes a swipe. */
internal fun futaberSwipeDecision(startX: Float, dx: Float, dy: Float, edgePx: Float, triggerPx: Float): FutaberSwipe? = when {
    abs(dx) < triggerPx || abs(dy) * 1.5f > abs(dx) -> null
    dx > 0f && startX <= edgePx -> FutaberSwipe.EdgeRight
    dx > 0f -> FutaberSwipe.Right
    else -> FutaberSwipe.Left
}

/** Once the move is clearly vertical the list scrolls, and the gesture is left to it. */
internal fun futaberSwipeIsVertical(dx: Float, dy: Float, slopPx: Float): Boolean = abs(dy) > slopPx && abs(dy) > abs(dx)

/**
 * Watches the drags of what it is put on without taking them: taps, long presses and the list's vertical scroll work
 * as before. Only a swipe that [futaberSwipeDecision] accepts is taken (and the rest of that touch with it).
 */
@Composable
internal fun rememberFutaberSwipeModifier(enabled: Boolean, onSwipe: (FutaberSwipe) -> Unit): Modifier {
    if (!enabled) return Modifier
    val latest by rememberUpdatedState(onSwipe)
    return Modifier.pointerInput(Unit) {
        val edgePx = FUTABER_SWIPE_EDGE_DP.dp.toPx()
        val triggerPx = FUTABER_SWIPE_TRIGGER_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // A second finger is a pinch, not a swipe.
                if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                if (futaberSwipeIsVertical(dx, dy, viewConfiguration.touchSlop)) return@awaitEachGesture
                val swipe = futaberSwipeDecision(down.position.x, dx, dy, edgePx, triggerPx) ?: continue
                change.consume()
                latest(swipe)
                // The rest of this touch is the swipe's: the row under the finger must not take it as a tap.
                while (true) {
                    val rest = awaitPointerEvent(PointerEventPass.Initial)
                    rest.changes.forEach { it.consume() }
                    if (rest.changes.none { it.pressed }) return@awaitEachGesture
                }
            }
        }
    }
}
