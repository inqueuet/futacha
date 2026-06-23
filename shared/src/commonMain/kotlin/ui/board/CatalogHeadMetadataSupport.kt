package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.model.EmbeddedHtmlPlacement
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.shouldResolveCatalogThreadTitleFromHead
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

private const val CATALOG_HEAD_METADATA_TIMEOUT_MS = 1_800L
private const val MAX_CATALOG_HEAD_METADATA_BATCH_SIZE = 12
private const val MAX_CATALOG_HEAD_METADATA_CONCURRENCY = 2

@Composable
internal fun rememberCatalogHeadMetadataTitles(
    items: List<CatalogItem>,
    embeddedHtml: List<EmbeddedHtmlContent>,
    boardUrl: String?,
    repository: BoardRepository,
    displayStyle: CatalogDisplayStyle,
    gridState: LazyGridState,
    listState: LazyListState,
    resolveHeadMetadata: Boolean
): Map<String, String> {
    val resolvedTitlesByItemId = remember(boardUrl, repository) { mutableStateMapOf<String, String>() }
    val resolvedCacheKeysByItemId = remember(boardUrl, repository) {
        mutableStateMapOf<String, CatalogHeadMetadataCacheKey>()
    }
    val hasHeader = embeddedHtml.any { it.placement == EmbeddedHtmlPlacement.Header }
    val itemById = remember(items) { items.associateBy { it.id } }
    val activeCacheKeysByItemId = remember(boardUrl, items) {
        items.associate { item ->
            item.id to buildCatalogHeadMetadataCacheKey(boardUrl, item)
        }
    }

    LaunchedEffect(activeCacheKeysByItemId) {
        resolvedTitlesByItemId.keys.retainAll(activeCacheKeysByItemId.keys)
        resolvedCacheKeysByItemId.keys.retainAll(activeCacheKeysByItemId.keys)
        val staleItemIds = resolvedCacheKeysByItemId
            .filter { (itemId, cacheKey) ->
                activeCacheKeysByItemId[itemId] != cacheKey
            }
            .keys
            .toList()
        staleItemIds.forEach { itemId ->
            resolvedTitlesByItemId.remove(itemId)
            resolvedCacheKeysByItemId.remove(itemId)
        }
    }

    LaunchedEffect(
        items,
        boardUrl,
        repository,
        displayStyle,
        gridState,
        listState,
        resolveHeadMetadata,
        hasHeader
    ) {
        if (!resolveHeadMetadata || boardUrl.isNullOrBlank()) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val visibleIndices = when (displayStyle) {
                CatalogDisplayStyle.Grid -> gridState.layoutInfo.visibleItemsInfo.map { it.index }
                CatalogDisplayStyle.List -> listState.layoutInfo.visibleItemsInfo.map { it.index }
            }
            val isScrolling = when (displayStyle) {
                CatalogDisplayStyle.Grid -> gridState.isScrollInProgress
                CatalogDisplayStyle.List -> listState.isScrollInProgress
            }
            visibleIndices.mapNotNull { visibleIndex ->
                val itemIndex = visibleIndex - if (hasHeader) 1 else 0
                items.getOrNull(itemIndex)?.id
            } to isScrolling
        }
            .distinctUntilChanged()
            .collectLatest { (visibleIds, isScrolling) ->
                if (isScrolling) {
                    return@collectLatest
                }
                val pendingItems = visibleIds
                    .asSequence()
                    .mapNotNull(itemById::get)
                    .filter { item ->
                        val cacheKey = activeCacheKeysByItemId[item.id]
                            ?: buildCatalogHeadMetadataCacheKey(boardUrl, item)
                        resolvedCacheKeysByItemId[item.id] != cacheKey &&
                            shouldResolveCatalogThreadTitleFromHead(boardUrl, item.title, item.replyCount)
                    }
                    .take(MAX_CATALOG_HEAD_METADATA_BATCH_SIZE)
                    .toList()
                if (pendingItems.isEmpty()) {
                    return@collectLatest
                }
                val resolvedTitles = coroutineScope {
                    val semaphore = Semaphore(MAX_CATALOG_HEAD_METADATA_CONCURRENCY)
                    pendingItems.map { item ->
                        async {
                            semaphore.withPermit {
                                val cacheKey = activeCacheKeysByItemId[item.id]
                                    ?: buildCatalogHeadMetadataCacheKey(boardUrl, item)
                                val fallbackTitle = buildCatalogFallbackDisplayTitle(item)
                                val title = try {
                                    withTimeoutOrNull(CATALOG_HEAD_METADATA_TIMEOUT_MS) {
                                        repository.resolveCatalogDisplayTitle(boardUrl, item)
                                    }
                                        ?.takeIf { it.isNotBlank() }
                                        ?: fallbackTitle
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Throwable) {
                                    fallbackTitle
                                }
                                CatalogHeadMetadataResult(
                                    itemId = item.id,
                                    cacheKey = cacheKey,
                                    title = title
                                )
                            }
                        }
                    }.awaitAll()
                }
                resolvedTitles.forEach { result ->
                    if (activeCacheKeysByItemId[result.itemId] == result.cacheKey) {
                        resolvedTitlesByItemId[result.itemId] = result.title
                        resolvedCacheKeysByItemId[result.itemId] = result.cacheKey
                    }
                }
            }
    }

    return resolvedTitlesByItemId
}

internal fun buildCatalogFallbackDisplayTitle(item: CatalogItem): String {
    return item.title?.takeIf { it.isNotBlank() } ?: "無題"
}

internal data class CatalogHeadMetadataCacheKey(
    val boardUrl: String,
    val itemId: String,
    val replyCount: Int,
    val title: String
)

private data class CatalogHeadMetadataResult(
    val itemId: String,
    val cacheKey: CatalogHeadMetadataCacheKey,
    val title: String
)

internal fun buildCatalogHeadMetadataCacheKey(
    boardUrl: String?,
    item: CatalogItem
): CatalogHeadMetadataCacheKey {
    return CatalogHeadMetadataCacheKey(
        boardUrl = boardUrl.orEmpty(),
        itemId = item.id,
        replyCount = item.replyCount,
        title = item.title.orEmpty()
    )
}
