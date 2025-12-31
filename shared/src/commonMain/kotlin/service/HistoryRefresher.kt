package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.resolveThreadTitle
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Clock

private const val HISTORY_REFRESH_TAG = "HistoryRefresher"

/**
 * Headless use case to refresh history entries without tying to Compose/UI.
 * Keeps a skip list for threads that returned 404/410 to avoid repeated fetches,
 * but does not delete entries from history.
 */
class HistoryRefresher(
    private val stateStore: AppStateStore,
    private val repository: BoardRepository,
    private val dispatcher: CoroutineDispatcher,
    private val autoSavedThreadRepository: SavedThreadRepository? = null,  // FIX: 自動保存チェック用
    private val httpClient: HttpClient? = null,
    private val fileSystem: FileSystem? = null,
    private val maxConcurrency: Int = 4
    // Caller owns repository lifecycle
) {
    private val skipThreadIds = MutableStateFlow<Set<HistoryKey>>(emptySet())
    private val updatesMutex = Mutex()

    // FIX: エラー状態を公開
    private val _lastRefreshError = MutableStateFlow<RefreshError?>(null)
    val lastRefreshError: StateFlow<RefreshError?> = _lastRefreshError.asStateFlow()

    data class RefreshError(
        val errorCount: Int,
        val totalThreads: Int,
        val timestamp: Long,
        val errors: List<ErrorDetail>
    )

    data class ErrorDetail(
        val threadId: String,
        val message: String
    )

    private data class HistoryKey(val boardKey: String, val threadId: String)

    @OptIn(ExperimentalTime::class)
    suspend fun refresh(
        boardsSnapshot: List<BoardSummary>? = null,
        historySnapshot: List<ThreadHistoryEntry>? = null,
        autoSaveBudgetMillis: Long? = null
    ) = withContext(dispatcher) {
        val refreshStartedAt = Clock.System.now().toEpochMilliseconds()
        val autoSaveDeadline = autoSaveBudgetMillis?.let { refreshStartedAt + it }
        val boards = boardsSnapshot ?: stateStore.boards.first()
        val history = historySnapshot ?: stateStore.history.first()
        if (history.isEmpty()) return@withContext

        val semaphore = Semaphore(maxConcurrency)
        val updates = mutableMapOf<HistoryKey, ThreadHistoryEntry>()
        val errors = mutableListOf<ErrorDetail>()
        // FIX: エラーリストの最大サイズを設定（メモリ使用量制限）
        val maxErrorsToTrack = 100 // 最大100件まで記録、表示は10件
        // FIX: updatesマップの最大サイズを設定（メモリ使用量制限）
        val maxUpdatesToAccumulate = 1000 // 最大1000件まで蓄積したらフラッシュ

        // FIX: Process in batches to avoid creating thousands of coroutines at once
        // This prevents memory spikes when history size is large
        // Reduced to 10 to further limit concurrent operations and memory usage
        val batchSize = 10

        coroutineScope {
            history.chunked(batchSize).forEach { batch ->
                // Process each batch
                batch.map { entry ->
                    async {
                        val board = boards.firstOrNull { it.id == entry.boardId }
                        val resolvedBoardId = entry.boardId.ifBlank { board?.id.orEmpty() }
                        val key = HistoryKey(
                            boardKey = resolvedBoardId.ifBlank { entry.boardUrl.ifBlank { board?.url.orEmpty() } },
                            threadId = entry.threadId
                        )
                        if (skipThreadIds.value.contains(key)) return@async
                        val baseUrl = board?.url
                            ?: entry.boardUrl.takeIf { it.isNotBlank() }?.let {
                                runCatching { BoardUrlResolver.resolveBoardBaseUrl(it) }.getOrNull()
                            }
                        if (baseUrl.isNullOrBlank() || baseUrl.contains("example.com", ignoreCase = true)) {
                            return@async
                        }

                        semaphore.withPermit {
                            try {
                                val page = repository.getThread(baseUrl, entry.threadId)
                                val opPost = page.posts.firstOrNull()
                                val resolvedTitle = resolveThreadTitle(opPost, entry.title)
                                var hasAutoSave = entry.hasAutoSave

                                // 背景更新でも本文・メディアを自動保存
                                if (httpClient != null && fileSystem != null && autoSavedThreadRepository != null) {
                                    if (autoSaveDeadline != null && Clock.System.now().toEpochMilliseconds() > autoSaveDeadline) {
                                        Logger.w(HISTORY_REFRESH_TAG, "Auto-save budget exceeded, skipping auto-save for ${entry.threadId}")
                                    } else {
                                        val saver = ThreadSaveService(httpClient, fileSystem)
                                        runCatching {
                                            saver.saveThread(
                                                threadId = entry.threadId,
                                                boardId = entry.boardId.ifBlank { board?.id ?: "" },
                                                boardName = page.boardTitle ?: entry.boardName.ifBlank { board?.name.orEmpty() },
                                                boardUrl = baseUrl,
                                                title = resolvedTitle,
                                                expiresAtLabel = page.expiresAtLabel,
                                                posts = page.posts,
                                                baseDirectory = AUTO_SAVE_DIRECTORY,
                                                writeMetadata = true
                                            ).getOrThrow()
                                        }.onSuccess { saved ->
                                            autoSavedThreadRepository.addThreadToIndex(saved)
                                                .onFailure { Logger.e(HISTORY_REFRESH_TAG, "Failed to index auto-saved thread ${entry.threadId}", it) }
                                            hasAutoSave = true
                                        }.onFailure { error ->
                                            Logger.e(HISTORY_REFRESH_TAG, "Auto-save during background refresh failed for ${entry.threadId}", error)
                                        }
                                    }
                                }

                                val updatedEntry = entry.copy(
                                    title = resolvedTitle,
                                    titleImageUrl = opPost?.thumbnailUrl ?: entry.titleImageUrl,
                                    boardName = page.boardTitle ?: entry.boardName.ifBlank { board?.name.orEmpty() },
                                    replyCount = page.posts.size,
                                    hasAutoSave = hasAutoSave
                                )
                                updatesMutex.withLock {
                                    updates[key] = updatedEntry
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                if (isNotFound(e)) {
                                    markSkipped(key)
                                    // FIX: 404/410の場合、自動保存があるかチェック
                                    val hasAutoSave = autoSavedThreadRepository?.let { repo ->
                                        runCatching {
                                            repo.loadThreadMetadata(entry.threadId).isSuccess
                                        }.getOrDefault(false)
                                    } ?: false

                                    if (hasAutoSave && !entry.hasAutoSave) {
                                        // 自動保存があることを履歴に反映
                                        updatesMutex.withLock {
                                            updates[key] = entry.copy(hasAutoSave = true)
                                        }
                                        Logger.i(HISTORY_REFRESH_TAG, "Thread ${entry.threadId} not found but has auto-save")
                                    } else {
                                        Logger.i(HISTORY_REFRESH_TAG, "Skip thread ${entry.threadId} (not found)")
                                    }
                                } else {
                                    Logger.e(HISTORY_REFRESH_TAG, "Failed to refresh ${entry.threadId}", e)
                                    updatesMutex.withLock {
                                        // FIX: エラーリストの上限チェック（メモリ使用量制限）
                                        if (errors.size < maxErrorsToTrack) {
                                            errors.add(ErrorDetail(
                                                threadId = entry.threadId,
                                                message = e.message ?: "Unknown error"
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.forEach { it.await() } // Wait for current batch to complete

                // FIX: 定期的にupdatesをフラッシュしてメモリ使用量を制限
                updatesMutex.withLock {
                    if (updates.size >= maxUpdatesToAccumulate) {
                        Logger.i(HISTORY_REFRESH_TAG, "Flushing ${updates.size} updates to prevent memory spike")
                        val latestHistory = stateStore.history.first()
                        val merged = latestHistory.map { entry ->
                            updates[historyKeyForEntry(entry)] ?: entry
                        }
                        stateStore.setHistory(merged)
                        updates.clear()
                    }
                }
            }
        }

        if (updates.isNotEmpty()) {
            val latestHistory = stateStore.history.first()
            val merged = latestHistory.map { entry ->
                updates[historyKeyForEntry(entry)] ?: entry
            }
            stateStore.setHistory(merged)
        }

        // FIX: エラー情報をより詳細に記録
        if (errors.isNotEmpty()) {
            val errorRate = (errors.size.toFloat() / history.size * 100).toInt()
            _lastRefreshError.value = RefreshError(
                errorCount = errors.size,
                totalThreads = history.size,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                errors = errors.take(10) // FIX: 最初の10件のエラーのみ保存してメモリ節約
            )

            // FIX: エラー率が高い場合は警告レベルを上げる
            if (errorRate > 50) {
                Logger.e(HISTORY_REFRESH_TAG, "History refresh completed with HIGH error rate: ${errors.size}/${history.size} ($errorRate%)")
            } else {
                Logger.w(HISTORY_REFRESH_TAG, "History refresh completed with ${errors.size}/${history.size} errors ($errorRate%)")
            }
        } else {
            _lastRefreshError.value = null
            Logger.i(HISTORY_REFRESH_TAG, "History refresh completed successfully for ${history.size} threads")
        }
    }

    fun clearSkippedThreads() {
        skipThreadIds.value = emptySet()
    }

    fun clearLastError() {
        _lastRefreshError.value = null
    }

    private fun markSkipped(key: HistoryKey) {
        skipThreadIds.update { it + key }
    }

    private fun historyKeyForEntry(entry: ThreadHistoryEntry): HistoryKey {
        val boardKey = entry.boardId.ifBlank { entry.boardUrl }
        return HistoryKey(boardKey = boardKey, threadId = entry.threadId)
    }

    private fun isNotFound(t: Throwable): Boolean {
        val status = (t as? ClientRequestException)?.response?.status?.value
        return status == 404 || status == 410
    }
}
