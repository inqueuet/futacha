package com.valoser.futacha.shared.ui.futaber

import androidx.compose.ui.graphics.Color
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.ui.board.OfflineThreadSource
import com.valoser.futacha.shared.ui.board.buildOfflineThreadLookupContext
import com.valoser.futacha.shared.ui.board.isOfflineFallbackCandidate
import com.valoser.futacha.shared.ui.board.isThreadLoadTimeout
import com.valoser.futacha.shared.ui.board.loadOfflineThreadPageCandidate
import com.valoser.futacha.shared.ui.board.statusCodeOrNull
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.resolveThreadTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * A thread the user opened: enough to show a title before the page arrives, plus what the
 * stored history said when it was opened (how many posts were seen, where reading stopped).
 */
internal data class FutaberThreadRef(
    val boardId: String,
    val threadId: String,
    val title: String,
    val thumbnailUrl: String,
    val replyCount: Int,
    /** Posts (including the first) the previous visit had seen; 0 when the thread is new to the history. */
    val seenCount: Int = 0,
    val resumePostId: String = "",
    val resumeIndex: Int = 0,
    val resumeOffset: Int = 0
)

internal fun CatalogItem.toFutaberThreadRef(boardId: String): FutaberThreadRef = FutaberThreadRef(
    boardId = boardId,
    threadId = id,
    title = futaberCatalogTitle(this),
    thumbnailUrl = thumbnailUrl.orEmpty(),
    replyCount = replyCount
)

/**
 * `boardUrl` of a history row holds the thread's own address, as the ふたちゃ visit record does,
 * so both modes read the same row. A board address the resolver rejects falls back to the board.
 */
internal fun futaberHistoryThreadUrl(board: BoardSummary, threadId: String): String =
    runCatching { BoardUrlResolver.resolveThreadUrl(board.url, threadId) }.getOrDefault(board.url)

/**
 * History row written when the screen is opened. The visit time is the open time only:
 * loading the page or scrolling never moves the entry (the history records the order in
 * which screens were opened). An existing entry keeps its read position and flags.
 */
internal fun buildFutaberOpenHistoryEntry(
    existing: ThreadHistoryEntry?,
    board: BoardSummary,
    ref: FutaberThreadRef,
    nowEpochMillis: Long
): ThreadHistoryEntry {
    val thumbnail = ref.thumbnailUrl.ifBlank { existing?.titleImageUrl.orEmpty() }
    return existing?.copy(
        boardId = board.id,
        boardName = board.name,
        boardUrl = futaberHistoryThreadUrl(board, ref.threadId),
        title = ref.title.ifBlank { existing.title },
        titleImageUrl = thumbnail,
        lastVisitedEpochMillis = nowEpochMillis
    ) ?: ThreadHistoryEntry(
        threadId = ref.threadId,
        boardId = board.id,
        title = ref.title,
        titleImageUrl = thumbnail,
        boardName = board.name,
        boardUrl = futaberHistoryThreadUrl(board, ref.threadId),
        lastVisitedEpochMillis = nowEpochMillis,
        // History counts posts including the first (what a loaded page reports); the catalog counts replies.
        replyCount = ref.replyCount + 1
    )
}

internal fun findFutaberHistoryEntry(
    history: List<ThreadHistoryEntry>,
    board: BoardSummary,
    threadId: String
): ThreadHistoryEntry? = history.firstOrNull {
    it.threadId == threadId && (it.boardId == board.id || (it.boardId.isBlank() && it.boardUrl == board.url))
}

/** The poster ID is rendered separately, so the date column drops the trailing `ID:xxxx`. */
internal fun futaberTimestampWithoutId(timestamp: String): String {
    val index = timestamp.indexOf("ID:")
    return (if (index == -1) timestamp else timestamp.substring(0, index)).trim()
}

private val DEFAULT_SUBJECTS = setOf("無題", "")
private val DEFAULT_NAMES = setOf("としあき", "名無し", "")

/** Subject and name are shown only when they differ from the board defaults, unless [always]. */
internal fun futaberPostTitleLine(post: Post, always: Boolean = false): String? {
    val subject = post.subject?.trim().orEmpty()
    val author = post.author?.trim().orEmpty()
    val parts = buildList {
        if (always) {
            if (subject.isNotEmpty()) add(subject)
            if (author.isNotEmpty()) add(author)
        } else {
            if (subject !in DEFAULT_SUBJECTS) add(subject)
            if (author !in DEFAULT_NAMES) add(author)
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ")
}

/** A quoted line starts with `>` (ASCII or full width). */
internal fun isFutaberQuoteLine(line: String): Boolean {
    val first = line.trimStart().firstOrNull() ?: return false
    return first == '>' || first == '＞'
}

private val LIGHT_POSTER_COLORS = listOf(
    Color(0xFF2D6A2D), Color(0xFF8A4B00), Color(0xFF6B3FA0), Color(0xFF0B6E6E),
    Color(0xFFA3261E), Color(0xFF3F5F9F), Color(0xFF7A5A00), Color(0xFF8A2D6B)
)
private val DARK_POSTER_COLORS = listOf(
    Color(0xFF7FD67F), Color(0xFFFFB35C), Color(0xFFC9A7F5), Color(0xFF5FD6D6),
    Color(0xFFFF8F86), Color(0xFF9DB9F2), Color(0xFFE9C95C), Color(0xFFF08FCB)
)

internal fun futaberPosterColors(isDark: Boolean): List<Color> =
    if (isDark) DARK_POSTER_COLORS else LIGHT_POSTER_COLORS

/** The same poster ID always gets the same colour, so a run of posts by one person is easy to follow. */
internal fun futaberPosterColor(posterId: String, isDark: Boolean): Color {
    val palette = futaberPosterColors(isDark)
    var hash = 0
    posterId.forEach { hash = hash * 31 + it.code }
    return palette[((hash % palette.size) + palette.size) % palette.size]
}

/**
 * The ref to show for [existing]'s thread, carrying what its stored history said before this visit. [seenCount] is the
 * number of posts this person had on screen when they last left the thread (this mode's own record, see
 * [FutaberSeenCount]); without one, the history row's count is the baseline, as before.
 */
internal fun futaberRefWithHistory(ref: FutaberThreadRef, existing: ThreadHistoryEntry?, seenCount: Int? = null): FutaberThreadRef =
    if (existing == null) ref else ref.copy(
        seenCount = seenCount ?: existing.replyCount,
        resumePostId = existing.lastReadPostId.orEmpty(),
        resumeIndex = existing.lastReadItemIndex,
        resumeOffset = existing.lastReadItemOffset
    )

/** Where to put the list after the page loads: the remembered post, else the remembered row. */
internal fun futaberResumeIndex(posts: List<Post>, ref: FutaberThreadRef): Int? {
    if (posts.isEmpty()) return null
    if (ref.resumePostId.isNotBlank()) {
        posts.indexOfFirst { it.id == ref.resumePostId }.takeIf { it >= 0 }?.let { return it }
    }
    return ref.resumeIndex.takeIf { it > 0 }?.coerceAtMost(posts.lastIndex)
}

/**
 * The row to scroll to when a thread is opened: the remembered post (else row) of its history, or null for a thread
 * that was never read, which stays at the top. Looked up in [rows], the list as it is shown.
 */
internal fun futaberRestoreRowPosition(posts: List<Post>, rows: List<FutaberRow>, ref: FutaberThreadRef): Int? =
    futaberResumeIndex(posts, ref)?.let { ordinal -> futaberRowPositionAtOrAfter(rows, ordinal) }

/** First post that arrived after the previous visit, or null when nothing is new (or nothing was seen). */
internal fun futaberFirstNewIndex(seenCount: Int, total: Int): Int? =
    if (seenCount in 1 until total) seenCount else null

/**
 * The history after a successful load: the title is worked out again from the first post (the catalog only knew a
 * shortened one), the post count is brought up to date and an empty thumbnail is filled from the first post. The
 * visit time, read position and order stay.
 */
internal fun futaberHistoryAfterLoad(
    history: List<ThreadHistoryEntry>,
    boardId: String,
    threadId: String,
    posts: List<Post>
): List<ThreadHistoryEntry> = history.map { entry ->
    if (entry.threadId != threadId || entry.boardId != boardId) entry
    else entry.copy(
        // The same rule ふたちゃ uses for the history: the first line of the body, else the subject, else the old title.
        title = if (posts.isEmpty()) entry.title else resolveThreadTitle(posts.first(), entry.title),
        replyCount = maxOf(entry.replyCount, posts.size),
        titleImageUrl = entry.titleImageUrl.ifBlank { posts.firstOrNull()?.thumbnailUrl.orEmpty() }
    )
}

/**
 * The history row of a thread whose live page was just read: it is alive now (the time ふたちゃ also records), and a
 * "fallen" mark an earlier failed check left on it is lifted. Not for a copy shown from an archive.
 */
internal fun futaberHistoryMarkAlive(
    history: List<ThreadHistoryEntry>,
    boardId: String,
    threadId: String,
    nowEpochMillis: Long
): List<ThreadHistoryEntry> = history.map { entry ->
    if (entry.threadId != threadId || entry.boardId != boardId) entry
    else entry.copy(lastConfirmedAliveEpochMillis = nowEpochMillis, isAutoRefreshDisabled = false)
}

/** The count of a post's そうだね label ("そうだね×3", "+3"); null when there is none or it is zero. */
internal fun futaberSaidaneCount(label: String?): Int? {
    val digits = label.orEmpty().filter { it.isDigit() }
    return digits.toIntOrNull()?.takeIf { it > 0 }
}


/**
 * The archived copy of a thread that has fallen off its board, found the way ふたちゃ and としあき(仮) find it
 * (the archive of the board, then FTBucket, ふたばフォレスト and ふたポ); null when none has it or no connection is available.
 */
internal suspend fun futaberArchivedThread(
    httpClient: io.ktor.client.HttpClient?,
    repository: com.valoser.futacha.shared.repo.BoardRepository,
    threadId: String,
    boardUrl: String,
    archiveSearchJson: kotlinx.serialization.json.Json
): com.valoser.futacha.shared.model.ThreadPage? {
    if (httpClient == null) return null
    val outcome = try {
        com.valoser.futacha.shared.ui.board.performThreadArchiveFallback(
            httpClient = httpClient,
            repository = repository,
            threadId = threadId,
            threadTitle = null,
            boardUrl = boardUrl,
            threadUrlOverride = null,
            archiveSearchJson = archiveSearchJson
        )
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        return null
    }
    return (outcome as? com.valoser.futacha.shared.ui.board.ArchiveFallbackOutcome.Success)?.page
}


/** The key of the shared setting that says how alike two pictures must be to count as the same NG image. */
internal val futaberImageNgThresholdKey: String =
    com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("thread", "threadImageNgPhashThreshold")

/** The address of a post's picture (the full-size one, else the thumbnail), or null for a post without one. */
internal fun futaberImageUrlOf(post: Post): String? =
    (post.imageUrl?.takeIf { it.isNotBlank() } ?: post.thumbnailUrl?.takeIf { it.isNotBlank() })

/** One line for an image NG rule in the settings: its note, else the file name of the picture it was made from. */
internal fun futaberImageNgRuleLabel(rule: com.valoser.futacha.shared.compat.CompatNgRule): String {
    val file = rule.imageUrl.orEmpty().substringBefore('?').substringAfterLast('/')
    val scope = if (rule.scopeKey == "*") "全板" else "この板"
    return listOf(rule.memo.trim().ifEmpty { file }, scope).filter { it.isNotEmpty() }.joinToString("　")
}


/** How long the auto-saved copy may take to be found before the load failure is shown instead (as ふたちゃ allows). */
internal const val FUTABER_OFFLINE_COPY_TIMEOUT_MILLIS = 5_000L

/** What the screen says while a thread is shown from the auto-saved copy because the live page could not be read. */
internal const val FUTABER_AUTO_SAVED_COPY_NOTICE = "通信できないため、保存したコピーを表示しています。最新のスレッドとは異なる場合があります"

/**
 * The newest auto-saved copy (the one the history keeps of each thread it refreshes) of [threadId], the copy ふたちゃ
 * falls back to when the thread cannot be read; null when there is none or it cannot be read.
 */
internal suspend fun futaberLoadAutoSavedPage(
    repository: SavedThreadRepository?,
    fileSystem: FileSystem?,
    board: BoardSummary,
    threadId: String
): ThreadPage? {
    if (repository == null || fileSystem == null) return null
    val lookup = buildOfflineThreadLookupContext(
        boardId = board.id,
        initialHistoryBoardId = board.id,
        effectiveBoardUrl = board.url,
        boardUrl = board.url,
        initialHistoryBoardUrl = board.url
    )
    return try {
        withContext(AppDispatchers.io) {
            loadOfflineThreadPageCandidate(
                threadId, lookup, fileSystem, listOf(OfflineThreadSource(repository, AUTO_SAVE_DIRECTORY))
            )?.page
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

/** The loader the thread screen calls to show the auto-saved copy; null when there is no repository or file system to read it from. */
internal fun futaberAutoSavedCopyLoader(
    repository: SavedThreadRepository?,
    fileSystem: FileSystem?,
    board: BoardSummary,
    threadId: String
): (suspend () -> ThreadPage?)? {
    if (repository == null || fileSystem == null) return null
    val loader: suspend () -> ThreadPage? = { futaberLoadAutoSavedPage(repository, fileSystem, board, threadId) }
    return loader
}

private fun String.hasJapanese(): Boolean = any { it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF' || it in '\uFF00'..'\uFFEF' }

/**
 * What to tell the person when something could not be loaded or saved: a plain Japanese sentence instead of the
 * exception's own (English) text. A server answer, a timeout and a lost connection each get their own words; a message
 * that is already Japanese is kept; anything else reads [fallback]. [target] names what was asked for ("スレッド").
 */
internal fun futaberFriendlyLoadError(
    error: Throwable,
    target: String = "データ",
    fallback: String = "${target}を読み込めませんでした"
): String {
    val status = error.statusCodeOrNull()
    return when {
        status == 404 || status == 410 -> "${target}が見つかりませんでした（落ちたか、削除された可能性があります）"
        status == 401 || status == 403 -> "アクセスが拒否されました（HTTP $status）"
        status == 429 -> "アクセスが集中しています。しばらくしてからもう一度お試しください（HTTP 429）"
        status != null && status >= 500 -> "サーバーが応答していません。しばらくしてからもう一度お試しください（HTTP $status）"
        status != null -> "サーバーからエラーが返されました（HTTP $status）"
        isThreadLoadTimeout(error) -> "通信がタイムアウトしました。電波の良い場所でもう一度お試しください"
        isOfflineFallbackCandidate(error) -> "通信できませんでした。ネットワーク接続を確認してください"
        else -> error.message?.takeIf { it.isNotBlank() && it.hasJapanese() } ?: fallback
    }
}
