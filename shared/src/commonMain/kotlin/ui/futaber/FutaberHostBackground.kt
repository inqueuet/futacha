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

/**
 * Android: the navigation bar at the bottom takes the mode's bar colour and its glyphs follow that colour, which can
 * differ from the status bar's (ふたば: a red top, a white bottom bar). Called after [com.valoser.futacha.shared.ui.theme.ApplyFutachaSystemBars],
 * whose navigation-bar settings it overrides; the shared function and the other modes are left as they are. It does
 * nothing on the other platforms (iPhone's bottom strip is [ApplyFutaberHostBackground]).
 */
@Composable
internal expect fun ApplyFutaberNavigationBar(
    color: Color,
    darkIcons: Boolean,
    /** The colour just given to the shared function: when it changes, that function sets the bar again, so this does too. */
    statusColor: Color
)
