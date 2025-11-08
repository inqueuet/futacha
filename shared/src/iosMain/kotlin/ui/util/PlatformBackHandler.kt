package com.valoser.futacha.shared.ui.util

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    // No system back gesture to intercept on iOS yet.
}
