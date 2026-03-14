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
