package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post

internal data class ThreadMediaPreviewState(
    val previewMediaIndex: Int? = null
)

internal fun emptyThreadMediaPreviewState(): ThreadMediaPreviewState = ThreadMediaPreviewState()

internal fun normalizeThreadMediaPreviewState(
    currentState: ThreadMediaPreviewState,
    totalCount: Int
): ThreadMediaPreviewState {
    return currentState.copy(
        previewMediaIndex = normalizeMediaPreviewIndex(
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
    return currentState.copy(previewMediaIndex = targetIndex)
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
    return currentState.copy(previewMediaIndex = null)
}

internal fun moveToNextThreadMediaPreview(
    currentState: ThreadMediaPreviewState,
    totalCount: Int
): ThreadMediaPreviewState {
    return currentState.copy(
        previewMediaIndex = nextMediaPreviewIndex(
            currentIndex = currentState.previewMediaIndex,
            totalCount = totalCount
        )
    )
}

internal fun moveToPreviousThreadMediaPreview(
    currentState: ThreadMediaPreviewState,
    totalCount: Int
): ThreadMediaPreviewState {
    return currentState.copy(
        previewMediaIndex = previousMediaPreviewIndex(
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

internal enum class MediaSaveAvailability {
    Busy,
    Unsupported,
    LocationRequired,
    Unavailable,
    Ready
}

internal fun resolveMediaSaveAvailability(
    isAnySaveInProgress: Boolean,
    isRemoteMedia: Boolean,
    requiresManualLocationSelection: Boolean,
    hasStorageDependencies: Boolean
): MediaSaveAvailability {
    return when {
        isAnySaveInProgress -> MediaSaveAvailability.Busy
        !isRemoteMedia -> MediaSaveAvailability.Unsupported
        requiresManualLocationSelection -> MediaSaveAvailability.LocationRequired
        !hasStorageDependencies -> MediaSaveAvailability.Unavailable
        else -> MediaSaveAvailability.Ready
    }
}

internal data class ThreadMediaSaveRequestState(
    val canStartSave: Boolean,
    val message: String? = null,
    val shouldOpenSaveDirectoryPicker: Boolean = false
)

internal fun resolveThreadMediaSaveRequestState(
    isAnySaveInProgress: Boolean,
    isRemoteMedia: Boolean,
    requiresManualLocationSelection: Boolean,
    hasStorageDependencies: Boolean
): ThreadMediaSaveRequestState {
    return when (
        resolveMediaSaveAvailability(
            isAnySaveInProgress = isAnySaveInProgress,
            isRemoteMedia = isRemoteMedia,
            requiresManualLocationSelection = requiresManualLocationSelection,
            hasStorageDependencies = hasStorageDependencies
        )
    ) {
        MediaSaveAvailability.Busy -> ThreadMediaSaveRequestState(
            canStartSave = false,
            message = buildThreadSaveBusyMessage()
        )
        MediaSaveAvailability.Unsupported -> ThreadMediaSaveRequestState(
            canStartSave = false,
            message = "このメディアは保存に対応していません"
        )
        MediaSaveAvailability.LocationRequired -> ThreadMediaSaveRequestState(
            canStartSave = false,
            message = buildThreadSaveLocationRequiredMessage(),
            shouldOpenSaveDirectoryPicker = true
        )
        MediaSaveAvailability.Unavailable -> ThreadMediaSaveRequestState(
            canStartSave = false,
            message = buildThreadSaveUnavailableMessage()
        )
        MediaSaveAvailability.Ready -> ThreadMediaSaveRequestState(canStartSave = true)
    }
}

internal enum class MediaType {
    Image,
    Video
}

internal fun isRemoteMediaUrl(url: String): Boolean {
    val normalized = url.trim()
    return normalized.startsWith("https://", ignoreCase = true) ||
        normalized.startsWith("http://", ignoreCase = true)
}

internal fun determineMediaType(url: String): MediaType {
    val cleaned = url.substringBefore('?')
    val extension = cleaned.substringAfterLast('.', "").lowercase()
    return if (extension in setOf("mp4", "webm", "mkv", "mov", "avi", "ts", "flv")) {
        MediaType.Video
    } else {
        MediaType.Image
    }
}

internal data class MediaPreviewEntry(
    val url: String,
    val mediaType: MediaType,
    val postId: String,
    val title: String
)

internal fun buildMediaPreviewEntries(posts: List<Post>): List<MediaPreviewEntry> {
    return posts.mapNotNull { post ->
        val targetUrl = post.imageUrl?.takeIf { it.isNotBlank() }
            ?: post.thumbnailUrl?.takeIf { it.isNotBlank() }
        if (targetUrl.isNullOrBlank()) return@mapNotNull null
        MediaPreviewEntry(
            url = targetUrl,
            mediaType = determineMediaType(targetUrl),
            postId = post.id,
            title = extractPreviewTitle(post)
        )
    }
}

private fun extractPreviewTitle(post: Post): String {
    val firstLine = messageHtmlToLines(post.messageHtml).firstOrNull()?.trim()
    if (!firstLine.isNullOrBlank()) return firstLine
    val subject = post.subject?.trim()
    if (!subject.isNullOrBlank()) return subject
    return "No.${post.id}"
}
