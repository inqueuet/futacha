package com.valoser.futacha

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveReportRetryPolicyTest {
    @Test
    fun pendingRetryIsReplacedSoDelaysDoNotAddUp() {
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            archiveReportRetryWorkPolicy(calledFromRetryWork = false, retryWorkRunning = false)
        )
        // The retry work schedules its own next run as the only one.
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            archiveReportRetryWorkPolicy(calledFromRetryWork = true, retryWorkRunning = false)
        )
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            archiveReportRetryWorkPolicy(calledFromRetryWork = true, retryWorkRunning = true)
        )
    }

    @Test
    fun aRetryRunningElsewhereIsNotCancelledMidBatch() {
        assertEquals(
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            archiveReportRetryWorkPolicy(calledFromRetryWork = false, retryWorkRunning = true)
        )
    }
}
