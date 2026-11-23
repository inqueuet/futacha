package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.compat.aimogeBoardShapeOf
import com.valoser.futacha.shared.compat.isAimogeNewsBoard
import com.valoser.futacha.shared.model.localNewsPreviewOriginalPath
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.model.SavedThread
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Use the indexed thumbnail first. Older ニュー速 saves can recover it from their exact generation. */
internal suspend fun SavedThreadRepository.historyThumbnailPath(
    threadId: String,
    boardId: String,
    boardUrl: String
): String? = withContext(AppDispatchers.io) {
    // History auto-saves and imported history are app-private path repositories.
    if (useSaveLocationApi) return@withContext null
    val expectedBoard = historyThumbnailBoardUrl(boardUrl)
    val news = isAimogeNewsBoard(boardUrl)
    for (saved in loadIndex().threads.filter { it.threadId == threadId }.sortedByDescending { it.savedAt }) {
        val storageId = resolveSavedThreadStorageId(saved)
        val sameBoardId = saved.boardId.equals(boardId, ignoreCase = true)
        val details = if (news && !sameBoardId) savedHistoryDetails(saved, news = true) else null
        if (!sameBoardId) {
            val actualBoard = if (news) details?.boardUrl else savedHistoryDetails(saved, news = false)?.boardUrl
            if (expectedBoard == null || actualBoard != expectedBoard) continue
        }
        resolveExistingHistoryThumbnail(storageId, saved.thumbnailPath)?.let { return@withContext it }
        if (news) {
            val stored = details ?: savedHistoryDetails(saved, news = true)
            if (stored?.boardUrl != expectedBoard) continue
            resolveExistingHistoryThumbnail(storageId, stored?.thumbnailPath)?.let { return@withContext it }
            resolveExistingHistoryThumbnail(storageId, stored?.originalPath)?.let { return@withContext it }
        }
    }
    null
}

private suspend fun SavedThreadRepository.resolveExistingHistoryThumbnail(
    storageId: String,
    relativePath: String?
): String? {
    val thumbnail = relativePath?.takeIf { path ->
        path.isNotBlank() && !path.startsWith('/') && ':' !in path && '\\' !in path &&
            path.split('/').none { it == ".." || it == "." || it.isEmpty() }
    } ?: return null
    val path = buildStoragePath("$storageId/$thumbnail")
    return if (fileSystem.exists(path)) fileSystem.resolveAbsolutePath(path) else null
}

/**
 * Every history row of another mode's board ID needs the saved board URL, which only
 * metadata.json (all posts) records. Decode it once per saved generation, not once
 * per row and recomposition.
 */
private suspend fun SavedThreadRepository.savedHistoryDetails(saved: SavedThread, news: Boolean): SavedHistoryDetails? {
    val storageId = resolveSavedThreadStorageId(saved)
    val key = SavedHistoryBoardUrlKey(fileSystem, buildStoragePath(storageId), saved.savedAt, news)
    savedHistoryBoardUrlMutex.withLock { savedHistoryBoardUrls[key]?.let { return it } }
    val metadata = if (news) {
        readRecoverableSavedThreadMetadata(storageId)?.takeIf {
            it.threadId == saved.threadId && it.boardId.equals(saved.boardId, ignoreCase = true)
        }
    } else loadThreadMetadata(saved.threadId, saved.boardId).getOrNull()
    metadata ?: return null
    val op = metadata.posts.firstOrNull()
    val details = SavedHistoryDetails(
        boardUrl = historyThumbnailBoardUrl(metadata.boardUrl),
        thumbnailPath = op?.localThumbnailPath,
        originalPath = op?.localNewsPreviewOriginalPath(metadata.boardUrl)
    )
    savedHistoryBoardUrlMutex.withLock {
        savedHistoryBoardUrls[key] = details
        while (savedHistoryBoardUrls.size > SAVED_HISTORY_BOARD_URL_CACHE_SIZE) {
            savedHistoryBoardUrls.remove(savedHistoryBoardUrls.keys.first())
        }
    }
    return details
}

private data class SavedHistoryBoardUrlKey(val fileSystem: Any, val storagePath: String, val savedAt: Long, val news: Boolean)
private data class SavedHistoryDetails(val boardUrl: String?, val thumbnailPath: String?, val originalPath: String?)
private const val SAVED_HISTORY_BOARD_URL_CACHE_SIZE = 256
private val savedHistoryBoardUrlMutex = Mutex()
private val savedHistoryBoardUrls = LinkedHashMap<SavedHistoryBoardUrlKey, SavedHistoryDetails>()

private fun historyThumbnailBoardUrl(url: String): String? {
    if (isAimogeNewsBoard(url)) return aimogeBoardShapeOf(url)
    return runCatching {
        BoardUrlResolver.resolveBoardBaseUrl(url).substringBefore('?').trimEnd('/').lowercase()
    }.getOrNull()
}
