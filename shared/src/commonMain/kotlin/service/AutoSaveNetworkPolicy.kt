package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.ui.compat.isCompatWifiConnected
import kotlin.concurrent.Volatile

/**
 * Whether history auto-saves may download originals and videos on the current
 * network. Auto-saves run while the user browses a thread and during background
 * history refreshes, and a thread can hold up to 1,200 media items; on a metered
 * connection they store the text and thumbnails only (originals already saved
 * earlier are kept). The check is the one the patrol's "Wi-Fi only" option uses:
 * Android's NOT_METERED capability, iOS's path monitor, the desktop state.
 */
object AutoSaveNetworkPolicy {
    /** Installed by the app host with its application context; lives for the process. */
    @Volatile
    private var hostProbe: (() -> Boolean)? = null

    /** Installed while an image loader (and thus a UI context) is alive. */
    @Volatile
    private var uiProbe: (() -> Boolean)? = null

    /**
     * Uses [platformContext] (the Android application context) for every later
     * check. Hosts call this at startup so background runs without a UI can tell
     * Wi-Fi from mobile data.
     */
    fun install(platformContext: Any) {
        hostProbe = { isCompatWifiConnected(platformContext) }
    }

    internal fun installForUi(platformContext: Any?): AutoCloseable {
        val probe: () -> Boolean = { isCompatWifiConnected(platformContext) }
        uiProbe = probe
        return AutoCloseable { if (uiProbe === probe) uiProbe = null }
    }

    /**
     * True on an unmetered network. Without an installed context iOS and desktop
     * still answer correctly; Android then reports metered, so a cold background
     * run saves text and thumbnails until the host installs its context.
     */
    fun allowsFullMediaDownloads(): Boolean {
        val probe = uiProbe ?: hostProbe ?: { isCompatWifiConnected(null) }
        return runCatching(probe).getOrDefault(false)
    }
}
