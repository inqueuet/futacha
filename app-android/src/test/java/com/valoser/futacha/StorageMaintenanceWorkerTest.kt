package com.valoser.futacha

import org.junit.Assert.assertTrue
import org.junit.Test

class StorageMaintenanceWorkerTest {
    @Test
    fun buildConstraints_requiresSafeMaintenanceConditions() {
        val constraints = StorageMaintenanceWorker.buildConstraints()

        assertTrue(constraints.requiresCharging())
        assertTrue(constraints.requiresDeviceIdle())
        assertTrue(constraints.requiresBatteryNotLow())
        assertTrue(constraints.requiresStorageNotLow())
    }
}
