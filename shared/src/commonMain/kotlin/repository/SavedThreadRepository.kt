package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 保存済みスレッドリポジトリ
 */
class SavedThreadRepository(
    private val fileSystem: FileSystem
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val indexMutex = Mutex()

    private val indexPath = "saved_threads/index.json"

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
                lastUpdated = System.currentTimeMillis()
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
                lastUpdated = System.currentTimeMillis()
            )

            saveIndexUnlocked(updatedIndex)
        }
    }

    /**
     * スレッドメタデータを読み込み
     */
    suspend fun loadThreadMetadata(threadId: String): Result<SavedThreadMetadata> = withContext(Dispatchers.Default) {
        runCatching {
            val metadataPath = "saved_threads/$threadId/metadata.json"
            val jsonString = fileSystem.readString(metadataPath).getOrThrow()
            json.decodeFromString<SavedThreadMetadata>(jsonString)
        }
    }

    /**
     * スレッドを削除
     */
    suspend fun deleteThread(threadId: String): Result<Unit> = withIndexLock {
        runCatching {
            val threadPath = "saved_threads/$threadId"
            fileSystem.deleteRecursively(threadPath).getOrThrow()

            val currentIndex = readIndexUnlocked()
            val updatedThreads = currentIndex.threads.filterNot { it.threadId == threadId }

            val updatedIndex = SavedThreadIndex(
                threads = updatedThreads,
                totalSize = updatedThreads.sumOf { it.totalSize },
                lastUpdated = System.currentTimeMillis()
            )

            saveIndexUnlocked(updatedIndex)
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
        val threadPath = "saved_threads/$threadId"
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
        return fileSystem.resolveAbsolutePath("saved_threads/$threadId/thread.html")
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
                lastUpdated = System.currentTimeMillis()
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
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
            .getOrElse {
                SavedThreadIndex(
                    threads = emptyList(),
                    totalSize = 0L,
                    lastUpdated = System.currentTimeMillis()
                )
            }
    }

    private suspend fun saveIndexUnlocked(index: SavedThreadIndex) {
        fileSystem.createDirectory("saved_threads").getOrThrow()
        val jsonString = json.encodeToString(index)
        fileSystem.writeString(indexPath, jsonString).getOrThrow()
    }
}
