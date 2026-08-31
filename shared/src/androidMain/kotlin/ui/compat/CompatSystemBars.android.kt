package com.valoser.futacha.shared.ui.compat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
@Suppress("DEPRECATION")
internal actual fun ApplyCompatSystemBars(
    statusBarColor: Color,
    navigationBarColor: Color,
    useDarkStatusBarIcons: Boolean,
    useDarkNavigationBarIcons: Boolean
) {
    val view = LocalView.current
    SideEffect {
        val activity = view.context.findCompatActivity() ?: return@SideEffect
        val window = activity.window
        window.statusBarColor = statusBarColor.toArgb()
        window.navigationBarColor = navigationBarColor.toArgb()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = useDarkStatusBarIcons
            isAppearanceLightNavigationBars = useDarkNavigationBarIcons
        }
    }
}

private tailrec fun Context.findCompatActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findCompatActivity()
    else -> null
}
