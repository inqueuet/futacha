package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * On iPhone the strip at the bottom of the screen (the home-indicator area) belongs to the host
 * view, not to Compose, and shows the system background. This gives that area the mode's bar
 * colour while the ふたばー screens are shown, and releases it when they leave, so the other
 * modes keep the host as it was. It does nothing on the other platforms.
 */
@Composable
internal expect fun ApplyFutaberHostBackground(color: Color)
