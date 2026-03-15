package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.CoroutineScope

internal data class BoardManagementInteractionBindingsBundle(
    val historyDrawerCallbacks: BoardManagementHistoryDrawerCallbacks,
    val menuActionCallbacks: BoardManagementMenuActionCallbacks,
    val dialogCallbacks: BoardManagementDialogCallbacks,
    val topBarCallbacks: BoardManagementTopBarCallbacks,
    val boardListCallbacks: BoardManagementBoardListCallbacks,
    val onHistorySettingsClick: () -> Unit,
    val onDismissAddDialog: () -> Unit,
    val onDeleteRequested: (BoardSummary) -> Unit,
    val onDismissDeleteDialog: () -> Unit,
    val onGlobalSettingsBack: () -> Unit,
    val onOpenCookieManagement: (() -> Unit)?,
    val onCookieManagementBack: () -> Unit
)

internal data class BoardManagementHistoryInteractionInputs(
    val coroutineScope: CoroutineScope,
    val closeDrawer: suspend () -> Unit,
    val openDrawer: suspend () -> Unit,
    val onExternalMenuAction: (BoardManagementMenuAction) -> Unit,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onHistoryRefresh: suspend () -> Unit,
    val onHistoryCleared: () -> Unit,
    val showSnackbar: suspend (String) -> Unit
)

internal data class BoardManagementStateInteractionInputs(
    val currentIsDeleteMode: () -> Boolean,
    val currentIsReorderMode: () -> Boolean,
    val currentIsHistoryRefreshing: () -> Boolean,
    val setIsDeleteMode: (Boolean) -> Unit,
    val setIsReorderMode: (Boolean) -> Unit,
    val setIsHistoryRefreshing: (Boolean) -> Unit,
    val currentIsMenuExpanded: () -> Boolean,
    val setIsMenuExpanded: (Boolean) -> Unit
)

internal data class BoardManagementOverlayInteractionInputs(
    val currentOverlayState: () -> BoardManagementOverlayState,
    val setOverlayState: (BoardManagementOverlayState) -> Unit,
    val hasCookieRepository: Boolean
)

internal data class BoardManagementBoardInteractionInputs(
    val onAddBoard: (String, String) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardSelected: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit
)

internal data class BoardManagementTopBarCallbacks(
    val onNavigationClick: () -> Unit,
    val onBackClick: () -> Unit,
    val onOpenMenu: () -> Unit,
    val onDismissMenu: () -> Unit,
    val onMenuActionSelected: (BoardManagementMenuAction) -> Unit
)

internal data class BoardManagementBoardListCallbacks(
    val onBoardClick: (BoardSummary) -> Unit,
    val onDeleteClick: (BoardSummary) -> Unit,
    val onMoveUp: (boards: List<BoardSummary>, index: Int) -> Unit,
    val onMoveDown: (boards: List<BoardSummary>, index: Int) -> Unit
)

internal fun mutateBoardManagementOverlayState(
    currentOverlayState: () -> BoardManagementOverlayState,
    setOverlayState: (BoardManagementOverlayState) -> Unit,
    transform: (BoardManagementOverlayState) -> BoardManagementOverlayState
) {
    setOverlayState(transform(currentOverlayState()))
}

internal fun buildBoardManagementInteractionBindingsBundle(
    historyInputs: BoardManagementHistoryInteractionInputs,
    stateInputs: BoardManagementStateInteractionInputs,
    overlayInputs: BoardManagementOverlayInteractionInputs,
    boardInputs: BoardManagementBoardInteractionInputs
): BoardManagementInteractionBindingsBundle {
    val historyDrawerCallbacks = buildBoardManagementHistoryDrawerCallbacks(
        coroutineScope = historyInputs.coroutineScope,
        closeDrawer = historyInputs.closeDrawer,
        onHistoryEntrySelected = historyInputs.onHistoryEntrySelected,
        onHistoryRefresh = historyInputs.onHistoryRefresh,
        onHistoryCleared = historyInputs.onHistoryCleared,
        showSnackbar = historyInputs.showSnackbar,
        currentIsHistoryRefreshing = stateInputs.currentIsHistoryRefreshing,
        setIsHistoryRefreshing = stateInputs.setIsHistoryRefreshing
    )
    val menuActionCallbacks = buildBoardManagementMenuActionCallbacks(
        coroutineScope = historyInputs.coroutineScope,
        openDrawer = historyInputs.openDrawer,
        onExternalMenuAction = historyInputs.onExternalMenuAction,
        currentIsDeleteMode = stateInputs.currentIsDeleteMode,
        currentIsReorderMode = stateInputs.currentIsReorderMode,
        setIsDeleteMode = stateInputs.setIsDeleteMode,
        setIsReorderMode = stateInputs.setIsReorderMode,
        setIsAddDialogVisible = {
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState
            ) { currentState ->
                if (it) openBoardManagementAddDialog(currentState) else dismissBoardManagementAddDialog(currentState)
            }
        },
        setIsGlobalSettingsVisible = {
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState
            ) { currentState ->
                if (it) openBoardManagementGlobalSettings(currentState) else closeBoardManagementGlobalSettings(currentState)
            }
        }
    )
    val dialogCallbacks = buildBoardManagementDialogCallbacks(
        coroutineScope = historyInputs.coroutineScope,
        onAddBoard = boardInputs.onAddBoard,
        onBoardDeleted = boardInputs.onBoardDeleted,
        setIsAddDialogVisible = {
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState
            ) { currentState ->
                if (it) openBoardManagementAddDialog(currentState) else dismissBoardManagementAddDialog(currentState)
            }
        },
        clearBoardToDelete = {
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState,
                transform = ::dismissBoardManagementDeleteDialog
            )
        },
        showSnackbar = historyInputs.showSnackbar
    )
    val topBarCallbacks = BoardManagementTopBarCallbacks(
        onNavigationClick = menuActionCallbacks.onNavigationClick,
        onBackClick = menuActionCallbacks.onBackClick,
        onOpenMenu = { stateInputs.setIsMenuExpanded(true) },
        onDismissMenu = { stateInputs.setIsMenuExpanded(false) },
        onMenuActionSelected = { action ->
            if (stateInputs.currentIsMenuExpanded()) {
                stateInputs.setIsMenuExpanded(false)
            }
            menuActionCallbacks.onMenuActionSelected(action)
        }
    )
    val boardListCallbacks = BoardManagementBoardListCallbacks(
        onBoardClick = boardInputs.onBoardSelected,
        onDeleteClick = { board ->
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState
            ) { currentState ->
                openBoardManagementDeleteDialog(currentState, board)
            }
        },
        onMoveUp = { boards, index ->
            boardInputs.onBoardsReordered(moveBoardSummary(boards, index, moveUp = true))
        },
        onMoveDown = { boards, index ->
            boardInputs.onBoardsReordered(moveBoardSummary(boards, index, moveUp = false))
        }
    )
    return BoardManagementInteractionBindingsBundle(
        historyDrawerCallbacks = historyDrawerCallbacks,
        menuActionCallbacks = menuActionCallbacks,
        dialogCallbacks = dialogCallbacks,
        topBarCallbacks = topBarCallbacks,
        boardListCallbacks = boardListCallbacks,
        onHistorySettingsClick = {
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState,
                transform = ::openBoardManagementGlobalSettings
            )
        },
        onDismissAddDialog = {
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState,
                transform = ::dismissBoardManagementAddDialog
            )
        },
        onDeleteRequested = { board ->
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState
            ) { currentState ->
                openBoardManagementDeleteDialog(currentState, board)
            }
        },
        onDismissDeleteDialog = {
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState,
                transform = ::dismissBoardManagementDeleteDialog
            )
        },
        onGlobalSettingsBack = {
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState,
                transform = ::closeBoardManagementGlobalSettings
            )
        },
        onOpenCookieManagement = if (overlayInputs.hasCookieRepository) {
            {
                mutateBoardManagementOverlayState(
                    currentOverlayState = overlayInputs.currentOverlayState,
                    setOverlayState = overlayInputs.setOverlayState,
                    transform = ::openBoardManagementCookieManagement
                )
            }
        } else {
            null
        },
        onCookieManagementBack = {
            mutateBoardManagementOverlayState(
                currentOverlayState = overlayInputs.currentOverlayState,
                setOverlayState = overlayInputs.setOverlayState,
                transform = ::closeBoardManagementCookieManagement
            )
        }
    )
}
