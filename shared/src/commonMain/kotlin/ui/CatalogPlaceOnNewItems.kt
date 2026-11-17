package com.valoser.futacha.shared.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot

/**
 * When a catalog gets a new list (a reload after coming back from a thread), keep the place by position, not by thread.
 * Lazy lists with keys follow the first visible thread; on a busy board (may) that thread has sunk toward the bottom of
 * the new catalog, so the list jumped to the old/isolated threads at the end (report 1447450829). The original apps keep
 * the scroll position. An empty list in between is ignored.
 */
@Composable
@NonRestartableComposable
internal fun KeepCatalogPlaceOnNewItems(
    items: List<*>,
    grid: LazyGridState? = null,
    list: LazyListState? = null,
    staggered: LazyStaggeredGridState? = null
) {
    val seen = remember(grid, list, staggered) { arrayOfNulls<Any>(1) }
    if (seen[0] === items || items.isEmpty()) return
    val first = seen[0] == null
    seen[0] = items
    if (first) return
    Snapshot.withoutReadObservation {
        grid?.let { it.requestScrollToItem(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) }
        list?.let { it.requestScrollToItem(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) }
        staggered?.let { it.requestScrollToItem(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) }
    }
}
