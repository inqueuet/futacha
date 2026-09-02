package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

internal const val THREAD_AUTO_SAVE_INITIAL_SETTLE_DELAY_MS = 1_000L
private const val THREAD_AUTO_SAVE_INITIAL_FRAME_COUNT = 2

/**
 * Defers only the first foreground auto-save until loaded content has had a
 * chance to render. Interval saves retain the existing 60-second schedule.
 */
internal suspend fun awaitThreadAutoSaveStartupWindow(
    previousTimestampMillis: Long,
    awaitFrame: suspend () -> Unit = { withFrameNanos { } },
    pause: suspend (Long) -> Unit = { delay(it) }
) {
    if (previousTimestampMillis != 0L) return

    repeat(THREAD_AUTO_SAVE_INITIAL_FRAME_COUNT) {
        awaitFrame()
    }
    pause(THREAD_AUTO_SAVE_INITIAL_SETTLE_DELAY_MS)
}
