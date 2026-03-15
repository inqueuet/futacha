package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.repo.BoardRepository

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogGrid(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    gridColumns: Int,
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val edgeSwipeRefreshBinding = rememberEdgeSwipeRefreshBinding(
        gridState = gridState,
        isRefreshing = isRefreshing,
        animationLabel = "catalogGridOverscroll"
    )

    LazyVerticalGrid(
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .offset { IntOffset(0, edgeSwipeRefreshBinding.visualState.overscrollOffset.toInt()) }
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
        items(items = items, key = { it.id }) { catalogItem ->
            CatalogCard(
                item = catalogItem,
                boardUrl = board?.url,
                repository = repository,
                onClick = { onThreadSelected(catalogItem) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogList(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val edgeSwipeRefreshBinding = rememberEdgeSwipeRefreshBinding(
        listState = listState,
        isRefreshing = isRefreshing,
        animationLabel = "catalogListOverscroll"
    )

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, edgeSwipeRefreshBinding.visualState.overscrollOffset.toInt()) }
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
        items(items = items, key = { it.id }) { catalogItem ->
            CatalogListItem(
                item = catalogItem,
                boardUrl = board?.url,
                repository = repository,
                onClick = { onThreadSelected(catalogItem) }
            )
        }
    }
}
