package com.valoser.futacha.shared.ui.compat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
internal actual fun ApplyCompatViewerSystemBars(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, hidden) {
        val activity = view.context.findCompatViewerActivity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (hidden) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (hidden) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
private tailrec fun Context.findCompatViewerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findCompatViewerActivity()
    else -> null
}
