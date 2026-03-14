package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal data class EdgeSwipeRefreshDragState(
    val totalDrag: Float,
    val overscrollTarget: Float,
    val shouldConsume: Boolean
)

internal data class EdgeSwipeRefreshReleaseState(
    val totalDrag: Float,
    val overscrollTarget: Float,
    val consumedDrag: Float
)

internal data class EdgeSwipeRefreshMetrics(
    val maxOverscrollPx: Float,
    val refreshTriggerPx: Float
)

internal data class ScrollEdgeState(
    val isAtTop: Boolean,
    val isAtBottom: Boolean
)

internal data class EdgeSwipeRefreshBinding(
    val edgeState: ScrollEdgeState,
    val metrics: EdgeSwipeRefreshMetrics,
    val visualState: EdgeSwipeRefreshVisualState
)

internal class EdgeSwipeRefreshVisualState(
    val overscrollOffset: Float,
    private val setOverscrollTarget: (Float) -> Unit
) {
    fun onOverscrollTargetChanged(value: Float) {
        setOverscrollTarget(value)
    }
}

internal fun resolveScrollEdgeState(
    canScrollBackward: Boolean,
    canScrollForward: Boolean
): ScrollEdgeState {
    return ScrollEdgeState(
        isAtTop = !canScrollBackward,
        isAtBottom = !canScrollForward
    )
}

@Composable
internal fun rememberEdgeSwipeRefreshMetrics(): EdgeSwipeRefreshMetrics {
    val density = LocalDensity.current
    return remember(density) {
        EdgeSwipeRefreshMetrics(
            maxOverscrollPx = with(density) { 64.dp.toPx() },
            refreshTriggerPx = with(density) { 56.dp.toPx() }
        )
    }
}

@Composable
internal fun rememberScrollEdgeState(listState: LazyListState): ScrollEdgeState {
    val isAtTop by remember(listState) {
        derivedStateOf { !listState.canScrollBackward }
    }
    val isAtBottom by remember(listState) {
        derivedStateOf { !listState.canScrollForward }
    }
    return remember(isAtTop, isAtBottom) {
        resolveScrollEdgeState(
            canScrollBackward = !isAtTop,
            canScrollForward = !isAtBottom
        )
    }
}

@Composable
internal fun rememberScrollEdgeState(gridState: LazyGridState): ScrollEdgeState {
    val isAtTop by remember(gridState) {
        derivedStateOf { !gridState.canScrollBackward }
    }
    val isAtBottom by remember(gridState) {
        derivedStateOf { !gridState.canScrollForward }
    }
    return remember(isAtTop, isAtBottom) {
        resolveScrollEdgeState(
            canScrollBackward = !isAtTop,
            canScrollForward = !isAtBottom
        )
    }
}

@Composable
internal fun rememberEdgeSwipeRefreshVisualState(
    isRefreshing: Boolean,
    animationLabel: String
): EdgeSwipeRefreshVisualState {
    var overscrollTarget by remember { mutableFloatStateOf(0f) }
    val overscrollOffset by animateFloatAsState(
        targetValue = overscrollTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = animationLabel
    )

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollTarget = 0f
        }
    }

    return remember(overscrollOffset) {
        EdgeSwipeRefreshVisualState(
            overscrollOffset = overscrollOffset,
            setOverscrollTarget = { overscrollTarget = it }
        )
    }
}

@Composable
internal fun rememberEdgeSwipeRefreshBinding(
    listState: LazyListState,
    isRefreshing: Boolean,
    animationLabel: String
): EdgeSwipeRefreshBinding {
    val edgeState = rememberScrollEdgeState(listState)
    val metrics = rememberEdgeSwipeRefreshMetrics()
    val visualState = rememberEdgeSwipeRefreshVisualState(
        isRefreshing = isRefreshing,
        animationLabel = animationLabel
    )
    return remember(edgeState, metrics, visualState) {
        EdgeSwipeRefreshBinding(
            edgeState = edgeState,
            metrics = metrics,
            visualState = visualState
        )
    }
}

@Composable
internal fun rememberEdgeSwipeRefreshBinding(
    gridState: LazyGridState,
    isRefreshing: Boolean,
    animationLabel: String
): EdgeSwipeRefreshBinding {
    val edgeState = rememberScrollEdgeState(gridState)
    val metrics = rememberEdgeSwipeRefreshMetrics()
    val visualState = rememberEdgeSwipeRefreshVisualState(
        isRefreshing = isRefreshing,
        animationLabel = animationLabel
    )
    return remember(edgeState, metrics, visualState) {
        EdgeSwipeRefreshBinding(
            edgeState = edgeState,
            metrics = metrics,
            visualState = visualState
        )
    }
}

internal fun updateEdgeSwipeRefreshDragState(
    totalDrag: Float,
    dragAmount: Float,
    isRefreshing: Boolean,
    isAtTop: Boolean,
    isAtBottom: Boolean,
    maxOverscrollPx: Float,
    overscrollMultiplier: Float = 0.4f
): EdgeSwipeRefreshDragState {
    if (isRefreshing) {
        return EdgeSwipeRefreshDragState(
            totalDrag = totalDrag,
            overscrollTarget = 0f,
            shouldConsume = false
        )
    }
    return when {
        isAtTop && dragAmount > 0f -> {
            val updatedTotalDrag = totalDrag + dragAmount
            EdgeSwipeRefreshDragState(
                totalDrag = updatedTotalDrag,
                overscrollTarget = (updatedTotalDrag * overscrollMultiplier).coerceIn(0f, maxOverscrollPx),
                shouldConsume = true
            )
        }

        isAtBottom && dragAmount < 0f -> {
            val updatedTotalDrag = totalDrag + dragAmount
            EdgeSwipeRefreshDragState(
                totalDrag = updatedTotalDrag,
                overscrollTarget = (updatedTotalDrag * overscrollMultiplier).coerceIn(-maxOverscrollPx, 0f),
                shouldConsume = true
            )
        }

        else -> EdgeSwipeRefreshDragState(
            totalDrag = totalDrag,
            overscrollTarget = (totalDrag * overscrollMultiplier).coerceIn(-maxOverscrollPx, maxOverscrollPx),
            shouldConsume = false
        )
    }
}

internal fun shouldTriggerEdgeSwipeRefresh(
    totalDrag: Float,
    refreshTriggerPx: Float
): Boolean {
    return abs(totalDrag) > refreshTriggerPx
}

internal fun releaseEdgeSwipeRefreshDrag(
    totalDrag: Float,
    dragAmount: Float,
    maxOverscrollPx: Float,
    overscrollMultiplier: Float = 0.4f
): EdgeSwipeRefreshReleaseState {
    if (totalDrag == 0f || dragAmount == 0f || totalDrag * dragAmount >= 0f) {
        return EdgeSwipeRefreshReleaseState(
            totalDrag = totalDrag,
            overscrollTarget = (totalDrag * overscrollMultiplier).coerceIn(-maxOverscrollPx, maxOverscrollPx),
            consumedDrag = 0f
        )
    }

    val consumedDrag = if (abs(dragAmount) <= abs(totalDrag)) {
        dragAmount
    } else {
        -totalDrag
    }
    val updatedTotalDrag = totalDrag + consumedDrag
    return EdgeSwipeRefreshReleaseState(
        totalDrag = updatedTotalDrag,
        overscrollTarget = (updatedTotalDrag * overscrollMultiplier).coerceIn(-maxOverscrollPx, maxOverscrollPx),
        consumedDrag = consumedDrag
    )
}

internal fun Modifier.edgeSwipeRefresh(
    isRefreshing: Boolean,
    isAtTop: Boolean,
    isAtBottom: Boolean,
    maxOverscrollPx: Float,
    refreshTriggerPx: Float,
    onOverscrollTargetChanged: (Float) -> Unit,
    onRefresh: () -> Unit
): Modifier = composed {
    val latestIsRefreshing by rememberUpdatedState(isRefreshing)
    val latestIsAtTop by rememberUpdatedState(isAtTop)
    val latestIsAtBottom by rememberUpdatedState(isAtBottom)
    val latestMaxOverscrollPx by rememberUpdatedState(maxOverscrollPx)
    val latestRefreshTriggerPx by rememberUpdatedState(refreshTriggerPx)
    val latestOnOverscrollTargetChanged by rememberUpdatedState(onOverscrollTargetChanged)
    val latestOnRefresh by rememberUpdatedState(onRefresh)

    var totalDrag by remember { mutableFloatStateOf(0f) }

    fun finishGesture(triggerRefresh: Boolean) {
        val shouldRefresh = triggerRefresh &&
            !latestIsRefreshing &&
            shouldTriggerEdgeSwipeRefresh(totalDrag, latestRefreshTriggerPx)
        totalDrag = 0f
        latestOnOverscrollTargetChanged(0f)
        if (shouldRefresh) {
            latestOnRefresh()
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                val releaseState = releaseEdgeSwipeRefreshDrag(
                    totalDrag = totalDrag,
                    dragAmount = available.y,
                    maxOverscrollPx = latestMaxOverscrollPx
                )
                if (releaseState.consumedDrag == 0f) {
                    return Offset.Zero
                }
                totalDrag = releaseState.totalDrag
                latestOnOverscrollTargetChanged(releaseState.overscrollTarget)
                return Offset(x = 0f, y = releaseState.consumedDrag)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                val updatedState = updateEdgeSwipeRefreshDragState(
                    totalDrag = totalDrag,
                    dragAmount = available.y,
                    isRefreshing = latestIsRefreshing,
                    isAtTop = latestIsAtTop,
                    isAtBottom = latestIsAtBottom,
                    maxOverscrollPx = latestMaxOverscrollPx
                )
                totalDrag = updatedState.totalDrag
                latestOnOverscrollTargetChanged(updatedState.overscrollTarget)
                return if (updatedState.shouldConsume) {
                    Offset(x = 0f, y = available.y)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                finishGesture(triggerRefresh = true)
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(nestedScrollConnection)
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation()
                finishGesture(triggerRefresh = up != null)
            }
        }
}
