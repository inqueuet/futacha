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
    val sourceKey: String?,
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
    private val sourceKey: String?,
    private val items: List<CatalogItem>
) {
    override fun equals(other: Any?): Boolean {
        return other is CatalogVisibleItemsItemsKey &&
            other.sourceKey == sourceKey &&
            other.items === items
    }

    override fun hashCode(): Int {
        return 31 * sourceKey.hashCode() + items.size
    }
}

private class CatalogVisibleItemsPreviousResult(
    var sourceKey: String?,
    var items: List<CatalogItem>,
    var visibleItems: List<CatalogItem>? = null
)

internal fun buildCatalogVisibleItemsRequest(
    sourceKey: String?,
    items: List<CatalogItem>,
    mode: CatalogMode,
    watchWords: List<String>,
    catalogNgWords: List<String>,
    catalogNgFilteringEnabled: Boolean,
    query: String
): CatalogVisibleItemsRequest {
    return CatalogVisibleItemsRequest(
        sourceKey = sourceKey,
        items = items,
        mode = mode,
        watchWords = watchWords,
        catalogNgWords = catalogNgWords,
        catalogNgFilteringEnabled = catalogNgFilteringEnabled,
        query = query
    )
}

internal fun shouldResetCatalogVisibleItemsForSourceChange(
    previousSourceKey: String?,
    currentSourceKey: String?
): Boolean {
    return previousSourceKey != currentSourceKey
}

@Composable
internal fun rememberCatalogVisibleItemsState(
    request: CatalogVisibleItemsRequest
): State<List<CatalogItem>> {
    val previousResult = remember {
        CatalogVisibleItemsPreviousResult(request.sourceKey, request.items)
    }
    if (
        shouldResetCatalogVisibleItemsForSourceChange(
            previousSourceKey = previousResult.sourceKey,
            currentSourceKey = request.sourceKey
        )
    ) {
        previousResult.visibleItems = null
        previousResult.sourceKey = request.sourceKey
        previousResult.items = request.items
    } else if (previousResult.items !== request.items) {
        previousResult.items = request.items
    }
    val itemsKey = CatalogVisibleItemsItemsKey(request.sourceKey, request.items)
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
