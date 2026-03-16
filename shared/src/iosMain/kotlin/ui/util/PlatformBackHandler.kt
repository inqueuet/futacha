@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package com.valoser.futacha.shared.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.util.findIosTopViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIView
import platform.Foundation.NSSelectorFromString
import platform.darwin.NSObject
import kotlinx.cinterop.ObjCAction

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val handler = remember {
        BackGestureHandler().apply {
            this.onBack = { currentOnBack() }
        }
    }

    DisposableEffect(enabled, handler) {
        handler.onBack = { currentOnBack() }
        val view = resolveRootView()
        if (view == null || !enabled) {
            onDispose { }
        } else {
            val gesture = UIScreenEdgePanGestureRecognizer(
                target = handler,
                action = NSSelectorFromString("handleBackGesture:")
            ).apply {
                edges = UIRectEdgeLeft
            }
            view.addGestureRecognizer(gesture)
            onDispose {
                view.removeGestureRecognizer(gesture)
            }
        }
    }
}

private fun resolveRootView(): UIView? {
    return findIosTopViewController()?.view
}

private class BackGestureHandler : NSObject() {
    var onBack: (() -> Unit)? = null

    @ObjCAction
    fun handleBackGesture(recognizer: UIScreenEdgePanGestureRecognizer) {
        if (recognizer.state == UIGestureRecognizerStateEnded) {
            onBack?.invoke()
        }
    }
}
