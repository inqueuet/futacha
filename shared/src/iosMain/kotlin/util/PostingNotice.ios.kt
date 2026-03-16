package com.valoser.futacha.shared.util

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

actual suspend fun confirmPostingNotice(): Boolean = suspendCancellableCoroutine { continuation ->
    dispatch_async(dispatch_get_main_queue()) {
        val controller = findIosTopViewController()
        if (controller == null) {
            Logger.w("PostingNotice", "Cannot present posting notice: top view controller is unavailable")
            continuation.resume(false)
            return@dispatch_async
        }
        val alert = UIAlertController.alertControllerWithTitle(
            title = "投稿前の確認",
            message = "投稿内容は公開されます。利用規約と板のルールを確認したうえで送信してください。",
            preferredStyle = UIAlertControllerStyleAlert
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = "キャンセル",
                style = UIAlertActionStyleCancel
            ) { _ ->
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = "同意して送信",
                style = UIAlertActionStyleDefault
            ) { _ ->
                if (continuation.isActive) {
                    continuation.resume(true)
                }
            }
        )
        runCatching {
            controller.presentViewController(alert, animated = true, completion = null)
        }.onFailure { error ->
            Logger.e("PostingNotice", "Failed to present posting notice", error)
            if (continuation.isActive) {
                continuation.resume(false)
            }
        }
    }
}
