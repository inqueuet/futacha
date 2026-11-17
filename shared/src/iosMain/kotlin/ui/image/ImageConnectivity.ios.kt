@file:OptIn(coil3.annotation.ExperimentalCoilApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.ui.image

import coil3.PlatformContext
import coil3.network.ConnectivityChecker
import kotlin.concurrent.AtomicInt
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_unsatisfied
import platform.darwin.dispatch_queue_create

/**
 * Coil reports iOS as always online. One process-lifetime path monitor answers instead;
 * until its first update arrives the device counts as online (the previous behaviour).
 */
private object IosImageConnectivity : ConnectivityChecker {
    private val offline = AtomicInt(0)
    // Held for the process lifetime; a released monitor stops delivering updates.
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            offline.value = if (nw_path_get_status(path) == nw_path_status_unsatisfied) 1 else 0
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.valoser.futacha.image-connectivity", null))
        nw_path_monitor_start(monitor)
    }

    override fun isOnline(): Boolean = offline.value == 0
}

internal actual fun createImageConnectivityChecker(platformContext: PlatformContext): ConnectivityChecker =
    IosImageConnectivity
