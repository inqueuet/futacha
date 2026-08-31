package com.valoser.futacha.shared.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
