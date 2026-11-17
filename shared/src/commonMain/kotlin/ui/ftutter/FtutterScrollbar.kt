package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.compat.CompatFastScrollbar

internal data class FtutterScrollbarSettings(val enabled: Boolean, val always: Boolean, val left: Boolean, val thickness: Int)
internal fun parseFtutterScrollbar(p: Map<String, String>, catalog: Boolean) = FtutterScrollbarSettings(
    p.ftutterBool(if (catalog) FtutterKeys.CATALOG_SCROLLBAR else FtutterKeys.THREAD_SCROLLBAR, true),
    p.ftutterBool(if (catalog) FtutterKeys.CATALOG_SCROLLBAR_ALWAYS else FtutterKeys.THREAD_SCROLLBAR_ALWAYS, false),
    p.ftutterBool(if (catalog) FtutterKeys.CATALOG_SCROLLBAR_LEFT else FtutterKeys.THREAD_SCROLLBAR_LEFT, false),
    p.ftutterInt(if (catalog) FtutterKeys.CATALOG_SCROLLBAR_THICKNESS else FtutterKeys.THREAD_SCROLLBAR_THICKNESS, 8, 1..100)
)
@Composable
internal fun BoxScope.FtutterScrollbar(settings: FtutterScrollbarSettings, list: LazyListState) {
    // The thumb is as long as "how many posts fit on the screen"; counting the posts on screen made it grow and shrink
    // with each post's height (App Store review 2026-11-16), so the count comes from the average of all measured posts.
    val memory = remember(list, androidx.compose.ui.platform.LocalDensity.current) { com.valoser.futacha.shared.ui.compat.ScrollSizeMemory() }
    val metrics by remember(list) { derivedStateOf {
        val info = list.layoutInfo
        memory.record(info.totalItemsCount, info.visibleItemsInfo.map { it.index to it.size })
        val total = info.totalItemsCount
        val count = ftutterScrollbarCount(total, ftutterStableVisibleCount(info.viewportEndOffset - info.viewportStartOffset,
            memory.averageSize(), info.visibleItemsInfo.size), list.canScrollForward || list.canScrollBackward)
        Triple(total, ftutterScrollbarFirst(total, list.firstVisibleItemIndex, count, list.canScrollForward), count)
    } }
    FtutterScrollbar(settings, metrics.first, metrics.second, metrics.third, list.isScrollInProgress, list::scrollToItem)
}
@Composable
internal fun BoxScope.FtutterScrollbar(settings: FtutterScrollbarSettings, grid: LazyGridState) {
    val metrics by remember(grid) { derivedStateOf { Triple(grid.layoutInfo.totalItemsCount,
        grid.firstVisibleItemIndex, grid.layoutInfo.visibleItemsInfo.size) } }
    FtutterScrollbar(settings, metrics.first, metrics.second, metrics.third, grid.isScrollInProgress, grid::scrollToItem)
}
@Composable
internal fun BoxScope.FtutterScrollbar(settings: FtutterScrollbarSettings, grid: LazyStaggeredGridState) {
    val metrics by remember(grid) { derivedStateOf { Triple(grid.layoutInfo.totalItemsCount,
        grid.firstVisibleItemIndex, grid.layoutInfo.visibleItemsInfo.size) } }
    FtutterScrollbar(settings, metrics.first, metrics.second, metrics.third, grid.isScrollInProgress, grid::scrollToItem)
}
@Composable
private fun BoxScope.FtutterScrollbar(settings: FtutterScrollbarSettings, total: Int, first: Int, count: Int,
    scrolling: Boolean, scroll: suspend (Int) -> Unit) {
    CompatFastScrollbar(settings.enabled, total, first, count, scrolling, scroll,
        alwaysVisible = settings.always, onLeft = settings.left, thickness = settings.thickness.dp,
        thumbColor = LocalFtutterColors.current.scrollBar)
}

/** Posts that fit in [viewport] px at [averageSize] px each (at least 1); [fallback] until anything is measured. */
internal fun ftutterStableVisibleCount(viewport: Int, averageSize: Float, fallback: Int): Int =
    if (viewport <= 0 || averageSize <= 0f) fallback else kotlin.math.round(viewport / averageSize).toInt().coerceAtLeast(1)

/**
 * The estimated count stays below [total] while the list can scroll, or the bar would not show at all (audit 2026-11-17
 * L9).
 */
internal fun ftutterScrollbarCount(total: Int, estimated: Int, scrollable: Boolean): Int =
    if (scrollable && total > 1) estimated.coerceAtMost(total - 1) else estimated

/** At the end of the list the thumb is at the bottom, whatever the estimate says (as ふたばー風's atEnd; L9). */
internal fun ftutterScrollbarFirst(total: Int, first: Int, count: Int, canScrollForward: Boolean): Int =
    if (!canScrollForward && total > count) total - count else first
