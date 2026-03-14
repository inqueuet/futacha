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

internal suspend fun SavedThreadRepository.deletePath(relativePath: String): Result<Unit> {
    return if (useSaveLocationApi) {
        fileSystem.delete(resolvedSaveLocation, relativePath)
    } else {
        fileSystem.deleteRecursively(buildStoragePath(relativePath))
    }
}
