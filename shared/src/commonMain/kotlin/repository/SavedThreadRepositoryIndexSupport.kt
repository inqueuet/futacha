package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Index limits shared by the reader and the writer, so every index the writer
 * produces can be read back. 32 MiB covers the entry cap at ~1.6 KB per entry.
 */
internal const val MAX_SAVED_THREAD_INDEX_BYTES = 32L * 1024L * 1024L
internal const val MAX_SAVED_THREAD_INDEX_ENTRIES = 20_000

/**
 * How often a path repository refreshes index.json.backup. Path writes replace
 * index.json atomically (temp file + rename), so a torn primary is not a risk
 * there and the backup only guards against later damage; rewriting both copies
 * on every auto-save doubled the I/O of the largest file the app writes.
 */
internal const val SAVED_THREAD_INDEX_BACKUP_REFRESH_MILLIS = 10L * 60L * 1000L

/** Thrown instead of writing an index the reader would reject as corrupt. */
internal class SavedThreadIndexLimitException(message: String) : IllegalStateException(message)

/**
 * A parsed index and the state of index.json it was read from or written as.
 * [persistedVerified] means the primary file equals [index] and a valid backup
 * exists, so a no-op mutation needs no repair write.
 */
internal class SavedThreadIndexCacheEntry(
    val index: SavedThreadIndex,
    val revision: Long,
    val primarySize: Long,
    var persistedVerified: Boolean
)

/**
 * Process-wide write counter per index lock key. Several repository instances
 * share one folder (UI, worker, maintenance); each checks this before reusing its
 * cached index, and the primary file size catches writes from other processes.
 */
internal object SavedThreadIndexRevisionRegistry {
    private val mutex = Mutex()
    private val revisions = mutableMapOf<String, Long>()

    suspend fun current(key: String): Long = mutex.withLock { revisions[key] ?: 0L }

    suspend fun advance(key: String): Long = mutex.withLock {
        ((revisions[key] ?: 0L) + 1L).also { revisions[key] = it }
    }
}

private suspend fun SavedThreadRepository.readCachedSavedThreadIndexUnlocked(): SavedThreadIndex? {
    val entry = cachedIndexEntry ?: return null
    val revision = SavedThreadIndexRevisionRegistry.current(storageLockKey(indexRelativePath))
    val size = if (revision == entry.revision) {
        runSuspendCatchingPreservingCancellation { getFileSizeAt(indexRelativePath) }.getOrNull()
    } else {
        null
    }
    if (size == null || size != entry.primarySize) {
        cachedIndexEntry = null
        return null
    }
    return entry.index
}

internal suspend fun SavedThreadRepository.readSavedThreadIndexUnlocked(): SavedThreadIndex {
    readCachedSavedThreadIndexUnlocked()?.let { return it }
    // Read before the file: every writer advances it under the same index lock.
    val revision = SavedThreadIndexRevisionRegistry.current(storageLockKey(indexRelativePath))
    var primarySize = -1L
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
        if (!isBackup) primarySize = size
        if (size !in 0L..indexByteLimit) {
            markCorruption(
                path,
                SerializationException("Saved thread index exceeds $indexByteLimit bytes"),
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
        if (jsonString.encodeToByteArray().size.toLong() > indexByteLimit) {
            markCorruption(
                path,
                SerializationException("Saved thread index grew beyond $indexByteLimit bytes while reading"),
                isBackup
            )
            return null
        }
        return try {
            withContext(AppDispatchers.parsing) {
                json.decodeFromString<SavedThreadIndex>(jsonString)
                    .keepNewestSavedThreadIndexEntries(path, indexEntryLimit) { dropped -> scheduleDroppedIndexEntryCleanup(dropped) }
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
        if (!isBackup) primarySize = size
        if (size !in 0L..indexByteLimit) {
            markCorruption(
                relativePath,
                SerializationException("Saved thread index exceeds $indexByteLimit bytes"),
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
        if (jsonString.encodeToByteArray().size.toLong() > indexByteLimit) {
            markCorruption(
                relativePath,
                SerializationException("Saved thread index grew beyond $indexByteLimit bytes while reading"),
                isBackup
            )
            return null
        }
        return try {
            withContext(AppDispatchers.parsing) {
                json.decodeFromString<SavedThreadIndex>(jsonString)
                    .keepNewestSavedThreadIndexEntries(relativePath, indexEntryLimit) { dropped -> scheduleDroppedIndexEntryCleanup(dropped) }
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
        return sanitizeAndRepairSavedThreadIndexUnlocked(primary).also { repaired ->
            if (primarySize > 0L) {
                cachedIndexEntry = SavedThreadIndexCacheEntry(
                    index = repaired,
                    revision = revision,
                    primarySize = primarySize,
                    persistedVerified = false
                )
            }
        }
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

/**
 * Older builds wrote indexes without an entry cap. Such an index is valid data, not
 * corruption: keep the newest entries so list/add/delete keep working.
 */
private fun SavedThreadIndex.keepNewestSavedThreadIndexEntries(
    path: String,
    maxEntries: Int,
    onDropped: (List<SavedThread>) -> Unit
): SavedThreadIndex {
    if (threads.size <= maxEntries) return this
    Logger.w(
        "SavedThreadRepository",
        "Saved thread index '$path' has ${threads.size} entries; keeping the newest $maxEntries"
    )
    val sorted = threads.sortedByDescending { it.savedAt }
    val kept = sorted.take(maxEntries)
    onDropped(sorted.drop(maxEntries))
    return copy(threads = kept, totalSize = kept.safeSavedThreadTotalSize())
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

/**
 * Writes the index. [forceBackup] refreshes index.json.backup even when a path
 * repository refreshed it recently: user deletions must not come back from an
 * older backup.
 */
internal suspend fun SavedThreadRepository.saveSavedThreadIndexUnlocked(
    index: SavedThreadIndex,
    forceBackup: Boolean = false
) {
    suspend fun writeIndexPayload(relativePath: String, jsonString: String) {
        if (useSaveLocationApi) {
            fileSystem.writeString(resolvedSaveLocation, relativePath, jsonString).getOrThrow()
        } else {
            fileSystem.writeString(buildStoragePath(relativePath), jsonString).getOrThrow()
        }
    }

    if (index.threads.size > indexEntryLimit) {
        throw SavedThreadIndexLimitException(
            "保存済みスレッドが上限（${indexEntryLimit}件）に達しました。不要な保存を削除してください"
        )
    }
    ensureSavedThreadBaseDirectoryPreparedUnlocked()
    val jsonString = withContext(AppDispatchers.parsing) {
        json.encodeToString(index).also { encoded ->
            // UTF-8 is at most 3 bytes per UTF-16 unit; only measure when it could exceed.
            if (encoded.length * 3L > indexByteLimit &&
                encoded.encodeToByteArray().size.toLong() > indexByteLimit
            ) {
                throw SavedThreadIndexLimitException(
                    "保存済みスレッドの一覧が大きすぎます。不要な保存を削除してください"
                )
            }
        }
    }
    val nowMillis = Clock.System.now().toEpochMilliseconds()
    // Location-API (SAF / bookmark) writes are not atomic, so the backup written
    // first is what survives a torn primary: keep writing both there.
    val writeBackup = useSaveLocationApi ||
        forceBackup ||
        lastIndexBackupWriteMillis == 0L ||
        hasEpochIntervalElapsed(nowMillis, lastIndexBackupWriteMillis, SAVED_THREAD_INDEX_BACKUP_REFRESH_MILLIS)
    val backupPath = "$indexRelativePath.backup"
    suspend fun writePayloads() {
        if (writeBackup) writeIndexPayload(backupPath, jsonString)
        writeIndexPayload(indexRelativePath, jsonString)
    }
    cachedIndexEntry = null
    var revision = 0L
    try {
        runSuspendCatchingPreservingCancellation {
            writePayloads()
        }.getOrElse { firstError ->
            isBaseDirectoryPrepared = false
            ensureSavedThreadBaseDirectoryPreparedUnlocked()
            runSuspendCatchingPreservingCancellation {
                writePayloads()
            }.getOrElse { retryError ->
                throw Exception(
                    "Failed to persist index after directory re-prepare. first=${firstError.message}, retry=${retryError.message}",
                    retryError
                )
            }
        }
    } finally {
        // Also after a failure: a partial write must invalidate every instance's cache.
        revision = withContext(NonCancellable) {
            SavedThreadIndexRevisionRegistry.advance(storageLockKey(indexRelativePath))
        }
    }
    if (writeBackup) lastIndexBackupWriteMillis = nowMillis
    val writtenSize = runSuspendCatchingPreservingCancellation { getFileSizeAt(indexRelativePath) }.getOrNull()
    if (writtenSize != null && writtenSize > 0L) {
        cachedIndexEntry = SavedThreadIndexCacheEntry(
            index = index,
            revision = revision,
            primarySize = writtenSize,
            persistedVerified = true
        )
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
