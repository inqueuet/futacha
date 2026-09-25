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
    private var rootPurgeCutoffMillis = Long.MIN_VALUE
    private val threadPurgeCutoffMillis = mutableMapOf<String, Long>()

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
    private val droppedEntryCleanupScope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private val droppedEntryCleanupMutex = Mutex()
    private val scheduledDroppedEntryStorageIds = mutableSetOf<String>()

    companion object {
        private const val INDEX_LOCK_WAIT_TIMEOUT_MILLIS = 30_000L
        private const val INDEX_LOCK_OPERATION_TIMEOUT_MILLIS = 30_000L
        private const val MAX_THREAD_PURGE_CUTOFFS = 1_024
        private const val MAX_ORPHAN_METADATA_SCAN_ENTRIES = 20_000
        private const val MAX_DROPPED_ENTRY_CLEANUP_PER_READ = 20_000
        private const val DROPPED_ENTRY_CLEANUP_LOCK_TIMEOUT_MILLIS = 15_000L
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
            deleteMutex.withLock {
                mutationMutex.withLock {
                    val existing = loadIndex().threads
                    val indexedStorageIds = existing.map(::resolveSavedThreadStorageId).toSet()
                    val recovered = mutableListOf<SavedThread>()
                    for (child in listFilesAt("").take(MAX_ORPHAN_METADATA_SCAN_ENTRIES)) {
                        // Some test/platform implementations return full paths; only accept direct children.
                        val storageId = child.removePrefix(baseDirectory.trimEnd('/') + "/")
                        if (!isRecoverableSavedThreadDirectory(storageId) || storageId in indexedStorageIds) continue
                        val saved = ThreadStorageLockRegistry.withStorageLockOrNull(
                            storageId = storageLockKey(storageId),
                            waitTimeoutMillis = 1_000L
                        ) {
                            recoverSavedThreadMetadata(storageId)
                        }
                        if (saved != null) recovered += saved
                    }
                    withIndexLock {
                        val current = readSavedThreadIndexUnlocked()
                        val identities = current.threads.map { purgeIdentityKey(it.threadId, it.boardId) }.toMutableSet()
                        val additions = recovered.sortedByDescending { it.savedAt }.filter { saved ->
                            val identity = purgeIdentityKey(saved.threadId, saved.boardId)
                            val cutoff = maxOf(rootPurgeCutoffMillis, threadPurgeCutoffMillis[identity] ?: Long.MIN_VALUE)
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
                    val cutoff = maxOf(
                        rootPurgeCutoffMillis,
                        threadPurgeCutoffMillis[identityKey] ?: Long.MIN_VALUE
                    )
                    if (thread.savedAt <= cutoff) {
                        ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                            deletePath(storageId).getOrThrow()
                        }
                        error("Discarded an auto-save that started before history deletion")
                    }
                    withIndexLock {
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
            val cutoff = maxOf(
                rootPurgeCutoffMillis,
                threadPurgeCutoffMillis[purgeIdentityKey(thread.threadId, thread.boardId)] ?: Long.MIN_VALUE
            )
            if (mutationStartedAtMillis <= cutoff) {
                ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                    deletePath(storageId).getOrThrow()
                }
                error("Discarded an imported snapshot that started before history deletion")
            }
            withIndexLock {
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

            fastCandidates.forEach { path ->
                tryLoadMetadataAt(path)?.let { return@withContext it }
            }

            val metadataCandidates = withIndexLock {
                this@SavedThreadRepository.resolveMetadataCandidatesUnlocked(normalizedThreadId, normalizedBoardId)
            }
            metadataCandidates.forEach { path ->
                tryLoadMetadataAt(path)?.let { return@withContext it }
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
            mutationMutex.withLock {
                threadPurgeCutoffMillis[purgeIdentityKey(threadId, boardId)] =
                    Clock.System.now().toEpochMilliseconds()
                trimThreadPurgeCutoffsLocked()
            }
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
                mutationMutex.withLock {
                    threadPurgeCutoffMillis[purgeIdentityKey(threadId, boardId)] =
                        Clock.System.now().toEpochMilliseconds()
                    trimThreadPurgeCutoffsLocked()
                }
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
                    rootPurgeCutoffMillis = Clock.System.now().toEpochMilliseconds()
                    fileSystem.deleteRecursively(baseDirectory).getOrThrow()
                    isBaseDirectoryPrepared = false
                    withIndexLock {
                        saveSavedThreadIndexUnlocked(
                            SavedThreadIndex(
                                threads = emptyList(),
                                totalSize = 0L,
                                lastUpdated = Clock.System.now().toEpochMilliseconds()
                            ),
                            forceBackup = true
                        )
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
        // Recursive deletes of whole thread folders must not run on the caller's (UI) thread.
        withContext(AppDispatchers.io) { cleanupReplacedSavedThreadStorageOnIo(storageIds) }
    }

    private suspend fun cleanupReplacedSavedThreadStorageOnIo(storageIds: Set<String>) {
        storageIds.forEach { storageId ->
            val cleanupResult = withTimeoutOrNull(15_000L) {
                ThreadStorageLockRegistry.withStorageLock(storageLockKey(storageId)) {
                    deletePath(storageId)
                }
            }
            val error = cleanupResult?.exceptionOrNull()
            if (cleanupResult == null) {
                Logger.w("SavedThreadRepository", "Timed out cleaning replaced saved thread storage: $storageId")
            } else if (error != null && !isPathAlreadyDeleted(error)) {
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

    private fun purgeIdentityKey(threadId: String, boardId: String?): String {
        return autoSaveRetentionKey(threadId, boardId)
    }

    private fun trimThreadPurgeCutoffsLocked() {
        if (threadPurgeCutoffMillis.size <= MAX_THREAD_PURGE_CUTOFFS) return
        threadPurgeCutoffMillis.entries
            .sortedBy { it.value }
            .take(threadPurgeCutoffMillis.size - MAX_THREAD_PURGE_CUTOFFS)
            .forEach { threadPurgeCutoffMillis.remove(it.key) }
    }

    private suspend fun findOrphanStorageIdsForHistoryIdentity(
        threadId: String,
        boardId: String?
    ): Set<String> {
        val targetIdentity = purgeIdentityKey(threadId, boardId)
        // Callers run on the UI scope when a history entry is swiped away, and
        // this may read thousands of metadata files. Only the identity is needed,
        // so skip decoding every post of every saved thread.
        return withContext(AppDispatchers.io) {
            listFilesAt("")
                .take(MAX_ORPHAN_METADATA_SCAN_ENTRIES)
                .mapNotNullTo(linkedSetOf()) { childName ->
                    val storageId = childName.trim().trim('/')
                    if (storageId.isBlank()) return@mapNotNullTo null
                    val identity = readStringAtWithLimit(
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
                    storageId.takeIf {
                        purgeIdentityKey(identity.threadId, identity.boardId) == targetIdentity
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
