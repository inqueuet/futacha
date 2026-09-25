package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

internal class CatalogVisibleItemsResult(
    val sourceKey: String?,
    val items: List<CatalogItem>,
    val visibleItems: List<CatalogItem>
)

/**
 * The list to show, or null while nothing has been computed for this board yet
 * (so the screen shows loading instead of "no threads"). A result for the same
 * board is kept while a newer one is computed after a refresh or filter change,
 * except one computed for the empty list shown before the first load.
 */
internal fun resolveDisplayedCatalogVisibleItems(
    result: CatalogVisibleItemsResult?,
    sourceKey: String?,
    items: List<CatalogItem>
): List<CatalogItem>? {
    if (result == null) return null
    if (shouldResetCatalogVisibleItemsForSourceChange(result.sourceKey, sourceKey)) return null
    if (result.items !== items && result.items.isEmpty() && items.isNotEmpty()) return null
    return result.visibleItems
}

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
internal fun rememberCatalogVisibleItems(
    request: CatalogVisibleItemsRequest
): List<CatalogItem>? {
    var result by remember { mutableStateOf<CatalogVisibleItemsResult?>(null) }
    val itemsKey = CatalogVisibleItemsItemsKey(request.sourceKey, request.items)
    val filterKey = CatalogVisibleItemsFilterKey(
        mode = request.mode,
        watchWords = request.watchWords,
        catalogNgWords = request.catalogNgWords,
        catalogNgFilteringEnabled = request.catalogNgFilteringEnabled,
        query = request.query
    )
    LaunchedEffect(itemsKey, filterKey) {
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
        result = CatalogVisibleItemsResult(request.sourceKey, request.items, visibleItems)
    }
    return resolveDisplayedCatalogVisibleItems(result, request.sourceKey, request.items)
}
