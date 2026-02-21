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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 保存済みスレッドリポジトリ
 */
class SavedThreadRepository(
    private val fileSystem: FileSystem,
    private val baseDirectory: String = MANUAL_SAVE_DIRECTORY,
    private val baseSaveLocation: SaveLocation? = null
) {
    // FIX: メモリリーク防止 - 静的レジストリではなく、インスタンス毎のMutexを使用
    private val indexMutex = Mutex()

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
        withIndexLock {
            saveIndexUnlocked(index)
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

    /**
     * スレッドメタデータを読み込み
     */
    suspend fun loadThreadMetadata(threadId: String, boardId: String? = null): Result<SavedThreadMetadata> = runSuspendCatchingNonCancellation {
        withContext(Dispatchers.Default) {
            val metadataCandidates = withIndexLock {
                resolveMetadataCandidatesUnlocked(threadId, boardId)
            }.distinct()
            var lastError: Throwable? = null
            for (metadataPath in metadataCandidates) {
                val jsonString = readStringAt(metadataPath).getOrElse { error ->
                    lastError = error
                    continue
                }
                val metadata = runCatching {
                    json.decodeFromString<SavedThreadMetadata>(jsonString)
                }.getOrElse { error ->
                    lastError = error
                    continue
                }
                return@withContext metadata
            }
            throw lastError ?: IllegalStateException("Metadata not found for threadId=$threadId boardId=${boardId.orEmpty()}")
        }
    }

    /**
     * スレッドを削除
     */
    suspend fun deleteThread(threadId: String, boardId: String? = null): Result<Unit> = runSuspendCatchingNonCancellation {
        data class DeletePlan(
            val backupIndexPath: String,
            val targetStorageIds: List<String>
        )

        val plan = withIndexLock {
            val currentIndex = readIndexUnlocked()

            // 削除対象が存在するか確認
            val threadsToDelete = currentIndex.threads
                .filter { isSameThreadIdentity(it, threadId, boardId) }
                .sortedByDescending { it.savedAt }
            if (threadsToDelete.isEmpty()) {
                return@withIndexLock null // 存在しない場合は正常終了
            }

            // バックアップ作成は短時間で済むためロック内で実施する
            val backupIndexPath = "$indexRelativePath.backup"
            val currentIndexJson = json.encodeToString(currentIndex)
            writeStringAt(backupIndexPath, currentIndexJson).getOrThrow()

            val targetStorageIds = threadsToDelete
                .map { resolveStorageId(it) }
                .distinct()
            DeletePlan(
                backupIndexPath = backupIndexPath,
                targetStorageIds = targetStorageIds
            )
        } ?: return@runSuspendCatchingNonCancellation

        // 重いファイル削除はロック外で実行して他操作をブロックしない
        plan.targetStorageIds.forEach { threadPath ->
            try {
                val deleteResult = deletePath(threadPath)
                if (deleteResult.isFailure) {
                    deletePath(plan.backupIndexPath)
                    throw deleteResult.exceptionOrNull()
                        ?: Exception("Failed to delete thread directory: $threadPath")
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                throw e
            }
        }

        withIndexLock {
            val latestIndex = readIndexUnlocked()
            val deletedStorageIds = plan.targetStorageIds.toSet()
            val updatedThreads = latestIndex.threads.filterNot {
                resolveStorageId(it) in deletedStorageIds
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

        deletePath(plan.backupIndexPath).getOrNull()
    }

    /**
     * すべてのスレッドを削除
     */
    suspend fun deleteAllThreads(): Result<Unit> = runSuspendCatchingNonCancellation {
        val storageIds = withIndexLock {
            val currentIndex = readIndexUnlocked()
            currentIndex.threads.map { resolveStorageId(it) }.distinct()
        }

        val deletionErrors = mutableListOf<Pair<String, Throwable>>()
        val successfullyDeletedKeys = mutableSetOf<String>()
        storageIds.forEach { threadPath ->
            val result = deletePath(threadPath)
            if (result.isSuccess) {
                successfullyDeletedKeys.add(threadPath)
            } else {
                deletionErrors.add(
                    threadPath to (result.exceptionOrNull()
                        ?: Exception("Unknown error deleting $threadPath"))
                )
            }
        }

        withIndexLock {
            val currentIndex = readIndexUnlocked()
            // 成功したもののみインデックスから削除
            val remainingThreads = currentIndex.threads.filterNot {
                resolveStorageId(it) in successfullyDeletedKeys
            }

            val updatedIndex = SavedThreadIndex(
                threads = remainingThreads,
                totalSize = remainingThreads.safeTotalSize(), // FIX: オーバーフロー対策
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )
            saveIndexUnlocked(updatedIndex)
        }

        // エラーがあった場合は例外をスロー
        if (deletionErrors.isNotEmpty()) {
            val errorMessage = deletionErrors.joinToString("\n") { (storageId, error) ->
                "$storageId: ${error.message}"
            }
            throw Exception("Failed to delete ${deletionErrors.size} thread(s):\n$errorMessage")
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
    suspend fun threadExists(threadId: String, boardId: String? = null): Boolean = withContext(Dispatchers.Default) {
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
    suspend fun getTotalSize(): Long = withIndexLock {
        readIndexUnlocked().totalSize
    }

    /**
     * スレッド数を取得
     */
    suspend fun getThreadCount(): Int = withIndexLock {
        readIndexUnlocked().threads.size
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

    private suspend fun <T> withIndexLock(block: suspend () -> T): T = withContext(Dispatchers.Default) {
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
            try {
                saveIndexUnlocked(repairedBackup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.e("SavedThreadRepository", "Failed to restore index from backup", e)
            }
            return repairedBackup
        }

        return emptyIndex()
    }

    private suspend fun sanitizeAndRepairIndexUnlocked(index: SavedThreadIndex): SavedThreadIndex {
        if (index.threads.isEmpty()) {
            if (index.totalSize != 0L) {
                val repaired = index.copy(
                    totalSize = 0L,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                try {
                    saveIndexUnlocked(repaired)
                } catch (e: CancellationException) {
                    throw e
                } catch (error: Throwable) {
                    Logger.e("SavedThreadRepository", "Failed to normalize empty index totalSize", error)
                }
                return repaired
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
        try {
            saveIndexUnlocked(repaired)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            Logger.e("SavedThreadRepository", "Failed to persist repaired index", error)
        }
        return repaired
    }

    private suspend fun saveIndexUnlocked(index: SavedThreadIndex) {
        if (useSaveLocationApi) {
            fileSystem.createDirectory(resolvedSaveLocation).getOrThrow()
            val jsonString = json.encodeToString(index)
            fileSystem.writeString(resolvedSaveLocation, indexRelativePath, jsonString).getOrThrow()
        } else {
            fileSystem.createDirectory(baseDirectory).getOrThrow()
            val jsonString = json.encodeToString(index)
            fileSystem.writeString(buildPath(indexRelativePath), jsonString).getOrThrow()
        }
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
        val fromIndex = currentIndex.threads
            .asSequence()
            .filter { isSameThreadIdentity(it, threadId, boardId) }
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
