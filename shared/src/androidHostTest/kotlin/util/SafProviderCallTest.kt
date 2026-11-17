package com.valoser.futacha.shared.util

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SafProviderCallTest {
    @Test fun nonInterruptibleProviderDoesNotHoldTheCallerAndLateHandlesAreClosed() = runBlocking {
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        try {
            assertNull(withTimeoutOrNull(150L) {
                awaitSafProviderCall {
                    while (release.count > 0) {
                        try { release.await() } catch (_: InterruptedException) { }
                    }
                    Closeable { closed.countDown() }
                }
            })
        } finally { release.countDown() }
        assertTrue(closed.await(3, TimeUnit.SECONDS))
    }

    @Test fun providerCallKeepsTheCallersSaveBatchListing() = runBlocking {
        val index = SafTreeIndex()
        val seen = withContext(index) {
            awaitSafProviderCall {
                // Nested calls (e.g. close inside a write) see the same batch.
                awaitSafProviderCall { currentCoroutineContext()[SafTreeIndex] }
            }
        }
        assertSame(index, seen)
        assertNull(awaitSafProviderCall { currentCoroutineContext()[SafTreeIndex] })
    }

    @Test fun hungProviderCallsDoNotBlockOtherSafCalls() = runBlocking {
        val release = CountDownLatch(1)
        try {
            repeat(4) {
                assertNull(withTimeoutOrNull(50L) {
                    awaitSafProviderCall {
                        while (release.count > 0) {
                            try { release.await() } catch (_: InterruptedException) { }
                        }
                    }
                })
            }
            assertEquals(42, withTimeout(3_000L) { awaitSafProviderCall { 42 } })
        } finally { release.countDown() }
    }

    @Test fun hungProviderThreadsAreCappedAndFreedWhenTheyReturn() = runBlocking {
        awaitInFlight(0)
        val release = CountDownLatch(1)
        try {
            repeat(MAX_SAF_PROVIDER_CALLS_IN_FLIGHT) {
                assertNull(withTimeoutOrNull(20L) {
                    awaitSafProviderCall {
                        while (release.count > 0) {
                            try { release.await() } catch (_: InterruptedException) { }
                        }
                    }
                })
            }
            assertEquals(MAX_SAF_PROVIDER_CALLS_IN_FLIGHT, safProviderCallsInFlight())
            // Past the cap a call fails at once instead of starting another thread.
            assertFailsWith<SaveProviderTimeoutException> {
                withTimeout(1_000L) { awaitSafProviderCall { 1 } }
            }
            assertEquals(MAX_SAF_PROVIDER_CALLS_IN_FLIGHT, safProviderCallsInFlight())
        } finally { release.countDown() }
        awaitInFlight(0)
        assertEquals(7, withTimeout(3_000L) { awaitSafProviderCall { 7 } })
    }

    private fun awaitInFlight(expected: Int) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (safProviderCallsInFlight() != expected && System.currentTimeMillis() < deadline) Thread.sleep(10L)
        assertEquals(expected, safProviderCallsInFlight())
    }
}
