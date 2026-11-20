package com.valoser.futacha.shared.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Recognizes unmetered Mac hardware ports. Unknown connections are excluded from automatic work. */
internal object DesktopNetworkState {
    @Volatile var unmetered = false
        private set
    @Volatile private var refreshedAtNanos: Long? = null

    /** Refreshes only when the last probe is older than [maxAgeMillis]. */
    suspend fun refreshIfStale(maxAgeMillis: Long) {
        val last = refreshedAtNanos
        if (last == null || System.nanoTime() - last > maxAgeMillis * 1_000_000) refresh()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        unmetered = runCatching {
            if (DesktopPlatform.isWindows) return@runCatching false
            val device = desktopDefaultRouteInterface(query("/sbin/route", "-n", "get", "default").orEmpty())
                ?: return@runCatching false
            val port = desktopHardwarePortName(query("/usr/sbin/networksetup", "-listallhardwareports").orEmpty(), device)
            port != null && !isMeteredDesktopHardwarePort(port)
        }.getOrDefault(false)
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
