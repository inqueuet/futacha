package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable

/** The desktop has no screen-sleep control here. */
@Composable
internal actual fun PlatformKeepScreenOnEffect(enabled: Boolean) = Unit
