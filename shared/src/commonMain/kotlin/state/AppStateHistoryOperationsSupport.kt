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
    private val deletedHistoryAtByKey = mutableMapOf<String, HistoryTombstone>()
    private var lastClearAtMillis: Long = 0L

    // Orders deletions for imports. Wall-clock times can go backwards or tie, so
    // "deleted after the import started" is decided by this counter.
    private var deletionSequence: Long = 0L
    private var lastClearSequence: Long = 0L
    private val activeImports = mutableSetOf<HistoryImportTicket>()

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
                    val sequence = ++deletionSequence
                    lastClearSequence = sequence
                    currentHistory.map(::historyEntryIdentity)
                        .filter(String::isNotBlank)
                        .forEach { key -> deletedHistoryAtByKey[key] = HistoryTombstone(deletedAt, sequence) }
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
                            val deletedAt = deletedHistoryAtByKey[key]?.atMillis ?: return@filter true
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
                deletedHistoryAtByKey[key] = HistoryTombstone(
                    atMillis = Clock.System.now().toEpochMilliseconds(),
                    sequence = ++deletionSequence
                )
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
        persistHistoryScrollPosition(request)
    }

    /**
     * Writes a scroll position without touching pending debounced writes. The
     * debounced job itself calls this; calling [updateHistoryScrollPositionImmediate]
     * from there cancelled the running job before its file write.
     */
    suspend fun persistHistoryScrollPosition(
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
        // A running import needs every deletion made after it started.
        val oldestImport = activeImports.minOfOrNull { it.sequence }
        deletedHistoryAtByKey.entries
            .filter { oldestImport == null || it.value.sequence <= oldestImport }
            .sortedBy { it.value.atMillis }
            .take(deletedHistoryAtByKey.size - MAX_DELETED_HISTORY_TOMBSTONES)
            .forEach { deletedHistoryAtByKey.remove(it.key) }
    }

    /**
     * Marks the start of an archive import. Call before reading the archive:
     * deletions and clears made after this point win over the imported entries.
     */
    suspend fun beginHistoryImport(): HistoryImportTicket = deletionGate.withLock {
        HistoryImportTicket(deletionSequence).also { activeImports += it }
    }

    /** Releases [ticket]; safe to call after [mergeImportedHistory] or on failure. */
    suspend fun endHistoryImport(ticket: HistoryImportTicket) {
        deletionGate.withLock { activeImports -= ticket }
    }

    /**
     * Merges imported entries into the latest history in one mutation.
     *
     * An imported entry restores a deletion made before [ticket] (the user
     * chose to import it), but not one made while the archive was being read.
     * A clear during the import drops the whole import. Other changes made in
     * the meantime (reading positions, new entries) are kept.
     */
    suspend fun mergeImportedHistory(
        imported: Collection<ThreadHistoryEntry>,
        ticket: HistoryImportTicket
    ): HistoryArchiveImportMergeResult = deletionGate.withLock {
        try {
            val clearedDuringImport = lastClearSequence > ticket.sequence
            val accepted = if (clearedDuringImport) {
                emptyList()
            } else {
                imported.filter { entry ->
                    val tombstone = deletedHistoryAtByKey[historyEntryIdentity(entry)]
                    tombstone == null || tombstone.sequence <= ticket.sequence
                }
            }
            accepted.mapTo(HashSet(), ::historyEntryIdentity).forEach(deletedHistoryAtByKey::remove)
            var merge = resolveHistoryArchiveImportMergeEntries(emptyList(), emptyList())
            runMutation(
                missingSnapshotMessage = "Skipping history import due to missing snapshot",
                buildPlan = { currentHistory ->
                    merge = resolveHistoryArchiveImportMergeEntries(currentHistory, accepted)
                    merge.updatedHistory.takeIf { it != currentHistory }
                        ?.let { AppStateHistoryMutationPlan(it, Unit) }
                }
            ) { "Failed to merge ${accepted.size} imported history entries" }
            merge.copy(skippedCount = merge.skippedCount + (imported.size - accepted.size))
        } finally {
            activeImports -= ticket
        }
    }

    private companion object {
        const val MAX_DELETED_HISTORY_TOMBSTONES = 1_024
    }
}

internal data class HistoryTombstone(val atMillis: Long, val sequence: Long)

class HistoryImportTicket internal constructor(internal val sequence: Long)
