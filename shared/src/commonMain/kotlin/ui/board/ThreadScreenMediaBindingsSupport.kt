package com.valoser.futacha.shared.ui.board

internal data class ThreadScreenMediaBindings(
    val normalizePreviewState: () -> Unit,
    val onMediaClick: (String, MediaType) -> Unit
)

internal fun buildThreadScreenMediaBindings(
    currentPreviewState: () -> ThreadMediaPreviewState,
    setPreviewState: (ThreadMediaPreviewState) -> Unit,
    currentEntries: () -> List<MediaPreviewEntry>
): ThreadScreenMediaBindings {
    return ThreadScreenMediaBindings(
        normalizePreviewState = {
            resolveThreadMediaPreviewNormalizationState(
                currentState = currentPreviewState(),
                totalCount = currentEntries().size
            )?.let(setPreviewState)
        },
        onMediaClick = { url, mediaType ->
            resolveThreadMediaClickState(
                currentState = currentPreviewState(),
                entries = currentEntries(),
                url = url,
                mediaType = mediaType
            )?.let(setPreviewState)
        }
    )
}
