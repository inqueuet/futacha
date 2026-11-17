package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.model.SavedThread
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            if (expectedBoard == null || savedHistoryBoardUrl(saved) != expectedBoard) continue
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

/**
 * Every history row of another mode's board ID needs the saved board URL, which only
 * metadata.json (all posts) records. Decode it once per saved generation, not once
 * per row and recomposition.
 */
private suspend fun SavedThreadRepository.savedHistoryBoardUrl(saved: SavedThread): String? {
    val key = SavedHistoryBoardUrlKey(fileSystem, buildStoragePath(resolveSavedThreadStorageId(saved)), saved.savedAt)
    savedHistoryBoardUrlMutex.withLock { savedHistoryBoardUrls[key]?.let { return it.value } }
    val metadata = loadThreadMetadata(saved.threadId, saved.boardId).getOrNull() ?: return null
    val boardUrl = historyThumbnailBoardUrl(metadata.boardUrl)
    savedHistoryBoardUrlMutex.withLock {
        savedHistoryBoardUrls.remove(key)
        savedHistoryBoardUrls[key] = SavedHistoryBoardUrl(boardUrl)
        while (savedHistoryBoardUrls.size > SAVED_HISTORY_BOARD_URL_CACHE_SIZE) {
            savedHistoryBoardUrls.remove(savedHistoryBoardUrls.keys.first())
        }
    }
    return boardUrl
}

private data class SavedHistoryBoardUrlKey(val fileSystem: Any, val storagePath: String, val savedAt: Long)
private class SavedHistoryBoardUrl(val value: String?)
private const val SAVED_HISTORY_BOARD_URL_CACHE_SIZE = 256
private val savedHistoryBoardUrlMutex = Mutex()
private val savedHistoryBoardUrls = LinkedHashMap<SavedHistoryBoardUrlKey, SavedHistoryBoardUrl>()

private fun historyThumbnailBoardUrl(url: String): String? = runCatching {
    BoardUrlResolver.resolveBoardBaseUrl(url).substringBefore('?').trimEnd('/').lowercase()
}.getOrNull()
