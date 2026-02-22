package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val OPERATION_BACKUP_FILE_PREFIX = "index.json."
private const val OPERATION_BACKUP_THREAD_DELETE_SUFFIX = ".thread_delete.backup"
private const val OPERATION_BACKUP_ALL_DELETE_SUFFIX = ".all_delete.backup"
private const val OPERATION_BACKUP_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
private const val OPERATION_BACKUP_CLEANUP_MIN_INTERVAL_MILLIS = 15L * 60L * 1000L
private const val OPERATION_BACKUP_CLEANUP_MAX_DELETIONS_PER_RUN = 24
private const val OPERATION_BACKUP_CLEANUP_ERROR_LOG_LIMIT = 3

/**
 * 保存済みスレッドリポジトリ
 */
class SavedThreadRepository(
    private val fileSystem: FileSystem,
    private val baseDirectory: String = MANUAL_SAVE_DIRECTORY,
    private val baseSaveLocation: SaveLocation? = null
) {
    data class SavedThreadStats(
        val threadCount: Int,
        val totalSize: Long
    )

    // FIX: メモリリーク防止 - 静的レジストリではなく、インスタンス毎のMutexを使用
    private val indexMutex = Mutex()
    private val mutationMutex = Mutex()
    private val deleteMutex = Mutex()
    private val backupCleanupMutex = Mutex()
    private var lastOperationBackupCleanupEpochMillis = 0L

    // FIX: 整数オーバーフロー対策 - 安全なサイズ合計計算
    private fun List<SavedThread>.safeTotalSize(): Long {
        var total = 0L
        for (thread in this) {
            val newTotal = total + thread.totalSize
            // オーバーフローチェック（符号反転を検出）
            if (newTotal < total) {
                Logger.w("SavedThreadRepository", "Total size overflow detected, capping at Long.MAX_VALUE")
                return Long.MAX_VALUE
            }
            total = newTotal
        }
        return total
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val resolvedSaveLocation = baseSaveLocation ?: SaveLocation.fromString(baseDirectory)
    private val useSaveLocationApi = resolvedSaveLocation !is SaveLocation.Path
    private val indexRelativePath = "index.json"
    private var isBaseDirectoryPrepared = false

    /**
     * インデックスを読み込み
     */
    suspend fun loadIndex(): SavedThreadIndex = withIndexLock {
        readIndexUnlocked()
    }

    /**
     * インデックスを保存
     */
    suspend fun saveIndex(index: SavedThreadIndex): Result<Unit> = runSuspendCatchingNonCancellation {
        mutationMutex.withLock {
            withIndexLock {
                saveIndexUnlocked(index)
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
        mutationMutex.withLock {
            var lastException: Throwable? = null
            repeat(3) { attempt ->
                try {
                    withIndexLock {
                        val currentIndex = readIndexUnlocked()
                        val updatedThreads = currentIndex.threads
                            .filterNot { isSameThreadIdentity(it, thread.threadId, thread.boardId) }
                            .plus(thread)
                            .sortedByDescending { it.savedAt }

                        val updatedIndex = SavedThreadIndex(
                            threads = updatedThreads,
                            totalSize = updatedThreads.safeTotalSize(), // FIX: オーバーフロー対策
                            lastUpdated = Clock.System.now().toEpochMilliseconds()
                        )
                        saveIndexUnlocked(updatedIndex)
                    }
                    return@withLock
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
    }

    /**
     * スレッドをインデックスから削除
     */
    suspend fun removeThreadFromIndex(threadId: String, boardId: String? = null): Result<Unit> = runSuspendCatchingNonCancellation {
        mutationMutex.withLock {
            withIndexLock {
                val currentIndex = readIndexUnlocked()
                val updatedThreads = currentIndex.threads.filterNot {
                    isSameThreadIdentity(it, threadId, boardId)
                }

                val updatedIndex = SavedThreadIndex(
                    threads = updatedThreads,
                    totalSize = updatedThreads.safeTotalSize(), // FIX: オーバーフロー対策
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )

                saveIndexUnlocked(updatedIndex)
            }
        }
    }

    /**
     * スレッドメタデータを読み込み
     */
    suspend fun loadThreadMetadata(threadId: String, boardId: String? = null): Result<SavedThreadMetadata> = runSuspendCatchingNonCancellation {
        withContext(Dispatchers.IO) {
            val normalizedThreadId = threadId.trim()
            if (normalizedThreadId.isBlank()) {
                throw IllegalArgumentException("threadId must not be blank")
            }
            val normalizedBoardId = boardId?.trim()?.takeIf { it.isNotBlank() }
            val triedPaths = linkedSetOf<String>()
            var lastError: Throwable? = null

            suspend fun tryLoadMetadataAt(path: String): SavedThreadMetadata? {
                if (!triedPaths.add(path)) return null
                val jsonString = readStringAt(path).getOrElse { error ->
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
                add("${resolveStorageId(normalizedThreadId, normalizedBoardId)}/metadata.json")
                add("$normalizedThreadId/metadata.json")
            }

            fastCandidates.forEach { path ->
                tryLoadMetadataAt(path)?.let { return@withContext it }
            }

            val metadataCandidates = withIndexLock {
                resolveMetadataCandidatesUnlocked(normalizedThreadId, normalizedBoardId)
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
        withContext(Dispatchers.IO) {
            data class DeletePlan(
                val backupIndexPath: String,
                val backupIndexJson: String,
                val targetStorageIds: Set<String>,
                val cutoffSavedAtByStorageId: Map<String, Long>
            )

            deleteMutex.withLock {
                val plan = mutationMutex.withLock {
                    withIndexLock {
                        val currentIndex = readIndexUnlocked()

                        // 削除対象が存在するか確認
                        val threadsToDelete = currentIndex.threads
                            .filter { isSameThreadIdentity(it, threadId, boardId) }
                            .sortedByDescending { it.savedAt }
                        if (threadsToDelete.isEmpty()) {
                            return@withIndexLock null // 存在しない場合は正常終了
                        }

                        // バックアップを操作単位で分離（同時削除時の競合防止）
                        val backupIndexPath = "$indexRelativePath.${Clock.System.now().toEpochMilliseconds()}.thread_delete.backup"
                        val currentIndexJson = json.encodeToString(currentIndex)

                        val cutoffSavedAtByStorageId = threadsToDelete
                            .groupBy { resolveStorageId(it) }
                            .mapValues { (_, threads) -> threads.maxOf { it.savedAt } }
                        DeletePlan(
                            backupIndexPath = backupIndexPath,
                            backupIndexJson = currentIndexJson,
                            targetStorageIds = cutoffSavedAtByStorageId.keys,
                            cutoffSavedAtByStorageId = cutoffSavedAtByStorageId
                        )
                    }
                } ?: return@withLock
                writeStringAt(plan.backupIndexPath, plan.backupIndexJson).getOrThrow()

                val deletionErrors = mutableListOf<Pair<String, Throwable>>()
                val successfullyDeletedStorageIds = mutableSetOf<String>()
                var keepBackup = false
                try {
                    plan.targetStorageIds.forEach { threadPath ->
                        coroutineContext.ensureActive()
                        yield()
                        val deleteResult = deletePath(threadPath)
                        val deleteError = deleteResult.exceptionOrNull()
                        if (deleteResult.isSuccess || isPathAlreadyDeleted(deleteError)) {
                            successfullyDeletedStorageIds.add(threadPath)
                        } else {
                            deletionErrors.add(
                                threadPath to (deleteError
                                    ?: Exception("Failed to delete thread directory: $threadPath"))
                            )
                        }
                    }

                    if (successfullyDeletedStorageIds.isNotEmpty()) {
                        mutationMutex.withLock {
                            withIndexLock {
                                val latestIndex = readIndexUnlocked()
                                val updatedThreads = latestIndex.threads.filterNot { thread ->
                                    val storageId = resolveStorageId(thread)
                                    val cutoffSavedAt = plan.cutoffSavedAtByStorageId[storageId] ?: return@filterNot false
                                    storageId in successfullyDeletedStorageIds && thread.savedAt <= cutoffSavedAt
                                }
                                val updatedIndex = SavedThreadIndex(
                                    threads = updatedThreads,
                                    totalSize = updatedThreads.safeTotalSize(), // FIX: オーバーフロー対策
                                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                                )

                                try {
                                    saveIndexUnlocked(updatedIndex)
                                } catch (e: Throwable) {
                                    if (e is CancellationException) throw e
                                    throw Exception("Failed to update index after deleting thread $threadId. Index may be inconsistent.", e)
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
                    if (deletionErrors.isNotEmpty()) {
                        keepBackup = true
                    }
                    finalizeDeleteBackup(plan.backupIndexPath, keepBackup)
                }

                if (deletionErrors.isNotEmpty()) {
                    val errorMessage = deletionErrors.joinToString("\n") { (storageId, error) ->
                        "$storageId: ${error.message}"
                    }
                    throw Exception("Failed to delete ${deletionErrors.size} thread directory(s):\n$errorMessage")
                }
            }
        }
    }

    /**
     * すべてのスレッドを削除
     */
    suspend fun deleteAllThreads(): Result<Unit> = runSuspendCatchingNonCancellation {
        withContext(Dispatchers.IO) {
            data class DeleteAllPlan(
                val backupIndexPath: String,
                val backupIndexJson: String,
                val targetStorageIds: Set<String>,
                val cutoffSavedAtByStorageId: Map<String, Long>
            )

            deleteMutex.withLock {
                val plan = mutationMutex.withLock {
                    withIndexLock {
                        val currentIndex = readIndexUnlocked()
                        if (currentIndex.threads.isEmpty()) {
                            return@withIndexLock null
                        }

                        val backupIndexPath = "$indexRelativePath.${Clock.System.now().toEpochMilliseconds()}.all_delete.backup"
                        val currentIndexJson = json.encodeToString(currentIndex)

                        val cutoffSavedAtByStorageId = currentIndex.threads
                            .groupBy { resolveStorageId(it) }
                            .mapValues { (_, threads) -> threads.maxOf { it.savedAt } }
                        DeleteAllPlan(
                            backupIndexPath = backupIndexPath,
                            backupIndexJson = currentIndexJson,
                            targetStorageIds = cutoffSavedAtByStorageId.keys,
                            cutoffSavedAtByStorageId = cutoffSavedAtByStorageId
                        )
                    }
                } ?: return@withLock
                writeStringAt(plan.backupIndexPath, plan.backupIndexJson).getOrThrow()

                val deletionErrors = mutableListOf<Pair<String, Throwable>>()
                val successfullyDeletedStorageIds = mutableSetOf<String>()
                var keepBackup = false
                try {
                    plan.targetStorageIds.forEach { threadPath ->
                        coroutineContext.ensureActive()
                        yield()
                        val result = deletePath(threadPath)
                        val deleteError = result.exceptionOrNull()
                        if (result.isSuccess || isPathAlreadyDeleted(deleteError)) {
                            successfullyDeletedStorageIds.add(threadPath)
                        } else {
                            deletionErrors.add(
                                threadPath to (deleteError
                                    ?: Exception("Unknown error deleting $threadPath"))
                            )
                        }
                    }

                    if (successfullyDeletedStorageIds.isNotEmpty()) {
                        mutationMutex.withLock {
                            withIndexLock {
                                val currentIndex = readIndexUnlocked()
                                // 成功した削除対象のうち、削除開始時点のエントリだけを除外
                                val remainingThreads = currentIndex.threads.filterNot { thread ->
                                    val storageId = resolveStorageId(thread)
                                    val cutoffSavedAt = plan.cutoffSavedAtByStorageId[storageId] ?: return@filterNot false
                                    storageId in successfullyDeletedStorageIds && thread.savedAt <= cutoffSavedAt
                                }

                                val updatedIndex = SavedThreadIndex(
                                    threads = remainingThreads,
                                    totalSize = remainingThreads.safeTotalSize(), // FIX: オーバーフロー対策
                                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                                )
                                saveIndexUnlocked(updatedIndex)
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
                    if (deletionErrors.isNotEmpty()) {
                        keepBackup = true
                    }
                    finalizeDeleteBackup(plan.backupIndexPath, keepBackup)
                }

                // エラーがあった場合は例外をスロー
                if (deletionErrors.isNotEmpty()) {
                    val errorMessage = deletionErrors.joinToString("\n") { (storageId, error) ->
                        "$storageId: ${error.message}"
                    }
                    throw Exception("Failed to delete ${deletionErrors.size} thread(s):\n$errorMessage")
                }
            }
        }
    }

    /**
     * すべての保存済みスレッドを取得
     */
    suspend fun getAllThreads(): List<SavedThread> = withIndexLock {
        readIndexUnlocked().threads
    }

    /**
     * スレッドが存在するか確認
     */
    suspend fun threadExists(threadId: String, boardId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val threadPath = withIndexLock {
            val currentIndex = readIndexUnlocked()
            currentIndex.threads
                .filter { isSameThreadIdentity(it, threadId, boardId) }
                .maxByOrNull { it.savedAt }
                ?.let { resolveStorageId(it) }
                ?: resolveStorageId(threadId = threadId, boardId = boardId)
        }
        if (useSaveLocationApi) {
            fileSystem.exists(resolvedSaveLocation, threadPath)
        } else {
            fileSystem.exists(buildPath(threadPath))
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
        val index = readIndexUnlocked()
        SavedThreadStats(
            threadCount = index.threads.size,
            totalSize = index.totalSize
        )
    }

    /**
     * スレッドHTMLパスを取得
     */
    fun getThreadHtmlPath(threadId: String, boardId: String? = null): String {
        val storageId = resolveStorageId(threadId = threadId, boardId = boardId)
        val relativePath = "$storageId/$threadId.htm"
        return if (useSaveLocationApi) {
            relativePath
        } else {
            fileSystem.resolveAbsolutePath(buildPath(relativePath))
        }
    }

    /**
     * スレッド情報を更新
     */
    suspend fun updateThread(thread: SavedThread): Result<Unit> = runSuspendCatchingNonCancellation {
        mutationMutex.withLock {
            withIndexLock {
                val currentIndex = readIndexUnlocked()
                val updatedThreads = currentIndex.threads.map {
                    if (isSameThreadIdentity(it, thread.threadId, thread.boardId)) thread else it
                }

                val updatedIndex = SavedThreadIndex(
                    threads = updatedThreads,
                    totalSize = updatedThreads.safeTotalSize(), // FIX: オーバーフロー対策
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )

                saveIndexUnlocked(updatedIndex)
            }
        }
    }

    private suspend fun <T> withIndexLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        indexMutex.withLock {
            block()
        }
    }

    private suspend fun readIndexUnlocked(): SavedThreadIndex {
        fun emptyIndex() = SavedThreadIndex(
            threads = emptyList(),
            totalSize = 0L,
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        )

        suspend fun readIndexFromPath(path: String): SavedThreadIndex? {
            return fileSystem.readString(path)
                .map { jsonString ->
                    try {
                        json.decodeFromString<SavedThreadIndex>(jsonString)
                    } catch (_: SerializationException) {
                        null
                    }
                }
                .getOrNull()
        }

        suspend fun readIndexFromLocation(relativePath: String): SavedThreadIndex? {
            return fileSystem.readString(resolvedSaveLocation, relativePath)
                .map { jsonString ->
                    try {
                        json.decodeFromString<SavedThreadIndex>(jsonString)
                    } catch (_: SerializationException) {
                        null
                    }
                }
                .getOrNull()
        }

        val primary = if (useSaveLocationApi) {
            readIndexFromLocation(indexRelativePath)
        } else {
            readIndexFromPath(buildPath(indexRelativePath))
        }
        if (primary != null) {
            return sanitizeAndRepairIndexUnlocked(primary)
        }

        val backupPath = "$indexRelativePath.backup"
        val backup = if (useSaveLocationApi) {
            readIndexFromLocation(backupPath)
        } else {
            readIndexFromPath(buildPath(backupPath))
        }
        if (backup != null) {
            val repairedBackup = sanitizeAndRepairIndexUnlocked(backup)
            Logger.w("SavedThreadRepository", "Loaded index from backup due to missing/corrupted primary index")
            return repairedBackup
        }

        return emptyIndex()
    }

    private suspend fun sanitizeAndRepairIndexUnlocked(index: SavedThreadIndex): SavedThreadIndex {
        if (index.threads.isEmpty()) {
            if (index.totalSize != 0L) {
                return index.copy(
                    totalSize = 0L,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
            }
            return index
        }

        // インデックス読み込み中に重いファイル存在チェックを行うとロック競合が増えるため、
        // ここではメモリ上で正規化できる項目（重複・totalSize）のみ修復する。
        val dedupedByStorageId = linkedMapOf<String, SavedThread>()
        index.threads.forEach { thread ->
            val storageId = resolveStorageId(thread)
            val current = dedupedByStorageId[storageId]
            if (current == null || thread.savedAt > current.savedAt) {
                dedupedByStorageId[storageId] = thread
            }
        }
        val normalizedThreads = dedupedByStorageId.values.toList()

        val recalculatedSize = normalizedThreads.safeTotalSize()
        val needsRepair =
            normalizedThreads.size != index.threads.size ||
            recalculatedSize != index.totalSize
        if (!needsRepair) {
            return index
        }

        val repaired = SavedThreadIndex(
            threads = normalizedThreads,
            totalSize = recalculatedSize,
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        )
        val droppedCount = index.threads.size - normalizedThreads.size
        if (droppedCount > 0) {
            Logger.w(
                "SavedThreadRepository",
                "Repaired index by dropping $droppedCount duplicate thread entries"
            )
        }
        return repaired
    }

    private suspend fun saveIndexUnlocked(index: SavedThreadIndex) {
        suspend fun writeIndexPayload(jsonString: String) {
            if (useSaveLocationApi) {
                fileSystem.writeString(resolvedSaveLocation, indexRelativePath, jsonString).getOrThrow()
            } else {
                fileSystem.writeString(buildPath(indexRelativePath), jsonString).getOrThrow()
            }
        }

        ensureBaseDirectoryPreparedUnlocked()
        val jsonString = json.encodeToString(index)
        runCatching {
            writeIndexPayload(jsonString)
        }.getOrElse { firstError ->
            // ディレクトリが外部要因で消えたケースに備え、作り直して1回だけ再試行する。
            isBaseDirectoryPrepared = false
            ensureBaseDirectoryPreparedUnlocked()
            runCatching {
                writeIndexPayload(jsonString)
            }.getOrElse { retryError ->
                throw Exception(
                    "Failed to persist index after directory re-prepare. first=${firstError.message}, retry=${retryError.message}",
                    retryError
                )
            }
        }
    }

    private suspend fun ensureBaseDirectoryPreparedUnlocked() {
        if (isBaseDirectoryPrepared) return
        if (useSaveLocationApi) {
            fileSystem.createDirectory(resolvedSaveLocation).getOrThrow()
        } else {
            fileSystem.createDirectory(baseDirectory).getOrThrow()
        }
        isBaseDirectoryPrepared = true
    }

    private fun isSameThreadIdentity(thread: SavedThread, threadId: String, boardId: String?): Boolean {
        if (thread.threadId != threadId) return false
        val normalizedBoardId = boardId?.trim().orEmpty()
        if (normalizedBoardId.isBlank()) return true
        val candidateBoardId = thread.boardId.trim()
        return candidateBoardId.equals(normalizedBoardId, ignoreCase = true)
    }

    private fun resolveStorageId(thread: SavedThread): String {
        return thread.storageId
            ?.takeIf { it.isNotBlank() }
            ?: resolveStorageId(thread.threadId, thread.boardId)
    }

    private fun resolveStorageId(threadId: String, boardId: String?): String {
        return buildThreadStorageId(boardId, threadId)
    }

    private suspend fun resolveMetadataCandidatesUnlocked(threadId: String, boardId: String?): List<String> {
        val currentIndex = readIndexUnlocked()
        val latestByStorageId = linkedMapOf<String, SavedThread>()
        currentIndex.threads.forEach { thread ->
            if (!isSameThreadIdentity(thread, threadId, boardId)) return@forEach
            val storageId = resolveStorageId(thread)
            val existing = latestByStorageId[storageId]
            if (existing == null || thread.savedAt > existing.savedAt) {
                latestByStorageId[storageId] = thread
            }
        }
        val fromIndex = latestByStorageId.values
            .asSequence()
            .sortedByDescending { it.savedAt }
            .map { thread -> "${resolveStorageId(thread)}/metadata.json" }
            .toList()

        val fallbackCurrent = "${resolveStorageId(threadId, boardId)}/metadata.json"
        val fallbackLegacy = "$threadId/metadata.json"
        return buildList {
            addAll(fromIndex)
            add(fallbackCurrent)
            add(fallbackLegacy)
        }
    }

    private fun buildPath(relativePath: String): String {
        return if (relativePath.isBlank()) {
            baseDirectory
        } else {
            "$baseDirectory/$relativePath"
        }
    }

    private suspend fun readStringAt(relativePath: String): Result<String> {
        return if (useSaveLocationApi) {
            fileSystem.readString(resolvedSaveLocation, relativePath)
        } else {
            fileSystem.readString(buildPath(relativePath))
        }
    }

    private suspend fun writeStringAt(relativePath: String, content: String): Result<Unit> {
        return if (useSaveLocationApi) {
            fileSystem.writeString(resolvedSaveLocation, relativePath, content)
        } else {
            fileSystem.writeString(buildPath(relativePath), content)
        }
    }

    private suspend fun deletePath(relativePath: String): Result<Unit> {
        return if (useSaveLocationApi) {
            fileSystem.delete(resolvedSaveLocation, relativePath)
        } else {
            fileSystem.deleteRecursively(buildPath(relativePath))
        }
    }

    private suspend fun finalizeDeleteBackup(backupPath: String, keepBackup: Boolean) {
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

    private suspend fun cleanupStaleOperationBackups() {
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
            val timestamp = extractOperationBackupTimestamp(fileName) ?: return@forEach
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

    private fun extractOperationBackupTimestamp(fileName: String): Long? {
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

    private fun isPathAlreadyDeleted(error: Throwable?): Boolean {
        val message = error?.message?.lowercase().orEmpty()
        if (message.isBlank()) return false
        return message.contains("not found") ||
            message.contains("no such file") ||
            message.contains("does not exist") ||
            message.contains("cannot find")
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
}
