package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogItem

/**
 * Builds catalog keys from board and thread identity so projection changes do
 * not transfer remembered image or interaction state to a different thread.
 * Malformed duplicate IDs first fall back to URL, then to an occurrence number
 * only when the duplicate rows have no distinct identity left.
 */
internal fun buildCatalogItemLazyKeys(
    boardIdentity: String,
    items: List<CatalogItem>
): List<String> {
    val stableBoardIdentity = boardIdentity.trim().ifEmpty { "unknown-board" }
    val baseKeys = items.map { item ->
        val threadIdentity = item.id.trim().ifEmpty {
            item.threadUrl.trim().ifEmpty { "unknown-thread" }
        }
        "catalog-item:$stableBoardIdentity:$threadIdentity"
    }
    val baseCounts = baseKeys.groupingBy { it }.eachCount()
    val urlFallbackKeys = items.indices.map { index ->
        val stableUrl = items[index].threadUrl.trim().ifEmpty { "missing-url" }
        "${baseKeys[index]}:url:$stableUrl"
    }
    val urlFallbackCounts = urlFallbackKeys.groupingBy { it }.eachCount()
    val occurrenceByFallback = mutableMapOf<String, Int>()

    return items.indices.map { index ->
        val baseKey = baseKeys[index]
        if (baseCounts.getValue(baseKey) == 1) {
            baseKey
        } else {
            val urlFallbackKey = urlFallbackKeys[index]
            if (urlFallbackCounts.getValue(urlFallbackKey) == 1) {
                urlFallbackKey
            } else {
                val occurrence = occurrenceByFallback[urlFallbackKey] ?: 0
                occurrenceByFallback[urlFallbackKey] = occurrence + 1
                "$urlFallbackKey:occurrence:$occurrence"
            }
        }
    }
}
