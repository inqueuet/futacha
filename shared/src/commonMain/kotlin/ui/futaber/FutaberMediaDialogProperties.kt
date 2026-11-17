package com.valoser.futacha.shared.ui.futaber

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

/** The near black of the picture list's and the viewer's bars. */
internal val FutaberMediaBarColor = Color(0xFF111111)

/**
 * The full-screen window of the picture list and the viewer. On iPhone the status bar stays outside the window and shows
 * what is behind it: the default dimming left the thread there as a grey band, so it is dimmed to the bars' own near
 * black, as the original's status bar is. The other platforms keep their default dialog.
 */
internal expect fun futaberMediaDialogProperties(): DialogProperties

/** True where the status bar is that near black, with white glyphs, while the picture list or the viewer is up (iPhone). */
internal expect val futaberMediaDarkensStatusBar: Boolean
