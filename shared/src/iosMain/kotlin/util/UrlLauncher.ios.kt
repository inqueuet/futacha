package com.valoser.futacha.shared.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

@Composable
actual fun rememberUrlLauncher(customTabs: Boolean): (String) -> Unit {
    return remember(customTabs) {
        { url: String ->
            try {
                val request = resolveUrlLaunchRequest(url)
                if (request == null) {
                    Logger.e("UrlLauncher", "Rejected URL (${describeUrlForLog(url)})", null)
                    presentIosAlert(
                        title = "リンクを開けません",
                        message = "URL を確認してください。"
                    )
                } else {
                    val nsUrl = NSURL.URLWithString(request.normalizedUrl)
                    if (nsUrl != null && UIApplication.sharedApplication.canOpenURL(nsUrl)) {
                        val parent = if (customTabs) findIosTopViewController() else null
                        if (parent != null && request.target == UrlLaunchTarget.Browser) {
                            IosInAppBrowser.present(parent, nsUrl)
                        } else UIApplication.sharedApplication.openURL(
                            nsUrl,
                            options = emptyMap<Any?, Any?>(),
                            completionHandler = null
                        )
                    } else {
                        Logger.e("UrlLauncher", "Failed to open URL (${describeUrlForLog(request.normalizedUrl)})", null)
                        presentIosAlert(
                            title = "このリンクは開けません",
                            message = "対応するアプリが見つかりません。"
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e(
                    "UrlLauncher",
                    "Failed to open URL (${describeUrlForLog(url)}), type=${describeFailureForLog(e)}"
                )
                presentIosAlert(
                    title = "リンクを開けません",
                    message = "もう一度お試しください。"
                )
            }
        }
    }
}

/**
 * The in-app browser (SFSafariViewController) that ふたったー風 opens. It lives outside Compose, so the app lock drawn
 * in Compose cannot cover it: when the lock is engaged it is closed, and after unlocking the same page is opened again
 * so nothing is lost (L127). Main thread only.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
internal object IosInAppBrowser {
    private var presented: kotlin.native.ref.WeakReference<SFSafariViewController>? = null
    private var presentedUrl: NSURL? = null
    private var reopenAfterUnlock: NSURL? = null

    fun present(parent: UIViewController, url: NSURL) {
        val browser = SFSafariViewController(uRL = url)
        presented = kotlin.native.ref.WeakReference(browser)
        presentedUrl = url
        parent.presentViewController(browser, true, null)
    }

    /** The lock was engaged on leaving the app. Called directly, since composition does not run under the browser. */
    fun onSessionLocked() {
        val browser = presented?.get()
        if (browser != null && browser.presentingViewController != null) {
            reopenAfterUnlock = presentedUrl
            browser.dismissViewControllerAnimated(false, completion = null)
        }
        presented = null
        presentedUrl = null
    }

    fun onAppUnlockedChanged(unlocked: Boolean) {
        if (!unlocked) {
            onSessionLocked()
        } else {
            val url = reopenAfterUnlock ?: return
            reopenAfterUnlock = null
            findIosTopViewController()?.let { present(it, url) }
        }
    }
}
