package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

/** Resolve the current indexed generation, without decoding every post in a saved thread. */
internal suspend fun SavedThreadRepository.historyThumbnailPath(
    threadId: String,
    boardId: String,
    boardUrl: String
): String? = withContext(AppDispatchers.io) {
    // History auto-saves and imported history are app-private path repositories.
    if (useSaveLocationApi) return@withContext null
    val expectedBoard = historyThumbnailBoardUrl(boardUrl)
    for (saved in loadIndex().threads.filter { it.threadId == threadId }.sortedByDescending { it.savedAt }) {
        if (!saved.boardId.equals(boardId, ignoreCase = true)) {
            // The two modes can use different board IDs. Never match on thread number alone.
            val metadata = loadThreadMetadata(threadId, saved.boardId).getOrNull() ?: continue
            if (expectedBoard == null || historyThumbnailBoardUrl(metadata.boardUrl) != expectedBoard) continue
        }
        val thumbnail = saved.thumbnailPath?.takeIf { path ->
            path.isNotBlank() && !path.startsWith('/') && ':' !in path && '\\' !in path &&
                path.split('/').none { it == ".." || it == "." || it.isEmpty() }
        } ?: continue
        val path = buildStoragePath("${resolveSavedThreadStorageId(saved)}/$thumbnail")
        if (fileSystem.exists(path)) return@withContext fileSystem.resolveAbsolutePath(path)
    }
    null
}

private fun historyThumbnailBoardUrl(url: String): String? = runCatching {
    BoardUrlResolver.resolveBoardBaseUrl(url).substringBefore('?').trimEnd('/').lowercase()
}.getOrNull()
