@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.ui.board.supportsIosAsyncVideoComposition
import com.valoser.futacha.shared.ui.board.supportsIosWebmPath
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.iOSAppOnMac

/** An iOS release number: the version whose frameworks the process runs with. */
internal data class IosEquivalentVersion(val major: Long, val minor: Long)

/**
 * The iOS release whose frameworks a process reporting [reportedMajor].[reportedMinor] runs with.
 *
 * On an iPhone or iPad the reported version is the iOS version and is returned unchanged. An iPhone/iPad app on an
 * Apple-silicon Mac is told the macOS version by `operatingSystemVersion` (13 on Ventura), so comparing it with iOS
 * numbers judged WebM, the asynchronous video composition and ずんだもん unavailable on a Mac that has them (audit L77).
 * macOS 11–15 ship the frameworks of iOS 14–18 (Big Sur 11 ↔ iOS 14 … Sequoia 15 ↔ iOS 18, the minor releases
 * alongside: Sonoma 14.4 ↔ iOS 17.4); from 26 both carry the same number, as `__builtin_available(iOS …)` in the
 * voice runtime's C side already maps it.
 */
internal fun iosEquivalentVersion(reportedMajor: Long, reportedMinor: Long, isIosAppOnMac: Boolean): IosEquivalentVersion =
    if (isIosAppOnMac && reportedMajor in 11L..15L) {
        IosEquivalentVersion(reportedMajor + 3, reportedMinor)
    } else {
        IosEquivalentVersion(reportedMajor, reportedMinor)
    }

/** The iOS release this process runs with; see [iosEquivalentVersion]. */
internal fun currentIosEquivalentVersion(): IosEquivalentVersion {
    val info = NSProcessInfo.processInfo
    return info.operatingSystemVersion.useContents {
        iosEquivalentVersion(majorVersion, minorVersion, isIosAppOnMac = info.iOSAppOnMac)
    }
}

internal fun iosWebmPlaybackAvailable(): Boolean =
    currentIosEquivalentVersion().let { version -> supportsIosWebmPath(version.major, version.minor) }

internal fun iosAsyncVideoCompositionAvailable(): Boolean =
    supportsIosAsyncVideoComposition(currentIosEquivalentVersion().major)

internal const val IOS_WEBM_PREVIEW_UNAVAILABLE =
    "WebMのプレビューにはiOS 17.4以降が必要です。添付やファイルへの保存は利用できます。"
