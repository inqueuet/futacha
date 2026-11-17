@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.audio.voicevox.createVoicePlatform
import com.valoser.futacha.shared.ui.board.supportsIosAsyncVideoComposition
import com.valoser.futacha.shared.ui.board.supportsIosWebmPath
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.iOSAppOnMac
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * L77: an iPhone/iPad app on an Apple-silicon Mac is told the macOS version by `operatingSystemVersion`, so the
 * iOS checks judged WebM (iOS 17.4), the asynchronous composition and ずんだもん (iOS 16) unavailable on a Mac that
 * has them. The version is mapped to the iOS release the Mac runs; an iPhone or iPad is left exactly as it was.
 */
class IosEquivalentVersionTest {
    @Test
    fun anIphoneOrIpadKeepsTheReportedVersion() {
        listOf(15L to 1L, 16L to 0L, 17L to 3L, 17L to 4L, 18L to 6L, 26L to 5L, 13L to 0L).forEach { (major, minor) ->
            assertEquals(IosEquivalentVersion(major, minor), iosEquivalentVersion(major, minor, isIosAppOnMac = false))
        }
    }

    @Test
    fun aMacMapsItsVersionToTheIosReleaseItRuns() {
        assertEquals(IosEquivalentVersion(14, 2), iosEquivalentVersion(11, 2, isIosAppOnMac = true))
        assertEquals(IosEquivalentVersion(15, 7), iosEquivalentVersion(12, 7, isIosAppOnMac = true))
        assertEquals(IosEquivalentVersion(16, 6), iosEquivalentVersion(13, 6, isIosAppOnMac = true))
        assertEquals(IosEquivalentVersion(17, 4), iosEquivalentVersion(14, 4, isIosAppOnMac = true))
        assertEquals(IosEquivalentVersion(18, 3), iosEquivalentVersion(15, 3, isIosAppOnMac = true))
        // From 26 macOS and iOS share one number.
        assertEquals(IosEquivalentVersion(26, 1), iosEquivalentVersion(26, 1, isIosAppOnMac = true))
        assertEquals(IosEquivalentVersion(27, 0), iosEquivalentVersion(27, 0, isIosAppOnMac = true))
    }

    @Test
    fun aMacWithTheFeaturesIsNoLongerJudgedWithoutThem() {
        fun webm(major: Long, minor: Long) =
            iosEquivalentVersion(major, minor, isIosAppOnMac = true).let { supportsIosWebmPath(it.major, it.minor) }
        fun ios16(major: Long) = iosEquivalentVersion(major, 0, isIosAppOnMac = true).major >= 16
        // Sonoma 14.4 has the iOS 17.4 WebKit; Ventura 13 has the iOS 16 frameworks.
        assertTrue(webm(14, 4)); assertTrue(webm(15, 0)); assertTrue(webm(26, 0))
        assertFalse(webm(14, 3)); assertFalse(webm(13, 6))
        assertTrue(ios16(13)); assertTrue(supportsIosAsyncVideoComposition(iosEquivalentVersion(13, 0, true).major))
        assertFalse(ios16(12)); assertFalse(supportsIosAsyncVideoComposition(iosEquivalentVersion(12, 7, true).major))
    }

    @Test
    fun thisSimulatorRunsTheIphonePathUnchanged() {
        val info = NSProcessInfo.processInfo
        assertFalse(info.iOSAppOnMac)
        val reported = info.operatingSystemVersion.useContents { IosEquivalentVersion(majorVersion, minorVersion) }
        assertEquals(reported, currentIosEquivalentVersion())
        assertEquals(supportsIosWebmPath(reported.major, reported.minor), iosWebmPlaybackAvailable())
        assertEquals(supportsIosAsyncVideoComposition(reported.major), iosAsyncVideoCompositionAvailable())
        assertEquals(reported.major >= 16, createVoicePlatform(null).available)
    }
}
