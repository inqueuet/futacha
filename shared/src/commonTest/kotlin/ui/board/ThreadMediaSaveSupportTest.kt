package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadMediaSaveSupportTest {
    @Test
    fun resolveMediaSaveAvailability_prioritizes_blocking_states() {
        assertEquals(
            MediaSaveAvailability.Busy,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = true,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            MediaSaveAvailability.Unsupported,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = false,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            MediaSaveAvailability.LocationRequired,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = true,
                hasStorageDependencies = true
            )
        )
        assertEquals(
            MediaSaveAvailability.Unavailable,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = false
            )
        )
        assertEquals(
            MediaSaveAvailability.Ready,
            resolveMediaSaveAvailability(
                isAnySaveInProgress = false,
                isRemoteMedia = true,
                requiresManualLocationSelection = false,
                hasStorageDependencies = true
            )
        )
    }

    @Test
    fun resolveThreadMediaSaveRequestState_maps_ready_and_location_required() {
        val ready = resolveThreadMediaSaveRequestState(
            isAnySaveInProgress = false,
            isRemoteMedia = true,
            requiresManualLocationSelection = false,
            hasStorageDependencies = true
        )
        assertTrue(ready.canStartSave)
        assertEquals(null, ready.message)
        assertFalse(ready.shouldOpenSaveDirectoryPicker)

        val locationRequired = resolveThreadMediaSaveRequestState(
            isAnySaveInProgress = false,
            isRemoteMedia = true,
            requiresManualLocationSelection = true,
            hasStorageDependencies = true
        )
        assertFalse(locationRequired.canStartSave)
        assertTrue(locationRequired.shouldOpenSaveDirectoryPicker)
        assertEquals(buildThreadSaveLocationRequiredMessage(), locationRequired.message)
    }
}
