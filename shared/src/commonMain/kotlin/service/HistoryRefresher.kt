package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val maxConcurrency: Int = 4
    // Caller owns repository lifecycle
) {
    private val skipThreadIds = MutableStateFlow<Set<String>>(emptySet())
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

    @OptIn(ExperimentalTime::class)
    suspend fun refresh(
        boardsSnapshot: List<BoardSummary>? = null,
        historySnapshot: List<ThreadHistoryEntry>? = null
    ) = withContext(dispatcher) {
        val boards = boardsSnapshot ?: stateStore.boards.first()
        val history = historySnapshot ?: stateStore.history.first()
        if (history.isEmpty()) return@withContext

        val semaphore = Semaphore(maxConcurrency)
        val updates = mutableMapOf<String, ThreadHistoryEntry>()
        val errors = mutableListOf<ErrorDetail>()
        val skipped = skipThreadIds.value

        coroutineScope {
            history.forEach { entry ->
                if (entry.threadId in skipped) return@forEach
                val baseUrl = boards.firstOrNull { it.id == entry.boardId }?.url
                    ?: entry.boardUrl.takeIf { it.isNotBlank() }?.let {
                        runCatching { BoardUrlResolver.resolveBoardBaseUrl(it) }.getOrNull()
                    }
                if (baseUrl.isNullOrBlank() || baseUrl.contains("example.com", ignoreCase = true)) {
                    return@forEach
                }

                async {
                    semaphore.withPermit {
                        try {
                            val page = repository.getThread(baseUrl, entry.threadId)
                            val opPost = page.posts.firstOrNull()
                            val updatedEntry = entry.copy(
                                title = opPost?.subject?.takeIf { it.isNotBlank() } ?: entry.title,
                                titleImageUrl = opPost?.thumbnailUrl ?: entry.titleImageUrl,
                                boardName = page.boardTitle ?: entry.boardName,
                                replyCount = page.posts.size
                            )
                            updatesMutex.withLock {
                                updates[entry.threadId] = updatedEntry
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            if (isNotFound(e)) {
                                markSkipped(entry.threadId)
                                Logger.i(HISTORY_REFRESH_TAG, "Skip thread ${entry.threadId} (not found)")
                            } else {
                                Logger.e(HISTORY_REFRESH_TAG, "Failed to refresh ${entry.threadId}", e)
                                updatesMutex.withLock {
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
        }

        if (updates.isNotEmpty()) {
            val latestHistory = stateStore.history.first()
            val merged = latestHistory.map { entry ->
                updates[entry.threadId] ?: entry
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

    private fun markSkipped(threadId: String) {
        skipThreadIds.value = skipThreadIds.value + threadId
    }

    private fun isNotFound(t: Throwable): Boolean {
        val status = (t as? ClientRequestException)?.response?.status?.value
        return status == 404 || status == 410
    }
}
