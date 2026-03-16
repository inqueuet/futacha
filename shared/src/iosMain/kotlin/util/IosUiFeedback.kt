package com.valoser.futacha.shared.util

import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal fun findIosTopViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val windows = application.windows.filterIsInstance<UIWindow>()
    var controller = windows.firstOrNull { it.isKeyWindow() }?.rootViewController
        ?: windows.firstOrNull()?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

internal fun presentIosAlert(
    title: String,
    message: String,
    buttonLabel: String = "OK"
) {
    dispatch_async(dispatch_get_main_queue()) {
        val controller = findIosTopViewController() ?: run {
            Logger.w("IosUiFeedback", "Cannot present alert: top view controller is unavailable")
            return@dispatch_async
        }
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = buttonLabel,
                style = UIAlertActionStyleDefault,
                handler = null
            )
        )
        runCatching {
            controller.presentViewController(alert, animated = true, completion = null)
        }.onFailure { error ->
            Logger.e("IosUiFeedback", "Failed to present alert", error)
        }
    }
}

internal fun presentIosTwoOptionAlert(
    title: String,
    message: String,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    cancelLabel: String = "キャンセル"
): Boolean {
    val controller = findIosTopViewController() ?: run {
        Logger.w("IosUiFeedback", "Cannot present choice alert: top view controller is unavailable")
        return false
    }
    dispatch_async(dispatch_get_main_queue()) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = primaryLabel,
                style = UIAlertActionStyleDefault,
                handler = { _ -> onPrimary() }
            )
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = secondaryLabel,
                style = UIAlertActionStyleDefault,
                handler = { _ -> onSecondary() }
            )
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = cancelLabel,
                style = UIAlertActionStyleCancel,
                handler = null
            )
        )
        runCatching {
            controller.presentViewController(alert, animated = true, completion = null)
        }.onFailure { error ->
            Logger.e("IosUiFeedback", "Failed to present choice alert", error)
        }
    }
    return true
}
