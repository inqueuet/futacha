package com.valoser.futacha.shared.model

/**
 * Mirrors Futaba's catalog mode tabs.
 *
 * Each entry corresponds to a `sort` query parameter observed in `/example/catalog*.txt`.
 */
enum class CatalogMode(
    val label: String,
    val sortParam: String?
) {
    Catalog("カタログ", null),
    WatchWords("監視", null),
    New("新順", "1"),
    Old("古順", "2"),
    Many("多い順", "3"),
    Few("少ない順", "4"),
    Momentum("勢い順", "6"),
    So("そ順", "8");

    companion object {
        val default = Many
        val watchSourceModes = listOf(Catalog, New, Old, Many, Few, Momentum, So)
    }

    fun applyClientTransform(
        items: List<CatalogItem>,
        watchWords: List<String>
    ): List<CatalogItem> = when (this) {
        WatchWords -> items.filterAndSortByWatchWords(watchWords)
        else -> items
    }
}

internal fun CatalogItem.matchesWatchWords(watchWords: List<String>): Boolean {
    val titleText = title?.lowercase().orEmpty()
    if (titleText.isEmpty()) return false
    return normalizeWatchWords(watchWords).any { titleText.contains(it) }
}

private fun normalizeWatchWords(watchWords: List<String>): List<String> {
    return watchWords
        .mapNotNull { it.trim().takeIf(String::isNotBlank)?.lowercase() }
        .distinct()
}

private fun List<CatalogItem>.filterAndSortByWatchWords(
    watchWords: List<String>
): List<CatalogItem> {
    val normalizedWords = normalizeWatchWords(watchWords)
    if (normalizedWords.isEmpty()) return emptyList()

    return mapNotNull { item ->
        val titleText = item.title?.lowercase().orEmpty()
        if (titleText.isEmpty()) return@mapNotNull null
        val matchCount = normalizedWords.count { titleText.contains(it) }
        if (matchCount == 0) return@mapNotNull null
        WatchWordMatch(item = item, matchCount = matchCount)
    }.sortedWith(
        compareByDescending<WatchWordMatch> { it.matchCount }
            .thenByDescending { it.item.replyCount }
            .thenByDescending { it.item.numericId() }
    ).map { it.item }
}

private data class WatchWordMatch(
    val item: CatalogItem,
    val matchCount: Int
)
