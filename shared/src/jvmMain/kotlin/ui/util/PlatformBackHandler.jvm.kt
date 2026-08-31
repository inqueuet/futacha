package com.valoser.futacha.shared.ui.util

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    iosEdgeGestureEnabled: Boolean,
    onBack: () -> Unit
) = Unit
