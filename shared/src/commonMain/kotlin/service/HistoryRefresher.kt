package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import com.valoser.futacha.shared.network.NetworkException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.Clock

private const val HISTORY_REFRESH_TAG = "HistoryRefresher"
private const val SKIP_THREAD_TTL_MILLIS = 12 * 60 * 60 * 1000L
private const val DEFAULT_THREAD_FETCH_TIMEOUT_MILLIS = 30_000L
private const val DEFAULT_MAX_AUTO_SAVES_PER_REFRESH = 5
private const val HISTORY_FLUSH_MAX_RETRIES = 5
private const val HISTORY_FLUSH_RETRY_DELAY_MILLIS = 250L
private const val HISTORY_FLUSH_RETRY_MAX_DELAY_MILLIS = 2_000L
private const val AUTO_SAVE_THREAD_TIMEOUT_MILLIS = 90_000L
private const val ERROR_STAGE_HISTORY_FLUSH = "history_flush"
private const val ERROR_STAGE_ARCHIVE_LOOKUP = "archive_lookup"
private const val ERROR_STAGE_THREAD_REFRESH = "thread_refresh"
private const val ERROR_STAGE_REFRESH_ABORT = "refresh_abort"
private const val ERROR_STAGE_REFRESH_FATAL = "refresh_fatal"
private const val THREAD_REFRESH_EARLY_ABORT_MIN_ATTEMPTS = 25
private const val THREAD_REFRESH_EARLY_ABORT_MIN_FAILURES = 20
private const val THREAD_REFRESH_EARLY_ABORT_FAILURE_RATE = 0.9f
private const val ARCHIVE_LOOKUP_EARLY_ABORT_MIN_ATTEMPTS = 30
private const val ARCHIVE_LOOKUP_EARLY_ABORT_MIN_FAILURES = 20
private const val ARCHIVE_LOOKUP_EARLY_ABORT_FAILURE_RATE = 0.85f
private const val STATE_SNAPSHOT_READ_TIMEOUT_MILLIS = 10_000L
private const val ABORT_FLUSH_TIMEOUT_MILLIS = 5_000L

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
    private val maxConcurrency: Int = 4,
    private val autoSaveMaxConcurrency: Int = 1,
    private val threadFetchTimeoutMillis: Long = DEFAULT_THREAD_FETCH_TIMEOUT_MILLIS,
    private val maxAutoSavesPerRefresh: Int = DEFAULT_MAX_AUTO_SAVES_PER_REFRESH
    // Caller owns repository lifecycle
) {
    class RefreshAlreadyRunningException :
        IllegalStateException("History refresh is already running")

    private val skipThreadIds = MutableStateFlow<Map<HistoryRefreshKey, Long>>(emptyMap())
    private val refreshMutex = Mutex()
    private val archiveSearchJson = Json { ignoreUnknownKeys = true }
    private val effectiveThreadFetchTimeoutMillis = threadFetchTimeoutMillis.coerceAtLeast(1_000L)
    private var historyRefreshCursor = 0

    // FIX: エラー状態を公開
    private val _lastRefreshError = MutableStateFlow<RefreshError?>(null)
    val lastRefreshError: StateFlow<RefreshError?> = _lastRefreshError.asStateFlow()

    data class RefreshError(
        val errorCount: Int,
        val totalThreads: Int,
        val timestamp: Long,
        val errors: List<ErrorDetail>,
        val stageCounts: Map<String, Int> = emptyMap()
    )

    data class ErrorDetail(
        val threadId: String,
        val message: String,
        val stage: String = ERROR_STAGE_THREAD_REFRESH
    )

    @OptIn(ExperimentalTime::class)
    suspend fun refresh(
        boardsSnapshot: List<BoardSummary>? = null,
        historySnapshot: List<ThreadHistoryEntry>? = null,
        autoSaveBudgetMillis: Long? = null,
        maxThreadsPerRun: Int? = null
    ) = withContext(dispatcher) {
        val locked = refreshMutex.tryLock()
        if (!locked) {
            Logger.w(HISTORY_REFRESH_TAG, "Refresh skipped: another refresh is already running")
            throw RefreshAlreadyRunningException()
        }
        var totalThreadsInRun = 0
        var publishedDetailedError = false
        var flushOnExit: (suspend (Boolean) -> Boolean)? = null
        try {
            val boards = boardsSnapshot ?: withTimeoutOrNull(STATE_SNAPSHOT_READ_TIMEOUT_MILLIS) {
                stateStore.boards.first()
            } ?: throw IllegalStateException("Timed out while reading board snapshot")
            val fullHistory = historySnapshot ?: withTimeoutOrNull(STATE_SNAPSHOT_READ_TIMEOUT_MILLIS) {
                stateStore.history.first()
            } ?: throw IllegalStateException("Timed out while reading history snapshot")
            val refreshStartedAt = Clock.System.now().toEpochMilliseconds()
            val autoSaveDeadline = autoSaveBudgetMillis?.let { refreshStartedAt + it }
            if (fullHistory.isEmpty()) return@withContext
            val history = selectHistoryWindow(fullHistory, maxThreadsPerRun)
            totalThreadsInRun = history.size
            val boardById = boards.associateBy { it.id }
            val boardByBaseUrl = boards
                .mapNotNull { board ->
                    normalizeBoardKey(board.url)?.let { key -> key to board }
                }
                .toMap()
            val activeHistoryKeys = fullHistory
                .mapTo(mutableSetOf()) { entry ->
                    buildHistoryRefreshKey(entry, boardById, boardByBaseUrl)
                }
            skipThreadIds.update { existing ->
                existing.filter { (key, skippedAtMillis) ->
                    key in activeHistoryKeys &&
                        (refreshStartedAt - skippedAtMillis) < SKIP_THREAD_TTL_MILLIS
                }
            }

            val effectiveMaxConcurrency = maxConcurrency.coerceAtLeast(1)
            val semaphore = Semaphore(effectiveMaxConcurrency)
            val autoSaveSemaphore = Semaphore(autoSaveMaxConcurrency.coerceAtLeast(1))
            val autoSaveScope = CoroutineScope(coroutineContext)
            val autoSaveService = if (
                httpClient != null &&
                fileSystem != null &&
                autoSavedThreadRepository != null
            ) {
                ThreadSaveService(httpClient, fileSystem)
            } else {
                null
            }
            val threadRefreshAbortThreshold = HistoryRefreshAbortThreshold(
                label = "thread refresh",
                minAttempts = THREAD_REFRESH_EARLY_ABORT_MIN_ATTEMPTS,
                minFailures = THREAD_REFRESH_EARLY_ABORT_MIN_FAILURES,
                failureRateThreshold = THREAD_REFRESH_EARLY_ABORT_FAILURE_RATE
            )
            val archiveLookupAbortThreshold = HistoryRefreshAbortThreshold(
                label = "archive lookup",
                minAttempts = ARCHIVE_LOOKUP_EARLY_ABORT_MIN_ATTEMPTS,
                minFailures = ARCHIVE_LOOKUP_EARLY_ABORT_MIN_FAILURES,
                failureRateThreshold = ARCHIVE_LOOKUP_EARLY_ABORT_FAILURE_RATE
            )
            val stats = HistoryRefreshRunStats()
            val errors = HistoryRefreshErrorTracker(
                maxErrorsToTrack = 100
            )
            val updates = HistoryRefreshUpdateBuffer(
                stateStore = stateStore,
                tag = HISTORY_REFRESH_TAG,
                maxUpdatesToAccumulate = 1000,
                maxFlushRetries = HISTORY_FLUSH_MAX_RETRIES,
                flushRetryDelayMillis = HISTORY_FLUSH_RETRY_DELAY_MILLIS,
                flushRetryMaxDelayMillis = HISTORY_FLUSH_RETRY_MAX_DELAY_MILLIS,
                recordError = errors::record,
                onFlushFailure = stats::recordHardFailure
            )

            // FIX: Process in batches to avoid creating thousands of coroutines at once
            // This prevents memory spikes when history size is large.
            // Keep it close to effective concurrency to reduce queued coroutine buildup.
            val batchSize = (effectiveMaxConcurrency * 2).coerceIn(1, 10)
            flushOnExit = { force -> updates.flush(force, ERROR_STAGE_HISTORY_FLUSH) }
            val runProcessor = HistoryRefreshRunProcessor(
                stateStore = stateStore,
                repository = repository,
                httpClient = httpClient,
                archiveSearchJson = archiveSearchJson,
                fetchSemaphore = semaphore,
                autoSaveSemaphore = autoSaveSemaphore,
                autoSaveScope = autoSaveScope,
                autoSaveService = autoSaveService,
                autoSavedThreadRepository = autoSavedThreadRepository,
                skipThreadIds = skipThreadIds,
                refreshStartedAt = refreshStartedAt,
                skipThreadTtlMillis = SKIP_THREAD_TTL_MILLIS,
                fetchTimeoutMillis = effectiveThreadFetchTimeoutMillis,
                autoSaveThreadTimeoutMillis = AUTO_SAVE_THREAD_TIMEOUT_MILLIS,
                autoSaveDeadline = autoSaveDeadline,
                maxAutoSavesPerRefresh = maxAutoSavesPerRefresh,
                threadRefreshAbortThreshold = threadRefreshAbortThreshold,
                archiveLookupAbortThreshold = archiveLookupAbortThreshold,
                stats = stats,
                errors = errors,
                updates = updates,
                threadRefreshStage = ERROR_STAGE_THREAD_REFRESH,
                archiveLookupStage = ERROR_STAGE_ARCHIVE_LOOKUP,
                refreshAbortStage = ERROR_STAGE_REFRESH_ABORT,
                tag = HISTORY_REFRESH_TAG
            )

            supervisorScope {
                var batchStart = 0
                while (batchStart < history.size) {
                    val batchEndExclusive = minOf(batchStart + batchSize, history.size)
                    val batch = history.subList(batchStart, batchEndExclusive)
                    val batchJobs = batch.map { entry ->
                        async {
                            runProcessor.processEntry(
                                entry = entry,
                                boardById = boardById,
                                boardByBaseUrl = boardByBaseUrl
                            )
                        }
                    }
                    batchJobs.forEach { it.await() } // Wait for current batch to complete

                    // 定期的にupdatesをフラッシュしてメモリ使用量を制限
                    val batchFlushSucceeded = updates.flush(
                        force = false,
                        failureStage = ERROR_STAGE_HISTORY_FLUSH
                    )
                    if (!batchFlushSucceeded) {
                        throw NetworkException("Failed to persist intermediate history updates")
                    }
                    batchStart = batchEndExclusive
                    yield()
                }
            }

            val finalFlushSucceeded = updates.flush(
                force = true,
                failureStage = ERROR_STAGE_HISTORY_FLUSH
            )
            if (!finalFlushSucceeded) {
                throw NetworkException("Failed to persist refreshed history updates")
            }

            // FIX: エラー情報をより詳細に記録
            val errorsSnapshot = errors.snapshot()
            if (errorsSnapshot.isNotEmpty()) {
                val errorRate = (errorsSnapshot.size.toFloat() / history.size * 100).toInt()
                val refreshError = buildHistoryRefreshError(
                    totalThreads = history.size,
                    details = errorsSnapshot
                )
                _lastRefreshError.update { refreshError }
                publishedDetailedError = true

                // FIX: エラー率が高い場合は警告レベルを上げる
                if (errorRate > 50) {
                    Logger.e(
                        HISTORY_REFRESH_TAG,
                        "History refresh completed with HIGH error rate: ${errorsSnapshot.size}/${history.size} ($errorRate%), stages=${refreshError.stageCounts}"
                    )
                } else {
                    Logger.w(
                        HISTORY_REFRESH_TAG,
                        "History refresh completed with ${errorsSnapshot.size}/${history.size} errors ($errorRate%), stages=${refreshError.stageCounts}"
                    )
                }
            } else {
                _lastRefreshError.update { null }
                publishedDetailedError = false
                Logger.i(HISTORY_REFRESH_TAG, "History refresh completed successfully for ${history.size} threads")
            }

            val statsSnapshot = stats.snapshot()
            if (
                statsSnapshot.attemptedCount > 0 &&
                statsSnapshot.successfulCount == 0 &&
                statsSnapshot.hardFailureCount > 0
            ) {
                throw NetworkException(
                    "History refresh failed for all ${statsSnapshot.attemptedCount} attempted threads"
                )
            }
        } catch (e: CancellationException) {
            bestEffortHistoryRefreshFlushOnAbort(
                flush = flushOnExit,
                reason = "cancelled",
                abortFlushTimeoutMillis = ABORT_FLUSH_TIMEOUT_MILLIS,
                tag = HISTORY_REFRESH_TAG
            )
            throw e
        } catch (error: Throwable) {
            bestEffortHistoryRefreshFlushOnAbort(
                flush = flushOnExit,
                reason = "failed: ${error.message.orEmpty()}",
                abortFlushTimeoutMillis = ABORT_FLUSH_TIMEOUT_MILLIS,
                tag = HISTORY_REFRESH_TAG
            )
            if (!publishedDetailedError) {
                _lastRefreshError.update {
                    buildHistoryRefreshError(
                    totalThreads = totalThreadsInRun,
                    details = listOf(
                        ErrorDetail(
                            threadId = "refresh-fatal",
                            message = error.message ?: "History refresh failed",
                            stage = ERROR_STAGE_REFRESH_FATAL
                        )
                    )
                )
                }
            }
            Logger.e(HISTORY_REFRESH_TAG, "History refresh aborted by fatal error", error)
            throw error
        } finally {
            if (locked) {
                refreshMutex.unlock()
            }
        }
    }

    fun close() {
        // Auto-save jobs are scoped to refresh() and cancel with the caller.
    }

    fun clearSkippedThreads() {
        skipThreadIds.update { emptyMap() }
    }

    fun clearLastError() {
        _lastRefreshError.update { null }
    }

    private fun isThreadSkipped(key: HistoryRefreshKey, nowMillis: Long): Boolean =
        isHistoryThreadSkipped(skipThreadIds, key, nowMillis, SKIP_THREAD_TTL_MILLIS)

    private fun selectHistoryWindow(
        history: List<ThreadHistoryEntry>,
        maxThreadsPerRun: Int?
    ): List<ThreadHistoryEntry> {
        val selection = selectHistoryRefreshWindow(
            history = history,
            maxThreadsPerRun = maxThreadsPerRun,
            cursor = historyRefreshCursor
        )
        historyRefreshCursor = selection.nextCursor
        return selection.entries
    }

    private fun normalizeBoardKey(url: String?): String? {
        return normalizeHistoryBoardKey(url)
    }
}
