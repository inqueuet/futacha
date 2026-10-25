package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock

/** Adds confirmed status to an existing history row, never recreating one deleted while a request ran. */
@Composable
internal fun ThreadStatusObservationEffect(
    store: AppStateStore?, boardId: String, threadId: String,
    page: ThreadPage?, live: Boolean, gone: Boolean = false
) {
    if (store == null || (!live && !gone)) return
    val history by store.history.collectAsState(emptyList())
    val entry = history.firstOrNull { it.boardId == boardId && it.threadId == threadId }
    val status = if (!live && gone) entry?.threadStatus?.copy(isDead = true) ?: ThreadStatus(isDead = true)
        else confirmedThreadStatus(page, gone)
    LaunchedEffect(store, boardId, threadId, status, entry != null) {
        if (entry == null || status == null || entry.threadStatus == status) return@LaunchedEffect
        try {
            store.updateHistory { current -> current.map { row ->
                if (row.boardId == boardId && row.threadId == threadId) row.copy(threadStatus = status) else row
            } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Logger.e("ThreadStatus", "スレッドの状態を保存できませんでした", error) }
    }
}

@Composable
internal fun ThreadOpeningSummaryEffect(
    threadKey: String, baselinePostCount: Int?, posts: List<Post>?, selfIds: Set<String>,
    hiddenIds: Set<String> = emptySet(), enabled: Boolean, live: Boolean, truncated: Boolean = false,
    onNotice: (String) -> Unit,
    baselineLastPostId: String? = null,
    openingTracker: ThreadOpeningSummaryTracker? = null
) {
    val localTracker = remember(threadKey) { ThreadOpeningSummaryTracker(baselinePostCount, baselineLastPostId) }
    val tracker = openingTracker ?: localTracker
    val notify by rememberUpdatedState(onNotice)
    LaunchedEffect(threadKey, tracker, posts, selfIds, hiddenIds, enabled, live, truncated) {
        if (posts != null) tracker.accept(posts, selfIds, hiddenIds, live, truncated)?.let {
            if (enabled) notify(it.text)
        }
    }
}

/** A visible thread may advance its read record; a background refresh may not. Deleted history stays deleted. */
internal suspend fun recordForegroundThreadView(
    store: AppStateStore, boardId: String, threadId: String, posts: List<Post>, nowMillis: Long,
    boardUrl: String? = null, isVisible: () -> Boolean = { true }
) {
    if (posts.isEmpty() || !isVisible()) return
    store.updateHistory { rows ->
        if (!isVisible()) return@updateHistory rows
        rows.map { row ->
            if (!matchesViewedThread(row, boardId, threadId, boardUrl)) row else {
                val before = row.lastViewedSnapshot
                val lastId = posts.last().id
                if (before != null && !threadPostsAfterBaseline(posts, before.postCount, before.lastPostId).any() &&
                    before.lastPostId != null && before.lastPostId != lastId) row
                else if (before?.postCount == posts.size && before.lastPostId == lastId) row
                else row.copy(lastViewedSnapshot = ThreadViewedSnapshot(posts.size, lastId, nowMillis))
            }
        }
    }
}

@Composable
internal fun ThreadViewedObservationEffect(
    store: AppStateStore?, boardId: String, threadId: String, page: ThreadPage?, live: Boolean,
    boardUrl: String? = null
) {
    if (store == null) return
    val visible = remember(store, boardId, threadId) { MutableStateFlow(false) }
    com.valoser.futacha.shared.ui.compat.CompatForegroundLifecycleEffect { visible.value = it }
    val foreground by visible.collectAsState()
    val lock = com.valoser.futacha.shared.ui.LocalFutachaAppLockHolder.current
    val unlocked by (lock?.sessionUnlocked?.collectAsState() ?: remember { mutableStateOf(true) })
    val history by store.history.collectAsState(emptyList())
    val hasEntry = history.any { matchesViewedThread(it, boardId, threadId, boardUrl) }
    val posts = page?.posts.orEmpty()
    LaunchedEffect(store, boardId, threadId, posts.size, posts.lastOrNull()?.id, live, foreground, unlocked, hasEntry) {
        if (!live || !foreground || !unlocked || !hasEntry || page?.isTruncated != false) return@LaunchedEffect
        observeForegroundThreadView(store, boardId, threadId, posts, Clock.System.now().toEpochMilliseconds(),
            isVisible = { visible.value && lock?.isUnlocked != false }, boardUrl = boardUrl)
    }
}

/**
 * The effect body of [ThreadViewedObservationEffect]. A failed history write (full disk, I/O
 * error) is rolled back and recorded by the store; it must not escape the effect (M75).
 */
internal suspend fun observeForegroundThreadView(
    store: AppStateStore, boardId: String, threadId: String, posts: List<Post>, nowMillis: Long,
    isVisible: () -> Boolean, boardUrl: String?
) {
    try {
        recordForegroundThreadView(store, boardId, threadId, posts, nowMillis, isVisible = isVisible, boardUrl = boardUrl)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Logger.e("ThreadViewed", "スレッドを読んだ記録を保存できませんでした", error) }
}

internal fun matchesViewedThread(row: ThreadHistoryEntry, boardId: String, threadId: String, boardUrl: String?): Boolean {
    if (row.threadId != threadId) return false
    if (row.boardId == boardId) return true
    val canonical = boardUrl?.let { com.valoser.futacha.shared.compat.canonicalizeBoardUrl(it) } ?: return false
    return com.valoser.futacha.shared.compat.canonicalizeBoardUrl(row.boardUrl) == canonical
}
