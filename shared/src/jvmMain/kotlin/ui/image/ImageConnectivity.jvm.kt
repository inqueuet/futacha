@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.image

import coil3.PlatformContext
import coil3.network.ConnectivityChecker

internal actual fun createImageConnectivityChecker(platformContext: PlatformContext): ConnectivityChecker =
    ConnectivityChecker.ONLINE
