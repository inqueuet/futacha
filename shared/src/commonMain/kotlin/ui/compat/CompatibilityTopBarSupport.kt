package com.valoser.futacha.shared.ui.compat

internal fun shouldShowCompatExplicitNavigationBack(
    isAndroidPlatform: Boolean,
    isDrawerOpen: Boolean
): Boolean = !isAndroidPlatform && !isDrawerOpen
