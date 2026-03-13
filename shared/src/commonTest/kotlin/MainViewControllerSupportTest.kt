package com.valoser.futacha.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainViewControllerSupportTest {
    @Test
    fun calculateIosBackgroundRefreshFlowBackoffMillis_caps_growth() {
        assertEquals(1_000L, calculateIosBackgroundRefreshFlowBackoffMillis(0))
        assertEquals(2_000L, calculateIosBackgroundRefreshFlowBackoffMillis(1))
        assertEquals(32_000L.coerceAtMost(30_000L), calculateIosBackgroundRefreshFlowBackoffMillis(5))
        assertEquals(30_000L, calculateIosBackgroundRefreshFlowBackoffMillis(6))
    }

    @Test
    fun resolveIosBackgroundRefreshFlowRetryState_honors_max_retry_limit() {
        val allowed = resolveIosBackgroundRefreshFlowRetryState(
            attempt = 11,
            maxRetries = 12
        )
        assertTrue(allowed.shouldRetry)
        assertEquals(30_000L, allowed.backoffMillis)

        val blocked = resolveIosBackgroundRefreshFlowRetryState(
            attempt = 12,
            maxRetries = 12
        )
        assertFalse(blocked.shouldRetry)
        assertNull(blocked.backoffMillis)
    }
}
