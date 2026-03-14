package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.util.Logger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlin.time.Clock

internal suspend fun SavedThreadRepository.readSavedThreadIndexUnlocked(): SavedThreadIndex {
    fun emptyIndex() = SavedThreadIndex(
        threads = emptyList(),
        totalSize = 0L,
        lastUpdated = Clock.System.now().toEpochMilliseconds()
    )

    var primaryCorrupted = false
    var backupCorrupted = false

    fun markCorruption(path: String, error: SerializationException, isBackup: Boolean) {
        if (isBackup) {
            backupCorrupted = true
        } else {
            primaryCorrupted = true
        }
        Logger.e(
            "SavedThreadRepository",
            "Failed to decode saved thread index at '$path': ${error.message}",
            error
        )
    }

    suspend fun readIndexFromPath(path: String, isBackup: Boolean): SavedThreadIndex? {
        val jsonString = fileSystem.readString(path).getOrElse { error ->
            if (isPathAlreadyDeleted(error)) {
                return null
            }
            throw IllegalStateException("Failed to read saved thread index at '$path': ${error.message}", error)
        }
        return try {
            json.decodeFromString<SavedThreadIndex>(jsonString)
        } catch (e: SerializationException) {
            markCorruption(path, e, isBackup)
            null
        }
    }

    suspend fun readIndexFromLocation(relativePath: String, isBackup: Boolean): SavedThreadIndex? {
        val jsonString = fileSystem.readString(resolvedSaveLocation, relativePath).getOrElse { error ->
            if (isPathAlreadyDeleted(error)) {
                return null
            }
            throw IllegalStateException("Failed to read saved thread index at '$relativePath': ${error.message}", error)
        }
        return try {
            json.decodeFromString<SavedThreadIndex>(jsonString)
        } catch (e: SerializationException) {
            markCorruption(relativePath, e, isBackup)
            null
        }
    }

    val primary = if (useSaveLocationApi) {
        readIndexFromLocation(indexRelativePath, isBackup = false)
    } else {
        readIndexFromPath(buildStoragePath(indexRelativePath), isBackup = false)
    }
    if (primary != null) {
        return sanitizeAndRepairSavedThreadIndexUnlocked(primary)
    }

    val backupPath = "$indexRelativePath.backup"
    val backup = if (useSaveLocationApi) {
        readIndexFromLocation(backupPath, isBackup = true)
    } else {
        readIndexFromPath(buildStoragePath(backupPath), isBackup = true)
    }
    if (backup != null) {
        val repairedBackup = sanitizeAndRepairSavedThreadIndexUnlocked(backup)
        Logger.w("SavedThreadRepository", "Loaded index from backup due to missing/corrupted primary index")
        return repairedBackup
    }

    if (primaryCorrupted || backupCorrupted) {
        throw IllegalStateException(
            "Saved thread index is corrupted and no valid backup is available " +
                "(primaryCorrupted=$primaryCorrupted, backupCorrupted=$backupCorrupted)"
        )
    }

    return emptyIndex()
}

internal suspend fun SavedThreadRepository.sanitizeAndRepairSavedThreadIndexUnlocked(
    index: SavedThreadIndex
): SavedThreadIndex {
    if (index.threads.isEmpty()) {
        if (index.totalSize != 0L) {
            return index.copy(
                totalSize = 0L,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )
        }
        return index
    }

    val sanitized = sanitizeSavedThreadIndex(
        index = index,
        nowMillis = Clock.System.now().toEpochMilliseconds(),
        onOverflow = ::logTotalSizeOverflow
    )
    val repaired = sanitized.index
    val droppedCount = sanitized.droppedDuplicateCount
    if (repaired == index) {
        return index
    }
    if (droppedCount > 0) {
        Logger.w(
            "SavedThreadRepository",
            "Repaired index by dropping $droppedCount duplicate thread entries"
        )
    }
    return repaired
}

internal suspend fun SavedThreadRepository.saveSavedThreadIndexUnlocked(index: SavedThreadIndex) {
    suspend fun writeIndexPayload(relativePath: String, jsonString: String) {
        if (useSaveLocationApi) {
            fileSystem.writeString(resolvedSaveLocation, relativePath, jsonString).getOrThrow()
        } else {
            fileSystem.writeString(buildStoragePath(relativePath), jsonString).getOrThrow()
        }
    }

    suspend fun deleteBestEffort(relativePath: String) {
        runCatching {
            if (useSaveLocationApi) {
                fileSystem.delete(resolvedSaveLocation, relativePath).getOrThrow()
            } else {
                fileSystem.delete(buildStoragePath(relativePath)).getOrThrow()
            }
        }
    }

    ensureSavedThreadBaseDirectoryPreparedUnlocked()
    val jsonString = json.encodeToString(index)
    val tempPath = "$indexRelativePath.tmp"
    val backupPath = "$indexRelativePath.backup"
    runCatching {
        writeIndexPayload(tempPath, jsonString)
        writeIndexPayload(backupPath, jsonString)
        writeIndexPayload(indexRelativePath, jsonString)
        deleteBestEffort(tempPath)
    }.getOrElse { firstError ->
        isBaseDirectoryPrepared = false
        ensureSavedThreadBaseDirectoryPreparedUnlocked()
        runCatching {
            writeIndexPayload(tempPath, jsonString)
            writeIndexPayload(backupPath, jsonString)
            writeIndexPayload(indexRelativePath, jsonString)
            deleteBestEffort(tempPath)
        }.getOrElse { retryError ->
            throw Exception(
                "Failed to persist index after directory re-prepare. first=${firstError.message}, retry=${retryError.message}",
                retryError
            )
        }
    }
}

internal suspend fun SavedThreadRepository.ensureSavedThreadBaseDirectoryPreparedUnlocked() {
    if (isBaseDirectoryPrepared) return
    if (useSaveLocationApi) {
        fileSystem.createDirectory(resolvedSaveLocation).getOrThrow()
    } else {
        fileSystem.createDirectory(baseDirectory).getOrThrow()
    }
    isBaseDirectoryPrepared = true
}

internal suspend fun SavedThreadRepository.resolveMetadataCandidatesUnlocked(
    threadId: String,
    boardId: String?
): List<String> {
    val currentIndex = readSavedThreadIndexUnlocked()
    return resolveSavedThreadMetadataCandidates(currentIndex.threads, threadId, boardId)
}
