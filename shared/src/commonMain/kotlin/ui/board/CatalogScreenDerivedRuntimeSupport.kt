package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

internal data class CatalogVisibleItemsRequest(
    val items: List<CatalogItem>,
    val mode: CatalogMode,
    val watchWords: List<String>,
    val catalogNgWords: List<String>,
    val catalogNgFilteringEnabled: Boolean,
    val query: String
)

internal fun buildCatalogVisibleItemsRequest(
    items: List<CatalogItem>,
    mode: CatalogMode,
    watchWords: List<String>,
    catalogNgWords: List<String>,
    catalogNgFilteringEnabled: Boolean,
    query: String
): CatalogVisibleItemsRequest {
    return CatalogVisibleItemsRequest(
        items = items,
        mode = mode,
        watchWords = watchWords,
        catalogNgWords = catalogNgWords,
        catalogNgFilteringEnabled = catalogNgFilteringEnabled,
        query = query
    )
}

@Composable
internal fun rememberCatalogVisibleItemsState(
    request: CatalogVisibleItemsRequest
): State<List<CatalogItem>> {
    return produceState<List<CatalogItem>>(
        initialValue = emptyList(),
        key1 = request
    ) {
        value = withContext(AppDispatchers.parsing) {
            buildVisibleCatalogItems(
                items = request.items,
                mode = request.mode,
                watchWords = request.watchWords,
                catalogNgWords = request.catalogNgWords,
                catalogNgFilteringEnabled = request.catalogNgFilteringEnabled,
                query = request.query
            )
        }
    }
}
