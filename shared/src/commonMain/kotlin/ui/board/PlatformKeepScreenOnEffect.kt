package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable

/** Keeps the display awake while [enabled] (FutabaViewer 18-20: the screen stays on while a video plays). */
@Composable
internal expect fun PlatformKeepScreenOnEffect(enabled: Boolean)

/**
 * The OS keeps one "screen on" flag, so overlapping effects share it: the flag goes on with the first
 * holder and off only when the last one lets go, in whatever order they are disposed. Used on the main thread.
 */
internal class KeepScreenOnCounter(private val apply: (Boolean) -> Unit) {
    var holders: Int = 0
        private set

    fun acquire() {
        holders += 1
        if (holders == 1) apply(true)
    }

    fun release() {
        if (holders == 0) return
        holders -= 1
        if (holders == 0) apply(false)
    }
}
