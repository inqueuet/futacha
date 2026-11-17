package com.valoser.futacha

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchAlertPermissionDecisionTest {
    @Test
    fun disablingNeverRequestsPermission() {
        assertEquals(
            WatchAlertPermissionAction.DISABLE,
            resolveWatchAlertPermissionAction(
                requestedEnabled = false,
                runtimePermissionRequired = true,
                permissionGranted = false
            )
        )
    }

    @Test
    fun preAndroid13EnablesWithoutRuntimePermission() {
        assertEquals(
            WatchAlertPermissionAction.ENABLE_IMMEDIATELY,
            resolveWatchAlertPermissionAction(
                requestedEnabled = true,
                runtimePermissionRequired = false,
                permissionGranted = false
            )
        )
    }

    @Test
    fun anAlreadyGrantedPermissionEnablesWithoutAnotherPrompt() {
        assertEquals(
            WatchAlertPermissionAction.ENABLE_IMMEDIATELY,
            resolveWatchAlertPermissionAction(
                requestedEnabled = true,
                runtimePermissionRequired = true,
                permissionGranted = true
            )
        )
    }

    @Test
    fun android13PlusExplainsBeforeRequestingAnAbsentPermission() {
        assertEquals(
            WatchAlertPermissionAction.EXPLAIN_AND_REQUEST_PERMISSION,
            resolveWatchAlertPermissionAction(
                requestedEnabled = true,
                runtimePermissionRequired = true,
                permissionGranted = false
            )
        )
    }
}
