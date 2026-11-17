package com.valoser.futacha.shared.ui.compat

/** Desktop connections are unmetered unless detected as tethered/cellular (see DesktopNetworkState). */
internal actual fun isCompatWifiConnected(platformContext: Any?): Boolean =
    if (com.valoser.futacha.shared.desktop.DesktopEnvironment.current == null) true
    else com.valoser.futacha.shared.desktop.DesktopNetworkState.unmetered
