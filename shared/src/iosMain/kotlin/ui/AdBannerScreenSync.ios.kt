package com.valoser.futacha.shared.ui

import platform.Foundation.NSUserDefaults
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val THREAD_SCREEN_AD_VISIBLE_KEY = "thread_screen_ad_visible"

internal actual fun syncAdBannerVisibility(
    isThreadScreen: Boolean
) {
    dispatch_async(dispatch_get_main_queue()) {
        NSUserDefaults.standardUserDefaults().setBool(isThreadScreen, forKey = THREAD_SCREEN_AD_VISIBLE_KEY)
        println("syncAdBannerVisibility(thread_screen_ad_visible=$isThreadScreen)")
    }
}
