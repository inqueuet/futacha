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
        val watchSourceModes = listOf(New, Old)
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
    return matchesNormalizedWatchWords(normalizeWatchWords(watchWords))
}

internal fun CatalogItem.matchesNormalizedWatchWords(normalizedWatchWords: List<String>): Boolean {
    val titleText = normalizeWatchSearchText(title.orEmpty())
    if (titleText.isEmpty()) return false
    return normalizedWatchWords.any { titleText.contains(it) }
}

/** Return user-entered labels in registration order, deduped by normalized value. */
internal fun CatalogItem.matchedWatchWords(watchWords: List<String>): List<String> {
    val titleText = normalizeWatchSearchText(title.orEmpty())
    if (titleText.isEmpty()) return emptyList()
    val seen = mutableSetOf<String>()
    return watchWords.mapNotNull { raw ->
        val normalized = normalizeWatchSearchText(raw)
        val label = raw.trim()
        label.takeIf {
            normalized.isNotBlank() && seen.add(normalized) && titleText.contains(normalized)
        }
    }
}

internal fun normalizeWatchWords(watchWords: List<String>): List<String> {
    return watchWords
        .mapNotNull { normalizeWatchSearchText(it).takeIf(String::isNotBlank) }
        .distinct()
}

internal fun normalizeWatchSearchText(value: String): String {
    return buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '\u3000' -> ' '
                    in '\uFF01'..'\uFF5E' -> char - 0xFEE0
                    else -> char
                }
            )
        }
    }.trim().lowercase()
}

/** Fold full-width katakana to hiragana for explicit catalog searches. */
internal fun normalizeCatalogSearchText(value: String): String = buildString(value.length) {
    normalizeWatchSearchText(value).forEach { char ->
        append(
            when (char) {
                in '\u30A1'..'\u30F6' -> char - 0x60
                '\u30FD' -> '\u309D'
                '\u30FE' -> '\u309E'
                else -> char
            }
        )
    }
}

private fun List<CatalogItem>.filterAndSortByWatchWords(
    watchWords: List<String>
): List<CatalogItem> {
    val normalizedWords = normalizeWatchWords(watchWords)
    if (normalizedWords.isEmpty()) return emptyList()

    return mapNotNull { item ->
        val matchCount = item.countNormalizedWatchWordMatches(normalizedWords)
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

private fun CatalogItem.countNormalizedWatchWordMatches(normalizedWords: List<String>): Int {
    val titleText = normalizeWatchSearchText(title.orEmpty())
    if (titleText.isEmpty()) return 0
    return normalizedWords.count { titleText.contains(it) }
}
