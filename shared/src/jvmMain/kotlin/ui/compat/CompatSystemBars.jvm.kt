package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal actual fun ApplyCompatSystemBars(
    statusBarColor: Color,
    navigationBarColor: Color,
    useDarkStatusBarIcons: Boolean,
    useDarkNavigationBarIcons: Boolean
) = Unit

@Composable
internal actual fun CompatSystemBarBackgrounds(
    statusBarColor: Color,
    navigationBarColor: Color,
    modifier: Modifier
) = Unit
