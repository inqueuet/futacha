package com.valoser.futacha.shared.ui.media

import androidx.compose.ui.window.DialogProperties

/** Fullscreen editors handle system bars with safeDrawingPadding inside the dialog. */
internal expect fun mediaEditorDialogProperties(): DialogProperties
