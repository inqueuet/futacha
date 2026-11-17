package com.valoser.futacha

import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
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

    @Test
    fun flexibleDownloadStartedByAnEarlierScreenIsNotResumedAsImmediate() {
        // Play reports a running flexible download like an interrupted immediate flow.
        listOf(InstallStatus.PENDING, InstallStatus.DOWNLOADING).forEach { status ->
            assertEquals(
                AndroidInAppUpdateAction.AWAIT_FLEXIBLE_DOWNLOAD,
                resolveAndroidInAppUpdateAction(
                    installStatus = status,
                    updateAvailability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                    flexibleFlowRecorded = true,
                    allowStartingNewUpdate = true
                )
            )
        }
    }

    @Test
    fun activeDownloadReportedAsAvailableDoesNotStartAnotherFlow() {
        // A seven-day-old update would otherwise select the immediate flow here.
        assertEquals(
            AndroidInAppUpdateAction.AWAIT_FLEXIBLE_DOWNLOAD,
            resolveAndroidInAppUpdateAction(
                installStatus = InstallStatus.DOWNLOADING,
                updateAvailability = UpdateAvailability.UPDATE_AVAILABLE,
                flexibleFlowRecorded = false,
                allowStartingNewUpdate = true
            )
        )
    }

    @Test
    fun downloadedFlexibleUpdateShowsTheRestartPrompt() {
        assertEquals(
            AndroidInAppUpdateAction.SHOW_DOWNLOADED,
            resolveAndroidInAppUpdateAction(
                installStatus = InstallStatus.DOWNLOADED,
                updateAvailability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                flexibleFlowRecorded = true,
                allowStartingNewUpdate = true
            )
        )
    }

    @Test
    fun interruptedImmediateFlowIsStillResumed() {
        listOf(true, false).forEach { allowStarting ->
            assertEquals(
                AndroidInAppUpdateAction.RESUME_IMMEDIATE,
                resolveAndroidInAppUpdateAction(
                    installStatus = InstallStatus.DOWNLOADING,
                    updateAvailability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                    flexibleFlowRecorded = false,
                    allowStartingNewUpdate = allowStarting
                )
            )
        }
    }

    @Test
    fun availableUpdateStartsOnlyWhenNewFlowsAreAllowed() {
        assertEquals(
            AndroidInAppUpdateAction.START_NEW,
            resolveAndroidInAppUpdateAction(
                installStatus = InstallStatus.UNKNOWN,
                updateAvailability = UpdateAvailability.UPDATE_AVAILABLE,
                flexibleFlowRecorded = false,
                allowStartingNewUpdate = true
            )
        )
        assertEquals(
            AndroidInAppUpdateAction.NONE,
            resolveAndroidInAppUpdateAction(
                installStatus = InstallStatus.UNKNOWN,
                updateAvailability = UpdateAvailability.UPDATE_AVAILABLE,
                flexibleFlowRecorded = false,
                allowStartingNewUpdate = false
            )
        )
    }

    @Test
    fun flexibleFlowRecordMatchesOnlyTheOfferedVersion() {
        val record = encodeInAppUpdateFlowRecord(AndroidInAppUpdateKind.FLEXIBLE, 180)
        assertTrue(isFlexibleInAppUpdateFlowRecordFor(record, 180))
        assertFalse(isFlexibleInAppUpdateFlowRecordFor(record, 181))
        assertFalse(
            isFlexibleInAppUpdateFlowRecordFor(
                encodeInAppUpdateFlowRecord(AndroidInAppUpdateKind.IMMEDIATE, 180),
                180
            )
        )
        assertFalse(isFlexibleInAppUpdateFlowRecordFor(null, 180))
    }
}
