package com.valoser.futacha.shared.ui.futaber

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/**
 * V32 (L-B3): the thin bar futaber's lists show at the right edge while they scroll (iOS's own scroll indicator), drawn
 * here because Compose has none. It appears when a scroll starts and fades out half a second after it stops; it is not
 * a control (the fast scroll bar of the extension E5 takes its place when that is on; this is the only function to
 * swap). Nothing is recomposed while scrolling: the bar is placed and faded in the draw phase.
 */
@Composable
internal fun rememberFutaberScrollIndicator(state: LazyListState, ink: Color? = null): Modifier {
    val memory = remember(state, androidx.compose.ui.platform.LocalDensity.current) { com.valoser.futacha.shared.ui.compat.ScrollSizeMemory() }
    return rememberFutaberScrollIndicator(
    state, isScrolling = { state.isScrollInProgress }, ink = ink
) { viewport ->
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    memory.record(info.totalItemsCount, visible.map { it.index to it.size })
    futaberScrollThumb(
        viewport = viewport,
        totalUnits = info.totalItemsCount,
        firstUnit = state.firstVisibleItemIndex,
        firstUnitOffset = state.firstVisibleItemScrollOffset.toFloat(),
        averageUnitSize = memory.averageSize(),
        atStart = !state.canScrollBackward,
        atEnd = !state.canScrollForward,
        minThumb = FUTABER_SCROLL_THUMB_MIN_DP * density
    )
}
}

/** [rememberFutaberScrollIndicator] of the catalog's grid: one unit is a row of cells. */
@Composable
internal fun rememberFutaberScrollIndicator(state: LazyGridState, columns: Int, ink: Color? = null): Modifier = rememberFutaberScrollIndicator(
    state, isScrolling = { state.isScrollInProgress }, ink = ink
) { viewport ->
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    val perRow = columns.coerceAtLeast(1)
    val rows = (info.totalItemsCount + perRow - 1) / perRow
    val rowSize = if (visible.isEmpty()) 0f else visible.maxOf { it.size.height }.toFloat() + info.mainAxisItemSpacing
    futaberScrollThumb(
        viewport = viewport,
        totalUnits = rows,
        firstUnit = state.firstVisibleItemIndex / perRow,
        firstUnitOffset = state.firstVisibleItemScrollOffset.toFloat(),
        averageUnitSize = rowSize,
        atStart = !state.canScrollBackward,
        atEnd = !state.canScrollForward,
        minThumb = FUTABER_SCROLL_THUMB_MIN_DP * density
    )
}

/**
 * [rememberFutaberScrollIndicator] of a plain scrolling column (a sheet's items, L-B4). With [flashWhenScrollable] the
 * bar shows once when the column first has more to scroll and then fades (iOS's `flashScrollIndicators`: a sheet that
 * stops at the original's items shows there is more under them).
 */
@Composable
internal fun rememberFutaberScrollIndicator(state: ScrollState, flashWhenScrollable: Boolean = false): Modifier =
    rememberFutaberScrollIndicator(
        state,
        isScrolling = { state.isScrollInProgress },
        flash = if (flashWhenScrollable) ({ state.maxValue > 0 }) else null
    ) { viewport ->
        futaberScrollStateThumb(viewport, state.maxValue.toFloat(), state.value.toFloat(), FUTABER_SCROLL_THUMB_MIN_DP * density)
    }

/**
 * Where the bar of a plain scrolling column goes: the viewport is [viewport] px of a content [maxScroll] px taller, scrolled
 * [scrolled] px. Null when there is nothing to scroll.
 */
internal fun futaberScrollStateThumb(viewport: Float, maxScroll: Float, scrolled: Float, minThumb: Float): FutaberScrollThumb? {
    if (viewport <= 0f || maxScroll <= 0f) return null
    val content = viewport + maxScroll
    val height = (viewport * viewport / content).coerceIn(minOf(minThumb, viewport), viewport)
    val travel = viewport - height
    return FutaberScrollThumb(travel * (scrolled / maxScroll).coerceIn(0f, 1f), height)
}

@Composable
private fun rememberFutaberScrollIndicator(
    key: Any,
    isScrolling: () -> Boolean,
    /** When given, the bar shows once (and fades) the first time this turns true. */
    flash: (() -> Boolean)? = null,
    /** The bar's colour; null takes ふたばー風's (dark on light themes). */
    ink: Color? = null,
    thumb: androidx.compose.ui.graphics.drawscope.DrawScope.(viewport: Float) -> FutaberScrollThumb?
): Modifier {
    val colors = LocalFutaberColors.current
    val alpha = remember(key) { Animatable(0f) }
    if (flash != null) LaunchedEffect(key) {
        snapshotFlow(flash).first { it }
        // Not while the person is already scrolling it (the bar then follows the scroll).
        if (!isScrolling()) {
            alpha.animateTo(1f, tween(FUTABER_SCROLL_INDICATOR_IN_MILLIS))
            delay(FUTABER_SCROLL_INDICATOR_FLASH_MILLIS)
            if (!isScrolling()) alpha.animateTo(0f, tween(FUTABER_SCROLL_INDICATOR_OUT_MILLIS))
        }
    }
    LaunchedEffect(key) {
        snapshotFlow(isScrolling).collectLatest { scrolling ->
            if (scrolling) alpha.animateTo(1f, tween(FUTABER_SCROLL_INDICATOR_IN_MILLIS))
            // A bar that is not showing is left alone (the flash on opening runs its own course).
            else if (alpha.value > 0f || alpha.targetValue > 0f) {
                delay(FUTABER_SCROLL_INDICATOR_LINGER_MILLIS)
                alpha.animateTo(0f, tween(FUTABER_SCROLL_INDICATOR_OUT_MILLIS))
            }
        }
    }
    // Not text: a dark bar on the light themes, a light one on the dark themes, as iOS draws it.
    val barInk = ink ?: if (colors.isDark) Color.White else Color.Black
    return remember(key, barInk) {
        Modifier.drawWithContent {
            drawContent()
            val shown = alpha.value
            if (shown <= 0f) return@drawWithContent
            val placed = thumb(size.height) ?: return@drawWithContent
            val width = FUTABER_SCROLL_THUMB_WIDTH_DP.dp.toPx()
            val inset = FUTABER_SCROLL_THUMB_INSET_DP.dp.toPx()
            drawRoundRect(
                color = barInk.copy(alpha = FUTABER_SCROLL_THUMB_ALPHA * shown),
                topLeft = Offset(size.width - inset - width, placed.top),
                size = Size(width, placed.height),
                cornerRadius = CornerRadius(width / 2f)
            )
        }
    }
}

/** Where the bar is in a list [FutaberScrollThumb.top] px from its top, [FutaberScrollThumb.height] px long. */
@Immutable
internal data class FutaberScrollThumb(val top: Float, val height: Float)

/**
 * Where the scroll bar goes: the list is taken as [totalUnits] units (posts, rows of cells) of [averageUnitSize] px, the
 * bar is as long as the share of it on screen (at least [minThumb]) and as far down as the list is scrolled. At the very
 * start and end it sits at the ends ([atStart], [atEnd]: the estimate of the size is not exact). Null when there is
 * nothing to scroll.
 */
internal fun futaberScrollThumb(
    viewport: Float,
    totalUnits: Int,
    firstUnit: Int,
    firstUnitOffset: Float,
    averageUnitSize: Float,
    atStart: Boolean,
    atEnd: Boolean,
    minThumb: Float
): FutaberScrollThumb? {
    if (viewport <= 0f || totalUnits <= 0 || averageUnitSize <= 0f) return null
    if (atStart && atEnd) return null
    val content = maxOf(totalUnits * averageUnitSize, viewport + 1f)
    val height = (viewport * viewport / content).coerceIn(minOf(minThumb, viewport), viewport)
    val travel = viewport - height
    val top = when {
        atStart -> 0f
        atEnd -> travel
        else -> travel * ((firstUnit * averageUnitSize + firstUnitOffset) / (content - viewport)).coerceIn(0f, 1f)
    }
    return FutaberScrollThumb(top, height)
}

/** iOS's indicator: 2.5 wide, 3 from the edge, at least 36 long, black at 35%. */
internal const val FUTABER_SCROLL_THUMB_WIDTH_DP = 2.5f
internal const val FUTABER_SCROLL_THUMB_INSET_DP = 3f
internal const val FUTABER_SCROLL_THUMB_MIN_DP = 36f
internal const val FUTABER_SCROLL_THUMB_ALPHA = 0.35f
private const val FUTABER_SCROLL_INDICATOR_IN_MILLIS = 120
private const val FUTABER_SCROLL_INDICATOR_LINGER_MILLIS = 500L
private const val FUTABER_SCROLL_INDICATOR_OUT_MILLIS = 300

/** How long the bar stays when it flashes on opening (iOS shows it about a second). */
internal const val FUTABER_SCROLL_INDICATOR_FLASH_MILLIS = 900L
