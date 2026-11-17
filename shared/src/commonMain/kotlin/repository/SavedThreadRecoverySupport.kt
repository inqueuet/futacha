package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation

internal fun isRecoverableSavedThreadDirectory(name: String): Boolean =
    name.isNotBlank() && name.length <= 255 && name !in setOf(".", "..") &&
        '/' !in name && '\\' !in name && '\u0000' !in name

internal suspend fun SavedThreadRepository.recoverSavedThreadMetadata(storageId: String): SavedThread? {
    if (!existsAt("$storageId/metadata.json")) return null
    return runSuspendCatchingPreservingCancellation {
        val encoded = readStringAtWithLimit("$storageId/metadata.json", MAX_SAVED_THREAD_METADATA_BYTES).getOrThrow()
        val metadata = requireSavedThreadMetadataWithinLimits(json.decodeFromString<SavedThreadMetadata>(encoded))
        require(metadata.threadId.isNotBlank())
        require(metadata.storageId == null || metadata.storageId == storageId)
        val incomplete = metadata.posts.count { !it.downloadSuccess }
        val missingHtml = metadata.isHtmlMissing || metadata.rawHtmlPath == null
        SavedThread(
            threadId = metadata.threadId,
            boardId = metadata.boardId,
            boardName = metadata.boardName,
            title = metadata.title,
            storageId = storageId,
            thumbnailPath = metadata.posts.firstOrNull()?.localThumbnailPath,
            savedAt = metadata.savedAt,
            postCount = metadata.posts.size,
            imageCount = metadata.posts.mapNotNull { it.localImagePath }.distinct().size,
            videoCount = metadata.posts.mapNotNull { it.localVideoPath }.distinct().size,
            totalSize = metadata.totalSize,
            status = if (incomplete > 0 || missingHtml || metadata.isTruncated) SaveStatus.PARTIAL else SaveStatus.COMPLETED,
            incompleteMediaCount = incomplete,
            isHtmlMissing = missingHtml
        )
    }.getOrNull()
}
