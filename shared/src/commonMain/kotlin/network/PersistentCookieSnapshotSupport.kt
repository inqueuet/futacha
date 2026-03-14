package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal data class PersistentCookieSnapshotLoadResult(
    val cookies: List<StoredCookie>,
    val restoredFromBackup: Boolean
)

internal suspend fun readPersistentCookieFileContent(
    fileSystem: FileSystem,
    path: String,
    maxCookieFileBytes: Long,
    logTag: String
): String? {
    val size = runCatching { fileSystem.getFileSize(path) }.getOrNull()
    if (size != null && size > maxCookieFileBytes) {
        Logger.w(
            logTag,
            "Skipping oversized cookie file '$path' (${size} bytes > $maxCookieFileBytes bytes)"
        )
        return null
    }
    return fileSystem.readString(path).getOrNull()
}

internal fun decodePersistentCookieSnapshotOrNull(
    json: Json,
    content: String,
    path: String,
    isBackup: Boolean,
    logTag: String
): PersistentCookieSnapshotLoadResult? {
    return try {
        val parsed = json.decodeFromString<StoredCookieFile>(content)
        PersistentCookieSnapshotLoadResult(
            cookies = parsed.cookies,
            restoredFromBackup = isBackup
        )
    } catch (error: SerializationException) {
        Logger.e(logTag, "Failed to parse cookie file at '$path': ${error.message}")
        null
    }
}

internal suspend fun loadPersistentCookieSnapshot(
    fileSystem: FileSystem,
    storagePath: String,
    json: Json,
    maxCookieFileBytes: Long,
    logTag: String
): PersistentCookieSnapshotLoadResult? {
    val primaryContent = readPersistentCookieFileContent(
        fileSystem = fileSystem,
        path = storagePath,
        maxCookieFileBytes = maxCookieFileBytes,
        logTag = logTag
    ).orEmpty()
    if (primaryContent.isNotBlank()) {
        decodePersistentCookieSnapshotOrNull(
            json = json,
            content = primaryContent,
            path = storagePath,
            isBackup = false,
            logTag = logTag
        )?.let { return it }
    }

    val backupPath = "$storagePath.backup"
    if (!fileSystem.exists(backupPath)) {
        return null
    }
    val backupContent = readPersistentCookieFileContent(
        fileSystem = fileSystem,
        path = backupPath,
        maxCookieFileBytes = maxCookieFileBytes,
        logTag = logTag
    ).orEmpty()
    if (backupContent.isBlank()) {
        return null
    }
    return decodePersistentCookieSnapshotOrNull(
        json = json,
        content = backupContent,
        path = backupPath,
        isBackup = true,
        logTag = logTag
    )
}

internal suspend fun persistPersistentCookieSnapshot(
    fileSystem: FileSystem,
    storagePath: String,
    content: String,
    logTag: String
) {
    val parentDir = storagePath.substringBeforeLast('/', "")
    if (parentDir.isNotEmpty()) {
        fileSystem.createDirectory(parentDir)
    }
    val backupPath = "$storagePath.backup"
    if (fileSystem.exists(storagePath)) {
        val currentContent = fileSystem.readString(storagePath).getOrNull()
        if (currentContent != null && currentContent.isNotBlank()) {
            fileSystem.writeString(backupPath, currentContent)
                .onFailure { error ->
                    Logger.w(logTag, "Failed to create backup: ${error.message}")
                }
        }
    }
    fileSystem.writeString(storagePath, content)
        .onFailure { error ->
            Logger.e(logTag, "Failed to save cookie file: ${error.message}", error)
        }
        .getOrThrow()
}
