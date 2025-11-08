package com.valoser.futacha.shared.ui.util

import androidx.compose.runtime.Composable

/**
 * Cross-platform hook for reacting to system back gestures/buttons.
 * Android provides a real implementation; other targets can be no-ops.
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
)
