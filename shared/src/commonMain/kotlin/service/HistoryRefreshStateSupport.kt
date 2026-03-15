package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

internal data class HistoryRefreshAbortThreshold(
    val label: String,
    val minAttempts: Int,
    val minFailures: Int,
    val failureRateThreshold: Float
)

internal data class HistoryRefreshCountersSnapshot(
    val attemptedCount: Int,
    val successfulCount: Int,
    val hardFailureCount: Int,
    val autoSaveCount: Int,
    val threadRefreshFailureCount: Int,
    val archiveLookupFailureCount: Int
)

internal class HistoryRefreshRunStats {
    private val mutex = Mutex()
    private var attemptedCount = 0
    private var successfulCount = 0
    private var hardFailureCount = 0
    private var autoSaveCount = 0
    private var threadRefreshFailureCount = 0
    private var archiveLookupFailureCount = 0

    suspend fun markAttempt() {
        mutex.withLock {
            attemptedCount += 1
        }
    }

    suspend fun markSuccess() {
        mutex.withLock {
            successfulCount += 1
        }
    }

    suspend fun recordHardFailure() {
        mutex.withLock {
            hardFailureCount += 1
        }
    }

    suspend fun recordThreadRefreshFailure() {
        mutex.withLock {
            hardFailureCount += 1
            threadRefreshFailureCount += 1
        }
    }

    suspend fun recordArchiveLookupFailure() {
        mutex.withLock {
            hardFailureCount += 1
            archiveLookupFailureCount += 1
        }
    }

    suspend fun threadRefreshAbortReason(threshold: HistoryRefreshAbortThreshold): String? {
        return mutex.withLock {
            abortReasonLocked(threadRefreshFailureCount, threshold)
        }
    }

    suspend fun archiveLookupAbortReason(threshold: HistoryRefreshAbortThreshold): String? {
        return mutex.withLock {
            abortReasonLocked(archiveLookupFailureCount, threshold)
        }
    }

    suspend fun tryReserveAutoSaveSlot(
        nowMillis: Long,
        autoSaveDeadline: Long?,
        maxAutoSavesPerRefresh: Int
    ): Boolean {
        return mutex.withLock {
            if (autoSaveDeadline != null && nowMillis > autoSaveDeadline) {
                false
            } else if (autoSaveCount >= maxAutoSavesPerRefresh) {
                false
            } else {
                autoSaveCount += 1
                true
            }
        }
    }

    suspend fun releaseAutoSaveSlotIfReserved() {
        mutex.withLock {
            if (autoSaveCount > 0) {
                autoSaveCount -= 1
            }
        }
    }

    suspend fun snapshot(): HistoryRefreshCountersSnapshot {
        return mutex.withLock {
            HistoryRefreshCountersSnapshot(
                attemptedCount = attemptedCount,
                successfulCount = successfulCount,
                hardFailureCount = hardFailureCount,
                autoSaveCount = autoSaveCount,
                threadRefreshFailureCount = threadRefreshFailureCount,
                archiveLookupFailureCount = archiveLookupFailureCount
            )
        }
    }

    private fun abortReasonLocked(
        failureCount: Int,
        threshold: HistoryRefreshAbortThreshold
    ): String? {
        if (attemptedCount < threshold.minAttempts) return null
        if (successfulCount > 0) return null
        if (failureCount < threshold.minFailures) return null
        val failureRate = failureCount.toFloat() / attemptedCount.toFloat()
        if (failureRate < threshold.failureRateThreshold) return null
        val ratePercent = (failureRate * 100).toInt()
        return "Aborting history refresh due to persistent ${threshold.label} failures " +
            "($failureCount/$attemptedCount, ${ratePercent}%)"
    }
}

internal class HistoryRefreshErrorTracker(
    private val maxErrorsToTrack: Int
) {
    private val mutex = Mutex()
    private val errors = mutableListOf<HistoryRefresher.ErrorDetail>()

    suspend fun record(threadId: String, message: String, stage: String) {
        mutex.withLock {
            if (errors.size < maxErrorsToTrack) {
                errors.add(
                    HistoryRefresher.ErrorDetail(
                        threadId = threadId,
                        message = message,
                        stage = stage
                    )
                )
            }
        }
    }

    suspend fun snapshot(): List<HistoryRefresher.ErrorDetail> {
        return mutex.withLock { errors.toList() }
    }
}

internal class HistoryRefreshUpdateBuffer(
    private val stateStore: AppStateStore,
    private val tag: String,
    private val maxUpdatesToAccumulate: Int,
    private val maxFlushRetries: Int,
    private val flushRetryDelayMillis: Long,
    private val flushRetryMaxDelayMillis: Long,
    private val recordError: suspend (threadId: String, message: String, stage: String) -> Unit,
    private val onFlushFailure: suspend () -> Unit
) {
    private val updatesMutex = Mutex()
    private val updates = mutableMapOf<HistoryRefreshKey, ThreadHistoryEntry>()

    suspend fun put(key: HistoryRefreshKey, entry: ThreadHistoryEntry) {
        updatesMutex.withLock {
            updates[key] = entry
        }
    }

    suspend fun flush(force: Boolean, failureStage: String): Boolean {
        val flushSnapshot = updatesMutex.withLock {
            if (!force && updates.size < maxUpdatesToAccumulate) {
                null
            } else if (updates.isEmpty()) {
                null
            } else {
                if (!force) {
                    Logger.i(tag, "Flushing ${updates.size} updates to prevent memory spike")
                }
                updates.toMap()
            }
        }
        if (flushSnapshot == null) return true

        var lastError: Throwable? = null
        repeat(maxFlushRetries) { attempt ->
            try {
                stateStore.mergeHistoryEntries(flushSnapshot.values)
                updatesMutex.withLock {
                    flushSnapshot.forEach { (key, entry) ->
                        if (updates[key] == entry) {
                            updates.remove(key)
                        }
                    }
                }
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                lastError = error
                val isLastAttempt = attempt >= maxFlushRetries - 1
                if (!isLastAttempt) {
                    Logger.w(
                        tag,
                        "Retrying history flush (${attempt + 1}/$maxFlushRetries): ${error.message}"
                    )
                    val backoffMultiplier = 1L shl attempt
                    val retryDelay = (flushRetryDelayMillis * backoffMultiplier)
                        .coerceAtMost(flushRetryMaxDelayMillis)
                    delay(retryDelay)
                    yield()
                }
            }
        }

        val failure = lastError
        Logger.e(
            tag,
            "Failed to flush ${flushSnapshot.size} history updates after $maxFlushRetries attempts",
            failure
        )
        recordError(
            "history-flush",
            failure?.message ?: "Failed to flush history updates",
            failureStage
        )
        onFlushFailure()
        return false
    }
}
