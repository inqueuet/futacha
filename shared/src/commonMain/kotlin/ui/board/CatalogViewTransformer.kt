package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal fun buildVisibleCatalogItems(
    items: List<CatalogItem>,
    mode: CatalogMode,
    watchWords: List<String>,
    catalogNgWords: List<String>,
    catalogNgFilteringEnabled: Boolean,
    query: String
): List<CatalogItem> {
    return items
        .let { mode.applyClientTransform(it, watchWords) }
        .filterByCatalogNgWords(catalogNgWords, catalogNgFilteringEnabled)
        .filterByQuery(query)
}

internal fun mergeWatchSourceCatalogItems(
    catalogs: List<List<CatalogItem>>
): List<CatalogItem> {
    return catalogs
        .flatten()
        .distinctBy { item -> item.id.ifBlank { item.threadUrl } }
}

internal fun List<CatalogItem>.filterByQuery(query: String): List<CatalogItem> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return this
    val normalizedQuery = trimmedQuery.lowercase()
    return filter { item ->
        val titleMatch = item.title?.lowercase()?.contains(normalizedQuery) == true
        val idMatch = item.id.lowercase().contains(normalizedQuery)
        val threadMatch = item.threadUrl.lowercase().contains(normalizedQuery)
        titleMatch || idMatch || threadMatch
    }
}

internal fun List<CatalogItem>.filterByCatalogNgWords(
    catalogNgWords: List<String>,
    enabled: Boolean
): List<CatalogItem> {
    if (!enabled) return this
    val wordFilters = catalogNgWords
        .mapNotNull { it.trim().takeIf(String::isNotBlank)?.lowercase() }
    if (wordFilters.isEmpty()) return this
    return filterNot { item ->
        matchesCatalogNgWords(item, wordFilters)
    }
}

internal fun matchesCatalogNgWords(
    item: CatalogItem,
    wordFilters: List<String>
): Boolean {
    val titleText = item.title?.lowercase().orEmpty()
    if (titleText.isEmpty()) return false
    return wordFilters.any { titleText.contains(it) }
}

internal suspend fun loadCatalogItemsForMode(
    boardUrl: String,
    mode: CatalogMode,
    fetchCatalog: suspend (String, CatalogMode) -> List<CatalogItem>
): List<CatalogItem> {
    if (mode != CatalogMode.WatchWords) {
        return fetchCatalog(boardUrl, mode)
    }

    val fetchResults = coroutineScope {
        CatalogMode.watchSourceModes.map { sourceMode ->
            async {
                runCatching {
                    fetchCatalog(boardUrl, sourceMode)
                }
            }
        }.awaitAll()
    }

    val successfulCatalogs = fetchResults.mapNotNull { it.getOrNull() }
    if (successfulCatalogs.isEmpty()) {
        val firstError = fetchResults.firstNotNullOfOrNull { it.exceptionOrNull() }
        throw firstError ?: IllegalStateException("監視モード用のカタログ取得に失敗しました")
    }

    return mergeWatchSourceCatalogItems(successfulCatalogs)
}
