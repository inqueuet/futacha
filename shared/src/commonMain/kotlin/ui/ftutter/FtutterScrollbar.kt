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
    val metrics by remember(list) { derivedStateOf { Triple(list.layoutInfo.totalItemsCount,
        list.firstVisibleItemIndex, list.layoutInfo.visibleItemsInfo.size) } }
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
