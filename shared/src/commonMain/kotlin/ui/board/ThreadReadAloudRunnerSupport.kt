package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ThreadReadAloudRunnerCallbacks(
    val onSegmentStart: (ReadAloudSegment, Int) -> Unit = { _, _ -> },
    val scrollToSegment: suspend (ReadAloudSegment) -> Unit = {},
    val speakSegment: suspend (ReadAloudSegment) -> Unit,
    val onFailure: suspend (Throwable) -> Unit = {}
)

internal data class ThreadReadAloudRunResult(
    val completedNormally: Boolean,
    val nextIndex: Int
)

internal data class ThreadReadAloudFinalState(
    val status: ReadAloudStatus,
    val message: String? = null
)

internal fun buildThreadReadAloudRunnerCallbacks(
    onSegmentStart: (ReadAloudSegment, Int) -> Unit,
    scrollToPostIndex: suspend (Int) -> Unit,
    speakText: suspend (String) -> Unit,
    onFailure: suspend (Throwable) -> Unit
): ThreadReadAloudRunnerCallbacks {
    return ThreadReadAloudRunnerCallbacks(
        onSegmentStart = onSegmentStart,
        scrollToSegment = { segment ->
            scrollToPostIndex(segment.postIndex)
        },
        speakSegment = { segment ->
            speakText(segment.body)
        },
        onFailure = onFailure
    )
}

internal fun resolveThreadReadAloudFinalState(
    completedNormally: Boolean,
    currentStatus: ReadAloudStatus
): ThreadReadAloudFinalState {
    return when {
        completedNormally -> ThreadReadAloudFinalState(
            status = ReadAloudStatus.Idle,
            message = buildReadAloudCompletedMessage()
        )
        currentStatus is ReadAloudStatus.Paused -> ThreadReadAloudFinalState(status = currentStatus)
        else -> ThreadReadAloudFinalState(status = ReadAloudStatus.Idle)
    }
}

internal suspend fun runThreadReadAloudSession(
    startIndex: Int,
    segments: List<ReadAloudSegment>,
    isRunnerActive: () -> Boolean,
    wasCancelledByUser: () -> Boolean,
    callbacks: ThreadReadAloudRunnerCallbacks,
    /**
     * The segments as they are now, read again after each one is spoken (re-audit L39: the list of the start was read to
     * the end, posts hidden by NG since were read, and posts that came in were not). Null keeps [segments].
     */
    latestSegments: (() -> List<ReadAloudSegment>)? = null
): ThreadReadAloudRunResult {
    var index = startIndex.coerceAtLeast(0)
    var segments = segments
    try {
        while (index < segments.size && isRunnerActive()) {
            val segment = segments[index]
            callbacks.onSegmentStart(segment, index)
            try {
                callbacks.scrollToSegment(segment)
            } catch (error: CancellationException) {
                // A touch on the list interrupts the scroll animation with a
                // CancellationException while this coroutine stays active;
                // keep reading instead of ending the session silently.
                currentCoroutineContext().ensureActive()
                if (error is TimeoutCancellationException) throw error
            }
            callbacks.speakSegment(segment)
            val refreshed = latestSegments?.invoke()
            if (refreshed == null || refreshed === segments) {
                index += 1
            } else {
                index = nextReadAloudIndexAfter(segments, index, refreshed)
                segments = refreshed
            }
        }
        return if (index >= segments.size && isRunnerActive()) {
            ThreadReadAloudRunResult(
                completedNormally = true,
                nextIndex = 0
            )
        } else {
            ThreadReadAloudRunResult(
                completedNormally = false,
                nextIndex = index
            )
        }
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        callbacks.onFailure(error)
        return ThreadReadAloudRunResult(
            completedNormally = false,
            nextIndex = index
        )
    } catch (error: CancellationException) {
        if (!wasCancelledByUser()) {
            throw error
        }
        currentCoroutineContext().ensureActive()
        return ThreadReadAloudRunResult(
            completedNormally = false,
            nextIndex = index
        )
    } catch (error: Exception) {
        callbacks.onFailure(error)
        return ThreadReadAloudRunResult(
            completedNormally = false,
            nextIndex = index
        )
    }
}

/**
 * Where reading goes on in [refreshed] after [old]`[spoken]` was read: right after it when it is still there; otherwise at
 * the first of the segments that were still to come; otherwise at the first new one; otherwise the end.
 */
internal fun nextReadAloudIndexAfter(old: List<ReadAloudSegment>, spoken: Int, refreshed: List<ReadAloudSegment>): Int {
    val same = refreshed.indexOf(old[spoken])
    if (same >= 0) return same + 1
    val ahead = old.drop(spoken + 1).toHashSet()
    refreshed.indexOfFirst { it in ahead }.takeIf { it >= 0 }?.let { return it }
    val known = old.toHashSet()
    refreshed.indexOfFirst { it !in known }.takeIf { it >= 0 }?.let { return it }
    return refreshed.size
}
