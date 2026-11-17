package com.valoser.futacha.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchStatusNoticeTest {
    @Test
    fun nothingToReportKeepsTheStatusMessage() {
        assertNull(buildWatchStatusNotice(null, isPhoneUnsupported = false, areNotificationsBlocked = false))
        assertEquals(
            "同期を要求しました",
            buildWatchStatusNotice("同期を要求しました", isPhoneUnsupported = false, areNotificationsBlocked = false)
        )
    }

    @Test
    fun unsupportedPhoneReplacesTheOptimisticRequestMessage() {
        assertEquals(
            PHONE_UNSUPPORTED_NOTICE,
            buildWatchStatusNotice("スマホで更新を要求しました", isPhoneUnsupported = true, areNotificationsBlocked = false)
        )
    }

    @Test
    fun blockedNotificationsAreAppended() {
        assertEquals(NOTIFICATIONS_BLOCKED_NOTICE, buildWatchStatusNotice(null, false, true))
        assertEquals(
            "同期を要求しました\n$NOTIFICATIONS_BLOCKED_NOTICE",
            buildWatchStatusNotice("同期を要求しました", false, true)
        )
        assertEquals(
            "$PHONE_UNSUPPORTED_NOTICE\n$NOTIFICATIONS_BLOCKED_NOTICE",
            buildWatchStatusNotice("x", true, true)
        )
    }
}
