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

    /**
     * A screen can be removed before its debounced write reaches the delay.
     * Cancel that write before committing the final position, otherwise the
     * old delayed request can overwrite the position captured on navigation.
     */
    suspend fun cancelPending(request: AppStateHistoryScrollUpdateRequest) {
        val scrollKey = buildScrollKey(request)
        val pending = scrollPositionMutex.withLock {
            scrollPositionJobs.remove(scrollKey)
        }
        pending?.cancel()
    }

    suspend fun cancelPendingForHistoryEntry(
        threadId: String,
        boardId: String,
        boardUrl: String
    ) {
        val scrollKey = buildScrollKey(
            AppStateHistoryScrollUpdateRequest(
                threadId = threadId,
                index = 0,
                offset = 0,
                boardId = boardId,
                title = "",
                titleImageUrl = "",
                boardName = "",
                boardUrl = boardUrl,
                replyCount = 0
            )
        )
        val pending = scrollPositionMutex.withLock {
            scrollPositionJobs.remove(scrollKey)
        }
        pending?.cancel()
    }

    suspend fun cancelAllPending() {
        val pending = scrollPositionMutex.withLock {
            scrollPositionJobs.cancelAndClear()
        }
        pending.forEach(Job::cancel)
    }
}
