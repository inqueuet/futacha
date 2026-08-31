package com.valoser.futacha.shared.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import kotlin.math.max
import kotlin.math.sqrt

private const val DRAG_DISTANCE_PX = 24f
private const val LONG_PRESS_DURATION_MILLIS = 500L

/**
 * Captures a single compact audit event for each touch gesture. It runs in the
 * Initial pointer pass, so child buttons and dialogs remain fully functional.
 */
internal fun Modifier.analyticsGestureSurface(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val start = down.position
        val startedAtMillis = down.uptimeMillis
        var end = start
        var endedAtMillis = startedAtMillis
        var maxDistanceSquared = 0f
        var peakPointerCount = 1
        var isPressed = true
        while (isPressed) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            peakPointerCount = max(peakPointerCount, event.changes.count { it.pressed })
            val activeChange = event.changes.firstOrNull { it.id == down.id }
            if (activeChange == null) {
                isPressed = false
            } else {
                val deltaX = activeChange.position.x - start.x
                val deltaY = activeChange.position.y - start.y
                maxDistanceSquared = max(maxDistanceSquared, deltaX * deltaX + deltaY * deltaY)
                end = activeChange.position
                endedAtMillis = activeChange.uptimeMillis
                isPressed = activeChange.pressed
            }
        }
        AnalyticsTracker.uiGesture(
            gesture = when {
                sqrt(maxDistanceSquared) >= DRAG_DISTANCE_PX -> "drag"
                endedAtMillis - startedAtMillis >= LONG_PRESS_DURATION_MILLIS -> "long_press"
                else -> "tap"
            },
            area = resolveAnalyticsTouchArea(start.x, start.y, size),
            xBucket = resolveAnalyticsPositionBucket(start.x, size.width),
            yBucket = resolveAnalyticsPositionBucket(start.y, size.height),
            direction = resolveAnalyticsDragDirection(start.x, start.y, end.x, end.y),
            durationMillis = (endedAtMillis - startedAtMillis).coerceAtLeast(0L),
            pointerCount = peakPointerCount
        )
    }
}

private fun resolveAnalyticsPositionBucket(value: Float, dimension: Int): String {
    if (dimension <= 0) return "unknown"
    return ((value / dimension.toFloat()) * 10f).toInt().coerceIn(0, 9).toString()
}

private fun resolveAnalyticsDragDirection(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float
): String {
    val deltaX = endX - startX
    val deltaY = endY - startY
    if (maxOf(kotlin.math.abs(deltaX), kotlin.math.abs(deltaY)) < DRAG_DISTANCE_PX) return "none"
    return when {
        kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.5f -> if (deltaX > 0f) "right" else "left"
        kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) * 1.5f -> if (deltaY > 0f) "down" else "up"
        else -> "diagonal"
    }
}

private fun resolveAnalyticsTouchArea(x: Float, y: Float, size: IntSize): String {
    if (size.width <= 0 || size.height <= 0) return "unknown"
    val horizontal = when {
        x < size.width / 3f -> "left"
        x < size.width * 2f / 3f -> "center"
        else -> "right"
    }
    val vertical = when {
        y < size.height / 3f -> "top"
        y < size.height * 2f / 3f -> "middle"
        else -> "bottom"
    }
    return "${vertical}_${horizontal}"
}
