package com.valoser.futacha.shared.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultBoardRepositoryPermitRaceTest {
    @Test
    fun permitIsReleasedWhenAcquireTimeoutFiresAfterAcquireReturned() = runBlocking {
        val real = Semaphore(2)
        // acquire() takes the permit and then keeps the thread busy past the timeout,
        // so the timeout fires after the permit was obtained but before the block returns.
        val racing = object : Semaphore by real {
            override suspend fun acquire() {
                delay(1)
                real.acquire()
                Thread.sleep(200)
            }
        }

        val result = withContext(Dispatchers.Default) {
            runDefaultBoardRepositoryHelperWithPermit(
                semaphoreTimeoutMillis = 50L,
                fetchTimeoutMillis = 1_000L,
                semaphore = racing
            ) { "fetched" }
        }

        assertTrue(result.timedOut)
        assertEquals(2, real.availablePermits)
    }
}
