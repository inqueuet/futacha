package com.valoser.futacha.shared.ui.compat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

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

@Composable
internal actual fun CompatSystemBarBackgrounds(
    statusBarColor: Color,
    navigationBarColor: Color,
    modifier: Modifier
) {
    val view = LocalView.current
    val statusInsets = WindowInsets.statusBars
    val navigationInsets = WindowInsets.navigationBars
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(modifier.onGloballyPositioned { origin = it.positionInWindow() }) {
        // Observe visibility/inset changes, but paint the native bar bounds:
        // Compose's safe inset can also include a taller display cutout.
        statusInsets.getTop(this)
        navigationInsets.getBottom(this)
        val insets = ViewCompat.getRootWindowInsets(view) ?: return@Canvas
        val statusHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top.toFloat()
        val navigationHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom.toFloat()
        // Window coordinates also keep legacy, non-edge-to-edge hosts from
        // painting an extra strip inside the application content.
        drawRect(statusBarColor, Offset(0f, -origin.y), Size(size.width, statusHeight))
        drawRect(
            navigationBarColor,
            Offset(0f, view.rootView.height - navigationHeight - origin.y),
            Size(size.width, navigationHeight)
        )
    }
}

private tailrec fun Context.findCompatActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findCompatActivity()
    else -> null
}
