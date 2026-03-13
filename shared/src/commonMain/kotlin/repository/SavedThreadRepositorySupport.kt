package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.util.Logger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import com.valoser.futacha.shared.service.buildLegacyThreadStorageId
import com.valoser.futacha.shared.service.buildThreadStorageId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.withLock

internal const val OPERATION_BACKUP_FILE_PREFIX = "index.json."
internal const val OPERATION_BACKUP_THREAD_DELETE_SUFFIX = ".thread_delete.backup"
internal const val OPERATION_BACKUP_ALL_DELETE_SUFFIX = ".all_delete.backup"
internal const val OPERATION_BACKUP_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
internal const val OPERATION_BACKUP_CLEANUP_MIN_INTERVAL_MILLIS = 15L * 60L * 1000L
internal const val OPERATION_BACKUP_CLEANUP_MAX_DELETIONS_PER_RUN = 24
internal const val OPERATION_BACKUP_CLEANUP_ERROR_LOG_LIMIT = 3

internal fun List<SavedThread>.safeSavedThreadTotalSize(
    onOverflow: () -> Unit = {}
): Long {
    var total = 0L
    for (thread in this) {
        val newTotal = total + thread.totalSize
        if (newTotal < total) {
            onOverflow()
            return Long.MAX_VALUE
        }
        total = newTotal
    }
    return total
}

internal fun buildSavedThreadIndex(
    threads: List<SavedThread>,
    nowMillis: Long,
    onOverflow: () -> Unit = {}
): SavedThreadIndex {
    return SavedThreadIndex(
        threads = threads,
        totalSize = threads.safeSavedThreadTotalSize(onOverflow),
        lastUpdated = nowMillis
    )
}

internal fun isSameSavedThreadIdentity(
    thread: SavedThread,
    threadId: String,
    boardId: String?
): Boolean {
    if (thread.threadId != threadId) return false
    val normalizedBoardId = boardId?.trim().orEmpty()
    val candidateBoardId = thread.boardId.trim()
    if (normalizedBoardId.isBlank()) return candidateBoardId.isBlank()
    return candidateBoardId.equals(normalizedBoardId, ignoreCase = true)
}

internal fun resolveSavedThreadStorageId(thread: SavedThread): String {
    return thread.storageId
        ?.takeIf { it.isNotBlank() }
        ?: resolveSavedThreadStorageId(thread.threadId, thread.boardId)
}

internal fun resolveSavedThreadStorageId(threadId: String, boardId: String?): String {
    return buildThreadStorageId(boardId, threadId)
}

internal fun resolveLegacySavedThreadStorageId(threadId: String, boardId: String?): String {
    return buildLegacyThreadStorageId(boardId, threadId)
}

internal data class SavedThreadIndexSanitizeResult(
    val index: SavedThreadIndex,
    val droppedDuplicateCount: Int
)

internal fun sanitizeSavedThreadIndex(
    index: SavedThreadIndex,
    nowMillis: Long,
    onOverflow: () -> Unit = {}
): SavedThreadIndexSanitizeResult {
    if (index.threads.isEmpty()) {
        val normalized = if (index.totalSize != 0L) {
            index.copy(totalSize = 0L, lastUpdated = nowMillis)
        } else {
            index
        }
        return SavedThreadIndexSanitizeResult(
            index = normalized,
            droppedDuplicateCount = 0
        )
    }

    val dedupedByStorageId = linkedMapOf<String, SavedThread>()
    index.threads.forEach { thread ->
        val storageId = resolveSavedThreadStorageId(thread)
        val current = dedupedByStorageId[storageId]
        if (current == null || thread.savedAt > current.savedAt) {
            dedupedByStorageId[storageId] = thread
        }
    }
    val normalizedThreads = dedupedByStorageId.values.toList()
    val recalculatedSize = normalizedThreads.safeSavedThreadTotalSize(onOverflow)
    val needsRepair =
        normalizedThreads.size != index.threads.size ||
            recalculatedSize != index.totalSize
    val normalizedIndex = if (needsRepair) {
        SavedThreadIndex(
            threads = normalizedThreads,
            totalSize = recalculatedSize,
            lastUpdated = nowMillis
        )
    } else {
        index
    }
    return SavedThreadIndexSanitizeResult(
        index = normalizedIndex,
        droppedDuplicateCount = index.threads.size - normalizedThreads.size
    )
}

internal fun resolveSavedThreadMetadataCandidates(
    threads: List<SavedThread>,
    threadId: String,
    boardId: String?
): List<String> {
    val latestByStorageId = linkedMapOf<String, SavedThread>()
    threads.forEach { thread ->
        if (!isSameSavedThreadIdentity(thread, threadId, boardId)) return@forEach
        val storageId = resolveSavedThreadStorageId(thread)
        val existing = latestByStorageId[storageId]
        if (existing == null || thread.savedAt > existing.savedAt) {
            latestByStorageId[storageId] = thread
        }
    }
    val fromIndex = latestByStorageId.values
        .asSequence()
        .sortedByDescending { it.savedAt }
        .map { thread -> "${resolveSavedThreadStorageId(thread)}/metadata.json" }
        .toList()

    val fallbackCurrent = "${resolveSavedThreadStorageId(threadId, boardId)}/metadata.json"
    val fallbackLegacyStorageId = "${resolveLegacySavedThreadStorageId(threadId, boardId)}/metadata.json"
    val fallbackLegacy = "$threadId/metadata.json"
    return buildList {
        addAll(fromIndex)
        add(fallbackCurrent)
        if (fallbackLegacyStorageId != fallbackCurrent) {
            add(fallbackLegacyStorageId)
        }
        add(fallbackLegacy)
    }
}

internal suspend fun SavedThreadRepository.buildUpdatedIndexUnlocked(
    transform: (List<SavedThread>) -> List<SavedThread>
): SavedThreadIndex {
    val currentIndex = readSavedThreadIndexUnlocked()
    return buildSavedThreadIndex(
        threads = transform(currentIndex.threads),
        nowMillis = Clock.System.now().toEpochMilliseconds(),
        onOverflow = ::logTotalSizeOverflow
    )
}

internal suspend fun SavedThreadRepository.mutateIndexThreadsUnlocked(
    transform: (List<SavedThread>) -> List<SavedThread>
) {
    saveSavedThreadIndexUnlocked(buildUpdatedIndexUnlocked(transform))
}

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
        // The base directory may have been deleted externally; re-prepare once and retry.
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

internal suspend fun SavedThreadRepository.finalizeDeleteBackup(
    backupPath: String,
    keepBackup: Boolean
) {
    if (keepBackup) {
        Logger.w("SavedThreadRepository", "Keeping backup index for recovery: $backupPath")
        val canonicalBackupPath = "$indexRelativePath.backup"
        readStringAt(backupPath)
            .onSuccess { backupJson ->
                writeStringAt(canonicalBackupPath, backupJson).onFailure { copyError ->
                    Logger.w(
                        "SavedThreadRepository",
                        "Failed to promote delete backup to $canonicalBackupPath: ${copyError.message}"
                    )
                }
            }
            .onFailure { readError ->
                Logger.w(
                    "SavedThreadRepository",
                    "Failed to read delete backup $backupPath for promotion: ${readError.message}"
                )
            }
        return
    }
    deletePath(backupPath).onFailure { error ->
        Logger.w(
            "SavedThreadRepository",
            "Failed to delete temporary backup index $backupPath: ${error.message}"
        )
    }
    cleanupStaleOperationBackups()
}

internal suspend fun SavedThreadRepository.cleanupStaleOperationBackups() {
    if (useSaveLocationApi) return
    val nowMillis = Clock.System.now().toEpochMilliseconds()
    val shouldRun = backupCleanupMutex.withLock {
        if (
            lastOperationBackupCleanupEpochMillis > 0L &&
            nowMillis - lastOperationBackupCleanupEpochMillis < OPERATION_BACKUP_CLEANUP_MIN_INTERVAL_MILLIS
        ) {
            false
        } else {
            lastOperationBackupCleanupEpochMillis = nowMillis
            true
        }
    }
    if (!shouldRun) return
    val cutoffMillis = nowMillis - OPERATION_BACKUP_RETENTION_MILLIS
    val fileNames = try {
        fileSystem.listFiles(baseDirectory)
    } catch (e: CancellationException) {
        throw e
    } catch (error: Throwable) {
        Logger.w(
            "SavedThreadRepository",
            "Failed to list files for backup cleanup in $baseDirectory: ${error.message}"
        )
        return
    }

    val maxTargets = OPERATION_BACKUP_CLEANUP_MAX_DELETIONS_PER_RUN
    val targets = mutableListOf<Pair<String, Long>>()
    var staleBackupCount = 0

    fun insertByTimestampAscending(candidate: Pair<String, Long>) {
        var insertIndex = targets.size
        for (index in targets.indices) {
            if (candidate.second < targets[index].second) {
                insertIndex = index
                break
            }
        }
        targets.add(insertIndex, candidate)
    }

    fileNames.forEach { fileName ->
        val timestamp = extractSavedThreadOperationBackupTimestamp(fileName) ?: return@forEach
        if (timestamp >= cutoffMillis) return@forEach
        staleBackupCount += 1
        val candidate = fileName to timestamp
        if (targets.size < maxTargets) {
            insertByTimestampAscending(candidate)
            return@forEach
        }
        val newestSelectedTimestamp = targets.lastOrNull()?.second ?: return@forEach
        if (timestamp >= newestSelectedTimestamp) return@forEach
        targets.removeAt(targets.lastIndex)
        insertByTimestampAscending(candidate)
    }
    if (staleBackupCount == 0 || targets.isEmpty()) return

    var deleteFailureCount = 0
    targets.forEach { (fileName, _) ->
        deletePath(fileName).onFailure { error ->
            deleteFailureCount += 1
            if (deleteFailureCount > OPERATION_BACKUP_CLEANUP_ERROR_LOG_LIMIT) return@onFailure
            Logger.w(
                "SavedThreadRepository",
                "Failed to delete stale operation backup $fileName: ${error.message}"
            )
        }
    }

    if (deleteFailureCount > OPERATION_BACKUP_CLEANUP_ERROR_LOG_LIMIT) {
        Logger.w(
            "SavedThreadRepository",
            "Suppressed ${deleteFailureCount - OPERATION_BACKUP_CLEANUP_ERROR_LOG_LIMIT} stale backup cleanup errors"
        )
    }
    val remainingStaleCount = staleBackupCount - targets.size
    if (remainingStaleCount > 0) {
        Logger.i(
            "SavedThreadRepository",
            "Stale operation backups remain: $remainingStaleCount (cleanup cap=$OPERATION_BACKUP_CLEANUP_MAX_DELETIONS_PER_RUN)"
        )
    }
}

internal fun extractSavedThreadOperationBackupTimestamp(fileName: String): Long? {
    if (!fileName.startsWith(OPERATION_BACKUP_FILE_PREFIX)) return null
    val suffix = when {
        fileName.endsWith(OPERATION_BACKUP_THREAD_DELETE_SUFFIX) -> OPERATION_BACKUP_THREAD_DELETE_SUFFIX
        fileName.endsWith(OPERATION_BACKUP_ALL_DELETE_SUFFIX) -> OPERATION_BACKUP_ALL_DELETE_SUFFIX
        else -> return null
    }
    val timestampPart = fileName
        .removePrefix(OPERATION_BACKUP_FILE_PREFIX)
        .removeSuffix(suffix)
    return timestampPart.toLongOrNull()
}

internal fun isPathAlreadyDeleted(error: Throwable?): Boolean {
    val message = error?.message?.lowercase().orEmpty()
    if (message.isBlank()) return false
    return message.contains("not found") ||
        message.contains("no such file") ||
        message.contains("does not exist") ||
        message.contains("cannot find")
}
