package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.valoser.futacha.shared.mix.MixEntry
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.CatalogTitleCompletionPolicy
import com.valoser.futacha.shared.util.resolveCatalogTitleCompletionPolicy
import com.valoser.futacha.shared.util.shouldResolveCatalogThreadTitleFromHead
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

private const val MIX_HEAD_TIMEOUT_MS = 1_800L
internal const val MIX_HEAD_BATCH_SIZE = 12
internal const val MIX_HEAD_CONCURRENCY = 2

/**
 * The rows of a MIX whose title is to be completed from the thread head: visible, not done for this cache key, and
 * wanted by the row's own board policy. At most [MIX_HEAD_BATCH_SIZE] over the whole MIX, not per board.
 */
internal fun planMixHeadMetadata(
    entries: Map<String, MixEntry>,
    visibleKeys: List<String>,
    doneKeys: Map<String, CatalogHeadMetadataCacheKey>,
    policyOf: (String) -> CatalogTitleCompletionPolicy = ::resolveCatalogTitleCompletionPolicy
): List<MixEntry> = visibleKeys.asSequence().mapNotNull(entries::get).filter { e ->
    doneKeys[e.key.asString()] != buildCatalogHeadMetadataCacheKey(e.board.url, e.item) &&
        shouldResolveCatalogThreadTitleFromHead(e.board.url, e.item.title, e.item.replyCount, policyOf(e.board.url))
}.take(MIX_HEAD_BATCH_SIZE).toList()

@Composable
internal fun rememberMixHeadMetadataTitles(
    entries: List<MixEntry>,
    repository: BoardRepository,
    displayStyle: CatalogDisplayStyle,
    gridState: LazyGridState,
    listState: LazyListState,
    resolveHeadMetadata: Boolean,
    headerOffset: Int = 0,
    visibleEntries: List<MixEntry> = entries
): Map<String, String> {
    val titles = remember(repository) { mutableStateMapOf<String, String>() }
    val doneKeys = remember(repository) { mutableStateMapOf<String, CatalogHeadMetadataCacheKey>() }
    val byKey = remember(entries) { entries.associateBy { it.key.asString() } }
    val active = remember(entries) { byKey.mapValues { (_, e) -> buildCatalogHeadMetadataCacheKey(e.board.url, e.item) } }
    LaunchedEffect(active) {
        titles.keys.retainAll(active.keys)
        doneKeys.keys.retainAll(active.keys)
        doneKeys.filter { (k, v) -> active[k] != v }.keys.toList().forEach { titles.remove(it); doneKeys.remove(it) }
    }
    LaunchedEffect(entries, visibleEntries, repository, displayStyle, gridState, listState, resolveHeadMetadata) {
        if (!resolveHeadMetadata) return@LaunchedEffect
        snapshotFlow {
            val scrolling = if (displayStyle == CatalogDisplayStyle.Grid) gridState.isScrollInProgress else listState.isScrollInProgress
            val indices = if (displayStyle == CatalogDisplayStyle.Grid) gridState.layoutInfo.visibleItemsInfo.map { it.index }
                else listState.layoutInfo.visibleItemsInfo.map { it.index }
            indices.mapNotNull { visibleEntries.getOrNull(it - headerOffset)?.key?.asString() } to scrolling
        }.distinctUntilChanged().collectLatest { (visible, scrolling) ->
            if (scrolling) return@collectLatest
            while (true) {
                val pending = planMixHeadMetadata(byKey, visible, doneKeys)
                if (pending.isEmpty()) break
                val results = coroutineScope {
                    val gate = Semaphore(MIX_HEAD_CONCURRENCY)
                    pending.map { e ->
                        async {
                            gate.withPermit {
                                val policy = resolveCatalogTitleCompletionPolicy(e.board.url)
                                val fallback = buildCatalogFallbackDisplayTitle(e.item)
                                val title = try {
                                    withTimeoutOrNull(MIX_HEAD_TIMEOUT_MS) {
                                        repository.resolveCatalogDisplayTitle(e.board.url, e.item, policy.allowFallbackHeadScan)
                                    }?.takeIf { it.isNotBlank() } ?: fallback
                                } catch (error: CancellationException) { throw error } catch (_: Throwable) { fallback }
                                Triple(e.key.asString(), buildCatalogHeadMetadataCacheKey(e.board.url, e.item), title)
                            }
                        }
                    }.awaitAll()
                }
                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    results.forEach { (k, ck, t) -> if (active[k] == ck) { titles[k] = t; doneKeys[k] = ck } }
                }
            }
        }
    }
    return titles
}
