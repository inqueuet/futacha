package com.valoser.futacha.shared.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal object DesktopNetworkState {
    @Volatile var wifi = false
        private set
    suspend fun refresh() = withContext(Dispatchers.IO) {
        fun query(vararg args: String): String? {
            val process = ProcessBuilder("/usr/sbin/networksetup", *args).redirectErrorStream(true).apply { environment()["LC_ALL"] = "C" }.start()
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) return null
                return process.inputStream.bufferedReader().readText().takeIf { process.exitValue() == 0 }
            } finally { process.destroyForcibly() }
        }
        wifi = runCatching {
            if (DesktopPlatform.isWindows) return@runCatching windowsWifiConnected()
            val ports = query("-listallhardwareports").orEmpty()
            val device = ports.split("\n\n").firstOrNull { "Hardware Port: Wi-Fi" in it }
                ?.lineSequence()?.firstOrNull { it.startsWith("Device: ") }?.substringAfter("Device: ")?.trim()
            device != null && query("-getairportnetwork", device).orEmpty().startsWith("Current Wi-Fi Network:")
        }.getOrDefault(false)
    }
}
