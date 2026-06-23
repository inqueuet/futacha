package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.model.matchesNormalizedWatchWords
import com.valoser.futacha.shared.model.normalizeWatchWords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

private const val CATALOG_WATCH_SOURCE_TIMEOUT_MS = 8_000L

internal fun buildVisibleCatalogItems(
    items: List<CatalogItem>,
    mode: CatalogMode,
    watchWords: List<String>,
    catalogNgWords: List<String>,
    catalogNgFilteringEnabled: Boolean,
    query: String
): List<CatalogItem> {
    return items
        // Guard Lazy* item keys from parser/network duplicates while keeping server order.
        .deduplicateByIdentity()
        .let { mode.applyClientTransform(it, watchWords) }
        .let { prioritizeWatchWordMatches(it, mode, watchWords) }
        .filterByCatalogNgWords(catalogNgWords, catalogNgFilteringEnabled)
        .filterByQuery(query)
}

internal fun prioritizeWatchWordMatches(
    items: List<CatalogItem>,
    mode: CatalogMode,
    watchWords: List<String>
): List<CatalogItem> {
    if (mode == CatalogMode.WatchWords) return items
    val normalizedWatchWords = normalizeWatchWords(watchWords)
    if (normalizedWatchWords.isEmpty()) return items
    val matched = ArrayList<CatalogItem>()
    val unmatched = ArrayList<CatalogItem>()
    items.forEach { item ->
        if (item.matchesNormalizedWatchWords(normalizedWatchWords)) {
            matched += item
        } else {
            unmatched += item
        }
    }
    if (matched.isEmpty()) return items
    return matched + unmatched
}

internal fun mergeWatchSourceCatalogItems(
    catalogs: List<List<CatalogItem>>
): List<CatalogItem> {
    return catalogs
        .flatten()
        .deduplicateByIdentity()
}

internal fun List<CatalogItem>.deduplicateByIdentity(): List<CatalogItem> {
    return distinctBy { item -> item.id.ifBlank { item.threadUrl } }
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
    onWatchWordsPartial: suspend (CatalogPageContent) -> Unit = {},
    fetchCatalog: suspend (String, CatalogMode) -> CatalogPageContent
): CatalogPageContent {
    if (mode != CatalogMode.WatchWords) {
        return fetchCatalog(boardUrl, mode)
    }

    val successfulCatalogs = mutableListOf<CatalogPageContent>()
    var firstError: Throwable? = null

    runCatchingCatalogSource {
        withTimeoutOrNull(CATALOG_WATCH_SOURCE_TIMEOUT_MS) {
            fetchCatalog(boardUrl, CatalogMode.Catalog)
        } ?: throw IllegalStateException("Catalog watch source timed out: ${CatalogMode.Catalog}")
    }.fold(
        onSuccess = { catalog ->
            successfulCatalogs += catalog
            onWatchWordsPartial(buildWatchWordsCatalogPage(successfulCatalogs))
        },
        onFailure = { error ->
            firstError = error
        }
    )

    val remainingModes = CatalogMode.watchSourceModes.filterNot { it == CatalogMode.Catalog }
    coroutineScope {
        val fetchSemaphore = Semaphore(2)
        val resultChannel = Channel<Result<CatalogPageContent>>(capacity = remainingModes.size)
        remainingModes.forEach { sourceMode ->
            launch {
                fetchSemaphore.withPermit {
                    resultChannel.send(
                        runCatchingCatalogSource {
                            withTimeoutOrNull(CATALOG_WATCH_SOURCE_TIMEOUT_MS) {
                                fetchCatalog(boardUrl, sourceMode)
                            } ?: throw IllegalStateException("Catalog watch source timed out: $sourceMode")
                        }
                    )
                }
            }
        }
        repeat(remainingModes.size) {
            resultChannel.receive().fold(
                onSuccess = { catalog ->
                    successfulCatalogs += catalog
                    onWatchWordsPartial(buildWatchWordsCatalogPage(successfulCatalogs))
                },
                onFailure = { error ->
                    if (firstError == null) {
                        firstError = error
                    }
                }
            )
        }
    }

    if (successfulCatalogs.isEmpty()) {
        throw firstError ?: IllegalStateException("監視モード用のカタログ取得に失敗しました")
    }

    return buildWatchWordsCatalogPage(successfulCatalogs)
}

private suspend inline fun runCatchingCatalogSource(
    block: suspend () -> CatalogPageContent
): Result<CatalogPageContent> {
    return try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

private fun buildWatchWordsCatalogPage(
    successfulCatalogs: List<CatalogPageContent>
): CatalogPageContent {
    return CatalogPageContent(
        items = mergeWatchSourceCatalogItems(successfulCatalogs.map { it.items }),
        embeddedHtml = successfulCatalogs.firstNotNullOfOrNull { page ->
            page.embeddedHtml.takeIf { it.isNotEmpty() }
        }.orEmpty()
    )
}
