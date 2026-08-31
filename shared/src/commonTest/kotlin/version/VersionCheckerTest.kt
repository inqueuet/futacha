package com.valoser.futacha.shared.version

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCheckerTest {
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
