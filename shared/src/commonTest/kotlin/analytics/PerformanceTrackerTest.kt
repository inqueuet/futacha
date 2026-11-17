package com.valoser.futacha.shared.analytics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PerformanceTrackerTest {
    @Test
    fun measure_preservesSuccessfulResultAndOriginalFailure() {
        assertEquals(42, PerformanceTracker.measure("test success") { 42 })

        val original = IllegalStateException("original")
        val thrown = assertFailsWith<IllegalStateException> {
            PerformanceTracker.measure("test failure") { throw original }
        }
        assertSame(original, thrown)
    }

    @Test
    fun measureSuspend_propagatesCancellation() = runBlocking {
        val original = CancellationException("cancelled")
        val thrown = assertFailsWith<CancellationException> {
            PerformanceTracker.measureSuspend("test cancellation") { throw original }
        }
        assertSame(original, thrown)
    }
}
