package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry

internal const val APP_STATE_HISTORY_MAX_ENTRIES = 20_000

/**
 * Drops the least recently visited entries beyond [maxEntries], so opening a
 * new thread keeps working once history reaches its size limit instead of
 * failing to save. Entries with equal visit times go from the end of the list
 * first. [protectedKeys] (the entries a mutation just added) are never dropped.
 */
internal fun trimAppStateHistoryToLimit(
    history: List<ThreadHistoryEntry>,
    maxEntries: Int,
    protectedKeys: Set<String> = emptySet()
): List<ThreadHistoryEntry> {
    if (history.size <= maxEntries) return history
    val removable = history.withIndex()
        .filter { historyEntryIdentity(it.value) !in protectedKeys }
        .sortedWith(
            compareBy<IndexedValue<ThreadHistoryEntry>> { it.value.lastVisitedEpochMillis }
                .thenByDescending { it.index }
        )
        .take(history.size - maxEntries)
        .mapTo(HashSet()) { it.index }
    return history.filterIndexed { index, _ -> index !in removable }
}
