package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException

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
    onNotice: (String) -> Unit
) {
    val tracker = remember(threadKey) { ThreadOpeningSummaryTracker(baselinePostCount) }
    val notify by rememberUpdatedState(onNotice)
    LaunchedEffect(threadKey, posts, selfIds, hiddenIds, enabled, live, truncated) {
        if (posts != null) tracker.accept(posts, selfIds, hiddenIds, live, truncated)?.let {
            if (enabled) notify(it.text)
        }
    }
}
