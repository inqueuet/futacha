package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal expect fun ApplyCompatSystemBars(
    statusBarColor: Color,
    navigationBarColor: Color,
    useDarkStatusBarIcons: Boolean,
    useDarkNavigationBarIcons: Boolean
)

@Composable
internal expect fun CompatSystemBarBackgrounds(
    statusBarColor: Color,
    navigationBarColor: Color,
    modifier: Modifier
)
