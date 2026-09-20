package com.valoser.futacha.shared.desktop

import kotlinx.coroutines.flow.MutableStateFlow

object DesktopLifecycle {
    val foreground = MutableStateFlow(true)
    val activationRequests = MutableStateFlow(0L)
}
