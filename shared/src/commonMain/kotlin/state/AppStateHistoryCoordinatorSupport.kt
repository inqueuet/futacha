package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal class AppStateHistoryCoordinator(
    private val storage: PlatformStateStorage,
    private val json: Json,
    private val tag: String,
    private val maxPersistPasses: Int,
    private val rethrowIfCancellation: (Throwable) -> Unit
) {
    private val historyMutex = Mutex()
    private val historyPersistMutex = Mutex()
    private var cachedHistory: List<ThreadHistoryEntry>? = null
    private var historyRevision: Long = 0L

    suspend fun setHistory(history: List<ThreadHistoryEntry>) {
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
    }

    suspend fun <T> runMutation(
        missingSnapshotMessage: String,
        buildPlan: (List<ThreadHistoryEntry>) -> AppStateHistoryMutationPlan<T>?,
        onCommitted: (T) -> Unit = {},
        buildFailureMessage: (T) -> String
    ) {
        val mutation = prepareHistoryMutation(
            missingSnapshotMessage = missingSnapshotMessage,
            buildPlan = buildPlan
        ) ?: return
        persistHistoryMutation(
            mutation = mutation,
            onCommitted = onCommitted,
            buildFailureMessage = buildFailureMessage
        )
    }

    private suspend fun readHistorySnapshot(): List<ThreadHistoryEntry>? {
        return readAppStateHistorySnapshot(
            historyMutex = historyMutex,
            currentCachedHistory = { cachedHistory },
            readStorageHistory = {
                val raw = storage.historyJson.first()
                if (raw == null) {
                    emptyList()
                } else {
                    decodeAppStateHistory(raw, json, tag)
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
        buildPlan: (List<ThreadHistoryEntry>) -> AppStateHistoryMutationPlan<T>?
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
            buildPlan = buildPlan
        )
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
            maxPasses = maxPersistPasses,
            writeHistoryJson = { updatedHistory ->
                storage.updateHistoryJson(encodeAppStateHistory(updatedHistory, json))
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
            }
        )
    }
}
