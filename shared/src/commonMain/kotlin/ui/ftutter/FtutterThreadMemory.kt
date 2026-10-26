package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.Post

internal fun ftutterHistoryIsFallen(history: ThreadHistoryEntry?): Boolean =
    history?.threadStatus?.isDead == true || history?.isAutoRefreshDisabled == true

/**
 * 表示に使う状態。落ちの記録は2通り（今の `threadStatus.isDead` と、以前の記録・`.ftt` の sureOti の
 * `isAutoRefreshDisabled`）あるので、どちらでも「落ち」を出す（L18。拡張「タブの隔離・落ち状態」ON の時に以前の印が消えていた）。
 */
internal fun ftutterDisplayStatus(history: ThreadHistoryEntry?): com.valoser.futacha.shared.model.ThreadStatus? =
    if (ftutterHistoryIsFallen(history)) (history?.threadStatus ?: com.valoser.futacha.shared.model.ThreadStatus()).copy(isDead = true)
    else history?.threadStatus

/** Background refreshes only publish history, so a held page must be checked against its latest count. */
internal fun ftutterThreadPageIsStale(page: ThreadPage, history: ThreadHistoryEntry?): Boolean =
    !ftutterHistoryIsFallen(history) && (history?.replyCount ?: 0) > page.posts.size

internal fun ftutterHistoryMarkAlive(
    history: List<ThreadHistoryEntry>, boardId: String, threadId: String, now: Long
): List<ThreadHistoryEntry> = com.valoser.futacha.shared.ui.futaber.futaberHistoryMarkAlive(
    history, boardId, threadId, now
).map { entry ->
    if (entry.boardId == boardId && entry.threadId == threadId)
        entry.copy(threadStatus = entry.threadStatus?.copy(isDead = false))
    else entry
}

internal fun ftutterHistoryAfterLiveLoad(
    history: List<ThreadHistoryEntry>, boardId: String, threadId: String, posts: List<Post>, now: Long
): List<ThreadHistoryEntry> = ftutterHistoryMarkAlive(
    com.valoser.futacha.shared.ui.futaber.futaberHistoryAfterLoad(history, boardId, threadId, posts),
    boardId, threadId, now
)

/** 「タブを全て更新」のタブ1件の結果（L131）。 */
internal sealed interface FtutterTabRefreshResult {
    val boardId: String
    val threadId: String
    data class Loaded(override val boardId: String, override val threadId: String, val posts: List<Post>) : FtutterTabRefreshResult
    data class Fallen(override val boardId: String, override val threadId: String) : FtutterTabRefreshResult
}

/** 404/410 で落ちたと確かめたスレの行（落ちの2通りの記録をどちらも立てる）。 */
internal fun ftutterHistoryEntryMarkFallen(entry: ThreadHistoryEntry): ThreadHistoryEntry = entry.copy(
    isAutoRefreshDisabled = true,
    threadStatus = (entry.threadStatus ?: com.valoser.futacha.shared.model.ThreadStatus()).copy(isDead = true)
)

/**
 * 「タブを全て更新」の結果を1回の走査で履歴へ当てる（L131：以前はタブごとに共有の履歴全体（最大2万件）を書き直した）。
 * 他のスレの行は同じ実体のまま。
 */
internal fun ftutterHistoryAfterTabRefresh(
    history: List<ThreadHistoryEntry>, results: List<FtutterTabRefreshResult>, now: Long
): List<ThreadHistoryEntry> {
    if (results.isEmpty()) return history
    val byKey = results.associateBy { it.boardId to it.threadId }
    return history.map { entry ->
        when (val result = byKey[entry.boardId to entry.threadId]) {
            null -> entry
            is FtutterTabRefreshResult.Loaded ->
                ftutterHistoryAfterLiveLoad(listOf(entry), result.boardId, result.threadId, result.posts, now).single()
            is FtutterTabRefreshResult.Fallen -> ftutterHistoryEntryMarkFallen(entry)
        }
    }
}

internal data class FtutterTabRefreshTarget(val url: String, val boardId: String, val threadId: String)

/**
 * タブを順に取り直し、結果をためて [FTUTTER_TAB_REFRESH_BATCH] 件ごと・最後・中止の時にまとめて [apply] へ渡す（L131）。
 * 取り消されても、それまでに取れた分は当てる。[fetch] は取れたレスを返し、取れなければ投げる（[isGone] が真なら落ち）。
 */
internal suspend fun ftutterRefreshTabs(
    targets: List<FtutterTabRefreshTarget>,
    fetch: suspend (FtutterTabRefreshTarget) -> List<Post>,
    isGone: (Throwable) -> Boolean,
    apply: suspend (List<FtutterTabRefreshResult>) -> Unit,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
): Int {
    var failed = 0
    val pending = mutableListOf<FtutterTabRefreshResult>()
    suspend fun flush() {
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { apply(batch) }
    }
    try {
        targets.forEachIndexed { index, target ->
            onProgress(index, targets.size)
            try {
                pending += FtutterTabRefreshResult.Loaded(target.boardId, target.threadId, fetch(target))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failed++
                if (isGone(error)) pending += FtutterTabRefreshResult.Fallen(target.boardId, target.threadId)
            }
            if (pending.size >= FTUTTER_TAB_REFRESH_BATCH) flush()
        }
        onProgress(targets.size, targets.size)
    } finally {
        flush()
    }
    return failed
}

internal const val FTUTTER_TAB_REFRESH_BATCH = 10

internal class FtutterThreadMemory {
    private val pages = LinkedHashMap<String, ThreadPage>()
    private val refresh = mutableSetOf<String>()
    private val openings = LinkedHashMap<String, com.valoser.futacha.shared.ui.board.ThreadOpeningVisit>()
    fun open(url: String, refreshOnOpen: Boolean, postCount: Int? = null, lastPostId: String? = null) {
        if (refreshOnOpen) refresh += url else refresh -= url
        openings.remove(url)
        openings[url] = com.valoser.futacha.shared.ui.board.ThreadOpeningVisit().apply { initialize(postCount, lastPostId) }
        while (openings.size > 12) openings.remove(openings.keys.first())
    }
    fun opening(url: String): com.valoser.futacha.shared.ui.board.ThreadOpeningVisit =
        openings.getOrPut(url) { com.valoser.futacha.shared.ui.board.ThreadOpeningVisit() }

    fun page(url: String): ThreadPage? = pages[url]
    fun takeRefresh(url: String): Boolean = refresh.remove(url)
    fun put(url: String, page: ThreadPage) {
        pages.remove(url); pages[url] = page
        while (pages.size > 12) pages.remove(pages.keys.first())
    }
    fun remove(url: String) { pages.remove(url); refresh.remove(url); openings.remove(url) }
}
