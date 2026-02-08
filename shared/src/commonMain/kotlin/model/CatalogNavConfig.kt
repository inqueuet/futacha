package com.valoser.futacha.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class CatalogNavEntryPlacement {
    BAR,
    HIDDEN
}

@Serializable
enum class CatalogNavEntryId {
    CreateThread,
    ScrollToTop,
    RefreshCatalog,
    PastThreadSearch,
    Mode,
    Settings;

    val defaultPlacement: CatalogNavEntryPlacement
        get() = when (this) {
            PastThreadSearch -> CatalogNavEntryPlacement.HIDDEN
            else -> CatalogNavEntryPlacement.BAR
        }

    val defaultOrder: Int
        get() = when (this) {
            CreateThread -> 0
            ScrollToTop -> 1
            RefreshCatalog -> 2
            PastThreadSearch -> 3
            Mode -> 4
            Settings -> 5
        }
}

@Serializable
data class CatalogNavEntryConfig(
    val id: CatalogNavEntryId,
    val placement: CatalogNavEntryPlacement = id.defaultPlacement,
    val order: Int = id.defaultOrder
)

fun defaultCatalogNavEntries(): List<CatalogNavEntryConfig> {
    return CatalogNavEntryId.entries.map { id ->
        CatalogNavEntryConfig(
            id = id,
            placement = id.defaultPlacement,
            order = id.defaultOrder
        )
    }
}

fun normalizeCatalogNavEntries(
    configs: List<CatalogNavEntryConfig>,
    maxBarItems: Int = 6
): List<CatalogNavEntryConfig> {
    val merged = CatalogNavEntryId.entries.map { id ->
        val existing = configs.firstOrNull { it.id == id }
        val placement = existing?.placement ?: id.defaultPlacement
        val order = existing?.order ?: id.defaultOrder
        CatalogNavEntryConfig(id = id, placement = placement, order = order)
    }.toMutableList()

    fun reorderPlacement(targetPlacement: CatalogNavEntryPlacement) {
        val items = merged.filter { it.placement == targetPlacement }
            .sortedWith(compareBy<CatalogNavEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
        items.forEachIndexed { index, config ->
            val idx = merged.indexOfFirst { it.id == config.id }
            if (idx >= 0) {
                merged[idx] = config.copy(order = index)
            }
        }
    }

    // Keep this feature implemented, but do not expose it from menu UI.
    merged.indexOfFirst { it.id == CatalogNavEntryId.PastThreadSearch }.takeIf { it >= 0 }?.let { idx ->
        merged[idx] = merged[idx].copy(placement = CatalogNavEntryPlacement.HIDDEN)
    }

    val barItems = merged.filter { it.placement == CatalogNavEntryPlacement.BAR }
        .sortedWith(compareBy<CatalogNavEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
    if (barItems.size > maxBarItems) {
        val overflow = barItems.drop(maxBarItems)
        overflow.forEach { item ->
            val idx = merged.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                merged[idx] = item.copy(placement = CatalogNavEntryPlacement.HIDDEN)
            }
        }
    }

    val hasBar = merged.any { it.placement == CatalogNavEntryPlacement.BAR }
    if (!hasBar) {
        merged.indexOfFirst { it.id == CatalogNavEntryId.Settings }.takeIf { it >= 0 }?.let { idx ->
            merged[idx] = merged[idx].copy(placement = CatalogNavEntryPlacement.BAR, order = 0)
        }
    }

    reorderPlacement(CatalogNavEntryPlacement.BAR)
    reorderPlacement(CatalogNavEntryPlacement.HIDDEN)
    return merged
}
