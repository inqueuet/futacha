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
): AppStateHistoryMutationPlan<String>? {
    val existingIndex = currentHistory.indexOfFirst {
        matchesHistoryEntryIdentity(it, entry.threadId, entry.boardId, entry.boardUrl)
    }
    if (existingIndex < 0) {
        return null
    }
    val updatedHistory = currentHistory.toMutableList().also {
        it[existingIndex] = mergeAppStateHistoryMetadataEntry(it[existingIndex], entry)
    }
    if (updatedHistory == currentHistory) return null
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
        val existing = currentHistory.firstOrNull { historyEntryIdentity(it) == targetKey }
        add(existing?.let { mergeAppStateHistoryMetadataEntry(it, entry) } ?: entry)
        addAll(currentHistory.filterNot { historyEntryIdentity(it) == targetKey })
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
    val currentEntriesWithKeys = currentHistory.map { existing ->
        historyEntryIdentity(existing) to existing
    }
    val currentByKey = buildMap {
        currentEntriesWithKeys.forEach { (key, existing) ->
            if (key.isNotBlank() && key !in this) put(key, existing)
        }
    }
    val updatedHistory = buildList {
        dedupedEntries.forEach { incoming ->
            val key = historyEntryIdentity(incoming)
            val existing = currentByKey[key]
            add(existing?.let { mergeAppStateHistoryMetadataEntry(it, incoming) } ?: incoming)
        }
        addAll(
            currentEntriesWithKeys
                .filterNot { (key, _) -> key in dedupedKeys }
                .map { (_, existing) -> existing }
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
            val mergedEntry = mergeAppStateHistoryMetadataEntry(existing, replacement)
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

internal fun resolveAppStateHistorySelfPostPlan(
    currentHistory: List<ThreadHistoryEntry>,
    threadId: String,
    boardId: String?,
    postedAtMillis: Long
): AppStateHistoryMutationPlan<String>? {
    val normalizedThreadId = threadId.trim()
    val normalizedBoardId = boardId?.trim().orEmpty()
    if (normalizedThreadId.isBlank()) return null
    var changed = false
    val updatedHistory = currentHistory.map { entry ->
        val matches = entry.threadId.trim() == normalizedThreadId &&
            (normalizedBoardId.isBlank() || entry.boardId.trim() == normalizedBoardId)
        if (matches && (!entry.hasSelfPost || entry.lastSelfPostEpochMillis != postedAtMillis)) {
            changed = true
            entry.copy(
                hasSelfPost = true,
                lastSelfPostEpochMillis = postedAtMillis
            )
        } else {
            entry
        }
    }
    if (!changed) return null
    return AppStateHistoryMutationPlan(
        updatedHistory = updatedHistory,
        metadata = normalizedThreadId
    )
}

internal fun resolveAppStateHistoryScrollUpdatePlan(
    currentHistory: List<ThreadHistoryEntry>,
    threadId: String,
    index: Int,
    offset: Int,
    postId: String?,
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
    } ?: return null
    if (shouldSkipHistoryScrollUpdate(existingEntry, index, offset, nowMillis, forcePersist, postId)) {
        return null
    }
    val updatedHistory = currentHistory.map { entry ->
        if (matchesHistoryEntryIdentity(entry, threadId, boardId, boardUrl)) {
            applyHistoryScrollUpdate(entry, index, offset, postId, nowMillis)
        } else {
            entry
        }
    }
    return AppStateHistoryMutationPlan(
        updatedHistory = updatedHistory,
        metadata = threadId
    )
}
