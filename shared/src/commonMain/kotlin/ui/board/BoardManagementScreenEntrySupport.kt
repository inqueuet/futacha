package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry

internal data class BoardManagementScreenContentArgs(
    val boards: List<BoardSummary>,
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit,
    override val screenContext: ResolvedScreenContext,
    val dependencies: ResolvedScreenServiceDependencies,
    val modifier: Modifier
) : ScreenContextOwner

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
    onBoardSelected: (BoardSummary) -> Unit,
    onAddBoard: (String, String) -> Unit,
    onMenuAction: (BoardManagementMenuAction) -> Unit,
    onBoardDeleted: (BoardSummary) -> Unit,
    onBoardsReordered: (List<BoardSummary>) -> Unit,
    screenContext: ResolvedScreenContext,
    dependencies: ResolvedScreenServiceDependencies,
    modifier: Modifier = Modifier
): BoardManagementScreenContentArgs {
    return BoardManagementScreenContentArgs(
        boards = boards,
        onBoardSelected = onBoardSelected,
        onAddBoard = onAddBoard,
        onMenuAction = onMenuAction,
        onBoardDeleted = onBoardDeleted,
        onBoardsReordered = onBoardsReordered,
        screenContext = screenContext,
        dependencies = dependencies,
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
        onBoardSelected = onBoardSelected,
        onAddBoard = onAddBoard,
        onMenuAction = onMenuAction,
        onBoardDeleted = onBoardDeleted,
        onBoardsReordered = onBoardsReordered,
        screenContext = resolveScreenContext(screenContract),
        dependencies = resolveBoardManagementScreenDependencies(
            dependencies = dependencies
        ),
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
    return buildBoardManagementScreenContentArgsFromContract(
        boards = boards,
        screenContract = buildScreenContract(
            history = history,
            historyCallbacks = historyCallbacks,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryCleared = onHistoryCleared,
            onHistoryRefresh = onHistoryRefresh,
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks
        ),
        onBoardSelected = onBoardSelected,
        onAddBoard = onAddBoard,
        onMenuAction = onMenuAction,
        modifier = modifier,
        onBoardDeleted = onBoardDeleted,
        onBoardsReordered = onBoardsReordered,
        dependencies = dependencies.withOverrides(
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSavedThreadRepository
        )
    )
}
