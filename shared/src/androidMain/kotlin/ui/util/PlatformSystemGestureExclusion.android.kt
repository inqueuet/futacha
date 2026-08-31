package com.valoser.futacha.shared.ui.util

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier

internal actual fun Modifier.platformSystemGestureExclusion(): Modifier = systemGestureExclusion()
