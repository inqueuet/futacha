package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatForegroundChecksTest {
    @Test
    fun referenceRawPoliciesAndLegacyDisplayValuesRemainReadable() {
        assertEquals(
            CompatForegroundNetworkPolicy.ALWAYS,
            parseCompatForegroundNetworkPolicy("usually")
        )
        assertEquals(
            CompatForegroundNetworkPolicy.ALWAYS,
            parseCompatForegroundNetworkPolicy("常に確認する")
        )
        assertEquals(
            CompatForegroundNetworkPolicy.WIFI_ONLY,
            parseCompatForegroundNetworkPolicy("wifi")
        )
        assertEquals(
            CompatForegroundNetworkPolicy.NONE,
            parseCompatForegroundNetworkPolicy("none")
        )
        assertTrue(compatForegroundPolicyEnabled("usually"))
        assertFalse(compatForegroundPolicyEnabled("none"))
    }

    @Test
    fun hiddenLastCheckTimesUseReferenceEpochSecondsAndSurviveRestart() {
        val now = 1_787_650_000_987L
        val stored = compatForegroundLastCheckStoredValue(now)
        assertEquals("1787650000", stored)
        assertEquals(1_787_650_000_000L, parseCompatForegroundLastCheckEpochMillis(stored))
        assertEquals(now, parseCompatForegroundLastCheckEpochMillis(now.toString()))

        val coldStart = planCompatForegroundChecks(
            nowEpochMillis = now,
            lastUpdateCheckEpochMillis = 0L,
            lastExistenceCheckEpochMillis = 0L,
            updatePolicy = CompatForegroundNetworkPolicy.ALWAYS,
            existencePolicy = CompatForegroundNetworkPolicy.ALWAYS,
            isWifiConnected = false
        )
        assertTrue(coldStart.checkUpdates)
        assertTrue(coldStart.checkExistence)

        val reopened = planCompatForegroundChecks(
            nowEpochMillis = now + 299_000L,
            lastUpdateCheckEpochMillis = parseCompatForegroundLastCheckEpochMillis(stored),
            lastExistenceCheckEpochMillis = parseCompatForegroundLastCheckEpochMillis(stored),
            updatePolicy = CompatForegroundNetworkPolicy.ALWAYS,
            existencePolicy = CompatForegroundNetworkPolicy.ALWAYS,
            isWifiConnected = false
        )
        assertFalse(reopened.hasWork)
    }

    @Test
    fun updateAndExistenceUseFiveAndFifteenMinuteIntervals() {
        val start = 1_000_000L
        assertFalse(
            planCompatForegroundChecks(
                start + 299_999,
                start,
                start,
                CompatForegroundNetworkPolicy.ALWAYS,
                CompatForegroundNetworkPolicy.ALWAYS,
                isWifiConnected = false
            ).hasWork
        )
        val update = planCompatForegroundChecks(
            start + 300_000,
            start,
            start,
            CompatForegroundNetworkPolicy.ALWAYS,
            CompatForegroundNetworkPolicy.ALWAYS,
            isWifiConnected = false
        )
        assertTrue(update.checkUpdates)
        assertFalse(update.checkExistence)
        val both = planCompatForegroundChecks(
            start + 900_000,
            start + 600_000,
            start,
            CompatForegroundNetworkPolicy.ALWAYS,
            CompatForegroundNetworkPolicy.ALWAYS,
            isWifiConnected = false
        )
        assertTrue(both.checkUpdates)
        assertTrue(both.checkExistence)
    }

    @Test
    fun historicalWifiPolicyUsesThePlatformUnmeteredSignalAndNoneNeverRuns() {
        val offWifi = planCompatForegroundChecks(
            2_000_000L,
            0L,
            0L,
            CompatForegroundNetworkPolicy.WIFI_ONLY,
            CompatForegroundNetworkPolicy.NONE,
            isWifiConnected = false
        )
        assertFalse(offWifi.hasWork)
        val onWifi = planCompatForegroundChecks(
            2_000_000L,
            0L,
            0L,
            CompatForegroundNetworkPolicy.WIFI_ONLY,
            CompatForegroundNetworkPolicy.NONE,
            isWifiConnected = true
        )
        assertTrue(onWifi.checkUpdates)
        assertFalse(onWifi.checkExistence)
    }

    @Test
    fun targetExistenceStaleBoundaryAndReplyCountExcludeOpeningPost() {
        assertEquals(1_800_000L, COMPAT_THREAD_EXISTENCE_STALE_MILLIS)
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "board",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post("123", author = null, subject = null, timestamp = "", messageHtml = "op", imageUrl = null, thumbnailUrl = null),
                Post("124", author = null, subject = null, timestamp = "", messageHtml = "reply", imageUrl = null, thumbnailUrl = null),
                Post("125", author = null, subject = null, timestamp = "", messageHtml = "reply", imageUrl = null, thumbnailUrl = null)
            )
        )
        assertEquals(2, page.compatReplyCount())
    }
}
