package com.valoser.futacha.shared.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [block] with a time limit that counts only while [isPaused] is false (L117): a reading paused for the audio focus
 * (another app's short interruption, or this app's own video) does not run out of time while it waits. Past the
 * limit, [block] is cancelled and [onTimeout]'s exception is thrown.
 */
internal suspend fun <T> withPauseAwareTimeout(
    limitMillis: Long,
    isPaused: () -> Boolean,
    onTimeout: () -> Throwable,
    tickMillis: Long = 250L,
    block: suspend CoroutineScope.() -> T
): T = coroutineScope {
    val work = async { block() }
    var counted = 0L
    while (withTimeoutOrNull(tickMillis) { work.join() } == null) {
        if (!isPaused()) counted += tickMillis
        if (counted >= limitMillis) {
            work.cancel()
            throw onTimeout()
        }
    }
    work.await()
}
