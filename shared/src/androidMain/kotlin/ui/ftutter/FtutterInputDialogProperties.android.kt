package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.window.DialogProperties

internal actual fun ftutterInputDialogProperties() = DialogProperties(decorFitsSystemWindows = false)

internal actual fun ftutterSheetDialogProperties() = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)
