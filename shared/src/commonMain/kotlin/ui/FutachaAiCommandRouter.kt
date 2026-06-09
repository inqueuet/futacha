package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandOutcome
import com.valoser.futacha.shared.ai.FutachaAiCommandRisk
import com.valoser.futacha.shared.ai.FutachaAiConfirmationRequest
import com.valoser.futacha.shared.ai.boardSelectorParameter
import com.valoser.futacha.shared.ai.boardUrlParameter
import com.valoser.futacha.shared.ai.catalogModeParameter
import com.valoser.futacha.shared.ai.confirmationReason
import com.valoser.futacha.shared.ai.searchQueryParameter
import com.valoser.futacha.shared.ai.titleParameter
import com.valoser.futacha.shared.ai.threadIdParameter
import com.valoser.futacha.shared.ai.threadUrlParameter
import com.valoser.futacha.shared.ai.wordParameter
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.buildAddBoardValidationState
import com.valoser.futacha.shared.ui.board.createCustomBoardSummary
import com.valoser.futacha.shared.ui.board.ALPHA_AI_POST_FILTER_ENABLED
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val AI_HISTORY_REFRESH_TIMEOUT_MILLIS = 60_000L
private const val AI_HISTORY_REFRESH_MAX_THREADS = 12
private const val AI_HISTORY_REFRESH_AUTO_SAVE_BUDGET_MILLIS = 10_000L
private const val AI_HISTORY_REFRESH_MAX_AUTO_SAVES = 1

internal data class FutachaAiRouterInputs(
    val stateStore: AppStateStore,
    val boards: List<BoardSummary>,
    val history: List<ThreadHistoryEntry>,
    val navigationState: FutachaNavigationState,
    val updateNavigationState: (FutachaNavigationState) -> Unit,
    val historyRefresher: HistoryRefresher?,
    val savedThreadRepository: SavedThreadRepository?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val isCookieManagementAvailable: Boolean,
    val appVersion: String,
    val isAiCommandEnabled: Boolean
)

@OptIn(ExperimentalTime::class)
internal suspend fun executeFutachaAiCommand(
    command: FutachaAiCommand,
    inputs: FutachaAiRouterInputs,
    confirmed: Boolean = false
): FutachaAiCommandOutcome {
    if (!inputs.isAiCommandEnabled && !command.action.isAllowedWhenAiCommandsDisabled()) {
        return FutachaAiCommandOutcome.Failed("AIアプリ操作は設定でOFFです。設定画面から「AIアプリ操作」をONにしてください。")
    }

    if (command.action.risk == FutachaAiCommandRisk.Confirm && !confirmed) {
        return FutachaAiCommandOutcome.NeedsConfirmation(
            buildFutachaAiConfirmationRequest(command)
        )
    }

    return when (command.action) {
        FutachaAiAction.OpenBoardList -> {
            inputs.updateNavigationState(FutachaNavigationState())
            FutachaAiCommandOutcome.Completed("板一覧を開きました")
        }
        FutachaAiAction.OpenBoard,
        FutachaAiAction.SearchAndOpenBoard -> {
            val board = resolveAiBoard(command, inputs) ?: return missingBoardOutcome(command)
            inputs.updateNavigationState(selectFutachaBoard(inputs.navigationState, board.id))
            FutachaAiCommandOutcome.Completed("${board.name} を開きました")
        }
        FutachaAiAction.RefreshCurrentBoard,
        FutachaAiAction.RefreshCatalog -> {
            val board = currentOrRequestedBoard(command, inputs) ?: return missingBoardOutcome(command)
            inputs.updateNavigationState(selectFutachaBoard(inputs.navigationState, board.id))
            FutachaAiCommandOutcome.NeedsForeground("${board.name} のカタログを更新します")
        }
        FutachaAiAction.OpenHistoryDrawer -> {
            if (inputs.navigationState.isSavedThreadsVisible) {
                inputs.updateNavigationState(FutachaNavigationState())
            }
            FutachaAiCommandOutcome.NeedsForeground("履歴ドロワーを開きます")
        }
        FutachaAiAction.RefreshHistory -> {
            val refresher = inputs.historyRefresher
                ?: return FutachaAiCommandOutcome.Failed("履歴更新サービスを利用できません")
            withTimeoutOrNull(AI_HISTORY_REFRESH_TIMEOUT_MILLIS) {
                refresher.refresh(
                    boardsSnapshot = inputs.boards,
                    historySnapshot = inputs.history,
                    autoSaveBudgetMillis = AI_HISTORY_REFRESH_AUTO_SAVE_BUDGET_MILLIS,
                    maxThreadsPerRun = AI_HISTORY_REFRESH_MAX_THREADS,
                    maxAutoSavesPerRun = AI_HISTORY_REFRESH_MAX_AUTO_SAVES
                )
                true
            } ?: return FutachaAiCommandOutcome.Failed("履歴更新がタイムアウトしました。更新対象を絞って再試行してください")
            FutachaAiCommandOutcome.Completed("履歴を更新しました")
        }
        FutachaAiAction.OpenThread,
        FutachaAiAction.OpenThreadFromUrl,
        FutachaAiAction.SearchThread,
        FutachaAiAction.SaveThread -> {
            val target = resolveAiThreadSelection(command, inputs)
                ?: return FutachaAiCommandOutcome.Failed("対象スレを特定できませんでした")
            inputs.updateNavigationState(applyFutachaThreadSelection(inputs.navigationState, target))
            val suffix = when (command.action) {
                FutachaAiAction.SearchThread -> "スレ内検索を開始します"
                FutachaAiAction.SaveThread -> "スレ保存を開始します"
                else -> ""
            }
            FutachaAiCommandOutcome.Completed("スレ ${target.threadId} を開きました${suffix.withLeadingSeparator()}")
        }
        FutachaAiAction.RefreshCurrentThread,
        FutachaAiAction.ScrollThreadToTop,
        FutachaAiAction.ScrollThreadToBottom,
        FutachaAiAction.StartThreadReadAloud,
        FutachaAiAction.PauseThreadReadAloud,
        FutachaAiAction.StopThreadReadAloud,
        FutachaAiAction.NextThreadReadAloud,
        FutachaAiAction.PreviousThreadReadAloud,
        FutachaAiAction.StartThreadSearch,
        FutachaAiAction.NextSearchResult,
        FutachaAiAction.PreviousSearchResult,
        FutachaAiAction.OpenGallery,
        FutachaAiAction.OpenThreadSettings,
        FutachaAiAction.OpenThreadExternally,
        FutachaAiAction.SaveCurrentThread,
        FutachaAiAction.DraftReply -> {
            val target = resolveCurrentThreadSelection(inputs)
                ?: resolveAiThreadSelection(command, inputs)
                ?: return FutachaAiCommandOutcome.Failed("先に対象スレを開いてください")
            inputs.updateNavigationState(applyFutachaThreadSelection(inputs.navigationState, target))
            FutachaAiCommandOutcome.NeedsForeground("${target.threadId} を開きました。${command.action.label} を実行します。")
        }
        FutachaAiAction.ScrollCatalogToTop,
        FutachaAiAction.StartCatalogSearch,
        FutachaAiAction.SearchCatalog,
        FutachaAiAction.OpenCatalogSettings,
        FutachaAiAction.OpenCatalogDisplaySettings,
        FutachaAiAction.OpenNgManagement,
        FutachaAiAction.OpenWatchWords,
        FutachaAiAction.OpenBoardExternally,
        FutachaAiAction.DraftThread -> {
            val board = currentOrRequestedBoard(command, inputs) ?: return missingBoardOutcome(command)
            inputs.updateNavigationState(selectFutachaBoard(inputs.navigationState, board.id))
            val suffix = if (command.action == FutachaAiAction.SearchCatalog) {
                command.searchQueryParameter()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "検索語: $it" }
                    .orEmpty()
            } else {
                "${command.action.label} を実行します。"
            }
            FutachaAiCommandOutcome.NeedsForeground("${board.name} を開きました。$suffix")
        }
        FutachaAiAction.OpenSavedThreads -> {
            if (inputs.savedThreadRepository == null) {
                return FutachaAiCommandOutcome.Failed("保存済みスレ一覧を利用できません")
            }
            inputs.updateNavigationState(inputs.navigationState.copy(selectedBoardId = null, selectedThreadId = null, isSavedThreadsVisible = true))
            FutachaAiCommandOutcome.Completed("保存済みスレ一覧を開きました")
        }
        FutachaAiAction.OpenGlobalSettings,
        FutachaAiAction.OpenVersionInfo,
        FutachaAiAction.OpenCookieManagement,
        FutachaAiAction.OpenFileManagerSettings -> {
            if (command.action == FutachaAiAction.OpenCookieManagement &&
                !inputs.isCookieManagementAvailable
            ) {
                return FutachaAiCommandOutcome.Failed("Cookie管理を利用できません")
            }
            if (command.action == FutachaAiAction.OpenCookieManagement &&
                inputs.navigationState.isSavedThreadsVisible
            ) {
                inputs.updateNavigationState(FutachaNavigationState())
            }
            FutachaAiCommandOutcome.Completed(
                when (command.action) {
                    FutachaAiAction.OpenVersionInfo -> "現在のバージョンは ${inputs.appVersion} です"
                    FutachaAiAction.OpenCookieManagement -> "Cookie管理画面を開きます"
                    else -> "設定画面を開きます"
                }
            )
        }
        FutachaAiAction.EnablePrivacyFilter -> {
            inputs.stateStore.setPrivacyFilterEnabled(true)
            FutachaAiCommandOutcome.Completed("プライバシーフィルタをONにしました")
        }
        FutachaAiAction.DisablePrivacyFilter -> {
            inputs.stateStore.setPrivacyFilterEnabled(false)
            FutachaAiCommandOutcome.Completed("プライバシーフィルタをOFFにしました")
        }
        FutachaAiAction.EnableBackgroundRefresh -> {
            inputs.stateStore.setBackgroundRefreshEnabled(true)
            FutachaAiCommandOutcome.Completed("バックグラウンド更新をONにしました")
        }
        FutachaAiAction.DisableBackgroundRefresh -> {
            inputs.stateStore.setBackgroundRefreshEnabled(false)
            FutachaAiCommandOutcome.Completed("バックグラウンド更新をOFFにしました")
        }
        FutachaAiAction.EnableThreadSummaryMode -> {
            inputs.stateStore.setThreadSummaryModeEnabled(true)
            FutachaAiCommandOutcome.Completed("スレ要約モードをONにしました")
        }
        FutachaAiAction.DisableThreadSummaryMode -> {
            inputs.stateStore.setThreadSummaryModeEnabled(false)
            FutachaAiCommandOutcome.Completed("スレ要約モードをOFFにしました")
        }
        FutachaAiAction.EnableAiPostFilter -> {
            if (!ALPHA_AI_POST_FILTER_ENABLED) {
                return FutachaAiCommandOutcome.Failed("荒らし非表示（アルファ版）は現在有効化できません")
            }
            inputs.stateStore.setAiPostFilterEnabled(true)
            FutachaAiCommandOutcome.Completed("荒らし非表示をONにしました")
        }
        FutachaAiAction.DisableAiPostFilter -> {
            inputs.stateStore.setAiPostFilterEnabled(false)
            FutachaAiCommandOutcome.Completed("荒らし非表示をOFFにしました")
        }
        FutachaAiAction.SetCatalogMode -> {
            val board = currentOrRequestedBoard(command, inputs) ?: return missingBoardOutcome(command)
            val mode = resolveCatalogMode(command)
                ?: return FutachaAiCommandOutcome.Failed("カタログモードを特定できませんでした")
            inputs.stateStore.setCatalogMode(board.id, mode)
            inputs.updateNavigationState(selectFutachaBoard(inputs.navigationState, board.id))
            FutachaAiCommandOutcome.Completed("${board.name} のカタログモードを ${mode.label} にしました")
        }
        FutachaAiAction.AddWatchWord -> {
            val word = command.wordParameter()
                ?: return FutachaAiCommandOutcome.Failed("追加する監視ワードがありません")
            val words = inputs.stateStore.watchWords.first().appendDistinct(word)
            inputs.stateStore.setWatchWords(words)
            FutachaAiCommandOutcome.Completed("監視ワードに「$word」を追加しました")
        }
        FutachaAiAction.AddNgWord -> {
            val word = command.wordParameter()
                ?: return FutachaAiCommandOutcome.Failed("追加するNGワードがありません")
            val words = inputs.stateStore.ngWords.first().appendDistinct(word)
            inputs.stateStore.setNgWords(words)
            FutachaAiCommandOutcome.Completed("NGワードに「$word」を追加しました")
        }
        FutachaAiAction.AddNgHeader -> {
            val header = command.parameter("header") ?: command.wordParameter()
                ?: return FutachaAiCommandOutcome.Failed("追加するNGヘッダーがありません")
            val headers = inputs.stateStore.ngHeaders.first().appendDistinct(header)
            inputs.stateStore.setNgHeaders(headers)
            FutachaAiCommandOutcome.Completed("NGヘッダーに「$header」を追加しました")
        }
        FutachaAiAction.DeleteHistoryEntry -> {
            when (val target = resolveHistoryEntryForDeletion(command, inputs)) {
                FutachaHistoryEntryResolveResult.Ambiguous -> {
                    FutachaAiCommandOutcome.Failed("同じ条件の履歴が複数あります。板も指定してください")
                }
                FutachaHistoryEntryResolveResult.Missing -> {
                    FutachaAiCommandOutcome.Failed("削除する履歴を特定できませんでした")
                }
                is FutachaHistoryEntryResolveResult.Found -> {
                    dismissHistoryEntry(
                        stateStore = inputs.stateStore,
                        autoSavedThreadRepository = inputs.autoSavedThreadRepository,
                        entry = target.entry
                    )
                    FutachaAiCommandOutcome.Completed("履歴から ${target.entry.threadId} を削除しました")
                }
            }
        }
        FutachaAiAction.ClearHistory -> {
            clearHistory(
                stateStore = inputs.stateStore,
                autoSavedThreadRepository = inputs.autoSavedThreadRepository,
                onSkippedThreadsCleared = {
                    inputs.historyRefresher?.clearSkippedThreads()
                }
            )
            FutachaAiCommandOutcome.Completed("履歴を全削除しました")
        }
        FutachaAiAction.DeleteSavedThread -> {
            val repository = inputs.savedThreadRepository
                ?: return FutachaAiCommandOutcome.Failed("保存済みスレのリポジトリを利用できません")
            when (val target = resolveSavedThreadSelection(command, inputs, repository)) {
                FutachaSavedThreadResolveResult.Ambiguous -> {
                    FutachaAiCommandOutcome.Failed("同じ条件の保存済みスレが複数あります。板も指定してください")
                }
                FutachaSavedThreadResolveResult.Missing -> {
                    FutachaAiCommandOutcome.Failed("削除する保存済みスレを特定できませんでした")
                }
                is FutachaSavedThreadResolveResult.Found -> {
                    repository.deleteThread(target.thread.threadId, target.thread.boardId)
                        .getOrElse { error ->
                            return FutachaAiCommandOutcome.Failed(
                                "保存済みスレ ${target.thread.threadId} を削除できませんでした: ${error.toAiErrorText()}"
                            )
                        }
                    FutachaAiCommandOutcome.Completed("保存済みスレ ${target.thread.threadId} を削除しました")
                }
            }
        }
        FutachaAiAction.ClearSavedThreads -> {
            val repository = inputs.savedThreadRepository
                ?: return FutachaAiCommandOutcome.Failed("保存済みスレのリポジトリを利用できません")
            repository.deleteAllThreads()
                .getOrElse { error ->
                    return FutachaAiCommandOutcome.Failed(
                        "保存済みスレを全削除できませんでした: ${error.toAiErrorText()}"
                    )
                }
            FutachaAiCommandOutcome.Completed("保存済みスレを全削除しました")
        }
        FutachaAiAction.AddBoard -> {
            val name = command.parameter("name", "board", "title", "label") ?: "新しい板"
            val url = command.boardUrlParameter()
                ?: return FutachaAiCommandOutcome.Failed("追加する板URLがありません")
            val currentBoards = inputs.stateStore.boards.first().ifEmpty { inputs.boards }
            val validation = buildAddBoardValidationState(
                name = name,
                url = url,
                existingBoards = currentBoards
            )
            if (!validation.canSubmit) {
                return FutachaAiCommandOutcome.Failed(
                    validation.helperText ?: "板名と有効なURLを指定してください"
                )
            }
            inputs.stateStore.updateBoards { boards ->
                boards + createCustomBoardSummary(
                    name = validation.trimmedName,
                    url = validation.normalizedInputUrl,
                    existingBoards = boards
                )
            }
            FutachaAiCommandOutcome.Completed("板「${validation.trimmedName}」を追加しました")
        }
        FutachaAiAction.DeleteBoard -> {
            val board = resolveAiBoard(command, inputs) ?: return missingBoardOutcome(command)
            val currentBoards = inputs.stateStore.boards.first()
            if (currentBoards.none { it.id == board.id }) {
                return FutachaAiCommandOutcome.Failed("削除する板を特定できませんでした")
            }
            inputs.stateStore.updateBoards { boards -> boards.filterNot { it.id == board.id } }
            if (inputs.navigationState.selectedBoardId == board.id) {
                inputs.updateNavigationState(FutachaNavigationState())
            }
            FutachaAiCommandOutcome.Completed("板「${board.name}」を削除しました")
        }
    }
}

private fun FutachaAiAction.isAllowedWhenAiCommandsDisabled(): Boolean {
    return this == FutachaAiAction.OpenGlobalSettings ||
        this == FutachaAiAction.OpenVersionInfo ||
        this == FutachaAiAction.OpenFileManagerSettings
}

private fun buildFutachaAiConfirmationRequest(command: FutachaAiCommand): FutachaAiConfirmationRequest {
    return FutachaAiConfirmationRequest(
        command = command,
        title = "AI操作の確認",
        message = "「${command.action.label}」を実行します。${command.action.confirmationReason()}、ユーザー確認後にだけ進めます。",
        confirmLabel = "続行"
    )
}

private fun missingBoardOutcome(command: FutachaAiCommand): FutachaAiCommandOutcome {
    return FutachaAiCommandOutcome.Failed("対象板を特定できませんでした: ${command.action.label}")
}

private fun resolveAiBoard(
    command: FutachaAiCommand,
    inputs: FutachaAiRouterInputs
): BoardSummary? {
    val requested = command.boardSelectorParameter()
    if (requested.isNullOrBlank()) {
        return inputs.navigationState.selectedBoardId?.let { current ->
            inputs.boards.firstOrNull { it.id == current }
        }
    }
    val normalized = requested.trim().lowercase()
    val normalizedUrl = normalizeAiBoardUrlForMatching(requested)
    return inputs.boards.firstOrNull { board ->
        val boardUrl = normalizeAiBoardUrlForMatching(board.url)
        board.id.equals(normalized, ignoreCase = true) ||
            board.name.equals(requested, ignoreCase = true) ||
            board.url.equals(requested, ignoreCase = true) ||
            (normalizedUrl.isNotBlank() && boardUrl == normalizedUrl)
    } ?: inputs.boards.firstOrNull { board ->
        val boardUrl = normalizeAiBoardUrlForMatching(board.url)
        board.name.lowercase().contains(normalized) ||
            board.id.lowercase().contains(normalized) ||
            (normalizedUrl.isNotBlank() && boardUrl.isNotBlank() && (
                boardUrl.contains(normalizedUrl) ||
                    normalizedUrl.contains(boardUrl)
                ))
    }
}

private fun normalizeAiBoardUrlForMatching(value: String): String {
    return value
        .trim()
        .lowercase()
        .substringBefore('#')
        .substringBefore('?')
        .removeSuffix("futaba.php")
        .trimEnd('/')
}

private fun currentOrRequestedBoard(
    command: FutachaAiCommand,
    inputs: FutachaAiRouterInputs
): BoardSummary? {
    return resolveAiBoard(command, inputs)
        ?: inputs.navigationState.selectedBoardId?.let { boardId ->
            inputs.boards.firstOrNull { it.id == boardId }
        }
}

private fun resolveAiThreadSelection(
    command: FutachaAiCommand,
    inputs: FutachaAiRouterInputs
): FutachaThreadSelection? {
    val url = command.threadUrlParameter()
    val parsed = url?.let(::parseThreadFromUrl)
    val threadId = command.threadIdParameter()
        ?: parsed?.second
        ?: inputs.navigationState.selectedThreadId.takeUnless { inputs.navigationState.isSavedThreadsVisible }
        ?: return null
    val board = resolveAiBoard(command, inputs)
        ?: parsed?.first?.let { parsedBoardKey ->
            inputs.boards.firstOrNull { board ->
                board.url.contains(parsedBoardKey, ignoreCase = true) ||
                    board.id.equals(parsedBoardKey, ignoreCase = true)
            }
        }
        ?: inputs.navigationState.selectedBoardId?.let { boardId ->
            inputs.boards.firstOrNull { it.id == boardId }
        }
        ?: resolveHistoryEntry(command.copyWithThreadId(threadId), inputs)?.let { entry ->
            inputs.boards.firstOrNull { it.id == entry.boardId }
        }
        ?: return null
    val historyEntry = inputs.history.firstOrNull {
        it.threadId == threadId && (it.boardId == board.id || it.boardUrl.equals(board.url, ignoreCase = true))
    }
    return FutachaThreadSelection(
        boardId = board.id,
        boardName = board.name,
        threadId = threadId,
        threadTitle = historyEntry?.title ?: command.titleParameter(),
        threadReplies = historyEntry?.replyCount,
        threadThumbnailUrl = historyEntry?.titleImageUrl,
        threadUrl = url ?: historyEntry?.boardUrl?.let { "$it/res/$threadId.htm" },
        isSavedThreadsVisible = false
    )
}

private fun resolveCurrentThreadSelection(inputs: FutachaAiRouterInputs): FutachaThreadSelection? {
    if (inputs.navigationState.isSavedThreadsVisible) return null
    val boardId = inputs.navigationState.selectedBoardId ?: return null
    val threadId = inputs.navigationState.selectedThreadId ?: return null
    val board = inputs.boards.firstOrNull { it.id == boardId } ?: return null
    return FutachaThreadSelection(
        boardId = board.id,
        boardName = board.name,
        threadId = threadId,
        threadTitle = inputs.navigationState.selectedThreadTitle,
        threadReplies = inputs.navigationState.selectedThreadReplies,
        threadThumbnailUrl = inputs.navigationState.selectedThreadThumbnailUrl,
        threadUrl = inputs.navigationState.selectedThreadUrl,
        isSavedThreadsVisible = false
    )
}

private fun resolveHistoryEntry(
    command: FutachaAiCommand,
    inputs: FutachaAiRouterInputs
): ThreadHistoryEntry? {
    val threadId = command.threadIdParameter()
        ?: inputs.navigationState.selectedThreadId
    val board = resolveAiBoard(command, inputs)
    return inputs.history.firstOrNull { entry ->
        (threadId == null || entry.threadId == threadId) &&
            (board == null || entry.boardId == board.id || entry.boardUrl.equals(board.url, ignoreCase = true))
    }
}

private sealed interface FutachaHistoryEntryResolveResult {
    data class Found(val entry: ThreadHistoryEntry) : FutachaHistoryEntryResolveResult
    data object Missing : FutachaHistoryEntryResolveResult
    data object Ambiguous : FutachaHistoryEntryResolveResult
}

private fun resolveHistoryEntryForDeletion(
    command: FutachaAiCommand,
    inputs: FutachaAiRouterInputs
): FutachaHistoryEntryResolveResult {
    val threadId = command.threadIdParameter()
        ?: inputs.navigationState.selectedThreadId
    val board = resolveAiBoard(command, inputs)
    val title = command.titleParameter()

    val matches = inputs.history.filter { entry ->
        (threadId == null || entry.threadId == threadId) &&
            (title == null || entry.title.equals(title, ignoreCase = true) || entry.title.contains(title, ignoreCase = true)) &&
            (board == null || entry.boardId == board.id || entry.boardUrl.equals(board.url, ignoreCase = true))
    }
    return when (matches.size) {
        0 -> FutachaHistoryEntryResolveResult.Missing
        1 -> FutachaHistoryEntryResolveResult.Found(matches.single())
        else -> FutachaHistoryEntryResolveResult.Ambiguous
    }
}

private sealed interface FutachaSavedThreadResolveResult {
    data class Found(val thread: SavedThread) : FutachaSavedThreadResolveResult
    data object Missing : FutachaSavedThreadResolveResult
    data object Ambiguous : FutachaSavedThreadResolveResult
}

private suspend fun resolveSavedThreadSelection(
    command: FutachaAiCommand,
    inputs: FutachaAiRouterInputs,
    repository: SavedThreadRepository
): FutachaSavedThreadResolveResult {
    val savedThreads = repository.getAllThreads()
    val threadId = command.threadIdParameter()
        ?: inputs.navigationState.selectedThreadId
    val explicitBoardId = command.parameter("boardId", "board_id")
        ?: resolveAiBoard(command, inputs)?.id
    val inferredBoardIds = buildList {
        explicitBoardId?.let(::add)
        if (explicitBoardId == null) {
            inputs.navigationState.selectedBoardId?.let(::add)
            if (threadId != null) {
                inputs.history
                    .filter { it.threadId == threadId }
                    .map { it.boardId }
                    .forEach(::add)
            }
        }
    }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }

    if (threadId != null) {
        val matches = savedThreads.filter { it.threadId == threadId }
        val scopedMatches = if (inferredBoardIds.isEmpty()) {
            matches
        } else {
            matches.filter { thread ->
                inferredBoardIds.any { boardId -> thread.boardId.equals(boardId, ignoreCase = true) }
            }
        }
        return scopedMatches.toSavedThreadResolveResult()
    }

    val title = command.titleParameter() ?: return FutachaSavedThreadResolveResult.Missing
    val normalizedTitle = title.lowercase()
    val exactMatches = savedThreads.filter { it.title.equals(title, ignoreCase = true) }
    val titleMatches = exactMatches.ifEmpty {
        savedThreads.filter { it.title.lowercase().contains(normalizedTitle) }
    }
    val scopedMatches = if (inferredBoardIds.isEmpty()) {
        titleMatches
    } else {
        titleMatches.filter { thread ->
            inferredBoardIds.any { boardId -> thread.boardId.equals(boardId, ignoreCase = true) }
        }
    }
    return scopedMatches.toSavedThreadResolveResult()
}

private fun List<SavedThread>.toSavedThreadResolveResult(): FutachaSavedThreadResolveResult {
    return when (size) {
        0 -> FutachaSavedThreadResolveResult.Missing
        1 -> FutachaSavedThreadResolveResult.Found(single())
        else -> FutachaSavedThreadResolveResult.Ambiguous
    }
}

private fun resolveCatalogMode(command: FutachaAiCommand): CatalogMode? {
    val raw = command.catalogModeParameter() ?: return null
    val normalized = raw.trim().lowercase()
    return CatalogMode.entries.firstOrNull { mode ->
        mode.name.lowercase() == normalized ||
            mode.label.lowercase() == normalized ||
            mode.sortParam == normalized
    }
}

private fun parseThreadFromUrl(url: String): Pair<String?, String>? {
    val threadId = Regex("""/res/(\d+)\.htm""").find(url)?.groupValues?.getOrNull(1)
        ?: Regex("""/res/(\d+)(?:[/?#]|$)""").find(url)?.groupValues?.getOrNull(1)
        ?: Regex("""[?&]thread(?:Id|_id)?=(\d+)""").find(url)?.groupValues?.getOrNull(1)
        ?: return null
    val boardKey = Regex("""https?://[^/]+/([^/?#]+)/?""").find(url)?.groupValues?.getOrNull(1)
    return boardKey to threadId
}

private fun List<String>.appendDistinct(value: String): List<String> {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return this
    return if (any { it.equals(trimmed, ignoreCase = true) }) this else this + trimmed
}

private fun String.withLeadingSeparator(): String {
    return if (isBlank()) "" else " ($this)"
}

private fun Throwable.toAiErrorText(): String {
    return message?.takeIf { it.isNotBlank() } ?: "不明なエラー"
}

private fun FutachaAiCommand.copyWithThreadId(threadId: String): FutachaAiCommand {
    return copy(parameters = parameters + ("threadId" to threadId))
}
