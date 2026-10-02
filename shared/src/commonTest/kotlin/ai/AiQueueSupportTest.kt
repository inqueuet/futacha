package com.valoser.futacha.shared.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.test.*

class AiQueueSupportTest {
    @Test fun boundedWaitGivesUpWithoutTakingTheLock() = runBlocking {
        val lock = Mutex(locked = true)
        var ran = false
        assertNull(lock.withLockWithin(50) { ran = true; Unit })
        assertFalse(ran)
        lock.unlock()
        assertFalse(lock.isLocked, "a timed-out waiter must not own the lock")
    }

    @Test fun deadlineInsideTheActionStartsAfterTheWait() = runBlocking {
        val lock = Mutex(locked = true)
        val waiter = async { lock.withLockWithin(2_000) { withTimeoutOrNull(300) { delay(200); "done" } } }
        delay(250) // longer than the inner deadline's work, shorter than the wait bound
        lock.unlock()
        assertEquals("done", waiter.await())
        assertFalse(lock.isLocked)
    }

    @Test fun cancellationWhileWaitingOrRunningReleasesTheLock() = runBlocking {
        val lock = Mutex(locked = true)
        val waiting = launch { lock.withLockWithin(10_000) { Unit } }
        delay(20); waiting.cancelAndJoin()
        lock.unlock()
        assertFalse(lock.isLocked)
        val running = launch { lock.withLockWithin(10_000) { awaitCancellation() } }
        delay(20); running.cancelAndJoin()
        assertFalse(lock.isLocked)
    }
}
