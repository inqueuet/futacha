package com.valoser.futacha.shared.ui.compat

internal actual fun isCompatWifiConnected(platformContext: Any?): Boolean =
    if (com.valoser.futacha.shared.desktop.DesktopEnvironment.current == null) true
    else com.valoser.futacha.shared.desktop.DesktopNetworkState.wifi
