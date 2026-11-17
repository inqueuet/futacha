package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A short tick for the moment a long press is recognised (the thread's post sheet, the catalog and
 * tab sheets, the panel button, the viewer's share). It confirms the press; what happens next is
 * unchanged. The platform's own setting for touch feedback decides whether it is felt.
 */
@Composable
internal fun rememberFutaberLongPressHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.LongPress) } }
}
