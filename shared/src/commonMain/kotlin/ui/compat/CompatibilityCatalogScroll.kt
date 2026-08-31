package com.valoser.futacha.shared.ui.compat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.CatalogFetchSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val COMPAT_PULL_FRICTION = 2f
// The legacy refresh gesture has a deliberate dead zone.  A 48dp header as
// the trigger made a light touch at the end of a short thread refresh it.
// A short/diagonal reading drag must not refresh a board. Keep this larger
// than the visual header so the release gesture is intentional on short lists.
private val COMPAT_PULL_MIN_TRIGGER = 96.dp
private const val COMPAT_PULL_MAX_DISPLAY_MULTIPLIER = 1.5f
private const val COMPAT_PULL_SETTLE_MILLIS = 200
private const val COMPAT_PULL_LOADING_ROTATION_MILLIS = 1_200
private const val COMPAT_FAST_SCROLL_HIDE_DELAY_MILLIS = 1_500L
private const val COMPAT_FAST_SCROLL_FADE_MILLIS = 500
private val CompatPullSettleEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

internal enum class CompatPullLabel {
    PULL,
    RELEASE,
    LOADING
}

internal fun compatPullLabel(offsetPx: Float, thresholdPx: Float, refreshing: Boolean): CompatPullLabel = when {
    refreshing -> CompatPullLabel.LOADING
    abs(offsetPx) >= thresholdPx -> CompatPullLabel.RELEASE
    else -> CompatPullLabel.PULL
}

internal fun shouldTriggerCompatPullRefresh(offsetPx: Float, thresholdPx: Float): Boolean =
    thresholdPx > 0f && abs(offsetPx) >= thresholdPx

/**
 * Updates the raw pull distance while consuming a reversal only until the
 * gesture reaches neutral. Keeping this reducer shared by nested-scroll and
 * pointer fallback paths prevents the latter from getting stuck above the
 * refresh threshold (#45).
 */
internal data class CompatPullDragUpdate(
    val totalDrag: Float,
    val consumedDrag: Float
)

internal fun updateCompatPullDrag(
    totalDrag: Float,
    dragAmount: Float,
    maxAbsDrag: Float
): CompatPullDragUpdate {
    val maximum = abs(maxAbsDrag)
    if (dragAmount == 0f || maximum == 0f) {
        return CompatPullDragUpdate(totalDrag, 0f)
    }
    if (totalDrag == 0f || totalDrag * dragAmount >= 0f) {
        val updated = (totalDrag + dragAmount).coerceIn(-maximum, maximum)
        return CompatPullDragUpdate(updated, updated - totalDrag)
    }
    val consumed = if (abs(dragAmount) <= abs(totalDrag)) dragAmount else -totalDrag
    return CompatPullDragUpdate(totalDrag + consumed, consumed)
}

/**
 * Progress of the pull header itself, rather than progress towards refresh.
 * The legacy UI reveals the hint as the finger moves; it must not be fully
 * visible for a one-pixel overscroll.
 */
internal fun compatPullVisualProgress(offsetPx: Float, headerHeightPx: Float): Float =
    if (headerHeightPx <= 0f) 0f else (abs(offsetPx) / headerHeightPx).coerceIn(0f, 1f)

/**
 * The content follows the finger by the actual overscroll amount.  Returning
 * a fixed header height here causes a sudden 48dp jump on the first pixel.
 */
internal fun compatPullContentOffsetPx(offsetPx: Float): Float = offsetPx

/**
 * Pull-to-refresh must only own a clearly vertical gesture.  Without an axis
 * lock, the vertical component of a diagonal pager/read gesture can reach the
 * list edge and refresh the board even though the user is moving sideways.
 */
internal enum class CompatPullGestureAxis { UNDECIDED, VERTICAL, REJECTED }

internal const val COMPAT_PULL_DIRECTION_RATIO = 1.25f

internal fun compatPullGestureAxis(
    totalDx: Float,
    totalDy: Float,
    touchSlopPx: Float
): CompatPullGestureAxis {
    val slop = touchSlopPx.coerceAtLeast(0f)
    val dx = abs(totalDx)
    val dy = abs(totalDy)
    if (maxOf(dx, dy) < slop) return CompatPullGestureAxis.UNDECIDED
    return if (dy >= dx * COMPAT_PULL_DIRECTION_RATIO) {
        CompatPullGestureAxis.VERTICAL
    } else {
        CompatPullGestureAxis.REJECTED
    }
}

/** A completed catalog refresh is a new snapshot, so the legacy screen starts at its first row. */
internal fun shouldScrollCompatCatalogToTopAfterRefresh(refreshSucceeded: Boolean): Boolean =
    refreshSucceeded

/**
 * The reference APK sends catalogThreadSize as `${size / 25}x25x256x0x1`.
 * The reference APK uses integer division here, not a ceiling. Keep the
 * exact wire value so the selected size is not silently changed by a mode
 * switch or background refresh (#17).
 */
internal fun compatCatalogFetchSettings(threadCount: Int): CatalogFetchSettings {
    val normalizedCount = threadCount.coerceIn(50, 3_000)
    val columns = (normalizedCount / 25).coerceIn(2, 120)
    return CatalogFetchSettings(
        columns = columns,
        rows = 25,
        titleLines = 256,
        showVisitedHistory = true
    ).normalized()
}

internal fun compatFastScrollbarTarget(
    totalItems: Int,
    trackHeightPx: Int,
    y: Float
): Int {
    val height = trackHeightPx.coerceAtLeast(1)
    val fraction = (y / height).coerceIn(0f, 1f)
    return ((totalItems - 1) * fraction).roundToInt().coerceIn(0, (totalItems - 1).coerceAtLeast(0))
}

internal fun compatFastScrollbarDragTarget(
    firstVisibleItemIndex: Int,
    totalItems: Int,
    visibleItemCount: Int,
    trackHeightPx: Int,
    dragDeltaY: Float
): Int {
    val maxFirstIndex = (totalItems - visibleItemCount).coerceAtLeast(0)
    if (maxFirstIndex == 0) return 0
    val thumbFraction = (visibleItemCount.toFloat() / totalItems.coerceAtLeast(1)).coerceIn(0.06f, 1f)
    val travelPx = (trackHeightPx.coerceAtLeast(1) * (1f - thumbFraction)).coerceAtLeast(1f)
    val deltaItems = (dragDeltaY / travelPx * maxFirstIndex).roundToInt()
    return (firstVisibleItemIndex + deltaItems).coerceIn(0, maxFirstIndex)
}

@Composable
internal fun CompatBidirectionalPullRefresh(
    enabled: Boolean,
    refreshing: Boolean,
    canScrollBackward: () -> Boolean,
    canScrollForward: () -> Boolean,
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "compat-pull-refresh",
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val latestEnabled by rememberUpdatedState(enabled)
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestCanScrollBackward by rememberUpdatedState(canScrollBackward)
    val latestCanScrollForward by rememberUpdatedState(canScrollForward)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val minimumTriggerPx = with(LocalDensity.current) { COMPAT_PULL_MIN_TRIGGER.toPx() }
    val maximumDisplayOffsetPx = minimumTriggerPx * COMPAT_PULL_MAX_DISPLAY_MULTIPLIER
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    var totalRawDrag by remember { mutableFloatStateOf(0f) }
    var displayOffset by remember { mutableFloatStateOf(0f) }
    var pointerGestureActive by remember { mutableStateOf(false) }
    var gestureDx by remember { mutableFloatStateOf(0f) }
    var gestureDy by remember { mutableFloatStateOf(0f) }
    var gestureAxis by remember { mutableStateOf(CompatPullGestureAxis.UNDECIDED) }
    // `refreshing` also covers the initial catalog/thread load.  The APK's
    // pull hint is gesture-owned, so an ordinary load must not expose it.
    var pullRefreshActive by remember { mutableStateOf(false) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val loadingTransition = rememberInfiniteTransition(label = "compat-pull-loading")
    val loadingRotation by loadingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 720f,
        animationSpec = infiniteRepeatable(
            animation = tween(COMPAT_PULL_LOADING_ROTATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "compat-pull-loading-rotation"
    )

    suspend fun animateOffset(start: Float, target: Float) {
        animate(
            initialValue = start,
            targetValue = target,
            animationSpec = tween(COMPAT_PULL_SETTLE_MILLIS, easing = CompatPullSettleEasing)
        ) { value, _ -> displayOffset = value }
    }

    fun animateOffsetTo(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch { animateOffset(displayOffset, target) }
    }

    fun resetPullState() {
        totalRawDrag = 0f
        displayOffset = 0f
        pullRefreshActive = false
        gestureDx = 0f
        gestureDy = 0f
        gestureAxis = CompatPullGestureAxis.UNDECIDED
    }

    fun recordGestureDelta(dx: Float, dy: Float) {
        if (gestureAxis != CompatPullGestureAxis.UNDECIDED) return
        gestureDx += dx
        gestureDy += dy
        gestureAxis = compatPullGestureAxis(gestureDx, gestureDy, touchSlopPx)
    }

    fun finishGesture() {
        // A nested-scroll child can consume the final delta before the
        // pointer observer sees ACTION_UP.  In that case raw drag is already
        // zero while the visual offset may still be non-zero; returning here
        // was the source of the intermittent stuck "画面を引っ張って…"
        // overlay.  Always clear a stale visual state on gesture end.
        if (totalRawDrag == 0f) {
            // ACTION_UP can be consumed by LazyColumn/LazyGrid before the
            // nested-scroll observer sees the last delta.  Never leave the
            // visual hint armed in that case; a real refresh is represented
            // by `refreshing` and will redraw the loading state separately.
            if (displayOffset != 0f) {
                settleJob?.cancel()
                resetPullState()
            }
            return
        }
        if (pullRefreshActive || latestRefreshing) return
        val threshold = maxOf(headerHeightPx.toFloat(), minimumTriggerPx)
        val direction = if (displayOffset >= 0f) 1f else -1f
        val trigger = latestEnabled && !latestRefreshing &&
            shouldTriggerCompatPullRefresh(displayOffset, threshold)
        totalRawDrag = 0f
        if (trigger) {
            pullRefreshActive = true
            settleJob?.cancel()
            settleJob = scope.launch {
                try {
                    animateOffset(displayOffset, direction * threshold)
                    latestOnRefresh()
                    animateOffset(displayOffset, 0f)
                } finally {
                    // The APK removes the pull hint after the refresh callback,
                    // including timeout/cancellation paths.  Leaving the last
                    // drag offset here makes "画面を引っ張って…" remain on screen.
                    resetPullState()
                }
            }
        } else {
            settleJob?.cancel()
            // Dismiss the hint at ACTION_UP immediately. A nested-scroll
            // child may otherwise keep the old APK's release text visible
            // during the settle animation.
            resetPullState()
        }
    }

    LaunchedEffect(refreshing) {
        if (!refreshing) {
            // A nested-scroll child can consume ACTION_UP before the pointer
            // observer sees it (especially at a LazyColumn edge). Completion
            // of the refresh is the authoritative reset for both directions.
            settleJob?.cancel()
            resetPullState()
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            settleJob?.cancel()
            resetPullState()
        }
    }

    LaunchedEffect(displayOffset, pullRefreshActive, refreshing, pointerGestureActive) {
        // LazyColumn/LazyGrid may consume the final pointer event. If that
        // happens the release callback above is never delivered and the
        // threshold label remains painted indefinitely. A quiet visual state
        // is safe to dismiss after a short grace period; an active refresh is
        // excluded because its lifecycle is owned by `refreshing`.
        if (!pointerGestureActive && !pullRefreshActive && !refreshing && abs(displayOffset) > 0.5f) {
            delay(350)
            if (!pointerGestureActive && !pullRefreshActive && !refreshing && abs(displayOffset) > 0.5f) {
                settleJob?.cancel()
                resetPullState()
            }
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                recordGestureDelta(available.x, available.y)
                if (gestureAxis != CompatPullGestureAxis.VERTICAL || totalRawDrag == 0f) {
                    return Offset.Zero
                }
                if (available.y == 0f || available.y * totalRawDrag >= 0f) return Offset.Zero
                val consumed = if (abs(available.y) <= abs(totalRawDrag)) available.y else -totalRawDrag
                totalRawDrag += consumed
                displayOffset = totalRawDrag / COMPAT_PULL_FRICTION
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && gestureAxis == CompatPullGestureAxis.UNDECIDED) {
                    recordGestureDelta(consumed.x + available.x, consumed.y + available.y)
                }
                if (!latestEnabled || latestRefreshing || pullRefreshActive || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                if (gestureAxis != CompatPullGestureAxis.VERTICAL) return Offset.Zero
                val mayPullTop = available.y > 0f && !latestCanScrollBackward()
                val mayPullBottom = available.y < 0f && !latestCanScrollForward()
                if (!mayPullTop && !mayPullBottom) return Offset.Zero
                if (totalRawDrag != 0f && totalRawDrag * available.y < 0f) return Offset.Zero
                settleJob?.cancel()
                totalRawDrag = (totalRawDrag + available.y)
                    .coerceIn(
                        -maximumDisplayOffsetPx * COMPAT_PULL_FRICTION,
                        maximumDisplayOffsetPx * COMPAT_PULL_FRICTION
                    )
                displayOffset = totalRawDrag / COMPAT_PULL_FRICTION
                return Offset(0f, available.y)
            }
        }
    }

    Box(
        modifier = modifier
            // Keep the pull header outside the viewport until the finger has
            // actually moved far enough to reveal it. Rendering a full-height
            // child at the edge and changing only its alpha made the whole
            // "画面を引っ張って…" block appear after a one-pixel overscroll
            // on Android 11 (#26).
            .clipToBounds()
            .testTag(testTag)
            .nestedScroll(connection)
            .pointerInput(enabled, refreshing) {
                awaitEachGesture {
                    pointerGestureActive = true
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastY = down.position.y
                    var fallbackRawDrag = 0f
                    gestureDx = 0f
                    gestureDy = 0f
                    gestureAxis = CompatPullGestureAxis.UNDECIDED
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        recordGestureDelta(
                            change.position.x - change.previousPosition.x,
                            change.position.y - change.previousPosition.y
                        )
                        val delta = change.position.y - lastY
                        lastY = change.position.y
                        // LazyColumn normally exposes the overscroll through
                        // NestedScrollConnection. Some Android/Compose versions
                        // consume the final bottom delta before onPostScroll;
                        // retain a small pointer-level fallback so an upward
                        // pull at the last response still refreshes.
                        if (
                            latestEnabled && !latestRefreshing && !pullRefreshActive &&
                            gestureAxis == CompatPullGestureAxis.VERTICAL &&
                            totalRawDrag == 0f && fallbackRawDrag == 0f &&
                            ((delta > 0f && !latestCanScrollBackward()) ||
                                (delta < 0f && !latestCanScrollForward()))
                        ) {
                            fallbackRawDrag = delta
                        } else if (fallbackRawDrag != 0f) {
                            fallbackRawDrag = updateCompatPullDrag(
                                totalDrag = fallbackRawDrag,
                                dragAmount = delta,
                                maxAbsDrag = maximumDisplayOffsetPx * COMPAT_PULL_FRICTION
                            ).totalDrag
                        }
                        if (fallbackRawDrag != 0f && totalRawDrag == 0f) {
                            displayOffset = fallbackRawDrag / COMPAT_PULL_FRICTION
                        }
                    }
                    if (totalRawDrag == 0f && fallbackRawDrag != 0f) {
                        totalRawDrag = fallbackRawDrag
                    }
                    finishGesture()
                    pointerGestureActive = false
                }
            }
    ) {
        val density = LocalDensity.current
        val visualHeaderHeightPx = with(density) { 48.dp.toPx() }
        val pullVisualProgress = compatPullVisualProgress(
            offsetPx = displayOffset,
            headerHeightPx = maxOf(headerHeightPx.toFloat(), visualHeaderHeightPx)
        )
        // The APK exposes the pull hint only while a gesture is in progress.
        // At offset == 0 it must not sit permanently over the catalog/loading UI.
        if (pullRefreshActive || abs(displayOffset) > 0.5f) {
            val label = when (compatPullLabel(displayOffset, maxOf(headerHeightPx.toFloat(), minimumTriggerPx), refreshing)) {
                CompatPullLabel.PULL -> "画面を引っ張って…"
                CompatPullLabel.RELEASE -> "指を離して更新…"
                CompatPullLabel.LOADING -> "読み込み中…"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .onSizeChanged { headerHeightPx = it.height }
                    .graphicsLayer { alpha = pullVisualProgress }
                    .graphicsLayer {
                        val headerHeight = maxOf(headerHeightPx.toFloat(), visualHeaderHeightPx)
                        translationY = if (displayOffset >= 0f) {
                            -headerHeight + displayOffset
                        } else {
                            headerHeight + displayOffset
                        }
                    }
                    .align(if (displayOffset >= 0f) Alignment.TopCenter else Alignment.BottomCenter),
                contentAlignment = Alignment.Center
            ) {
                val pullFraction = pullVisualProgress
                Canvas(
                    modifier = Modifier
                        .width(25.dp)
                        .height(25.dp)
                        .testTag("$testTag-indicator")
                        .graphicsLayer {
                            rotationZ = if (refreshing) loadingRotation else pullFraction * 90f
                        }
                ) {
                    drawArc(
                        color = Color(0xFF00897B),
                        startAngle = -90f,
                        sweepAngle = 300f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = label,
                    color = Color.DarkGray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // Leave room for the hint while it is visible.  Otherwise the label
        // is painted over the first/last response during a pull gesture.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = compatPullContentOffsetPx(displayOffset)
                },
            content = content
        )
    }
}

@Composable
internal fun BoxScope.CompatFastScrollbar(
    enabled: Boolean,
    totalItems: Int,
    firstVisibleItemIndex: Int,
    visibleItemCount: Int,
    isScrollInProgress: Boolean,
    onScrollToItem: suspend (Int) -> Unit
) {
    if (!enabled || totalItems <= visibleItemCount || visibleItemCount <= 0) return
    val scope = rememberCoroutineScope()
    val latestOnScrollToItem by rememberUpdatedState(onScrollToItem)
    val latestFirstVisibleItemIndex by rememberUpdatedState(firstVisibleItemIndex)
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(isScrollInProgress, dragging) {
        if (isScrollInProgress || dragging) {
            visible = true
            animate(opacity, 1f, animationSpec = tween(COMPAT_FAST_SCROLL_FADE_MILLIS)) { value, _ ->
                opacity = value
            }
        } else if (visible) {
            delay(COMPAT_FAST_SCROLL_HIDE_DELAY_MILLIS)
            animate(opacity, 0f, animationSpec = tween(COMPAT_FAST_SCROLL_FADE_MILLIS)) { value, _ ->
                opacity = value
            }
            visible = false
        }
    }

    fun scrollForDrag(startIndex: Int, dragDeltaY: Float) {
        val target = compatFastScrollbarDragTarget(
            firstVisibleItemIndex = startIndex,
            totalItems = totalItems,
            visibleItemCount = visibleItemCount,
            trackHeightPx = trackHeightPx,
            dragDeltaY = dragDeltaY
        )
        scrollJob?.cancel()
        scrollJob = scope.launch { latestOnScrollToItem(target) }
    }

    val thumbFraction = (visibleItemCount.toFloat() / totalItems).coerceIn(0.06f, 1f)
    val travelFraction = if (totalItems <= visibleItemCount) 0f else {
        firstVisibleItemIndex.toFloat() / (totalItems - visibleItemCount).coerceAtLeast(1)
    }.coerceIn(0f, 1f)
    // Only the visible thumb owns fast scrolling. A full-height transparent
    // hit strip intercepts ordinary reading swipes made near the right edge
    // and turns them into absolute seeks. Drag relative to the position at
    // pointer-down so grabbing the thumb never causes an initial jump.
    val thumbGestureModifier = Modifier.pointerInput(totalItems, visibleItemCount, touchSlopPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startIndex = latestFirstVisibleItemIndex
            var isDragging = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val moved = change.position != change.previousPosition
                if (moved) {
                    val distance = change.position.y - down.position.y
                    if (!isDragging && abs(distance) >= touchSlopPx) {
                        isDragging = true
                        dragging = true
                    }
                    if (isDragging) {
                        change.consume()
                        scrollForDrag(startIndex, distance)
                    }
                }
                if (!change.pressed) {
                    dragging = false
                    break
                }
            }
            dragging = false
        }
    }
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(24.dp)
            .fillMaxHeight()
            .testTag("compat-fast-scrollbar")
            .onSizeChanged { trackHeightPx = it.height }
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(18.dp)
                .fillMaxHeight(thumbFraction)
                .graphicsLayer {
                    alpha = opacity
                    translationY = (trackHeightPx * (1f - thumbFraction) * travelFraction)
                }
                .then(if (visible || opacity > 0.01f) thumbGestureModifier else Modifier),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                Modifier
                    .padding(end = 3.dp)
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF757575), RoundedCornerShape(3.dp))
            )
        }
    }
}
