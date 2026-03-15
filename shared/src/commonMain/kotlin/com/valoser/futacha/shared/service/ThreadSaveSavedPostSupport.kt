package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SavedPost

internal data class ThreadSaveResolvedPostMedia(
    val originalImageUrl: String?,
    val localImagePath: String?,
    val originalVideoUrl: String?,
    val localVideoPath: String?,
    val originalThumbnailUrl: String?,
    val localThumbnailPath: String?
)

internal fun resolveThreadSavePostMedia(
    post: Post,
    mediaKeyToFileInfoMap: Map<String, ThreadSaveLocalFileInfo>
): ThreadSaveResolvedPostMedia {
    val imageFileInfo = post.imageUrl?.let {
        mediaKeyToFileInfoMap[buildThreadSaveMediaDownloadKey(it, ThreadSaveMediaRequestType.FULL_IMAGE)]
    }
    val thumbnailFileInfo = post.thumbnailUrl?.let {
        mediaKeyToFileInfoMap[buildThreadSaveMediaDownloadKey(it, ThreadSaveMediaRequestType.THUMBNAIL)]
    }
    return ThreadSaveResolvedPostMedia(
        originalImageUrl = post.imageUrl?.takeIf { imageFileInfo?.fileType == FileType.FULL_IMAGE },
        localImagePath = imageFileInfo
            ?.takeIf { it.fileType == FileType.FULL_IMAGE }
            ?.relativePath,
        originalVideoUrl = post.imageUrl?.takeIf { imageFileInfo?.fileType == FileType.VIDEO },
        localVideoPath = imageFileInfo
            ?.takeIf { it.fileType == FileType.VIDEO }
            ?.relativePath,
        originalThumbnailUrl = post.thumbnailUrl,
        localThumbnailPath = thumbnailFileInfo?.relativePath
    )
}

internal fun buildThreadSaveSavedPost(
    post: Post,
    resolvedMedia: ThreadSaveResolvedPostMedia,
    urlToPathMap: Map<String, String>
): SavedPost {
    return SavedPost(
        id = post.id,
        order = post.order,
        author = post.author,
        subject = post.subject,
        timestamp = post.timestamp,
        messageHtml = convertSavedThreadHtmlPaths(post.messageHtml, urlToPathMap),
        originalImageUrl = resolvedMedia.originalImageUrl,
        localImagePath = resolvedMedia.localImagePath,
        originalVideoUrl = resolvedMedia.originalVideoUrl,
        localVideoPath = resolvedMedia.localVideoPath,
        originalThumbnailUrl = resolvedMedia.originalThumbnailUrl,
        localThumbnailPath = resolvedMedia.localThumbnailPath,
        downloadSuccess = resolveThreadSavedPostDownloadSuccess(
            originalImageUrl = post.imageUrl,
            localImagePath = resolvedMedia.localImagePath,
            localVideoPath = resolvedMedia.localVideoPath
        )
    )
}
