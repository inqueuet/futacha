package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.repo.BoardRepository

@Composable
internal fun LoadingCatalog(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
internal fun CatalogError(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
internal fun CatalogSuccessContent(
    items: List<CatalogItem>,
    embeddedHtml: List<EmbeddedHtmlContent>,
    board: BoardSummary?,
    repository: BoardRepository,
    isSearching: Boolean,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    displayStyle: CatalogDisplayStyle,
    gridColumns: Int,
    gridState: LazyGridState,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        CatalogEmptyContent(
            isSearching = isSearching,
            modifier = modifier
        )
    } else {
        when (displayStyle) {
            CatalogDisplayStyle.Grid -> CatalogGrid(
                items = items,
                embeddedHtml = embeddedHtml,
                board = board,
                repository = repository,
                onThreadSelected = onThreadSelected,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing,
                gridColumns = gridColumns,
                gridState = gridState,
                modifier = modifier
            )

            CatalogDisplayStyle.List -> CatalogList(
                items = items,
                embeddedHtml = embeddedHtml,
                board = board,
                repository = repository,
                onThreadSelected = onThreadSelected,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing,
                listState = listState,
                modifier = modifier
            )
        }
    }
}

@Composable
internal fun CatalogEmptyContent(
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSearching) "検索に一致するスレッドがありません" else "表示できるスレッドがありません",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
