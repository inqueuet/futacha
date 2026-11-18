package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class CompatCatalogProjectionRequest(
    val items: List<CatalogItem>,
    val resolvedTitles: Map<String, String>,
    val searchQuery: String,
    val preference: CompatCatalogPreference,
    val priorityThreshold: Int,
    val catalogNgEnabled: Boolean,
    val watchWords: List<String>,
    val catalogImagePhashRules: List<CompatNgRule>,
    val catalogImagePhashes: Map<String, String>,
    val catalogImageNgPhashThreshold: Int,
    val catalogRuleIndex: CompatCatalogRuleIndex,
    val catalogSourceTitleLength: Int
)

/**
 * Runs the complete compatibility catalog projection on the bounded parsing
 * dispatcher. The caller's keyed coroutine owns the generation: replacing the
 * request cancels this work before an older result can be published.
 */
internal suspend fun calculateCompatCatalogProjection(
    request: CompatCatalogProjectionRequest,
    dispatcher: CoroutineDispatcher = AppDispatchers.parsing
): List<CatalogItem> = withContext(dispatcher) {
    val projectionContext = currentCoroutineContext()
    buildCompatCatalogProjection(request) {
        projectionContext.ensureActive()
    }
}

/**
 * Pure projection equivalent to the former synchronous Compose `remember`
 * block. [cancellationCheckpoint] is called throughout large list passes so a
 * superseded search/filter generation does not keep consuming the parsing
 * dispatcher until all 3000 entries have finished.
 */
internal fun buildCompatCatalogProjection(
    request: CompatCatalogProjectionRequest,
    cancellationCheckpoint: () -> Unit = {}
): List<CatalogItem> {
    fun checkpoint(index: Int) {
        if (index and 63 == 0) cancellationCheckpoint()
    }

    val itemsWithResolvedTitles = ArrayList<CatalogItem>(request.items.size)
    request.items.forEachIndexed { index, item ->
        checkpoint(index)
        val resolvedTitle = request.resolvedTitles[item.id]
            ?.takeIf(String::isNotBlank)
            ?: item.title
        // The whole title is matched (search, NG, watch words); it is cut to the display length only at the end (seq53).
        itemsWithResolvedTitles += if (resolvedTitle == item.title) item else item.copy(title = resolvedTitle)
    }

    val hiddenPhashItems = mutableSetOf<String>()
    itemsWithResolvedTitles.forEachIndexed { index, item ->
        checkpoint(index)
        val phash = request.catalogImagePhashes[item.id]
        if (
            phash != null && request.catalogImagePhashRules.any { rule ->
                CompatImagePhash.isSimilar(
                    phash,
                    rule.normalizedValue,
                    request.catalogImageNgPhashThreshold
                )
            }
        ) {
            hiddenPhashItems += item.id
        }
    }

    val ngFiltered = if (!request.catalogNgEnabled) {
        itemsWithResolvedTitles
    } else {
        itemsWithResolvedTitles.filterIndexed { index, item ->
            checkpoint(index)
            !request.catalogRuleIndex.hides(item) && item.id !in hiddenPhashItems
        }
    }

    val normalizedQuery = normalizeCompatCatalogMatchText(request.searchQuery)
    val filtered = if (normalizedQuery.isBlank()) {
        ngFiltered
    } else {
        ngFiltered.filterIndexed { index, item ->
            checkpoint(index)
            normalizeCompatCatalogMatchText(item.title.orEmpty()).contains(normalizedQuery) ||
                item.id.contains(request.searchQuery, ignoreCase = true)
        }
    }

    val normalizedWatchWords = request.watchWords.map(::normalizeCompatCatalogMatchText).filter(String::isNotBlank)
    cancellationCheckpoint()
    return projectCompatCatalogItems(
        items = filtered,
        replyPriorityEnabled = request.preference.replyPriorityEnabled,
        replyThreshold = request.priorityThreshold,
        showNonPriority = request.preference.showNonPriority,
        isExtracted = { item ->
            normalizedWatchWords.isNotEmpty() && normalizeCompatCatalogMatchText(item.title.orEmpty()).let { title ->
                normalizedWatchWords.any(title::contains)
            } || request.catalogRuleIndex.extracts(item)
        }
    ).map { item ->
        val cut = truncateCompatCatalogSourceTitle(item.title, request.catalogSourceTitleLength)
        if (cut == item.title) item else item.copy(title = cut)
    }
}

/**
 * Matches the first title truncation performed by reference app's CatalogLoader.
 *
 * Grid/list renderers apply their own, independent limit afterwards.  Search,
 * NG and watch words see the whole title; only the shown title is cut (seq53).
 */
internal fun truncateCompatCatalogSourceTitle(
    title: String?,
    sourceLimit: Int
): String? = title?.take(sourceLimit.coerceIn(10, 30))

/**
 * Applies the final reference app CatalogLoader projection without replacing the
 * selected Futaba sort order with a reply-count sort.
 *
 * The reference loader first keeps only entries at or above the configured
 * reply threshold, then promotes watched/extracted entries inside that group,
 * and finally appends the below-threshold group when requested.  Disabling
 * reply priority makes the effective threshold zero while extraction remains
 * active.
 */
internal fun projectCompatCatalogItems(
    items: List<CatalogItem>,
    replyPriorityEnabled: Boolean,
    replyThreshold: Int,
    showNonPriority: Boolean,
    isExtracted: (CatalogItem) -> Boolean
): List<CatalogItem> {
    val effectiveThreshold = if (replyPriorityEnabled) replyThreshold.coerceIn(0, 30) else 0
    val (priority, nonPriority) = items.partition { item ->
        item.replyCount >= effectiveThreshold
    }
    val (extracted, ordinary) = priority.partition(isExtracted)
    return buildList(items.size) {
        addAll(extracted)
        addAll(ordinary)
        if (showNonPriority || effectiveThreshold == 0) addAll(nonPriority)
    }
}

/** The whole title of each thread (the head metadata's when it has one), for matching the words after the cut (seq53). */
internal fun compatCatalogFullTitles(items: List<CatalogItem>, resolvedTitles: Map<String, String>): Map<String, String> {
    val result = HashMap<String, String>(items.size)
    items.forEach { item ->
        val title = resolvedTitles[item.id]?.takeIf(String::isNotBlank) ?: item.title ?: return@forEach
        if (item.id !in result) result[item.id] = title
    }
    return result
}
