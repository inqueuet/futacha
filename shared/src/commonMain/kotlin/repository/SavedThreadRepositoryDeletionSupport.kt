package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.service.ThreadStorageLockRegistry
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

internal const val OPERATION_BACKUP_FILE_PREFIX = "index.json."
internal const val OPERATION_BACKUP_THREAD_DELETE_SUFFIX = ".thread_delete.backup"
internal const val OPERATION_BACKUP_ALL_DELETE_SUFFIX = ".all_delete.backup"
internal const val OPERATION_BACKUP_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
internal const val OPERATION_BACKUP_CLEANUP_MIN_INTERVAL_MILLIS = 15L * 60L * 1000L
internal const val OPERATION_BACKUP_CLEANUP_MAX_DELETIONS_PER_RUN = 24
internal const val OPERATION_BACKUP_CLEANUP_ERROR_LOG_LIMIT = 3
internal const val STORAGE_LOCK_WAIT_TIMEOUT_MILLIS = 15_000L

internal data class SavedThreadDeletePlan(
    val backupIndexPath: String,
    val backupIndexJson: String,
    val targetStorageIds: Set<String>,
    val cutoffSavedAtByStorageId: Map<String, Long>
)

internal data class SavedThreadStorageDeletionResult(
    val successfullyDeletedStorageIds: Set<String>,
    val deletionErrors: List<Pair<String, Throwable>>
)

internal data class SavedThreadDeleteOperationRequest(
    val backupIndexPath: String,
    val deletionErrorSubjectLabel: String,
    val indexUpdateFailureMessage: String,
    val selectThreadsToDelete: (SavedThreadIndex) -> List<SavedThread>
)

internal fun buildSavedThreadDeletePlan(
    currentIndex: SavedThreadIndex,
    threadsToDelete: List<SavedThread>,
    backupIndexPath: String,
    encodeIndex: (SavedThreadIndex) -> String
): SavedThreadDeletePlan? {
    if (threadsToDelete.isEmpty()) return null
    val cutoffSavedAtByStorageId = threadsToDelete
        .groupBy { resolveSavedThreadStorageId(it) }
        .mapValues { (_, threads) -> threads.maxOf { it.savedAt } }
    return SavedThreadDeletePlan(
        backupIndexPath = backupIndexPath,
        backupIndexJson = encodeIndex(currentIndex),
        targetStorageIds = cutoffSavedAtByStorageId.keys,
        cutoffSavedAtByStorageId = cutoffSavedAtByStorageId
    )
}

internal fun filterThreadsAfterSavedThreadDeletion(
    threads: List<SavedThread>,
    successfullyDeletedStorageIds: Set<String>,
    cutoffSavedAtByStorageId: Map<String, Long>
): List<SavedThread> {
    if (successfullyDeletedStorageIds.isEmpty()) return threads
    return threads.filterNot { thread ->
        val storageId = resolveSavedThreadStorageId(thread)
        val cutoffSavedAt = cutoffSavedAtByStorageId[storageId] ?: return@filterNot false
        storageId in successfullyDeletedStorageIds && thread.savedAt <= cutoffSavedAt
    }
}

internal fun buildSavedThreadDeletionErrorMessage(
    deletionErrors: List<Pair<String, Throwable>>,
    subjectLabel: String
): String? {
    if (deletionErrors.isEmpty()) return null
    val detail = deletionErrors.joinToString("\n") { (storageId, error) ->
        "$storageId: ${error.message}"
    }
    return "Failed to delete ${deletionErrors.size} $subjectLabel:\n$detail"
}

internal suspend fun SavedThreadRepository.executeSavedThreadDeleteOperation(
    request: SavedThreadDeleteOperationRequest
) {
    val plan = deleteMutex.withLock {
        withIndexLock {
            val currentIndex = readSavedThreadIndexUnlocked()
            buildSavedThreadDeletePlan(
                currentIndex = currentIndex,
                threadsToDelete = request.selectThreadsToDelete(currentIndex),
                backupIndexPath = request.backupIndexPath,
                encodeIndex = json::encodeToString
            )
        }
    } ?: return
    writeStringAt(plan.backupIndexPath, plan.backupIndexJson).getOrThrow()

    var keepBackup = false
    var deletionResult: SavedThreadStorageDeletionResult? = null
    try {
        deletionResult = executeSavedThreadDeletePlan(
            plan = plan,
            storageLockWaitTimeoutMillis = STORAGE_LOCK_WAIT_TIMEOUT_MILLIS
        )
        if (deletionResult.successfullyDeletedStorageIds.isNotEmpty()) {
            deleteMutex.withLock {
                withIndexLock {
                    val updatedIndex = buildUpdatedIndexUnlocked { threads ->
                        filterThreadsAfterSavedThreadDeletion(
                            threads = threads,
                            successfullyDeletedStorageIds = deletionResult.successfullyDeletedStorageIds,
                            cutoffSavedAtByStorageId = plan.cutoffSavedAtByStorageId
                        )
                    }
                    try {
                        saveSavedThreadIndexUnlocked(updatedIndex)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        throw Exception(request.indexUpdateFailureMessage, e)
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        keepBackup = true
        throw e
    } catch (e: Throwable) {
        keepBackup = true
        throw e
    } finally {
        if (deletionResult?.deletionErrors?.isNotEmpty() == true) {
            keepBackup = true
        }
        finalizeDeleteBackup(
            backupPath = plan.backupIndexPath,
            keepBackup = keepBackup
        )
    }

    buildSavedThreadDeletionErrorMessage(
        deletionErrors = deletionResult.deletionErrors,
        subjectLabel = request.deletionErrorSubjectLabel
    )?.let { throw Exception(it) }
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

internal suspend fun SavedThreadRepository.executeSavedThreadDeletePlan(
    plan: SavedThreadDeletePlan,
    storageLockWaitTimeoutMillis: Long
): SavedThreadStorageDeletionResult {
    val deletionErrors = mutableListOf<Pair<String, Throwable>>()
    val successfullyDeletedStorageIds = mutableSetOf<String>()

    plan.targetStorageIds.forEach { threadPath ->
        coroutineContext.ensureActive()
        yield()
        val deleteResult = withTimeoutOrNull(storageLockWaitTimeoutMillis) {
            ThreadStorageLockRegistry.withStorageLock(storageLockKey(threadPath)) {
                deletePath(threadPath)
            }
        } ?: Result.failure(
            IllegalStateException("Timed out waiting for storage lock: $threadPath")
        )
        val deleteError = deleteResult.exceptionOrNull()
        if (deleteResult.isSuccess || isPathAlreadyDeleted(deleteError)) {
            successfullyDeletedStorageIds.add(threadPath)
        } else {
            deletionErrors.add(
                threadPath to (deleteError ?: Exception("Failed to delete thread directory: $threadPath"))
            )
        }
    }

    return SavedThreadStorageDeletionResult(
        successfullyDeletedStorageIds = successfullyDeletedStorageIds,
        deletionErrors = deletionErrors
    )
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
