package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.time.Clock

internal const val THREAD_AUTO_SAVE_INITIAL_SETTLE_DELAY_MS = 30_000L
private const val THREAD_AUTO_SAVE_INITIAL_FRAME_COUNT = 2

/**
 * Defers only the first foreground auto-save until loaded content has had a
 * chance to render. Interval saves retain the existing 60-second schedule.
 *
 * [windowStartedAtMillis] is when the screen first became ready to save. The
 * effect restarts whenever the page changes (refresh, archive supplement,
 * automatic scrolling); measuring from that fixed start means a restart waits
 * only for the remainder instead of postponing the first save indefinitely.
 */
internal suspend fun awaitThreadAutoSaveStartupWindow(
    previousTimestampMillis: Long,
    windowStartedAtMillis: Long? = null,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    awaitFrame: suspend () -> Unit = { withFrameNanos { } },
    pause: suspend (Long) -> Unit = { delay(it) }
) {
    if (previousTimestampMillis != 0L) return

    repeat(THREAD_AUTO_SAVE_INITIAL_FRAME_COUNT) {
        awaitFrame()
    }
    val elapsed = windowStartedAtMillis?.let { (nowMillis() - it).coerceAtLeast(0L) } ?: 0L
    val remaining = THREAD_AUTO_SAVE_INITIAL_SETTLE_DELAY_MS - elapsed
    if (remaining > 0L) pause(remaining)
}

/** Remembers the start of the initial auto-save window across effect restarts. */
internal class ThreadAutoSaveStartupWindowHolder {
    var startedAtMillis: Long? = null
}
