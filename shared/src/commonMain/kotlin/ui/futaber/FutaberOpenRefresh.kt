package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// L-B7「開く・更新・タップ」 (look doc §1.2 S22・G21・F03・G20): the pieces kept apart from the big screens.

// ---- S22: a catalog row loads its thread before the thread opens ----

/** The thread a catalog row is loading before it opens; [token] tells a late answer of an earlier press from this one. */
internal data class FutaberOpening(val boardId: String, val threadId: String, val token: Int)

/**
 * What the catalog's opening of a thread is doing (S22, pure): nothing, or loading one row's thread. A new press replaces
 * the one loading (the earlier answer is then refused by its token); back and a board switch cancel it.
 */
internal data class FutaberOpenFlow(val opening: FutaberOpening? = null, val nextToken: Int = 1) {
    /** The flow after a row of [threadId] was pressed, and the token its answer must carry. */
    fun start(boardId: String, threadId: String): Pair<FutaberOpenFlow, Int> =
        FutaberOpenFlow(FutaberOpening(boardId, threadId, nextToken), nextToken + 1) to nextToken

    /** Whether the answer with [token] is still wanted (not cancelled, not replaced by a later press). */
    fun accepts(token: Int): Boolean = opening?.token == token

    /** The flow once the answer of [token] arrived: back to nothing when it was the one wanted. */
    fun finish(token: Int): FutaberOpenFlow = if (accepts(token)) copy(opening = null) else this

    fun cancel(): FutaberOpenFlow = copy(opening = null)

    /** The row painted as pressed in the catalog of [boardId], while its thread loads. */
    fun pressedThreadId(boardId: String): String? = opening?.takeIf { it.boardId == boardId }?.threadId
}

/**
 * What a thread is opened with: the page the catalog loaded before opening it (a failure too, so the fallbacks run at
 * once instead of after a second wait), and whether it was opened from the tab strip (F03 then tells the new posts
 * since the stored seen count).
 */
internal class FutaberThreadHandoff(
    val key: FutaberTabKey,
    val content: Result<ThreadPageContent>? = null,
    val fromTab: Boolean = false
)

/**
 * S22: the catalog's opening of a thread. [open] marks the row and loads the page; when it is there (or failed) the
 * thread opens with it ([take] gives it to that thread once). Cancelled by [cancel] (back, a board switch, another way
 * of opening a thread); a second press replaces the first.
 */
@Stable
internal class FutaberCatalogOpener(private val fetchTimeoutMillis: Long = FUTABER_OPEN_FETCH_TIMEOUT_MILLIS) {
    var flow by mutableStateOf(FutaberOpenFlow())
        private set
    private var job: Job? = null
    private var handoff: FutaberThreadHandoff? = null

    val isLoading: Boolean get() = flow.opening != null

    fun pressedThreadId(boardId: String): String? = flow.pressedThreadId(boardId)

    fun open(
        scope: CoroutineScope,
        boardId: String,
        threadId: String,
        fetch: suspend () -> ThreadPageContent,
        onOpen: () -> Unit
    ) {
        job?.cancel()
        val (started, token) = flow.start(boardId, threadId)
        flow = started
        job = scope.launch {
            val result = try {
                // A connection that stops answering no longer leaves 「Loading...」 up: past the limit the thread opens
                // with the failure, as for a timeout of the fetch itself (audit A9).
                kotlinx.coroutines.withTimeoutOrNull(fetchTimeoutMillis) { fetch() }?.let { Result.success(it) }
                    ?: Result.failure(FutaberOpenTimeoutException())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (!flow.accepts(token)) return@launch
            flow = flow.finish(token)
            job = null
            onOpen()
            // After onOpen: opening a thread clears what an earlier way of opening left (see [reset]).
            handoff = FutaberThreadHandoff(FutaberTabKey(boardId, threadId), content = result)
        }
    }

    /** Back, a board switch: the load stops and the row is no longer marked. */
    fun cancel() {
        job?.cancel()
        job = null
        if (flow.opening != null) flow = flow.cancel()
    }

    /** Every way of opening a thread starts here: a pending load stops and an unused handoff is dropped. */
    fun reset() {
        cancel()
        handoff = null
    }

    /** F03: the thread of [key] is being opened from the tab strip. */
    fun openedFromTab(key: FutaberTabKey) {
        handoff = FutaberThreadHandoff(key, fromTab = true)
    }

    /** What the thread of [key] is opened with, once (null for any other thread). */
    fun take(key: FutaberTabKey?): FutaberThreadHandoff? {
        val held = handoff ?: return null
        handoff = null
        return held.takeIf { it.key == key }
    }
}

/** The iOS UI test's delay of the tutorial board's thread (0 everywhere else, and in a release build). */
internal expect fun futaberTestThreadDelayMillis(): Long

/** The bundled tutorial board's repository; with a test delay its thread answers late (S22's "Loading..." can be seen). */
internal fun futaberTutorialRepository(threadDelayMillis: Long = futaberTestThreadDelayMillis()): BoardRepository {
    val fake = FakeBoardRepository()
    if (threadDelayMillis <= 0L) return fake
    return object : BoardRepository by fake {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            delay(threadDelayMillis)
            return fake.getThreadContent(board, threadId)
        }
    }
}

/** S22: the HUD over the catalog while a row's thread loads; Android's back cancels the load (iOS's edge does nothing here). */
@Composable
internal fun FutaberCatalogOpeningLayer(opener: FutaberCatalogOpener?) {
    if (opener == null || !opener.isLoading) return
    PlatformBackHandler(iosEdgeGestureEnabled = false) { opener.cancel() }
    FutaberHud()
}

// ---- G21: pulling up at the end of the thread refreshes it ----

/** How far past the end the finger must pull before letting go refreshes (about 60, look doc §1.2 item 17). */
internal const val FUTABER_PULL_REFRESH_DP = 60f

/** The end row's "引っ張って..." (the original's 50pt label under the expiry). */
internal const val FUTABER_PULL_LABEL = "引っ張って..."
internal const val FUTABER_PULL_ROW_DP = 50

/**
 * G21 (pure): adds up what a finger pulls up past the end of the list; [release] tells whether letting go refreshes.
 * Only the finger's own scroll counts (a fling that runs into the end never refreshes), and pulling back down takes off.
 */
internal class FutaberEndPull(private val thresholdPx: Float) {
    var pulledPx: Float = 0f
        private set

    /** A new touch: nothing is pulled yet. */
    fun start() { pulledPx = 0f }

    /**
     * A move upward of [leftOverY] (negative: the finger moves up), [byFinger] whether the finger moved (not a fling),
     * [atEnd] whether the list is at its end then.
     */
    fun onScroll(leftOverY: Float, byFinger: Boolean, atEnd: Boolean) {
        if (!byFinger) return
        if (leftOverY < 0f && atEnd) pulledPx += -leftOverY
    }

    /** The finger moved down by [downPx] (a positive scroll toward the top): the pull is taken back. */
    fun onPullBack(downPx: Float) {
        if (downPx > 0f) pulledPx = (pulledPx - downPx).coerceAtLeast(0f)
    }

    /** The finger was lifted: true once when it had pulled far enough. */
    fun release(): Boolean {
        val fire = pulledPx >= thresholdPx
        pulledPx = 0f
        return fire
    }
}

/**
 * G21: the modifier of the thread's list that refreshes it when the finger pulls up past its end and lets go. It reads
 * the finger itself, before the list (`PointerEventPass.Initial`), and consumes nothing: the list scrolls and stretches
 * (Android) or bounces (iOS) as before. The finger's movement counts only while the list is at its end (iOS's bounce
 * takes the scroll's left-over itself, so the nested scroll's left-over is not a measure there); a fling never counts.
 */
@Composable
internal fun rememberFutaberEndPull(listState: LazyListState, enabled: Boolean, onRefresh: () -> Unit): Modifier {
    if (!enabled) return Modifier
    val thresholdPx = with(LocalDensity.current) { FUTABER_PULL_REFRESH_DP * density }
    val pull = remember(thresholdPx) { FutaberEndPull(thresholdPx) }
    val refresh by rememberUpdatedState(onRefresh)
    return Modifier.pointerInput(pull, listState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pull.start()
            var lastY = down.position.y
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && change.pressed) {
                    val dy = change.position.y - lastY
                    lastY = change.position.y
                    if (dy < 0f) pull.onScroll(dy, byFinger = true, atEnd = !listState.canScrollForward) else pull.onPullBack(dy)
                }
            } while (event.changes.any { it.pressed })
            if (pull.release()) refresh()
        }
    }
}

// ---- F03: 「新着 N」 after a refresh and on switching tabs ----

/** F03: the posts a load brought: [after] fetched posts against [before] (the previous page, or the seen count); never below 0. */
internal fun futaberNewCountAfterLoad(before: Int, after: Int): Int = (after - before).coerceAtLeast(0)

/**
 * F03: when a load is to be followed by 「新着 N」. Armed by the refresh button and the pull with the page's count before,
 * and on opening from a tab with the seen count; a live page then shows the box, anything else (a failure, an archive, a
 * copy, another reload) does not. Opening from the catalog never arms it (as the original, as far as it was captured).
 */
@Stable
internal class FutaberNewCountNotice(initialBase: Int?) {
    private var base: Int? = initialBase
    var message by mutableStateOf<String?>(null)
        private set

    fun arm(before: Int?) { base = before }

    fun onLivePage(after: Int) {
        val from = base ?: return
        base = null
        message = futaberNewPostsToastText(futaberNewCountAfterLoad(from, after))
    }

    fun disarm() { base = null }

    fun hide() { message = null }
}

/** How long opening from the catalog waits for the thread before it opens with a failure (audit A9). */
internal const val FUTABER_OPEN_FETCH_TIMEOUT_MILLIS = 30_000L

/** The fetch before opening took longer than [FUTABER_OPEN_FETCH_TIMEOUT_MILLIS]. */
internal class FutaberOpenTimeoutException : Exception("スレッドの読み込みがタイムアウトしました")
