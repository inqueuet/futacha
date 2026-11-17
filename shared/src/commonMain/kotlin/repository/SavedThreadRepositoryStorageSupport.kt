package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation

internal const val MAX_SAVED_THREAD_METADATA_BYTES = 8L * 1024L * 1024L
internal const val MAX_SAVED_THREAD_METADATA_POSTS = 10_000
private const val MAX_SAVED_THREAD_METADATA_FIELD_CHARS = 2 * 1024 * 1024
private const val MAX_SAVED_THREAD_METADATA_PATH_CHARS = 8_192

internal fun requireSavedThreadMetadataWithinLimits(metadata: SavedThreadMetadata): SavedThreadMetadata {
    require(metadata.posts.size <= MAX_SAVED_THREAD_METADATA_POSTS) {
        "Saved thread metadata contains too many posts"
    }
    require(metadata.totalSize >= 0L) { "Saved thread metadata has an invalid size" }
    require(metadata.threadId.length <= MAX_SAVED_THREAD_METADATA_PATH_CHARS)
    require(metadata.boardId.length <= MAX_SAVED_THREAD_METADATA_PATH_CHARS)
    require(metadata.storageId.orEmpty().length <= MAX_SAVED_THREAD_METADATA_PATH_CHARS)
    for (post in metadata.posts) {
        require(post.id.length <= MAX_SAVED_THREAD_METADATA_PATH_CHARS)
        require(post.messageHtml.length <= MAX_SAVED_THREAD_METADATA_FIELD_CHARS)
        require(post.localImagePath.orEmpty().length <= MAX_SAVED_THREAD_METADATA_PATH_CHARS)
        require(post.localVideoPath.orEmpty().length <= MAX_SAVED_THREAD_METADATA_PATH_CHARS)
        require(post.localThumbnailPath.orEmpty().length <= MAX_SAVED_THREAD_METADATA_PATH_CHARS)
    }
    return metadata
}

internal fun SavedThreadRepository.buildStoragePath(relativePath: String): String {
    return if (relativePath.isBlank()) {
        baseDirectory
    } else {
        "$baseDirectory/$relativePath"
    }
}

internal suspend fun SavedThreadRepository.readStringAt(relativePath: String): Result<String> {
    return if (useSaveLocationApi) {
        fileSystem.readString(resolvedSaveLocation, relativePath)
    } else {
        fileSystem.readString(buildStoragePath(relativePath))
    }
}

internal suspend fun SavedThreadRepository.readStringAtWithLimit(
    relativePath: String,
    maxBytes: Long
): Result<String> = runSuspendCatchingPreservingCancellation {
    require(maxBytes >= 0L) { "maxBytes must be non-negative" }
    val sizeBeforeRead = getFileSizeAt(relativePath)
    require(sizeBeforeRead in 0L..maxBytes) { "File exceeds its permitted size" }
    val content = readStringAt(relativePath).getOrThrow()
    require(content.encodeToByteArray().size.toLong() <= maxBytes) {
        "File grew beyond its permitted size while reading"
    }
    content
}

internal suspend fun SavedThreadRepository.readBytesAt(relativePath: String): Result<ByteArray> {
    return if (useSaveLocationApi) {
        fileSystem.readBytes(resolvedSaveLocation, relativePath)
    } else {
        fileSystem.readBytes(buildStoragePath(relativePath))
    }
}

internal suspend fun SavedThreadRepository.getFileSizeAt(relativePath: String): Long {
    return if (useSaveLocationApi) {
        fileSystem.getFileSize(resolvedSaveLocation, relativePath)
    } else {
        fileSystem.getFileSize(buildStoragePath(relativePath))
    }
}

internal suspend fun SavedThreadRepository.existsAt(relativePath: String): Boolean {
    return if (useSaveLocationApi) {
        fileSystem.exists(resolvedSaveLocation, relativePath)
    } else {
        fileSystem.exists(buildStoragePath(relativePath))
    }
}

internal suspend fun SavedThreadRepository.listFilesAt(relativePath: String): List<String> {
    return if (useSaveLocationApi) {
        fileSystem.listFiles(resolvedSaveLocation, relativePath)
    } else {
        fileSystem.listFiles(buildStoragePath(relativePath))
    }
}

internal suspend fun SavedThreadRepository.writeStringAt(
    relativePath: String,
    content: String
): Result<Unit> {
    return if (useSaveLocationApi) {
        fileSystem.writeString(resolvedSaveLocation, relativePath, content)
    } else {
        fileSystem.writeString(buildStoragePath(relativePath), content)
    }
}

internal suspend fun SavedThreadRepository.writeBytesAt(
    relativePath: String,
    bytes: ByteArray
): Result<Unit> {
    return if (useSaveLocationApi) {
        fileSystem.writeBytes(resolvedSaveLocation, relativePath, bytes)
    } else {
        fileSystem.writeBytes(buildStoragePath(relativePath), bytes)
    }
}

internal suspend fun SavedThreadRepository.deletePath(relativePath: String): Result<Unit> {
    return if (useSaveLocationApi) {
        fileSystem.delete(resolvedSaveLocation, relativePath)
    } else {
        fileSystem.deleteRecursively(buildStoragePath(relativePath))
    }
}
