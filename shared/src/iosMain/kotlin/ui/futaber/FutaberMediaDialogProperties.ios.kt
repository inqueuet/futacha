package com.valoser.futacha.shared.ui.futaber

import androidx.compose.ui.window.DialogProperties

internal actual fun futaberMediaDialogProperties() = DialogProperties(usePlatformDefaultWidth = false, scrimColor = FutaberMediaBarColor)

internal actual val futaberMediaDarkensStatusBar: Boolean = true
