package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication

private const val IOS_APP_ICON_MANAGER_TAG = "IosAppIconManager"
private const val IOS_CLASSIC_ICON_NAME = "AppIconClassic"

@OptIn(ExperimentalForeignApi::class)
actual fun applyAppIconVariant(
    platformContext: Any?,
    variant: AppIconVariant
) {
    val application = UIApplication.sharedApplication
    val setNameSelector = NSSelectorFromString("setAlternateIconName:completionHandler:")
    if (!application.respondsToSelector(setNameSelector)) {
        Logger.d(
            IOS_APP_ICON_MANAGER_TAG,
            "Alternate icons are not supported on this iOS runtime"
        )
        return
    }

    val targetName = when (variant) {
        AppIconVariant.Current -> null
        AppIconVariant.Classic -> IOS_CLASSIC_ICON_NAME
        AppIconVariant.Midnight -> null
    }

    runCatching {
        application.performSelector(setNameSelector, targetName, null)
    }.onFailure { error ->
        Logger.w(
            IOS_APP_ICON_MANAGER_TAG,
            "Failed to apply alternate icon: ${error.message}"
        )
    }
}
