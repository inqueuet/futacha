package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.service.buildThreadStorageLockKey
import com.valoser.futacha.shared.service.ThreadStorageLockRegistry
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.AUTO_SAVE_EVICTION_RECENT_GRACE_MILLIS
import com.valoser.futacha.shared.service.AUTO_SAVE_MAX_TOTAL_BYTES
import com.valoser.futacha.shared.service.AutoSaveRetentionRegistry
import com.valoser.futacha.shared.service.autoSaveRetentionKey
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.serialization.json.Json

/**
 * 保存済みスレッドリポジトリ
 */
class SavedThreadRepository(
    internal val fileSystem: FileSystem,
    internal val baseDirectory: String = MANUAL_SAVE_DIRECTORY,
    baseSaveLocation: SaveLocation? = null
) {
    private data class IndexLockResult<T>(val value: T)

    data class SavedThreadStats(
        val threadCount: Int,
        val totalSize: Long
    )

    // Instance mutex protects local state; ThreadStorageLockRegistry serializes index writes across repository instances.
    private val indexMutex = Mutex()
    private val mutationMutex = Mutex()
    internal val deleteMutex = Mutex()
    internal val backupCleanupMutex = Mutex()
    internal var lastOperationBackupCleanupEpochMillis = 0L
    // Purge cutoffs live in state shared by every instance of this root (see SavedThreadPurgeState).
    private var purgeStateCache: SavedThreadPurgeState? = null

    internal val json = Json {
        ignoreUnknownKeys = true
    }

    internal val resolvedSaveLocation = baseSaveLocation ?: SaveLocation.fromString(baseDirectory)
    internal val useSaveLocationApi = resolvedSaveLocation !is SaveLocation.Path
    internal val indexRelativePath = "index.json"
    /** The app-private history auto-save root, which may drop its oldest saves to stay bounded. */
    internal val isAutoSaveRepository =
        !useSaveLocationApi && baseDirectory.trim().trimEnd('/') == AUTO_SAVE_DIRECTORY
    internal var isBaseDirectoryPrepared = false
    /** Index limits; the reader and writer use the same values. Lowered only by tests. */
    internal var indexEntryLimit = MAX_SAVED_THREAD_INDEX_ENTRIES
    internal var indexByteLimit = MAX_SAVED_THREAD_INDEX_BYTES
    /** Size cap of an auto-save repository ([isAutoSaveRepository]). Lowered only by tests. */
    internal var autoSaveTotalByteLimit = AUTO_SAVE_MAX_TOTAL_BYTES
    /** Parsed index reused while nothing has rewritten index.json; guarded by the index lock. */
    internal var cachedIndexEntry: SavedThreadIndexCacheEntry? = null
    /** When this instance last refreshed index.json.backup; 0 until its first index write. */
    internal var lastIndexBackupWriteMillis = 0L
    internal var replacedStorageCleanupWaitMillis = 15_000L
    private val droppedEntryCleanupScope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private val droppedEntryCleanupMutex = Mutex()
    private val scheduledDroppedEntryStorageIds = mutableSetOf<String>()
    // storageId -> purge identity of the thread its metadata.json describes. Reading every saved
    // thread's metadata (up to 20,000 files of up to 8 MB) on each history swipe made deleting
    // one entry take seconds; a folder's identity never changes, so each is read once.
    private val storageIdentityCache = HashMap<String, String>()
    private val orphanScanMutex = Mutex()

    companion object {
        private const val INDEX_LOCK_WAIT_TIMEOUT_MILLIS = 30_000L
        private const val INDEX_LOCK_OPERATION_TIMEOUT_MILLIS = 30_000L
        private const val MAX_THREAD_PURGE_CUTOFFS = 1_024
        private const val MAX_ORPHAN_METADATA_SCAN_ENTRIES = 20_000
        private const val MAX_DROPPED_ENTRY_CLEANUP_PER_READ = 20_000
        private const val DROPPED_ENTRY_CLEANUP_LOCK_TIMEOUT_MILLIS = 15_000L
        private const val PURGE_RESUME_BATCH_SIZE = 64
    }

    /**
     * Deletes, in the background, the folders of auto-save index entries dropped
     * because an index from an older build exceeded the entry cap; nothing would
     * reference them again. A folder is kept if the index lists it again by then
     * or its thread is being saved. Manual saves are never deleted here: they
     * stay recoverable with [recoverUnindexedThreads].
     */
    internal fun scheduleDroppedIndexEntryCleanup(dropped: List<SavedThread>) {
        if (!isAutoSaveRepository || dropped.isEmpty()) return
        val candidates = dropped
            .asSequence()
            .take(MAX_DROPPED_ENTRY_CLEANUP_PER_READ)
            .map { it to resolveSavedThreadStorageId(it) }
            .toList()
        droppedEntryCleanupScope.launch {
            val fresh = droppedEntryCleanupMutex.withLock {
                candidates.filter { (_, storageId) -> scheduledDroppedEntryStorageIds.add(storageId) }
            }
            fresh.forEach { (thread, storageId) ->
                yield()
                val retained = AutoSaveRetentionRegistry.snapshot()
                if (purgeIdentityKey(thread.threadId, thread.boardId) in retained) return@forEach
                withTimeoutOrNull(DROPPED_ENTRY_CLEANUP_LOCK_TIMEOUT_MILLIS) {
                    ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                        val stillIndexed = runSuspendCatchingNonCancellation {
                            withIndexLock {
                                readSavedThreadIndexUnlocked().threads.any { resolveSavedThreadStorageId(it) == storageId }
                            }
                        }.getOrDefault(true)
                        if (!stillIndexed) {
                            deletePath(storageId).exceptionOrNull()?.let { error ->
                                if (!isPathAlreadyDeleted(error)) {
                                    Logger.w("SavedThreadRepository", "Failed to delete dropped auto-save $storageId: ${error.message}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * インデックスを読み込み
     */
    suspend fun loadIndex(): SavedThreadIndex = withIndexLock {
        this@SavedThreadRepository.readSavedThreadIndexUnlocked()
    }

    /** Explicit manual-save recovery; never run this for history auto-save repositories. */
    suspend fun recoverUnindexedThreads(): Result<Int> = runSuspendCatchingNonCancellation {
        withContext(AppDispatchers.io) {
            resumeInterruptedPurgeIfNeeded()
            // Scan without holding the mutation locks: on a SAF folder the scan costs one
            // provider query per saved thread, and holding the locks blocked every save and
            // delete meanwhile. The batch context lists each folder once instead of once per
            // lookup (which was O(folders × children)).
            val candidates = withContext(fileSystem.saveBatchContext()) {
                val indexedStorageIds = loadIndex().threads.map(::resolveSavedThreadStorageId).toSet()
                val found = mutableListOf<SavedThread>()
                for (child in listFilesAt("").take(MAX_ORPHAN_METADATA_SCAN_ENTRIES)) {
                    // Some test/platform implementations return full paths; only accept direct children.
                    val storageId = child.removePrefix(baseDirectory.trimEnd('/') + "/")
                    if (!isRecoverableSavedThreadDirectory(storageId) || storageId in indexedStorageIds) continue
                    if (storageId == indexRelativePath || storageId.startsWith("$indexRelativePath.")) continue
                    val saved = ThreadStorageLockRegistry.withStorageLockOrNull(
                        storageId = storageLockKey(storageId),
                        waitTimeoutMillis = 1_000L
                    ) {
                        recoverSavedThreadMetadata(storageId)
                    }
                    if (saved != null) found += saved
                }
                found
            }
            if (candidates.isEmpty()) return@withContext 0
            deleteMutex.withLock {
                mutationMutex.withLock {
                    // Re-check only the few candidates under the locks, without cached
                    // listings, so a thread deleted during the scan is not resurrected.
                    val recovered = candidates.filter { saved ->
                        val storageId = saved.storageId ?: return@filter false
                        existsAt("$storageId/metadata.json")
                    }
                    // A purge interrupted by a failure or a kill leaves folders behind
                    // with an empty index; its persisted cutoff keeps them deleted.
                    val rootCutoff = maxOf(rootPurgeCutoffMillis(), readPersistedPurgeCutoffMillis())
                    withIndexLock {
                        val current = readSavedThreadIndexUnlocked()
                        val currentStorageIds = current.threads.map(::resolveSavedThreadStorageId).toSet()
                        val identities = current.threads.map { purgeIdentityKey(it.threadId, it.boardId) }.toMutableSet()
                        val additions = recovered.sortedByDescending { it.savedAt }.filter { saved ->
                            if (resolveSavedThreadStorageId(saved) in currentStorageIds) return@filter false
                            val identity = purgeIdentityKey(saved.threadId, saved.boardId)
                            val cutoff = maxOf(rootCutoff, purgeCutoffMillis(identity))
                            saved.savedAt > cutoff && identities.add(identity)
                        }
                        if (additions.isNotEmpty()) {
                            saveSavedThreadIndexUnlocked(buildSavedThreadIndex(
                                (current.threads + additions).sortedByDescending { it.savedAt },
                                Clock.System.now().toEpochMilliseconds()
                            ))
                        }
                        additions.size
                    }
                }
            }
        }
    }

    /**
     * インデックスを保存
     */
    suspend fun saveIndex(index: SavedThreadIndex): Result<Unit> = runSuspendCatchingNonCancellation {
        mutationMutex.withLock {
            withIndexLock {
                this@SavedThreadRepository.saveSavedThreadIndexUnlocked(index)
            }
        }
    }

    /**
     * スレッドをインデックスに追加
     *
     * FIX: データ整合性保証
     * - リトライロジックで一時的な書き込み失敗に対応
     * - インデックス更新とファイル保存は同じトランザクション内で実行
     * - 失敗時は古いインデックスが保持されるため、整合性が保たれる
     */
    suspend fun addThreadToIndex(thread: SavedThread): Result<Unit> = runSuspendCatchingNonCancellation {
        var lastException: Throwable? = null
        val replacedStorageIds = linkedSetOf<String>()
        repeat(3) { attempt ->
            try {
                // Taken before the index lock: saves in flight are never evicted for size.
                val retainedIdentities = if (isAutoSaveRepository) {
                    AutoSaveRetentionRegistry.snapshot()
                } else {
                    emptySet()
                }
                mutationMutex.withLock {
                    val storageId = resolveSavedThreadStorageId(thread)
                    val identityKey = purgeIdentityKey(thread.threadId, thread.boardId)
                    // Checked under the index lock every instance of this root shares, so a
                    // purge through another instance is either seen here or deletes the
                    // entry after it is written.
                    val discarded = withIndexLock {
                        if (thread.savedAt <= purgeCutoffMillis(identityKey)) return@withIndexLock true
                        replacedStorageIds.clear()
                        val evicted = this@SavedThreadRepository.mutateIndexThreadsReturningEvictedUnlocked { threads ->
                            val newStorageId = resolveSavedThreadStorageId(thread)
                            replacedStorageIds.clear()
                            threads
                                .filter { isSameSavedThreadIdentity(it, thread.threadId, thread.boardId) }
                                .mapTo(replacedStorageIds) { resolveSavedThreadStorageId(it) }
                            replacedStorageIds.remove(newStorageId)
                            val updated = threads
                                .filterNot { isSameSavedThreadIdentity(it, thread.threadId, thread.boardId) }
                                .plus(thread)
                                .sortedByDescending { it.savedAt }
                            if (!isAutoSaveRepository) {
                                updated
                            } else {
                                val nowMillis = Clock.System.now().toEpochMilliseconds()
                                val overLimit = selectSavedThreadsOverSizeLimit(
                                    threads = updated,
                                    maxTotalBytes = autoSaveTotalByteLimit
                                ) { candidate ->
                                    isSameSavedThreadIdentity(candidate, thread.threadId, thread.boardId) ||
                                        purgeIdentityKey(candidate.threadId, candidate.boardId) in retainedIdentities ||
                                        candidate.savedAt in
                                        (nowMillis - AUTO_SAVE_EVICTION_RECENT_GRACE_MILLIS)..nowMillis
                                }
                                if (overLimit.isNotEmpty()) {
                                    Logger.i(
                                        "SavedThreadRepository",
                                        "Evicting ${overLimit.size} oldest auto-saves beyond the $autoSaveTotalByteLimit-byte limit"
                                    )
                                    overLimit.mapTo(replacedStorageIds) { resolveSavedThreadStorageId(it) }
                                    replacedStorageIds.remove(newStorageId)
                                }
                                updated.filterNot { it in overLimit }
                            }
                        }
                        evicted.mapTo(replacedStorageIds) { resolveSavedThreadStorageId(it) }
                        replacedStorageIds.remove(resolveSavedThreadStorageId(thread))
                        false
                    }
                    if (discarded) {
                        ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                            deletePath(storageId).getOrThrow()
                        }
                        error("Discarded an auto-save that started before history deletion")
                    }
                }
                cleanupReplacedSavedThreadStorage(replacedStorageIds)
                return@runSuspendCatchingNonCancellation
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                lastException = e
                if (attempt < 2) {
                    delay(100L * (attempt + 1))
                }
            }
        }
        throw lastException ?: Exception("Failed to save index after adding thread ${thread.threadId}")
    }

    /**
     * Add an archived/imported snapshot without deleting other snapshots for the same board/thread.
     * Re-importing the same storageId updates that snapshot idempotently.
     */
    suspend fun addThreadSnapshotToIndex(
        thread: SavedThread,
        mutationStartedAtMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): Result<Unit> = runSuspendCatchingNonCancellation {
        mutationMutex.withLock {
            val storageId = resolveSavedThreadStorageId(thread)
            val identityKey = purgeIdentityKey(thread.threadId, thread.boardId)
            // Under the shared index lock, as in addThreadToIndex.
            val evictedStorageIds = withIndexLock {
                if (mutationStartedAtMillis <= purgeCutoffMillis(identityKey)) return@withIndexLock null
                val newStorageId = storageId
                this@SavedThreadRepository.mutateIndexThreadsReturningEvictedUnlocked { threads ->
                    threads
                        .filterNot { resolveSavedThreadStorageId(it) == newStorageId }
                        .plus(thread)
                        .sortedByDescending { it.savedAt }
                }
                    .mapTo(linkedSetOf()) { resolveSavedThreadStorageId(it) }
                    .apply { remove(newStorageId) }
            }
            if (evictedStorageIds == null) {
                ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                    deletePath(storageId).getOrThrow()
                }
                error("Discarded an imported snapshot that started before history deletion")
            }
            evictedStorageIds
        }.let { evictedStorageIds -> cleanupReplacedSavedThreadStorage(evictedStorageIds) }
    }

    /**
     * スレッドをインデックスから削除
     */
    suspend fun removeThreadFromIndex(threadId: String, boardId: String? = null): Result<Unit> = runSuspendCatchingNonCancellation {
        mutationMutex.withLock {
            withIndexLock {
                this@SavedThreadRepository.mutateIndexThreadsUnlocked { threads ->
                    threads.filterNot {
                        isSameSavedThreadIdentity(it, threadId, boardId)
                    }
                }
            }
        }
    }

    /**
     * スレッドメタデータを読み込み
     */
    suspend fun loadThreadMetadata(threadId: String, boardId: String? = null): Result<SavedThreadMetadata> = runSuspendCatchingNonCancellation {
        withContext(AppDispatchers.io) {
            val normalizedThreadId = threadId.trim()
            if (normalizedThreadId.isBlank()) {
                throw IllegalArgumentException("threadId must not be blank")
            }
            val normalizedBoardId = boardId?.trim()?.takeIf { it.isNotBlank() }
            resumeInterruptedPurgeIfNeeded()
            val triedPaths = linkedSetOf<String>()
            var lastError: Throwable? = null

            suspend fun tryLoadMetadataBackupAt(path: String): SavedThreadMetadata? {
                if (!path.endsWith("/metadata.json")) return null
                val backupPath = "$path.backup"
                if (!triedPaths.add(backupPath)) return null
                val backupJson = this@SavedThreadRepository.readStringAtWithLimit(
                    backupPath,
                    MAX_SAVED_THREAD_METADATA_BYTES
                ).getOrElse { error ->
                    lastError = error
                    return null
                }
                return runSuspendCatchingNonCancellation {
                    withContext(AppDispatchers.parsing) {
                        requireSavedThreadMetadataWithinLimits(
                            json.decodeFromString<SavedThreadMetadata>(backupJson)
                        )
                    }
                }.getOrElse { error ->
                    lastError = error
                    null
                }
            }

            suspend fun tryLoadMetadataAt(path: String): SavedThreadMetadata? {
                if (!triedPaths.add(path)) return null
                val jsonString = this@SavedThreadRepository.readStringAtWithLimit(
                    path,
                    MAX_SAVED_THREAD_METADATA_BYTES
                ).getOrElse { error ->
                    lastError = error
                    return tryLoadMetadataBackupAt(path)
                }
                val metadata = runSuspendCatchingNonCancellation {
                    withContext(AppDispatchers.parsing) {
                        requireSavedThreadMetadataWithinLimits(
                            json.decodeFromString<SavedThreadMetadata>(jsonString)
                        )
                    }
                }.getOrElse { error ->
                    lastError = error
                    return tryLoadMetadataBackupAt(path)
                }
                return metadata
            }

            val fastCandidates = buildList {
                add("${resolveSavedThreadStorageId(normalizedThreadId, normalizedBoardId)}/metadata.json")
                val legacyStorageId = resolveLegacySavedThreadStorageId(normalizedThreadId, normalizedBoardId)
                if (legacyStorageId != resolveSavedThreadStorageId(normalizedThreadId, normalizedBoardId)) {
                    add("$legacyStorageId/metadata.json")
                }
                add("$normalizedThreadId/metadata.json")
            }

            suspend fun tryLoadLiveMetadataAt(path: String): SavedThreadMetadata? =
                tryLoadMetadataAt(path)?.takeUnless { metadata ->
                    isInterruptedPurgeLeftover(path, metadata).also { leftover ->
                        if (leftover) lastError = IllegalStateException("Saved thread was deleted")
                    }
                }

            fastCandidates.forEach { path ->
                tryLoadLiveMetadataAt(path)?.let { return@withContext it }
            }

            val metadataCandidates = withIndexLock {
                this@SavedThreadRepository.resolveMetadataCandidatesUnlocked(normalizedThreadId, normalizedBoardId)
            }
            metadataCandidates.forEach { path ->
                tryLoadLiveMetadataAt(path)?.let { return@withContext it }
            }

            throw lastError ?: IllegalStateException("Metadata not found for threadId=$threadId boardId=${boardId.orEmpty()}")
        }
    }

    /**
     * スレッドを削除
     */
    suspend fun deleteThread(threadId: String, boardId: String? = null): Result<Unit> = runSuspendCatchingNonCancellation {
        withContext(AppDispatchers.io) {
            this@SavedThreadRepository.executeSavedThreadDeleteOperation(
                buildDeleteThreadOperationRequest(threadId = threadId, boardId = boardId)
            ).let { }
        }
    }

    /** Deletes indexed data plus legacy/orphan paths for one history identity. */
    suspend fun purgeThreadStorage(threadId: String, boardId: String? = null): Result<Unit> =
        runSuspendCatchingNonCancellation {
            val currentStorageId = resolveSavedThreadStorageId(threadId, boardId)
            mutationMutex.withLock { recordThreadPurgeCutoff(purgeIdentityKey(threadId, boardId)) }
            deleteThread(threadId, boardId).getOrThrow()
            val candidates = linkedSetOf(
                currentStorageId,
                resolveLegacySavedThreadStorageId(threadId, boardId),
                threadId.trim()
            ).apply {
                addAll(findOrphanStorageIdsForHistoryIdentity(threadId, boardId))
            }.filter(String::isNotBlank)
            candidates.forEach { storageId ->
                ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                    deletePath(storageId).exceptionOrNull()?.let { error ->
                        if (!isPathAlreadyDeleted(error)) throw error
                    }
                }
            }
        }

    /**
     * [purgeThreadStorage] without scanning every folder's metadata for orphans. Used for
     * background cleanup of many entries, e.g. history dropped by its size limit.
     */
    suspend fun purgeIndexedThreadStorage(threadId: String, boardId: String? = null): Result<Unit> =
        runSuspendCatchingNonCancellation {
            withContext(AppDispatchers.io) {
                mutationMutex.withLock { recordThreadPurgeCutoff(purgeIdentityKey(threadId, boardId)) }
                deleteThread(threadId, boardId).getOrThrow()
                linkedSetOf(
                    resolveSavedThreadStorageId(threadId, boardId),
                    resolveLegacySavedThreadStorageId(threadId, boardId)
                ).filter(String::isNotBlank).forEach { storageId ->
                    ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                        deletePath(storageId).exceptionOrNull()?.let { error ->
                            if (!isPathAlreadyDeleted(error)) throw error
                        }
                    }
                }
            }
        }

    /**
     * [purgeIndexedThreadStorage] for many threads at once: one delete operation and one index
     * rewrite for all of them instead of one per thread. Used when history trimming drops
     * many entries at the same time. Each identity is a (threadId, boardId) pair.
     */
    suspend fun purgeIndexedThreadsStorage(identities: List<Pair<String, String?>>): Result<Unit> =
        runSuspendCatchingNonCancellation {
            if (identities.isEmpty()) return@runSuspendCatchingNonCancellation
            withContext(AppDispatchers.io) {
                mutationMutex.withLock {
                    identities.forEach { (threadId, boardId) ->
                        recordThreadPurgeCutoff(purgeIdentityKey(threadId, boardId))
                    }
                }
                val identitiesByKey = identities.groupBy { (threadId, boardId) -> purgeIdentityKey(threadId, boardId) }
                this@SavedThreadRepository.executeSavedThreadDeleteOperation(
                    SavedThreadDeleteOperationRequest(
                        backupIndexPath =
                            "$indexRelativePath.${Clock.System.now().toEpochMilliseconds()}$OPERATION_BACKUP_THREAD_DELETE_SUFFIX",
                        deletionErrorSubjectLabel = "thread directory(s)",
                        indexUpdateFailureMessage =
                            "Failed to update index after deleting ${identities.size} thread(s). Index may be inconsistent.",
                        selectThreadsToDelete = { index ->
                            index.threads
                                .filter { thread ->
                                    identitiesByKey[purgeIdentityKey(thread.threadId, thread.boardId)]
                                        ?.any { (threadId, boardId) -> isSameSavedThreadIdentity(thread, threadId, boardId) } == true
                                }
                                .sortedByDescending { it.savedAt }
                        }
                    )
                )
                identities
                    .flatMap { (threadId, boardId) ->
                        listOf(
                            resolveSavedThreadStorageId(threadId, boardId),
                            resolveLegacySavedThreadStorageId(threadId, boardId)
                        )
                    }
                    .filter(String::isNotBlank)
                    .distinct()
                    .forEach { storageId ->
                        ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                            deletePath(storageId).exceptionOrNull()?.let { error ->
                                if (!isPathAlreadyDeleted(error)) throw error
                            }
                        }
                    }
            }
        }

    /**
     * スレッドを削除し、削除後のインデックスを返す。
     */
    suspend fun deleteThreadAndLoadIndex(threadId: String, boardId: String? = null): Result<SavedThreadIndex> =
        runSuspendCatchingNonCancellation {
            withContext(AppDispatchers.io) {
                this@SavedThreadRepository.executeSavedThreadDeleteOperation(
                    buildDeleteThreadOperationRequest(threadId = threadId, boardId = boardId)
                )
            }
        }

    /**
     * すべてのスレッドを削除
     */
    suspend fun deleteAllThreads(): Result<Unit> = runSuspendCatchingNonCancellation {
        withContext(AppDispatchers.io) {
            this@SavedThreadRepository.executeSavedThreadDeleteOperation(
                SavedThreadDeleteOperationRequest(
                    backupIndexPath = "$indexRelativePath.${Clock.System.now().toEpochMilliseconds()}$OPERATION_BACKUP_ALL_DELETE_SUFFIX",
                    deletionErrorSubjectLabel = "thread(s)",
                    indexUpdateFailureMessage =
                        "Failed to update index after deleting all threads. Index may be inconsistent.",
                    selectThreadsToDelete = { index -> index.threads }
                )
            ).let { }
        }
    }

    /**
     * Removes the complete repository root, including orphan generations,
     * corrupt/backup indexes and staging directories that are not represented
     * by the current index. Intended for app-owned history payload roots only.
     */
    suspend fun purgeAllStorage(): Result<Unit> = runSuspendCatchingNonCancellation {
        require(!useSaveLocationApi) {
            "Whole-root purge is only supported for app-owned path repositories"
        }
        withContext(AppDispatchers.io) {
            deleteMutex.withLock {
                mutationMutex.withLock {
                    val cutoffMillis = Clock.System.now().toEpochMilliseconds()
                    // Everything in the root now is to be deleted. The list is kept outside
                    // the root until the whole root is gone, so a purge cut short by a
                    // failure or a kill is finished later instead of its threads coming back.
                    val leftovers = listRootChildrenForPurge()
                    val state = purgeState()
                    // Under the marker lock, so a resume of an older purge finishing now
                    // cannot write over or delete this purge's marker (G4-3).
                    val generation = state.markerMutex.withLock {
                        val generation = state.mutex.withLock {
                            state.rootCutoffMillis = cutoffMillis
                            state.rootCutoffMark = kotlin.time.TimeSource.Monotonic.markNow()
                            state.pendingLeftovers.clear()
                            state.pendingLeftovers.addAll(leftovers)
                            state.pendingLeftoverCutoffMillis = cutoffMillis
                            state.interruptedPurgeResumeChecked = true
                            ++state.markerGeneration
                        }
                        fileSystem.writeString(
                            purgeMarkerPath,
                            encodeSavedThreadPurgeMarker(SavedThreadPurgeMarker(cutoffMillis, leftovers))
                        )
                            .onSuccess { deleteLegacyPurgeMarker() }
                            .exceptionOrNull()
                            ?.let { Logger.w("SavedThreadRepository", "Failed to record purge cutoff: ${it.message}") }
                        generation
                    }
                    suspend fun clearIndex() {
                        isBaseDirectoryPrepared = false
                        withIndexLock {
                            saveSavedThreadIndexUnlocked(SavedThreadIndex(emptyList(), 0L,
                                Clock.System.now().toEpochMilliseconds()), forceBackup = true)
                        }
                    }
                    clearIndex()
                    var purged = false
                    try {
                        val started = kotlin.time.TimeSource.Monotonic.markNow()
                        var attempts = 0
                        while (true) {
                            val result = fileSystem.deleteRecursively(baseDirectory)
                            if (result.isSuccess) {
                                state.markerMutex.withLock {
                                    // A newer purge through another instance keeps its own marker.
                                    // The leftovers stay hidden in this process: a read may have
                                    // loaded one just before the delete. A later save has a newer savedAt.
                                    if (state.mutex.withLock { state.markerGeneration } == generation) {
                                        fileSystem.delete(purgeMarkerPath)
                                    }
                                }
                                purged = true
                                break
                            }
                            val failure = result.exceptionOrNull()!!
                            val chunkLimit = failure.message.orEmpty().let {
                                it.contains("Too many files") || it.contains("Timed out while deleting file tree")
                            }
                            if (!chunkLimit || ++attempts >= 128 || started.elapsedNow().inWholeMilliseconds >= 120_000L) {
                                throw failure
                            }
                            yield()
                        }
                    } finally {
                        withContext(NonCancellable) {
                            // The next metadata read or recovery tries the rest once more.
                            if (!purged) {
                                state.mutex.withLock {
                                    if (state.markerGeneration == generation) state.interruptedPurgeResumeChecked = false
                                }
                            }
                            clearIndex()
                        }
                    }
                }
            }
        }
    }

    /**
     * すべての保存済みスレッドを取得
     */
    suspend fun getAllThreads(): List<SavedThread> = withIndexLock {
        this@SavedThreadRepository.readSavedThreadIndexUnlocked().threads
    }

    /**
     * スレッドが存在するか確認
     */
    suspend fun threadExists(threadId: String, boardId: String? = null): Boolean = withContext(AppDispatchers.io) {
        val threadPaths = withIndexLock {
            val currentIndex = this@SavedThreadRepository.readSavedThreadIndexUnlocked()
            val fromIndex = currentIndex.threads
                .filter { isSameSavedThreadIdentity(it, threadId, boardId) }
                .sortedByDescending { it.savedAt }
                .map { resolveSavedThreadStorageId(it) }
                .distinct()
            if (fromIndex.isNotEmpty()) {
                fromIndex
            } else {
                buildList {
                    val currentStorageId = resolveSavedThreadStorageId(threadId = threadId, boardId = boardId)
                    add(currentStorageId)
                    val legacyStorageId = resolveLegacySavedThreadStorageId(threadId = threadId, boardId = boardId)
                    if (legacyStorageId != currentStorageId) {
                        add(legacyStorageId)
                    }
                }
            }
        }

        if (useSaveLocationApi) {
            threadPaths.any { path -> fileSystem.exists(resolvedSaveLocation, path) }
        } else {
            threadPaths.any { path -> fileSystem.exists(buildStoragePath(path)) }
        }
    }

    /**
     * 合計ストレージサイズを取得
     */
    suspend fun getTotalSize(): Long = getStats().totalSize

    /**
     * スレッド数を取得
     */
    suspend fun getThreadCount(): Int = getStats().threadCount

    /**
     * スレッド数と合計サイズを1回のインデックス読み込みで取得
     */
    suspend fun getStats(): SavedThreadStats = withIndexLock {
        val index = this@SavedThreadRepository.readSavedThreadIndexUnlocked()
        SavedThreadStats(
            threadCount = index.threads.size,
            totalSize = index.totalSize
        )
    }

    /** Storage id of the newest indexed save of this thread, or null when none is indexed. */
    suspend fun resolveIndexedStorageId(threadId: String, boardId: String? = null): String? = withIndexLock {
        readSavedThreadIndexUnlocked()
            .threads
            .asSequence()
            .filter { isSameSavedThreadIdentity(it, threadId, boardId) }
            .sortedByDescending { it.savedAt }
            .map { resolveSavedThreadStorageId(it) }
            .firstOrNull()
    }

    /**
     * スレッドHTMLパスを取得
     */
    suspend fun getThreadHtmlPath(threadId: String, boardId: String? = null): String {
        val storageId = withIndexLock {
            readSavedThreadIndexUnlocked()
                .threads
                .asSequence()
                .filter { isSameSavedThreadIdentity(it, threadId, boardId) }
                .sortedByDescending { it.savedAt }
                .map { resolveSavedThreadStorageId(it) }
                .firstOrNull()
        } ?: resolveSavedThreadStorageId(threadId = threadId, boardId = boardId)
        val relativePath = "$storageId/$threadId.htm"
        return if (useSaveLocationApi) {
            relativePath
        } else {
            fileSystem.resolveAbsolutePath(buildStoragePath(relativePath))
        }
    }

    private suspend fun cleanupReplacedSavedThreadStorage(storageIds: Set<String>) {
        if (storageIds.isEmpty()) return
        withContext(AppDispatchers.io) {
            storageIds.forEach { storageId ->
                val completed = withTimeoutOrNull(replacedStorageCleanupWaitMillis) {
                    cleanupReplacedSavedThreadStorageOnIo(setOf(storageId))
                    true
                }
                if (completed == null) {
                    // A busy save must not leave its predecessor orphaned.
                    // Retry outside the caller's bounded completion path.
                    droppedEntryCleanupScope.launch { cleanupReplacedSavedThreadStorageOnIo(setOf(storageId)) }
                }
            }
        }
    }

    private suspend fun cleanupReplacedSavedThreadStorageOnIo(storageIds: Set<String>) {
        storageIds.forEach { storageId ->
            val cleanupResult = runSuspendCatchingNonCancellation {
                ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                    // Another repository may have published this generation
                    // while we waited. Check the index, then delete with only
                    // the storage lock held: a large folder (or a slow SAF
                    // provider) must not block every index reader, and the
                    // index lock's operation timeout must not cut the delete.
                    val stillIndexed = withIndexLock {
                        readSavedThreadIndexUnlocked().threads.any {
                            resolveSavedThreadStorageId(it) == storageId
                        }
                    }
                    if (!stillIndexed) {
                        deletePath(storageId).getOrThrow()
                    }
                }
            }
            val error = cleanupResult.exceptionOrNull()
            if (error != null && !isPathAlreadyDeleted(error)) {
                Logger.w(
                    "SavedThreadRepository",
                    "Failed to clean replaced saved thread storage $storageId: ${error.message}"
                )
            }
        }
    }

    /**
     * スレッド情報を更新
     */
    suspend fun updateThread(thread: SavedThread): Result<Unit> = runSuspendCatchingNonCancellation {
        mutationMutex.withLock {
            withIndexLock {
                this@SavedThreadRepository.mutateIndexThreadsUnlocked { threads ->
                    threads.map {
                        if (isSameSavedThreadIdentity(it, thread.threadId, thread.boardId)) thread else it
                    }
                }
            }
        }
    }

    internal suspend fun <T> withIndexLock(block: suspend () -> T): T = withContext(AppDispatchers.io) {
        val result = ThreadStorageLockRegistry.withStorageLockOrNull(
            storageId = storageLockKey(indexRelativePath),
            waitTimeoutMillis = INDEX_LOCK_WAIT_TIMEOUT_MILLIS
        ) {
            var indexLocked = false
            val acquired = withTimeoutOrNull(INDEX_LOCK_WAIT_TIMEOUT_MILLIS) {
                indexMutex.lock()
                indexLocked = true
                true
            } == true
            if (!acquired) {
                if (indexLocked) {
                    indexMutex.unlock()
                }
                throw IllegalStateException(
                    "Timed out waiting for saved thread index lock after ${INDEX_LOCK_WAIT_TIMEOUT_MILLIS}ms"
                )
            }
            try {
                withTimeoutOrNull(INDEX_LOCK_OPERATION_TIMEOUT_MILLIS) {
                    IndexLockResult(block())
                } ?: throw IllegalStateException(
                    "Timed out while holding saved thread index lock after ${INDEX_LOCK_OPERATION_TIMEOUT_MILLIS}ms"
                )
            } finally {
                indexMutex.unlock()
            }
        }
        if (result != null) {
            return@withContext result.value
        }
        throw IllegalStateException(
            "Timed out waiting for saved thread index lock after ${INDEX_LOCK_WAIT_TIMEOUT_MILLIS}ms"
        )
    }

    internal fun storageLockKey(relativePath: String): String {
        val baseLocationForLock = if (useSaveLocationApi) resolvedSaveLocation else null
        return buildThreadStorageLockKey(
            storageId = relativePath,
            baseDirectory = baseDirectory,
            baseSaveLocation = baseLocationForLock
        )
    }

    /** Records the last [purgeAllStorage] cutoff and leftovers until it completes (see [savedThreadPurgeMarkerPath]). */
    private val purgeMarkerPath: String get() = savedThreadPurgeMarkerPath(baseDirectory)

    private val legacyPurgeMarkerPath: String get() = legacySavedThreadPurgeMarkerPath(baseDirectory)

    private suspend fun deleteLegacyPurgeMarker() {
        if (legacyPurgeMarkerPath == purgeMarkerPath || !fileSystem.exists(legacyPurgeMarkerPath)) return
        fileSystem.delete(legacyPurgeMarkerPath).exceptionOrNull()?.let { error ->
            Logger.w("SavedThreadRepository", "Failed to delete the old purge marker: ${error.message}")
        }
    }

    /**
     * Reads the purge marker, moving one an older build left at [legacyPurgeMarkerPath]
     * into the private location first; the old copy is removed once the new one is
     * written. Callers hold the state's marker lock.
     */
    private suspend fun readPurgeMarkerUnlocked(): SavedThreadPurgeMarker? {
        if (useSaveLocationApi) return null
        if (fileSystem.exists(purgeMarkerPath)) {
            // A marker at the new location supersedes an old one: it lists everything left then.
            deleteLegacyPurgeMarker()
            return fileSystem.readString(purgeMarkerPath).getOrNull()?.let(::decodeSavedThreadPurgeMarker)
        }
        if (legacyPurgeMarkerPath == purgeMarkerPath || !fileSystem.exists(legacyPurgeMarkerPath)) return null
        val encoded = fileSystem.readString(legacyPurgeMarkerPath).getOrNull() ?: return null
        val legacy = decodeSavedThreadPurgeMarker(encoded)
        if (legacy == null) {
            deleteLegacyPurgeMarker()
            return null
        }
        fileSystem.writeString(purgeMarkerPath, encodeSavedThreadPurgeMarker(legacy))
            .onSuccess { deleteLegacyPurgeMarker() }
            .onFailure { Logger.w("SavedThreadRepository", "Failed to move the purge marker: ${it.message}") }
        return legacy
    }

    private suspend fun readPersistedPurgeCutoffMillis(): Long {
        val state = purgeState()
        return state.markerMutex.withLock { readPurgeMarkerUnlocked() }?.cutoffMillis ?: Long.MIN_VALUE
    }

    private suspend fun purgeState(): SavedThreadPurgeState =
        purgeStateCache ?: SavedThreadPurgeRegistry
            .stateFor(fileSystem, storageLockKey(indexRelativePath))
            .also { purgeStateCache = it }

    private suspend fun rootPurgeCutoffMillis(): Long =
        purgeState().let { state ->
            state.mutex.withLock {
                effectivePurgeCutoffMillis(
                    state.rootCutoffMillis,
                    state.rootCutoffMark,
                    Clock.System.now().toEpochMilliseconds()
                )
            }
        }

    /** Saves of [identityKey] started at or before this were deleted and must not be indexed. */
    private suspend fun purgeCutoffMillis(identityKey: String): Long = purgeState().let { state ->
        state.mutex.withLock {
            val nowMillis = Clock.System.now().toEpochMilliseconds()
            val threadCutoff = state.threadCutoffMillis[identityKey]?.let { cutoff ->
                effectivePurgeCutoffMillis(cutoff, state.threadCutoffMarks[identityKey], nowMillis)
            } ?: Long.MIN_VALUE
            maxOf(
                effectivePurgeCutoffMillis(state.rootCutoffMillis, state.rootCutoffMark, nowMillis),
                threadCutoff
            )
        }
    }

    private suspend fun recordThreadPurgeCutoff(identityKey: String) {
        val state = purgeState()
        state.mutex.withLock {
            state.threadCutoffMillis.remove(identityKey)
            state.threadCutoffMillis[identityKey] = Clock.System.now().toEpochMilliseconds()
            state.threadCutoffMarks[identityKey] = kotlin.time.TimeSource.Monotonic.markNow()
            // Insertion order is cutoff order: drop the oldest.
            while (state.threadCutoffMillis.size > MAX_THREAD_PURGE_CUTOFFS) {
                val oldest = state.threadCutoffMillis.keys.first()
                state.threadCutoffMillis.remove(oldest)
                state.threadCutoffMarks.remove(oldest)
            }
        }
    }

    /** Direct children of the root, by name, for the purge marker. */
    private suspend fun listRootChildrenForPurge(): List<String> {
        val prefix = baseDirectory.trimEnd('/') + "/"
        return runSuspendCatchingNonCancellation { fileSystem.listFiles(baseDirectory) }
            .getOrDefault(emptyList())
            .asSequence()
            .map { it.removePrefix(prefix) }
            .filter(::isRecoverableSavedThreadDirectory)
            .distinct()
            .take(MAX_ORPHAN_METADATA_SCAN_ENTRIES)
            .toList()
    }

    /**
     * Finishes a [purgeAllStorage] that a failure or a kill cut short. The read that
     * triggers it only loads the marker, so every leftover is hidden from
     * [loadThreadMetadata] at once (concurrent reads wait for that load, not for the
     * deletes); the leftovers are then deleted in the background (G4-2). Runs once per
     * process, and once more after a purge fails.
     */
    private suspend fun resumeInterruptedPurgeIfNeeded() {
        if (useSaveLocationApi) return
        val state = purgeState()
        if (state.mutex.withLock { state.interruptedPurgeResumeChecked }) return
        state.markerMutex.withLock {
            if (state.mutex.withLock { state.interruptedPurgeResumeChecked }) return
            // Best effort: a failure here must not fail the read that triggered it.
            val persisted = runSuspendCatchingNonCancellation { readPurgeMarkerUnlocked() }
                .onFailure { Logger.w("SavedThreadRepository", "Failed to read the purge marker: ${it.message}") }
                .getOrNull()
            val (marker, generation) = state.mutex.withLock {
                state.interruptedPurgeResumeChecked = true
                // Without a readable marker, what a failed purge of this process listed is still finished.
                val marker = persisted ?: state.pendingLeftovers.takeIf { it.isNotEmpty() }?.let { pending ->
                    SavedThreadPurgeMarker(state.pendingLeftoverCutoffMillis, pending.toList())
                }
                state.pendingLeftovers.clear()
                if (marker != null) {
                    state.pendingLeftovers.addAll(marker.leftovers)
                    state.pendingLeftoverCutoffMillis = marker.cutoffMillis
                }
                marker to state.markerGeneration
            }
            if (marker != null) {
                state.resumeJob = droppedEntryCleanupScope.launch {
                    runSuspendCatchingNonCancellation {
                        finishInterruptedPurge(state, marker, generation, markerPersisted = persisted != null)
                    }.onFailure {
                        Logger.w("SavedThreadRepository", "Failed to finish an interrupted purge: ${it.message}")
                    }
                }
            }
        }
    }

    /** Waits for the background part of [resumeInterruptedPurgeIfNeeded]; for tests. */
    internal suspend fun awaitInterruptedPurgeResume() {
        purgeState().resumeJob?.join()
    }

    /**
     * Deletes what [marker] listed, except the current index files and folders that a
     * save made after it re-used (indexed, busy, or with newer metadata). Holds no
     * repository-wide lock across the deletes: each item takes only its storage lock and
     * briefly the index lock. Stops when a newer purge ([SavedThreadPurgeState.markerGeneration])
     * took over. What still cannot be deleted stays listed and hidden.
     */
    private suspend fun finishInterruptedPurge(
        state: SavedThreadPurgeState,
        marker: SavedThreadPurgeMarker,
        generation: Long,
        markerPersisted: Boolean
    ) {
        suspend fun isCurrent() = state.mutex.withLock { state.markerGeneration == generation }
        val currentIndexFiles = setOf(indexRelativePath, "$indexRelativePath.backup")
        val remaining = mutableListOf<String>()
        val settled = mutableListOf<String>()
        suspend fun publishSettled() {
            if (settled.isEmpty()) return
            state.mutex.withLock {
                if (state.markerGeneration == generation) state.pendingLeftovers.removeAll(settled.toSet())
            }
            settled.clear()
        }
        marker.leftovers.forEachIndexed { position, name ->
            if (position % PURGE_RESUME_BATCH_SIZE == 0) {
                publishSettled()
                yield()
                if (!isCurrent()) return
            }
            // A deleted leftover stays hidden: a read may have loaded its metadata just before
            // the delete. One kept for a later save is shown again; an undeleted one stays listed.
            val outcome = if (name in currentIndexFiles) {
                PurgeLeftoverOutcome.KEPT
            } else {
                ThreadStorageLockRegistry.withStorageLockOrNull(
                    storageId = storageLockKey(name),
                    waitTimeoutMillis = 1_000L
                ) {
                    val indexed = withIndexLock {
                        readSavedThreadIndexUnlocked().threads.any { resolveSavedThreadStorageId(it) == name }
                    }
                    val reusedAfterPurge = indexed || readMetadataSavedAtOrNull(name)
                        ?.let { savedAt -> savedAt > marker.cutoffMillis } == true
                    if (reusedAfterPurge) {
                        PurgeLeftoverOutcome.KEPT
                    } else {
                        val error = deletePath(name).exceptionOrNull()
                        if (error != null && !isPathAlreadyDeleted(error)) {
                            PurgeLeftoverOutcome.UNDELETED
                        } else {
                            PurgeLeftoverOutcome.DELETED
                        }
                    }
                } ?: PurgeLeftoverOutcome.UNDELETED
            }
            when (outcome) {
                PurgeLeftoverOutcome.UNDELETED -> remaining += name
                PurgeLeftoverOutcome.KEPT -> settled += name
                PurgeLeftoverOutcome.DELETED -> Unit
            }
        }
        publishSettled()
        state.markerMutex.withLock {
            if (!isCurrent()) return
            // Another process may have finished or replaced this marker meanwhile.
            val onDisk = readPurgeMarkerUnlocked()
            val unchanged = if (onDisk != null) onDisk.cutoffMillis == marker.cutoffMillis else !markerPersisted
            if (!unchanged) return
            if (remaining.isEmpty()) {
                fileSystem.delete(purgeMarkerPath)
            } else {
                Logger.w("SavedThreadRepository", "Interrupted purge left ${remaining.size} item(s) undeleted")
                fileSystem.writeString(
                    purgeMarkerPath,
                    encodeSavedThreadPurgeMarker(SavedThreadPurgeMarker(marker.cutoffMillis, remaining))
                )
            }
        }
    }

    private suspend fun readMetadataSavedAtOrNull(storageId: String): Long? {
        if (!existsAt("$storageId/metadata.json")) return null
        return runSuspendCatchingNonCancellation {
            val encoded = readStringAtWithLimit("$storageId/metadata.json", MAX_SAVED_THREAD_METADATA_BYTES).getOrThrow()
            json.decodeFromString<SavedThreadMetadata>(encoded).savedAt
        }.getOrNull()
    }

    /**
     * Whether [metadata] read from [path] belongs to a not yet deleted leftover of an
     * interrupted purge, and so to a thread the user already deleted.
     */
    private suspend fun isInterruptedPurgeLeftover(path: String, metadata: SavedThreadMetadata): Boolean {
        val storageId = path.substringBefore('/')
        val state = purgeState()
        val cutoff = state.mutex.withLock {
            if (storageId !in state.pendingLeftovers) return false
            state.pendingLeftoverCutoffMillis
        }
        if (metadata.savedAt > cutoff) return false
        return withIndexLock {
            readSavedThreadIndexUnlocked().threads.none { resolveSavedThreadStorageId(it) == storageId }
        }
    }

    private fun purgeIdentityKey(threadId: String, boardId: String?): String {
        return autoSaveRetentionKey(threadId, boardId)
    }


    private suspend fun findOrphanStorageIdsForHistoryIdentity(
        threadId: String,
        boardId: String?
    ): Set<String> {
        val targetIdentity = purgeIdentityKey(threadId, boardId)
        // Callers run on the UI scope when a history entry is swiped away, and
        // this may read thousands of metadata files. Only the identity is needed,
        // so skip decoding every post of every saved thread, and read each folder's
        // metadata once: later scans (and scans queued behind this one) use the cache.
        return orphanScanMutex.withLock {
            withContext(AppDispatchers.io + fileSystem.saveBatchContext()) {
                val children = listFilesAt("")
                    .take(MAX_ORPHAN_METADATA_SCAN_ENTRIES)
                    .map { it.trim().trim('/') }
                    .filter { it.isNotBlank() && !it.startsWith(indexRelativePath) }
                // Folders deleted since the last scan leave the cache.
                storageIdentityCache.keys.retainAll(children.toSet())
                children.mapNotNullTo(linkedSetOf()) { storageId ->
                    val identity = storageIdentityCache[storageId] ?: run {
                        val probed = readStringAtWithLimit(
                            "$storageId/metadata.json",
                            MAX_SAVED_THREAD_METADATA_BYTES
                        )
                            .getOrNull()
                            ?.let { encoded ->
                                runCatching {
                                    json.decodeFromString(SavedThreadIdentityProbe.serializer(), encoded)
                                }.getOrNull()
                            }
                            ?.takeIf { it.threadId.isNotBlank() }
                            ?: return@mapNotNullTo null
                        purgeIdentityKey(probed.threadId, probed.boardId).also {
                            if (storageIdentityCache.size < MAX_ORPHAN_METADATA_SCAN_ENTRIES) {
                                storageIdentityCache[storageId] = it
                            }
                        }
                    }
                    storageId.takeIf { identity == targetIdentity }
                }
            }
        }
    }

    private fun buildDeleteThreadOperationRequest(
        threadId: String,
        boardId: String?
    ): SavedThreadDeleteOperationRequest {
        return SavedThreadDeleteOperationRequest(
            backupIndexPath = "$indexRelativePath.${Clock.System.now().toEpochMilliseconds()}$OPERATION_BACKUP_THREAD_DELETE_SUFFIX",
            deletionErrorSubjectLabel = "thread directory(s)",
            indexUpdateFailureMessage =
                "Failed to update index after deleting thread $threadId. Index may be inconsistent.",
            selectThreadsToDelete = { index ->
                index.threads
                    .filter { isSameSavedThreadIdentity(it, threadId, boardId) }
                    .sortedByDescending { it.savedAt }
            }
        )
    }

    private suspend inline fun <T> runSuspendCatchingNonCancellation(
        crossinline block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    internal fun logTotalSizeOverflow() {
        Logger.w("SavedThreadRepository", "Total size overflow detected, capping at Long.MAX_VALUE")
    }
}

/** The two metadata fields the orphan scan compares; the rest is ignored. */
@kotlinx.serialization.Serializable
private data class SavedThreadIdentityProbe(
    val threadId: String = "",
    val boardId: String? = null
)

private enum class PurgeLeftoverOutcome { DELETED, KEPT, UNDELETED }
