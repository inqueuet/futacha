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
    New("新順", "1"),
    Old("古順", "2"),
    Many("多い順", "3"),
    Few("少ない順", "4"),
    Momentum("勢い順", "6"),
    So("そ順", "8");

    companion object {
        val default = Many
    }

    fun applyLocalSort(items: List<CatalogItem>): List<CatalogItem> = when (this) {
        New -> items.sortedByDescending { it.numericId() }
        Old -> items.sortedBy { it.numericId() }
        Many, Catalog -> items.sortedByDescending { it.replyCount }
        Few -> items.sortedBy { it.replyCount }
        Momentum -> items.sortedByDescending { entry ->
            val divisor = ((entry.numericId() % 10) + 1).toDouble()
            entry.replyCount.toDouble() / divisor
        }
        So -> items
    }
}
