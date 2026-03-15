package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.resolveThreadTitle
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

internal const val HISTORY_REFRESH_ABORT_REASON_PREFIX =
    "Aborting history refresh due to persistent"

internal data class HistoryRefreshWindowSelection(
    val entries: List<ThreadHistoryEntry>,
    val nextCursor: Int
)

internal data class HistoryRefreshResolvedEntry(
    val entry: ThreadHistoryEntry,
    val board: BoardSummary?,
    val key: HistoryRefreshKey,
    val baseUrl: String
)

internal data class HistoryRefreshKey(
    val boardKey: String,
    val threadId: String
)

internal fun buildHistoryBoardKey(
    entry: ThreadHistoryEntry,
    boardById: Map<String, BoardSummary>,
    boardByBaseUrl: Map<String, BoardSummary>
): String {
    val board = resolveHistoryBoardForEntry(entry, boardById, boardByBaseUrl)
    return entry.boardId
        .takeIf { it.isNotBlank() }
        ?: board?.id?.takeIf { it.isNotBlank() }
        ?: entry.boardUrl.ifBlank { board?.url.orEmpty() }
}

internal fun buildHistoryRefreshKey(
    entry: ThreadHistoryEntry,
    boardById: Map<String, BoardSummary>,
    boardByBaseUrl: Map<String, BoardSummary>
): HistoryRefreshKey {
    val boardKey = buildHistoryBoardKey(entry, boardById, boardByBaseUrl)
    return HistoryRefreshKey(boardKey = boardKey, threadId = entry.threadId)
}

internal fun resolveHistoryBoardForEntry(
    entry: ThreadHistoryEntry,
    boardById: Map<String, BoardSummary>,
    boardByBaseUrl: Map<String, BoardSummary>
): BoardSummary? {
    entry.boardId.takeIf { it.isNotBlank() }?.let { boardId ->
        boardById[boardId]?.let { return it }
    }
    val key = normalizeHistoryBoardKey(entry.boardUrl) ?: return null
    return boardByBaseUrl[key]
}

internal fun normalizeHistoryBoardKey(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val resolved = runCatching { BoardUrlResolver.resolveBoardBaseUrl(url) }.getOrDefault(url)
    return resolved.trimEnd('/').lowercase().ifBlank { null }
}

internal fun isHistoryRefreshAbortSignal(t: Throwable, reasonPrefix: String): Boolean {
    return t is NetworkException && t.message?.startsWith(reasonPrefix) == true
}

internal fun isHistoryRefreshNotFound(t: Throwable): Boolean {
    val status = (t as? ClientRequestException)?.response?.status?.value
        ?: (t as? NetworkException)?.statusCode
    return status == 404 || status == 410
}

internal fun buildHistoryRefreshError(
    totalThreads: Int,
    details: List<HistoryRefresher.ErrorDetail>
): HistoryRefresher.RefreshError {
    val stageCounts = linkedMapOf<String, Int>()
    details.forEach { detail ->
        stageCounts[detail.stage] = (stageCounts[detail.stage] ?: 0) + 1
    }
    return HistoryRefresher.RefreshError(
        errorCount = details.size,
        totalThreads = totalThreads,
        timestamp = Clock.System.now().toEpochMilliseconds(),
        errors = details.take(10),
        stageCounts = stageCounts
    )
}

internal fun resolveHistoryRefreshEntry(
    entry: ThreadHistoryEntry,
    boardById: Map<String, BoardSummary>,
    boardByBaseUrl: Map<String, BoardSummary>,
    skipThreadIds: StateFlow<Map<HistoryRefreshKey, Long>>,
    nowMillis: Long,
    skipThreadTtlMillis: Long
): HistoryRefreshResolvedEntry? {
    val board = resolveHistoryBoardForEntry(entry, boardById, boardByBaseUrl)
    val key = buildHistoryRefreshKey(entry, boardById, boardByBaseUrl)
    if (isHistoryThreadSkipped(skipThreadIds, key, nowMillis, skipThreadTtlMillis)) {
        return null
    }
    val baseUrl = board?.url
        ?: entry.boardUrl.takeIf { it.isNotBlank() }?.let {
            runCatching { BoardUrlResolver.resolveBoardBaseUrl(it) }.getOrNull()
        }
    if (baseUrl.isNullOrBlank() || baseUrl.contains("example.com", ignoreCase = true)) {
        return null
    }
    return HistoryRefreshResolvedEntry(
        entry = entry,
        board = board,
        key = key,
        baseUrl = baseUrl
    )
}

internal class HistoryRefreshRunProcessor(
    private val stateStore: AppStateStore,
    private val repository: BoardRepository,
    private val httpClient: HttpClient?,
    private val archiveSearchJson: Json,
    private val fetchSemaphore: Semaphore,
    private val autoSaveSemaphore: Semaphore,
    private val autoSaveScope: CoroutineScope,
    private val autoSaveService: ThreadSaveService?,
    private val autoSavedThreadRepository: SavedThreadRepository?,
    private val skipThreadIds: MutableStateFlow<Map<HistoryRefreshKey, Long>>,
    private val refreshStartedAt: Long,
    private val skipThreadTtlMillis: Long,
    private val fetchTimeoutMillis: Long,
    private val autoSaveThreadTimeoutMillis: Long,
    private val autoSaveDeadline: Long?,
    private val maxAutoSavesPerRefresh: Int,
    private val threadRefreshAbortThreshold: HistoryRefreshAbortThreshold,
    private val archiveLookupAbortThreshold: HistoryRefreshAbortThreshold,
    private val stats: HistoryRefreshRunStats,
    private val errors: HistoryRefreshErrorTracker,
    private val updates: HistoryRefreshUpdateBuffer,
    private val threadRefreshStage: String,
    private val archiveLookupStage: String,
    private val refreshAbortStage: String,
    private val tag: String
) {
    private val autoSaveLauncher = HistoryRefreshAutoSaveLauncher(
        stateStore = stateStore,
        autoSaveScope = autoSaveScope,
        autoSaveSemaphore = autoSaveSemaphore,
        autoSaveService = autoSaveService,
        autoSavedThreadRepository = autoSavedThreadRepository,
        autoSaveThreadTimeoutMillis = autoSaveThreadTimeoutMillis,
        autoSaveDeadline = autoSaveDeadline,
        maxAutoSavesPerRefresh = maxAutoSavesPerRefresh,
        stats = stats,
        tag = tag
    )
    private val missingEntryDependencies = HistoryRefreshMissingEntryDependencies(
        httpClient = httpClient,
        repository = repository,
        archiveSearchJson = archiveSearchJson,
        fetchTimeoutMillis = fetchTimeoutMillis,
        autoSavedThreadRepository = autoSavedThreadRepository,
        skipThreadIds = skipThreadIds,
        stats = stats,
        errors = errors,
        updates = updates,
        archiveLookupAbortThreshold = archiveLookupAbortThreshold,
        archiveLookupStage = archiveLookupStage,
        refreshAbortStage = refreshAbortStage,
        tag = tag
    )

    suspend fun processEntry(
        entry: ThreadHistoryEntry,
        boardById: Map<String, BoardSummary>,
        boardByBaseUrl: Map<String, BoardSummary>
    ) {
        try {
            val resolvedEntry = resolveHistoryRefreshEntry(
                entry = entry,
                boardById = boardById,
                boardByBaseUrl = boardByBaseUrl,
                skipThreadIds = skipThreadIds,
                nowMillis = refreshStartedAt,
                skipThreadTtlMillis = skipThreadTtlMillis
            ) ?: return
            fetchSemaphore.withPermit {
                refreshResolvedEntry(resolvedEntry)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            if (isHistoryRefreshAbortSignal(error, HISTORY_REFRESH_ABORT_REASON_PREFIX)) {
                throw error
            }
            Logger.e(tag, "Unexpected failure while refreshing ${entry.threadId}", error)
            errors.record(
                threadId = entry.threadId,
                message = error.message ?: "Unexpected refresh failure",
                stage = threadRefreshStage
            )
            stats.recordThreadRefreshFailure()
        }
    }

    private suspend fun refreshResolvedEntry(
        resolvedEntry: HistoryRefreshResolvedEntry
    ) {
        val entry = resolvedEntry.entry
        val board = resolvedEntry.board
        val key = resolvedEntry.key
        val baseUrl = resolvedEntry.baseUrl
        try {
            stats.markAttempt()
            val page = withTimeoutOrNull(fetchTimeoutMillis) {
                repository.getThread(baseUrl, entry.threadId)
            } ?: throw NetworkException("Thread fetch timed out for ${entry.threadId}")
            val opPost = page.posts.firstOrNull()
            val resolvedTitle = resolveThreadTitle(opPost, entry.title)
            stats.markSuccess()
            val updatedEntry = entry.copy(
                title = resolvedTitle,
                titleImageUrl = opPost?.thumbnailUrl ?: entry.titleImageUrl,
                boardName = page.boardTitle ?: entry.boardName.ifBlank { board?.name.orEmpty() },
                replyCount = page.posts.size,
                hasAutoSave = entry.hasAutoSave
            )
            updates.put(key, updatedEntry)

            if (shouldAutoSaveRefreshedEntry(entry, page.posts.size)) {
                autoSaveLauncher.launch(
                    HistoryRefreshAutoSavePlan(
                        resolvedEntry = resolvedEntry,
                        updatedEntry = updatedEntry,
                        resolvedTitle = resolvedTitle,
                        boardName = page.boardTitle ?: entry.boardName.ifBlank { board?.name.orEmpty() },
                        expiresAtLabel = page.expiresAtLabel,
                        posts = page.posts
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            if (isHistoryRefreshNotFound(error)) {
                handleHistoryRefreshNotFound(
                    resolvedEntry = resolvedEntry,
                    dependencies = missingEntryDependencies
                )
            } else {
                Logger.e(tag, "Failed to refresh ${entry.threadId}", error)
                errors.record(
                    threadId = entry.threadId,
                    message = error.message ?: "Unknown error",
                    stage = threadRefreshStage
                )
                stats.recordThreadRefreshFailure()
                val abortReason = stats.threadRefreshAbortReason(threadRefreshAbortThreshold)
                if (abortReason != null) {
                    errors.record(
                        threadId = "refresh-abort",
                        message = abortReason,
                        stage = refreshAbortStage
                    )
                    Logger.e(tag, abortReason, error)
                    throw NetworkException(abortReason)
                }
            }
        }
    }

    private fun shouldAutoSaveRefreshedEntry(
        entry: ThreadHistoryEntry,
        replyCount: Int
    ): Boolean {
        return !entry.hasAutoSave || replyCount != entry.replyCount
    }
}

internal fun resolveHistoryEntryBoardId(
    entry: ThreadHistoryEntry,
    board: BoardSummary?,
    baseUrl: String
): String {
    return entry.boardId.ifBlank {
        board?.id
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { BoardUrlResolver.resolveBoardSlug(baseUrl) }.getOrDefault("")
    }
}

internal fun markHistoryThreadSkipped(
    skipThreadIds: MutableStateFlow<Map<HistoryRefreshKey, Long>>,
    key: HistoryRefreshKey,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
) {
    skipThreadIds.update { it + (key to nowMillis) }
}

internal fun isHistoryThreadSkipped(
    skipThreadIds: StateFlow<Map<HistoryRefreshKey, Long>>,
    key: HistoryRefreshKey,
    nowMillis: Long,
    ttlMillis: Long
): Boolean {
    val skippedAtMillis = skipThreadIds.value[key] ?: return false
    return (nowMillis - skippedAtMillis) < ttlMillis
}

internal suspend fun bestEffortHistoryRefreshFlushOnAbort(
    flush: (suspend (Boolean) -> Boolean)?,
    reason: String,
    abortFlushTimeoutMillis: Long,
    tag: String
) {
    if (flush == null) return
    val flushed = runCatching {
        withContext(NonCancellable) {
            withTimeout(abortFlushTimeoutMillis) {
                flush(true)
            }
        }
    }.onFailure { error ->
        Logger.w(
            tag,
            "Best-effort flush on abort failed ($reason): ${error.message}"
        )
    }.getOrNull()

    if (flushed == false) {
        Logger.w(tag, "Best-effort flush on abort did not persist all updates ($reason)")
    }
}

internal fun selectHistoryRefreshWindow(
    history: List<ThreadHistoryEntry>,
    maxThreadsPerRun: Int?,
    cursor: Int
): HistoryRefreshWindowSelection {
    if (history.isEmpty()) {
        return HistoryRefreshWindowSelection(entries = emptyList(), nextCursor = 0)
    }

    val limit = maxThreadsPerRun?.coerceAtLeast(1) ?: history.size
    if (limit >= history.size) {
        return HistoryRefreshWindowSelection(entries = history, nextCursor = cursor)
    }

    val size = history.size
    val start = ((cursor % size) + size) % size
    val endExclusive = start + limit
    val nextCursor = endExclusive % size
    val entries = if (endExclusive <= size) {
        history.subList(start, endExclusive)
    } else {
        buildList(limit) {
            addAll(history.subList(start, size))
            addAll(history.subList(0, endExclusive - size))
        }
    }
    return HistoryRefreshWindowSelection(entries = entries, nextCursor = nextCursor)
}
