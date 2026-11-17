package com.valoser.futacha.shared.ui.media

import androidx.compose.ui.window.DialogProperties

internal actual fun mediaEditorDialogProperties() = DialogProperties(
    usePlatformDefaultWidth = false,
    // Let safeDrawingPadding receive the system-bar insets in fullscreen dialogs.
    decorFitsSystemWindows = false
)
