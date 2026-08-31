package com.valoser.futacha.shared.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnalyticsSdkCallSupportTest {
    @Test
    fun sdkBoundaryCapturesOrdinaryExceptions() {
        val error = IllegalStateException("sdk failure")
        val result = runAnalyticsSdkCatching<Int> { throw error }

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun sdkBoundaryDoesNotSwallowFatalErrors() {
        val error = AssertionError("fatal")
        val thrown = assertFailsWith<AssertionError> {
            runAnalyticsSdkCatching<Int> { throw error }
        }

        assertSame(error, thrown)
        assertEquals("fatal", thrown.message)
    }
}
