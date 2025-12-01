package com.valoser.futacha.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThreadMenuItemId {
    Reply,
    ScrollToTop,
    ScrollToBottom,
    Refresh,
    Gallery,
    Save,
    Filter,
    Settings;

    val defaultOrder: Int
        get() = when (this) {
            Reply -> 0
            ScrollToTop -> 1
            ScrollToBottom -> 2
            Refresh -> 3
            Gallery -> 4
            Save -> 5
            Filter -> 6
            Settings -> 7
        }
}

@Serializable
data class ThreadMenuItemConfig(
    val id: ThreadMenuItemId,
    val isEnabled: Boolean = true,
    val order: Int = id.defaultOrder
)

fun defaultThreadMenuConfig(): List<ThreadMenuItemConfig> {
    return ThreadMenuItemId.entries.map { id ->
        ThreadMenuItemConfig(
            id = id,
            isEnabled = true,
            order = id.defaultOrder
        )
    }
}

fun normalizeThreadMenuConfig(config: List<ThreadMenuItemConfig>): List<ThreadMenuItemConfig> {
    val merged = ThreadMenuItemId.entries.map { id ->
        val existing = config.firstOrNull { it.id == id }
        val enabled = when {
            id == ThreadMenuItemId.Settings -> true
            existing != null -> existing.isEnabled
            else -> true
        }
        val order = existing?.order ?: id.defaultOrder
        ThreadMenuItemConfig(id = id, isEnabled = enabled, order = order)
    }

    val hasActionEnabled = merged.any { it.id != ThreadMenuItemId.Settings && it.isEnabled }
    val ensuredEnabled = if (hasActionEnabled) {
        merged
    } else {
        merged.map { item ->
            if (item.id == ThreadMenuItemId.Refresh) item.copy(isEnabled = true) else item
        }
    }

    return ensuredEnabled
        .sortedWith(compareBy<ThreadMenuItemConfig> { it.order }.thenBy { it.id.defaultOrder })
        .mapIndexed { index, item ->
            item.copy(order = index)
        }
}

@Serializable
enum class ThreadSettingsMenuItemId {
    NgManagement,
    ExternalApp,
    ReadAloud,
    Privacy;

    val defaultOrder: Int
        get() = when (this) {
            NgManagement -> 0
            ExternalApp -> 1
            ReadAloud -> 2
            Privacy -> 3
        }
}

@Serializable
data class ThreadSettingsMenuItemConfig(
    val id: ThreadSettingsMenuItemId,
    val isEnabled: Boolean = true,
    val order: Int = id.defaultOrder
)

fun defaultThreadSettingsMenuConfig(): List<ThreadSettingsMenuItemConfig> {
    return ThreadSettingsMenuItemId.entries.map { id ->
        ThreadSettingsMenuItemConfig(
            id = id,
            isEnabled = true,
            order = id.defaultOrder
        )
    }
}

fun normalizeThreadSettingsMenuConfig(config: List<ThreadSettingsMenuItemConfig>): List<ThreadSettingsMenuItemConfig> {
    val merged = ThreadSettingsMenuItemId.entries.map { id ->
        val existing = config.firstOrNull { it.id == id }
        ThreadSettingsMenuItemConfig(
            id = id,
            isEnabled = existing?.isEnabled ?: true,
            order = existing?.order ?: id.defaultOrder
        )
    }
    val hasEnabled = merged.any { it.isEnabled }
    val ensured = if (hasEnabled) merged else merged.map {
        if (it.id == ThreadSettingsMenuItemId.NgManagement) it.copy(isEnabled = true) else it
    }
    return ensured
        .sortedWith(compareBy<ThreadSettingsMenuItemConfig> { it.order }.thenBy { it.id.defaultOrder })
        .mapIndexed { index, item -> item.copy(order = index) }
}

@Serializable
enum class ThreadMenuEntryPlacement {
    BAR,
    SHEET,
    HIDDEN
}

@Serializable
enum class ThreadMenuEntryId {
    Reply,
    ScrollToTop,
    ScrollToBottom,
    Refresh,
    Gallery,
    Save,
    Filter,
    Settings,
    NgManagement,
    ExternalApp,
    ReadAloud,
    Privacy;

    val defaultPlacement: ThreadMenuEntryPlacement
        get() = when (this) {
            Reply, ScrollToTop, ScrollToBottom, Refresh, Gallery, Save, Filter, Settings -> ThreadMenuEntryPlacement.BAR
            NgManagement, ExternalApp, ReadAloud, Privacy -> ThreadMenuEntryPlacement.SHEET
        }

    val defaultOrder: Int
        get() = when (this) {
            Reply -> 0
            ScrollToTop -> 1
            ScrollToBottom -> 2
            Refresh -> 3
            Gallery -> 4
            Save -> 5
            Filter -> 6
            Settings -> 7
            NgManagement -> 0
            ExternalApp -> 1
            ReadAloud -> 2
            Privacy -> 3
        }
}

@Serializable
data class ThreadMenuEntryConfig(
    val id: ThreadMenuEntryId,
    val placement: ThreadMenuEntryPlacement = id.defaultPlacement,
    val order: Int = id.defaultOrder
)

fun defaultThreadMenuEntries(): List<ThreadMenuEntryConfig> {
    return ThreadMenuEntryId.entries.map { id ->
        ThreadMenuEntryConfig(
            id = id,
            placement = id.defaultPlacement,
            order = id.defaultOrder
        )
    }
}

fun normalizeThreadMenuEntries(
    configs: List<ThreadMenuEntryConfig>,
    maxBarItems: Int = 8
): List<ThreadMenuEntryConfig> {
    val merged = ThreadMenuEntryId.entries.map { id ->
        val existing = configs.firstOrNull { it.id == id }
        val placement = existing?.placement ?: id.defaultPlacement
        val order = existing?.order ?: id.defaultOrder
        ThreadMenuEntryConfig(id = id, placement = placement, order = order)
    }.toMutableList()

    fun reorderPlacement(targetPlacement: ThreadMenuEntryPlacement) {
        val items = merged.filter { it.placement == targetPlacement }
            .sortedWith(compareBy<ThreadMenuEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
        items.forEachIndexed { index, config ->
            val idx = merged.indexOfFirst { it.id == config.id }
            if (idx >= 0) {
                merged[idx] = config.copy(order = index)
            }
        }
    }

    // Ensure sheet has opener: if there is any SHEET item, force Settings into BAR
    val hasSheetItems = merged.any { it.placement == ThreadMenuEntryPlacement.SHEET }
    if (hasSheetItems) {
        merged.indexOfFirst { it.id == ThreadMenuEntryId.Settings }.takeIf { it >= 0 }?.let { idx ->
            merged[idx] = merged[idx].copy(placement = ThreadMenuEntryPlacement.BAR)
        }
    }

    // Ensure at least one BAR item
    val hasBar = merged.any { it.placement == ThreadMenuEntryPlacement.BAR }
    if (!hasBar) {
        merged.indexOfFirst { it.id == ThreadMenuEntryId.Settings }.takeIf { it >= 0 }?.let { idx ->
            merged[idx] = merged[idx].copy(placement = ThreadMenuEntryPlacement.BAR, order = 0)
        }
    }

    // Enforce bar max items: overflow items move to SHEET preserving relative order
    val barItems = merged.filter { it.placement == ThreadMenuEntryPlacement.BAR }
        .sortedWith(compareBy<ThreadMenuEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
    if (barItems.size > maxBarItems) {
        val overflow = barItems.drop(maxBarItems)
        overflow.forEach { item ->
            val idx = merged.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                merged[idx] = merged[idx].copy(placement = ThreadMenuEntryPlacement.SHEET)
            }
        }
    }

    // If sheet empty but Settings only used to open sheet, keep placement as-is (can be hidden) but ensure order
    reorderPlacement(ThreadMenuEntryPlacement.BAR)
    reorderPlacement(ThreadMenuEntryPlacement.SHEET)
    return merged
}
