package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.model.EmbeddedHtmlPlacement
import com.valoser.futacha.shared.model.matchedWatchWords

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogGrid(
    items: List<CatalogItem>,
    embeddedHtml: List<EmbeddedHtmlContent>,
    board: BoardSummary?,
    watchWords: List<String>,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    gridColumns: Int,
    gridState: LazyGridState,
    resolvedHeadTitles: Map<String, String>,
    resolveHeadMetadata: Boolean,
    modifier: Modifier = Modifier,
    /** MIX only: one entry per item, same order. Rows then take their own board, key, label and watch words. */
    mixEntries: List<com.valoser.futacha.shared.mix.MixEntry>? = null,
    mixNowMillis: Long = 0L,
    mixWatchWordsOf: (com.valoser.futacha.shared.mix.MixEntry) -> List<String> = { watchWords }
) {
    val currentSelection = androidx.compose.runtime.rememberUpdatedState(onThreadSelected)
    val stableSelection = remember { { item: CatalogItem -> currentSelection.value(item) } }
    val lazyItemKeys = remember(board?.id, board?.url, items, mixEntries) {
        buildCatalogItemLazyKeys(
            boardIdentity = board?.id?.ifBlank { board.url } ?: "unknown-board",
            items = items,
            boardIdentityAt = mixEntries?.let { rows -> { index: Int -> rows.getOrNull(index)?.board?.id.orEmpty() } }
        )
    }
    val edgeSwipeRefreshBinding = rememberEdgeSwipeRefreshBinding(
        gridState = gridState,
        isRefreshing = isRefreshing,
        animationLabel = "catalogGridOverscroll"
    )
    com.valoser.futacha.shared.ui.KeepCatalogPlaceOnNewItems(items, grid = gridState)
    LazyVerticalGrid(
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .offset { IntOffset(0, edgeSwipeRefreshBinding.visualState.overscrollOffset.value.toInt()) }
            .edgeSwipeRefresh(
                isRefreshing = isRefreshing,
                isAtTop = edgeSwipeRefreshBinding.edgeState.isAtTop,
                isAtBottom = edgeSwipeRefreshBinding.edgeState.isAtBottom,
                maxOverscrollPx = edgeSwipeRefreshBinding.metrics.maxOverscrollPx,
                refreshTriggerPx = edgeSwipeRefreshBinding.metrics.refreshTriggerPx,
                onOverscrollTargetChanged = edgeSwipeRefreshBinding.visualState::onOverscrollTargetChanged,
                onRefresh = onRefresh
            ),
        columns = GridCells.Fixed(gridColumns.coerceIn(MIN_CATALOG_GRID_COLUMNS, MAX_CATALOG_GRID_COLUMNS)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        if (embeddedHtml.any { it.placement == EmbeddedHtmlPlacement.Header }) {
            item(
                key = "catalog-embedded-html-header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                EmbeddedHtmlSection(
                    snippets = embeddedHtml,
                    placement = EmbeddedHtmlPlacement.Header,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        gridItemsIndexed(items = items, key = { index, _ -> lazyItemKeys[index] }) { index, catalogItem ->
            val mixEntry = mixEntries?.getOrNull(index)
            val rowWords = if (mixEntry != null) mixWatchWordsOf(mixEntry) else watchWords
            CatalogCard(
                item = catalogItem,
                boardUrl = mixEntry?.board?.url ?: board?.url,
                resolvedDisplayTitle = resolvedHeadTitles[mixEntry?.key?.asString() ?: catalogItem.id],
                resolveHeadMetadata = resolveHeadMetadata,
                matchedWatchWords = remember(catalogItem.title, rowWords) { catalogItem.matchedWatchWords(rowWords) },
                onClick = { stableSelection(catalogItem) },
                cellLabel = mixEntry?.let { com.valoser.futacha.shared.mix.mixCellLabel(it, mixNowMillis) },
                countOverride = mixEntry?.let { com.valoser.futacha.shared.mix.mixCountOverride(it) }
            )
        }
        if (embeddedHtml.any { it.placement == EmbeddedHtmlPlacement.Footer }) {
            item(
                key = "catalog-embedded-html-footer",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                EmbeddedHtmlSection(
                    snippets = embeddedHtml,
                    placement = EmbeddedHtmlPlacement.Footer,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogList(
    items: List<CatalogItem>,
    embeddedHtml: List<EmbeddedHtmlContent>,
    board: BoardSummary?,
    watchWords: List<String>,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    listState: LazyListState,
    resolvedHeadTitles: Map<String, String>,
    resolveHeadMetadata: Boolean,
    modifier: Modifier = Modifier,
    /** MIX only: one entry per item, same order. Rows then take their own board, key, label and watch words. */
    mixEntries: List<com.valoser.futacha.shared.mix.MixEntry>? = null,
    mixNowMillis: Long = 0L,
    mixWatchWordsOf: (com.valoser.futacha.shared.mix.MixEntry) -> List<String> = { watchWords }
) {
    val currentSelection = androidx.compose.runtime.rememberUpdatedState(onThreadSelected)
    val stableSelection = remember { { item: CatalogItem -> currentSelection.value(item) } }
    val lazyItemKeys = remember(board?.id, board?.url, items, mixEntries) {
        buildCatalogItemLazyKeys(
            boardIdentity = board?.id?.ifBlank { board.url } ?: "unknown-board",
            items = items,
            boardIdentityAt = mixEntries?.let { rows -> { index: Int -> rows.getOrNull(index)?.board?.id.orEmpty() } }
        )
    }
    val edgeSwipeRefreshBinding = rememberEdgeSwipeRefreshBinding(
        listState = listState,
        isRefreshing = isRefreshing,
        animationLabel = "catalogListOverscroll"
    )
    com.valoser.futacha.shared.ui.KeepCatalogPlaceOnNewItems(items, list = listState)
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, edgeSwipeRefreshBinding.visualState.overscrollOffset.value.toInt()) }
            .edgeSwipeRefresh(
                isRefreshing = isRefreshing,
                isAtTop = edgeSwipeRefreshBinding.edgeState.isAtTop,
                isAtBottom = edgeSwipeRefreshBinding.edgeState.isAtBottom,
                maxOverscrollPx = edgeSwipeRefreshBinding.metrics.maxOverscrollPx,
                refreshTriggerPx = edgeSwipeRefreshBinding.metrics.refreshTriggerPx,
                onOverscrollTargetChanged = edgeSwipeRefreshBinding.visualState::onOverscrollTargetChanged,
                onRefresh = onRefresh
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        if (embeddedHtml.any { it.placement == EmbeddedHtmlPlacement.Header }) {
            item(key = "catalog-embedded-html-header") {
                EmbeddedHtmlSection(
                    snippets = embeddedHtml,
                    placement = EmbeddedHtmlPlacement.Header,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        itemsIndexed(items = items, key = { index, _ -> lazyItemKeys[index] }) { index, catalogItem ->
            val mixEntry = mixEntries?.getOrNull(index)
            val rowWords = if (mixEntry != null) mixWatchWordsOf(mixEntry) else watchWords
            CatalogListItem(
                item = catalogItem,
                boardUrl = mixEntry?.board?.url ?: board?.url,
                resolvedDisplayTitle = resolvedHeadTitles[mixEntry?.key?.asString() ?: catalogItem.id],
                resolveHeadMetadata = resolveHeadMetadata,
                matchedWatchWords = remember(catalogItem.title, rowWords) { catalogItem.matchedWatchWords(rowWords) },
                onClick = { stableSelection(catalogItem) },
                cellLabel = mixEntry?.let { com.valoser.futacha.shared.mix.mixCellLabel(it, mixNowMillis) },
                countOverride = mixEntry?.let { com.valoser.futacha.shared.mix.mixCountOverride(it) }
            )
        }
        if (embeddedHtml.any { it.placement == EmbeddedHtmlPlacement.Footer }) {
            item(key = "catalog-embedded-html-footer") {
                EmbeddedHtmlSection(
                    snippets = embeddedHtml,
                    placement = EmbeddedHtmlPlacement.Footer,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
