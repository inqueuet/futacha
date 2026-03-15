package com.valoser.futacha.shared.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AppStateHistoryScrollPersistenceCoordinator(
    private val debounceDelayMillis: Long,
    private val buildScrollKey: (AppStateHistoryScrollUpdateRequest) -> String,
    private val performImmediateUpdate: suspend (AppStateHistoryScrollUpdateRequest) -> Unit
) {
    private val scrollPositionMutex = Mutex()
    private val scrollPositionJobs = AtomicJobMap()
    private var scrollDebounceScope: CoroutineScope? = null

    suspend fun setScope(scope: CoroutineScope) {
        scrollPositionMutex.withLock {
            scrollDebounceScope = scope
        }
    }

    suspend fun schedule(request: AppStateHistoryScrollUpdateRequest) {
        val scrollKey = buildScrollKey(request)
        scheduleAppStateHistoryScrollPersistence(
            scrollPositionMutex = scrollPositionMutex,
            currentScope = { scrollDebounceScope },
            clearScope = { scrollDebounceScope = null },
            scrollPositionJobs = scrollPositionJobs,
            scrollKey = scrollKey,
            startDebouncedJob = { scope, key ->
                scope.launch {
                    delay(debounceDelayMillis)
                    try {
                        performImmediateUpdate(request)
                    } finally {
                        scrollPositionJobs.removeIfSame(key, this.coroutineContext[Job])
                    }
                }
            },
            performImmediateUpdate = {
                performImmediateUpdate(request)
            }
        )
    }
}
