package com.valoser.futacha.shared.repository

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
