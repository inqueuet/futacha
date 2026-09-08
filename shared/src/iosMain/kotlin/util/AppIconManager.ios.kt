package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.alternateIconName

private const val IOS_APP_ICON_MANAGER_TAG = "IosAppIconManager"
private const val IOS_CLASSIC_ICON_NAME = "AppIconClassic"

@OptIn(ExperimentalForeignApi::class)
actual fun applyAppIconVariant(
    platformContext: Any?,
    variant: AppIconVariant
) {
    setIosAlternateIconName(
        when (variant) {
            AppIconVariant.Current -> null
            AppIconVariant.Classic -> IOS_CLASSIC_ICON_NAME
            AppIconVariant.Midnight -> null
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun setIosAlternateIconName(targetName: String?) {
    val application = UIApplication.sharedApplication
    if (application.alternateIconName == targetName) return
    val setNameSelector = NSSelectorFromString("setAlternateIconName:completionHandler:")
    if (!application.respondsToSelector(setNameSelector)) {
        Logger.d(
            IOS_APP_ICON_MANAGER_TAG,
            "Alternate icons are not supported on this iOS runtime"
        )
        return
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
