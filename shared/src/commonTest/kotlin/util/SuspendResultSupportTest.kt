package com.valoser.futacha.shared.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SuspendResultSupportTest {
    @Test
    fun cancellationIsRethrown() {
        runBlocking {
            assertFailsWith<CancellationException> {
                runSuspendCatchingPreservingCancellation<Unit> {
                    throw CancellationException("cancel")
                }
            }
        }
    }

    @Test
    fun ordinaryFailureIsReturned() {
        runBlocking {
            val result = runSuspendCatchingPreservingCancellation<Unit> {
                error("failure")
            }
            assertTrue(result.isFailure)
            assertEquals("failure", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun fatalErrorIsRethrown() {
        runBlocking {
            assertFailsWith<AssertionError> {
                runSuspendCatchingPreservingCancellation<Unit> {
                    throw AssertionError("fatal")
                }
            }
        }
    }

    @Test
    fun localTimeoutIsReturnedAsFailure() {
        runBlocking {
            val result = runSuspendCatchingPreservingCancellation<Unit> {
                withTimeout(1) { delay(100) }
            }
            assertTrue(result.exceptionOrNull() is kotlinx.coroutines.TimeoutCancellationException)
        }
    }
}
