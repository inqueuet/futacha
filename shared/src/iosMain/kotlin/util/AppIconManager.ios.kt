package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.AppIconVariant
import platform.UIKit.UIApplication

private const val IOS_APP_ICON_MANAGER_TAG = "IosAppIconManager"
private const val IOS_CLASSIC_ICON_NAME = "AppIconClassic"
private const val IOS_MIDNIGHT_ICON_NAME = "AppIconMidnight"

actual fun applyAppIconVariant(
    platformContext: Any?,
    variant: AppIconVariant
) {
    val application = UIApplication.sharedApplication
    if (!application.supportsAlternateIcons) return
    val targetName = when (variant) {
        AppIconVariant.Current -> null
        AppIconVariant.Classic -> IOS_CLASSIC_ICON_NAME
        AppIconVariant.Midnight -> IOS_MIDNIGHT_ICON_NAME
    }
    if (application.alternateIconName == targetName) return
    application.setAlternateIconName(targetName) { error ->
        if (error != null) {
            Logger.w(
                IOS_APP_ICON_MANAGER_TAG,
                "Failed to apply alternate icon: ${error.localizedDescription}"
            )
        }
    }
}
