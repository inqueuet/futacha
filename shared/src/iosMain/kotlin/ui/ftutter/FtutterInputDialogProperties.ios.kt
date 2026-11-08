package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.window.DialogProperties

internal actual fun ftutterInputDialogProperties() = DialogProperties()

internal actual fun ftutterSheetDialogProperties() = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
