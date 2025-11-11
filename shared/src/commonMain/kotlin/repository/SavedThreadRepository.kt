package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.Dispatchers
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

    private val indexPath = "saved_threads/index.json"

    /**
     * インデックスを読み込み
     */
    suspend fun loadIndex(): SavedThreadIndex = withContext(Dispatchers.Default) {
        fileSystem.readString(indexPath)
            .map { jsonString ->
                try {
                    json.decodeFromString<SavedThreadIndex>(jsonString)
                } catch (e: SerializationException) {
                    // インデックスファイルが壊れている場合は空のインデックスを返す
                    SavedThreadIndex(
                        threads = emptyList(),
                        totalSize = 0L,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
            .getOrElse {
                // ファイルが存在しない場合は空のインデックスを返す
                SavedThreadIndex(
                    threads = emptyList(),
                    totalSize = 0L,
                    lastUpdated = System.currentTimeMillis()
                )
            }
    }

    /**
     * インデックスを保存
     */
    suspend fun saveIndex(index: SavedThreadIndex): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            fileSystem.createDirectory("saved_threads").getOrThrow()
            val jsonString = json.encodeToString(index)
            fileSystem.writeString(indexPath, jsonString).getOrThrow()
        }
    }

    /**
     * スレッドをインデックスに追加
     */
    suspend fun addThreadToIndex(thread: SavedThread): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val currentIndex = loadIndex()
            val updatedThreads = currentIndex.threads
                .filterNot { it.threadId == thread.threadId } // 既存のエントリを削除
                .plus(thread) // 新しいエントリを追加
                .sortedByDescending { it.savedAt } // 保存日時の降順でソート

            val updatedIndex = SavedThreadIndex(
                threads = updatedThreads,
                totalSize = updatedThreads.sumOf { it.totalSize },
                lastUpdated = System.currentTimeMillis()
            )

            saveIndex(updatedIndex).getOrThrow()
        }
    }

    /**
     * スレッドをインデックスから削除
     */
    suspend fun removeThreadFromIndex(threadId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val currentIndex = loadIndex()
            val updatedThreads = currentIndex.threads.filterNot { it.threadId == threadId }

            val updatedIndex = SavedThreadIndex(
                threads = updatedThreads,
                totalSize = updatedThreads.sumOf { it.totalSize },
                lastUpdated = System.currentTimeMillis()
            )

            saveIndex(updatedIndex).getOrThrow()
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
    suspend fun deleteThread(threadId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            // ディレクトリを削除
            val threadPath = "saved_threads/$threadId"
            fileSystem.deleteRecursively(threadPath).getOrThrow()

            // インデックスから削除
            removeThreadFromIndex(threadId).getOrThrow()
        }
    }

    /**
     * すべての保存済みスレッドを取得
     */
    suspend fun getAllThreads(): List<SavedThread> = withContext(Dispatchers.Default) {
        loadIndex().threads
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
    suspend fun getTotalSize(): Long = withContext(Dispatchers.Default) {
        loadIndex().totalSize
    }

    /**
     * スレッド数を取得
     */
    suspend fun getThreadCount(): Int = withContext(Dispatchers.Default) {
        loadIndex().threads.size
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
    suspend fun updateThread(thread: SavedThread): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val currentIndex = loadIndex()
            val updatedThreads = currentIndex.threads.map {
                if (it.threadId == thread.threadId) thread else it
            }

            val updatedIndex = SavedThreadIndex(
                threads = updatedThreads,
                totalSize = updatedThreads.sumOf { it.totalSize },
                lastUpdated = System.currentTimeMillis()
            )

            saveIndex(updatedIndex).getOrThrow()
        }
    }
}
