package com.valoser.futacha.shared.ui.compat

internal actual fun isCompatWifiConnected(platformContext: Any?): Boolean =
    IosCompatNetworkStateBridge.isWifiConnected()
