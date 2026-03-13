package com.valoser.futacha.shared.ui.board

internal fun buildReadAloudNoTargetMessage(): String = "読み上げ対象がありません"

internal fun buildReadAloudPausedMessage(): String = "読み上げを一時停止しました"

internal fun buildReadAloudCompletedMessage(): String = "読み上げを完了しました"

internal fun buildReadAloudStoppedMessage(): String = "読み上げを停止しました"

internal fun buildReadAloudFailureMessage(error: Throwable): String {
    return "読み上げ中にエラーが発生しました: ${error.message ?: "不明なエラー"}"
}

internal data class ReadAloudStartState(
    val canStart: Boolean,
    val normalizedIndex: Int,
    val message: String? = null
)

internal fun resolveReadAloudStartState(
    segmentCount: Int,
    currentIndex: Int,
    isJobRunning: Boolean
): ReadAloudStartState {
    return when {
        segmentCount <= 0 -> ReadAloudStartState(
            canStart = false,
            normalizedIndex = 0,
            message = buildReadAloudNoTargetMessage()
        )
        isJobRunning -> ReadAloudStartState(
            canStart = false,
            normalizedIndex = currentIndex.coerceIn(0, segmentCount - 1)
        )
        else -> ReadAloudStartState(
            canStart = true,
            normalizedIndex = if (currentIndex >= segmentCount) 0 else currentIndex.coerceAtLeast(0)
        )
    }
}

internal data class ReadAloudPauseState(
    val status: ReadAloudStatus,
    val message: String
)

internal fun resolveReadAloudPauseState(status: ReadAloudStatus): ReadAloudPauseState? {
    val segment = (status as? ReadAloudStatus.Speaking)?.segment ?: return null
    return ReadAloudPauseState(
        status = ReadAloudStatus.Paused(segment),
        message = buildReadAloudPausedMessage()
    )
}

internal data class ReadAloudSeekState(
    val targetIndex: Int,
    val targetSegment: ReadAloudSegment?,
    val shouldRestart: Boolean
)

internal fun resolveReadAloudSeekState(
    segments: List<ReadAloudSegment>,
    status: ReadAloudStatus,
    targetIndex: Int
): ReadAloudSeekState? {
    if (segments.isEmpty()) return null
    val clampedIndex = targetIndex.coerceIn(0, segments.lastIndex.coerceAtLeast(0))
    return ReadAloudSeekState(
        targetIndex = clampedIndex,
        targetSegment = segments.getOrNull(clampedIndex),
        shouldRestart = status is ReadAloudStatus.Speaking || status is ReadAloudStatus.Paused
    )
}

internal data class ReadAloudControlState(
    val totalSegments: Int,
    val completedSegments: Int,
    val currentSegment: ReadAloudSegment?,
    val canSeek: Boolean,
    val sliderValue: Float,
    val visiblePostId: String?,
    val canSeekToVisible: Boolean,
    val playLabel: String,
    val isPlayEnabled: Boolean,
    val isPauseEnabled: Boolean,
    val isStopEnabled: Boolean
)

internal fun resolveReadAloudControlState(
    segments: List<ReadAloudSegment>,
    currentIndex: Int,
    visibleSegmentIndex: Int,
    status: ReadAloudStatus
): ReadAloudControlState {
    val totalSegments = segments.size
    val completedSegments = currentIndex.coerceIn(0, totalSegments)
    val currentSegment = when (status) {
        is ReadAloudStatus.Speaking -> status.segment
        is ReadAloudStatus.Paused -> status.segment
        ReadAloudStatus.Idle -> segments.getOrNull(currentIndex.coerceIn(0, (segments.lastIndex).coerceAtLeast(0)))
    }
    val sliderIndex = currentIndex.coerceIn(0, (segments.lastIndex).coerceAtLeast(0))
    val isPlaying = status is ReadAloudStatus.Speaking
    val isPaused = status is ReadAloudStatus.Paused
    val visiblePostId = segments.getOrNull(visibleSegmentIndex)?.postId
    return ReadAloudControlState(
        totalSegments = totalSegments,
        completedSegments = completedSegments,
        currentSegment = currentSegment,
        canSeek = segments.isNotEmpty(),
        sliderValue = sliderIndex.toFloat(),
        visiblePostId = visiblePostId,
        canSeekToVisible = visibleSegmentIndex in segments.indices && visiblePostId != null,
        playLabel = if (isPaused) "再開" else "再生",
        isPlayEnabled = totalSegments > 0 && !isPlaying,
        isPauseEnabled = isPlaying,
        isStopEnabled = status !is ReadAloudStatus.Idle
    )
}

internal data class ThreadReadAloudControlCallbacks(
    val onSeek: (Int) -> Unit,
    val onSeekToVisible: () -> Unit,
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onStop: () -> Unit,
    val onDismiss: () -> Unit
)

internal fun buildThreadReadAloudControlCallbacks(
    firstVisibleSegmentIndex: () -> Int,
    onSeekToIndex: (Int, Boolean) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onShowStoppedMessage: () -> Unit,
    onDismiss: () -> Unit
): ThreadReadAloudControlCallbacks {
    return ThreadReadAloudControlCallbacks(
        onSeek = { index ->
            onSeekToIndex(index, true)
        },
        onSeekToVisible = {
            val visibleIndex = firstVisibleSegmentIndex()
            if (visibleIndex >= 0) {
                onSeekToIndex(visibleIndex, true)
            }
        },
        onPlay = onPlay,
        onPause = onPause,
        onStop = {
            onStop()
            onShowStoppedMessage()
        },
        onDismiss = onDismiss
    )
}
