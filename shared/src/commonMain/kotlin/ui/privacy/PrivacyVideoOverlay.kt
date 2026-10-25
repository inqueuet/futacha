package com.valoser.futacha.shared.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Native video surfaces do not reliably inherit a Compose alpha layer. Draw above the video and
 * its poster, before the host's controls. The native player's built-in controls are also covered;
 * the host's separate buttons and error text stay outside this veil.
 *
 * A mode's legacy media opacity is independent of modern privacy. Keep that opacity while the
 * modern image strength contributes only in IMAGES scope; SCREEN already draws a window filter.
 */
@Composable
internal fun PrivacyVideoOverlay(legacyContentAlpha: Float = 1f) {
    val modernContentAlpha = privacyImageContentAlpha(1f)
    val alpha = 1f - legacyContentAlpha.coerceIn(0f, 1f) * modernContentAlpha.coerceIn(0f, 1f)
    if (alpha > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
}
