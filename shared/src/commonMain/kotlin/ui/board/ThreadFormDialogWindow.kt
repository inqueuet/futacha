package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.window.DialogProperties

/**
 * The reply form's window. On Android it draws under the system bars and is not panned by the keyboard: a panned
 * window plus the form's own keyboard padding moved the form twice, and the comment's first lines went off the top
 * of the screen (may 1449257110). The top bar then reaches under the status bar and the form pads the navigation bar ([threadFormPadsSystemBars]).
 */
internal expect fun threadFormDialogProperties(): DialogProperties

internal expect val threadFormPadsSystemBars: Boolean
