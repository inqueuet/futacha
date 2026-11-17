package com.valoser.futacha.shared.ui.compat

import platform.Foundation.NSLock

/** Receives Network.framework updates from the Swift application host. */
object IosCompatNetworkStateBridge {
    private val lock = NSLock()
    private var wifiConnected = false

    fun updateWifiConnected(connected: Boolean) {
        lock.lock()
        try {
            wifiConnected = connected
        } finally {
            lock.unlock()
        }
    }

    fun isWifiConnected(): Boolean {
        lock.lock()
        return try {
            wifiConnected
        } finally {
            lock.unlock()
        }
    }
}
