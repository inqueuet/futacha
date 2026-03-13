package com.valoser.futacha.shared.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStateScrollPersistenceSupportTest {
    @Test
    fun scheduleAppStateHistoryScrollPersistence_runsImmediateWithoutScope() {
        runBlocking {
            val mutex = Mutex()
            val jobMap = AtomicJobMap()
            var immediateRuns = 0
            var startedJob = false

            scheduleAppStateHistoryScrollPersistence(
                scrollPositionMutex = mutex,
                currentScope = { null },
                clearScope = {},
                scrollPositionJobs = jobMap,
                scrollKey = "thread-key",
                startDebouncedJob = { _, _ ->
                    startedJob = true
                    Job()
                },
                performImmediateUpdate = { immediateRuns += 1 }
            )

            assertEquals(1, immediateRuns)
            assertTrue(!startedJob)
        }
    }

    @Test
    fun scheduleAppStateHistoryScrollPersistence_clearsInactiveScopeAndRunsImmediate() {
        runBlocking {
            val mutex = Mutex()
            val jobMap = AtomicJobMap()
            val inactiveScope = CoroutineScope(Job().apply { cancel() })
            var cleared = false
            var immediateRuns = 0

            scheduleAppStateHistoryScrollPersistence(
                scrollPositionMutex = mutex,
                currentScope = { inactiveScope },
                clearScope = { cleared = true },
                scrollPositionJobs = jobMap,
                scrollKey = "thread-key",
                startDebouncedJob = { _, _ -> Job() },
                performImmediateUpdate = { immediateRuns += 1 }
            )

            assertTrue(cleared)
            assertEquals(1, immediateRuns)
        }
    }

    @Test
    fun scheduleAppStateHistoryScrollPersistence_replacesOldJobWhenScopeIsActive() {
        runBlocking {
            val mutex = Mutex()
            val jobMap = AtomicJobMap()
            val scope = CoroutineScope(SupervisorJob())
            val oldJob = Job()
            jobMap.putAndCancelOld("thread-key", oldJob)
            var immediateRuns = 0
            var startedJob: Job? = null

            scheduleAppStateHistoryScrollPersistence(
                scrollPositionMutex = mutex,
                currentScope = { scope },
                clearScope = {},
                scrollPositionJobs = jobMap,
                scrollKey = "thread-key",
                startDebouncedJob = { _, _ ->
                    Job().also { startedJob = it }
                },
                performImmediateUpdate = { immediateRuns += 1 }
            )

            assertEquals(0, immediateRuns)
            assertTrue(oldJob.isCancelled)
            assertTrue(startedJob?.isActive == true)
            scope.coroutineContext[Job]?.cancel()
        }
    }
}
