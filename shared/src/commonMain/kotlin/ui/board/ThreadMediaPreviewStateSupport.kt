package com.valoser.futacha.shared.ui.board

import kotlin.math.abs

internal data class ThreadMediaPreviewState(
    val previewMediaIndex: Int? = null
)

internal fun emptyThreadMediaPreviewState(): ThreadMediaPreviewState = ThreadMediaPreviewState()

private fun ThreadMediaPreviewState.withPreviewMediaIndex(index: Int?): ThreadMediaPreviewState {
    return copy(previewMediaIndex = index)
}

internal fun normalizeThreadMediaPreviewState(
    currentState: ThreadMediaPreviewState,
    totalCount: Int
): ThreadMediaPreviewState {
    return currentState.withPreviewMediaIndex(
        normalizeMediaPreviewIndex(
            currentIndex = currentState.previewMediaIndex,
            totalCount = totalCount
        )
    )
}

internal fun openThreadMediaPreview(
    currentState: ThreadMediaPreviewState,
    entries: List<MediaPreviewEntry>,
    url: String,
    mediaType: MediaType
): ThreadMediaPreviewState {
    val targetIndex = entries.indexOfFirst { it.url == url && it.mediaType == mediaType }
    if (targetIndex < 0) return currentState
    return currentState.withPreviewMediaIndex(targetIndex)
}

internal fun resolveThreadMediaPreviewNormalizationState(
    currentState: ThreadMediaPreviewState,
    totalCount: Int
): ThreadMediaPreviewState? {
    val normalizedState = normalizeThreadMediaPreviewState(
        currentState = currentState,
        totalCount = totalCount
    )
    return normalizedState.takeIf { it != currentState }
}

internal fun resolveThreadMediaClickState(
    currentState: ThreadMediaPreviewState,
    entries: List<MediaPreviewEntry>,
    url: String,
    mediaType: MediaType
): ThreadMediaPreviewState? {
    val nextState = openThreadMediaPreview(
        currentState = currentState,
        entries = entries,
        url = url,
        mediaType = mediaType
    )
    return nextState.takeIf { it != currentState }
}

internal fun dismissThreadMediaPreview(currentState: ThreadMediaPreviewState): ThreadMediaPreviewState {
    return currentState.withPreviewMediaIndex(null)
}

internal fun moveToNextThreadMediaPreview(
    currentState: ThreadMediaPreviewState,
    totalCount: Int
): ThreadMediaPreviewState {
    return currentState.withPreviewMediaIndex(
        nextMediaPreviewIndex(
            currentIndex = currentState.previewMediaIndex,
            totalCount = totalCount
        )
    )
}

internal fun moveToPreviousThreadMediaPreview(
    currentState: ThreadMediaPreviewState,
    totalCount: Int
): ThreadMediaPreviewState {
    return currentState.withPreviewMediaIndex(
        previousMediaPreviewIndex(
            currentIndex = currentState.previewMediaIndex,
            totalCount = totalCount
        )
    )
}

internal fun currentThreadMediaPreviewEntry(
    state: ThreadMediaPreviewState,
    entries: List<MediaPreviewEntry>
): MediaPreviewEntry? = state.previewMediaIndex?.let { entries.getOrNull(it) }

internal data class ThreadMediaPreviewDialogState(
    val entry: MediaPreviewEntry,
    val currentIndex: Int,
    val totalCount: Int,
    val isSaveEnabled: Boolean,
    val isSaveInProgress: Boolean
)

internal fun resolveThreadMediaPreviewDialogState(
    state: ThreadMediaPreviewState,
    entries: List<MediaPreviewEntry>,
    isSaveInProgress: Boolean
): ThreadMediaPreviewDialogState? {
    val entry = currentThreadMediaPreviewEntry(state, entries) ?: return null
    return ThreadMediaPreviewDialogState(
        entry = entry,
        currentIndex = state.previewMediaIndex ?: 0,
        totalCount = entries.size,
        isSaveEnabled = isRemoteMediaUrl(entry.url) && !isSaveInProgress,
        isSaveInProgress = isSaveInProgress
    )
}

internal fun normalizeMediaPreviewIndex(currentIndex: Int?, totalCount: Int): Int? {
    if (currentIndex == null) return null
    return if (currentIndex in 0 until totalCount) currentIndex else null
}

internal fun nextMediaPreviewIndex(currentIndex: Int?, totalCount: Int): Int? {
    if (totalCount <= 0) return null
    val resolvedIndex = currentIndex ?: 0
    return (resolvedIndex + 1) % totalCount
}

internal fun previousMediaPreviewIndex(currentIndex: Int?, totalCount: Int): Int? {
    if (totalCount <= 0) return null
    val resolvedIndex = currentIndex ?: 0
    return (resolvedIndex + totalCount - 1) % totalCount
}

internal enum class SwipeNavigationAction {
    None,
    Next,
    Previous
}

internal fun resolveSwipeNavigationAction(
    totalDx: Float,
    totalDy: Float,
    thresholdPx: Float
): SwipeNavigationAction {
    if (thresholdPx <= 0f) return SwipeNavigationAction.None
    val horizontalDistance = abs(totalDx)
    val verticalDistance = abs(totalDy)
    if (horizontalDistance < thresholdPx || horizontalDistance <= verticalDistance) {
        return SwipeNavigationAction.None
    }
    return if (totalDx < 0f) {
        SwipeNavigationAction.Next
    } else {
        SwipeNavigationAction.Previous
    }
}
