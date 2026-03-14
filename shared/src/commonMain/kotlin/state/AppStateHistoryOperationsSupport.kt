package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.Logger
import kotlin.time.Clock

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
    suspend fun setHistory(history: List<ThreadHistoryEntry>) {
        runStorageMutation(
            "setHistory",
            { "Failed to save history with ${history.size} entries" },
            {},
            true
        ) {
            historyCoordinator.setHistory(history)
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

    suspend fun prependOrReplaceHistoryEntries(entries: List<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        runMutation(
            missingSnapshotMessage = "Skipping history prepend batch due to missing snapshot",
            buildPlan = { currentHistory ->
                resolveAppStateHistoryBatchPrependPlan(currentHistory, entries)
            }
        ) { dedupedSize ->
            "Failed to prepend $dedupedSize history entries"
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

    suspend fun scheduleHistoryScrollPositionUpdate(
        request: AppStateHistoryScrollUpdateRequest
    ) {
        scrollPersistenceCoordinator.schedule(request)
    }

    suspend fun updateHistoryScrollPositionImmediate(
        request: AppStateHistoryScrollUpdateRequest
    ) {
        runMutation(
            missingSnapshotMessage = "Skipping history scroll persistence due to missing snapshot",
            buildPlan = { currentHistory ->
                resolveAppStateHistoryScrollUpdatePlan(
                    currentHistory = currentHistory,
                    threadId = request.threadId,
                    index = request.index,
                    offset = request.offset,
                    boardId = request.boardId,
                    title = request.title,
                    titleImageUrl = request.titleImageUrl,
                    boardName = request.boardName,
                    boardUrl = request.boardUrl,
                    replyCount = request.replyCount,
                    nowMillis = Clock.System.now().toEpochMilliseconds()
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
}
