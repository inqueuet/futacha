package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.compat.BoardSite
import com.valoser.futacha.shared.compat.boardSiteOf
import com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadStatus
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.SiteThreadState
import com.valoser.futacha.shared.repo.SiteThreadSummary
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** Android Worker and iOS BGProcessing: up to 10 `summary` requests (200 threads) in one run. */
const val AIMOGE_SUMMARY_REQUESTS_LONG = 10
/** iOS BGAppRefresh and the Watch refresh: 2 requests (40 threads). */
const val AIMOGE_SUMMARY_REQUESTS_SHORT = 2
private const val SUMMARY_IDS_PER_REQUEST = 20
private const val TAG = "SiteRefreshRouter"

/** One あいもげ thread to check, with the board address its `summary` goes to. */
data class SiteRefreshTarget(val boardUrl: String, val threadId: String)

/** What a run of [SiteRefreshRouter] did. */
data class SiteRefreshOutcome(
    val requests: Int,
    /** The targets no request answered (cap, deadline, pause, error): the next run starts from the first of them. */
    val notVisited: List<SiteRefreshTarget>
)

/**
 * Sends the あいもげ alive checks of one background run: per board, 20 threads in one `summary`, at most
 * [maxRequests] requests, none while the site or the board is paused, and none starting after [fetchStartDeadline]
 * except the first. Each batch's answers go to `onBatch` as soon as they arrive, so a run cut off keeps them.
 * Error answers follow [siteErrorActionOf]; none of them is a reason for the Worker to retry.
 * Background requests carry no credential and no delete key (the あいもげ repository adds neither to reads).
 */
class SiteRefreshRouter(
    private val repository: BoardRepository,
    private val maxRequests: Int,
    private val requestTimeoutMillis: Long,
    private val fetchStartDeadline: Long? = null,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    suspend fun run(targets: List<SiteRefreshTarget>, onBatch: suspend (List<SiteThreadSummary>) -> Unit): SiteRefreshOutcome {
        if (targets.isEmpty()) return SiteRefreshOutcome(0, emptyList())
        if (SiteRefreshPause.sitePausedNow()) return SiteRefreshOutcome(0, targets)
        val visited = HashSet<SiteRefreshTarget>()
        var requests = 0
        val byBoard = LinkedHashMap<String, MutableList<SiteRefreshTarget>>()
        targets.forEach { byBoard.getOrPut(it.boardUrl) { mutableListOf() } += it }
        run stop@{
            byBoard.forEach board@{ (boardUrl, rows) ->
                if (SiteRefreshPause.boardPausedNow(boardUrl)) return@board
                rows.distinctBy { it.threadId }.chunked(SUMMARY_IDS_PER_REQUEST).forEach { chunk ->
                    if (requests >= maxRequests) return@stop
                    if (requests > 0 && fetchStartDeadline != null && nowMillis() >= fetchStartDeadline) return@stop
                    requests++
                    val answers = try {
                        withTimeoutOrNull(requestTimeoutMillis) { repository.getThreadSummaries(boardUrl, chunk.map { it.threadId }) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        when (siteErrorActionOf(e)) {
                            SiteErrorAction.PAUSE_SITE -> {
                                SiteRefreshPause.recordError(e)
                                Logger.w(TAG, "あいもげ paused after HTTP ${(e as? com.valoser.futacha.shared.network.NetworkException)?.statusCode}")
                                return@stop
                            }
                            SiteErrorAction.PAUSE_BOARD -> {
                                SiteRefreshPause.recordBoardGone(boardUrl)
                                Logger.w(TAG, "An あいもげ board answered 404; paused for a day")
                                return@board
                            }
                            SiteErrorAction.STOP_BOARD -> {
                                Logger.w(TAG, "An あいもげ board request was refused (400); skipping the board")
                                return@board
                            }
                            SiteErrorAction.STOP_RUN -> {
                                Logger.w(TAG, "あいもげ answered HTTP ${(e as? com.valoser.futacha.shared.network.NetworkException)?.statusCode}; stopping this run")
                                return@stop
                            }
                            SiteErrorAction.UNKNOWN -> null
                        }
                    }
                    visited += chunk
                    if (answers != null) {
                        SiteRefreshPause.recordSuccess()
                        onBatch(answers)
                    }
                }
            }
        }
        return SiteRefreshOutcome(requests, targets.filterNot { it in visited })
    }
}

/** The あいもげ rows a background history run checks: not fallen, auto-refresh not stopped. */
internal fun aimogeHistoryTargets(history: List<ThreadHistoryEntry>): List<Pair<ThreadHistoryEntry, SiteRefreshTarget>> =
    history.mapNotNull { entry ->
        if (entry.isAutoRefreshDisabled || entry.threadStatus?.isDead == true) return@mapNotNull null
        if (boardSiteOf(entry.boardUrl) != BoardSite.AIMOGE) return@mapNotNull null
        val board = siteCanonicalBoardUrlOf(entry.boardUrl) ?: return@mapNotNull null
        entry to SiteRefreshTarget(board, entry.threadId.trim().lowercase())
    }

/** The history row after one `summary` answer; null when nothing changes (the count never falls). */
internal fun aimogeHistoryUpdate(entry: ThreadHistoryEntry, answer: SiteThreadSummary, nowMillis: Long): ThreadHistoryEntry? =
    when (answer.state) {
        SiteThreadState.GONE -> entry.copy(
            isAutoRefreshDisabled = true,
            threadStatus = (entry.threadStatus ?: ThreadStatus()).copy(isDead = true)
        )
        SiteThreadState.ALIVE -> answer.postCount?.takeIf { it > entry.replyCount }?.let {
            entry.copy(replyCount = it, lastConfirmedAliveEpochMillis = nowMillis)
        }
        SiteThreadState.UNKNOWN -> null
    }

/** [targets] turned to start at the saved [cursorIdentity] (the first row the last run did not reach). */
internal fun <T> rotateFrom(targets: List<T>, cursorIdentity: String?, identity: (T) -> String): List<T> {
    val start = cursorIdentity?.let { id -> targets.indexOfFirst { identity(it) == id } }?.takeIf { it > 0 } ?: return targets
    return targets.drop(start) + targets.take(start)
}

internal const val AIMOGE_REFRESH_CURSOR_PATH = "private/aimoge_refresh_cursor.json"

@kotlinx.serialization.Serializable
private data class PersistedAimogeCursor(val threadId: String? = null)

/** Where the next あいもげ background run starts; separate from the Futaba history cursor. */
internal object AimogeRefreshCursor {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    suspend fun read(fs: com.valoser.futacha.shared.util.FileSystem?, path: String): String? {
        fs ?: return null
        return com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation {
            if (!fs.exists(path)) return@runSuspendCatchingPreservingCancellation null
            require(fs.getFileSize(path) in 0L..4096L)
            json.decodeFromString(PersistedAimogeCursor.serializer(), fs.readString(path).getOrThrow()).threadId
        }.getOrNull()
    }

    suspend fun write(fs: com.valoser.futacha.shared.util.FileSystem?, path: String, threadId: String?) {
        fs ?: return
        com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation {
            fs.createDirectory(path.substringBeforeLast('/')).getOrThrow()
            fs.writeString(path, json.encodeToString(PersistedAimogeCursor.serializer(), PersistedAimogeCursor(threadId))).getOrThrow()
        }
    }
}
