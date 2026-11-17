package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.analytics.CrashReporter
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Saving a reading position is best effort; an I/O failure must not end the UI scope. */
internal fun CoroutineScope.launchHistoryScrollPersistence(block: suspend CoroutineScope.() -> Unit): Job = launch {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Logger.e("ScrollPersistence", "Failed to save thread reading position", error)
        CrashReporter.recordNonFatal(error, keys = mapOf("operation" to "history_scroll_persistence"))
    }
}

/**
 * Final reading-position save used when a thread screen is disposed (D11).
 * The UI scope is usually being cancelled at that moment, so the write starts
 * undispatched (a cancelled job still begins executing) and runs under
 * [NonCancellable] so the last position actually reaches storage.
 */
internal fun CoroutineScope.launchFinalHistoryScrollPersistence(
    block: suspend CoroutineScope.() -> Unit
): Job = launch(start = CoroutineStart.UNDISPATCHED) {
    withContext(NonCancellable) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.e("ScrollPersistence", "Failed to save final thread reading position", error)
            CrashReporter.recordNonFatal(error, keys = mapOf("operation" to "history_scroll_persistence_final"))
        }
    }
}

internal suspend fun scheduleAppStateHistoryScrollPersistence(
    scrollPositionMutex: Mutex,
    currentScope: () -> CoroutineScope?,
    clearScope: () -> Unit,
    scrollPositionJobs: AtomicJobMap,
    scrollKey: String,
    startDebouncedJob: (CoroutineScope, String) -> Job,
    performImmediateUpdate: suspend () -> Unit
) {
    var runImmediate = false
    var oldJob: Job? = null
    var staleJobs: List<Job> = emptyList()
    scrollPositionMutex.withLock {
        val scope = currentScope()
        val scopeJob = scope?.coroutineContext?.get(Job)
        val isScopeInactive = scopeJob != null && !scopeJob.isActive
        if (scope == null || isScopeInactive) {
            if (isScopeInactive) {
                clearScope()
                staleJobs = scrollPositionJobs.cancelAndClear()
            }
            runImmediate = true
            return@withLock
        }

        val newJob = startDebouncedJob(scope, scrollKey)
        if (!newJob.isActive) {
            runImmediate = true
            return@withLock
        }

        oldJob = scrollPositionJobs.putAndCancelOld(scrollKey, newJob)
    }
    staleJobs.forEach { it.cancel() }
    oldJob?.cancel()
    if (runImmediate) {
        performImmediateUpdate()
    }
}
