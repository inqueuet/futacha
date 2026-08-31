package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem

internal const val COMPAT_CATALOG_DIE_BORDER_RATIO = 0.9

data class CompatCatalogGenerationDiff(
    val vanishedWithin: List<CatalogItem>,
    val vanishedBottom: List<CatalogItem>
)

enum class CompatCatalogReplyIndicatorKind { UNREAD, PREVIOUS_CATALOG }

data class CompatCatalogReplyIndicator(
    val count: Int,
    val kind: CompatCatalogReplyIndicatorKind
)

/** Visited-thread unread (red) takes precedence over generation diff (gray). */
fun resolveCompatCatalogReplyIndicator(
    currentReplyCount: Int,
    checkedReplyCount: Int?,
    previousCatalogDelta: Int?
): CompatCatalogReplyIndicator? {
    val unread = checkedReplyCount?.let { (currentReplyCount - it).coerceAtLeast(0) }
    if (unread != null) {
        return unread.takeIf { it > 0 }?.let {
            CompatCatalogReplyIndicator(it, CompatCatalogReplyIndicatorKind.UNREAD)
        }
    }
    return previousCatalogDelta?.takeIf { it > 0 }?.let {
        CompatCatalogReplyIndicator(it, CompatCatalogReplyIndicatorKind.PREVIOUS_CATALOG)
    }
}

/**
 * Keeps historical dropped rows outside the live generation used for the
 * next diff, while presenting them after every live catalog item.  This is
 * deliberately a projection only: Undo can hide newly appended rows without
 * deleting the durable "消えたスレ" record.
 */
fun appendCompatDroppedCatalogItems(
    current: List<CatalogItem>,
    dropped: List<CompatDroppedCatalogItem>,
    enabled: Boolean,
    contentReady: Boolean = true,
    suppressedThreadIds: Set<String> = emptySet()
): List<CatalogItem> {
    if (!enabled || !contentReady || dropped.isEmpty()) return current
    val knownIds = current.mapTo(mutableSetOf()) { it.id.ifBlank { it.threadUrl } }
    val appended = dropped
        .asSequence()
        .filterNot { it.item.id in suppressedThreadIds }
        .sortedByDescending(CompatDroppedCatalogItem::lastSeenAtEpochMillis)
        .map(CompatDroppedCatalogItem::item)
        .filter { knownIds.add(it.id.ifBlank { it.threadUrl }) }
        .toList()
    return current + appended
}

/**
 * Returns one entry for every item in the current generation, including zero.
 * Keeping zeroes lets the UI distinguish "this refresh had no increase" from
 * "there is no generation baseline yet" and only fall back to the tab's
 * unread count in the latter case.
 */
fun buildCompatCatalogReplyDeltas(
    current: List<CatalogItem>,
    previous: List<CatalogItem>
): Map<String, Int> {
    if (current.isEmpty() || previous.isEmpty()) return emptyMap()
    val previousCounts = previous.associate { item ->
        item.compatCatalogReplyDeltaKey() to item.replyCount
    }
    return current.associate { item ->
        val previousCount = previousCounts[item.compatCatalogReplyDeltaKey()]
        item.compatCatalogReplyDeltaKey() to if (previousCount == null) {
            0
        } else {
            (item.replyCount - previousCount).coerceAtLeast(0)
        }
    }
}

fun CatalogItem.compatCatalogReplyDeltaKey(): String =
    compatCatalogThreadKey()?.toString() ?: canonicalizeThreadUrl(threadUrl)?.canonicalUrl ?: threadUrl

fun diffCompatCatalogGenerations(
    current: List<CatalogItem>,
    previous: List<CatalogItem>,
    requestedThreadCount: Int,
    enabled: Boolean
): CompatCatalogGenerationDiff {
    if (!enabled || current.isEmpty() || previous.isEmpty()) {
        return CompatCatalogGenerationDiff(emptyList(), emptyList())
    }
    val currentWithKeys = current.mapNotNull { item -> item.compatCatalogThreadKey()?.let { it to item } }
    if (currentWithKeys.isEmpty()) return CompatCatalogGenerationDiff(emptyList(), emptyList())
    val currentKeys = currentWithKeys.mapTo(mutableSetOf()) { it.first }
    val currentDescending = currentWithKeys.map { it.first }.sortedDescending()
    val minimumCurrentKey = currentDescending.last()
    val fetchedPastBottom = current.size < requestedThreadCount.coerceAtLeast(0)
    val within = mutableListOf<CatalogItem>()
    val bottom = mutableListOf<CatalogItem>()

    previous.mapNotNull { item -> item.compatCatalogThreadKey()?.let { it to item } }
        .sortedByDescending { it.first }
        .forEach { (previousKey, item) ->
            if (previousKey in currentKeys) return@forEach
            if (previousKey < minimumCurrentKey) {
                if (fetchedPastBottom) bottom += item
                return@forEach
            }
            val newerCount = currentDescending.countDescendingValuesGreaterThan(previousKey)
            if (newerCount < currentDescending.size * COMPAT_CATALOG_DIE_BORDER_RATIO) {
                within += item
            } else {
                bottom += item
            }
        }
    return CompatCatalogGenerationDiff(within, bottom)
}

private fun List<Long>.countDescendingValuesGreaterThan(target: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
        val middle = low + (high - low) / 2
        if (this[middle] > target) {
            low = middle + 1
        } else {
            high = middle
        }
    }
    return low
}

fun buildCompatCatalogItemStates(
    items: List<CatalogItem>,
    previousStates: Map<String, CompatCatalogItemState>,
    fetchedAtEpochMillis: Long,
    sort: CompatCatalogSort,
    requestedThreadCount: Int
): Map<String, CompatCatalogItemState> {
    if (items.isEmpty()) return emptyMap()
    val fallbackSeconds = fetchedAtEpochMillis / 1_000L
    val created = items.associate { item ->
        item.id to (
            previousStates[item.id]?.createdAtEpochSeconds?.takeIf { it > 0L }
                ?: item.compatCatalogImageCreatedAtEpochSeconds()
                ?: fallbackSeconds
            )
    }
    val shouldClassifyOld = sort != CompatCatalogSort.NEW || requestedThreadCount >= 2_000
    val oldest = created.values.minOrNull() ?: fallbackSeconds
    val newest = created.values.maxOrNull() ?: fallbackSeconds
    val oldBorder = oldest + (newest - oldest) / 10L
    return created.mapValues { (_, createdAt) ->
        CompatCatalogItemState(
            createdAtEpochSeconds = createdAt,
            isOld = shouldClassifyOld && createdAt < oldBorder
        )
    }
}

private fun CatalogItem.compatCatalogThreadKey(): Long? = id.toLongOrNull()
    ?: threadUrl.substringAfterLast("/res/", "").substringBefore('.').toLongOrNull()

private fun CatalogItem.compatCatalogImageCreatedAtEpochSeconds(): Long? {
    val url = thumbnailUrl ?: fullImageUrl ?: return null
    val fileName = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val millis = fileName.substringBefore("s.", missingDelimiterValue = "").toLongOrNull() ?: return null
    return millis.takeIf { it > 0L }?.div(1_000L)
}
