@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.ui.board.supportsIosAsyncVideoComposition
import com.valoser.futacha.shared.ui.board.supportsIosWebmPath
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo

internal fun iosWebmPlaybackAvailable(): Boolean =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        supportsIosWebmPath(majorVersion, minorVersion)
    }

internal fun iosAsyncVideoCompositionAvailable(): Boolean =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        supportsIosAsyncVideoComposition(majorVersion)
    }

internal const val IOS_WEBM_PREVIEW_UNAVAILABLE =
    "WebMのプレビューにはiOS 17.4以降が必要です。添付やファイルへの保存は利用できます。"
