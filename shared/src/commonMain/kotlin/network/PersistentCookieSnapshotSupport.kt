package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal data class PersistentCookieSnapshotLoadResult(
    val cookies: List<StoredCookie>,
    val revision: Long,
    val restoredFromBackup: Boolean
)

internal suspend fun readPersistentCookieFileContent(
    fileSystem: FileSystem,
    path: String,
    maxCookieFileBytes: Long,
    logTag: String
): String? {
    val size = runSuspendCatchingPreservingCancellation { fileSystem.getFileSize(path) }.getOrNull()
        ?: return null
    if (size !in 0L..maxCookieFileBytes) {
        Logger.w(
            logTag,
            "Skipping oversized cookie file '$path' (${size} bytes > $maxCookieFileBytes bytes)"
        )
        return null
    }
    return fileSystem.readString(path).getOrNull()?.takeIf { content ->
        content.encodeToByteArray().size.toLong() <= maxCookieFileBytes
    }
}

internal suspend fun decodePersistentCookieSnapshotOrNull(
    json: Json,
    content: String,
    path: String,
    isBackup: Boolean,
    logTag: String
): PersistentCookieSnapshotLoadResult? {
    return try {
        val parsed = withContext(AppDispatchers.parsing) {
            json.decodeFromString<StoredCookieFile>(content)
        }
        PersistentCookieSnapshotLoadResult(
            cookies = parsed.cookies,
            revision = normalizePersistentCookieSnapshotRevision(parsed.revision),
            restoredFromBackup = isBackup
        )
    } catch (error: SerializationException) {
        Logger.e(logTag, "Failed to parse cookie file at '$path': ${error.message}")
        null
    }
}

internal fun normalizePersistentCookieSnapshotRevision(revision: Long): Long {
    // Revisions only order writes within this process. A corrupt near-overflow
    // value would otherwise wrap on the next cookie update and permanently
    // prevent newer snapshots from being persisted.
    return revision.takeIf { it in 0L..Long.MAX_VALUE - 1_000_000L } ?: 0L
}

internal fun shouldPersistCookieSnapshotRevision(
    persistedRevision: Long,
    snapshotRevision: Long
): Boolean {
    return snapshotRevision > persistedRevision
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
    maxCookieFileBytes: Long = 1_048_576L,
    logTag: String
) {
    require(maxCookieFileBytes >= 0L) { "maxCookieFileBytes must be non-negative" }
    require(content.encodeToByteArray().size.toLong() <= maxCookieFileBytes) {
        "Cookie snapshot exceeds its permitted size"
    }
    val parentDir = storagePath.substringBeforeLast('/', "")
    if (parentDir.isNotEmpty()) {
        fileSystem.createDirectory(parentDir)
    }
    val backupPath = "$storagePath.backup"
    if (fileSystem.exists(storagePath)) {
        val currentSize = fileSystem.getFileSize(storagePath)
        val currentContent = if (currentSize in 0L..maxCookieFileBytes) {
            fileSystem.readString(storagePath).getOrNull()?.takeIf { existing ->
                existing.encodeToByteArray().size.toLong() <= maxCookieFileBytes
            }
        } else {
            Logger.w(logTag, "Skipped oversized cookie backup at '$storagePath'")
            null
        }
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
