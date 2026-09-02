package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.normalizeCatalogSearchText
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
        itemsWithResolvedTitles += item.copy(
            title = truncateCompatCatalogSourceTitle(
                resolvedTitle,
                request.catalogSourceTitleLength
            )
        )
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

    val normalizedQuery = normalizeCatalogSearchText(request.searchQuery)
    val filtered = if (normalizedQuery.isBlank()) {
        ngFiltered
    } else {
        ngFiltered.filterIndexed { index, item ->
            checkpoint(index)
            normalizeCatalogSearchText(item.title.orEmpty()).contains(normalizedQuery) ||
                item.id.contains(request.searchQuery, ignoreCase = true)
        }
    }

    cancellationCheckpoint()
    return projectCompatCatalogItems(
        items = filtered,
        replyPriorityEnabled = request.preference.replyPriorityEnabled,
        replyThreshold = request.priorityThreshold,
        showNonPriority = request.preference.showNonPriority,
        isExtracted = { item ->
            request.watchWords.any { word ->
                item.title.orEmpty().contains(word, ignoreCase = true)
            } || request.catalogRuleIndex.extracts(item)
        }
    )
}

/**
 * Matches the first title truncation performed by 1.apk's CatalogLoader.
 *
 * Grid/list renderers apply their own, independent limit afterwards.  Keeping
 * this source-stage truncation separate also ensures search, NG and extraction
 * never inspect characters that the reference parser already discarded.
 */
internal fun truncateCompatCatalogSourceTitle(
    title: String?,
    sourceLimit: Int
): String? = title?.take(sourceLimit.coerceIn(10, 30))

/**
 * Applies the final 1.apk CatalogLoader projection without replacing the
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
