package com.valoser.futacha.shared.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Desktop connections count as unmetered (the "Wi-Fi only" settings allow them)
 * unless the default route positively goes through a tethered phone, modem or
 * cellular port. Wired LAN, Wi-Fi and VPNs are unmetered; so is an unknown
 * state, which also covers the time before the first probe. Windows has no
 * cheap metered-connection query here and is treated as unmetered.
 */
internal object DesktopNetworkState {
    @Volatile var unmetered = true
        private set
    @Volatile private var refreshedAtNanos: Long? = null

    /** Refreshes only when the last probe is older than [maxAgeMillis]. */
    suspend fun refreshIfStale(maxAgeMillis: Long) {
        val last = refreshedAtNanos
        if (last == null || System.nanoTime() - last > maxAgeMillis * 1_000_000) refresh()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        unmetered = runCatching {
            if (DesktopPlatform.isWindows) return@runCatching true
            val device = desktopDefaultRouteInterface(query("/sbin/route", "-n", "get", "default").orEmpty())
                ?: return@runCatching true
            val port = desktopHardwarePortName(query("/usr/sbin/networksetup", "-listallhardwareports").orEmpty(), device)
            !isMeteredDesktopHardwarePort(port)
        }.getOrDefault(true)
        refreshedAtNanos = System.nanoTime()
    }

    private fun query(vararg command: String): String? {
        val process = ProcessBuilder(*command).redirectErrorStream(true).apply { environment()["LC_ALL"] = "C" }.start()
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) return null
            return process.inputStream.bufferedReader().readText().takeIf { process.exitValue() == 0 }
        } finally { process.destroyForcibly() }
    }
}

/** The `interface:` line of `route -n get default`, or null without a default route. */
internal fun desktopDefaultRouteInterface(routeOutput: String): String? =
    routeOutput.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("interface:") }
        ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

/** The `Hardware Port:` name of [device] in `networksetup -listallhardwareports`. */
internal fun desktopHardwarePortName(ports: String, device: String): String? =
    ports.split(Regex("\\n\\s*\\n")).firstOrNull { block ->
        block.lineSequence().any { it.trim() == "Device: $device" }
    }?.lineSequence()?.map { it.trim() }?.firstOrNull { it.startsWith("Hardware Port:") }
        ?.substringAfter(':')?.trim()

internal fun isMeteredDesktopHardwarePort(name: String?): Boolean {
    val lower = name?.lowercase() ?: return false
    return METERED_PORT_MARKERS.any { it in lower }
}

private val METERED_PORT_MARKERS = listOf("iphone", "ipad", "bluetooth pan", "modem", "wwan", "cellular", "tether", "android", "rndis")
