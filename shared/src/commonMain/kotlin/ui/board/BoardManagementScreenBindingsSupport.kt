package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem

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

internal data class BoardManagementScreenBindings(
    val scaffold: BoardManagementScaffoldBindings,
    val overlay: BoardManagementOverlayBindings
)

internal fun buildBoardManagementScreenBindings(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    isDeleteMode: Boolean,
    isReorderMode: Boolean,
    isDrawerOpen: Boolean,
    chromeState: BoardManagementChromeState,
    isMenuExpanded: Boolean,
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    onDismissDrawerTap: () -> Unit,
    interactionBindings: BoardManagementInteractionBindingsBundle,
    overlayState: BoardManagementOverlayState,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    autoSavedThreadRepository: SavedThreadRepository?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?
): BoardManagementScreenBindings {
    return BoardManagementScreenBindings(
        scaffold = BoardManagementScaffoldBindings(
            history = history,
            boards = boards,
            isDeleteMode = isDeleteMode,
            isReorderMode = isReorderMode,
            isDrawerOpen = isDrawerOpen,
            chromeState = chromeState,
            isMenuExpanded = isMenuExpanded,
            drawerState = drawerState,
            snackbarHostState = snackbarHostState,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onDismissDrawerTap = onDismissDrawerTap,
            historyDrawerCallbacks = interactionBindings.historyDrawerCallbacks,
            topBarCallbacks = interactionBindings.topBarCallbacks,
            boardListCallbacks = interactionBindings.boardListCallbacks,
            onHistorySettingsClick = interactionBindings.onHistorySettingsClick
        ),
        overlay = BoardManagementOverlayBindings(
            boards = boards,
            history = history,
            overlayState = overlayState,
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks,
            autoSavedThreadRepository = autoSavedThreadRepository,
            fileSystem = fileSystem,
            cookieRepository = cookieRepository,
            onDismissAddDialog = interactionBindings.onDismissAddDialog,
            onAddBoardSubmitted = interactionBindings.dialogCallbacks.onAddBoardSubmitted,
            onDismissDeleteDialog = interactionBindings.onDismissDeleteDialog,
            onDeleteBoardConfirmed = interactionBindings.dialogCallbacks.onDeleteBoardConfirmed,
            onGlobalSettingsBack = interactionBindings.onGlobalSettingsBack,
            onOpenCookieManagement = interactionBindings.onOpenCookieManagement,
            onCookieManagementBack = interactionBindings.onCookieManagementBack
        )
    )
}
