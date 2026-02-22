package com.valoser.futacha.shared.model

import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.resolveBookmarkPathForDisplay
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore

fun SavedThreadMetadata.toThreadPage(
    fileSystem: FileSystem,
    baseDirectory: String = AUTO_SAVE_DIRECTORY,
    baseSaveLocation: SaveLocation? = null
): ThreadPage {
    val storageFolder = storageId
        ?.takeIf { it.isNotBlank() }
        ?: buildThreadStorageId(boardId, threadId)
    fun resolveLocalPath(relativePath: String?): String? {
        val normalizedRelativePath = relativePath
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (
            normalizedRelativePath.startsWith("content://") ||
            normalizedRelativePath.startsWith("file://") ||
            normalizedRelativePath.startsWith("/")
        ) {
            return normalizedRelativePath
        }

        return when (val location = baseSaveLocation) {
            null -> fileSystem.resolveAbsolutePath("$baseDirectory/$storageFolder/$normalizedRelativePath")
            is SaveLocation.Path -> {
                val resolvedBase = fileSystem.resolveAbsolutePath(location.path).trimEnd('/')
                "$resolvedBase/$storageFolder/$normalizedRelativePath"
            }
            is SaveLocation.TreeUri -> buildDocumentUriFromTree(
                treeUri = location.uri,
                storageFolder = storageFolder,
                relativePath = normalizedRelativePath
            )
            is SaveLocation.Bookmark -> {
                val bookmarkBasePath = resolveBookmarkPathForDisplay(location.bookmarkData)
                    ?.trimEnd('/')
                    ?: return null
                "$bookmarkBasePath/$storageFolder/$normalizedRelativePath"
            }
        }
    }

    val convertedPosts = posts.map { savedPost ->
        val resolvedImagePath = resolveLocalPath(savedPost.localImagePath)
        val resolvedVideoPath = resolveLocalPath(savedPost.localVideoPath)
        val resolvedThumbnailPath = resolveLocalPath(savedPost.localThumbnailPath)
        Post(
            id = savedPost.id,
            order = savedPost.order,
            author = savedPost.author,
            subject = savedPost.subject,
            timestamp = savedPost.timestamp,
            posterId = null,
            messageHtml = savedPost.messageHtml,
            imageUrl = resolvedImagePath
                ?: resolvedVideoPath
                ?: savedPost.originalImageUrl
                ?: savedPost.originalVideoUrl,
            thumbnailUrl = resolvedThumbnailPath ?: savedPost.originalThumbnailUrl,
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

private fun buildDocumentUriFromTree(
    treeUri: String,
    storageFolder: String,
    relativePath: String
): String? {
    if (!treeUri.startsWith("content://")) return null
    val sanitizedTreeUri = treeUri.substringBefore('?').substringBefore('#').trimEnd('/')
    val treeMarker = "/tree/"
    val treeMarkerIndex = sanitizedTreeUri.indexOf(treeMarker)
    if (treeMarkerIndex == -1) return null
    val authorityPrefix = sanitizedTreeUri.substring(0, treeMarkerIndex)
    val treeId = sanitizedTreeUri
        .substring(treeMarkerIndex + treeMarker.length)
        .substringBefore('/')
        .takeIf { it.isNotBlank() }
        ?: return null

    val encodedSuffix = buildSafDocIdSuffix(storageFolder, relativePath)
    val fullDocId = if (encodedSuffix.isBlank()) {
        treeId
    } else {
        "$treeId%2F$encodedSuffix"
    }
    return "$authorityPrefix/tree/$treeId/document/$fullDocId"
}

private fun buildSafDocIdSuffix(storageFolder: String, relativePath: String): String {
    val normalizedSegments = "$storageFolder/$relativePath"
        .split('/')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (normalizedSegments.isEmpty()) return ""
    return normalizedSegments.joinToString("%2F") { encodeSafDocIdSegment(it) }
}

private fun encodeSafDocIdSegment(value: String): String {
    return value
        .replace("%", "%25")
        .replace("/", "%2F")
        .replace(" ", "%20")
}
