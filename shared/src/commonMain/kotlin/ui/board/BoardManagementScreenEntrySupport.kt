package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry

internal data class BoardManagementScreenContentArgs(
    val boards: List<BoardSummary>,
    val history: List<ThreadHistoryEntry>,
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit,
    val historyCallbacks: ResolvedScreenHistoryCallbacks,
    val dependencies: ResolvedScreenServiceDependencies,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val modifier: Modifier
)

internal fun resolveBoardManagementScreenDependencies(
    dependencies: BoardManagementScreenDependencies = BoardManagementScreenDependencies(),
    autoSavedThreadRepository: com.valoser.futacha.shared.repository.SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository? = dependencies.cookieRepository,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = dependencies.fileSystem
): ResolvedScreenServiceDependencies {
    return resolveScreenServiceDependencies(
        dependencies = dependencies.services,
        autoSavedThreadRepository = autoSavedThreadRepository,
        cookieRepository = cookieRepository,
        fileSystem = fileSystem
    )
}

internal fun assembleBoardManagementScreenContentArgs(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    onBoardSelected: (BoardSummary) -> Unit,
    onAddBoard: (String, String) -> Unit,
    onMenuAction: (BoardManagementMenuAction) -> Unit,
    onBoardDeleted: (BoardSummary) -> Unit,
    onBoardsReordered: (List<BoardSummary>) -> Unit,
    historyCallbacks: ResolvedScreenHistoryCallbacks,
    dependencies: ResolvedScreenServiceDependencies,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    modifier: Modifier = Modifier
): BoardManagementScreenContentArgs {
    return BoardManagementScreenContentArgs(
        boards = boards,
        history = history,
        onBoardSelected = onBoardSelected,
        onAddBoard = onAddBoard,
        onMenuAction = onMenuAction,
        onBoardDeleted = onBoardDeleted,
        onBoardsReordered = onBoardsReordered,
        historyCallbacks = historyCallbacks,
        dependencies = dependencies,
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
        modifier = modifier
    )
}

internal fun buildBoardManagementScreenContentArgsFromContract(
    boards: List<BoardSummary>,
    screenContract: ScreenContract,
    onBoardSelected: (BoardSummary) -> Unit,
    onAddBoard: (String, String) -> Unit,
    onMenuAction: (BoardManagementMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    onBoardDeleted: (BoardSummary) -> Unit = {},
    onBoardsReordered: (List<BoardSummary>) -> Unit = {},
    dependencies: BoardManagementScreenDependencies = BoardManagementScreenDependencies()
): BoardManagementScreenContentArgs {
    return assembleBoardManagementScreenContentArgs(
        boards = boards,
        history = screenContract.history,
        onBoardSelected = onBoardSelected,
        onAddBoard = onAddBoard,
        onMenuAction = onMenuAction,
        onBoardDeleted = onBoardDeleted,
        onBoardsReordered = onBoardsReordered,
        historyCallbacks = resolveScreenHistoryCallbacks(
            historyCallbacks = screenContract.historyCallbacks
        ),
        dependencies = resolveBoardManagementScreenDependencies(
            dependencies = dependencies
        ),
        preferencesState = screenContract.preferencesState,
        preferencesCallbacks = screenContract.preferencesCallbacks,
        modifier = modifier
    )
}

internal fun buildBoardManagementScreenContentArgs(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    onBoardSelected: (BoardSummary) -> Unit,
    onAddBoard: (String, String) -> Unit,
    onMenuAction: (BoardManagementMenuAction) -> Unit,
    historyCallbacks: ScreenHistoryCallbacks = ScreenHistoryCallbacks(),
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntrySelected,
    onHistoryRefresh: suspend () -> Unit = historyCallbacks.onHistoryRefresh,
    modifier: Modifier = Modifier,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryDismissed,
    onHistoryCleared: () -> Unit = historyCallbacks.onHistoryCleared,
    onBoardDeleted: (BoardSummary) -> Unit = {},
    onBoardsReordered: (List<BoardSummary>) -> Unit = {},
    dependencies: BoardManagementScreenDependencies = BoardManagementScreenDependencies(),
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository? = dependencies.cookieRepository,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks(),
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = dependencies.fileSystem,
    autoSavedThreadRepository: com.valoser.futacha.shared.repository.SavedThreadRepository? = dependencies.autoSavedThreadRepository
): BoardManagementScreenContentArgs {
    return assembleBoardManagementScreenContentArgs(
        boards = boards,
        history = history,
        onBoardSelected = onBoardSelected,
        onAddBoard = onAddBoard,
        onMenuAction = onMenuAction,
        onBoardDeleted = onBoardDeleted,
        onBoardsReordered = onBoardsReordered,
        historyCallbacks = resolveScreenHistoryCallbacks(
            historyCallbacks = historyCallbacks,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryCleared = onHistoryCleared,
            onHistoryRefresh = onHistoryRefresh
        ),
        dependencies = resolveBoardManagementScreenDependencies(
            dependencies = dependencies,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem
        ),
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
        modifier = modifier
    )
}
