package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AppStatePersistedHistoryContinuation(
    val revision: Long,
    val history: List<ThreadHistoryEntry>
)

internal suspend fun readAppStateHistorySnapshot(
    historyMutex: Mutex,
    currentCachedHistory: () -> List<ThreadHistoryEntry>?,
    readStorageHistory: suspend () -> List<ThreadHistoryEntry>,
    setCachedHistory: (List<ThreadHistoryEntry>) -> Unit,
    onReadFailure: (Throwable) -> Unit,
    rethrowIfCancellation: (Throwable) -> Unit
): List<ThreadHistoryEntry>? {
    val cached = historyMutex.withLock { currentCachedHistory() }
    if (cached != null) {
        return cached
    }
    val decoded = try {
        readStorageHistory()
    } catch (e: Exception) {
        rethrowIfCancellation(e)
        onReadFailure(e)
        return null
    }
    return historyMutex.withLock {
        val latest = currentCachedHistory()
        if (latest != null) {
            latest
        } else {
            setCachedHistory(decoded)
            decoded
        }
    }
}

internal suspend fun rollbackAppStateHistoryMutation(
    historyMutex: Mutex,
    failedRevision: Long,
    previousRevision: Long,
    previousHistory: List<ThreadHistoryEntry>?,
    currentHistoryRevision: () -> Long,
    restoreHistoryState: (Long, List<ThreadHistoryEntry>?) -> Unit
) {
    historyMutex.withLock {
        if (currentHistoryRevision() == failedRevision) {
            restoreHistoryState(previousRevision, previousHistory)
        }
    }
}

internal suspend fun <T> persistAppStateHistoryMutation(
    mutation: HistoryMutation<T>,
    persistHistory: suspend (Long, List<ThreadHistoryEntry>) -> Unit,
    rollbackHistoryMutation: suspend (Long, Long, List<ThreadHistoryEntry>?) -> Unit,
    onPersistFailure: (String, Throwable) -> Unit,
    rethrowIfCancellation: (Throwable) -> Unit,
    onCommitted: (T) -> Unit = {},
    buildFailureMessage: (T) -> String
) {
    try {
        persistHistory(mutation.revision, mutation.updatedHistory)
        onCommitted(mutation.metadata)
    } catch (e: Exception) {
        rethrowIfCancellation(e)
        rollbackHistoryMutation(
            mutation.revision,
            mutation.previousRevision,
            mutation.previousHistory
        )
        onPersistFailure(buildFailureMessage(mutation.metadata), e)
        throw e
    }
}

internal suspend fun <T> buildAppStateHistoryMutation(
    historyMutex: Mutex,
    historySnapshot: List<ThreadHistoryEntry>,
    readLockedHistory: () -> List<ThreadHistoryEntry>?,
    currentRevision: () -> Long,
    previousHistory: () -> List<ThreadHistoryEntry>?,
    updateCachedState: (Long, List<ThreadHistoryEntry>) -> Unit,
    buildPlan: (List<ThreadHistoryEntry>) -> AppStateHistoryMutationPlan<T>?
): HistoryMutation<T>? {
    return historyMutex.withLock {
        val currentHistory = readLockedHistory() ?: historySnapshot
        val plan = buildPlan(currentHistory) ?: return@withLock null
        val mutation = createAppStateHistoryMutation(
            currentRevision = currentRevision(),
            previousHistory = previousHistory(),
            plan = plan
        )
        updateCachedState(mutation.revision, mutation.updatedHistory)
        mutation
    }
}

internal suspend fun persistAppStateHistory(
    historyPersistMutex: Mutex,
    revision: Long,
    history: List<ThreadHistoryEntry>,
    maxPasses: Int,
    writeHistoryJson: suspend (List<ThreadHistoryEntry>) -> Unit,
    readLatestHistoryContinuation: suspend (Long) -> AppStatePersistedHistoryContinuation?
) {
    historyPersistMutex.withLock {
        var targetRevision = revision
        var targetHistory = history
        var passCount = 0
        while (true) {
            passCount += 1
            if (passCount > maxPasses) {
                throw IllegalStateException(
                    "History persistence exceeded $maxPasses passes; aborting to prevent lock starvation"
                )
            }
            writeHistoryJson(targetHistory)
            val continuation = readLatestHistoryContinuation(targetRevision) ?: break
            targetRevision = continuation.revision
            targetHistory = continuation.history
        }
    }
}
