package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem

/** Catalog probes update known totals, never the read position or the open-tab set. */
fun catalogReplyCountsByUrl(items: List<CatalogItem>): Map<String, Int> = buildMap {
    items.forEach { item ->
        val url = canonicalizeThreadUrl(item.threadUrl)?.canonicalUrl ?: return@forEach
        if (item.replyCount >= 0) put(url, maxOf(get(url) ?: 0, item.replyCount))
    }
}

internal fun CompatTab.withCatalogReplyCount(counts: Map<String, Int>, fetchedAt: Long): CompatTab {
    val count = counts[canonicalUrl] ?: return this
    if (fetchedAt < contentUpdatedAtEpochMillis || count <= replyCount) return this
    return copy(replyCount = count, contentUpdatedAtEpochMillis = fetchedAt)
}

fun updatedCatalogReplyCount(tab: CompatTab, counts: Map<String, Int>, fetchedAt: Long): CompatTab =
    tab.withCatalogReplyCount(counts, fetchedAt)

/** Key by full URL so identical thread numbers on different boards never share unread state. */
fun buildCatalogReplyIndicators(
    items: List<CatalogItem>, tabs: List<CompatTab>, deltas: Map<String, Int>
): Map<String, CompatCatalogReplyIndicator> {
    val byUrl = tabs.associateBy { it.canonicalUrl }
    return buildMap {
        items.forEach { item ->
            val tab = canonicalizeThreadUrl(item.threadUrl)?.canonicalUrl?.let(byUrl::get)
            resolveCompatCatalogReplyIndicator(item.replyCount, tab?.checkedReplyCount,
                deltas[item.compatCatalogReplyDeltaKey()])?.let { put(item.threadUrl, it) }
        }
    }
}
