package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.CancellationException

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
    callbacks: ThreadReadAloudRunnerCallbacks
): ThreadReadAloudRunResult {
    var index = startIndex.coerceAtLeast(0)
    try {
        while (index < segments.size && isRunnerActive()) {
            val segment = segments[index]
            callbacks.onSegmentStart(segment, index)
            callbacks.scrollToSegment(segment)
            callbacks.speakSegment(segment)
            index += 1
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
    } catch (error: CancellationException) {
        if (!wasCancelledByUser()) {
            throw error
        }
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
