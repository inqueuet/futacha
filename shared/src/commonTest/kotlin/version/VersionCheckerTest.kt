package com.valoser.futacha.shared.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCheckerTest {
    @Test
    fun iosUpdatePrompt_isFlexibleUntilDaySevenAndImmediateAfterward() {
        assertEquals(UpdatePromptStyle.FLEXIBLE, selectIosUpdatePromptStyle(0))
        assertEquals(UpdatePromptStyle.FLEXIBLE, selectIosUpdatePromptStyle(1))
        assertEquals(UpdatePromptStyle.FLEXIBLE, selectIosUpdatePromptStyle(6))
        assertEquals(UpdatePromptStyle.IMMEDIATE, selectIosUpdatePromptStyle(7))
        assertEquals(UpdatePromptStyle.IMMEDIATE, selectIosUpdatePromptStyle(30))
    }

    @Test
    fun updateStaleness_usesCompletedDaysAndClampsFutureDates() {
        val day = 86_400_000L
        assertEquals(0, calculateUpdateStalenessDays(10 * day, 9 * day))
        assertEquals(0, calculateUpdateStalenessDays(10 * day, 10 * day + day - 1))
        assertEquals(1, calculateUpdateStalenessDays(10 * day, 11 * day))
        assertEquals(7, calculateUpdateStalenessDays(10 * day, 17 * day))
    }

    @Test
    fun buildUpdateMessage_onlyShowsVersionAvailability() {
        val message = buildUpdateMessage(
            current = "1.0.0",
            latest = "1.1.0",
            releaseName = "Release **1.1.0**",
            releaseBody = "- [改善](https://example.com)しました\n\n> 引用"
        )

        assertTrue(message.contains("現在: v1.0.0"))
        assertTrue(message.contains("最新: v1.1.0"))
        assertTrue(message.contains("新しいバージョンがあります"))
        assertTrue(!message.contains("Release"))
        assertTrue(!message.contains("改善"))
    }

    @Test
    fun isNewerVersion_comparesLargeNumericPreReleaseIdentifiersWithoutOverflow() {
        assertTrue(
            isNewerVersion(
                currentVersion = "1.0.0-999999999999999999999999999999",
                latestVersion = "1.0.0-1000000000000000000000000000000"
            )
        )
        assertFalse(
            isNewerVersion(
                currentVersion = "1.0.0-1000000000000000000000000000000",
                latestVersion = "1.0.0-999999999999999999999999999999"
            )
        )
    }

    @Test
    fun isNewerVersion_rejectsMalformedPreReleaseIdentifiers() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0-"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0-alpha..1"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0-alpha_1"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0.1"))
    }

    @Test
    fun isNewerVersion_ignoresHyphensInsideBuildMetadata() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0+build-999"))
        assertTrue(isNewerVersion("1.0.0+local-1", "1.0.1+build-1"))
    }
}
