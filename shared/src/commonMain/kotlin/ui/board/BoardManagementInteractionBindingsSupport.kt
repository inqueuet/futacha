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

internal fun buildBoardManagementInteractionBindingsBundle(
    coroutineScope: CoroutineScope,
    closeDrawer: suspend () -> Unit,
    openDrawer: suspend () -> Unit,
    onExternalMenuAction: (BoardManagementMenuAction) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onHistoryRefresh: suspend () -> Unit,
    onHistoryCleared: () -> Unit,
    onAddBoard: (String, String) -> Unit,
    onBoardDeleted: (BoardSummary) -> Unit,
    showSnackbar: suspend (String) -> Unit,
    currentIsDeleteMode: () -> Boolean,
    currentIsReorderMode: () -> Boolean,
    currentIsHistoryRefreshing: () -> Boolean,
    setIsDeleteMode: (Boolean) -> Unit,
    setIsReorderMode: (Boolean) -> Unit,
    setIsHistoryRefreshing: (Boolean) -> Unit,
    currentOverlayState: () -> BoardManagementOverlayState,
    setOverlayState: (BoardManagementOverlayState) -> Unit,
    hasCookieRepository: Boolean,
    currentIsMenuExpanded: () -> Boolean,
    setIsMenuExpanded: (Boolean) -> Unit,
    onBoardSelected: (BoardSummary) -> Unit,
    onBoardsReordered: (List<BoardSummary>) -> Unit
): BoardManagementInteractionBindingsBundle {
    val historyDrawerCallbacks = buildBoardManagementHistoryDrawerCallbacks(
        coroutineScope = coroutineScope,
        closeDrawer = closeDrawer,
        onHistoryEntrySelected = onHistoryEntrySelected,
        onHistoryRefresh = onHistoryRefresh,
        onHistoryCleared = onHistoryCleared,
        showSnackbar = showSnackbar,
        currentIsHistoryRefreshing = currentIsHistoryRefreshing,
        setIsHistoryRefreshing = setIsHistoryRefreshing
    )
    val menuActionCallbacks = buildBoardManagementMenuActionCallbacks(
        coroutineScope = coroutineScope,
        openDrawer = openDrawer,
        onExternalMenuAction = onExternalMenuAction,
        currentIsDeleteMode = currentIsDeleteMode,
        currentIsReorderMode = currentIsReorderMode,
        setIsDeleteMode = setIsDeleteMode,
        setIsReorderMode = setIsReorderMode,
        setIsAddDialogVisible = {
            setOverlayState(
                if (it) {
                    openBoardManagementAddDialog(currentOverlayState())
                } else {
                    dismissBoardManagementAddDialog(currentOverlayState())
                }
            )
        },
        setIsGlobalSettingsVisible = {
            setOverlayState(
                if (it) {
                    openBoardManagementGlobalSettings(currentOverlayState())
                } else {
                    closeBoardManagementGlobalSettings(currentOverlayState())
                }
            )
        }
    )
    val dialogCallbacks = buildBoardManagementDialogCallbacks(
        coroutineScope = coroutineScope,
        onAddBoard = onAddBoard,
        onBoardDeleted = onBoardDeleted,
        setIsAddDialogVisible = {
            setOverlayState(
                if (it) {
                    openBoardManagementAddDialog(currentOverlayState())
                } else {
                    dismissBoardManagementAddDialog(currentOverlayState())
                }
            )
        },
        clearBoardToDelete = {
            setOverlayState(dismissBoardManagementDeleteDialog(currentOverlayState()))
        },
        showSnackbar = showSnackbar
    )
    val topBarCallbacks = BoardManagementTopBarCallbacks(
        onNavigationClick = menuActionCallbacks.onNavigationClick,
        onBackClick = menuActionCallbacks.onBackClick,
        onOpenMenu = { setIsMenuExpanded(true) },
        onDismissMenu = { setIsMenuExpanded(false) },
        onMenuActionSelected = { action ->
            if (currentIsMenuExpanded()) {
                setIsMenuExpanded(false)
            }
            menuActionCallbacks.onMenuActionSelected(action)
        }
    )
    val boardListCallbacks = BoardManagementBoardListCallbacks(
        onBoardClick = onBoardSelected,
        onDeleteClick = { board ->
            setOverlayState(openBoardManagementDeleteDialog(currentOverlayState(), board))
        },
        onMoveUp = { boards, index ->
            onBoardsReordered(moveBoardSummary(boards, index, moveUp = true))
        },
        onMoveDown = { boards, index ->
            onBoardsReordered(moveBoardSummary(boards, index, moveUp = false))
        }
    )
    return BoardManagementInteractionBindingsBundle(
        historyDrawerCallbacks = historyDrawerCallbacks,
        menuActionCallbacks = menuActionCallbacks,
        dialogCallbacks = dialogCallbacks,
        topBarCallbacks = topBarCallbacks,
        boardListCallbacks = boardListCallbacks,
        onHistorySettingsClick = {
            setOverlayState(openBoardManagementGlobalSettings(currentOverlayState()))
        },
        onDismissAddDialog = {
            setOverlayState(dismissBoardManagementAddDialog(currentOverlayState()))
        },
        onDeleteRequested = { board ->
            setOverlayState(openBoardManagementDeleteDialog(currentOverlayState(), board))
        },
        onDismissDeleteDialog = {
            setOverlayState(dismissBoardManagementDeleteDialog(currentOverlayState()))
        },
        onGlobalSettingsBack = {
            setOverlayState(closeBoardManagementGlobalSettings(currentOverlayState()))
        },
        onOpenCookieManagement = if (hasCookieRepository) {
            {
                setOverlayState(openBoardManagementCookieManagement(currentOverlayState()))
            }
        } else {
            null
        },
        onCookieManagementBack = {
            setOverlayState(closeBoardManagementCookieManagement(currentOverlayState()))
        }
    )
}
