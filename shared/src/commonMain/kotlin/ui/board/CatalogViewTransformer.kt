package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.model.mergePageParseWarnings
import com.valoser.futacha.shared.model.matchesNormalizedWatchWords
import com.valoser.futacha.shared.model.normalizeWatchWords
import com.valoser.futacha.shared.model.normalizeWatchSearchText
import com.valoser.futacha.shared.model.normalizeCatalogSearchText
import com.valoser.futacha.shared.model.numericId
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val CATALOG_WATCH_SOURCE_TIMEOUT_MS = 8_000L
private const val CATALOG_WATCH_SOURCE_CACHE_TTL_MS = 3 * 60 * 1000L

internal fun buildVisibleCatalogItems(
    items: List<CatalogItem>,
    mode: CatalogMode,
    watchWords: List<String>,
    catalogNgWords: List<String>,
    catalogNgFilteringEnabled: Boolean,
    query: String
): List<CatalogItem> {
    val deduplicatedItems = items.deduplicateByIdentity()
    val normalizedWatchWords = normalizeWatchWords(watchWords)
    val normalizedCatalogNgWords = normalizeCatalogFilterWords(
        words = catalogNgWords,
        enabled = catalogNgFilteringEnabled
    )
    val normalizedQuery = normalizeCatalogQuery(query)
    if (mode == CatalogMode.WatchWords && normalizedWatchWords.isEmpty()) {
        return emptyList()
    }
    if (
        mode != CatalogMode.WatchWords &&
        normalizedWatchWords.isEmpty() &&
        normalizedCatalogNgWords.isEmpty() &&
        normalizedQuery == null
    ) {
        return deduplicatedItems
    }
    return deduplicatedItems
        .toCatalogFilterCandidates()
        .let { candidates ->
            if (mode == CatalogMode.WatchWords) {
                candidates.filterAndSortByNormalizedWatchWords(normalizedWatchWords)
            } else {
                candidates
            }
        }
        .let { prioritizeNormalizedWatchWordMatches(it, mode, normalizedWatchWords) }
        .filterByCatalogNgWords(normalizedCatalogNgWords)
        .filterByNormalizedQuery(normalizedQuery)
        .map { it.item }
}

private data class CatalogFilterCandidate(
    val item: CatalogItem,
    val normalizedTitle: String,
    val normalizedId: String,
    val normalizedThreadUrl: String
)

private fun List<CatalogItem>.toCatalogFilterCandidates(): List<CatalogFilterCandidate> {
    return map { item ->
        CatalogFilterCandidate(
            item = item,
            normalizedTitle = normalizeWatchSearchText(item.title.orEmpty()),
            normalizedId = item.id.lowercase(),
            normalizedThreadUrl = item.threadUrl.lowercase()
        )
    }
}

private fun normalizeCatalogQuery(query: String): String? {
    return normalizeCatalogSearchText(query).takeIf(String::isNotEmpty)
}

private fun normalizeCatalogFilterWords(
    words: List<String>,
    enabled: Boolean
): List<String> {
    if (!enabled) return emptyList()
    return words
        .mapNotNull { normalizeWatchSearchText(it).takeIf(String::isNotBlank) }
        .distinct()
}

private fun List<CatalogFilterCandidate>.filterAndSortByNormalizedWatchWords(
    normalizedWatchWords: List<String>
): List<CatalogFilterCandidate> {
    if (normalizedWatchWords.isEmpty()) return emptyList()
    return mapNotNull { candidate ->
        val matchCount = candidate.countNormalizedWatchWordMatches(normalizedWatchWords)
        if (matchCount == 0) return@mapNotNull null
        CatalogWatchWordMatch(candidate = candidate, matchCount = matchCount)
    }.sortedWith(
        compareByDescending<CatalogWatchWordMatch> { it.matchCount }
            .thenByDescending { it.candidate.item.replyCount }
            .thenByDescending { it.candidate.item.numericId() }
    ).map { it.candidate }
}

private data class CatalogWatchWordMatch(
    val candidate: CatalogFilterCandidate,
    val matchCount: Int
)

private fun CatalogFilterCandidate.countNormalizedWatchWordMatches(
    normalizedWatchWords: List<String>
): Int {
    if (normalizedTitle.isEmpty()) return 0
    return normalizedWatchWords.count { normalizedTitle.contains(it) }
}

private fun prioritizeNormalizedWatchWordMatches(
    candidates: List<CatalogFilterCandidate>,
    mode: CatalogMode,
    normalizedWatchWords: List<String>
): List<CatalogFilterCandidate> {
    if (mode == CatalogMode.WatchWords || normalizedWatchWords.isEmpty()) return candidates
    val matched = ArrayList<CatalogFilterCandidate>()
    val unmatched = ArrayList<CatalogFilterCandidate>()
    candidates.forEach { candidate ->
        if (candidate.matchesNormalizedWatchWords(normalizedWatchWords)) {
            matched += candidate
        } else {
            unmatched += candidate
        }
    }
    if (matched.isEmpty()) return candidates
    return matched + unmatched
}

private fun CatalogFilterCandidate.matchesNormalizedWatchWords(
    normalizedWatchWords: List<String>
): Boolean {
    if (normalizedTitle.isEmpty()) return false
    return normalizedWatchWords.any { normalizedTitle.contains(it) }
}

private fun List<CatalogFilterCandidate>.filterByCatalogNgWords(
    normalizedCatalogNgWords: List<String>
): List<CatalogFilterCandidate> {
    if (normalizedCatalogNgWords.isEmpty()) return this
    return filterNot { candidate ->
        candidate.matchesCatalogNgWords(normalizedCatalogNgWords)
    }
}

private fun CatalogFilterCandidate.matchesCatalogNgWords(
    normalizedCatalogNgWords: List<String>
): Boolean {
    if (normalizedTitle.isEmpty()) return false
    return normalizedCatalogNgWords.any { normalizedTitle.contains(it) }
}

private fun List<CatalogFilterCandidate>.filterByNormalizedQuery(
    normalizedQuery: String?
): List<CatalogFilterCandidate> {
    normalizedQuery ?: return this
    return filter { candidate ->
        normalizeCatalogSearchText(candidate.item.title.orEmpty()).contains(normalizedQuery) ||
            candidate.normalizedId.contains(normalizedQuery) ||
            candidate.normalizedThreadUrl.contains(normalizedQuery)
    }
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
    val normalizedQuery = normalizeCatalogSearchText(trimmedQuery)
    return filter { item ->
        val titleMatch = normalizeCatalogSearchText(item.title.orEmpty()).contains(normalizedQuery)
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
        .mapNotNull { normalizeWatchSearchText(it).takeIf(String::isNotBlank) }
    if (wordFilters.isEmpty()) return this
    return filterNot { item ->
        matchesCatalogNgWords(item, wordFilters)
    }
}

internal fun matchesCatalogNgWords(
    item: CatalogItem,
    wordFilters: List<String>
): Boolean {
    val titleText = normalizeWatchSearchText(item.title.orEmpty())
    if (titleText.isEmpty()) return false
    return wordFilters.any { titleText.contains(it) }
}

internal suspend fun loadCatalogItemsForMode(
    boardUrl: String,
    mode: CatalogMode,
    watchWords: List<String> = emptyList(),
    onWatchWordsPartial: suspend (CatalogPageContent) -> Unit = {},
    fetchCatalog: suspend (String, CatalogMode) -> CatalogPageContent
): CatalogPageContent {
    if (mode != CatalogMode.WatchWords) {
        val displayCatalog = fetchCatalog(boardUrl, mode)
        val normalizedWatchWords = normalizeWatchWords(watchWords)
        if (normalizedWatchWords.isEmpty()) {
            return displayCatalog
        }
        onWatchWordsPartial(displayCatalog)
        val watchSourceCatalogs = fetchAdditionalWatchSourceCatalogs(
            boardUrl = boardUrl,
            excludeMode = mode,
            fetchCatalog = fetchCatalog
        )
        if (watchSourceCatalogs.isEmpty()) {
            return displayCatalog
        }
        val watchMatchedItems = mergeWatchSourceCatalogItems(
            watchSourceCatalogs.map { it.items }
        ).filter { item ->
            item.matchesNormalizedWatchWords(normalizedWatchWords)
        }
        if (watchMatchedItems.isEmpty()) {
            return displayCatalog
        }
        return displayCatalog.copy(
            items = mergeWatchSourceCatalogItems(listOf(watchMatchedItems, displayCatalog.items)),
            parseWarning = mergePageParseWarnings(listOf(displayCatalog.parseWarning) + watchSourceCatalogs.map { it.parseWarning })
        )
    }

    val successfulCatalogs = mutableListOf<CatalogPageContent>()
    var firstError: Throwable? = null

    val firstSourceMode = CatalogMode.watchSourceModes.firstOrNull()
        ?: throw IllegalStateException("監視モード用のカタログ取得元がありません")

    runCatchingCatalogSource {
        withTimeoutOrNull(CATALOG_WATCH_SOURCE_TIMEOUT_MS) {
            fetchCatalog(boardUrl, firstSourceMode)
        } ?: throw IllegalStateException("Catalog watch source timed out: $firstSourceMode")
    }.fold(
        onSuccess = { catalog ->
            successfulCatalogs += catalog
            onWatchWordsPartial(buildWatchWordsCatalogPage(successfulCatalogs))
        },
        onFailure = { error ->
            firstError = error
        }
    )

    val remainingModes = CatalogMode.watchSourceModes.drop(1)
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

private suspend fun fetchAdditionalWatchSourceCatalogs(
    boardUrl: String,
    excludeMode: CatalogMode,
    fetchCatalog: suspend (String, CatalogMode) -> CatalogPageContent
): List<CatalogPageContent> {
    val sourceModes = CatalogMode.watchSourceModes.filterNot { it == excludeMode }
    if (sourceModes.isEmpty()) return emptyList()
    readCachedAdditionalWatchSourceCatalogs(boardUrl, sourceModes)?.let { cached ->
        return cached
    }
    val successfulCatalogs = mutableListOf<CatalogPageContent>()
    var failureCount = 0
    coroutineScope {
        val fetchSemaphore = Semaphore(2)
        val resultChannel = Channel<Result<CatalogPageContent>>(capacity = sourceModes.size)
        sourceModes.forEach { sourceMode ->
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
        repeat(sourceModes.size) {
            resultChannel.receive().fold(
                onSuccess = { catalog ->
                    successfulCatalogs += catalog
                },
                onFailure = {
                    failureCount += 1
                }
            )
        }
    }
    if (failureCount == 0 && successfulCatalogs.size == sourceModes.size) {
        writeCachedAdditionalWatchSourceCatalogs(boardUrl, sourceModes, successfulCatalogs)
    } else {
        clearCatalogWatchSourceCache(boardUrl)
    }
    return successfulCatalogs
}

internal suspend fun clearCatalogWatchSourceCache(boardUrl: String? = null) {
    CatalogWatchSourceCache.clear(boardUrl)
}

private suspend fun readCachedAdditionalWatchSourceCatalogs(
    boardUrl: String,
    sourceModes: List<CatalogMode>
): List<CatalogPageContent>? {
    return CatalogWatchSourceCache.read(boardUrl, sourceModes)
}

private suspend fun writeCachedAdditionalWatchSourceCatalogs(
    boardUrl: String,
    sourceModes: List<CatalogMode>,
    catalogs: List<CatalogPageContent>
) {
    CatalogWatchSourceCache.write(boardUrl, sourceModes, catalogs)
}

@OptIn(ExperimentalTime::class)
private object CatalogWatchSourceCache {
    private data class Key(
        val boardUrl: String,
        val sourceModes: List<CatalogMode>
    )

    private data class Entry(
        val createdAtEpochMillis: Long,
        val catalogs: List<CatalogPageContent>
    )

    private val mutex = Mutex()
    private val entries = mutableMapOf<Key, Entry>()

    suspend fun read(boardUrl: String, sourceModes: List<CatalogMode>): List<CatalogPageContent>? {
        val key = Key(boardUrl, sourceModes)
        val now = Clock.System.now().toEpochMilliseconds()
        return mutex.withLock {
            removeExpiredEntries(now)
            val entry = entries[key] ?: return@withLock null
            entry.catalogs
        }
    }

    suspend fun write(
        boardUrl: String,
        sourceModes: List<CatalogMode>,
        catalogs: List<CatalogPageContent>
    ) {
        if (catalogs.isEmpty()) return
        val key = Key(boardUrl, sourceModes)
        val now = Clock.System.now().toEpochMilliseconds()
        mutex.withLock {
            removeExpiredEntries(now)
            entries[key] = Entry(now, catalogs)
            if (entries.size > CATALOG_WATCH_SOURCE_CACHE_MAX_ENTRIES) {
                entries.entries
                    .sortedBy { it.value.createdAtEpochMillis }
                    .take(entries.size - CATALOG_WATCH_SOURCE_CACHE_MAX_ENTRIES)
                    .forEach { entries.remove(it.key) }
            }
        }
    }

    private fun removeExpiredEntries(nowEpochMillis: Long) {
        entries.entries.removeAll { (_, entry) ->
            hasEpochIntervalElapsed(
                nowEpochMillis,
                entry.createdAtEpochMillis,
                CATALOG_WATCH_SOURCE_CACHE_TTL_MS + 1L
            )
        }
    }

    suspend fun clear(boardUrl: String?) {
        mutex.withLock {
            if (boardUrl == null) {
                entries.clear()
            } else {
                entries.keys.removeAll { it.boardUrl == boardUrl }
            }
        }
    }
}

private const val CATALOG_WATCH_SOURCE_CACHE_MAX_ENTRIES = 8

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
        }.orEmpty(),
        parseWarning = mergePageParseWarnings(successfulCatalogs.map { it.parseWarning })
    )
}
