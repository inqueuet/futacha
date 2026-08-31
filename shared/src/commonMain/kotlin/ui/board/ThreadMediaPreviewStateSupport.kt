package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
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
    return openThreadMediaPreview(
        currentState = currentState,
        entries = entries,
        indexByKey = buildMediaPreviewIndexByKey(entries),
        url = url,
        mediaType = mediaType
    )
}

internal fun openThreadMediaPreview(
    currentState: ThreadMediaPreviewState,
    entries: List<MediaPreviewEntry>,
    indexByKey: Map<MediaPreviewKey, Int>,
    url: String,
    mediaType: MediaType
): ThreadMediaPreviewState {
    val targetIndex = indexByKey[MediaPreviewKey(url = url, mediaType = mediaType)] ?: -1
    if (targetIndex !in entries.indices) return currentState
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
    return resolveThreadMediaClickState(
        currentState = currentState,
        entries = entries,
        indexByKey = buildMediaPreviewIndexByKey(entries),
        url = url,
        mediaType = mediaType
    )
}

internal fun resolveThreadMediaClickState(
    currentState: ThreadMediaPreviewState,
    entries: List<MediaPreviewEntry>,
    indexByKey: Map<MediaPreviewKey, Int>,
    url: String,
    mediaType: MediaType
): ThreadMediaPreviewState? {
    val nextState = openThreadMediaPreview(
        currentState = currentState,
        entries = entries,
        indexByKey = indexByKey,
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

internal fun isSwipeNavigationStartWithinBounds(
    position: Offset,
    containerSize: IntSize,
    startPaddingPx: Float,
    topPaddingPx: Float,
    endPaddingPx: Float,
    bottomPaddingPx: Float
): Boolean {
    if (containerSize.width <= 0 || containerSize.height <= 0) return false
    val left = startPaddingPx.coerceAtLeast(0f)
    val top = topPaddingPx.coerceAtLeast(0f)
    val right = containerSize.width - endPaddingPx.coerceAtLeast(0f)
    val bottom = containerSize.height - bottomPaddingPx.coerceAtLeast(0f)
    if (left >= right || top >= bottom) return false
    return position.x in left..right && position.y in top..bottom
}
