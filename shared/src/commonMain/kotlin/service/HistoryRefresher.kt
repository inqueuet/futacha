package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.fetchArchiveSearchResults
import com.valoser.futacha.shared.network.selectLatestArchiveMatch
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
import io.ktor.http.Url
import com.valoser.futacha.shared.network.NetworkException
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
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Clock

private const val HISTORY_REFRESH_TAG = "HistoryRefresher"
private const val SKIP_THREAD_TTL_MILLIS = 12 * 60 * 60 * 1000L
private const val DEFAULT_THREAD_FETCH_TIMEOUT_MILLIS = 30_000L
private const val DEFAULT_MAX_AUTO_SAVES_PER_REFRESH = 5

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
    private val skipThreadIds = MutableStateFlow<Map<HistoryKey, Long>>(emptyMap())
    private val refreshMutex = Mutex()
    private val updatesMutex = Mutex()
    private val archiveSearchJson = Json { ignoreUnknownKeys = true }

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
    private sealed interface ArchiveRefreshResult {
        data class Success(val entry: ThreadHistoryEntry) : ArchiveRefreshResult
        data object NotFound : ArchiveRefreshResult
        data object NoMatch : ArchiveRefreshResult
        data object Error : ArchiveRefreshResult
    }

    @OptIn(ExperimentalTime::class)
    suspend fun refresh(
        boardsSnapshot: List<BoardSummary>? = null,
        historySnapshot: List<ThreadHistoryEntry>? = null,
        autoSaveBudgetMillis: Long? = null
    ) = withContext(dispatcher) {
        val locked = refreshMutex.tryLock()
        if (!locked) {
            Logger.w(HISTORY_REFRESH_TAG, "Refresh skipped: another refresh is already running")
            return@withContext
        }
        try {
            val refreshStartedAt = Clock.System.now().toEpochMilliseconds()
            val autoSaveDeadline = autoSaveBudgetMillis?.let { refreshStartedAt + it }
            val boards = boardsSnapshot ?: stateStore.boards.first()
            val history = historySnapshot ?: stateStore.history.first()
            if (history.isEmpty()) return@withContext
            val boardById = boards.associateBy { it.id }
            val boardByBaseUrl = boards
                .mapNotNull { board ->
                    normalizeBoardKey(board.url)?.let { key -> key to board }
                }
                .toMap()
            val activeHistoryKeys = history
                .mapTo(mutableSetOf()) { entry ->
                    historyKeyForEntry(entry, boardById, boardByBaseUrl)
                }
            skipThreadIds.update { existing ->
                existing.filter { (key, skippedAtMillis) ->
                    key in activeHistoryKeys &&
                        (refreshStartedAt - skippedAtMillis) < SKIP_THREAD_TTL_MILLIS
                }
            }

            val semaphore = Semaphore(maxConcurrency)
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
            val updates = mutableMapOf<HistoryKey, ThreadHistoryEntry>()
            val errors = mutableListOf<ErrorDetail>()
            var attemptedCount = 0
            var successfulCount = 0
            var hardFailureCount = 0
            var autoSaveCount = 0
            val statsMutex = Mutex()
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
                        val board = resolveBoardForEntry(entry, boardById, boardByBaseUrl)
                        val key = historyKeyForEntry(entry, boardById, boardByBaseUrl)
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
                                val page = withTimeout(threadFetchTimeoutMillis) {
                                    repository.getThread(baseUrl, entry.threadId)
                                }
                                val opPost = page.posts.firstOrNull()
                                val resolvedTitle = resolveThreadTitle(opPost, entry.title)
                                var hasAutoSave = entry.hasAutoSave
                                statsMutex.withLock {
                                    successfulCount += 1
                                }

                                // 背景更新でも本文・メディアを自動保存
                                // FIX: 既に自動保存済みかつレス数が変わらない場合は保存を省略してI/O負荷を抑える
                                val shouldAutoSave = !entry.hasAutoSave || page.posts.size != entry.replyCount
                                if (shouldAutoSave && autoSaveService != null && autoSavedThreadRepository != null) {
                                    val allowAutoSave = statsMutex.withLock {
                                        if (autoSaveCount >= maxAutoSavesPerRefresh) {
                                            false
                                        } else {
                                            autoSaveCount += 1
                                            true
                                        }
                                    }
                                    if (!allowAutoSave) {
                                        Logger.d(
                                            HISTORY_REFRESH_TAG,
                                            "Auto-save limit reached ($maxAutoSavesPerRefresh), skipping ${entry.threadId}"
                                        )
                                    } else if (autoSaveDeadline != null && Clock.System.now().toEpochMilliseconds() > autoSaveDeadline) {
                                        Logger.w(HISTORY_REFRESH_TAG, "Auto-save budget exceeded, skipping auto-save for ${entry.threadId}")
                                    } else {
                                        try {
                                            val saved = autoSaveSemaphore.withPermit {
                                                if (autoSaveDeadline != null && Clock.System.now().toEpochMilliseconds() > autoSaveDeadline) {
                                                    Logger.w(HISTORY_REFRESH_TAG, "Auto-save budget exceeded while waiting permit, skipping ${entry.threadId}")
                                                    return@withPermit null
                                                }
                                                autoSaveService.saveThread(
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
                                            }
                                            if (saved != null) {
                                                autoSavedThreadRepository.addThreadToIndex(saved)
                                                    .onFailure { Logger.e(HISTORY_REFRESH_TAG, "Failed to index auto-saved thread ${entry.threadId}", it) }
                                                hasAutoSave = true
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (error: Throwable) {
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
                                    when (val archiveResult = tryRefreshFromArchive(entry, board)) {
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
                                            markSkipped(key)
                                        }
                                        ArchiveRefreshResult.Error -> {
                                            return@withPermit
                                        }
                                    }
                                    // FIX: 404/410の場合、自動保存があるかチェック
                                    val hasAutoSave = autoSavedThreadRepository?.let { repo ->
                                        try {
                                            repo.loadThreadMetadata(
                                                threadId = entry.threadId,
                                                boardId = entry.boardId.ifBlank { board?.id }
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
                                    updatesMutex.withLock {
                                        // FIX: エラーリストの上限チェック（メモリ使用量制限）
                                        if (errors.size < maxErrorsToTrack) {
                                            errors.add(ErrorDetail(
                                                threadId = entry.threadId,
                                                message = e.message ?: "Unknown error"
                                            ))
                                        }
                                    }
                                    statsMutex.withLock {
                                        hardFailureCount += 1
                                    }
                                }
                            }
                        }
                        }
                    }.forEach { it.await() } // Wait for current batch to complete

                    // FIX: 定期的にupdatesをフラッシュしてメモリ使用量を制限
                    val flushSnapshot = updatesMutex.withLock {
                        if (updates.size >= maxUpdatesToAccumulate) {
                            Logger.i(HISTORY_REFRESH_TAG, "Flushing ${updates.size} updates to prevent memory spike")
                            val snapshot = updates.toMap()
                            updates.clear()
                            snapshot
                        } else {
                            null
                        }
                    }
                    if (flushSnapshot != null) {
                        stateStore.mergeHistoryEntries(flushSnapshot.values)
                    }
                }
            }

            val remainingUpdates = updatesMutex.withLock {
                if (updates.isEmpty()) {
                    null
                } else {
                    val snapshot = updates.toMap()
                    updates.clear()
                    snapshot
                }
            }
            if (remainingUpdates != null) {
                stateStore.mergeHistoryEntries(remainingUpdates.values)
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

            val (attempted, successful, hardFailures) = statsMutex.withLock {
                Triple(attemptedCount, successfulCount, hardFailureCount)
            }
            if (attempted > 0 && successful == 0 && hardFailures > 0) {
                throw NetworkException("History refresh failed for all $attempted attempted threads")
            }
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

    private fun markSkipped(key: HistoryKey) {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        skipThreadIds.update { it + (key to nowMillis) }
    }

    private fun isThreadSkipped(key: HistoryKey, nowMillis: Long): Boolean {
        val skippedAtMillis = skipThreadIds.value[key] ?: return false
        return (nowMillis - skippedAtMillis) < SKIP_THREAD_TTL_MILLIS
    }

    private fun historyKeyForEntry(
        entry: ThreadHistoryEntry,
        boardById: Map<String, BoardSummary>,
        boardByBaseUrl: Map<String, BoardSummary>
    ): HistoryKey {
        val board = resolveBoardForEntry(entry, boardById, boardByBaseUrl)
        val boardKey = entry.boardId
            .takeIf { it.isNotBlank() }
            ?: board?.id?.takeIf { it.isNotBlank() }
            ?: entry.boardUrl.ifBlank { board?.url.orEmpty() }
        return HistoryKey(boardKey = boardKey, threadId = entry.threadId)
    }

    private fun resolveBoardForEntry(
        entry: ThreadHistoryEntry,
        boardById: Map<String, BoardSummary>,
        boardByBaseUrl: Map<String, BoardSummary>
    ): BoardSummary? {
        entry.boardId.takeIf { it.isNotBlank() }?.let { boardId ->
            boardById[boardId]?.let { return it }
        }
        val key = normalizeBoardKey(entry.boardUrl) ?: return null
        return boardByBaseUrl[key]
    }

    private fun normalizeBoardKey(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val resolved = runCatching { BoardUrlResolver.resolveBoardBaseUrl(url) }.getOrDefault(url)
        return resolved.trimEnd('/').lowercase().ifBlank { null }
    }

    private suspend fun tryRefreshFromArchive(
        entry: ThreadHistoryEntry,
        board: BoardSummary?
    ): ArchiveRefreshResult {
        val client = httpClient ?: return ArchiveRefreshResult.NoMatch
        val scope = extractArchiveSearchScope(board?.url ?: entry.boardUrl)
        val queryCandidates = buildList {
            if (entry.threadId.isNotBlank()) add(entry.threadId)
            if (entry.title.isNotBlank()) add(entry.title)
        }.distinct()
        if (queryCandidates.isEmpty()) return ArchiveRefreshResult.NoMatch

        queryCandidates.forEach { query ->
            val results = try {
                withTimeout(threadFetchTimeoutMillis) {
                    fetchArchiveSearchResults(client, query, scope, archiveSearchJson)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                Logger.w(HISTORY_REFRESH_TAG, "Archive search failed for ${entry.threadId}: ${error.message}")
                return ArchiveRefreshResult.Error
            }
            val matched = selectLatestArchiveMatch(results, entry.threadId) ?: return@forEach
            return resolveArchiveEntry(entry, board, matched)
        }
        return ArchiveRefreshResult.NoMatch
    }

    private suspend fun resolveArchiveEntry(
        entry: ThreadHistoryEntry,
        board: BoardSummary?,
        match: ArchiveSearchItem
    ): ArchiveRefreshResult {
        val baseUrl = resolveArchiveBaseUrl(match.htmlUrl, board?.url ?: entry.boardUrl)
        val boardName = entry.boardName.ifBlank { board?.name.orEmpty() }
        val pageResult = try {
            Result.success(
                withTimeout(threadFetchTimeoutMillis) {
                    if (match.htmlUrl.isNotBlank()) {
                        repository.getThreadByUrl(match.htmlUrl)
                    } else {
                        baseUrl?.let { repository.getThread(it, entry.threadId) }
                    }
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
        val page = pageResult.getOrNull()
        val pageError = pageResult.exceptionOrNull()
        if (page == null && pageError != null && isNotFound(pageError)) {
            Logger.i(HISTORY_REFRESH_TAG, "Archive thread missing for ${entry.threadId}")
            return ArchiveRefreshResult.NotFound
        }
        return if (page != null) {
            val opPost = page.posts.firstOrNull()
            val resolvedTitle = resolveThreadTitle(opPost, match.title ?: entry.title)
            val resolvedImage = opPost?.thumbnailUrl ?: match.thumbUrl ?: entry.titleImageUrl
            Logger.i(HISTORY_REFRESH_TAG, "Archive refresh succeeded for ${entry.threadId}")
            ArchiveRefreshResult.Success(entry.copy(
                title = resolvedTitle,
                titleImageUrl = resolvedImage,
                boardName = page.boardTitle ?: boardName,
                boardUrl = baseUrl ?: entry.boardUrl,
                replyCount = page.posts.size
            ))
        } else {
            ArchiveRefreshResult.Error
        }
    }

    private fun resolveArchiveBaseUrl(threadUrl: String, fallbackBoardUrl: String?): String? {
        if (threadUrl.isBlank()) return fallbackBoardUrl?.takeIf { it.isNotBlank() }
        return runCatching {
            val url = Url(threadUrl)
            val segments = url.encodedPath.split('/').filter { it.isNotBlank() }
            val boardSegments = segments.takeWhile { it.lowercase() != "res" }
            if (boardSegments.isEmpty()) {
                fallbackBoardUrl?.takeIf { it.isNotBlank() }
            } else {
                val path = "/" + boardSegments.joinToString("/")
                buildString {
                    append(url.protocol.name)
                    append("://")
                    append(url.host)
                    if (url.port != url.protocol.defaultPort) {
                        append(":${url.port}")
                    }
                    append(path.trimEnd('/'))
                }
            }
        }.getOrElse { fallbackBoardUrl?.takeIf { it.isNotBlank() } }
    }

    private fun isNotFound(t: Throwable): Boolean {
        val status = (t as? ClientRequestException)?.response?.status?.value
            ?: (t as? NetworkException)?.statusCode
        return status == 404 || status == 410
    }
}
