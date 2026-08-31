package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.matchesNormalizedWatchWords
import com.valoser.futacha.shared.model.normalizeWatchWords
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.resolveBoardWatchWordKey
import com.valoser.futacha.shared.state.resolveEffectiveWatchWordsForBoard
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CatalogWatchAlertRefresher(
    private val stateStore: AppStateStore,
    private val repository: BoardRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxConcurrency: Int = 2
) {
    private val refreshMutex = Mutex()

    suspend fun refresh(): CatalogWatchAlertRefreshResult {
        if (!refreshMutex.tryLock()) {
            throw RefreshAlreadyRunningException()
        }
        return try {
            withContext(dispatcher) {
                refreshLocked()
            }
        } finally {
            refreshMutex.unlock()
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun refreshLocked(): CatalogWatchAlertRefreshResult {
        val globalWatchWords = stateStore.watchWords.first()
        val boardWatchWords = stateStore.boardWatchWords.first()
        val targets = stateStore.boards.first()
            .filterNot { it.isMockBoardForWatchAlert() }
            .mapNotNull { board ->
                val boardKey = resolveBoardWatchWordKey(board)
                val normalizedWatchWords = normalizeWatchWords(
                    resolveEffectiveWatchWordsForBoard(
                        globalWatchWords = globalWatchWords,
                        boardWatchWords = boardWatchWords,
                        boardId = boardKey
                    )
                )
                if (normalizedWatchWords.isEmpty()) {
                    null
                } else {
                    WatchAlertBoardTarget(board, normalizedWatchWords)
                }
            }
        if (targets.isEmpty()) {
            return CatalogWatchAlertRefreshResult()
        }

        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val existingHistoryKeys = stateStore.history.first()
            .mapTo(mutableSetOf()) { it.watchAlertIdentityKey() }
        val matches = mutableListOf<CatalogWatchAlertMatch>()
        val seenKeys = existingHistoryKeys.toMutableSet()
        val failures = fetchWatchSourceCatalogs(targets) { source ->
            val remaining = MAX_WATCH_ALERT_MATCHES_PER_RUN - matches.size
            if (remaining <= 0) return@fetchWatchSourceCatalogs
            source.items.asSequence()
                .distinctBy { item -> item.id.ifBlank { item.threadUrl } }
                .filter { item -> item.matchesNormalizedWatchWords(source.normalizedWatchWords) }
                .mapNotNull { item -> item.toWatchAlertMatch(source.board, nowMillis) }
                .filter { match -> seenKeys.add(match.identityKey) }
                .take(remaining)
                .forEach(matches::add)
        }

        return CatalogWatchAlertRefreshResult(
            matches = matches,
            failures = failures
        )
    }

    private suspend fun fetchWatchSourceCatalogs(
        targets: List<WatchAlertBoardTarget>,
        onCatalog: (WatchAlertCatalogSource) -> Unit
    ): List<CatalogWatchAlertFailure> = coroutineScope {
        val concurrency = maxConcurrency.coerceAtLeast(1)
        val requests = targets.asSequence()
            .flatMap { target -> CatalogMode.watchSourceModes.asSequence().map { mode -> target to mode } }
            .iterator()
        val failures = mutableListOf<CatalogWatchAlertFailure>()
        while (requests.hasNext()) {
            val tasks = buildList {
                var batchSize = 0
                while (batchSize < concurrency && requests.hasNext()) {
                    val (target, mode) = requests.next()
                    add(async {
                        val result = runCatching {
                            WatchAlertCatalogSource(
                                board = target.board,
                                mode = mode,
                                normalizedWatchWords = target.normalizedWatchWords,
                                items = repository.getCatalog(target.board.url, mode)
                            )
                        }
                        result.fold(
                            onSuccess = { WatchAlertCatalogFetchOutcome.Success(it) },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                WatchAlertCatalogFetchOutcome.Failure(
                                    CatalogWatchAlertFailure(
                                        boardId = target.board.id,
                                        boardName = target.board.name,
                                        sourceMode = mode,
                                        message = error.message ?: "unknown error"
                                    )
                                )
                            }
                        )
                    })
                    batchSize += 1
                }
            }
            tasks.forEach { task ->
                when (val outcome = task.await()) {
                    is WatchAlertCatalogFetchOutcome.Success -> onCatalog(outcome.source)
                    is WatchAlertCatalogFetchOutcome.Failure -> failures += outcome.failure
                }
            }
        }
        failures
    }

    class RefreshAlreadyRunningException : IllegalStateException("Catalog watch alert refresh is already running")
}

data class CatalogWatchAlertRefreshResult(
    val matches: List<CatalogWatchAlertMatch> = emptyList(),
    val failures: List<CatalogWatchAlertFailure> = emptyList()
) {
    val failureCount: Int
        get() = failures.size
}

data class CatalogWatchAlertMatch(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    val title: String,
    val titleImageUrl: String,
    val replyCount: Int,
    val detectedAtEpochMillis: Long
) {
    val identityKey: String
        get() = "${boardId.ifBlank { boardUrl }}::$threadId"
}

data class CatalogWatchAlertFailure(
    val boardId: String,
    val boardName: String,
    val sourceMode: CatalogMode? = null,
    val message: String
)

private data class WatchAlertCatalogSource(
    val board: BoardSummary,
    val mode: CatalogMode,
    val normalizedWatchWords: List<String>,
    val items: List<CatalogItem>
)

private data class WatchAlertBoardTarget(
    val board: BoardSummary,
    val normalizedWatchWords: List<String>
)

private sealed interface WatchAlertCatalogFetchOutcome {
    data class Success(val source: WatchAlertCatalogSource) : WatchAlertCatalogFetchOutcome
    data class Failure(val failure: CatalogWatchAlertFailure) : WatchAlertCatalogFetchOutcome
}

internal const val MAX_WATCH_ALERT_MATCHES_PER_RUN = 5_000

private fun BoardSummary.isMockBoardForWatchAlert(): Boolean {
    return url.contains("example.com", ignoreCase = true)
}

private fun ThreadHistoryEntry.watchAlertIdentityKey(): String {
    return "${boardId.ifBlank { boardUrl }}::$threadId"
}

private fun CatalogItem.toWatchAlertMatch(
    board: BoardSummary,
    nowMillis: Long
): CatalogWatchAlertMatch? {
    val threadId = id.trim().take(WATCH_ALERT_THREAD_ID_MAX_CHARS).takeIf { it.isNotBlank() } ?: return null
    return CatalogWatchAlertMatch(
        threadId = threadId,
        boardId = board.id.take(WATCH_ALERT_BOARD_ID_MAX_CHARS),
        title = title?.takeIf { it.isNotBlank() }
            ?.take(WATCH_ALERT_TITLE_MAX_CHARS)
            ?: "No.$threadId",
        titleImageUrl = thumbnailUrl.orEmpty().take(WATCH_ALERT_URL_MAX_CHARS),
        boardName = board.name.take(WATCH_ALERT_BOARD_NAME_MAX_CHARS),
        boardUrl = board.url.take(WATCH_ALERT_URL_MAX_CHARS),
        replyCount = replyCount,
        detectedAtEpochMillis = nowMillis
    )
}

private const val WATCH_ALERT_THREAD_ID_MAX_CHARS = 128
private const val WATCH_ALERT_BOARD_ID_MAX_CHARS = 256
private const val WATCH_ALERT_BOARD_NAME_MAX_CHARS = 256
private const val WATCH_ALERT_TITLE_MAX_CHARS = 1_000
private const val WATCH_ALERT_URL_MAX_CHARS = 8_192
