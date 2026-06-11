package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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

private data class CatalogVisibleItemsFilterKey(
    val mode: CatalogMode,
    val watchWords: List<String>,
    val catalogNgWords: List<String>,
    val catalogNgFilteringEnabled: Boolean,
    val query: String
)

private class CatalogVisibleItemsItemsKey(
    private val items: List<CatalogItem>
) {
    override fun equals(other: Any?): Boolean {
        return other is CatalogVisibleItemsItemsKey && other.items === items
    }

    override fun hashCode(): Int {
        return items.size
    }
}

private class CatalogVisibleItemsPreviousResult(
    var items: List<CatalogItem>,
    var visibleItems: List<CatalogItem>? = null
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
    val previousResult = remember {
        CatalogVisibleItemsPreviousResult(request.items)
    }
    if (previousResult.items !== request.items) {
        previousResult.items = request.items
        previousResult.visibleItems = null
    }
    val itemsKey = CatalogVisibleItemsItemsKey(request.items)
    val filterKey = CatalogVisibleItemsFilterKey(
        mode = request.mode,
        watchWords = request.watchWords,
        catalogNgWords = request.catalogNgWords,
        catalogNgFilteringEnabled = request.catalogNgFilteringEnabled,
        query = request.query
    )
    return produceState<List<CatalogItem>>(
        initialValue = previousResult.visibleItems.orEmpty(),
        key1 = itemsKey,
        key2 = filterKey
    ) {
        val visibleItems = withContext(AppDispatchers.parsing) {
            buildVisibleCatalogItems(
                items = request.items,
                mode = request.mode,
                watchWords = request.watchWords,
                catalogNgWords = request.catalogNgWords,
                catalogNgFilteringEnabled = request.catalogNgFilteringEnabled,
                query = request.query
            )
        }
        value = visibleItems
        previousResult.visibleItems = visibleItems
    }
}
