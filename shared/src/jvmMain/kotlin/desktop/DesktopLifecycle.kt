package com.valoser.futacha.shared.desktop

import kotlinx.coroutines.flow.MutableStateFlow

object DesktopLifecycle {
    /**
     * The desktop counterpart of an app in the foreground: the window is shown and
     * neither minimized nor hidden. Losing focus to another app or to an OS panel
     * (NSOpenPanel, the share picker) keeps it true.
     */
    val foreground = MutableStateFlow(true)
    /** A window of the app has keyboard focus. */
    val focused = MutableStateFlow(true)
    val activationRequests = MutableStateFlow(0L)
    /** Bumped when the OS brings the app to the front, e.g. after a notification click. */
    val appActivations = MutableStateFlow(0L)
}

fun desktopWindowInForeground(showing: Boolean, minimized: Boolean, appHidden: Boolean): Boolean =
    showing && !minimized && !appHidden
