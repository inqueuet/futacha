package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf
import com.valoser.futacha.shared.model.ThreadHistoryEntry

internal enum class HistorySortOption(val label: String) {
    LastVisited("最後に見た日時"),
    Title("スレタイ"),
    LastConfirmedAlive("最終生存確認日時"),
    LastSelfPost("自分が書き込んだ日時"),
    ReplyCount("レス数")
}

internal enum class HistorySortDirection(val label: String) {
    Descending("新しい・多い順"),
    Ascending("古い・少ない順")
}

internal enum class HistoryLifeFilter(val label: String) {
    All("すべて"),
    Alive("生存確認あり"),
    Expired("消滅確認済み"),
    Unknown("状態不明")
}

internal enum class HistoryLifeStatus {
    Alive,
    Expired,
    Unknown
}

internal data class HistoryBoardFilterOption(
    val key: String,
    val label: String
)

internal data class HistoryViewSettings(
    val sortOption: HistorySortOption = HistorySortOption.LastVisited,
    val sortDirection: HistorySortDirection = HistorySortDirection.Descending,
    val selfPostsOnly: Boolean = false,
    val lifeFilter: HistoryLifeFilter = HistoryLifeFilter.All,
    val boardKey: String? = null,
    val titleQuery: String = ""
) {
    val isDefault: Boolean
        get() = this == Default

    val activeSettingCount: Int
        get() = listOf(
            sortOption != Default.sortOption || sortDirection != Default.sortDirection,
            selfPostsOnly,
            lifeFilter != HistoryLifeFilter.All,
            boardKey != null,
            titleQuery.isNotBlank()
        ).count { it }

    companion object {
        val Default = HistoryViewSettings()

        val Saver: Saver<HistoryViewSettings, Any> = listSaver(
            save = { settings ->
                listOf(
                    settings.sortOption.name,
                    settings.sortDirection.name,
                    settings.selfPostsOnly,
                    settings.lifeFilter.name,
                    settings.boardKey,
                    settings.titleQuery
                )
            },
            restore = { restored ->
                HistoryViewSettings(
                    sortOption = HistorySortOption.entries.firstOrNull {
                        it.name == restored.getOrNull(0) as? String
                    } ?: Default.sortOption,
                    sortDirection = HistorySortDirection.entries.firstOrNull {
                        it.name == restored.getOrNull(1) as? String
                    } ?: Default.sortDirection,
                    selfPostsOnly = restored.getOrNull(2) as? Boolean ?: Default.selfPostsOnly,
                    lifeFilter = HistoryLifeFilter.entries.firstOrNull {
                        it.name == restored.getOrNull(3) as? String
                    } ?: Default.lifeFilter,
                    boardKey = restored.getOrNull(4) as? String,
                    titleQuery = restored.getOrNull(5) as? String ?: Default.titleQuery
                )
            }
        )
    }
}

internal data class HistoryViewSettingsBinding(
    val settings: HistoryViewSettings,
    val onSettingsChanged: (HistoryViewSettings) -> Unit
)

internal val LocalHistoryViewSettingsBinding =
    staticCompositionLocalOf<HistoryViewSettingsBinding?> { null }

internal fun resolveHistoryLifeStatus(entry: ThreadHistoryEntry): HistoryLifeStatus {
    return when {
        entry.isAutoRefreshDisabled -> HistoryLifeStatus.Expired
        entry.lastConfirmedAliveEpochMillis != null -> HistoryLifeStatus.Alive
        else -> HistoryLifeStatus.Unknown
    }
}

internal fun historyBoardFilterKey(entry: ThreadHistoryEntry): String {
    return entry.boardId.trim().takeIf { it.isNotBlank() }
        ?: entry.boardUrl.trim().substringBefore('?').trimEnd('/').lowercase()
}

internal fun buildHistoryBoardFilterOptions(
    history: List<ThreadHistoryEntry>
): List<HistoryBoardFilterOption> {
    return history
        .map { entry ->
            HistoryBoardFilterOption(
                key = historyBoardFilterKey(entry),
                label = entry.boardName.ifBlank { entry.boardId.ifBlank { "名称不明の板" } }
            )
        }
        .filter { it.key.isNotBlank() }
        .distinctBy { it.key }
        .sortedBy { it.label.lowercase() }
}

internal fun applyHistoryViewSettings(
    history: List<ThreadHistoryEntry>,
    settings: HistoryViewSettings
): List<ThreadHistoryEntry> {
    val normalizedQuery = settings.titleQuery.trim().lowercase()
    val filtered = history.filter { entry ->
        (!settings.selfPostsOnly || entry.hasSelfPost) &&
            matchesHistoryLifeFilter(entry, settings.lifeFilter) &&
            (settings.boardKey == null || historyBoardFilterKey(entry) == settings.boardKey) &&
            (normalizedQuery.isEmpty() || entry.title.lowercase().contains(normalizedQuery))
    }
    return filtered.sortedWith { first, second ->
        val firstMissing = isHistorySortValueMissing(first, settings.sortOption)
        val secondMissing = isHistorySortValueMissing(second, settings.sortOption)
        if (firstMissing != secondMissing) {
            return@sortedWith if (firstMissing) 1 else -1
        }
        val primary = compareHistoryEntries(first, second, settings.sortOption)
        val directed = if (settings.sortDirection == HistorySortDirection.Descending) {
            -primary
        } else {
            primary
        }
        if (directed != 0) directed else second.lastVisitedEpochMillis.compareTo(first.lastVisitedEpochMillis)
    }
}

private fun isHistorySortValueMissing(
    entry: ThreadHistoryEntry,
    option: HistorySortOption
): Boolean {
    return when (option) {
        HistorySortOption.LastConfirmedAlive -> entry.lastConfirmedAliveEpochMillis == null
        HistorySortOption.LastSelfPost -> entry.lastSelfPostEpochMillis == null
        else -> false
    }
}

private fun matchesHistoryLifeFilter(
    entry: ThreadHistoryEntry,
    filter: HistoryLifeFilter
): Boolean {
    return when (filter) {
        HistoryLifeFilter.All -> true
        HistoryLifeFilter.Alive -> resolveHistoryLifeStatus(entry) == HistoryLifeStatus.Alive
        HistoryLifeFilter.Expired -> resolveHistoryLifeStatus(entry) == HistoryLifeStatus.Expired
        HistoryLifeFilter.Unknown -> resolveHistoryLifeStatus(entry) == HistoryLifeStatus.Unknown
    }
}

private fun compareHistoryEntries(
    first: ThreadHistoryEntry,
    second: ThreadHistoryEntry,
    option: HistorySortOption
): Int {
    return when (option) {
        HistorySortOption.LastVisited -> first.lastVisitedEpochMillis.compareTo(second.lastVisitedEpochMillis)
        HistorySortOption.Title -> first.title.lowercase().compareTo(second.title.lowercase())
        HistorySortOption.LastConfirmedAlive -> compareNullableHistoryTimestamps(
            first.lastConfirmedAliveEpochMillis,
            second.lastConfirmedAliveEpochMillis
        )
        HistorySortOption.LastSelfPost -> compareNullableHistoryTimestamps(
            first.lastSelfPostEpochMillis,
            second.lastSelfPostEpochMillis
        )
        HistorySortOption.ReplyCount -> first.replyCount.compareTo(second.replyCount)
    }
}

private fun compareNullableHistoryTimestamps(first: Long?, second: Long?): Int {
    return when {
        first == null && second == null -> 0
        first == null -> 0
        second == null -> 0
        else -> first.compareTo(second)
    }
}

internal fun buildHistoryViewSummaryLabels(
    settings: HistoryViewSettings,
    boardOptions: List<HistoryBoardFilterOption>
): List<String> {
    if (settings.isDefault) return emptyList()
    return buildList {
        if (settings.sortOption != HistorySortOption.LastVisited ||
            settings.sortDirection != HistorySortDirection.Descending
        ) {
            val directionLabel = when {
                settings.sortOption == HistorySortOption.Title &&
                    settings.sortDirection == HistorySortDirection.Ascending -> "昇順"
                settings.sortOption == HistorySortOption.Title -> "降順"
                settings.sortDirection == HistorySortDirection.Ascending -> "古い・少ない順"
                else -> "新しい・多い順"
            }
            add("${settings.sortOption.label}・$directionLabel")
        }
        if (settings.selfPostsOnly) add("自分の書き込み")
        if (settings.lifeFilter != HistoryLifeFilter.All) add(settings.lifeFilter.label)
        settings.boardKey?.let { key ->
            boardOptions.firstOrNull { it.key == key }?.label?.let(::add)
        }
        settings.titleQuery.trim().takeIf { it.isNotEmpty() }?.let { add("スレタイ: $it") }
    }
}
