package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.boardSelectorParameter
import com.valoser.futacha.shared.ai.boardUrlParameter
import com.valoser.futacha.shared.ai.buildFutachaAiConfirmationMessage
import com.valoser.futacha.shared.ai.confirmationDetailLines
import com.valoser.futacha.shared.ai.draftCommentParameter
import com.valoser.futacha.shared.ai.draftSubjectParameter
import com.valoser.futacha.shared.ai.requiresConfirmation
import com.valoser.futacha.shared.ai.searchQueryParameter
import com.valoser.futacha.shared.ai.threadIdParameter
import com.valoser.futacha.shared.ai.threadUrlParameter
import com.valoser.futacha.shared.ai.titleParameter
import com.valoser.futacha.shared.ai.toConfirmationValue
import com.valoser.futacha.shared.ai.wordParameter
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.AI_UNTRUSTED_THREAD_URL_MESSAGE
import com.valoser.futacha.shared.ui.board.ALPHA_AI_POST_FILTER_ENABLED
import com.valoser.futacha.shared.ui.compat.COMPAT_AI_COMMANDS_DISABLED_MESSAGE
import com.valoser.futacha.shared.ui.isAiThreadUrlAllowed
import com.valoser.futacha.shared.ui.isAllowedWhenAiCommandsDisabled

// X10-1 / X10-2: the AI commands (Siri / Shortcuts / `futacha://ai` links) this mode runs, worked out as plain values so
// the screen only carries them out. Everything here is pure; the screen side is FutaberAiHost.kt.

/** What a command needs to know about the screen at the moment it runs. */
internal data class FutaberAiContext(
    val boards: List<BoardSummary>,
    val history: List<ThreadHistoryEntry>,
    /** The board whose catalog (or thread) is on screen; null with no board. */
    val currentBoard: BoardSummary?,
    /** The thread on screen (a live thread or a saved copy); null on the catalog. */
    val currentThread: FutaberThreadRef?,
    val historyRefreshAvailable: Boolean = false,
    val cookiesAvailable: Boolean = false,
    val savedThreadsAvailable: Boolean = false,
    val appVersion: String = ""
)

/** The shared switches a command may turn on or off (the same stored values every mode reads). */
internal enum class FutaberAiFlag(val label: String) {
    Privacy("プライバシーフィルタ"),
    BackgroundRefresh("バックグラウンド更新"),
    ThreadSummary("スレ要約モード"),
    AiPostFilter("荒らし非表示")
}

/** What the screen does for a command. */
internal sealed interface FutaberAiEffect {
    data object OpenDrawer : FutaberAiEffect
    data class OpenBoard(val board: BoardSummary) : FutaberAiEffect
    data class RefreshCatalog(val board: BoardSummary) : FutaberAiEffect
    /** Carried out by the catalog of [board] (scroll to the top, the search bar). */
    data class CatalogCommand(val board: BoardSummary, val command: FutachaAiCommand) : FutaberAiEffect
    data object OpenManage : FutaberAiEffect
    data object RefreshHistory : FutaberAiEffect
    data class OpenThread(val board: BoardSummary, val ref: FutaberThreadRef) : FutaberAiEffect
    /** A board that is not registered: the usual "追加して開きますか？" question comes first. */
    data class AddBoardAndOpen(val request: FutaberBoardAddRequest) : FutaberAiEffect
    /** Carried out by the screen of the thread [ref] of [board] (opened first when another one is on screen). */
    data class ThreadCommand(val board: BoardSummary, val ref: FutaberThreadRef, val command: FutachaAiCommand) : FutaberAiEffect
    data object OpenSettings : FutaberAiEffect
    data object OpenCookies : FutaberAiEffect
    data class OpenUrl(val url: String) : FutaberAiEffect
    data class SetFlag(val flag: FutaberAiFlag, val enabled: Boolean) : FutaberAiEffect
    data class AddNgWord(val word: String) : FutaberAiEffect
    data class AddNgHeader(val header: String) : FutaberAiEffect
    data class AddWatchWord(val word: String, val board: BoardSummary?) : FutaberAiEffect
    data class DeleteHistory(val entry: ThreadHistoryEntry) : FutaberAiEffect
    data object ClearHistory : FutaberAiEffect
    /** Deletes the one saved thread (保存箱) matching [threadId] (and one of [boardKeys], when given). */
    data class DeleteSaved(val threadId: String?, val boardKeys: Set<String>?) : FutaberAiEffect
    data object ClearSaved : FutaberAiEffect
    data class AddBoard(val name: String, val url: String) : FutaberAiEffect
    data class DeleteBoard(val board: BoardSummary) : FutaberAiEffect
    /** Opens the write screen for [target] with [comment] (and [subject] for a new thread); [ref] is opened first. */
    data class Draft(val target: FutaberPostTarget, val ref: FutaberThreadRef?, val board: BoardSummary, val subject: String, val comment: String) : FutaberAiEffect
}

internal sealed interface FutaberAiPlan {
    /** Carry out [effect]; [message] is told to the person right away (null: the effect tells its own result). */
    data class Run(val effect: FutaberAiEffect, val message: String?) : FutaberAiPlan
    /** Ask first (X10-2), with the wording ふたちゃ uses; OK plans the command again as confirmed. */
    data class Confirm(val command: FutachaAiCommand, val title: String, val message: String, val confirmLabel: String = "続行") : FutaberAiPlan
    /** Not run: the reason is told and the command is consumed. */
    data class Fail(val message: String) : FutaberAiPlan
}

/** The same wording as the other modes' gate; this mode's switch is on the "AI・補助機能" page too. */
internal const val FUTABER_AI_COMMANDS_DISABLED_MESSAGE = COMPAT_AI_COMMANDS_DISABLED_MESSAGE

internal const val FUTABER_AI_OPEN_THREAD_FIRST = "先に対象スレを開いてください"
internal const val FUTABER_AI_THREAD_NOT_FOUND = "対象スレを特定できませんでした"

/** The commands this mode runs (X10-1, X10-2); every other one is answered as not supported here. */
internal val FUTABER_AI_SUPPORTED_ACTIONS: Set<FutachaAiAction> = setOf(
    FutachaAiAction.OpenBoardList,
    FutachaAiAction.OpenBoard,
    FutachaAiAction.SearchAndOpenBoard,
    FutachaAiAction.RefreshCurrentBoard,
    FutachaAiAction.RefreshCatalog,
    FutachaAiAction.OpenHistoryDrawer,
    FutachaAiAction.RefreshHistory,
    FutachaAiAction.OpenThread,
    FutachaAiAction.OpenThreadFromUrl,
    FutachaAiAction.RefreshCurrentThread,
    FutachaAiAction.ScrollThreadToTop,
    FutachaAiAction.ScrollThreadToBottom,
    FutachaAiAction.StartThreadReadAloud,
    FutachaAiAction.PauseThreadReadAloud,
    FutachaAiAction.StopThreadReadAloud,
    FutachaAiAction.NextThreadReadAloud,
    FutachaAiAction.PreviousThreadReadAloud,
    FutachaAiAction.ScrollCatalogToTop,
    FutachaAiAction.StartCatalogSearch,
    FutachaAiAction.SearchCatalog,
    FutachaAiAction.StartThreadSearch,
    FutachaAiAction.SearchThread,
    FutachaAiAction.NextSearchResult,
    FutachaAiAction.PreviousSearchResult,
    FutachaAiAction.OpenGallery,
    FutachaAiAction.OpenGlobalSettings,
    FutachaAiAction.OpenCatalogSettings,
    FutachaAiAction.OpenThreadSettings,
    FutachaAiAction.OpenCatalogDisplaySettings,
    FutachaAiAction.OpenVersionInfo,
    FutachaAiAction.OpenFileManagerSettings,
    FutachaAiAction.OpenCookieManagement,
    FutachaAiAction.EnablePrivacyFilter,
    FutachaAiAction.DisablePrivacyFilter,
    FutachaAiAction.EnableBackgroundRefresh,
    FutachaAiAction.DisableBackgroundRefresh,
    FutachaAiAction.EnableThreadSummaryMode,
    FutachaAiAction.DisableThreadSummaryMode,
    FutachaAiAction.EnableAiPostFilter,
    FutachaAiAction.DisableAiPostFilter,
    FutachaAiAction.AddWatchWord,
    FutachaAiAction.AddNgWord,
    FutachaAiAction.AddNgHeader,
    FutachaAiAction.OpenBoardExternally,
    FutachaAiAction.OpenThreadExternally,
    // X10-2: asked first.
    FutachaAiAction.SaveCurrentThread,
    FutachaAiAction.SaveThread,
    FutachaAiAction.DeleteHistoryEntry,
    FutachaAiAction.ClearHistory,
    FutachaAiAction.DeleteSavedThread,
    FutachaAiAction.ClearSavedThreads,
    FutachaAiAction.AddBoard,
    FutachaAiAction.DeleteBoard,
    FutachaAiAction.DraftReply,
    FutachaAiAction.DraftThread
)

/** The commands the thread screen carries out (after the thread is open and its posts are on screen). */
internal val FUTABER_AI_THREAD_SCREEN_ACTIONS: Set<FutachaAiAction> = setOf(
    FutachaAiAction.RefreshCurrentThread,
    FutachaAiAction.ScrollThreadToTop,
    FutachaAiAction.ScrollThreadToBottom,
    FutachaAiAction.StartThreadReadAloud,
    FutachaAiAction.PauseThreadReadAloud,
    FutachaAiAction.StopThreadReadAloud,
    FutachaAiAction.NextThreadReadAloud,
    FutachaAiAction.PreviousThreadReadAloud,
    FutachaAiAction.StartThreadSearch,
    FutachaAiAction.SearchThread,
    FutachaAiAction.NextSearchResult,
    FutachaAiAction.PreviousSearchResult,
    FutachaAiAction.OpenGallery,
    FutachaAiAction.SaveCurrentThread
)

/**
 * The plan for [command]. [enabled] is the shared "AIアプリ操作" setting read now; [confirmed] is true once the person
 * said OK to the confirmation of the same command.
 */
internal fun futaberAiPlan(
    command: FutachaAiCommand,
    context: FutaberAiContext,
    enabled: Boolean,
    confirmed: Boolean = false
): FutaberAiPlan {
    if (!enabled && !command.isAllowedWhenAiCommandsDisabled()) return FutaberAiPlan.Fail(FUTABER_AI_COMMANDS_DISABLED_MESSAGE)
    if (command.action !in FUTABER_AI_SUPPORTED_ACTIONS) return FutaberAiPlan.Fail(futaberAiUnsupportedNotice(command))
    val plan = futaberAiResolve(command, context)
    if (plan !is FutaberAiPlan.Run || confirmed || !command.requiresConfirmation()) return plan
    return FutaberAiPlan.Confirm(
        command = command,
        title = "AI操作の確認",
        message = buildFutachaAiConfirmationMessage(command, futaberAiConfirmationDetails(command, plan.effect))
    )
}

/** The values the confirmation shows, resolved the way the confirmed run resolves them. */
private fun futaberAiConfirmationDetails(command: FutachaAiCommand, effect: FutaberAiEffect): List<String> = when (effect) {
    is FutaberAiEffect.AddBoard -> listOf("板名: ${effect.name.toConfirmationValue()}", "URL: ${effect.url.toConfirmationValue()}")
    is FutaberAiEffect.DeleteBoard -> listOf("板: ${effect.board.name.toConfirmationValue()}")
    is FutaberAiEffect.DeleteHistory -> listOf(
        "スレ: ${effect.entry.threadId.toConfirmationValue()}",
        "タイトル: ${effect.entry.title.ifBlank { "(無題)" }.toConfirmationValue()}"
    )
    is FutaberAiEffect.ThreadCommand -> listOf(
        "スレ: ${effect.ref.threadId.toConfirmationValue()}",
        "タイトル: ${effect.ref.title.ifBlank { "(無題)" }.toConfirmationValue()}"
    )
    is FutaberAiEffect.Draft -> listOfNotNull(
        "板: ${effect.board.name.toConfirmationValue()}",
        effect.ref?.let { "スレ: ${it.threadId.toConfirmationValue()}" },
        effect.subject.takeIf { it.isNotBlank() }?.let { "題名: ${it.toConfirmationValue()}" },
        effect.comment.takeIf { it.isNotBlank() }?.let { "本文: ${it.toConfirmationValue()}" }
    )
    else -> command.confirmationDetailLines()
}

private fun futaberAiResolve(command: FutachaAiCommand, context: FutaberAiContext): FutaberAiPlan {
    val action = command.action
    fun run(effect: FutaberAiEffect, message: String? = null) = FutaberAiPlan.Run(effect, message)
    fun missingBoard() = FutaberAiPlan.Fail("対象板を特定できませんでした: ${action.label}")
    return when (action) {
        FutachaAiAction.OpenBoardList -> run(FutaberAiEffect.OpenDrawer, "板一覧を開きました")
        FutachaAiAction.OpenBoard, FutachaAiAction.SearchAndOpenBoard -> {
            val board = futaberAiBoard(command.boardSelectorParameter(), context)
            when {
                board != null -> run(FutaberAiEffect.OpenBoard(board), "${board.name} を開きました")
                else -> command.boardUrlParameter()?.let { futaberBoardAddRequest(it) }
                    ?.let { run(FutaberAiEffect.AddBoardAndOpen(it)) }
                    ?: missingBoard()
            }
        }
        FutachaAiAction.RefreshCurrentBoard, FutachaAiAction.RefreshCatalog -> {
            val board = futaberAiNamedBoard(command, context) ?: context.currentBoard ?: return missingBoard()
            run(FutaberAiEffect.RefreshCatalog(board), "${board.name} のカタログを更新します")
        }
        FutachaAiAction.ScrollCatalogToTop, FutachaAiAction.StartCatalogSearch, FutachaAiAction.SearchCatalog -> {
            val board = futaberAiNamedBoard(command, context) ?: context.currentBoard ?: return missingBoard()
            run(FutaberAiEffect.CatalogCommand(board, command.withFutaberTarget(board.id, null)))
        }
        FutachaAiAction.OpenBoardExternally -> {
            val board = futaberAiNamedBoard(command, context) ?: context.currentBoard ?: return missingBoard()
            run(FutaberAiEffect.OpenUrl(board.url), "${board.name} をブラウザで開きます")
        }
        FutachaAiAction.OpenHistoryDrawer -> run(FutaberAiEffect.OpenManage, "履歴を開きました")
        FutachaAiAction.RefreshHistory ->
            if (context.historyRefreshAvailable) run(FutaberAiEffect.RefreshHistory)
            else FutaberAiPlan.Fail("履歴更新サービスを利用できません")
        FutachaAiAction.OpenThread, FutachaAiAction.OpenThreadFromUrl -> when (val target = futaberAiThreadTarget(command, context, allowAdd = true)) {
            is FutaberAiThreadTarget.Found -> run(FutaberAiEffect.OpenThread(target.board, target.ref), "スレ ${target.ref.threadId} を開きました")
            is FutaberAiThreadTarget.AddBoard -> run(FutaberAiEffect.AddBoardAndOpen(target.request))
            is FutaberAiThreadTarget.Rejected -> FutaberAiPlan.Fail(target.message)
            FutaberAiThreadTarget.None, FutaberAiThreadTarget.Missing -> FutaberAiPlan.Fail(FUTABER_AI_THREAD_NOT_FOUND)
        }
        in FUTABER_AI_THREAD_SCREEN_ACTIONS, FutachaAiAction.SaveThread -> {
            val (board, ref) = futaberAiThreadOrCurrent(command, context).getOrElse { return FutaberAiPlan.Fail(it.message.orEmpty()) }
            // "指定スレを保存" is the same screen operation as "現在のスレを保存" once that thread is open.
            val screenAction = if (action == FutachaAiAction.SaveThread) FutachaAiAction.SaveCurrentThread else action
            run(FutaberAiEffect.ThreadCommand(board, ref, command.copy(action = screenAction).withFutaberTarget(board.id, ref.threadId)))
        }
        FutachaAiAction.OpenThreadExternally -> {
            val (board, ref) = futaberAiThreadOrCurrent(command, context).getOrElse { return FutaberAiPlan.Fail(it.message.orEmpty()) }
            run(FutaberAiEffect.OpenUrl(futaberHistoryThreadUrl(board, ref.threadId)), "スレ ${ref.threadId} をブラウザで開きます")
        }
        FutachaAiAction.OpenGlobalSettings,
        FutachaAiAction.OpenCatalogSettings,
        FutachaAiAction.OpenThreadSettings,
        FutachaAiAction.OpenCatalogDisplaySettings,
        FutachaAiAction.OpenFileManagerSettings -> run(FutaberAiEffect.OpenSettings, "設定画面を開きました")
        FutachaAiAction.OpenVersionInfo -> run(FutaberAiEffect.OpenSettings, "現在のバージョンは ${context.appVersion} です")
        FutachaAiAction.OpenCookieManagement ->
            if (context.cookiesAvailable) run(FutaberAiEffect.OpenCookies, "Cookie管理画面を開きました")
            else FutaberAiPlan.Fail("Cookie管理を利用できません")
        FutachaAiAction.EnablePrivacyFilter -> run(FutaberAiEffect.SetFlag(FutaberAiFlag.Privacy, true))
        FutachaAiAction.DisablePrivacyFilter -> run(FutaberAiEffect.SetFlag(FutaberAiFlag.Privacy, false))
        FutachaAiAction.EnableBackgroundRefresh -> run(FutaberAiEffect.SetFlag(FutaberAiFlag.BackgroundRefresh, true))
        FutachaAiAction.DisableBackgroundRefresh -> run(FutaberAiEffect.SetFlag(FutaberAiFlag.BackgroundRefresh, false))
        FutachaAiAction.EnableThreadSummaryMode -> run(FutaberAiEffect.SetFlag(FutaberAiFlag.ThreadSummary, true))
        FutachaAiAction.DisableThreadSummaryMode -> run(FutaberAiEffect.SetFlag(FutaberAiFlag.ThreadSummary, false))
        FutachaAiAction.EnableAiPostFilter ->
            if (ALPHA_AI_POST_FILTER_ENABLED) run(FutaberAiEffect.SetFlag(FutaberAiFlag.AiPostFilter, true))
            else FutaberAiPlan.Fail("荒らし非表示（アルファ版）は現在有効化できません")
        FutachaAiAction.DisableAiPostFilter -> run(FutaberAiEffect.SetFlag(FutaberAiFlag.AiPostFilter, false))
        FutachaAiAction.AddWatchWord -> {
            val word = command.wordParameter()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return FutaberAiPlan.Fail("追加する監視ワードがありません")
            run(FutaberAiEffect.AddWatchWord(word, futaberAiNamedBoard(command, context) ?: context.currentBoard), "監視ワードに「$word」を追加しました")
        }
        FutachaAiAction.AddNgWord -> {
            val word = command.wordParameter()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return FutaberAiPlan.Fail("追加するNGワードがありません")
            run(FutaberAiEffect.AddNgWord(word), "NGワードに「$word」を追加しました")
        }
        FutachaAiAction.AddNgHeader -> {
            val header = (command.parameter("header") ?: command.wordParameter())?.trim()?.takeIf { it.isNotEmpty() }
                ?: return FutaberAiPlan.Fail("追加するNGヘッダーがありません")
            run(FutaberAiEffect.AddNgHeader(header), "NGヘッダーに「$header」を追加しました")
        }
        FutachaAiAction.DeleteHistoryEntry -> when (val found = futaberAiHistoryEntry(command, context)) {
            is FutaberAiHistoryMatch.Found -> run(FutaberAiEffect.DeleteHistory(found.entry), "履歴から ${found.entry.threadId} を削除しました")
            FutaberAiHistoryMatch.Ambiguous -> FutaberAiPlan.Fail("同じ条件の履歴が複数あります。板も指定してください")
            FutaberAiHistoryMatch.Missing -> FutaberAiPlan.Fail("削除する履歴を特定できませんでした")
        }
        // The history band's "全件削除" tells its own result.
        FutachaAiAction.ClearHistory -> run(FutaberAiEffect.ClearHistory)
        FutachaAiAction.DeleteSavedThread -> {
            if (!context.savedThreadsAvailable) return FutaberAiPlan.Fail("保存済みスレの保存先を利用できません")
            val threadId = futaberAiExplicitThreadId(command) ?: context.currentThread?.threadId
                ?: return FutaberAiPlan.Fail("削除する保存済みスレを特定できませんでした")
            val board = futaberAiNamedBoard(command, context)
                ?: context.currentBoard?.takeIf { futaberAiExplicitThreadId(command) == null }
            run(FutaberAiEffect.DeleteSaved(threadId, board?.let { futaberAiSavedBoardKeys(it) }))
        }
        FutachaAiAction.ClearSavedThreads ->
            if (context.savedThreadsAvailable) run(FutaberAiEffect.ClearSaved)
            else FutaberAiPlan.Fail("保存済みスレの保存先を利用できません")
        FutachaAiAction.AddBoard -> {
            val url = command.boardUrlParameter() ?: return FutaberAiPlan.Fail("追加する板URLがありません")
            val name = command.parameter("name", "board", "title", "label").orEmpty()
            val state = futaberAddBoardState(name, url, context.boards)
            if (!state.canSubmit) return FutaberAiPlan.Fail(state.helperText ?: "板名と有効なURLを指定してください")
            run(FutaberAiEffect.AddBoard(state.resolvedName, url), "板「${state.resolvedName}」を追加しました")
        }
        FutachaAiAction.DeleteBoard -> {
            val board = futaberAiBoard(command.boardSelectorParameter(), context) ?: return missingBoard()
            run(FutaberAiEffect.DeleteBoard(board), "板「${board.name}」を削除しました")
        }
        FutachaAiAction.DraftReply -> {
            val (board, ref) = futaberAiThreadOrCurrent(command, context).getOrElse { return FutaberAiPlan.Fail(it.message.orEmpty()) }
            run(
                FutaberAiEffect.Draft(
                    FutaberPostTarget.Reply(board.id, ref.threadId, ref.title), ref, board, "",
                    command.draftCommentParameter().orEmpty()
                ),
                "返信の下書きを開きました"
            )
        }
        FutachaAiAction.DraftThread -> {
            val board = futaberAiNamedBoard(command, context) ?: context.currentBoard ?: return missingBoard()
            run(
                FutaberAiEffect.Draft(
                    FutaberPostTarget.CreateThread(board.id), null, board,
                    command.draftSubjectParameter().orEmpty(), command.draftCommentParameter().orEmpty()
                ),
                "スレ立ての下書きを開きました"
            )
        }
        else -> FutaberAiPlan.Fail(futaberAiUnsupportedNotice(command))
    }
}

// ---- boards ----

/**
 * The board [selector] names (its id, name or address; an exact match first, then a partial one, as ふたちゃ does), or
 * the board on screen when nothing is named.
 */
internal fun futaberAiBoard(selector: String?, context: FutaberAiContext): BoardSummary? {
    if (selector.isNullOrBlank()) return context.currentBoard
    return futaberAiMatchBoard(selector, context.boards)
}

private fun futaberAiMatchBoard(selector: String, boards: List<BoardSummary>): BoardSummary? {
    val requested = selector.trim()
    val normalized = requested.lowercase()
    val normalizedUrl = futaberAiBoardUrlKey(requested)
    return boards.firstOrNull { board ->
        board.id.equals(requested, ignoreCase = true) ||
            board.name.equals(requested, ignoreCase = true) ||
            board.url.equals(requested, ignoreCase = true) ||
            (normalizedUrl.isNotBlank() && futaberAiBoardUrlKey(board.url) == normalizedUrl)
    } ?: boards.firstOrNull { board ->
        val boardUrl = futaberAiBoardUrlKey(board.url)
        board.name.lowercase().contains(normalized) ||
            board.id.lowercase().contains(normalized) ||
            (normalizedUrl.contains("/") && boardUrl.isNotBlank() && (boardUrl.contains(normalizedUrl) || normalizedUrl.contains(boardUrl)))
    }
}

private fun futaberAiBoardUrlKey(value: String): String = value.trim().lowercase()
    .substringBefore('#').substringBefore('?').removeSuffix("futaba.php").trimEnd('/')

/**
 * The board a command that is about something else (a word, a search, a thread) names explicitly. Only the board keys
 * are read, so a search word in "query" is never taken for a board name (ふたちゃ's selector also reads "query").
 */
internal fun futaberAiNamedBoard(command: FutachaAiCommand, context: FutaberAiContext): BoardSummary? {
    val selector = command.parameter("board", "boardId", "board_id", "boardName", "board_name") ?: return null
    return futaberAiMatchBoard(selector, context.boards)
}

/** The keys a saved thread of [board] may be indexed under (the shared board key, or this app's board id). */
internal fun futaberAiSavedBoardKeys(board: BoardSummary): Set<String> = setOf(board.id, futaberCompatBoardKey(board))

// ---- threads ----

internal sealed interface FutaberAiThreadTarget {
    data class Found(val board: BoardSummary, val ref: FutaberThreadRef) : FutaberAiThreadTarget
    data class AddBoard(val request: FutaberBoardAddRequest) : FutaberAiThreadTarget
    data class Rejected(val message: String) : FutaberAiThreadTarget
    /** The command names no thread. */
    data object None : FutaberAiThreadTarget
    /** It names one that cannot be found. */
    data object Missing : FutaberAiThreadTarget
}

private val FUTABER_AI_RES_URL_REGEX = Regex("""/res/(\d+)(?:\.html?)?(?:[/?#]|$)""", RegexOption.IGNORE_CASE)

/** The thread number a command names (its thread keys, or its URL); null when it names none. */
internal fun futaberAiExplicitThreadId(command: FutachaAiCommand): String? =
    command.parameter("thread", "threadId", "thread_id", "threadNo", "thread_no")?.trim()?.takeIf { it.isNotEmpty() }
        ?: command.threadUrlParameter()?.let { url ->
            canonicalizeThreadUrl(url)?.threadNo ?: FUTABER_AI_RES_URL_REGEX.find(url)?.groupValues?.getOrNull(1)
        }

/**
 * The thread [command] names. Only a Futaba address, or one of a registered board, is followed (S-1); a thread of an
 * official board that is not registered becomes the "追加して開く" question when [allowAdd].
 */
internal fun futaberAiThreadTarget(command: FutachaAiCommand, context: FutaberAiContext, allowAdd: Boolean): FutaberAiThreadTarget {
    val url = command.threadUrlParameter()
    if (url != null && !isAiThreadUrlAllowed(url, context.boards)) return FutaberAiThreadTarget.Rejected(AI_UNTRUSTED_THREAD_URL_MESSAGE)
    val threadId = futaberAiExplicitThreadId(command) ?: return FutaberAiThreadTarget.None
    val canonical = url?.let(::canonicalizeThreadUrl)
    val urlBoard = when {
        canonical != null -> futaberBoardByCanonicalUrl(context.boards, canonical.canonicalBoardUrl)
        url != null && url.contains("://") -> url.substringBefore("/res/").let { base ->
            context.boards.firstOrNull { futaberAiBoardUrlKey(it.url) == futaberAiBoardUrlKey(base) }
        }
        else -> null
    }
    val board = futaberAiNamedBoard(command, context)
        ?: urlBoard
        ?: (if (url == null || !url.contains("://")) context.currentBoard?.takeIf { current ->
            // The board on screen, unless the history knows the thread on another board only.
            context.currentThread?.threadId == threadId || context.history.none { it.threadId == threadId && it.boardId != current.id }
        } else null)
        ?: context.history.firstOrNull { it.threadId == threadId }?.let { entry -> context.boards.firstOrNull { it.id == entry.boardId } }
    if (board == null) {
        if (allowAdd && canonical != null) {
            futaberBoardAddRequest(url, threadId = threadId, title = command.titleParameter().orEmpty())
                ?.let { return FutaberAiThreadTarget.AddBoard(it) }
        }
        return FutaberAiThreadTarget.Missing
    }
    val current = context.currentThread?.takeIf { it.boardId == board.id && it.threadId == threadId }
    val ref = current
        ?: context.history.firstOrNull { it.threadId == threadId && (it.boardId == board.id || it.boardId.isBlank()) }
            ?.let { futaberRefForHistory(it, context.boards)?.takeIf { ref -> ref.boardId == board.id } }
        ?: FutaberThreadRef(board.id, threadId, command.parameter("title", "subject").orEmpty(), "", 0)
    return FutaberAiThreadTarget.Found(board, ref)
}

/** The thread a thread command is for: the one it names, else the one on screen. */
private fun futaberAiThreadOrCurrent(command: FutachaAiCommand, context: FutaberAiContext): Result<Pair<BoardSummary, FutaberThreadRef>> =
    when (val target = futaberAiThreadTarget(command, context, allowAdd = false)) {
        is FutaberAiThreadTarget.Found -> Result.success(target.board to target.ref)
        is FutaberAiThreadTarget.Rejected -> Result.failure(IllegalStateException(target.message))
        FutaberAiThreadTarget.None -> {
            val board = context.currentBoard
            val ref = context.currentThread
            if (board != null && ref != null) Result.success(board to ref) else Result.failure(IllegalStateException(FUTABER_AI_OPEN_THREAD_FIRST))
        }
        else -> Result.failure(IllegalStateException(FUTABER_AI_THREAD_NOT_FOUND))
    }

/** [this] with the board / thread it was resolved to, so the screen that receives it can tell whether it is meant for it. */
internal fun FutachaAiCommand.withFutaberTarget(boardId: String, threadId: String?): FutachaAiCommand =
    copy(parameters = parameters + listOfNotNull("boardId" to boardId, threadId?.let { "threadId" to it }))

// ---- history ----

internal sealed interface FutaberAiHistoryMatch {
    data class Found(val entry: ThreadHistoryEntry) : FutaberAiHistoryMatch
    data object Ambiguous : FutaberAiHistoryMatch
    data object Missing : FutaberAiHistoryMatch
}

/** The one history row [command] names (the thread on screen when it names none). */
internal fun futaberAiHistoryEntry(command: FutachaAiCommand, context: FutaberAiContext): FutaberAiHistoryMatch {
    val url = command.threadUrlParameter()
    if (url != null && !isAiThreadUrlAllowed(url, context.boards)) return FutaberAiHistoryMatch.Missing
    val threadId = futaberAiExplicitThreadId(command) ?: command.threadIdParameter() ?: context.currentThread?.threadId
        ?: return FutaberAiHistoryMatch.Missing
    val board = futaberAiNamedBoard(command, context)
        ?: url?.let(::canonicalizeThreadUrl)?.let { futaberBoardByCanonicalUrl(context.boards, it.canonicalBoardUrl) }
        ?: context.currentBoard?.takeIf { futaberAiExplicitThreadId(command) == null && command.threadIdParameter() == null }
    val matches = context.history.filter { it.threadId == threadId && (board == null || it.boardId == board.id) }
    return when (matches.size) {
        0 -> FutaberAiHistoryMatch.Missing
        1 -> FutaberAiHistoryMatch.Found(matches.single())
        else -> FutaberAiHistoryMatch.Ambiguous
    }
}

/** Appends [value] unless the list already has it (ignoring case), as ふたちゃ's command does. */
internal fun List<String>.futaberAppendDistinct(value: String): List<String> {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || any { it.equals(trimmed, ignoreCase = true) }) return this
    return this + trimmed
}
