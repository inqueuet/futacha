package com.valoser.futacha

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes the periodic and one-time HistoryRefreshWorker runs of this process (G-8).
 *
 * They are separate unique works, so WorkManager starts them side by side; the
 * shared-feature patrol and the compatibility refresh have no lock of their own
 * and ran twice. A run that waited while another run of the same [K] (profile
 * and generation) finished successfully is skipped, because that run already
 * did its work. A run that cannot get the gate within the wait limit is skipped
 * too, so the wait plus a full run stays within WorkManager's execution window.
 */
internal class BackgroundRunGate<K : Any> {
    private val mutex = Mutex()
    private val finishedRuns = AtomicLong(0L)

    /** Key and run number of the last successful run; accessed under [mutex]. */
    private var lastSuccess: Pair<K, Long>? = null

    suspend fun <R> runExclusive(
        key: K,
        maxWaitMillis: Long,
        isSuccess: (R) -> Boolean,
        onSkipped: () -> R,
        block: suspend () -> R
    ): R {
        val finishedBeforeWaiting = finishedRuns.get()
        var acquired = false
        // The flag, not the timeout's return value: a timeout or a stop racing the
        // resumed lock() could otherwise lose an acquired lock.
        try {
            withTimeoutOrNull(maxWaitMillis) {
                mutex.lock()
                acquired = true
            }
        } catch (cancelled: CancellationException) {
            if (acquired) mutex.unlock()
            throw cancelled
        }
        if (!acquired) return onSkipped()
        try {
            val last = lastSuccess
            if (last != null && last.first == key && last.second > finishedBeforeWaiting) {
                return onSkipped()
            }
            var succeeded = false
            try {
                val result = block()
                succeeded = isSuccess(result)
                return result
            } finally {
                val run = finishedRuns.incrementAndGet()
                if (succeeded) lastSuccess = key to run
            }
        } finally {
            mutex.unlock()
        }
    }
}

/**
 * Records that a run left out a step because another refresh held it (H4-4).
 *
 * Such a run still finishes successfully, but it must not count as covering a
 * run that waited on [BackgroundRunGate]: the skipped step was done by nobody.
 */
internal class BackgroundRunCoverage {
    @Volatile
    var isComplete: Boolean = true
        private set

    /** Runs [step]; when it is busy elsewhere ([isBusy]), skips only this step. */
    suspend fun <T> runSkippingIfBusy(isBusy: (Exception) -> Boolean, step: suspend () -> T): T? = try {
        step()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (!isBusy(error)) throw error
        isComplete = false
        null
    }
}

/**
 * Waits while the mode switch that produced [expectedGeneration] is still
 * clearing its journal (M4-3).
 *
 * The switch persists the new generation, which enqueues this work, before it
 * clears the journal; a work started in between failed the commit check and
 * finished without doing anything. Returns false when the journal of that
 * generation is still there after [maxWaitMillis]; a journal of another
 * generation is left to the caller's own generation check.
 */
internal suspend fun awaitModeSwitchJournalCleared(
    expectedGeneration: Long,
    readJournalGeneration: () -> Long?,
    maxWaitMillis: Long,
    pollMillis: Long = MODE_SWITCH_JOURNAL_POLL_MILLIS
): Boolean {
    fun pendingForThisWork(): Boolean =
        expectedGeneration >= 0L && readJournalGeneration() == expectedGeneration
    if (!pendingForThisWork()) return true
    return withTimeoutOrNull(maxWaitMillis) {
        while (pendingForThisWork()) delay(pollMillis)
        true
    } ?: !pendingForThisWork()
}

private const val MODE_SWITCH_JOURNAL_POLL_MILLIS = 200L

/**
 * Filters, delivers and records watch matches as one step under [lock] (G-7).
 *
 * Reading the ledger before taking the lock let two runs both see a match as
 * new and both post its notification. [deliver] returns whether the matches
 * should be recorded as notified.
 */
internal fun <T> deliverNewMatchesOnce(
    lock: Any,
    matches: List<T>,
    filterNew: (List<T>) -> List<T>,
    markDelivered: (List<T>) -> Unit,
    deliver: (List<T>) -> Boolean
) {
    if (matches.isEmpty()) return
    synchronized(lock) {
        val fresh = filterNew(matches)
        if (fresh.isEmpty()) return
        if (deliver(fresh)) markDelivered(fresh)
    }
}
