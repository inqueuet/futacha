package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.Logger
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AppStateHistoryOperations(
    private val tag: String,
    private val historyCoordinator: AppStateHistoryCoordinator,
    private val scrollPersistenceCoordinator: AppStateHistoryScrollPersistenceCoordinator,
    private val runStorageMutation: suspend (
        operation: String,
        failureMessage: () -> String,
        onFailure: suspend () -> Unit,
        rethrowOnFailure: Boolean,
        block: suspend () -> Unit
    ) -> Unit
) {
    private val deletionGate = Mutex()
    private val deletedHistoryAtByKey = mutableMapOf<String, Long>()
    private var lastClearAtMillis: Long = 0L

    suspend fun setHistory(history: List<ThreadHistoryEntry>) {
        deletionGate.withLock {
            history.mapTo(mutableSetOf(), ::historyEntryIdentity)
                .filter(String::isNotBlank)
                .forEach(deletedHistoryAtByKey::remove)
            runStorageMutation(
                "setHistory",
                { "Failed to save history with ${history.size} entries" },
                {},
                true
            ) {
                historyCoordinator.setHistory(history)
            }
        }
    }

    suspend fun clearHistory() {
        scrollPersistenceCoordinator.cancelAllPending()
        deletionGate.withLock {
            runMutation(
                missingSnapshotMessage = "Skipping history clear due to missing snapshot",
                buildPlan = { currentHistory ->
                    val deletedAt = Clock.System.now().toEpochMilliseconds()
                    lastClearAtMillis = maxOf(lastClearAtMillis, deletedAt)
                    currentHistory.map(::historyEntryIdentity)
                        .filter(String::isNotBlank)
                        .forEach { key -> deletedHistoryAtByKey[key] = deletedAt }
                    trimDeletedHistoryTombstones()
                    AppStateHistoryMutationPlan(emptyList(), currentHistory.size)
                }
            ) { count -> "Failed to clear $count history entries" }
        }
    }

    suspend fun updateHistory(
        transform: (List<ThreadHistoryEntry>) -> List<ThreadHistoryEntry>
    ) {
        deletionGate.withLock {
            runMutation(
                missingSnapshotMessage = "Skipping atomic history update due to missing snapshot",
                buildPlan = { currentHistory ->
                    val currentKeys = currentHistory.mapTo(mutableSetOf(), ::historyEntryIdentity)
                    transform(currentHistory)
                        .filter { candidate ->
                            val key = historyEntryIdentity(candidate)
                            if (key in currentKeys) return@filter true
                            if (candidate.lastVisitedEpochMillis <= lastClearAtMillis) {
                                return@filter false
                            }
                            val deletedAt = deletedHistoryAtByKey[key] ?: return@filter true
                            if (candidate.lastVisitedEpochMillis > deletedAt) {
                                deletedHistoryAtByKey.remove(key)
                                true
                            } else {
                                false
                            }
                        }
                        .takeIf { it != currentHistory }
                        ?.let { AppStateHistoryMutationPlan(it, Unit) }
                }
            ) { "Failed to update history atomically" }
        }
    }

    suspend fun upsertHistoryEntry(entry: ThreadHistoryEntry) {
        runMutation(
            missingSnapshotMessage = "Skipping history upsert due to missing snapshot",
            buildPlan = { currentHistory ->
                resolveAppStateHistoryUpsertPlan(currentHistory, entry)
            }
        ) { threadId ->
            "Failed to upsert history entry $threadId"
        }
    }

    suspend fun prependOrReplaceHistoryEntry(entry: ThreadHistoryEntry) {
        deletionGate.withLock {
            deletedHistoryAtByKey.remove(historyEntryIdentity(entry))
            runMutation(
                missingSnapshotMessage = "Skipping history prepend due to missing snapshot",
                buildPlan = { currentHistory ->
                    resolveAppStateHistoryPrependPlan(currentHistory, entry) ?: run {
                        Logger.w(tag, "Skipping history prepend due to invalid identity")
                        null
                    }
                }
            ) { threadId ->
                "Failed to prepend history entry $threadId"
            }
        }
    }

    suspend fun prependOrReplaceHistoryEntries(entries: List<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        deletionGate.withLock {
            entries.mapTo(mutableSetOf(), ::historyEntryIdentity)
                .forEach(deletedHistoryAtByKey::remove)
            runMutation(
                missingSnapshotMessage = "Skipping history prepend batch due to missing snapshot",
                buildPlan = { currentHistory ->
                    resolveAppStateHistoryBatchPrependPlan(currentHistory, entries)
                }
            ) { dedupedSize ->
                "Failed to prepend $dedupedSize history entries"
            }
        }
    }

    suspend fun mergeHistoryEntries(entries: Collection<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        runMutation(
            missingSnapshotMessage = "Skipping history merge due to missing snapshot",
            buildPlan = { currentHistory ->
                resolveAppStateHistoryMergePlan(currentHistory, entries)?.let { plan ->
                    AppStateHistoryMutationPlan(
                        updatedHistory = plan.updatedHistory,
                        metadata = plan.droppedUpdateCount
                    )
                }
            },
            onCommitted = { appendedSize ->
                if (appendedSize > 0) {
                    Logger.i(tag, "Dropped $appendedSize stale history update(s) during merge")
                }
            }
        ) { _ ->
            "Failed to merge ${entries.size} history entries"
        }
    }

    suspend fun removeHistoryEntry(entry: ThreadHistoryEntry) {
        scrollPersistenceCoordinator.cancelPendingForHistoryEntry(
            threadId = entry.threadId,
            boardId = entry.boardId,
            boardUrl = entry.boardUrl
        )
        deletionGate.withLock {
            historyEntryIdentity(entry).takeIf(String::isNotBlank)?.let { key ->
                deletedHistoryAtByKey[key] = Clock.System.now().toEpochMilliseconds()
                trimDeletedHistoryTombstones()
            }
            runMutation(
                missingSnapshotMessage = "Skipping history removal due to missing snapshot",
                buildPlan = { currentHistory ->
                    resolveAppStateHistoryRemovalPlan(currentHistory, entry) ?: run {
                        Logger.w(tag, "Skipping history removal due to invalid identity")
                        null
                    }
                }
            ) { threadId ->
                "Failed to remove history entry $threadId"
            }
        }
    }

    suspend fun cancelAllPendingScrollUpdates() {
        scrollPersistenceCoordinator.cancelAllPending()
    }

    suspend fun markHistorySelfPost(
        threadId: String,
        boardId: String?,
        postedAtMillis: Long
    ) {
        runMutation(
            missingSnapshotMessage = "Skipping history self-post update due to missing snapshot",
            buildPlan = { currentHistory ->
                resolveAppStateHistorySelfPostPlan(
                    currentHistory = currentHistory,
                    threadId = threadId,
                    boardId = boardId,
                    postedAtMillis = postedAtMillis
                )
            }
        ) { targetThreadId ->
            "Failed to mark self post for history thread $targetThreadId"
        }
    }

    suspend fun scheduleHistoryScrollPositionUpdate(
        request: AppStateHistoryScrollUpdateRequest
    ) {
        scrollPersistenceCoordinator.schedule(request)
    }

    suspend fun updateHistoryScrollPositionImmediate(
        request: AppStateHistoryScrollUpdateRequest
    ) {
        scrollPersistenceCoordinator.cancelPending(request)
        runMutation(
            missingSnapshotMessage = "Skipping history scroll persistence due to missing snapshot",
            buildPlan = { currentHistory ->
                resolveAppStateHistoryScrollUpdatePlan(
                    currentHistory = currentHistory,
                    threadId = request.threadId,
                    index = request.index,
                    offset = request.offset,
                    postId = request.postId,
                    boardId = request.boardId,
                    title = request.title,
                    titleImageUrl = request.titleImageUrl,
                    boardName = request.boardName,
                    boardUrl = request.boardUrl,
                    replyCount = request.replyCount,
                    nowMillis = Clock.System.now().toEpochMilliseconds(),
                    forcePersist = request.forcePersist
                )
            }
        ) { targetThreadId ->
            "Failed to persist updated history for thread $targetThreadId"
        }
    }

    private suspend fun <T> runMutation(
        missingSnapshotMessage: String,
        buildPlan: (List<ThreadHistoryEntry>) -> AppStateHistoryMutationPlan<T>?,
        onCommitted: (T) -> Unit = {},
        buildFailureMessage: (T) -> String
    ) {
        historyCoordinator.runMutation(
            missingSnapshotMessage = missingSnapshotMessage,
            buildPlan = buildPlan,
            onCommitted = onCommitted,
            buildFailureMessage = buildFailureMessage
        )
    }

    private fun trimDeletedHistoryTombstones() {
        if (deletedHistoryAtByKey.size <= MAX_DELETED_HISTORY_TOMBSTONES) return
        deletedHistoryAtByKey.entries
            .sortedBy { it.value }
            .take(deletedHistoryAtByKey.size - MAX_DELETED_HISTORY_TOMBSTONES)
            .forEach { deletedHistoryAtByKey.remove(it.key) }
    }

    private companion object {
        const val MAX_DELETED_HISTORY_TOMBSTONES = 1_024
    }
}
