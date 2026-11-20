package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.valoser.futacha.shared.compat.BoardSite
import com.valoser.futacha.shared.compat.canonicalizeSiteThreadUrl
import com.valoser.futacha.shared.model.Post
import kotlin.time.TimeSource

internal class ThreadSearchJump(
    val threadUrl: String,
    val site: BoardSite,
    val postNumber: String
) {
    private val created = TimeSource.Monotonic.markNow()

    fun matches(url: String): Boolean =
        created.elapsedNow().inWholeSeconds < 90 && canonicalizeSiteThreadUrl(url)?.canonicalUrl == threadUrl

    fun matchesPost(id: String, sequence: Int?): Boolean =
        postNumber == if (site == BoardSite.AIMOGE) sequence?.toString() else id

    fun matchesPost(post: Post): Boolean = matchesPost(post.id, post.displayNumber)
}

internal class ThreadSearchJumpState {
    var pending by mutableStateOf<ThreadSearchJump?>(null)
        private set

    fun select(threadUrl: String, postNumber: String) {
        val thread = canonicalizeSiteThreadUrl(threadUrl)
        pending = if (thread != null && postNumber.isNotBlank()) {
            ThreadSearchJump(thread.canonicalUrl, thread.site, postNumber)
        } else null
    }

    fun consume(request: ThreadSearchJump): Boolean {
        if (pending !== request) return false
        pending = null
        return true
    }
}

internal val LocalThreadSearchJumpState = staticCompositionLocalOf<ThreadSearchJumpState?> { null }

internal const val THREAD_SEARCH_JUMP_UNAVAILABLE = "検索結果のレスは現在の表示にありません"

@Composable
internal fun <T> ThreadSearchJumpEffect(
    threadUrl: () -> String,
    rows: List<T>,
    ready: () -> Boolean,
    onUnavailable: () -> Unit,
    matchesPost: (ThreadSearchJump, T) -> Boolean,
    onJump: (Int) -> Unit
) {
    val state = LocalThreadSearchJumpState.current ?: return
    val request = state.pending ?: return
    if (!request.matches(threadUrl())) return
    val isReady = ready()
    val index = remember(request, rows, isReady) {
        if (isReady) rows.indexOfFirst { matchesPost(request, it) } else -1
    }
    LaunchedEffect(request, isReady, index) {
        if (isReady && state.consume(request)) {
            if (index >= 0) onJump(index) else onUnavailable()
        }
    }
}
