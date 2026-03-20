package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class BoardManagementScaffoldBindings(
    val history: List<ThreadHistoryEntry>,
    val boards: List<BoardSummary>,
    val isDeleteMode: Boolean,
    val isReorderMode: Boolean,
    val isDrawerOpen: Boolean,
    val chromeState: BoardManagementChromeState,
    val isMenuExpanded: Boolean,
    val drawerState: DrawerState,
    val snackbarHostState: SnackbarHostState,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onDismissDrawerTap: () -> Unit,
    val historyDrawerCallbacks: BoardManagementHistoryDrawerCallbacks,
    val topBarCallbacks: BoardManagementTopBarCallbacks,
    val boardListCallbacks: BoardManagementBoardListCallbacks,
    val onHistorySettingsClick: () -> Unit
)

internal data class BoardManagementOverlayBindings(
    val boards: List<BoardSummary>,
    val history: List<ThreadHistoryEntry>,
    val overlayState: BoardManagementOverlayState,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val fileSystem: FileSystem?,
    val cookieRepository: CookieRepository?,
    val onDismissAddDialog: () -> Unit,
    val onAddBoardSubmitted: (String, String) -> Unit,
    val onDismissDeleteDialog: () -> Unit,
    val onDeleteBoardConfirmed: (BoardSummary) -> Unit,
    val onGlobalSettingsBack: () -> Unit,
    val onOpenCookieManagement: (() -> Unit)?,
    val onCookieManagementBack: () -> Unit
)

internal data class BoardManagementScreenWiringInputs(
    val boards: List<BoardSummary>,
    val history: List<ThreadHistoryEntry>,
    val drawerState: DrawerState,
    val snackbarHostState: SnackbarHostState,
    val coroutineScope: CoroutineScope,
    val isDrawerOpen: Boolean,
    val isDeleteMode: Boolean,
    val isReorderMode: Boolean,
    val isMenuExpanded: Boolean,
    val overlayState: BoardManagementOverlayState,
    val isHistoryRefreshing: Boolean,
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onHistoryRefresh: suspend () -> Unit,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryCleared: () -> Unit,
    val setIsDeleteMode: (Boolean) -> Unit,
    val setIsReorderMode: (Boolean) -> Unit,
    val setIsHistoryRefreshing: (Boolean) -> Unit,
    val setIsMenuExpanded: (Boolean) -> Unit,
    val setOverlayState: (BoardManagementOverlayState) -> Unit,
    val chromeState: BoardManagementChromeState,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val fileSystem: FileSystem?,
    val cookieRepository: CookieRepository?
)

internal data class BoardManagementScreenWiringBundle(
    val interactionBindings: BoardManagementInteractionBindingsBundle,
    val lifecycleBindings: BoardManagementLifecycleBindings,
    val scaffoldBindings: BoardManagementScaffoldBindings,
    val overlayBindings: BoardManagementOverlayBindings
)

@Composable
internal fun rememberBoardManagementScreenWiringBundle(
    inputs: BoardManagementScreenWiringInputs
): BoardManagementScreenWiringBundle {
    val onHistoryEntrySelectedState by rememberUpdatedState(inputs.onHistoryEntrySelected)
    val onHistoryRefreshState by rememberUpdatedState(inputs.onHistoryRefresh)
    val onHistoryClearedState by rememberUpdatedState(inputs.onHistoryCleared)
    val interactionBindings = remember(
        inputs.drawerState,
        inputs.coroutineScope,
        inputs.snackbarHostState,
        inputs.cookieRepository,
        inputs.onMenuAction,
        inputs.onAddBoard,
        inputs.onBoardDeleted,
        inputs.onBoardSelected,
        inputs.onBoardsReordered
    ) {
        buildBoardManagementInteractionBindingsBundle(
            historyInputs = BoardManagementHistoryInteractionInputs(
                coroutineScope = inputs.coroutineScope,
                closeDrawer = { inputs.drawerState.close() },
                openDrawer = { inputs.drawerState.open() },
                onExternalMenuAction = inputs.onMenuAction,
                onHistoryEntrySelected = { onHistoryEntrySelectedState(it) },
                onHistoryRefresh = { onHistoryRefreshState() },
                onHistoryCleared = { onHistoryClearedState() },
                showSnackbar = inputs.snackbarHostState::showSnackbar
            ),
            stateInputs = BoardManagementStateInteractionInputs(
                currentIsDeleteMode = { inputs.isDeleteMode },
                currentIsReorderMode = { inputs.isReorderMode },
                currentIsHistoryRefreshing = { inputs.isHistoryRefreshing },
                setIsDeleteMode = inputs.setIsDeleteMode,
                setIsReorderMode = inputs.setIsReorderMode,
                setIsHistoryRefreshing = inputs.setIsHistoryRefreshing,
                currentIsMenuExpanded = { inputs.isMenuExpanded },
                setIsMenuExpanded = inputs.setIsMenuExpanded
            ),
            overlayInputs = BoardManagementOverlayInteractionInputs(
                currentOverlayState = { inputs.overlayState },
                setOverlayState = inputs.setOverlayState,
                hasCookieRepository = inputs.cookieRepository != null
            ),
            boardInputs = BoardManagementBoardInteractionInputs(
                onAddBoard = inputs.onAddBoard,
                onBoardDeleted = inputs.onBoardDeleted,
                onBoardSelected = inputs.onBoardSelected,
                onBoardsReordered = inputs.onBoardsReordered
            )
        )
    }
    val lifecycleBindings = remember(
        inputs.drawerState,
        inputs.coroutineScope,
        inputs.isDrawerOpen,
        inputs.isDeleteMode,
        inputs.isReorderMode
    ) {
        buildBoardManagementLifecycleBindings(
            coroutineScope = inputs.coroutineScope,
            currentBackAction = {
                resolveBoardManagementBackAction(
                    isDrawerOpen = inputs.isDrawerOpen,
                    isDeleteMode = inputs.isDeleteMode,
                    isReorderMode = inputs.isReorderMode
                )
            },
            closeDrawer = { inputs.drawerState.close() },
            clearEditModes = {
                val clearedState = clearBoardManagementEditModes()
                inputs.setIsDeleteMode(clearedState.isDeleteMode)
                inputs.setIsReorderMode(clearedState.isReorderMode)
            }
        )
    }
    val scaffoldBindings = remember(
        inputs.history,
        inputs.boards,
        inputs.isDeleteMode,
        inputs.isReorderMode,
        inputs.isDrawerOpen,
        inputs.chromeState,
        inputs.isMenuExpanded,
        inputs.drawerState,
        inputs.snackbarHostState,
        inputs.onHistoryEntryDismissed,
        interactionBindings
    ) {
        BoardManagementScaffoldBindings(
            history = inputs.history,
            boards = inputs.boards,
            isDeleteMode = inputs.isDeleteMode,
            isReorderMode = inputs.isReorderMode,
            isDrawerOpen = inputs.isDrawerOpen,
            chromeState = inputs.chromeState,
            isMenuExpanded = inputs.isMenuExpanded,
            drawerState = inputs.drawerState,
            snackbarHostState = inputs.snackbarHostState,
            onHistoryEntryDismissed = inputs.onHistoryEntryDismissed,
            onDismissDrawerTap = { inputs.coroutineScope.launch { inputs.drawerState.close() } },
            historyDrawerCallbacks = interactionBindings.historyDrawerCallbacks,
            topBarCallbacks = interactionBindings.topBarCallbacks,
            boardListCallbacks = interactionBindings.boardListCallbacks,
            onHistorySettingsClick = interactionBindings.onHistorySettingsClick
        )
    }
    val overlayBindings = remember(
        inputs.boards,
        inputs.history,
        inputs.overlayState,
        inputs.preferencesState,
        inputs.preferencesCallbacks,
        inputs.autoSavedThreadRepository,
        inputs.fileSystem,
        inputs.cookieRepository,
        interactionBindings
    ) {
        BoardManagementOverlayBindings(
            boards = inputs.boards,
            history = inputs.history,
            overlayState = inputs.overlayState,
            preferencesState = inputs.preferencesState,
            preferencesCallbacks = inputs.preferencesCallbacks,
            autoSavedThreadRepository = inputs.autoSavedThreadRepository,
            fileSystem = inputs.fileSystem,
            cookieRepository = inputs.cookieRepository,
            onDismissAddDialog = interactionBindings.onDismissAddDialog,
            onAddBoardSubmitted = interactionBindings.dialogCallbacks.onAddBoardSubmitted,
            onDismissDeleteDialog = interactionBindings.onDismissDeleteDialog,
            onDeleteBoardConfirmed = interactionBindings.dialogCallbacks.onDeleteBoardConfirmed,
            onGlobalSettingsBack = interactionBindings.onGlobalSettingsBack,
            onOpenCookieManagement = interactionBindings.onOpenCookieManagement,
            onCookieManagementBack = interactionBindings.onCookieManagementBack
        )
    }
    return remember(
        interactionBindings,
        lifecycleBindings,
        scaffoldBindings,
        overlayBindings
    ) {
        BoardManagementScreenWiringBundle(
            interactionBindings = interactionBindings,
            lifecycleBindings = lifecycleBindings,
            scaffoldBindings = scaffoldBindings,
            overlayBindings = overlayBindings
        )
    }
}
