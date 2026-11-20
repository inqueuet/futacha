package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.Logger
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalAtomicApi::class)
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
    ) -> Unit,
    /** Where deletions are kept across restarts (null: memory only, as before 2026-11-09). */
    private val tombstonePersistence: AppStateHistoryTombstonePersistence? = null
) {
    private val deletionGate = Mutex()
    private val deletedHistoryAtByKey = mutableMapOf<String, HistoryTombstone>()
    private var lastClearAtMillis: Long = 0L

    // Orders deletions for imports. Wall-clock times can go backwards or tie, so
    // "deleted after the import started" is decided by this counter.
    private var deletionSequence: Long = 0L
    private var lastClearSequence: Long = 0L
    private val activeImports = mutableSetOf<HistoryImportTicket>()
    private var tombstonesLoaded = false
    /** [deletionSequence] for callers that must take a ticket without suspending (a screen's open, audit F8). */
    private val publishedDeletionSequence = AtomicLong(0L)

    private val openedRows = MutableSharedFlow<List<ThreadHistoryEntry>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Rows the person opened or chose to bring in (an open, an import, a restore), which forget their deletion here.
     * The shared history copy (compat/shared/SharedHistory.kt) forgets it in としあき(仮)'s store too (plan 1-5, audit 3's
     * 9), so the times do not have to decide it there.
     */
    val historyOpens: SharedFlow<List<ThreadHistoryEntry>> = openedRows.asSharedFlow()

    private fun tellOpened(entries: Collection<ThreadHistoryEntry>) {
        if (entries.isNotEmpty()) openedRows.tryEmit(entries.toList())
    }

    /** Forgets the deletions of [entries] (as for an open, without writing the rows): としあき(仮) opened them again. */
    suspend fun forgetHistoryTombstones(entries: Collection<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        deletionGate.withLock {
            ensureTombstonesLoadedLocked()
            val removed = entries.mapTo(HashSet(), ::historyEntryIdentity).count { deletedHistoryAtByKey.remove(it) != null }
            if (removed > 0) persistTombstonesLocked()
        }
    }

    private fun nextDeletionSequence(): Long = (++deletionSequence).also { publishedDeletionSequence.store(it) }

    /** The deletion counter now: a later [prependOrReplaceHistoryEntry] with it is refused if the row was deleted since. */
    fun deletionTicket(): Long = publishedDeletionSequence.load()

    /** Loads the kept deletions once, under [deletionGate]. */
    private suspend fun ensureTombstonesLoadedLocked() {
        if (tombstonesLoaded) return
        tombstonesLoaded = true
        val persistence = tombstonePersistence ?: return
        try {
            val (loaded, clearedAt) = loadHistoryTombstones(persistence.read(), Clock.System.now().toEpochMilliseconds())
            loaded.forEach { (key, value) -> if (key !in deletedHistoryAtByKey) deletedHistoryAtByKey[key] = value }
            lastClearAtMillis = maxOf(lastClearAtMillis, clearedAt)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) { tombstonesLoaded = false; throw error }
            Logger.w(tag, "Failed to load history tombstones: ${error.message}")
        }
    }

    /** Writes the deletions to keep; before the rows are removed, so a crash in between cannot bring them back. */
    private suspend fun persistTombstonesLocked() {
        val persistence = tombstonePersistence ?: return
        try {
            persistence.write(
                selectPersistedHistoryTombstones(deletedHistoryAtByKey, lastClearAtMillis, Clock.System.now().toEpochMilliseconds())
            )
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Logger.w(tag, "Failed to save history tombstones: ${error.message}")
        }
    }

    // When each thread's scroll position was last written. The throttle cannot use
    // lastVisitedEpochMillis because scrolling no longer updates it.
    private val scrollPersistGate = Mutex()
    private val lastScrollPersistAtByKey = LinkedHashMap<String, Long>()

    /** Tests only: replaces the whole history (no product path calls it; see the plan's 1-5). */
    suspend fun setHistory(history: List<ThreadHistoryEntry>) {
        deletionGate.withLock {
            ensureTombstonesLoadedLocked()
            val keys = history.mapTo(mutableSetOf(), ::historyEntryIdentity).filter(String::isNotBlank)
            if (keys.any { deletedHistoryAtByKey.remove(it) != null }) persistTombstonesLocked()
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
            ensureTombstonesLoadedLocked()
            // The clear time is kept first (audit F3): a copy read before the clear must not come back after a restart.
            val deletedAt = Clock.System.now().toEpochMilliseconds()
            lastClearAtMillis = maxOf(lastClearAtMillis, deletedAt)
            persistTombstonesLocked()
            runMutation(
                missingSnapshotMessage = "Skipping history clear due to missing snapshot",
                buildPlan = { currentHistory ->
                    val sequence = nextDeletionSequence()
                    lastClearSequence = sequence
                    currentHistory.forEach { entry ->
                        historyEntryIdentity(entry).takeIf(String::isNotBlank)?.let { key ->
                            deletedHistoryAtByKey[key] = HistoryTombstone(deletedAt, sequence, entry.lastVisitedEpochMillis)
                        }
                    }
                    trimDeletedHistoryTombstones()
                    AppStateHistoryMutationPlan(emptyList(), currentHistory.size)
                }
            ) { count -> "Failed to clear $count history entries" }
        }
    }

    /** Checks the archive coverage inside the same history mutation that removes the rows. */
    suspend fun clearHistoryIf(covered: (List<ThreadHistoryEntry>) -> Boolean): List<ThreadHistoryEntry>? {
        return deletionGate.withLock {
            ensureTombstonesLoadedLocked()
            var removed: List<ThreadHistoryEntry>? = null
            runMutation(
                missingSnapshotMessage = "Skipping archived history clear due to missing snapshot",
                buildPlan = { current ->
                    if (covered(current)) AppStateHistoryMutationPlan(emptyList(), current) else null
                },
                onCommitted = { current ->
                    val deletedAt = Clock.System.now().toEpochMilliseconds()
                    current.forEach { entry ->
                        historyEntryIdentity(entry).takeIf(String::isNotBlank)?.let { key ->
                            deletedHistoryAtByKey[key] =
                                HistoryTombstone(deletedAt, nextDeletionSequence(), entry.lastVisitedEpochMillis)
                        }
                    }
                    trimDeletedHistoryTombstones()
                    removed = current
                }
            ) { current -> "Failed to clear ${current.size} archived history entries" }
            if (removed != null) persistTombstonesLocked()
            removed
        }
    }

    suspend fun updateHistory(
        transform: (List<ThreadHistoryEntry>) -> List<ThreadHistoryEntry>
    ) {
        deletionGate.withLock {
            ensureTombstonesLoadedLocked()
            var tombstoneLifted = false
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
                                tombstoneLifted = true
                                true
                            } else {
                                false
                            }
                        }
                        .takeIf { it != currentHistory }
                        ?.let { AppStateHistoryMutationPlan(it, Unit) }
                }
            ) { "Failed to update history atomically" }
            if (tombstoneLifted) persistTombstonesLocked()
        }
    }

    /**
     * A restore the person chose (a settings backup, plan B3): like [updateHistory], but a row [transform] adds comes in
     * whatever its time, and its deletion is forgotten. Without this, a row deleted (or cleared) earlier and restored now
     * stayed out of the AppState modes while としあき(仮) showed it.
     */
    suspend fun restoreHistory(
        transform: (List<ThreadHistoryEntry>) -> List<ThreadHistoryEntry>
    ) {
        deletionGate.withLock {
            ensureTombstonesLoadedLocked()
            var tombstoneLifted = false
            val added = mutableListOf<ThreadHistoryEntry>()
            runMutation(
                missingSnapshotMessage = "Skipping history restore due to missing snapshot",
                buildPlan = { currentHistory ->
                    val currentKeys = currentHistory.mapTo(mutableSetOf(), ::historyEntryIdentity)
                    added.clear()
                    transform(currentHistory)
                        .onEach { candidate ->
                            val key = historyEntryIdentity(candidate)
                            if (key !in currentKeys) {
                                added += candidate
                                if (deletedHistoryAtByKey.remove(key) != null) tombstoneLifted = true
                            }
                        }
                        .takeIf { it != currentHistory }
                        ?.let { AppStateHistoryMutationPlan(it, Unit) }
                }
            ) { "Failed to restore history" }
            if (tombstoneLifted) persistTombstonesLocked()
            tellOpened(added)
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

    /**
     * A screen's open: the row goes in whatever its time (a row the person deleted comes back when they open it
     * again). With [openedAtTicket] ([deletionTicket] taken when the person opened it), a deletion made after that
     * point wins, so a late write cannot bring back a row deleted just after opening (audit F8).
     */
    suspend fun prependOrReplaceHistoryEntry(entry: ThreadHistoryEntry, openedAtTicket: Long? = null) {
        deletionGate.withLock {
            ensureTombstonesLoadedLocked()
            val key = historyEntryIdentity(entry)
            if (openedAtTicket != null) {
                val deletedSince = (deletedHistoryAtByKey[key]?.sequence ?: 0L) > openedAtTicket ||
                    lastClearSequence > openedAtTicket
                if (deletedSince) {
                    Logger.i(tag, "Skipping a history open deleted since it started")
                    return@withLock
                }
            }
            if (deletedHistoryAtByKey.remove(key) != null) persistTombstonesLocked()
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
            tellOpened(listOf(entry))
        }
    }

    suspend fun prependOrReplaceHistoryEntries(entries: List<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        deletionGate.withLock {
            ensureTombstonesLoadedLocked()
            val removed = entries.mapTo(mutableSetOf(), ::historyEntryIdentity)
                .count { deletedHistoryAtByKey.remove(it) != null }
            if (removed > 0) persistTombstonesLocked()
            runMutation(
                missingSnapshotMessage = "Skipping history prepend batch due to missing snapshot",
                buildPlan = { currentHistory ->
                    resolveAppStateHistoryBatchPrependPlan(currentHistory, entries)
                }
            ) { dedupedSize ->
                "Failed to prepend $dedupedSize history entries"
            }
            tellOpened(entries)
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
            ensureTombstonesLoadedLocked()
            historyEntryIdentity(entry).takeIf(String::isNotBlank)?.let { key ->
                deletedHistoryAtByKey[key] = HistoryTombstone(
                    atMillis = Clock.System.now().toEpochMilliseconds(),
                    sequence = nextDeletionSequence(),
                    entryLastVisitedEpochMillis = entry.lastVisitedEpochMillis
                )
                trimDeletedHistoryTombstones()
                persistTombstonesLocked()
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

    /** Removes several entries with one write. Each removed entry gets a tombstone, as with [removeHistoryEntry]. */
    suspend fun removeHistoryEntries(entries: Collection<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        entries.forEach { entry ->
            scrollPersistenceCoordinator.cancelPendingForHistoryEntry(
                threadId = entry.threadId,
                boardId = entry.boardId,
                boardUrl = entry.boardUrl
            )
        }
        deletionGate.withLock {
            ensureTombstonesLoadedLocked()
            val keys = entries.map(::historyEntryIdentity).filterTo(mutableSetOf(), String::isNotBlank)
            if (keys.isEmpty()) return@withLock
            val deletedAt = Clock.System.now().toEpochMilliseconds()
            entries.forEach { entry ->
                historyEntryIdentity(entry).takeIf(String::isNotBlank)?.let { key ->
                    deletedHistoryAtByKey[key] = HistoryTombstone(deletedAt, nextDeletionSequence(), entry.lastVisitedEpochMillis)
                }
            }
            trimDeletedHistoryTombstones()
            persistTombstonesLocked()
            runMutation(
                missingSnapshotMessage = "Skipping history removal due to missing snapshot",
                buildPlan = { currentHistory ->
                    val updated = currentHistory.filterNot { historyEntryIdentity(it) in keys }
                    if (updated.size == currentHistory.size) null
                    else AppStateHistoryMutationPlan(updatedHistory = updated, metadata = currentHistory.size - updated.size)
                }
            ) { count ->
                "Failed to remove $count history entries"
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
        val persistKey = buildHistoryScrollJobKey(request.threadId, request.boardId, request.boardUrl)
        val lastPersistedAtMillis = scrollPersistGate.withLock { lastScrollPersistAtByKey[persistKey] }
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        var committed = false
        runMutation(
            missingSnapshotMessage = "Skipping history scroll persistence due to missing snapshot",
            onCommitted = { committed = true },
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
                    nowMillis = nowMillis,
                    forcePersist = request.forcePersist,
                    lastPersistedAtMillis = lastPersistedAtMillis
                )
            }
        ) { targetThreadId ->
            "Failed to persist updated history for thread $targetThreadId"
        }
        if (committed) {
            scrollPersistGate.withLock {
                lastScrollPersistAtByKey.remove(persistKey)
                lastScrollPersistAtByKey[persistKey] = nowMillis
                while (lastScrollPersistAtByKey.size > MAX_SCROLL_PERSIST_TIMESTAMPS) {
                    lastScrollPersistAtByKey.remove(lastScrollPersistAtByKey.keys.first())
                }
            }
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
        // Recent deletions are kept whatever their number: a bulk removal of more rows than the cap
        // (ふたったー風's "delete all") must not let a stale writer still in flight bring part of it back.
        // Only tombstones older than the retention can go, oldest first.
        val keepAfterMillis = Clock.System.now().toEpochMilliseconds() - MIN_DELETED_HISTORY_TOMBSTONE_RETENTION_MILLIS
        deletedHistoryAtByKey.entries
            .filter { (oldestImport == null || it.value.sequence <= oldestImport) && it.value.atMillis < keepAfterMillis }
            .sortedWith(compareBy({ it.value.atMillis }, { it.value.sequence }))
            .take(deletedHistoryAtByKey.size - MAX_DELETED_HISTORY_TOMBSTONES)
            .forEach { deletedHistoryAtByKey.remove(it.key) }
    }

    /**
     * Marks the start of an archive import. Call before reading the archive:
     * deletions and clears made after this point win over the imported entries.
     */
    suspend fun beginHistoryImport(): HistoryImportTicket = deletionGate.withLock {
        ensureTombstonesLoadedLocked()
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
            if (accepted.mapTo(HashSet(), ::historyEntryIdentity).count { deletedHistoryAtByKey.remove(it) != null } > 0) {
                persistTombstonesLocked()
            }
            var merge = resolveHistoryArchiveImportMergeEntries(emptyList(), emptyList())
            runMutation(
                missingSnapshotMessage = "Skipping history import due to missing snapshot",
                buildPlan = { currentHistory ->
                    merge = resolveHistoryArchiveImportMergeEntries(currentHistory, accepted)
                    merge.updatedHistory.takeIf { it != currentHistory }
                        ?.let { AppStateHistoryMutationPlan(it, Unit) }
                }
            ) { "Failed to merge ${accepted.size} imported history entries" }
            tellOpened(accepted)
            merge.copy(skippedCount = merge.skippedCount + (imported.size - accepted.size))
        } finally {
            activeImports -= ticket
        }
    }

    private companion object {
        const val MAX_DELETED_HISTORY_TOMBSTONES = 1_024
        // Deletions younger than this are never trimmed (the cap applies to older ones only). History holds at
        // most a few tens of thousands of rows, so this bounds the map by what was deleted in the last minutes.
        const val MIN_DELETED_HISTORY_TOMBSTONE_RETENTION_MILLIS = 10 * 60 * 1_000L
        const val MAX_SCROLL_PERSIST_TIMESTAMPS = 256
    }
}

internal data class HistoryTombstone(
    val atMillis: Long,
    val sequence: Long,
    /** The deleted row's last visit (orders the kept tombstones that share [atMillis]). */
    val entryLastVisitedEpochMillis: Long = 0L
)

class HistoryImportTicket internal constructor(internal val sequence: Long)
