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
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.resolveThreadTitle
import io.ktor.client.HttpClient
import com.valoser.futacha.shared.network.NetworkException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
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
private const val REFRESH_ABORT_REASON_PREFIX = "Aborting history refresh due to persistent"

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
    private val updatesMutex = Mutex()
    private val archiveSearchJson = Json { ignoreUnknownKeys = true }
    private val effectiveThreadFetchTimeoutMillis = threadFetchTimeoutMillis.coerceAtLeast(1_000L)
    private val autoSaveScope = CoroutineScope(SupervisorJob() + dispatcher)
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
            val autoSaveService = if (
                httpClient != null &&
                fileSystem != null &&
                autoSavedThreadRepository != null
            ) {
                ThreadSaveService(httpClient, fileSystem)
            } else {
                null
            }
            val updates = mutableMapOf<HistoryRefreshKey, ThreadHistoryEntry>()
            val errors = mutableListOf<ErrorDetail>()
            var attemptedCount = 0
            var successfulCount = 0
            var hardFailureCount = 0
            var autoSaveCount = 0
            var threadRefreshFailureCount = 0
            var archiveLookupFailureCount = 0
            val statsMutex = Mutex()
            val errorsMutex = Mutex()
            // FIX: エラーリストの最大サイズを設定（メモリ使用量制限）
            val maxErrorsToTrack = 100 // 最大100件まで記録、表示は10件
            // FIX: updatesマップの最大サイズを設定（メモリ使用量制限）
            val maxUpdatesToAccumulate = 1000 // 最大1000件まで蓄積したらフラッシュ

            suspend fun recordError(
                threadId: String,
                message: String,
                stage: String
            ) {
                errorsMutex.withLock {
                    if (errors.size < maxErrorsToTrack) {
                        errors.add(
                            ErrorDetail(
                                threadId = threadId,
                                message = message,
                                stage = stage
                            )
                        )
                    }
                }
            }

            suspend fun stageEarlyAbortMessage(stage: String): String? {
                return statsMutex.withLock {
                    val failureCount: Int
                    val minAttempts: Int
                    val minFailures: Int
                    val threshold: Float
                    val label: String
                    when (stage) {
                        ERROR_STAGE_THREAD_REFRESH -> {
                            failureCount = threadRefreshFailureCount
                            minAttempts = THREAD_REFRESH_EARLY_ABORT_MIN_ATTEMPTS
                            minFailures = THREAD_REFRESH_EARLY_ABORT_MIN_FAILURES
                            threshold = THREAD_REFRESH_EARLY_ABORT_FAILURE_RATE
                            label = "thread refresh"
                        }
                        ERROR_STAGE_ARCHIVE_LOOKUP -> {
                            failureCount = archiveLookupFailureCount
                            minAttempts = ARCHIVE_LOOKUP_EARLY_ABORT_MIN_ATTEMPTS
                            minFailures = ARCHIVE_LOOKUP_EARLY_ABORT_MIN_FAILURES
                            threshold = ARCHIVE_LOOKUP_EARLY_ABORT_FAILURE_RATE
                            label = "archive lookup"
                        }
                        else -> return@withLock null
                    }
                    if (attemptedCount < minAttempts) return@withLock null
                    if (successfulCount > 0) return@withLock null
                    if (failureCount < minFailures) return@withLock null
                    val failureRate = failureCount.toFloat() / attemptedCount.toFloat()
                    if (failureRate < threshold) return@withLock null
                    val ratePercent = (failureRate * 100).toInt()
                    "Aborting history refresh due to persistent $label failures " +
                        "($failureCount/$attemptedCount, ${ratePercent}%)"
                }
            }

            // FIX: Process in batches to avoid creating thousands of coroutines at once
            // This prevents memory spikes when history size is large.
            // Keep it close to effective concurrency to reduce queued coroutine buildup.
            val batchSize = (effectiveMaxConcurrency * 2).coerceIn(1, 10)

            suspend fun flushPendingUpdates(force: Boolean): Boolean {
                val flushSnapshot = updatesMutex.withLock {
                    if (!force && updates.size < maxUpdatesToAccumulate) {
                        null
                    } else if (updates.isEmpty()) {
                        null
                    } else {
                        if (!force) {
                            Logger.i(HISTORY_REFRESH_TAG, "Flushing ${updates.size} updates to prevent memory spike")
                        }
                        updates.toMap()
                    }
                }
                if (flushSnapshot != null) {
                    var lastError: Throwable? = null
                    repeat(HISTORY_FLUSH_MAX_RETRIES) { attempt ->
                        try {
                            stateStore.mergeHistoryEntries(flushSnapshot.values)
                            updatesMutex.withLock {
                                flushSnapshot.forEach { (key, entry) ->
                                    if (updates[key] == entry) {
                                        updates.remove(key)
                                    }
                                }
                            }
                            return true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (error: Throwable) {
                            lastError = error
                            val isLastAttempt = attempt >= HISTORY_FLUSH_MAX_RETRIES - 1
                            if (!isLastAttempt) {
                                Logger.w(
                                    HISTORY_REFRESH_TAG,
                                    "Retrying history flush (${attempt + 1}/$HISTORY_FLUSH_MAX_RETRIES): ${error.message}"
                                )
                                val backoffMultiplier = 1L shl attempt
                                val retryDelay = (
                                    HISTORY_FLUSH_RETRY_DELAY_MILLIS * backoffMultiplier
                                    ).coerceAtMost(HISTORY_FLUSH_RETRY_MAX_DELAY_MILLIS)
                                delay(retryDelay)
                                yield()
                            }
                        }
                    }
                    val failure = lastError
                    Logger.e(
                        HISTORY_REFRESH_TAG,
                        "Failed to flush ${flushSnapshot.size} history updates after $HISTORY_FLUSH_MAX_RETRIES attempts",
                        failure
                    )
                    recordError(
                        threadId = "history-flush",
                        message = failure?.message ?: "Failed to flush history updates",
                        stage = ERROR_STAGE_HISTORY_FLUSH
                    )
                    statsMutex.withLock {
                        hardFailureCount += 1
                    }
                    return false
                }
                return true
            }
            flushOnExit = { force -> flushPendingUpdates(force) }

            supervisorScope {
                var batchStart = 0
                while (batchStart < history.size) {
                    val batchEndExclusive = minOf(batchStart + batchSize, history.size)
                    val batch = history.subList(batchStart, batchEndExclusive)
                    val batchJobs = batch.map { entry ->
                        async {
                            try {
                                val board = resolveBoardForEntry(entry, boardById, boardByBaseUrl)
                                val key = buildHistoryRefreshKey(entry, boardById, boardByBaseUrl)
                                if (isThreadSkipped(key, refreshStartedAt)) return@async
                                val baseUrl = board?.url
                                    ?: entry.boardUrl.takeIf { it.isNotBlank() }?.let {
                                        runCatching { BoardUrlResolver.resolveBoardBaseUrl(it) }.getOrNull()
                                    }
                                if (baseUrl.isNullOrBlank() || baseUrl.contains("example.com", ignoreCase = true)) {
                                    return@async
                                }

                                semaphore.withPermit {
                                    try {
                                        statsMutex.withLock {
                                            attemptedCount += 1
                                        }
                                        val page = withTimeoutOrNull(effectiveThreadFetchTimeoutMillis) {
                                            repository.getThread(baseUrl, entry.threadId)
                                        } ?: throw NetworkException(
                                            "Thread fetch timed out for ${entry.threadId}"
                                        )
                                        val opPost = page.posts.firstOrNull()
                                        val resolvedTitle = resolveThreadTitle(opPost, entry.title)
                                        statsMutex.withLock {
                                            successfulCount += 1
                                        }
                                        val updatedEntry = entry.copy(
                                            title = resolvedTitle,
                                            titleImageUrl = opPost?.thumbnailUrl ?: entry.titleImageUrl,
                                            boardName = page.boardTitle ?: entry.boardName.ifBlank { board?.name.orEmpty() },
                                            replyCount = page.posts.size,
                                            hasAutoSave = entry.hasAutoSave
                                        )
                                        updatesMutex.withLock {
                                            updates[key] = updatedEntry
                                        }

                                        // 背景更新でも本文・メディアを自動保存
                                        // FIX: 既に自動保存済みかつレス数が変わらない場合は保存を省略してI/O負荷を抑える
                                        val shouldAutoSave = !entry.hasAutoSave || page.posts.size != entry.replyCount
                                        if (shouldAutoSave && autoSaveService != null && autoSavedThreadRepository != null) {
                                            autoSaveScope.launch {
                                                val nowForBudgetCheck = Clock.System.now().toEpochMilliseconds()
                                                var autoSaveSlotReserved = false
                                                val allowAutoSave = statsMutex.withLock {
                                                    if (
                                                        autoSaveDeadline != null &&
                                                        nowForBudgetCheck > autoSaveDeadline
                                                    ) {
                                                        false
                                                    } else if (autoSaveCount >= maxAutoSavesPerRefresh) {
                                                        false
                                                    } else {
                                                        autoSaveCount += 1
                                                        autoSaveSlotReserved = true
                                                        true
                                                    }
                                                }
                                                if (!allowAutoSave) {
                                                    if (autoSaveDeadline != null && nowForBudgetCheck > autoSaveDeadline) {
                                                        Logger.w(HISTORY_REFRESH_TAG, "Auto-save budget exceeded, skipping auto-save for ${entry.threadId}")
                                                    } else {
                                                        Logger.d(
                                                            HISTORY_REFRESH_TAG,
                                                            "Auto-save limit reached ($maxAutoSavesPerRefresh), skipping ${entry.threadId}"
                                                        )
                                                    }
                                                    return@launch
                                                }
                                                try {
                                                    val resolvedBoardId = entry.boardId.ifBlank {
                                                        board?.id
                                                            ?.takeIf { it.isNotBlank() }
                                                            ?: runCatching { BoardUrlResolver.resolveBoardSlug(baseUrl) }.getOrDefault("")
                                                    }
                                                    val saved = autoSaveSemaphore.withPermit {
                                                        if (autoSaveDeadline != null && Clock.System.now().toEpochMilliseconds() > autoSaveDeadline) {
                                                            Logger.w(HISTORY_REFRESH_TAG, "Auto-save budget exceeded while waiting permit, skipping ${entry.threadId}")
                                                            statsMutex.withLock {
                                                                if (autoSaveSlotReserved && autoSaveCount > 0) {
                                                                    autoSaveCount -= 1
                                                                }
                                                            }
                                                            autoSaveSlotReserved = false
                                                            return@withPermit null
                                                        }
                                                        withTimeoutOrNull(AUTO_SAVE_THREAD_TIMEOUT_MILLIS) {
                                                            autoSaveService.saveThread(
                                                                threadId = entry.threadId,
                                                                boardId = resolvedBoardId,
                                                                boardName = page.boardTitle ?: entry.boardName.ifBlank { board?.name.orEmpty() },
                                                                boardUrl = baseUrl,
                                                                title = resolvedTitle,
                                                                expiresAtLabel = page.expiresAtLabel,
                                                                posts = page.posts,
                                                                baseDirectory = AUTO_SAVE_DIRECTORY,
                                                                writeMetadata = true
                                                            ).getOrThrow()
                                                        }
                                                    }
                                                    if (saved != null) {
                                                        autoSavedThreadRepository.addThreadToIndex(saved)
                                                            .onFailure { Logger.e(HISTORY_REFRESH_TAG, "Failed to index auto-saved thread ${entry.threadId}", it) }
                                                        runCatching {
                                                            stateStore.upsertHistoryEntry(updatedEntry.copy(hasAutoSave = true))
                                                        }.onFailure {
                                                            Logger.w(
                                                                HISTORY_REFRESH_TAG,
                                                                "Failed to update history hasAutoSave flag for ${entry.threadId}: ${it.message}"
                                                            )
                                                        }
                                                    } else {
                                                        Logger.w(HISTORY_REFRESH_TAG, "Auto-save timed out for ${entry.threadId}")
                                                    }
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (error: Throwable) {
                                                    Logger.e(HISTORY_REFRESH_TAG, "Auto-save during background refresh failed for ${entry.threadId}", error)
                                                }
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                        if (isHistoryRefreshNotFound(e)) {
                                            when (
                                                val archiveResult = tryRefreshHistoryEntryFromArchive(
                                                    entry = entry,
                                                    board = board,
                                                    httpClient = httpClient,
                                                    repository = repository,
                                                    fetchTimeoutMillis = effectiveThreadFetchTimeoutMillis,
                                                    archiveSearchJson = archiveSearchJson,
                                                    tag = HISTORY_REFRESH_TAG
                                                )
                                            ) {
                                                is ArchiveRefreshResult.Success -> {
                                                    statsMutex.withLock {
                                                        successfulCount += 1
                                                    }
                                                    updatesMutex.withLock {
                                                        updates[key] = archiveResult.entry
                                                    }
                                                    return@withPermit
                                                }
                                                ArchiveRefreshResult.NotFound,
                                                ArchiveRefreshResult.NoMatch -> {
                                                    markHistoryThreadSkipped(skipThreadIds, key)
                                                }
                                                is ArchiveRefreshResult.Error -> {
                                                    recordError(
                                                        threadId = entry.threadId,
                                                        message = archiveResult.message,
                                                        stage = ERROR_STAGE_ARCHIVE_LOOKUP
                                                    )
                                                    statsMutex.withLock {
                                                        hardFailureCount += 1
                                                        archiveLookupFailureCount += 1
                                                    }
                                                    val abortReason = stageEarlyAbortMessage(ERROR_STAGE_ARCHIVE_LOOKUP)
                                                    if (abortReason != null) {
                                                        recordError(
                                                            threadId = "refresh-abort",
                                                            message = abortReason,
                                                            stage = ERROR_STAGE_REFRESH_ABORT
                                                        )
                                                        Logger.e(HISTORY_REFRESH_TAG, abortReason)
                                                        throw NetworkException(abortReason)
                                                    }
                                                    return@withPermit
                                                }
                                            }
                                            // FIX: 404/410の場合、自動保存があるかチェック
                                            val hasAutoSave = autoSavedThreadRepository?.let { repo ->
                                                try {
                                                    val resolvedBoardId = entry.boardId.ifBlank {
                                                        board?.id
                                                            ?.takeIf { it.isNotBlank() }
                                                            ?: runCatching { BoardUrlResolver.resolveBoardSlug(baseUrl) }.getOrDefault("")
                                                    }
                                                    repo.loadThreadMetadata(
                                                        threadId = entry.threadId,
                                                        boardId = resolvedBoardId.ifBlank { null }
                                                    ).isSuccess
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (_: Throwable) {
                                                    false
                                                }
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
                                            recordError(
                                                threadId = entry.threadId,
                                                message = e.message ?: "Unknown error",
                                                stage = ERROR_STAGE_THREAD_REFRESH
                                            )
                                            statsMutex.withLock {
                                                hardFailureCount += 1
                                                threadRefreshFailureCount += 1
                                            }
                                            val abortReason = stageEarlyAbortMessage(ERROR_STAGE_THREAD_REFRESH)
                                            if (abortReason != null) {
                                                recordError(
                                                    threadId = "refresh-abort",
                                                    message = abortReason,
                                                    stage = ERROR_STAGE_REFRESH_ABORT
                                                )
                                                Logger.e(HISTORY_REFRESH_TAG, abortReason, e)
                                                throw NetworkException(abortReason)
                                            }
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (error: Throwable) {
                                if (isRefreshAbortSignal(error)) {
                                    throw error
                                }
                                Logger.e(HISTORY_REFRESH_TAG, "Unexpected failure while refreshing ${entry.threadId}", error)
                                recordError(
                                    threadId = entry.threadId,
                                    message = error.message ?: "Unexpected refresh failure",
                                    stage = ERROR_STAGE_THREAD_REFRESH
                                )
                                statsMutex.withLock {
                                    hardFailureCount += 1
                                    threadRefreshFailureCount += 1
                                }
                            }
                        }
                    }
                    batchJobs.forEach { it.await() } // Wait for current batch to complete

                    // 定期的にupdatesをフラッシュしてメモリ使用量を制限
                    val batchFlushSucceeded = flushPendingUpdates(force = false)
                    if (!batchFlushSucceeded) {
                        throw NetworkException("Failed to persist intermediate history updates")
                    }
                    batchStart = batchEndExclusive
                    yield()
                }
            }

            val finalFlushSucceeded = flushPendingUpdates(force = true)
            if (!finalFlushSucceeded) {
                throw NetworkException("Failed to persist refreshed history updates")
            }

            // FIX: エラー情報をより詳細に記録
            val errorsSnapshot = errorsMutex.withLock { errors.toList() }
            if (errorsSnapshot.isNotEmpty()) {
                val errorRate = (errorsSnapshot.size.toFloat() / history.size * 100).toInt()
                val refreshError = buildHistoryRefreshError(
                    totalThreads = history.size,
                    details = errorsSnapshot
                )
                _lastRefreshError.value = refreshError
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
                _lastRefreshError.value = null
                publishedDetailedError = false
                Logger.i(HISTORY_REFRESH_TAG, "History refresh completed successfully for ${history.size} threads")
            }

            val (attempted, successful, hardFailures) = statsMutex.withLock {
                Triple(attemptedCount, successfulCount, hardFailureCount)
            }
            if (attempted > 0 && successful == 0 && hardFailures > 0) {
                throw NetworkException("History refresh failed for all $attempted attempted threads")
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
                _lastRefreshError.value = buildHistoryRefreshError(
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
            Logger.e(HISTORY_REFRESH_TAG, "History refresh aborted by fatal error", error)
            throw error
        } finally {
            if (locked) {
                refreshMutex.unlock()
            }
        }
    }

    fun clearSkippedThreads() {
        skipThreadIds.value = emptyMap()
    }

    fun clearLastError() {
        _lastRefreshError.value = null
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

    private fun resolveBoardForEntry(
        entry: ThreadHistoryEntry,
        boardById: Map<String, BoardSummary>,
        boardByBaseUrl: Map<String, BoardSummary>
    ): BoardSummary? {
        return resolveHistoryBoardForEntry(entry, boardById, boardByBaseUrl)
    }

    private fun normalizeBoardKey(url: String?): String? {
        return normalizeHistoryBoardKey(url)
    }

    private fun isRefreshAbortSignal(t: Throwable): Boolean {
        return isHistoryRefreshAbortSignal(t, REFRESH_ABORT_REASON_PREFIX)
    }
}
