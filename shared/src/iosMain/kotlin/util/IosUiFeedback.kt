package com.valoser.futacha.shared.util

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.AtomicInt
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal fun findIosTopViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val windows = buildList {
        application.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .forEach { scene ->
                if (scene.activationState == 0L || scene.activationState == 1L) {
                    addAll(scene.windows.filterIsInstance<UIWindow>())
                }
            }
        if (isEmpty()) {
            addAll(application.windows.filterIsInstance<UIWindow>())
        }
    }
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

/** Keeps source selection, cancellation and the following picker in one coroutine. */
internal suspend fun awaitIosTwoOptionChoice(
    title: String,
    message: String,
    primaryLabel: String,
    secondaryLabel: String,
    cancelLabel: String = "キャンセル"
): Boolean? = suspendCancellableCoroutine { continuation ->
    val completed = AtomicInt(0)
    val alert = UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
    fun finish(value: Boolean?) {
        if (!completed.compareAndSet(0, 1)) return
        // Do not present the next sheet while this alert is still in UIKit's chain.
        alert.dismissViewControllerAnimated(true) { continuation.resume(value) }
    }
    alert.addAction(UIAlertAction.actionWithTitle(primaryLabel, UIAlertActionStyleDefault) { finish(true) })
    alert.addAction(UIAlertAction.actionWithTitle(secondaryLabel, UIAlertActionStyleDefault) { finish(false) })
    alert.addAction(UIAlertAction.actionWithTitle(cancelLabel, UIAlertActionStyleCancel) { finish(null) })
    continuation.invokeOnCancellation {
        completed.compareAndSet(0, 1)
        dispatch_async(dispatch_get_main_queue()) {
            if (alert.presentingViewController != null) alert.dismissViewControllerAnimated(true, null)
        }
    }
    dispatch_async(dispatch_get_main_queue()) {
        if (!continuation.isActive) return@dispatch_async
        presentPicker(alert, "attachment source chooser") {
            if (completed.compareAndSet(0, 1)) {
                continuation.resumeWithException(IllegalStateException("Cannot present attachment source chooser"))
            }
        }
    }
}
