package com.valoser.futacha.shared.model

import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore

fun SavedThreadMetadata.toThreadPage(
    fileSystem: FileSystem,
    baseDirectory: String = AUTO_SAVE_DIRECTORY
): ThreadPage {
    val basePath = "$baseDirectory/$threadId"
    fun resolveLocalPath(relativePath: String?): String? {
        return relativePath?.let {
            fileSystem.resolveAbsolutePath("$basePath/$it")
        }
    }

    val convertedPosts = posts.map { savedPost ->
        Post(
            id = savedPost.id,
            order = savedPost.order,
            author = savedPost.author,
            subject = savedPost.subject,
            timestamp = savedPost.timestamp,
            posterId = null,
            messageHtml = savedPost.messageHtml,
            imageUrl = resolveLocalPath(savedPost.localImagePath) ?: savedPost.originalImageUrl,
            thumbnailUrl = resolveLocalPath(savedPost.localThumbnailPath) ?: savedPost.originalThumbnailUrl,
            saidaneLabel = null,
            isDeleted = !savedPost.downloadSuccess,
            referencedCount = 0,
            quoteReferences = emptyList()
        )
    }
    val postsWithReferences = ThreadHtmlParserCore.rebuildReferences(convertedPosts)

    return ThreadPage(
        threadId = threadId,
        boardTitle = boardName,
        expiresAtLabel = expiresAtLabel,
        deletedNotice = null,
        posts = postsWithReferences,
        isTruncated = false,
        truncationReason = null
    )
}
