package com.valoser.futacha.shared.ui.compat

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock

private const val COMPAT_NETWORK_STATE_CACHE_MILLIS = 1_000L
private var lastCompatNetworkCheckAt = Long.MIN_VALUE
private var lastCompatWifiConnected = false

internal actual fun isCompatWifiConnected(platformContext: Any?): Boolean {
    val now = SystemClock.elapsedRealtime()
    val cachedAge = now - lastCompatNetworkCheckAt
    if (cachedAge >= 0L && cachedAge < COMPAT_NETWORK_STATE_CACHE_MILLIS) {
        return lastCompatWifiConnected
    }
    val context = platformContext as? Context ?: return false
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork
    val capabilities = network?.let(manager::getNetworkCapabilities)
    // The reference keeps the historical "Wi-Fi" label but now evaluates
    // Android's non-metered capability, which also permits Ethernet and a
    // user-marked unmetered network.
    val connected = capabilities != null &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    lastCompatWifiConnected = connected
    lastCompatNetworkCheckAt = now
    return connected
}
