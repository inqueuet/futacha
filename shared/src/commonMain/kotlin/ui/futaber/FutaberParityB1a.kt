package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.formatSize
import com.valoser.futacha.shared.ui.board.nextThreadSearchResultIndex
import com.valoser.futacha.shared.ui.board.previousThreadSearchResultIndex
import com.valoser.futacha.shared.ui.board.savedThreadCompletionSummary
import com.valoser.futacha.shared.ui.compat.CompatTouchScrollAction
import com.valoser.futacha.shared.ui.compat.buildCompatForestUrl
import com.valoser.futacha.shared.ui.compat.buildCompatFtbucketUrl
import com.valoser.futacha.shared.ui.compat.buildCompatFutapoUrl
import com.valoser.futacha.shared.ui.compat.compatTouchScrollAction
import com.valoser.futacha.shared.ui.compat.registerCompatTsumanne
import com.valoser.futacha.shared.ui.compat.toCompatUserMessage
import com.valoser.futacha.shared.ui.isIosReviewPlatform
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * The first half of the B1 batch of the mode parity work (docs/mode-feature-parity-plan-2026-10-12.md §5.3): things
 * ふたちゃ and としあき(仮) have that this mode lacked. Everything here is behind a switch of the extensions page (off by
 * default) except "追加して開く" for a board that is not registered, which only asks first and adds nothing by itself.
 * Kept apart from the big screens (FutaberApp / FutaberThread), which take only the calls.
 */

private const val PARITY_TAG = "FutaberParity"

// ---- H11: closing several tabs at once keeps the starred threads ----

/**
 * The tabs after a bulk close turned [before] into [after]: with [enabled], the tabs of starred threads that the close
 * took away are put back where they were (the order of [before] is kept). A key that was not a tab before is never added.
 */
internal fun futaberKeepFavoriteTabs(
    before: List<FutaberTabKey>,
    after: List<FutaberTabKey>,
    favorites: List<FutaberFavorite>,
    enabled: Boolean
): List<FutaberTabKey> {
    if (!enabled) return after
    val kept = after.toHashSet()
    val starred = favorites.mapTo(HashSet()) { it.key }
    return before.filter { it in kept || it in starred }
}

// ---- H12: a row of a board that is not registered offers to add it, then opens it ----

/** What "「◯◯」を板一覧に追加して開きますか？" adds ([canonicalBoardUrl] is `https://host/b/`) and opens afterwards. */
internal data class FutaberBoardAddRequest(
    val name: String,
    val canonicalBoardUrl: String,
    /** The thread to open once the board is there; null opens the board's catalog. */
    val threadId: String?,
    val title: String = "",
    val thumbnailUrl: String = "",
    val replyCount: Int = 0
) {
    /** The address the board is registered with, the form the board list uses for the official boards. */
    val boardUrl: String get() = canonicalBoardUrl + "futaba.php"
}

/**
 * The request for [rawUrl] (a thread's or a board's address), or null when it is not an official board's address (the
 * tutorial board, another site): those keep saying that the board is not registered, as before.
 */
internal fun futaberBoardAddRequest(
    rawUrl: String,
    boardName: String = "",
    threadId: String? = null,
    title: String = "",
    thumbnailUrl: String = "",
    replyCount: Int = 0
): FutaberBoardAddRequest? {
    val thread = canonicalizeThreadUrl(rawUrl)
    val canonicalBoard = thread?.canonicalBoardUrl ?: canonicalizeBoardUrl(rawUrl) ?: return null
    val thread0 = thread?.threadNo ?: threadId?.takeIf { it.isNotBlank() }
    val name = boardName.trim().ifEmpty { futaberDefaultBoardName(canonicalBoard + "futaba.php") }
    return FutaberBoardAddRequest(
        name = futaberSafeTake(name, FUTABER_BOARD_NAME_MAX_CHARS),
        canonicalBoardUrl = canonicalBoard,
        threadId = thread0,
        title = title,
        thumbnailUrl = thumbnailUrl,
        replyCount = replyCount
    )
}

/** For a history row: its address is the thread's own (older rows of other modes may hold the board's). */
internal fun futaberBoardAddRequestForHistory(entry: ThreadHistoryEntry): FutaberBoardAddRequest? =
    futaberBoardAddRequest(
        entry.boardUrl, entry.boardName, entry.threadId, entry.title.ifBlank { "(無題)" }, entry.titleImageUrl, entry.replyCount
    )

/** For a patrol result (its address is the canonical thread address). */
internal fun futaberBoardAddRequestForWatchResult(result: CompatWatchResult): FutaberBoardAddRequest? {
    val entry = result.history
    return futaberBoardAddRequest(
        entry.canonicalUrl, entry.boardName, entry.threadNo, entry.title.ifBlank { "(無題)" },
        entry.thumbnailUrl.orEmpty(), entry.replyCount
    )
}

/** The registered board with the same canonical address, if any (it may have been registered under another id). */
internal fun futaberBoardByCanonicalUrl(boards: List<BoardSummary>, canonicalBoardUrl: String): BoardSummary? =
    boards.firstOrNull { canonicalizeBoardUrl(it.url) == canonicalBoardUrl }

internal fun futaberBoardAddQuestion(request: FutaberBoardAddRequest): String =
    "「${request.name}」を板一覧に追加して開きますか？"

/** Holds the question on screen and the board that is to be opened once it is in the list. */
@Stable
internal class FutaberBoardAddController {
    var asking by mutableStateOf<FutaberBoardAddRequest?>(null)
    var pending by mutableStateOf<FutaberBoardAddRequest?>(null)
}

/**
 * The question and the opening afterwards. [openRequest] is what the screens call: a board that is registered (by its
 * address, under any id) opens at once, a board that is not is asked about first.
 */
@Composable
internal fun FutaberBoardAddHost(
    controller: FutaberBoardAddController,
    boards: List<BoardSummary>,
    stateStore: AppStateStore,
    onOpenThread: (BoardSummary, FutaberThreadRef) -> Unit,
    onOpenBoard: (BoardSummary) -> Unit,
    notify: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val colors = LocalFutaberColors.current
    // Once the board is in the list (the same waiting for the boards a link from another app does), it is opened.
    LaunchedEffect(controller.pending, boards) {
        val request = controller.pending ?: return@LaunchedEffect
        val target = futaberBoardByCanonicalUrl(boards, request.canonicalBoardUrl) ?: return@LaunchedEffect
        controller.pending = null
        futaberOpenAddedBoard(target, request, onOpenThread, onOpenBoard)
    }
    val request = controller.asking ?: return
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { controller.asking = null },
        containerColor = colors.bar,
        titleContentColor = colors.body,
        textContentColor = colors.body,
        title = { Text("板が登録されていません") },
        text = { Text(futaberBoardAddQuestion(request), modifier = Modifier.testTag("futaber-board-add-question")) },
        confirmButton = {
            TextButton(
                onClick = {
                    controller.asking = null
                    controller.pending = request
                    scope.launch {
                        try {
                            var present = false
                            stateStore.updateBoards { latest ->
                                val next = futaberAddBoard(latest, request.name, request.boardUrl)
                                present = futaberBoardByCanonicalUrl(next, request.canonicalBoardUrl) != null
                                next
                            }
                            if (!present) {
                                controller.pending = null
                                notify("板を追加できませんでした")
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Logger.e(PARITY_TAG, "Failed to add a board", error)
                            controller.pending = null
                            notify(FUTABER_STORE_FAILURE_NOTICE)
                        }
                    }
                },
                modifier = Modifier.testTag("futaber-board-add-confirm")
            ) { Text("追加して開く", color = colors.link) }
        },
        dismissButton = {
            TextButton(onClick = { controller.asking = null }, modifier = Modifier.testTag("futaber-board-add-cancel")) {
                Text("キャンセル", color = colors.link)
            }
        }
    ) }
}

/** Opens [request] on [boards] at once when its board is registered; otherwise asks to add it. */
internal fun FutaberBoardAddController.openRequest(
    request: FutaberBoardAddRequest,
    boards: List<BoardSummary>,
    onOpenThread: (BoardSummary, FutaberThreadRef) -> Unit,
    onOpenBoard: (BoardSummary) -> Unit
) {
    val registered = futaberBoardByCanonicalUrl(boards, request.canonicalBoardUrl)
    if (registered != null) futaberOpenAddedBoard(registered, request, onOpenThread, onOpenBoard)
    else asking = request
}

private fun futaberOpenAddedBoard(
    target: BoardSummary,
    request: FutaberBoardAddRequest,
    onOpenThread: (BoardSummary, FutaberThreadRef) -> Unit,
    onOpenBoard: (BoardSummary) -> Unit
) {
    val threadId = request.threadId
    if (threadId == null) onOpenBoard(target)
    else onOpenThread(target, FutaberThreadRef(target.id, threadId, request.title, request.thumbnailUrl, request.replyCount))
}

// ---- W08: the external archives of the open thread ----

/** The places "URL・アーカイブ" offers for [threadUrl]; null where that archive does not keep the board. */
internal data class FutaberArchiveLinks(val ftbucket: String, val forest: String?, val futapo: String?)

internal fun futaberArchiveLinks(threadUrl: String): FutaberArchiveLinks =
    FutaberArchiveLinks(buildCompatFtbucketUrl(threadUrl), buildCompatForestUrl(threadUrl), buildCompatFutapoUrl(threadUrl))

/**
 * The sheet of "URL・アーカイブ": the same four actions and the same messages as としあき(仮). Registering in つまんね。
 * sends the thread's address there (only when the person picks it); the others open the archive in the browser.
 *
 * The sheet closes as soon as an action is picked, so the registration runs in [scope], which must outlive the sheet
 * (the thread screen's): a scope of the sheet itself was cancelled with it and the result was never reported.
 */
@Composable
internal fun FutaberArchiveMenuSheet(
    threadUrl: String,
    title: String,
    httpClient: HttpClient?,
    scope: CoroutineScope,
    openUrl: (String) -> Unit,
    onNotice: (String) -> Unit,
    onDismiss: () -> Unit,
    register: suspend (HttpClient, String, String) -> Result<String> = ::registerCompatTsumanne
) {
    val links = remember(threadUrl) { futaberArchiveLinks(threadUrl) }
    FutaberPostActionSheet(
        title = "URL・アーカイブ",
        actions = listOf(
            FutaberPostAction("archive-ftbucket", "FTBucketに登録") { onDismiss(); openUrl(links.ftbucket) },
            FutaberPostAction("archive-tsumanne", "つまんね。に登録") {
                onDismiss()
                futaberRegisterTsumanne(scope, httpClient, threadUrl, title, openUrl, onNotice, register)
            },
            FutaberPostAction("archive-forest", "ふたばフォレストで開く") {
                onDismiss()
                links.forest?.let(openUrl) ?: onNotice("ふたばフォレストはmay板のスレだけ対応しています")
            },
            FutaberPostAction("archive-futapo", "ふたポで開く") {
                onDismiss()
                links.futapo?.let(openUrl) ?: onNotice("ふたポのURLを作成できませんでした（may/img板のみ対応）")
            },
            FutaberPostAction("cancel", "キャンセル") { onDismiss() }
        ),
        onDismiss = onDismiss
    )
}

internal fun futaberRegisterTsumanne(
    scope: CoroutineScope,
    httpClient: HttpClient?,
    threadUrl: String,
    title: String,
    openUrl: (String) -> Unit,
    onNotice: (String) -> Unit,
    register: suspend (HttpClient, String, String) -> Result<String> = ::registerCompatTsumanne
) {
    val client = httpClient ?: run { onNotice("通信機能を初期化できませんでした"); return }
    onNotice("つまんね。に登録しています…")
    scope.launch {
        register(client, threadUrl, title)
            .onSuccess { openUrl(it); onNotice("つまんね。に登録しました") }
            .onFailure { onNotice(it.toCompatUserMessage("つまんね。への登録に失敗しました")) }
    }
}

// ---- S05: how complete a saved thread is ----

/** The text of the "保存状態" dialog: the shared summary of the save, then the counts and the size. */
internal fun futaberSavedStatusText(saved: SavedThread): String = buildString {
    append(savedThreadCompletionSummary(saved))
    append("\n\n投稿 ${saved.postCount}件　画像 ${saved.imageCount}件　動画 ${saved.videoCount}件")
    append("\n容量 ${formatSize(saved.totalSize)}")
    append("\n未保存 ${saved.incompleteMediaCount}件" + if (saved.isHtmlMissing) "（HTMLなし）" else "")
}

@Composable
internal fun FutaberSavedStatusDialog(
    saved: SavedThread,
    /** Opens the live thread so it can be saved again from its menu; null when its board is not registered. */
    onReopen: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bar,
        titleContentColor = colors.body,
        textContentColor = colors.body,
        title = { Text("保存状態") },
        text = { Text(futaberSavedStatusText(saved), modifier = Modifier.testTag("futaber-saved-status")) },
        confirmButton = {
            if (onReopen != null) TextButton(
                onClick = { onDismiss(); onReopen() },
                modifier = Modifier.testTag("futaber-saved-reopen")
            ) { Text("スレッドを開いて保存し直す", color = colors.link) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = colors.link) } }
    ) }
}

// ---- H10: the favourites of the other modes, read only ----

/** One starred thread of another mode: the thread to open here (null while its board is not registered). */
internal data class FutaberOtherFavorite(val tab: CompatTab, val ref: FutaberThreadRef?)

/** The other modes' starred tabs, their board matched by the shared board key (the canonical address). */
internal fun futaberOtherModeFavorites(tabs: List<CompatTab>, boards: List<BoardSummary>): List<FutaberOtherFavorite> {
    val byKey = boards.associateBy { futaberCompatBoardKey(it) }
    return tabs.filter { it.favorite && it.threadNo.isNotBlank() }.distinctBy { it.key }.map { tab ->
        val board = byKey[tab.boardKey]
        FutaberOtherFavorite(
            tab,
            board?.let { FutaberThreadRef(it.id, tab.threadNo, tab.title.ifBlank { "(無題)" }, tab.thumbnailUrl.orEmpty(), tab.replyCount) }
        )
    }
}

internal fun futaberBoardAddRequestForOtherFavorite(favorite: FutaberOtherFavorite): FutaberBoardAddRequest? {
    val tab = favorite.tab
    return futaberBoardAddRequest(
        tab.canonicalUrl.ifBlank { tab.originalUrl }, tab.boardName, tab.threadNo, tab.title.ifBlank { "(無題)" },
        tab.thumbnailUrl.orEmpty(), tab.replyCount
    )
}

// ---- T13: a page up / down, and the top ----

/** Scrolls the list one screen up or down (the amount the volume keys use); false when the list has no size yet. */
internal suspend fun futaberScrollPage(listState: LazyListState, up: Boolean): Boolean {
    val page = listState.layoutInfo.viewportSize.height.toFloat()
    if (page <= 0f) return false
    listState.animateScrollBy(if (up) -page else page)
    return true
}

/** The operation menu's "1ページ上へ" / "1ページ下へ" / "先頭へ" (the scroll menu extension). */
internal fun futaberScrollMenuItems(
    scope: CoroutineScope,
    listState: LazyListState,
    enabled: Boolean,
    onDismiss: () -> Unit
): List<FutaberMenuItem> = listOf(
    FutaberMenuItem("page-up", "1ページ上へ", Icons.Outlined.KeyboardArrowUp, enabled = enabled) {
        onDismiss(); scope.launch { futaberScrollPage(listState, up = true) }
    },
    FutaberMenuItem("page-down", "1ページ下へ", Icons.Outlined.KeyboardArrowDown, enabled = enabled) {
        onDismiss(); scope.launch { futaberScrollPage(listState, up = false) }
    },
    FutaberMenuItem("top", "先頭へ", Icons.Outlined.VerticalAlignTop, enabled = enabled) {
        onDismiss(); scope.launch { listState.animateScrollToItem(0) }
    }
)

// ---- T05: moving between the posts a search found ----

/** The next ([forward]) or previous found post; from "none yet" (-1) the first or the last. */
internal fun futaberSearchHitStep(current: Int, count: Int, forward: Boolean): Int =
    if (forward) nextThreadSearchResultIndex(current, count) else previousThreadSearchResultIndex(current, count)

/** "n/N", or "-/N" before the first move. */
internal fun futaberSearchHitLabel(current: Int, count: Int): String =
    if (current in 0 until count) "${current + 1}/$count" else "-/$count"

/** "▲ n/N ▼" inside the filter chip; each arrow is its own button, so the chip's own tap still clears the search. */
@Composable
internal fun FutaberSearchHitNav(current: Int, count: Int, onMove: (forward: Boolean) -> Unit) {
    val colors = LocalFutaberColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
        Text(
            "▲", color = colors.link, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.heightIn(min = 32.dp).clickable(onClickLabel = "前の一致へ") { onMove(false) }
                .padding(horizontal = 8.dp, vertical = 6.dp).testTag("futaber-search-prev")
        )
        Text(futaberSearchHitLabel(current, count), color = colors.body, fontSize = 12.sp, modifier = Modifier.testTag("futaber-search-position"))
        Text(
            "▼", color = colors.link, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.heightIn(min = 32.dp).clickable(onClickLabel = "次の一致へ") { onMove(true) }
                .padding(horizontal = 8.dp, vertical = 6.dp).testTag("futaber-search-next")
        )
    }
}

// ---- T14: tapping the upper or lower third of the thread scrolls a page ----

/** -1 for a page up, 1 for a page down, 0 for the middle third (and for a point outside the list). */
internal fun futaberTapScrollDirection(yInList: Float, listHeight: Float): Int =
    when (compatTouchScrollAction(yInList, listHeight)) {
        CompatTouchScrollAction.PAGE_UP -> -1
        CompatTouchScrollAction.PAGE_DOWN -> 1
        CompatTouchScrollAction.NONE -> 0
    }

/**
 * Where the thread's list is, so a tap on a post (in root coordinates) can be told apart by its third. Taps reach it only
 * from the posts' own tap detector, which the quotes, IDs, links, pictures and counters take before it.
 */
internal class FutaberTapScroll(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val leftEdgePx: Float
) {
    private var listCoordinates: LayoutCoordinates? = null
    val listModifier: Modifier = Modifier.onPlaced { listCoordinates = it }

    fun onTap(rootOffset: Offset) {
        val list = listCoordinates?.takeIf { it.isAttached } ?: return
        val origin = list.positionInRoot()
        // On iOS the left edge belongs to the swipe that opens the menu.
        if (rootOffset.x - origin.x < leftEdgePx) return
        when (futaberTapScrollDirection(rootOffset.y - origin.y, list.size.height.toFloat())) {
            -1 -> scope.launch { futaberScrollPage(listState, up = true) }
            1 -> scope.launch { futaberScrollPage(listState, up = false) }
        }
    }
}

/** The tap scroll of the thread, or null while it is off (the posts then take no tap at all, as before). */
@Composable
internal fun rememberFutaberTapScroll(listState: LazyListState, enabled: Boolean): FutaberTapScroll? {
    val scope = rememberCoroutineScope()
    val edgePx = with(LocalDensity.current) { if (isIosReviewPlatform()) 20.dp.toPx() else 0f }
    val holder = remember(listState, edgePx) { FutaberTapScroll(scope, listState, edgePx) }
    return if (enabled) holder else null
}

// ---- P12: the shared "板名の誤投稿確認" ----

/** The warning the confirmation shows (the same words as としあき(仮) and ふたちゃ), or null when there is none or it is off. */
internal fun futaberPostDestinationWarning(boardUrl: String, comment: String, enabled: Boolean): String? =
    com.valoser.futacha.shared.ui.compat.compatPostDestinationWarning(boardUrl, comment, enabled)
