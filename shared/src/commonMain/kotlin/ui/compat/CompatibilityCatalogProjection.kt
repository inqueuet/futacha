package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem

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
