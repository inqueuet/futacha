package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.service.buildThreadStorageLockKey
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    data class SavedThreadStats(
        val threadCount: Int,
        val totalSize: Long
    )

    // FIX: メモリリーク防止 - 静的レジストリではなく、インスタンス毎のMutexを使用
    private val indexMutex = Mutex()
    private val mutationMutex = Mutex()
    internal val deleteMutex = Mutex()
    internal val backupCleanupMutex = Mutex()
    internal var lastOperationBackupCleanupEpochMillis = 0L

    internal val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    internal val resolvedSaveLocation = baseSaveLocation ?: SaveLocation.fromString(baseDirectory)
    internal val useSaveLocationApi = resolvedSaveLocation !is SaveLocation.Path
    internal val indexRelativePath = "index.json"
    internal var isBaseDirectoryPrepared = false

    /**
     * インデックスを読み込み
     */
    suspend fun loadIndex(): SavedThreadIndex = withIndexLock {
        this@SavedThreadRepository.readSavedThreadIndexUnlocked()
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
        repeat(3) { attempt ->
            try {
                mutationMutex.withLock {
                    withIndexLock {
                        this@SavedThreadRepository.mutateIndexThreadsUnlocked { threads ->
                            threads
                                .filterNot { isSameSavedThreadIdentity(it, thread.threadId, thread.boardId) }
                                .plus(thread)
                                .sortedByDescending { it.savedAt }
                        }
                    }
                }
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

            suspend fun tryLoadMetadataAt(path: String): SavedThreadMetadata? {
                if (!triedPaths.add(path)) return null
                val jsonString = this@SavedThreadRepository.readStringAt(path).getOrElse { error ->
                    lastError = error
                    return null
                }
                val metadata = runCatching {
                    json.decodeFromString<SavedThreadMetadata>(jsonString)
                }.getOrElse { error ->
                    lastError = error
                    return null
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
                SavedThreadDeleteOperationRequest(
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
            )
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

    /**
     * スレッドHTMLパスを取得
     */
    fun getThreadHtmlPath(threadId: String, boardId: String? = null): String {
        val storageId = resolveSavedThreadStorageId(threadId = threadId, boardId = boardId)
        val relativePath = "$storageId/$threadId.htm"
        return if (useSaveLocationApi) {
            relativePath
        } else {
            fileSystem.resolveAbsolutePath(buildStoragePath(relativePath))
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
        indexMutex.withLock {
            block()
        }
    }
    internal fun storageLockKey(relativePath: String): String {
        val baseLocationForLock = if (useSaveLocationApi) resolvedSaveLocation else null
        return buildThreadStorageLockKey(
            storageId = relativePath,
            baseDirectory = baseDirectory,
            baseSaveLocation = baseLocationForLock
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
