package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedFeaturePhaseBudgetsTest {
    @Test
    fun shortRunLeavesTheWatchWordCatalogsTheirShare() {
        // iOS BGAppRefresh gives the patrol 4 s.
        val phases = sharedFeaturePhaseBudgets(4_000L, checkUpdates = true, checkExistence = true, checkWatchWords = true)

        assertEquals(1_200L, phases.updateMillis)
        assertEquals(800L, phases.existenceMillis)
        assertEquals(800L, phases.watchCheckMillis)
        // Without caps the update phase used the whole budget before the watch words.
        assertTrue(4_000L - phases.updateMillis!! - phases.existenceMillis >= 2_000L)
    }

    @Test
    fun shortRunWithOnlyTheUpdatePhaseKeepsTheWholeBudget() {
        val phases = sharedFeaturePhaseBudgets(4_000L, checkUpdates = true, checkExistence = false, checkWatchWords = false)

        assertNull(phases.updateMillis)
    }

    @Test
    fun shortRunWithoutExistenceSplitsUpdateAndWatch() {
        val phases = sharedFeaturePhaseBudgets(3_000L, checkUpdates = true, checkExistence = false, checkWatchWords = true)

        assertEquals(1_125L, phases.updateMillis)
        assertEquals(750L, phases.watchCheckMillis)
        assertEquals(COMPAT_EXISTENCE_BUDGET_MILLIS, phases.existenceMillis)
    }

    @Test
    fun longOrUnlimitedRunsKeepThePhaseDefaults() {
        val defaults = SharedFeaturePhaseBudgets()

        assertEquals(defaults, sharedFeaturePhaseBudgets(null, true, true, true))
        // Android passes 2 minutes and BGProcessing 3 minutes.
        assertEquals(defaults, sharedFeaturePhaseBudgets(120_000L, true, true, true))
        assertEquals(defaults, sharedFeaturePhaseBudgets(SHARED_FEATURE_SPLIT_BELOW_MILLIS, true, true, true))
    }
}
