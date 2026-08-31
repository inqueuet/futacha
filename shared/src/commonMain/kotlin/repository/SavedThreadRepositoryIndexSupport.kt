package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import kotlin.coroutines.cancellation.CancellationException

private const val MAX_SAVED_THREAD_INDEX_BYTES = 4L * 1024L * 1024L
private const val MAX_SAVED_THREAD_INDEX_ENTRIES = 20_000

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
        if (!fileSystem.exists(path)) return null
        val size = fileSystem.getFileSize(path)
        if (size !in 0L..MAX_SAVED_THREAD_INDEX_BYTES) {
            markCorruption(
                path,
                SerializationException("Saved thread index exceeds $MAX_SAVED_THREAD_INDEX_BYTES bytes"),
                isBackup
            )
            return null
        }
        val jsonString = fileSystem.readString(path).getOrElse { error ->
            if (isPathAlreadyDeleted(error)) {
                return null
            }
            throw IllegalStateException("Failed to read saved thread index at '$path': ${error.message}", error)
        }
        if (jsonString.encodeToByteArray().size.toLong() > MAX_SAVED_THREAD_INDEX_BYTES) {
            markCorruption(
                path,
                SerializationException("Saved thread index grew beyond $MAX_SAVED_THREAD_INDEX_BYTES bytes while reading"),
                isBackup
            )
            return null
        }
        return try {
            withContext(AppDispatchers.parsing) {
                json.decodeFromString<SavedThreadIndex>(jsonString).also { decoded ->
                    require(decoded.threads.size <= MAX_SAVED_THREAD_INDEX_ENTRIES) {
                        "Saved thread index exceeds $MAX_SAVED_THREAD_INDEX_ENTRIES entries"
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            markCorruption(path, e as? SerializationException ?: SerializationException(e.message.orEmpty()), isBackup)
            null
        }
    }

    suspend fun readIndexFromLocation(relativePath: String, isBackup: Boolean): SavedThreadIndex? {
        if (!fileSystem.exists(resolvedSaveLocation, relativePath)) return null
        val size = fileSystem.getFileSize(resolvedSaveLocation, relativePath)
        if (size !in 0L..MAX_SAVED_THREAD_INDEX_BYTES) {
            markCorruption(
                relativePath,
                SerializationException("Saved thread index exceeds $MAX_SAVED_THREAD_INDEX_BYTES bytes"),
                isBackup
            )
            return null
        }
        val jsonString = fileSystem.readString(resolvedSaveLocation, relativePath).getOrElse { error ->
            if (isPathAlreadyDeleted(error)) {
                return null
            }
            throw IllegalStateException("Failed to read saved thread index at '$relativePath': ${error.message}", error)
        }
        if (jsonString.encodeToByteArray().size.toLong() > MAX_SAVED_THREAD_INDEX_BYTES) {
            markCorruption(
                relativePath,
                SerializationException("Saved thread index grew beyond $MAX_SAVED_THREAD_INDEX_BYTES bytes while reading"),
                isBackup
            )
            return null
        }
        return try {
            withContext(AppDispatchers.parsing) {
                json.decodeFromString<SavedThreadIndex>(jsonString).also { decoded ->
                    require(decoded.threads.size <= MAX_SAVED_THREAD_INDEX_ENTRIES) {
                        "Saved thread index exceeds $MAX_SAVED_THREAD_INDEX_ENTRIES entries"
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            markCorruption(
                relativePath,
                e as? SerializationException ?: SerializationException(e.message.orEmpty()),
                isBackup
            )
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
    val droppedInvalidCount = sanitized.droppedInvalidCount
    if (repaired == index) {
        return index
    }
    if (droppedCount > 0) {
        Logger.w(
            "SavedThreadRepository",
            "Repaired index by dropping $droppedCount duplicate thread entries"
        )
    }
    if (droppedInvalidCount > 0) {
        Logger.w(
            "SavedThreadRepository",
            "Repaired index by dropping $droppedInvalidCount invalid thread entries"
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

    ensureSavedThreadBaseDirectoryPreparedUnlocked()
    val jsonString = withContext(AppDispatchers.parsing) {
        json.encodeToString(index)
    }
    val backupPath = "$indexRelativePath.backup"
    runSuspendCatchingPreservingCancellation {
        writeIndexPayload(backupPath, jsonString)
        writeIndexPayload(indexRelativePath, jsonString)
    }.getOrElse { firstError ->
        isBaseDirectoryPrepared = false
        ensureSavedThreadBaseDirectoryPreparedUnlocked()
        runSuspendCatchingPreservingCancellation {
            writeIndexPayload(backupPath, jsonString)
            writeIndexPayload(indexRelativePath, jsonString)
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
