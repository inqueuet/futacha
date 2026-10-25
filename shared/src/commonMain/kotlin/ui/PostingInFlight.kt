package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.first

/**
 * The posts (replies, new threads) being sent in the app. A link, a notification or an AI command that would move away
 * from the screen waits until none is being sent (L30: moving away cancelled the send, which may already have reached
 * the server, so sending again could post twice).
 */
@Stable
internal class PostingInFlight {
    var count by mutableIntStateOf(0)
        private set

    fun begin() {
        count += 1
    }

    fun end() {
        if (count > 0) count -= 1
    }

    /** Returns once nothing is being sent. */
    suspend fun awaitIdle() {
        snapshotFlow { count }.first { it == 0 }
    }
}

internal val LocalPostingInFlight = staticCompositionLocalOf<PostingInFlight?> { null }

/** Counts this screen's send in [LocalPostingInFlight] while [sending] is true. */
@Composable
internal fun ReportPostingInFlight(sending: Boolean) {
    val tracker = LocalPostingInFlight.current ?: return
    DisposableEffect(tracker, sending) {
        if (sending) tracker.begin()
        onDispose { if (sending) tracker.end() }
    }
}
