package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSThread
import platform.UIKit.UIApplication
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.supportsAlternateIcons
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val IOS_APP_ICON_MANAGER_TAG = "IosAppIconManager"
private const val IOS_CLASSIC_ICON_NAME = "AppIconClassic"

@OptIn(ExperimentalForeignApi::class)
actual fun applyAppIconVariant(
    platformContext: Any?,
    variant: AppIconVariant
) {
    val targetName = when (variant) {
        AppIconVariant.Current -> null
        AppIconVariant.Classic -> IOS_CLASSIC_ICON_NAME
        AppIconVariant.Midnight -> null
    }
    // UIApplication icon APIs are main-thread only; the mode switch
    // coordinator may call this from a background coroutine.
    if (NSThread.isMainThread) {
        setIosAlternateIconName(targetName)
    } else {
        dispatch_async(dispatch_get_main_queue()) { setIosAlternateIconName(targetName) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun setIosAlternateIconName(targetName: String?) {
    val application = UIApplication.sharedApplication
    if (application.alternateIconName == targetName) return
    val setNameSelector = NSSelectorFromString("setAlternateIconName:completionHandler:")
    if (!application.respondsToSelector(setNameSelector) || !application.supportsAlternateIcons) {
        Logger.d(
            IOS_APP_ICON_MANAGER_TAG,
            "Alternate icons are not supported on this iOS runtime"
        )
        return
    }

    // Call the typed binding: performSelector on this void method made
    // Kotlin/Native treat the garbage return register as an object.
    application.setAlternateIconName(targetName) { error ->
        if (error != null) {
            Logger.w(
                IOS_APP_ICON_MANAGER_TAG,
                "Failed to apply alternate icon: ${error.localizedDescription}"
            )
        }
    }
}
