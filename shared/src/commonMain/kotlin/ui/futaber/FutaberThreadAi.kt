package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.PostModerationResult
import com.valoser.futacha.shared.ai.detectCopyPastePosts
import com.valoser.futacha.shared.ai.getAiConnectionStore
import com.valoser.futacha.shared.ai.searchQueryParameter
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.AiCommandEffectKey
import com.valoser.futacha.shared.ui.board.OpenAiLimitNotice
import com.valoser.futacha.shared.ui.board.rememberModerationViewport
import com.valoser.futacha.shared.ui.board.rememberSelectedAiService
import com.valoser.futacha.shared.ui.compat.CompatThreadAiModerationPart
import com.valoser.futacha.shared.ui.compat.CompatThreadAiPanel
import com.valoser.futacha.shared.ui.compat.CompatThreadAiSession
import com.valoser.futacha.shared.ui.compat.CompatThreadAiState
import com.valoser.futacha.shared.ui.compat.CompatThreadAiSummaryPart
import com.valoser.futacha.shared.ui.compat.buildCompatThreadAiSourceKey
import com.valoser.futacha.shared.ui.compat.resolveVisibility
import com.valoser.futacha.shared.ui.isAiCommandForThread
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// A06 (AI thread summary) and A07 (AI hiding of trolling posts) for this mode, and the thread screen's side of the AI
// commands (X10-1). The AI parts run only while the shared switches are on (both off by default) and use the shared AI
// session the other modes use (device AI / OpenAI with their limits, consent and keys); nothing here sends analytics.

/**
 * What the AI of one thread decided, read by the thread screen: [hidden] joins the posts NG hides (so "NGを無効にする"
 * shows them again and "NGで隠したレス" lists them, as in ふたちゃ), [state] feeds the summary panel. Kept apart so the
 * thread screen is composed again only when the hidden posts change.
 */
@Stable
internal class FutaberThreadAiHolder {
    var hidden by mutableStateOf(emptySet<String>())
    var state by mutableStateOf(CompatThreadAiState())
    var retry by mutableIntStateOf(0)
}

/** One per thread: a refresh keeps the previous decisions until they are checked again, so hidden posts do not flash back. */
@Composable
internal fun rememberFutaberThreadAiHolder(boardId: String, threadId: String): FutaberThreadAiHolder =
    remember(boardId, threadId) { FutaberThreadAiHolder() }

/** The posts the thread hides: NG (words, headers, rules), look-alike pictures, and the AI's judgement. */
internal fun futaberHiddenWithAi(ngHidden: Set<String>, phashHidden: Set<String>, aiHidden: Set<String>): Set<String> = when {
    phashHidden.isEmpty() && aiHidden.isEmpty() -> ngHidden
    aiHidden.isEmpty() -> ngHidden + phashHidden
    else -> ngHidden + phashHidden + aiHidden
}

/** The order the moderation follows the screen in: the listed posts, by their own number. */
internal fun futaberAiOrderedIds(rows: List<FutaberRow>): List<String> = rows.map { it.post.id }

/** The list's item keys (`"<ordinal>:<post id>"`, the key the thread list gives its rows) to the post ids. */
internal fun futaberAiItemKeys(rows: List<FutaberRow>): Map<String, String> =
    rows.associate { "${it.ordinal}:${it.post.id}" to it.post.id }

/**
 * Runs the summary and the moderation of the thread on screen while their shared switches are on, and publishes what
 * they decided into [holder]. With both switches off nothing runs (and nothing is hidden).
 */
@Composable
internal fun FutaberThreadAi(
    holder: FutaberThreadAiHolder,
    stateStore: AppStateStore?,
    page: ThreadPage?,
    tabKey: String,
    title: String,
    selfPostIds: Set<String>,
    listState: LazyListState,
    rows: List<FutaberRow>
) {
    val orderedIds = remember(rows) { futaberAiOrderedIds(rows) }
    val itemKeys = remember(rows) { futaberAiItemKeys(rows) }
    FutaberThreadAiByIds(holder, stateStore, page, tabKey, title, selfPostIds, listState, orderedIds, itemKeys)
}

/**
 * [FutaberThreadAi] for a list that is not made of [FutaberRow]s (ふたったー風): [orderedIds] are the listed posts in
 * screen order, [itemKeys] the list's item keys to the post ids.
 */
@Composable
internal fun FutaberThreadAiByIds(
    holder: FutaberThreadAiHolder,
    stateStore: AppStateStore?,
    page: ThreadPage?,
    tabKey: String,
    title: String,
    selfPostIds: Set<String>,
    listState: LazyListState,
    orderedIds: List<String>,
    itemKeys: Map<String, String>
) {
    val summaryEnabled = stateStore?.isThreadSummaryModeEnabled?.collectAsState(false)?.value ?: false
    val moderationEnabled = stateStore?.isAiPostFilterEnabled?.collectAsState(false)?.value ?: false
    if (!summaryEnabled && !moderationEnabled) {
        LaunchedEffect(holder) {
            holder.hidden = emptySet()
            holder.state = CompatThreadAiState()
        }
        return
    }
    FutaberThreadAiRunner(holder, summaryEnabled, moderationEnabled, page, tabKey, title, selfPostIds, listState, orderedIds, itemKeys)
}

@Composable
private fun FutaberThreadAiRunner(
    holder: FutaberThreadAiHolder,
    summaryEnabled: Boolean,
    moderationEnabled: Boolean,
    page: ThreadPage?,
    tabKey: String,
    title: String,
    selfPostIds: Set<String>,
    listState: LazyListState,
    orderedIds: List<String>,
    itemKeys: Map<String, String>
) {
    // The OpenAI limit (429) notice the other modes show while their moderation waits.
    OpenAiLimitNotice(moderationEnabled)
    val platformContext = LocalPlatformContext.current
    val service = rememberSelectedAiService(platformContext)
    val session = remember(service) { CompatThreadAiSession(service) }
    LaunchedEffect(session, moderationEnabled) { if (!moderationEnabled) session.clearModerationRetries() }
    // An API key retry in the settings resumes moderation paused by that key's failure.
    val keyRetries = remember(platformContext) { getAiConnectionStore(platformContext).keyRetries.drop(1) }
    // The snapshot the shared session reads, built off the main thread (it walks every post).
    val snapshot by produceState<CompatThreadSnapshot?>(null, page, tabKey) {
        value = page?.let { withContext(AppDispatchers.parsing) { it.toCompatThreadSnapshot(tabKey, 0) } }
    }
    var moderationPart by remember(tabKey, service, moderationEnabled) {
        mutableStateOf(CompatThreadAiModerationPart(running = moderationEnabled && page != null))
    }
    var summaryPart by remember(tabKey, service, summaryEnabled) {
        mutableStateOf(CompatThreadAiSummaryPart(running = summaryEnabled && page != null))
    }
    val nearbyIds = rememberModerationViewport(listState, orderedIds, itemKeys, moderationEnabled)
    val sourceKey = remember(snapshot) { snapshot?.let(::buildCompatThreadAiSourceKey) }
    val retry = holder.retry
    LaunchedEffect(sourceKey, title, service, summaryEnabled, moderationEnabled, retry) {
        val source = snapshot ?: return@LaunchedEffect
        coroutineScope {
            if (moderationEnabled) launch {
                session.moderate(source, title, snapshotFlow { nearbyIds.value }, retry, keyRetries) { next ->
                    val previous = moderationPart
                    moderationPart = next.copy(results = if (next.running && next.results.isEmpty()) previous.results else next.results)
                }
            }
            if (summaryEnabled) launch {
                session.summarize(source, title) { next ->
                    val previous = summaryPart
                    summaryPart = next.copy(summary = next.summary ?: previous.summary)
                }
            }
        }
    }
    val moderationPosts = moderationPart.posts
    val copyPasteResults by produceState(emptyList<PostModerationResult>(), moderationPosts, moderationEnabled) {
        value = if (moderationEnabled && moderationPosts.isNotEmpty()) {
            withContext(AppDispatchers.parsing) { detectCopyPastePosts(moderationPosts) }
        } else emptyList()
    }
    val merged = remember(moderationPart, summaryPart, summaryEnabled, moderationEnabled) {
        CompatThreadAiState(
            summaryEnabled = summaryEnabled,
            moderationEnabled = moderationEnabled,
            running = moderationPart.running || summaryPart.running,
            summary = summaryPart.summary,
            summaryError = summaryPart.error,
            moderationError = moderationPart.error,
            posts = moderationPart.posts,
            results = moderationPart.results
        )
    }
    val automaticallyHide = service.automaticallyHideModeratedPosts
    // Working out the hidden posts walks every post; the previous value stays while it runs.
    val resolved by produceState(
        CompatThreadAiState(summaryEnabled, moderationEnabled), merged, copyPasteResults, selfPostIds, automaticallyHide
    ) {
        value = withContext(AppDispatchers.parsing) { merged.resolveVisibility(selfPostIds, automaticallyHide, copyPasteResults) }
    }
    LaunchedEffect(resolved) {
        holder.state = resolved
        holder.hidden = if (resolved.moderationEnabled) resolved.hiddenPostNos else emptySet()
    }
}

/** The summary under the top bar while "スレ要約" is on (the panel the other modes use; it folds, open by default). */
@Composable
internal fun FutaberThreadAiPanel(holder: FutaberThreadAiHolder) {
    val state = holder.state
    if (!state.summaryEnabled) return
    CompatThreadAiPanel(state, onRetry = { holder.retry += 1 })
}

// ---- X10-1: the commands carried out by the thread screen ----

internal fun futaberAiThreadFailedMessage(command: FutachaAiCommand): String =
    "スレッドを読み込めなかったため「${command.action.label}」を実行しませんでした"

/**
 * Carries out the AI command forwarded to this thread ([link]) once its posts are on screen and the reading position
 * is restored ([ready]); when the thread could not be read ([failed]) the command is dropped with a word. The command is
 * taken off before the work starts, and the work runs on the screen's scope, so nothing runs twice and the next command
 * does not cancel a scroll or a read-aloud start.
 */
@Composable
internal fun FutaberThreadAiCommands(
    link: FutaberAiThreadLink?,
    boardId: String,
    threadId: String,
    ready: Boolean,
    failed: Boolean,
    listState: LazyListState,
    reader: FutaberReadAloud,
    rows: List<FutaberRow>,
    query: String,
    searchHit: Int,
    onSearchHit: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenGallery: () -> Boolean,
    onRefresh: () -> Unit,
    onOpenSave: () -> Boolean
) {
    val command = link?.command
    val scope = rememberCoroutineScope()
    val latestRows by rememberUpdatedState(rows)
    val latestQuery by rememberUpdatedState(query)
    val latestHit by rememberUpdatedState(searchHit)
    LaunchedEffect(AiCommandEffectKey(command), ready, failed) {
        val received = command ?: return@LaunchedEffect
        val target = link ?: return@LaunchedEffect
        // Not for this thread: the mode drops it (another screen is now on top).
        if (!isAiCommandForThread(received, boardId, threadId)) return@LaunchedEffect
        if (!ready) {
            if (failed) {
                target.consume(received)
                target.notify(futaberAiThreadFailedMessage(received))
            }
            return@LaunchedEffect
        }
        target.consume(received)
        val message = futaberRunThreadAiCommand(
            received, scope, listState, reader, latestRows, { latestRows }, latestQuery, latestHit,
            onSearchHit, onSearch, onOpenMenu, onOpenGallery, onRefresh, onOpenSave
        )
        message?.let(target.notify)
    }
}

private fun futaberRunThreadAiCommand(
    command: FutachaAiCommand,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: LazyListState,
    reader: FutaberReadAloud,
    rows: List<FutaberRow>,
    currentRows: () -> List<FutaberRow>,
    query: String,
    searchHit: Int,
    onSearchHit: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenGallery: () -> Boolean,
    onRefresh: () -> Unit,
    onOpenSave: () -> Boolean
): String? = when (command.action) {
    FutachaAiAction.RefreshCurrentThread -> { onRefresh(); "スレッドを更新します" }
    FutachaAiAction.ScrollThreadToTop -> {
        scope.launch { if (rows.isNotEmpty()) listState.animateScrollToItem(0) }
        "スレの先頭へ移動しました"
    }
    FutachaAiAction.ScrollThreadToBottom -> {
        scope.launch { if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex) }
        "スレの末尾へ移動しました"
    }
    FutachaAiAction.StartThreadReadAloud -> when {
        reader.isPaused -> { reader.resume(); "読み上げを再開しました" }
        reader.isReading -> "すでに読み上げ中です"
        rows.isEmpty() -> "読み上げるレスがありません"
        else -> {
            // As the menu's "読み上げを開始": from the first post on screen, the posts as they are listed now.
            futaberStartReadAloudFollowing(reader, rows, listState.firstVisibleItemIndex, currentRows) { listState.animateScrollToItem(it) }
            "読み上げを開始しました"
        }
    }
    FutachaAiAction.PauseThreadReadAloud -> when {
        reader.isPaused -> { reader.resume(); "読み上げを再開しました" }
        reader.isReading -> { reader.pause(); "読み上げを一時停止しました" }
        else -> "読み上げていません"
    }
    FutachaAiAction.StopThreadReadAloud -> if (reader.isReading) { reader.stop(); "読み上げを停止しました" } else "読み上げていません"
    FutachaAiAction.NextThreadReadAloud -> if (reader.isReading) { reader.skip(1); "次のレスへ移動しました" } else "読み上げていません"
    FutachaAiAction.PreviousThreadReadAloud -> if (reader.isReading) { reader.skip(-1); "前のレスへ移動しました" } else "読み上げていません"
    // The search box is in the operation menu, as when the person opens it.
    FutachaAiAction.StartThreadSearch -> { onOpenMenu(); "スレ内検索を開きました" }
    FutachaAiAction.SearchThread -> {
        val word = command.searchQueryParameter().orEmpty()
        if (word.isBlank()) { onOpenMenu(); "スレ内検索を開きました" } else { onSearch(word); "スレ内を「$word」で検索しました" }
    }
    FutachaAiAction.NextSearchResult, FutachaAiAction.PreviousSearchResult -> when {
        query.isBlank() -> "検索語がありません。先にスレ内検索をしてください"
        rows.isEmpty() -> "検索結果がありません"
        else -> {
            val next = futaberSearchHitStep(searchHit, rows.size, command.action == FutachaAiAction.NextSearchResult)
            onSearchHit(next)
            scope.launch { listState.animateScrollToItem(next.coerceIn(0, rows.lastIndex)) }
            "検索結果 ${futaberSearchHitLabel(next, rows.size)}"
        }
    }
    FutachaAiAction.OpenGallery -> if (onOpenGallery()) "画像一覧を開きました" else "このスレッドには画像がありません"
    FutachaAiAction.SaveCurrentThread -> if (onOpenSave()) null else "このスレッドは保存できません"
    else -> futaberAiUnsupportedNotice(command)
}

/**
 * Reads the posts of [rowsAtStart] and scrolls to each one where it is in the rows shown at that moment (L112: the AI
 * command kept the rows of when it arrived, so after a search or NG changed the list it scrolled to the wrong row).
 */
internal fun futaberStartReadAloudFollowing(
    reader: FutaberReadAloud,
    rowsAtStart: List<FutaberRow>,
    firstVisibleRow: Int,
    currentRows: () -> List<FutaberRow>,
    scrollTo: suspend (Int) -> Unit
) {
    val readPosts = rowsAtStart.map { it.post }
    reader.start(readPosts, firstVisibleRow) { index ->
        futaberRowPositionOfPost(currentRows(), readPosts.getOrNull(index)?.id)?.let { scrollTo(it) }
    }
}
