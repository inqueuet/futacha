package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

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
    val onHistoryExport: suspend () -> String = { "" },
    val onHistoryExportThenClear: suspend () -> String = { "" },
    val onHistoryExportSelected: suspend (List<ThreadHistoryEntry>) -> String = { "" },
    val onHistoryLoadImportPreview: suspend () -> com.valoser.futacha.shared.ui.FutachaHistoryArchivePreview? = { null },
    val onHistoryImport: suspend () -> String = { "" },
    val onHistoryImportSelected: suspend (Set<String>) -> String = { "" },
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
    val onPinClick: (boards: List<BoardSummary>, index: Int) -> Unit,
    val onMoveUp: (boards: List<BoardSummary>, index: Int) -> Unit,
    val onMoveDown: (boards: List<BoardSummary>, index: Int) -> Unit,
    val onRename: (boards: List<BoardSummary>, boardId: String, name: String) -> Unit = { _, _, _ -> },
    // Persists a whole new order at once (the end of a drag).
    val onReorder: (boards: List<BoardSummary>) -> Unit = {}
)

/**
 * A board list produced by one edit on the list the screen showed, carrying the
 * edit itself. Rapid ↑↓, pin and rename taps reach the store before the screen
 * shows the previous result; the store re-applies [apply] to its latest list
 * ([applyBoardListUpdate]) so no earlier edit is overwritten by a stale list.
 */
internal class BoardListEdit(
    preview: List<BoardSummary>,
    val apply: (List<BoardSummary>) -> List<BoardSummary>
) : List<BoardSummary> by preview

internal fun boardListEdit(
    shown: List<BoardSummary>,
    apply: (List<BoardSummary>) -> List<BoardSummary>
): BoardListEdit = BoardListEdit(apply(shown), apply)

/** The list to store for [update] given the store's [latest] list. */
internal fun applyBoardListUpdate(latest: List<BoardSummary>, update: List<BoardSummary>): List<BoardSummary> =
    if (update is BoardListEdit) update.apply(latest) else update

/**
 * A dragged order applied to the latest list: boards keep their latest name and
 * pin, a board deleted meanwhile stays deleted and one added meanwhile is kept
 * at the end.
 */
internal fun reorderBoardSummariesLike(latest: List<BoardSummary>, order: List<BoardSummary>): List<BoardSummary> {
    val position = order.withIndex().associate { (index, board) -> board.id to index }
    val (ordered, added) = latest.partition { it.id in position }
    return ordered.sortedBy { position.getValue(it.id) } + added
}

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
        onHistoryExport = historyInputs.onHistoryExport,
        onHistoryExportThenClear = historyInputs.onHistoryExportThenClear,
        onHistoryExportSelected = historyInputs.onHistoryExportSelected,
        onHistoryLoadImportPreview = historyInputs.onHistoryLoadImportPreview,
        onHistoryImport = historyInputs.onHistoryImport,
        onHistoryImportSelected = historyInputs.onHistoryImportSelected,
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
            stateInputs.setIsMenuExpanded(false)
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
        onPinClick = { boards, index ->
            boards.getOrNull(index)?.id?.let { boardId ->
                boardInputs.onBoardsReordered(boardListEdit(boards) { latest ->
                    toggleBoardSummaryPinned(latest, latest.indexOfFirst { it.id == boardId })
                })
            }
        },
        onMoveUp = { boards, index ->
            boards.getOrNull(index)?.id?.let { boardId ->
                boardInputs.onBoardsReordered(boardListEdit(boards) { latest ->
                    moveBoardSummary(latest, latest.indexOfFirst { it.id == boardId }, moveUp = true)
                })
            }
        },
        onMoveDown = { boards, index ->
            boards.getOrNull(index)?.id?.let { boardId ->
                boardInputs.onBoardsReordered(boardListEdit(boards) { latest ->
                    moveBoardSummary(latest, latest.indexOfFirst { it.id == boardId }, moveUp = false)
                })
            }
        },
        onRename = { boards, boardId, name ->
            boardInputs.onBoardsReordered(boardListEdit(boards) { latest -> renameBoardSummary(latest, boardId, name) })
        },
        onReorder = { order ->
            boardInputs.onBoardsReordered(boardListEdit(order) { latest -> reorderBoardSummariesLike(latest, order) })
        }
    )
    return BoardManagementInteractionBindingsBundle(
        historyDrawerCallbacks = historyDrawerCallbacks,
        menuActionCallbacks = menuActionCallbacks,
        dialogCallbacks = dialogCallbacks,
        topBarCallbacks = topBarCallbacks,
        boardListCallbacks = boardListCallbacks,
        onHistorySettingsClick = {
            historyInputs.coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                historyInputs.closeDrawer()
                mutateBoardManagementOverlayState(
                    currentOverlayState = overlayInputs.currentOverlayState,
                    setOverlayState = overlayInputs.setOverlayState,
                    transform = ::openBoardManagementGlobalSettings
                )
            }
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
