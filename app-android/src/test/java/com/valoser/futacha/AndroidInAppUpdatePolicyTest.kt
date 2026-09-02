package com.valoser.futacha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidInAppUpdatePolicyTest {
    @Test
    fun updateBecomesEmergencyAtDaySeven() {
        assertFalse(isAndroidInAppUpdateEmergency(stalenessDays = 6, updatePriority = 0))
        assertTrue(isAndroidInAppUpdateEmergency(stalenessDays = 7, updatePriority = 0))
    }

    @Test
    fun playPriorityFourIsEmergencyImmediately() {
        assertTrue(
            isAndroidInAppUpdateEmergency(
                stalenessDays = 0,
                updatePriority = IMMEDIATE_UPDATE_PRIORITY
            )
        )
    }

    @Test
    fun newlyRecognizedUpdateUsesFlexibleFlowImmediately() {
        assertEquals(
            AndroidInAppUpdateKind.FLEXIBLE,
            selectAndroidInAppUpdateKind(
                stalenessDays = FLEXIBLE_UPDATE_STALENESS_DAYS,
                updatePriority = 0,
                flexibleAllowed = true,
                immediateAllowed = true
            )
        )
    }

    @Test
    fun oneDayOldUpdateStillUsesFlexibleFlow() {
        assertEquals(
            AndroidInAppUpdateKind.FLEXIBLE,
            selectAndroidInAppUpdateKind(
                stalenessDays = 1,
                updatePriority = 0,
                flexibleAllowed = true,
                immediateAllowed = true
            )
        )
    }

    @Test
    fun sevenDayOldUpdateUsesImmediateFlow() {
        assertEquals(
            AndroidInAppUpdateKind.IMMEDIATE,
            selectAndroidInAppUpdateKind(
                stalenessDays = IMMEDIATE_UPDATE_STALENESS_DAYS,
                updatePriority = 0,
                flexibleAllowed = true,
                immediateAllowed = true
            )
        )
    }

    @Test
    fun highPriorityUpdateUsesImmediateFlowImmediately() {
        assertEquals(
            AndroidInAppUpdateKind.IMMEDIATE,
            selectAndroidInAppUpdateKind(
                stalenessDays = 0,
                updatePriority = IMMEDIATE_UPDATE_PRIORITY,
                flexibleAllowed = true,
                immediateAllowed = true
            )
        )
    }

    @Test
    fun unavailableStalenessFallsBackToFlexibleFlow() {
        assertEquals(
            AndroidInAppUpdateKind.FLEXIBLE,
            selectAndroidInAppUpdateKind(
                stalenessDays = null,
                updatePriority = 0,
                flexibleAllowed = true,
                immediateAllowed = true
            )
        )
    }

    @Test
    fun fallsBackToFlexibleWhenImmediateFlowIsNotAllowed() {
        assertEquals(
            AndroidInAppUpdateKind.FLEXIBLE,
            selectAndroidInAppUpdateKind(
                stalenessDays = IMMEDIATE_UPDATE_STALENESS_DAYS,
                updatePriority = IMMEDIATE_UPDATE_PRIORITY,
                flexibleAllowed = true,
                immediateAllowed = false
            )
        )
    }

    @Test
    fun highPriorityUpdateFallsBackToFlexibleWhenImmediateFlowIsNotAllowed() {
        assertEquals(
            AndroidInAppUpdateKind.FLEXIBLE,
            selectAndroidInAppUpdateKind(
                stalenessDays = 0,
                updatePriority = IMMEDIATE_UPDATE_PRIORITY,
                flexibleAllowed = true,
                immediateAllowed = false
            )
        )
    }
}
