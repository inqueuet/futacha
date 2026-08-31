package com.valoser.futacha.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutachaAppObservedRuntimeStateSupportTest {
    @Test
    fun shouldContinuouslyRefreshFutachaAiAvailability_requiresVisibleThreadScreen() {
        assertFalse(
            shouldContinuouslyRefreshFutachaAiAvailability(
                isThreadSummaryModeEnabled = true,
                isAiPostFilterEnabled = false,
                isThreadScreenVisible = false
            )
        )
        assertFalse(
            shouldContinuouslyRefreshFutachaAiAvailability(
                isThreadSummaryModeEnabled = false,
                isAiPostFilterEnabled = true,
                isThreadScreenVisible = false
            )
        )
        assertTrue(
            shouldContinuouslyRefreshFutachaAiAvailability(
                isThreadSummaryModeEnabled = true,
                isAiPostFilterEnabled = false,
                isThreadScreenVisible = true
            )
        )
        assertTrue(
            shouldContinuouslyRefreshFutachaAiAvailability(
                isThreadSummaryModeEnabled = false,
                isAiPostFilterEnabled = true,
                isThreadScreenVisible = true
            )
        )
    }

    @Test
    fun shouldContinuouslyRefreshFutachaAiAvailability_staysIdleWhenAiFeaturesAreDisabled() {
        assertFalse(
            shouldContinuouslyRefreshFutachaAiAvailability(
                isThreadSummaryModeEnabled = false,
                isAiPostFilterEnabled = false,
                isThreadScreenVisible = true
            )
        )
    }
}
