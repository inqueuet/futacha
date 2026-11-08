package com.valoser.futacha.shared.ui.futaber

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
internal actual fun ApplyFutaberHostBackground(color: Color) = Unit

@Composable
@Suppress("DEPRECATION")
internal actual fun ApplyFutaberNavigationBar(color: Color, darkIcons: Boolean, statusColor: Color) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findFutaberActivity()?.window ?: return@SideEffect
        window.navigationBarColor = color.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = darkIcons
    }
}

private tailrec fun Context.findFutaberActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findFutaberActivity()
    else -> null
}
