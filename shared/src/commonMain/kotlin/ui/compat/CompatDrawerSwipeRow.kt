@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.valoser.futacha.shared.ui.compat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private enum class CompatDrawerSwipePhase {
    IDLE,
    DRAGGING,
    RETURNING,
    DISMISSING,
    COLLAPSING
}

/**
 * APK-compatible row dismissal.
 *
 * The old APK does not use Material's anchored dismiss state. It starts after
 * horizontal touch slop, accepts either a half-width release or a matching
 * horizontal fling, translates/fades for 200 ms, then collapses the row for a
 * further 200 ms before mutating the drawer data.
 */
@Composable
internal fun CompatDismissibleDrawerRow(
    itemKey: Any,
    onDismissed: () -> Unit,
    enabled: Boolean = true,
    /**
     * The drawer's parent owns a leftward drag while it is open.  The APK
     * still allows a rightward row dismissal, so callers can opt into that
     * directional split instead of disabling row gestures altogether.
     */
    allowRightSwipeOnly: Boolean = false,
    content: @Composable () -> Unit
) {
    // A disabled row remains completely transparent to pointer input. This
    // is useful for surfaces where a parent owns every horizontal gesture.
    if (!enabled) {
        content()
        return
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val latestOnDismissed by rememberUpdatedState(onDismissed)
    val velocityTracker = remember(itemKey) { VelocityTracker() }
    val touchSlopPx = viewConfiguration.touchSlop
    val minFlingVelocityPx = with(density) {
        50.dp.toPx() * COMPAT_DRAWER_SWIPE_MIN_FLING_MULTIPLIER
    }
    val maxFlingVelocityPx = with(density) { 8_000.dp.toPx() }

    var rowWidthPx by remember(itemKey) { mutableFloatStateOf(0f) }
    var rowHeightPx by remember(itemKey) { mutableFloatStateOf(0f) }
    var rawDistancePx by remember(itemKey) { mutableFloatStateOf(0f) }
    var translationX by remember(itemKey) { mutableFloatStateOf(0f) }
    var swipeAlpha by remember(itemKey) { mutableFloatStateOf(1f) }
    var collapseFraction by remember(itemKey) { mutableFloatStateOf(1f) }
    var phase by remember(itemKey) { mutableStateOf(CompatDrawerSwipePhase.IDLE) }

    fun animateBack() {
        if (phase == CompatDrawerSwipePhase.RETURNING) return
        scope.launch {
            phase = CompatDrawerSwipePhase.RETURNING
            val startTranslation = translationX
            val startAlpha = swipeAlpha
            Animatable(0f).animateTo(
                targetValue = 1f,
                animationSpec = tween(COMPAT_DRAWER_SWIPE_ANIMATION_MILLIS)
            ) {
                translationX = startTranslation * (1f - value)
                swipeAlpha = startAlpha + (1f - startAlpha) * value
            }
            rawDistancePx = 0f
            swipeAlpha = 1f
            phase = CompatDrawerSwipePhase.IDLE
        }
    }

    fun animateDismiss(direction: Float) {
        if (phase == CompatDrawerSwipePhase.DISMISSING || phase == CompatDrawerSwipePhase.COLLAPSING) return
        scope.launch {
            phase = CompatDrawerSwipePhase.DISMISSING
            val startTranslation = translationX
            val startAlpha = swipeAlpha
            Animatable(0f).animateTo(
                targetValue = 1f,
                animationSpec = tween(COMPAT_DRAWER_SWIPE_ANIMATION_MILLIS)
            ) {
                translationX = startTranslation + (direction * rowWidthPx - startTranslation) * value
                swipeAlpha = startAlpha * (1f - value)
            }
            phase = CompatDrawerSwipePhase.COLLAPSING
            Animatable(1f).animateTo(
                // The old ListView listener collapses to one pixel before
                // the data mutation; collapsing straight to zero changes the
                // neighbouring-row timing and looks like a different motion.
                targetValue = 1f / rowHeightPx.coerceAtLeast(1f),
                animationSpec = tween(COMPAT_DRAWER_SWIPE_ANIMATION_MILLIS)
            ) { collapseFraction = value }
            // The APK calls the database mutation only after both animations.
            latestOnDismissed()
        }
    }

    val currentTranslationX = translationX
    val currentSwipeAlpha = swipeAlpha

    val rowModifier = Modifier
        .fillMaxWidth()
        .onSizeChanged { size ->
            if (size.width > 0) rowWidthPx = size.width.toFloat()
            if (rowHeightPx <= 0f && size.height > 0) rowHeightPx = size.height.toFloat()
        }
        .compatDrawerAnimatedHeight(rowHeightPx, collapseFraction)
        .clipToBounds()
        .pointerInput(itemKey, rowWidthPx) {
            detectCompatDrawerHorizontalDragGestures(
                touchSlopPx = touchSlopPx,
                allowRightSwipeOnly = allowRightSwipeOnly,
                onDragStart = {
                    if (phase != CompatDrawerSwipePhase.IDLE) return@detectCompatDrawerHorizontalDragGestures
                    velocityTracker.resetTracking()
                    rawDistancePx = 0f
                    translationX = 0f
                    swipeAlpha = 1f
                    phase = CompatDrawerSwipePhase.DRAGGING
                },
                onHorizontalDrag = { change, dragAmount ->
                    if (phase != CompatDrawerSwipePhase.DRAGGING) return@detectCompatDrawerHorizontalDragGestures
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    // Compose has already consumed touch slop before this
                    // callback. Add it back to retain the APK listener's raw
                    // dx for alpha and the half-width decision, while keeping
                    // the displayed translation at dx - slop.
                    rawDistancePx += dragAmount
                    if (rawDistancePx != 0f && abs(rawDistancePx) <= abs(dragAmount)) {
                        rawDistancePx += rawDistancePx.sign * touchSlopPx
                    }
                    val direction = rawDistancePx.sign
                    translationX = if (abs(rawDistancePx) > touchSlopPx && direction != 0f) {
                        rawDistancePx - direction * touchSlopPx
                    } else {
                        0f
                    }
                    swipeAlpha = compatDrawerSwipeAlpha(rawDistancePx, rowWidthPx)
                },
                onDragEnd = {
                    if (phase != CompatDrawerSwipePhase.DRAGGING) return@detectCompatDrawerHorizontalDragGestures
                    val velocity = velocityTracker.calculateVelocity()
                    val shouldDismiss = shouldDismissCompatDrawerSwipe(
                        rawDistancePx = rawDistancePx,
                        widthPx = rowWidthPx,
                        xVelocityPxPerSecond = velocity.x,
                        yVelocityPxPerSecond = velocity.y,
                        minFlingVelocityPxPerSecond = minFlingVelocityPx,
                        maxFlingVelocityPxPerSecond = maxFlingVelocityPx,
                        swiping = true
                    )
                    if (shouldDismiss && rawDistancePx != 0f) {
                        animateDismiss(rawDistancePx.sign)
                    } else {
                        animateBack()
                    }
                },
                onDragCancel = {
                    if (phase == CompatDrawerSwipePhase.DRAGGING) animateBack()
                }
            )
        }

    Box(modifier = rowModifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = currentTranslationX
                    alpha = currentSwipeAlpha
                }
        ) {
            content()
        }
    }
}

/**
 * Direction-aware variant of Compose's horizontal drag detector.
 *
 * [detectHorizontalDragGestures] consumes the touch-slop event before its
 * callback runs. That prevents the parent drawer from seeing a leftward drag
 * from a row. This detector waits until the direction is known and only then
 * consumes a rightward dismissal, leaving leftward/vertical gestures to the
 * drawer or list scroll container (#42).
 */
private suspend fun PointerInputScope.detectCompatDrawerHorizontalDragGestures(
    touchSlopPx: Float,
    allowRightSwipeOnly: Boolean,
    onDragStart: () -> Unit,
    onHorizontalDrag: (PointerInputChange, Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )
        var totalDx = 0f
        var totalDy = 0f
        var dragging = false
        var rejected = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (dragging) onDragCancel()
                break
            }
            if (!change.pressed) {
                if (dragging) onDragEnd()
                break
            }
            if (!dragging && !rejected && change.isConsumed) {
                // A parent scroll/drawer recognizer already owns this gesture.
                rejected = true
                continue
            }

            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y
            totalDx += dx
            totalDy += dy

            if (!dragging && !rejected &&
                maxOf(abs(totalDx), abs(totalDy)) >= touchSlopPx
            ) {
                val horizontal = abs(totalDx) >= abs(totalDy) * 1.25f
                if (!horizontal || (allowRightSwipeOnly && totalDx < 0f)) {
                    rejected = true
                } else {
                    dragging = true
                    onDragStart()
                }
            }
            if (dragging) {
                change.consume()
                onHorizontalDrag(change, dx)
            }
        }
    }
}

private fun Modifier.compatDrawerAnimatedHeight(
    originalHeightPx: Float,
    collapseFraction: Float
): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val baseHeight = originalHeightPx.roundToInt().takeIf { it > 0 } ?: placeable.height
        val targetHeight = (baseHeight * collapseFraction).roundToInt().coerceIn(0, placeable.height)
        layout(placeable.width, targetHeight) {
            placeable.placeRelative(0, 0)
        }
    }
)
