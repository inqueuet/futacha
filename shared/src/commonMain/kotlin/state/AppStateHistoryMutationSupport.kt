package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry

internal data class AppStateHistoryMutationPlan<out T>(
    val updatedHistory: List<ThreadHistoryEntry>,
    val metadata: T
)

internal data class AppStateHistoryMergePlan(
    val updatedHistory: List<ThreadHistoryEntry>,
    val droppedUpdateCount: Int
)

internal fun <T> createAppStateHistoryMutation(
    currentRevision: Long,
    previousHistory: List<ThreadHistoryEntry>?,
    plan: AppStateHistoryMutationPlan<T>
): HistoryMutation<T> {
    return HistoryMutation(
        revision = currentRevision + 1L,
        updatedHistory = plan.updatedHistory,
        previousRevision = currentRevision,
        previousHistory = previousHistory,
        metadata = plan.metadata
    )
}

internal fun resolveAppStateHistoryUpsertPlan(
    currentHistory: List<ThreadHistoryEntry>,
    entry: ThreadHistoryEntry
): AppStateHistoryMutationPlan<String> {
    val existingIndex = currentHistory.indexOfFirst {
        matchesHistoryEntryIdentity(it, entry.threadId, entry.boardId, entry.boardUrl)
    }
    val updatedHistory = if (existingIndex >= 0) {
        currentHistory.toMutableList().also { it[existingIndex] = entry }
    } else {
        buildList {
            addAll(currentHistory)
            add(entry)
        }
    }
    return AppStateHistoryMutationPlan(
        updatedHistory = updatedHistory,
        metadata = entry.threadId
    )
}

internal fun resolveAppStateHistoryPrependPlan(
    currentHistory: List<ThreadHistoryEntry>,
    entry: ThreadHistoryEntry
): AppStateHistoryMutationPlan<String>? {
    val targetKey = historyEntryIdentity(entry)
    if (targetKey.isBlank()) {
        return null
    }
    val updatedHistory = buildList {
        add(entry)
        addAll(
            currentHistory.filterNot {
                historyEntryIdentity(it) == targetKey
            }
        )
    }
    return AppStateHistoryMutationPlan(
        updatedHistory = updatedHistory,
        metadata = entry.threadId
    )
}

internal fun resolveAppStateHistoryBatchPrependPlan(
    currentHistory: List<ThreadHistoryEntry>,
    entries: List<ThreadHistoryEntry>
): AppStateHistoryMutationPlan<Int>? {
    val dedupedByKey = linkedMapOf<String, ThreadHistoryEntry>()
    entries.forEach { candidate ->
        val key = historyEntryIdentity(candidate)
        if (key.isNotBlank() && key !in dedupedByKey) {
            dedupedByKey[key] = candidate
        }
    }
    val dedupedEntries = dedupedByKey.values.toList()
    if (dedupedEntries.isEmpty()) {
        return null
    }
    val dedupedKeys = dedupedByKey.keys
    val updatedHistory = buildList {
        addAll(dedupedEntries)
        addAll(
            currentHistory.filterNot { existing ->
                historyEntryIdentity(existing) in dedupedKeys
            }
        )
    }
    return AppStateHistoryMutationPlan(
        updatedHistory = updatedHistory,
        metadata = dedupedEntries.size
    )
}

internal fun resolveAppStateHistoryMergePlan(
    currentHistory: List<ThreadHistoryEntry>,
    entries: Collection<ThreadHistoryEntry>
): AppStateHistoryMergePlan? {
    val updatesByKey = linkedMapOf<String, ThreadHistoryEntry>()
    entries.forEach { candidate ->
        val key = historyEntryIdentity(candidate)
        if (key.isNotBlank()) {
            updatesByKey[key] = candidate
        }
    }
    if (updatesByKey.isEmpty()) {
        return null
    }

    var changed = false
    val remainingUpdates = updatesByKey.toMutableMap()
    val merged = currentHistory.map { existing ->
        val key = historyEntryIdentity(existing)
        val replacement = remainingUpdates.remove(key)
        if (replacement != null) {
            val mergedEntry = mergeAppStateHistoryEntry(existing, replacement)
            if (mergedEntry != existing) {
                changed = true
            }
            mergedEntry
        } else {
            existing
        }
    }
    if (!changed) {
        return null
    }
    return AppStateHistoryMergePlan(
        updatedHistory = merged,
        droppedUpdateCount = remainingUpdates.size
    )
}

internal fun resolveAppStateHistoryRemovalPlan(
    currentHistory: List<ThreadHistoryEntry>,
    entry: ThreadHistoryEntry
): AppStateHistoryMutationPlan<String>? {
    val targetKey = historyEntryIdentity(entry)
    if (targetKey.isBlank()) {
        return null
    }
    val updatedHistory = currentHistory.filterNot {
        historyEntryIdentity(it) == targetKey
    }
    if (updatedHistory.size == currentHistory.size) {
        return null
    }
    return AppStateHistoryMutationPlan(
        updatedHistory = updatedHistory,
        metadata = entry.threadId
    )
}

internal fun resolveAppStateHistoryScrollUpdatePlan(
    currentHistory: List<ThreadHistoryEntry>,
    threadId: String,
    index: Int,
    offset: Int,
    boardId: String,
    title: String,
    titleImageUrl: String,
    boardName: String,
    boardUrl: String,
    replyCount: Int,
    nowMillis: Long,
    forcePersist: Boolean = false
): AppStateHistoryMutationPlan<String>? {
    val existingEntry = currentHistory.firstOrNull {
        matchesHistoryEntryIdentity(it, threadId, boardId, boardUrl)
    }
    if (shouldSkipHistoryScrollUpdate(existingEntry, index, offset, nowMillis, forcePersist)) {
        return null
    }
    val updatedHistory = if (existingEntry != null) {
        currentHistory.map { entry ->
            if (matchesHistoryEntryIdentity(entry, threadId, boardId, boardUrl)) {
                applyHistoryScrollUpdate(entry, index, offset, nowMillis)
            } else {
                entry
            }
        }
    } else {
        buildList {
            add(
                buildNewHistoryScrollEntry(
                    threadId = threadId,
                    index = index,
                    offset = offset,
                    boardId = boardId,
                    title = title,
                    titleImageUrl = titleImageUrl,
                    boardName = boardName,
                    boardUrl = boardUrl,
                    replyCount = replyCount,
                    nowMillis = nowMillis
                )
            )
            addAll(currentHistory)
        }
    }
    return AppStateHistoryMutationPlan(
        updatedHistory = updatedHistory,
        metadata = threadId
    )
}
