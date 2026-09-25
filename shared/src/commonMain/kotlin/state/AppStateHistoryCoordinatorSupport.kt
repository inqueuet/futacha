package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.withContext
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal class AppStateHistoryCoordinator(
    private val storage: PlatformStateStorage,
    private val historyFileStore: AppStateHistoryFileStore?,
    private val json: Json,
    private val tag: String,
    private val rethrowIfCancellation: (Throwable) -> Unit,
    private val maxEntries: Int = historyFileStore?.maxEntries ?: APP_STATE_HISTORY_MAX_ENTRIES,
    /**
     * Receives entries the size limit dropped, after the history without them was
     * written. It must not block: storage cleanup runs elsewhere.
     */
    private val onEntriesTrimmed: (List<ThreadHistoryEntry>) -> Unit = {}
) {
    private val historyMutex = Mutex()
    private val historyPersistMutex = Mutex()
    private var cachedHistory: List<ThreadHistoryEntry>? = null
    private var historyRevision: Long = 0L
    private var persistedHistoryRevision: Long = 0L

    suspend fun setHistory(requestedHistory: List<ThreadHistoryEntry>) {
        val trimmed = mutableListOf<ThreadHistoryEntry>()
        val history = withContext(AppDispatchers.io) { boundToLimit(requestedHistory, emptySet(), trimmed) }
        val (revision, previousRevision, previousHistory) = historyMutex.withLock {
            val beforeRevision = historyRevision
            val beforeHistory = cachedHistory
            cachedHistory = history
            historyRevision = beforeRevision + 1L
            Triple(historyRevision, beforeRevision, beforeHistory)
        }
        try {
            persistHistory(revision, history)
        } catch (error: Exception) {
            rethrowIfCancellation(error)
            rollbackHistoryMutation(
                failedRevision = revision,
                previousRevision = previousRevision,
                previousHistory = previousHistory
            )
            throw error
        }
        reportTrimmed(trimmed)
    }

    suspend fun <T> runMutation(
        missingSnapshotMessage: String,
        buildPlan: (List<ThreadHistoryEntry>) -> AppStateHistoryMutationPlan<T>?,
        onCommitted: (T) -> Unit = {},
        buildFailureMessage: (T) -> String
    ) {
        val trimmed = mutableListOf<ThreadHistoryEntry>()
        val mutation = withContext(AppDispatchers.io) {
            prepareHistoryMutation(missingSnapshotMessage, buildPlan, trimmed)
        } ?: return
        persistHistoryMutation(
            mutation = mutation,
            onCommitted = onCommitted,
            buildFailureMessage = buildFailureMessage
        )
        reportTrimmed(trimmed)
    }

    private fun reportTrimmed(trimmed: List<ThreadHistoryEntry>) {
        if (trimmed.isEmpty()) return
        runCatching { onEntriesTrimmed(trimmed.toList()) }.onFailure { error ->
            Logger.w(tag, "Failed to schedule cleanup of trimmed history entries: ${error.message}")
        }
    }

    private suspend fun readHistorySnapshot(): List<ThreadHistoryEntry>? {
        return readAppStateHistorySnapshot(
            historyMutex = historyMutex,
            currentCachedHistory = { cachedHistory },
            readStorageHistory = {
                if (historyFileStore != null) {
                    historyFileStore.readHistorySnapshot(
                        clearLegacyHistoryJson = { storage.updateHistoryJson("[]") }
                    ) {
                        storage.historyJson.first()
                    }
                } else {
                    val raw = storage.historyJson.first()
                    if (raw == null) {
                        emptyList()
                    } else {
                        decodeAppStateHistory(raw, json, tag)
                    }
                }
            },
            setCachedHistory = { cachedHistory = it },
            onReadFailure = { error ->
                Logger.e(tag, "Failed to read history state", error)
            },
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    private fun readHistorySnapshotLocked(): List<ThreadHistoryEntry>? {
        return cachedHistory
    }

    private suspend fun rollbackHistoryMutation(
        failedRevision: Long,
        previousRevision: Long,
        previousHistory: List<ThreadHistoryEntry>?
    ) {
        rollbackAppStateHistoryMutation(
            historyMutex = historyMutex,
            failedRevision = failedRevision,
            previousRevision = previousRevision,
            previousHistory = previousHistory,
            currentHistoryRevision = { historyRevision },
            restoreHistoryState = { restoredRevision, restoredHistory ->
                historyRevision = restoredRevision
                cachedHistory = restoredHistory
            }
        )
    }

    private suspend fun <T> prepareHistoryMutation(
        missingSnapshotMessage: String,
        buildPlan: (List<ThreadHistoryEntry>) -> AppStateHistoryMutationPlan<T>?,
        trimmed: MutableList<ThreadHistoryEntry>
    ): HistoryMutation<T>? {
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(tag, missingSnapshotMessage)
            return null
        }
        return buildAppStateHistoryMutation(
            historyMutex = historyMutex,
            historySnapshot = historySnapshot,
            readLockedHistory = ::readHistorySnapshotLocked,
            currentRevision = { historyRevision },
            previousHistory = { cachedHistory },
            updateCachedState = { revision, history ->
                historyRevision = revision
                cachedHistory = history
            },
            buildPlan = { current ->
                buildPlan(current)?.let { plan ->
                    val currentKeys = current.mapTo(HashSet(), ::historyEntryIdentity)
                    val addedKeys = plan.updatedHistory.map(::historyEntryIdentity)
                        .filterTo(HashSet()) { it !in currentKeys }
                    trimmed.clear()
                    plan.copy(updatedHistory = boundToLimit(plan.updatedHistory, addedKeys, trimmed))
                }
            }
        )
    }

    private fun boundToLimit(
        history: List<ThreadHistoryEntry>,
        protectedKeys: Set<String>,
        trimmed: MutableList<ThreadHistoryEntry>
    ): List<ThreadHistoryEntry> {
        val bounded = trimAppStateHistoryToLimit(history, maxEntries, protectedKeys)
        if (bounded.size < history.size) {
            Logger.i(tag, "Dropped ${history.size - bounded.size} oldest history entries beyond the $maxEntries limit")
            val keptKeys = bounded.mapTo(HashSet(), ::historyEntryIdentity)
            history.filterTo(trimmed) { entry ->
                historyEntryIdentity(entry).let { key -> key.isNotBlank() && key !in keptKeys }
            }
        }
        return bounded
    }

    private suspend fun <T> persistHistoryMutation(
        mutation: HistoryMutation<T>,
        onCommitted: (T) -> Unit = {},
        buildFailureMessage: (T) -> String
    ) {
        persistAppStateHistoryMutation(
            mutation = mutation,
            persistHistory = ::persistHistory,
            rollbackHistoryMutation = ::rollbackHistoryMutation,
            onPersistFailure = { message, error ->
                Logger.e(tag, message, error)
            },
            rethrowIfCancellation = rethrowIfCancellation,
            onCommitted = onCommitted,
            buildFailureMessage = buildFailureMessage
        )
    }

    private suspend fun persistHistory(revision: Long, history: List<ThreadHistoryEntry>) {
        persistAppStateHistory(
            historyPersistMutex = historyPersistMutex,
            revision = revision,
            history = history,
            writeHistoryJson = { _, updatedHistory ->
                if (historyFileStore != null) {
                    historyFileStore.persistHistorySnapshot(updatedHistory)
                    if (updatedHistory.isEmpty()) {
                        // Keep an explicit empty legacy value. If the split
                        // manifest is later damaged or removed, startup must
                        // not migrate an old history_json payload back.
                        storage.updateHistoryJson("[]")
                    }
                } else {
                    writeAppStateHistoryJson(
                        history = updatedHistory,
                        encodeHistoryJson = { targetHistory ->
                            encodeAppStateHistoryMeasured(targetHistory, json)
                        },
                        updateHistoryJson = storage::updateHistoryJson
                    )
                }
            },
            readLatestHistoryContinuation = { targetRevision ->
                historyMutex.withLock {
                    if (historyRevision > targetRevision) {
                        val latest = cachedHistory
                        if (latest != null) {
                            AppStatePersistedHistoryContinuation(
                                revision = historyRevision,
                                history = latest
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            },
            shouldSkipRevision = { targetRevision ->
                targetRevision <= persistedHistoryRevision
            },
            markPersistedRevision = { targetRevision ->
                persistedHistoryRevision = maxOf(persistedHistoryRevision, targetRevision)
            }
        )
    }
}
