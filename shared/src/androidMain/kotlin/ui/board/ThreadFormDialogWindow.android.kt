package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.window.DialogProperties

internal actual fun threadFormDialogProperties() = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

internal actual val threadFormPadsSystemBars: Boolean = true
