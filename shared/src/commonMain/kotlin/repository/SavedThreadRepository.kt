package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 保存済みスレッドリポジトリ
 */
class SavedThreadRepository(
    private val fileSystem: FileSystem,
    private val baseDirectory: String = MANUAL_SAVE_DIRECTORY
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val indexMutex = Mutex()

    private val indexPath = "$baseDirectory/index.json"

    /**
     * インデックスを読み込み
     */
    suspend fun loadIndex(): SavedThreadIndex = withIndexLock {
        readIndexUnlocked()
    }

    /**
     * インデックスを保存
     */
    suspend fun saveIndex(index: SavedThreadIndex): Result<Unit> = withIndexLock {
        runCatching {
            saveIndexUnlocked(index)
        }
    }

    /**
     * スレッドをインデックスに追加
     */
    suspend fun addThreadToIndex(thread: SavedThread): Result<Unit> = withIndexLock {
        runCatching {
            val currentIndex = readIndexUnlocked()
            val updatedThreads = currentIndex.threads
                .filterNot { it.threadId == thread.threadId }
                .plus(thread)
                .sortedByDescending { it.savedAt }

            val updatedIndex = SavedThreadIndex(
                threads = updatedThreads,
                totalSize = updatedThreads.sumOf { it.totalSize },
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )

            saveIndexUnlocked(updatedIndex)
        }
    }

    /**
     * スレッドをインデックスから削除
     */
    suspend fun removeThreadFromIndex(threadId: String): Result<Unit> = withIndexLock {
        runCatching {
            val currentIndex = readIndexUnlocked()
            val updatedThreads = currentIndex.threads.filterNot { it.threadId == threadId }

            val updatedIndex = SavedThreadIndex(
                threads = updatedThreads,
                totalSize = updatedThreads.sumOf { it.totalSize },
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )

            saveIndexUnlocked(updatedIndex)
        }
    }

    /**
     * スレッドメタデータを読み込み
     */
    suspend fun loadThreadMetadata(threadId: String): Result<SavedThreadMetadata> = withContext(Dispatchers.Default) {
        runCatching {
            val metadataPath = "$baseDirectory/$threadId/metadata.json"
            val jsonString = fileSystem.readString(metadataPath).getOrThrow()
            json.decodeFromString<SavedThreadMetadata>(jsonString)
        }
    }

    /**
     * スレッドを削除
     */
    suspend fun deleteThread(threadId: String): Result<Unit> = withIndexLock {
        runCatching {
            val currentIndex = readIndexUnlocked()

            // 削除対象が存在するか確認
            val threadToDelete = currentIndex.threads.find { it.threadId == threadId }
                ?: return@runCatching  // 存在しない場合は正常終了

            // ファイル削除を試行
            val threadPath = "$baseDirectory/$threadId"
            val deleteResult = fileSystem.deleteRecursively(threadPath)

            if (deleteResult.isFailure) {
                // ファイル削除失敗時はインデックスを更新せずに例外をスロー
                throw deleteResult.exceptionOrNull()
                    ?: Exception("Failed to delete thread directory: $threadPath")
            }

            // 削除成功後のみインデックスを更新
            val updatedThreads = currentIndex.threads.filterNot { it.threadId == threadId }
            val updatedIndex = SavedThreadIndex(
                threads = updatedThreads,
                totalSize = updatedThreads.sumOf { it.totalSize },
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )

            saveIndexUnlocked(updatedIndex)
        }
    }

    /**
     * すべてのスレッドを削除
     */
    suspend fun deleteAllThreads(): Result<Unit> = withIndexLock {
        runCatching {
            val currentIndex = readIndexUnlocked()
            val deletionErrors = mutableListOf<Pair<String, Throwable>>()
            val successfullyDeletedIds = mutableSetOf<String>()

            // 各スレッドを削除し、失敗を記録
            currentIndex.threads.forEach { thread ->
                val threadPath = "$baseDirectory/${thread.threadId}"
                val result = fileSystem.deleteRecursively(threadPath)

                if (result.isSuccess) {
                    successfullyDeletedIds.add(thread.threadId)
                } else {
                    deletionErrors.add(
                        thread.threadId to (result.exceptionOrNull()
                            ?: Exception("Unknown error deleting $threadPath"))
                    )
                }
            }

            // 成功したもののみインデックスから削除
            val remainingThreads = currentIndex.threads.filterNot {
                it.threadId in successfullyDeletedIds
            }

            val updatedIndex = SavedThreadIndex(
                threads = remainingThreads,
                totalSize = remainingThreads.sumOf { it.totalSize },
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )
            saveIndexUnlocked(updatedIndex)

            // エラーがあった場合は例外をスロー
            if (deletionErrors.isNotEmpty()) {
                val errorMessage = deletionErrors.joinToString("\n") { (id, error) ->
                    "Thread $id: ${error.message}"
                }
                throw Exception("Failed to delete ${deletionErrors.size} thread(s):\n$errorMessage")
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
    suspend fun threadExists(threadId: String): Boolean = withContext(Dispatchers.Default) {
        val threadPath = "$baseDirectory/$threadId"
        fileSystem.exists(threadPath)
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
    fun getThreadHtmlPath(threadId: String): String {
        return fileSystem.resolveAbsolutePath("$baseDirectory/$threadId/thread.html")
    }

    /**
     * スレッド情報を更新
     */
    suspend fun updateThread(thread: SavedThread): Result<Unit> = withIndexLock {
        runCatching {
            val currentIndex = readIndexUnlocked()
            val updatedThreads = currentIndex.threads.map {
                if (it.threadId == thread.threadId) thread else it
            }

            val updatedIndex = SavedThreadIndex(
                threads = updatedThreads,
                totalSize = updatedThreads.sumOf { it.totalSize },
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
        return fileSystem.readString(indexPath)
            .map { jsonString ->
                try {
                    json.decodeFromString<SavedThreadIndex>(jsonString)
                } catch (e: SerializationException) {
                    SavedThreadIndex(
                        threads = emptyList(),
                        totalSize = 0L,
                        lastUpdated = Clock.System.now().toEpochMilliseconds()
                    )
                }
            }
            .getOrElse {
                SavedThreadIndex(
                    threads = emptyList(),
                    totalSize = 0L,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
            }
    }

    private suspend fun saveIndexUnlocked(index: SavedThreadIndex) {
        fileSystem.createDirectory(baseDirectory).getOrThrow()
        val jsonString = json.encodeToString(index)
        fileSystem.writeString(indexPath, jsonString).getOrThrow()
    }
}
