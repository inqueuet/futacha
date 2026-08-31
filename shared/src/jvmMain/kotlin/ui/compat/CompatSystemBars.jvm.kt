package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal actual fun ApplyCompatSystemBars(
    statusBarColor: Color,
    navigationBarColor: Color,
    useDarkStatusBarIcons: Boolean,
    useDarkNavigationBarIcons: Boolean
) = Unit
