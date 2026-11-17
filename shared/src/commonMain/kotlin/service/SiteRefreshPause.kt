package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.aimoge.AimogeApiException
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

internal const val SITE_REFRESH_PAUSE_PATH = "private/site_refresh_pause.json"
private const val SITE_REFRESH_PAUSE_MAX_BYTES = 64L * 1024L
const val SITE_PAUSE_429_MILLIS = 15 * 60_000L
const val SITE_PAUSE_5XX_MILLIS = 5 * 60_000L
const val SITE_PAUSE_MAX_MILLIS = 60 * 60_000L
const val SITE_BOARD_PAUSE_MILLIS = 24 * 60 * 60_000L

/** What the あいもげ background requests do after an error answer. */
enum class SiteErrorAction {
    /** 429 / 5xx: stop the rest of this run and pause the site. */
    PAUSE_SITE,
    /** 404 of a board request: the board is gone or renamed; its threads are not made fallen, the board pauses 24 h. */
    PAUSE_BOARD,
    /** 400 (our own mistake) and 422 TOKEN_NOT_FOUND: log, stop the rest of the board / run, never send again now. */
    STOP_BOARD,
    STOP_RUN,
    /** A connection failure or anything else: answer nothing for this batch. */
    UNKNOWN
}

fun siteErrorActionOf(error: Throwable): SiteErrorAction {
    val status = (error as? NetworkException)?.statusCode ?: return SiteErrorAction.UNKNOWN
    return when {
        status == 429 || status in 500..599 -> SiteErrorAction.PAUSE_SITE
        status == 404 -> SiteErrorAction.PAUSE_BOARD
        status == 400 -> SiteErrorAction.STOP_BOARD
        status == 422 || status == 401 -> SiteErrorAction.STOP_RUN
        else -> SiteErrorAction.UNKNOWN
    }
}

/**
 * The length of the next site pause: 15 min after 429, 5 min after 5xx, doubled when the first request after a pause
 * got the same again (up to 1 h), and never shorter than a `Retry-After` the server sent.
 */
fun nextSitePauseMillis(status: Int, previousLengthMillis: Long?, retryAfterSeconds: Long?): Long {
    val base = if (status == 429) SITE_PAUSE_429_MILLIS else SITE_PAUSE_5XX_MILLIS
    val length = if (previousLengthMillis != null && previousLengthMillis > 0) {
        (previousLengthMillis * 2).coerceAtMost(SITE_PAUSE_MAX_MILLIS)
    } else base
    val retryAfter = retryAfterSeconds?.takeIf { it > 0 }?.let { it * 1000 } ?: 0L
    return maxOf(length, retryAfter)
}

@Serializable
internal data class PersistedSitePause(
    val untilMillis: Long = 0L,
    /** The length of the last pause; kept after it ends so the next 429 right after doubles it. */
    val lengthMillis: Long = 0L,
    /** Board address → paused until. */
    val boards: Map<String, Long> = emptyMap()
)

/**
 * The pause of あいもげ requests after 429 / 5xx (and the 24 h pause of a board answering 404), kept in
 * [SITE_REFRESH_PAUSE_PATH] across runs. One per process: the foreground repository records into it too, and its
 * automatic reads send nothing while it lasts.
 */
object SiteRefreshPause {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    // Records come from the foreground and the background at once; every change is one compare-and-set.
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val stateRef = kotlin.concurrent.atomics.AtomicReference(PersistedSitePause())
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val state: PersistedSitePause get() = stateRef.load()

    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private inline fun update(change: (PersistedSitePause) -> PersistedSitePause) {
        while (true) {
            val old = stateRef.load()
            if (stateRef.compareAndSet(old, change(old))) return
        }
    }

    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private fun set(value: PersistedSitePause) = stateRef.store(value)
    private var loadedFrom: FileSystem? = null
    internal var nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    fun sitePausedNow(): Boolean = state.untilMillis > nowMillis()

    fun boardPausedNow(boardUrl: String): Boolean = (state.boards[boardUrl] ?: 0L) > nowMillis()

    internal fun snapshot(): PersistedSitePause = state

    /** Tests only. */
    internal fun reset() {
        set(PersistedSitePause())
        loadedFrom = null
        nowMillis = { Clock.System.now().toEpochMilliseconds() }
    }

    suspend fun load(fs: FileSystem?) {
        fs ?: return
        mutex.withLock {
            if (loadedFrom === fs) return
            loadedFrom = fs
            val read = runSuspendCatchingPreservingCancellation {
                if (!fs.exists(SITE_REFRESH_PAUSE_PATH)) return@runSuspendCatchingPreservingCancellation null
                require(fs.getFileSize(SITE_REFRESH_PAUSE_PATH) in 0L..SITE_REFRESH_PAUSE_MAX_BYTES)
                json.decodeFromString(PersistedSitePause.serializer(), fs.readString(SITE_REFRESH_PAUSE_PATH).getOrThrow())
            }.getOrNull() ?: return
            // The later of what this process saw and what the file says.
            update { state ->
                PersistedSitePause(
                    untilMillis = maxOf(state.untilMillis, read.untilMillis),
                    lengthMillis = maxOf(state.lengthMillis, read.lengthMillis),
                    boards = (read.boards.keys + state.boards.keys).associateWith { maxOf(read.boards[it] ?: 0L, state.boards[it] ?: 0L) }
                )
            }
        }
    }

    /** Records a 429 / 5xx answer (foreground or background). */
    fun recordSiteError(status: Int, retryAfterSeconds: Long?) {
        val now = nowMillis()
        update { current ->
            // Doubled only when the pause just ended (the first request after it got the same again).
            val previous = current.lengthMillis.takeIf { it > 0 && current.untilMillis > 0 && now - current.untilMillis < current.lengthMillis }
            val length = nextSitePauseMillis(status, previous, retryAfterSeconds)
            current.copy(untilMillis = maxOf(current.untilMillis, now + length), lengthMillis = length)
        }
    }

    fun recordError(error: Throwable) {
        val status = (error as? NetworkException)?.statusCode ?: return
        if (status == 429 || status in 500..599) {
            recordSiteError(status, (error.cause as? AimogeApiException)?.retryAfterSeconds)
        }
    }

    fun recordBoardGone(boardUrl: String) {
        val until = nowMillis() + SITE_BOARD_PAUSE_MILLIS
        update { it.copy(boards = it.boards + (boardUrl to until)) }
    }

    /** A success after the pause ended brings the length back. */
    fun recordSuccess() {
        val now = nowMillis()
        update { current -> if (current.lengthMillis != 0L && current.untilMillis <= now) current.copy(lengthMillis = 0L) else current }
    }

    suspend fun persist(fs: FileSystem?) {
        fs ?: return
        mutex.withLock {
        val now = nowMillis()
        update { it.copy(boards = it.boards.filterValues { until -> until > now }) }
        val trimmed = state
        runSuspendCatchingPreservingCancellation {
            fs.createDirectory(SITE_REFRESH_PAUSE_PATH.substringBeforeLast('/')).getOrThrow()
            fs.writeString(SITE_REFRESH_PAUSE_PATH, json.encodeToString(PersistedSitePause.serializer(), trimmed)).getOrThrow()
        }.onFailure { Logger.w("SiteRefreshPause", "Could not save the site pause: ${it.message}") }
        }
    }
}
