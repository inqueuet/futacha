package com.valoser.futacha.shared.ui.futaber

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.searchQueryParameter
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.resolveBoardWatchWordKey
import com.valoser.futacha.shared.ui.AI_SCREEN_COMMAND_EXPIRY_MILLIS
import com.valoser.futacha.shared.ui.AiCommandEffectKey
import com.valoser.futacha.shared.ui.AiCommandHoldDecision
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import com.valoser.futacha.shared.ui.LocalFutachaAppLockHolder
import com.valoser.futacha.shared.ui.LocalFutachaAppUnlocked
import com.valoser.futacha.shared.ui.buildAiCommandHeldByLockMessage
import com.valoser.futacha.shared.ui.buildAiScreenCommandExpiredMessage
import com.valoser.futacha.shared.ui.compat.CompatAiScreenCommandSlot
import com.valoser.futacha.shared.ui.isAiCommandForThread
import com.valoser.futacha.shared.ui.readPersistedAiCommandEnabled
import com.valoser.futacha.shared.ui.resolveAiCommandHoldDecision
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

// X10-1 / X10-2, the screen side: the commands received at the app root (FutaberAiCommands.kt) are handed to the mode's
// screen, which plans them (FutaberAiRouter.kt) and carries them out with the calls its own buttons make. No analytics.

private const val FUTABER_AI_TAG = "FutaberAi"
private const val FUTABER_AI_COMMAND_ID_MAX_BYTES = 128
private const val FUTABER_AI_HANDLED_COMMAND_ID_MAX_COUNT = 128

/**
 * Where the commands received at the app root go: the mode's screen registers [runner] while it is on screen. Made by
 * [FutaberAiCommandSink] and passed down to [FutaberApp].
 */
@Stable
internal class FutaberAiInbox {
    internal var runner: (suspend (FutachaAiCommand) -> Unit)? by mutableStateOf(null)

    /**
     * Waits for the unlock and for the screen ([readyTimeoutMillis] at most), then runs [command] (which reached the
     * app [arrivedAt]; one older than [maxAgeMillis], when its sender gave one, is dropped quietly, and one that waited
     * behind the app lock for too long is dropped with the other modes' message). [onTaken] runs once the command is
     * settled — just before it runs, or when it is dropped — so its sender can hand over the next one while this one is
     * still running (audit M16: iOS waited for the whole command, so a long one held up the next Shortcut).
     */
    internal suspend fun deliver(
        command: FutachaAiCommand,
        arrivedAt: TimeSource.Monotonic.ValueTimeMark,
        maxAgeMillis: Long?,
        appLock: FutachaAppLockHolder,
        readyTimeoutMillis: Long = AI_SCREEN_COMMAND_EXPIRY_MILLIS,
        onTaken: () -> Unit = {},
        onNotice: (String) -> Unit
    ) {
        appLock.awaitUnlocked()
        val waited = withTimeoutOrNull(readyTimeoutMillis) { snapshotFlow { runner }.filterNotNull().first() }
        appLock.awaitUnlocked()
        // The screen registered now: the one that took [waited] may have gone while the lock was up (a mode switch), and
        // running on it lost the command without a word (audit L19). A newer screen takes it instead; none drops it with a word.
        val ready = if (waited == null) null else runner
        val decision = resolveAiCommandHoldDecision(
            ageMillis = arrivedAt.elapsedNow().inWholeMilliseconds,
            maxAgeMillis = maxAgeMillis,
            wasHeldByLock = appLock.wasHeldByLock(arrivedAt)
        )
        onTaken()
        when {
            decision == AiCommandHoldDecision.DropExpired -> Logger.w(FUTABER_AI_TAG, "Dropped expired AI command: ${command.action.id}")
            decision == AiCommandHoldDecision.DropHeldByLock -> onNotice(buildAiCommandHeldByLockMessage(command))
            ready == null -> onNotice(buildAiScreenCommandExpiredMessage(command))
            else -> ready(command)
        }
    }
}

/** What the thread screen receives: the command it may run now (if any), how to take it off, and where to tell the result. */
internal class FutaberAiThreadLink(
    val command: FutachaAiCommand?,
    val consume: (FutachaAiCommand) -> Unit,
    val notify: (String) -> Unit
)

/** The commands forwarded to the thread screen and to the catalog, not yet carried out (one each). */
@Stable
internal class FutaberAiScreens {
    val thread = CompatAiScreenCommandSlot()
    val catalog = CompatAiScreenCommandSlot()
    internal var unlocked by mutableStateOf(true)
    internal var notify: (String) -> Unit = {}

    fun threadLink(): FutaberAiThreadLink = FutaberAiThreadLink(thread.deliverable(unlocked), { thread.consume(it) }) { notify(it) }

    fun catalogCommand(): FutachaAiCommand? = catalog.deliverable(unlocked)
}

@Composable
internal fun rememberFutaberAiScreens(): FutaberAiScreens = remember { FutaberAiScreens() }

/**
 * What the screen offers the commands; each member is a call the mode's own buttons already make (so a command does
 * what the person could do by hand, the way it is done there).
 */
internal class FutaberAiAppActions(
    val setDrawerOpen: (Boolean) -> Unit,
    /** Closes the settings and the history panel, so the screen a command opens is the one on top. */
    val closeOverlays: () -> Unit,
    val openBoard: (BoardSummary) -> Unit,
    val refreshCatalog: () -> Unit,
    val openManage: () -> Unit,
    val refreshHistory: (suspend () -> Unit)?,
    val openThread: (BoardSummary, FutaberThreadRef) -> Unit,
    val addBoardAndOpen: (FutaberBoardAddRequest) -> Unit,
    val openSettings: () -> Unit,
    val openCookies: (() -> Unit)?,
    val openUrl: (String) -> Unit,
    /** Deletes the row and returns once it is gone (throws if it could not be), so "削除しました" follows only success (audit L18). */
    val deleteHistory: suspend (ThreadHistoryEntry) -> Unit,
    val clearHistory: () -> Unit,
    /** Changes the boards and returns once they are stored (throws if they could not be; audit L18). */
    val editBoards: suspend ((List<BoardSummary>) -> List<BoardSummary>) -> Unit,
    val openDraft: (target: FutaberPostTarget, subject: String, comment: String) -> Unit,
    /** True while a post to another target is being sent: the draft is not opened then (re-audit M13). */
    val draftRefused: (FutaberPostTarget) -> Boolean = { false },
    val savedRepository: SavedThreadRepository?,
    val onSavedChanged: () -> Unit
)

/**
 * Registers the mode's runner for the received commands, keeps the forwarded ones until the screen takes them (and
 * drops them when they wait too long, or wait behind the lock, as the other modes do), and shows the confirmation of
 * the commands that need one (X10-2). Draws nothing else.
 */
@Composable
internal fun FutaberAiCommandHost(
    screens: FutaberAiScreens,
    inbox: FutaberAiInbox?,
    stateStore: AppStateStore,
    context: FutaberAiContext,
    actions: FutaberAiAppActions,
    notify: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val latestContext by rememberUpdatedState(context)
    val latestActions by rememberUpdatedState(actions)
    val latestNotify by rememberUpdatedState(notify)
    val isAppUnlocked = LocalFutachaAppUnlocked.current
    val appLock = LocalFutachaAppLockHolder.current ?: remember {
        FutachaAppLockHolder().apply { openSessionWithoutLock(); setContentVisible(true) }
    }
    SideEffect {
        screens.unlocked = isAppUnlocked
        screens.notify = { latestNotify(it) }
    }
    var confirmation by remember { mutableStateOf<FutaberAiPlan.Confirm?>(null) }
    val handledIds = remember { LinkedHashSet<String>() }
    var refreshingHistory by remember { mutableStateOf(false) }

    suspend fun carryOut(plan: FutaberAiPlan.Run) {
        val a = latestActions
        val ctx = latestContext
        fun isCurrent(board: BoardSummary, ref: FutaberThreadRef) =
            ctx.currentBoard?.id == board.id && ctx.currentThread?.threadId == ref.threadId
        var message = plan.message
        when (val effect = plan.effect) {
            FutaberAiEffect.OpenDrawer -> { a.closeOverlays(); a.setDrawerOpen(true) }
            is FutaberAiEffect.OpenBoard -> { a.closeOverlays(); a.setDrawerOpen(false); a.openBoard(effect.board) }
            is FutaberAiEffect.RefreshCatalog -> {
                a.closeOverlays(); a.setDrawerOpen(false)
                a.openBoard(effect.board)
                a.refreshCatalog()
            }
            is FutaberAiEffect.CatalogCommand -> {
                a.closeOverlays(); a.setDrawerOpen(false)
                if (ctx.currentBoard?.id != effect.board.id || ctx.currentThread != null) a.openBoard(effect.board)
                screens.catalog.forward(effect.command)
            }
            FutaberAiEffect.OpenManage -> { a.setDrawerOpen(false); a.closeOverlays(); a.openManage() }
            FutaberAiEffect.RefreshHistory -> {
                val refresh = a.refreshHistory
                if (refresh == null) message = "履歴更新サービスを利用できません"
                else if (refreshingHistory) message = "履歴を更新中です"
                else {
                    // Outside the command's coroutine: the next command must not cancel a long refresh.
                    refreshingHistory = true
                    scope.launch {
                        try {
                            refresh()
                            latestNotify("履歴を更新しました")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Logger.e(FUTABER_AI_TAG, "AI history refresh failed", error)
                            latestNotify("履歴を更新できませんでした: ${error.message.orEmpty()}")
                        } finally {
                            refreshingHistory = false
                        }
                    }
                }
            }
            is FutaberAiEffect.OpenThread -> {
                a.closeOverlays(); a.setDrawerOpen(false)
                if (!isCurrent(effect.board, effect.ref)) a.openThread(effect.board, effect.ref)
            }
            is FutaberAiEffect.AddBoardAndOpen -> { a.closeOverlays(); a.setDrawerOpen(false); a.addBoardAndOpen(effect.request) }
            is FutaberAiEffect.ThreadCommand -> {
                a.closeOverlays(); a.setDrawerOpen(false)
                if (!isCurrent(effect.board, effect.ref)) a.openThread(effect.board, effect.ref)
                // In the same step as the opening, so the screen of the new thread is the one that sees it.
                screens.thread.forward(effect.command)
            }
            FutaberAiEffect.OpenSettings -> { a.setDrawerOpen(false); a.openSettings() }
            FutaberAiEffect.OpenCookies -> a.openCookies?.invoke() ?: run { message = "Cookie管理を利用できません" }
            is FutaberAiEffect.OpenUrl -> a.openUrl(effect.url)
            is FutaberAiEffect.SetFlag -> {
                when (effect.flag) {
                    FutaberAiFlag.Privacy -> stateStore.setPrivacyFilterEnabled(effect.enabled)
                    FutaberAiFlag.BackgroundRefresh -> stateStore.setBackgroundRefreshEnabled(effect.enabled)
                    FutaberAiFlag.ThreadSummary -> stateStore.setThreadSummaryModeEnabled(effect.enabled)
                    FutaberAiFlag.AiPostFilter -> stateStore.setAiPostFilterEnabled(effect.enabled)
                }
                message = "${effect.flag.label}を${if (effect.enabled) "ON" else "OFF"}にしました"
            }
            is FutaberAiEffect.AddNgWord -> stateStore.updateNgWords { it.futaberAppendDistinct(effect.word) }
            is FutaberAiEffect.AddNgHeader -> stateStore.updateNgHeaders { it.futaberAppendDistinct(effect.header) }
            is FutaberAiEffect.AddWatchWord -> {
                stateStore.updateWatchWords { it.futaberAppendDistinct(effect.word) }
                // A board with its own watch words gets it too, as ふたちゃ's command does.
                effect.board?.let { target ->
                    val key = resolveBoardWatchWordKey(target)
                    val perBoard = stateStore.boardWatchWords.first()
                    if (perBoard.containsKey(key)) stateStore.setBoardWatchWords(key, perBoard[key].orEmpty().futaberAppendDistinct(effect.word))
                }
            }
            is FutaberAiEffect.DeleteHistory -> a.deleteHistory(effect.entry)
            FutaberAiEffect.ClearHistory -> a.clearHistory()
            is FutaberAiEffect.DeleteSaved -> {
                val repository = a.savedRepository
                if (repository == null) message = "保存済みスレの保存先を利用できません"
                else {
                    val matches = repository.getAllThreads().filter { saved ->
                        saved.threadId == effect.threadId && (effect.boardKeys == null || effect.boardKeys.any { saved.boardId.equals(it, ignoreCase = true) })
                    }
                    message = when (matches.size) {
                        0 -> "削除する保存済みスレを特定できませんでした"
                        1 -> repository.deleteThread(matches.single().threadId, matches.single().boardId).fold(
                            onSuccess = { "保存済みスレ ${effect.threadId} を削除しました" },
                            onFailure = { "保存済みスレを削除できませんでした: ${it.message.orEmpty()}" }
                        )
                        else -> "同じ条件の保存済みスレが複数あります。板も指定してください"
                    }
                    a.onSavedChanged()
                }
            }
            FutaberAiEffect.ClearSaved -> {
                val repository = a.savedRepository
                message = if (repository == null) "保存済みスレの保存先を利用できません"
                else repository.deleteAllThreads().fold(
                    onSuccess = { "保存済みスレを全削除しました" },
                    onFailure = { "保存済みスレを全削除できませんでした: ${it.message.orEmpty()}" }
                )
                a.onSavedChanged()
            }
            is FutaberAiEffect.AddBoard -> a.editBoards { futaberAddBoard(it, effect.name, effect.url) }
            is FutaberAiEffect.DeleteBoard -> a.editBoards { futaberDeleteBoard(it, effect.board.id) }
            // While another post is being sent, nothing moves and nothing is written into the write screen (re-audit M13:
            // the subject went in although the screen was not switched, and "開きました" covered the refusal).
            is FutaberAiEffect.Draft -> if (a.draftRefused(effect.target)) {
                message = FUTABER_POST_IN_FLIGHT_NOTICE
            } else {
                a.closeOverlays(); a.setDrawerOpen(false)
                effect.ref?.let { ref -> if (!isCurrent(effect.board, ref)) a.openThread(effect.board, ref) }
                a.openDraft(effect.target, effect.subject, effect.comment)
            }
        }
        message?.let(latestNotify)
    }

    suspend fun runCommand(command: FutachaAiCommand, confirmed: Boolean) {
        val enabled = readPersistedAiCommandEnabled(stateStore, fallback = false)
        when (val plan = futaberAiPlan(command, latestContext, enabled, confirmed)) {
            is FutaberAiPlan.Fail -> latestNotify(plan.message)
            is FutaberAiPlan.Confirm -> {
                // A confirmation already on screen is kept (the other modes do the same): a later one would change the
                // question under the person's finger.
                if (confirmation == null) confirmation = plan
                else latestNotify("確認待ちのAI操作があるため「${command.action.label}」は実行しませんでした")
            }
            is FutaberAiPlan.Run -> try {
                carryOut(plan)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_AI_TAG, "AI command failed: ${command.action.id}", error)
                latestNotify(error.message?.takeIf { it.isNotBlank() }?.let { "AI操作に失敗しました: $it" } ?: "AI操作に失敗しました")
            }
        }
    }

    if (inbox != null) {
        LaunchedEffect(inbox) {
            val runner: suspend (FutachaAiCommand) -> Unit = { command ->
                if (!futaberIsDuplicateAiCommand(handledIds, command)) runCommand(command, confirmed = false)
            }
            inbox.runner = runner
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                if (inbox.runner === runner) inbox.runner = null
            }
        }
    }

    // The forwarded commands age from the forwarding (also while stopped or locked), as in the other modes.
    LaunchedEffect(AiCommandEffectKey(screens.thread.pending), isAppUnlocked) {
        screens.thread.supervise(appLock)?.let(latestNotify)
    }
    LaunchedEffect(AiCommandEffectKey(screens.catalog.pending), isAppUnlocked) {
        screens.catalog.supervise(appLock)?.let(latestNotify)
    }
    // A command for a thread (or a catalog) that is no longer on screen is dropped, never run on another one.
    val boardId = context.currentBoard?.id
    val threadId = context.currentThread?.threadId
    LaunchedEffect(boardId, threadId, AiCommandEffectKey(screens.thread.pending)) {
        val pending = screens.thread.pending ?: return@LaunchedEffect
        if (boardId == null || threadId == null || !isAiCommandForThread(pending, boardId, threadId)) screens.thread.clear()
    }
    LaunchedEffect(boardId, threadId, AiCommandEffectKey(screens.catalog.pending)) {
        val pending = screens.catalog.pending ?: return@LaunchedEffect
        if (boardId == null || threadId != null || !futaberAiCommandIsForBoard(pending, boardId)) screens.catalog.clear()
    }

    confirmation?.takeIf { isAppUnlocked }?.let { request ->
        val colors = LocalFutaberColors.current
        fun dismiss() {
            confirmation = null
            latestNotify("AI操作をキャンセルしました")
        }
        FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = ::dismiss,
                containerColor = colors.bar,
                titleContentColor = colors.body,
                textContentColor = colors.body,
                title = { Text(request.title) },
                text = { Text(request.message, modifier = Modifier.testTag("futaber-ai-confirm-message")) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (!appLock.isUnlocked) return@TextButton
                            confirmation = null
                            scope.launch { runCommand(request.command, confirmed = true) }
                        },
                        modifier = Modifier.testTag("futaber-ai-confirm")
                    ) { Text(request.confirmLabel, color = colors.link) }
                },
                dismissButton = {
                    TextButton(onClick = ::dismiss, modifier = Modifier.testTag("futaber-ai-cancel")) {
                        Text("キャンセル", color = colors.link)
                    }
                }
            )
        }
    }
}

/** Whether a catalog command was resolved for [boardId] (commands without the mark are not the screen's). */
internal fun futaberAiCommandIsForBoard(command: FutachaAiCommand, boardId: String): Boolean =
    command.parameter("boardId", "board_id")?.equals(boardId, ignoreCase = true) == true

/** The same id that came before (a Shortcut delivered twice) is not run again; ids are remembered up to a bound. */
internal fun futaberIsDuplicateAiCommand(handled: LinkedHashSet<String>, command: FutachaAiCommand): Boolean {
    val id = command.parameters["commandId"]
        ?.takeIf { it.isNotBlank() && it.encodeToByteArray().size <= FUTABER_AI_COMMAND_ID_MAX_BYTES }
        ?: return false
    if (!handled.add(id)) return true
    while (handled.size > FUTABER_AI_HANDLED_COMMAND_ID_MAX_COUNT) handled.remove(handled.first())
    return false
}

/**
 * Carries out the catalog commands (scroll to the top, the search bar) once the catalog of their board is on screen.
 * [shown]: the catalog is what is shown (not a thread, not a saved copy).
 */
@Composable
internal fun FutaberAiCatalogReceiver(
    screens: FutaberAiScreens,
    board: BoardSummary?,
    view: FutaberCatalogViewState,
    search: FutaberCatalogSearchState,
    shown: Boolean
) {
    val command = screens.catalogCommand()
    LaunchedEffect(AiCommandEffectKey(command), shown, board?.id) {
        val received = command ?: return@LaunchedEffect
        val target = board ?: return@LaunchedEffect
        if (!shown || !futaberAiCommandIsForBoard(received, target.id)) return@LaunchedEffect
        screens.catalog.consume(received)
        val message = when (received.action) {
            FutachaAiAction.ScrollCatalogToTop -> { view.scrollToTopTick += 1; "カタログの先頭へ移動しました" }
            FutachaAiAction.StartCatalogSearch -> { search.open = true; "カタログ検索を開きました" }
            FutachaAiAction.SearchCatalog -> {
                val query = received.searchQueryParameter().orEmpty()
                search.open = true
                search.query = query
                if (query.isBlank()) "カタログ検索を開きました" else "カタログを「$query」で検索しました"
            }
            else -> null
        }
        message?.let(screens.notify)
    }
}
