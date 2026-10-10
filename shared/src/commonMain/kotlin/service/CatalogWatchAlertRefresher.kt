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
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CatalogWatchAlertRefresher(
    private val stateStore: AppStateStore,
    private val repository: BoardRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxConcurrency: Int = 2,
    private val diagnosticsStore: com.valoser.futacha.shared.compat.CompatibilityStore? = null
) {
    private val refreshMutex = Mutex()
    private var lastStartedBoardId: String? = null
    private var runTargetNames: List<String> = emptyList()
    private var runWords: List<String> = emptyList()

    /**
     * [onMatchesFound] receives all matches of this run once: when the check
     * ends, or with those found so far when the caller's timeout cuts it off
     * (then non-cancellably). Notifying only from the result dropped every
     * match of a cut-off run; one call keeps one summary notification per run.
     */
    suspend fun refresh(
        onMatchesFound: (suspend (List<CatalogWatchAlertMatch>) -> Unit)? = null
    ): CatalogWatchAlertRefreshResult {
        if (!refreshMutex.tryLock()) {
            throw RefreshAlreadyRunningException()
        }
        var result: CatalogWatchAlertRefreshResult? = null
        lastStartedBoardId = null
        runTargetNames = emptyList()
        runWords = emptyList()
        var interrupted = true
        var detected = emptyList<CatalogWatchAlertMatch>()
        try {
            result = withContext(dispatcher) {
                refreshLocked { matches -> detected = matches; onMatchesFound?.invoke(matches) }
            }
            interrupted = false
            return result
        } finally {
            try { withContext(NonCancellable) { kotlinx.coroutines.withTimeoutOrNull(1000) { runCatching {
            diagnosticsStore?.savePreference("compat.background.catalog_watch_cursor", if (interrupted) lastStartedBoardId.orEmpty() else "")
            com.valoser.futacha.shared.compat.recordWatchRunDiagnostics(diagnosticsStore,
                com.valoser.futacha.shared.compat.WATCH_RUN_CATALOG_KEY,
                com.valoser.futacha.shared.compat.WatchRunDiagnostics(
                    checkedAt = Clock.System.now().toEpochMilliseconds(),
                    outcome = if (interrupted) "中断" else if (result?.failures.orEmpty().isNotEmpty()) "一部失敗" else "完了",
                    targets = runTargetNames,
                    words = runWords,
                    matchCount = result?.matches?.size ?: detected.size,
                    failures = result?.failures.orEmpty().map { "${it.boardName}: ${it.message}" },
                    matchedTitles = (result?.matches ?: detected).map { it.title }
                ))
            } } } } finally { refreshMutex.unlock() }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun refreshLocked(
        onMatchesFound: (suspend (List<CatalogWatchAlertMatch>) -> Unit)?
    ): CatalogWatchAlertRefreshResult {
        val globalWatchWords = stateStore.watchWords.first()
        val boardWatchWords = stateStore.boardWatchWords.first()
        val originalTargets = stateStore.boards.first()
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
        val cursor = diagnosticsStore?.preferences?.first()?.get("compat.background.catalog_watch_cursor")
        val cursorIndex = originalTargets.indexOfFirst { it.board.id == cursor }
        val targets = if (cursorIndex >= 0) originalTargets.drop(cursorIndex + 1) + originalTargets.take(cursorIndex + 1) else originalTargets
        runTargetNames = targets.map { it.board.name }
        runWords = targets.flatMap { it.normalizedWatchWords }.distinct()
        if (targets.isEmpty()) {
            return CatalogWatchAlertRefreshResult()
        }

        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val existingHistoryKeys = stateStore.history.first()
            .mapTo(mutableSetOf()) { it.watchAlertIdentityKey() }
        val matches = mutableListOf<CatalogWatchAlertMatch>()
        val seenKeys = existingHistoryKeys.toMutableSet()
        val matchOrder = mutableMapOf<String, Int>()
        val failures = try {
            fetchWatchSourceCatalogs(targets) { source ->
                val remaining = MAX_WATCH_ALERT_MATCHES_PER_RUN - matches.size
                if (remaining <= 0) return@fetchWatchSourceCatalogs
                source.items.asSequence()
                    .distinctBy { item -> item.id.ifBlank { item.threadUrl } }
                    .filter { item -> item.matchesNormalizedWatchWords(source.normalizedWatchWords) }
                    .mapNotNull { item -> item.toWatchAlertMatch(source.board, nowMillis) }
                    .filter { match -> seenKeys.add(match.identityKey) }
                    .take(remaining)
                    .forEach { match ->
                        matchOrder[match.identityKey] = targets.indexOfFirst { it.board.id == source.board.id } * 2 + CatalogMode.watchSourceModes.indexOf(source.mode)
                        matches.add(match)
                    }
                matches.sortBy { matchOrder[it.identityKey] }
            }
        } catch (cancelled: CancellationException) {
            if (matches.isNotEmpty() && onMatchesFound != null) {
                withContext(NonCancellable) {
                    try {
                        onMatchesFound(matches.toList())
                    } catch (failure: Exception) {
                        Logger.w(CATALOG_WATCH_ALERT_TAG, "Delivering cut-off matches failed: ${failure.message}")
                    }
                }
            }
            throw cancelled
        }
        if (matches.isNotEmpty()) onMatchesFound?.invoke(matches.toList())

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
        val collectMutex = Mutex()
        while (requests.hasNext()) {
            val tasks = buildList {
                var batchSize = 0
                while (batchSize < concurrency && requests.hasNext()) {
                    val (target, mode) = requests.next()
                    lastStartedBoardId = target.board.id
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
                            onSuccess = {
                                collectMutex.withLock { onCatalog(it) }
                                WatchAlertCatalogFetchOutcome.Success(it)
                            },
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
                    is WatchAlertCatalogFetchOutcome.Success -> Unit
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
private const val CATALOG_WATCH_ALERT_TAG = "CatalogWatchAlertRefresher"


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
