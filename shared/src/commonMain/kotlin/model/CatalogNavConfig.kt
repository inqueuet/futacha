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
        get() = CatalogNavEntryPlacement.BAR

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

fun isCatalogNavEntryRequiredInBar(id: CatalogNavEntryId): Boolean {
    return id == CatalogNavEntryId.Settings
}

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
    val shouldMigrateLegacyHiddenPastThreadSearch = isLegacyHiddenPastThreadSearchDefault(configs)
    val merged = CatalogNavEntryId.entries.map { id ->
        val existing = configs.firstOrNull { it.id == id }
        val placement = when {
            isCatalogNavEntryRequiredInBar(id) -> {
                CatalogNavEntryPlacement.BAR
            }
            shouldMigrateLegacyHiddenPastThreadSearch && id == CatalogNavEntryId.PastThreadSearch -> {
                CatalogNavEntryPlacement.BAR
            }
            else -> existing?.placement ?: id.defaultPlacement
        }
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

    val barItems = merged.filter { it.placement == CatalogNavEntryPlacement.BAR }
        .sortedWith(compareBy<CatalogNavEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
    if (barItems.size > maxBarItems) {
        val requiredIds = CatalogNavEntryId.entries
            .filter(::isCatalogNavEntryRequiredInBar)
            .toSet()
        val optionalLimit = (maxBarItems - requiredIds.size).coerceAtLeast(0)
        val retainedOptionalIds = barItems
            .filterNot { it.id in requiredIds }
            .take(optionalLimit)
            .map { it.id }
            .toSet()
        val retainedIds = requiredIds + retainedOptionalIds
        val overflow = barItems.filterNot { it.id in retainedIds }
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

private fun isLegacyHiddenPastThreadSearchDefault(configs: List<CatalogNavEntryConfig>): Boolean {
    val byId = configs.associateBy { it.id }
    if (CatalogNavEntryId.entries.any { it !in byId }) return false
    return CatalogNavEntryId.entries.all { id ->
        val config = byId.getValue(id)
        val legacyPlacement = if (id == CatalogNavEntryId.PastThreadSearch) {
            CatalogNavEntryPlacement.HIDDEN
        } else {
            CatalogNavEntryPlacement.BAR
        }
        config.placement == legacyPlacement && config.order == id.defaultOrder
    }
}
