package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ThreadScreenMediaBindings(
    val normalizePreviewState: () -> Unit,
    val onMediaClick: (String, MediaType) -> Unit
)

internal fun buildThreadScreenMediaBindings(
    coroutineScope: CoroutineScope,
    currentPreviewState: () -> ThreadMediaPreviewState,
    setPreviewState: (ThreadMediaPreviewState) -> Unit,
    currentEntries: () -> List<MediaPreviewEntry>,
    ensureMediaPreviewCollection: suspend () -> MediaPreviewCollection
): ThreadScreenMediaBindings {
    return ThreadScreenMediaBindings(
        normalizePreviewState = {
            resolveThreadMediaPreviewNormalizationState(
                currentState = currentPreviewState(),
                totalCount = currentEntries().size
            )?.let(setPreviewState)
        },
        onMediaClick = { url, mediaType ->
            coroutineScope.launch {
                val collection = ensureMediaPreviewCollection()
                resolveThreadMediaClickState(
                    currentState = currentPreviewState(),
                    entries = collection.entries,
                    indexByKey = collection.indexByKey,
                    url = url,
                    mediaType = mediaType
                )?.let(setPreviewState)
            }
        }
    )
}
