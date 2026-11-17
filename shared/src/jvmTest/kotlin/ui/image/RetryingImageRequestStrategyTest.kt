@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.image

import coil3.fetch.FetchResult
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.*

class RetryingImageRequestStrategyTest {
    private fun failing(calls: IntArray): suspend () -> FetchResult = {
        calls[0]++
        throw IOException("Unable to resolve host")
    }

    @Test fun offlineConnectionFailureDoesNotSpendTheRetryBudget() = runBlocking {
        val calls = IntArray(1)
        var waits = 0
        val strategy = RetryingImageRequestStrategy(isOnline = { false }, offlineGraceMillis = 50) { _, _ -> waits++ }
        assertFailsWith<IOException> { strategy.apply("offline.jpg", failing(calls)) }
        assertEquals(1, calls[0], "A fully offline, uncached image must fail after its one attempt")
        assertEquals(0, waits)
    }

    @Test fun offlineUnderMemoryPressureFailsWithoutWaitingForTheNetwork() = runBlocking {
        val calls = IntArray(1)
        var polls = 0
        val strategy = RetryingImageRequestStrategy(isOnline = { polls++; false }, skipOfflineWait = { true },
            offlineGraceMillis = 60_000) { _, _ -> fail("must not back off") }
        assertFailsWith<IOException> { strategy.apply("pressure.jpg", failing(calls)) }
        assertEquals(1, calls[0])
        assertEquals(1, polls, "The scarce permit must not be held while polling connectivity")
    }

    @Test fun networkReturningDuringTheGraceKeepsRetrying() = runBlocking {
        val calls = IntArray(1)
        var checks = 0
        // Offline at the first failure (handover lag), back online on the next poll.
        val strategy = RetryingImageRequestStrategy(isOnline = { checks++ > 0 }, offlineGraceMillis = 5_000) { _, _ -> }
        assertFailsWith<IOException> { strategy.apply("handover.jpg", failing(calls)) }
        assertEquals(3, calls[0])
    }

    @Test fun onlineConnectionFailuresKeepTheThreeAttemptBudget() = runBlocking {
        val calls = IntArray(1)
        var waits = 0
        val strategy = RetryingImageRequestStrategy { _, _ -> waits++ }
        assertFailsWith<IOException> { strategy.apply("online.jpg", failing(calls)) }
        assertEquals(3, calls[0])
        assertEquals(2, waits)
    }

    @Test fun cacheOnlyMissIsNeverRetriedOrDelayed() = runBlocking {
        val calls = IntArray(1)
        val strategy = RetryingImageRequestStrategy(isOnline = { false }) { _, _ -> fail("must not back off") }
        assertFailsWith<ImageNotCachedException> { strategy.apply("cached.jpg") { calls[0]++; throw ImageNotCachedException() } }
        assertEquals(1, calls[0])
    }
}
