package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun BoardManagementScreen(
    boards: List<BoardSummary>,
    screenContract: ScreenContract,
    onBoardSelected: (BoardSummary) -> Unit,
    onAddBoard: (String, String) -> Unit,
    onMenuAction: (BoardManagementMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    onBoardDeleted: (BoardSummary) -> Unit = {},
    onBoardsReordered: (List<BoardSummary>) -> Unit = {},
    dependencies: BoardManagementScreenDependencies = BoardManagementScreenDependencies()
) {
    BoardManagementScreen(
        boards = boards,
        history = screenContract.history,
        onBoardSelected = onBoardSelected,
        onAddBoard = onAddBoard,
        onMenuAction = onMenuAction,
        historyCallbacks = screenContract.historyCallbacks,
        modifier = modifier,
        onBoardDeleted = onBoardDeleted,
        onBoardsReordered = onBoardsReordered,
        dependencies = dependencies,
        preferencesState = screenContract.preferencesState,
        preferencesCallbacks = screenContract.preferencesCallbacks
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun BoardManagementScreen(
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
    cookieRepository: CookieRepository? = dependencies.cookieRepository,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks(),
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = dependencies.fileSystem,
    autoSavedThreadRepository: SavedThreadRepository? = dependencies.autoSavedThreadRepository,
) {
    val runtimeObjects = rememberBoardManagementRuntimeObjectsBundle()
    val mutableStateBundle = rememberBoardManagementMutableStateBundle()
    val snackbarHostState = runtimeObjects.snackbarHostState
    val scope = runtimeObjects.coroutineScope
    val drawerState = runtimeObjects.drawerState
    val isDrawerOpen = runtimeObjects.isDrawerOpen
    var isMenuExpanded by mutableStateBundle.isMenuExpanded
    var isDeleteMode by mutableStateBundle.isDeleteMode
    var isReorderMode by mutableStateBundle.isReorderMode
    var overlayState by mutableStateBundle.overlayState
    var isHistoryRefreshing by mutableStateBundle.isHistoryRefreshing
    val chromeState = resolveBoardManagementChromeState(
        isDeleteMode = isDeleteMode,
        isReorderMode = isReorderMode
    )
    val onHistoryEntrySelectedState = rememberUpdatedState(onHistoryEntrySelected)
    val onHistoryRefreshState = rememberUpdatedState(onHistoryRefresh)
    val onHistoryClearedState = rememberUpdatedState(onHistoryCleared)
    val interactionBindings = remember(
        drawerState,
        scope,
        snackbarHostState,
        cookieRepository,
        onMenuAction,
        onAddBoard,
        onBoardDeleted
    ) {
        buildBoardManagementInteractionBindingsBundle(
            historyInputs = BoardManagementHistoryInteractionInputs(
                coroutineScope = scope,
                closeDrawer = { drawerState.close() },
                openDrawer = { drawerState.open() },
                onExternalMenuAction = onMenuAction,
                onHistoryEntrySelected = { onHistoryEntrySelectedState.value(it) },
                onHistoryRefresh = { onHistoryRefreshState.value() },
                onHistoryCleared = { onHistoryClearedState.value() },
                showSnackbar = snackbarHostState::showSnackbar
            ),
            stateInputs = BoardManagementStateInteractionInputs(
                currentIsDeleteMode = { isDeleteMode },
                currentIsReorderMode = { isReorderMode },
                currentIsHistoryRefreshing = { isHistoryRefreshing },
                setIsDeleteMode = { isDeleteMode = it },
                setIsReorderMode = { isReorderMode = it },
                setIsHistoryRefreshing = { isHistoryRefreshing = it },
                currentIsMenuExpanded = { isMenuExpanded },
                setIsMenuExpanded = { isMenuExpanded = it }
            ),
            overlayInputs = BoardManagementOverlayInteractionInputs(
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                hasCookieRepository = cookieRepository != null
            ),
            boardInputs = BoardManagementBoardInteractionInputs(
                onAddBoard = onAddBoard,
                onBoardDeleted = onBoardDeleted,
                onBoardSelected = onBoardSelected,
                onBoardsReordered = onBoardsReordered
            )
        )
    }
    val lifecycleBindings = remember(drawerState, scope, isDrawerOpen, isDeleteMode, isReorderMode) {
        buildBoardManagementLifecycleBindings(
            coroutineScope = scope,
            currentBackAction = {
                resolveBoardManagementBackAction(
                    isDrawerOpen = isDrawerOpen,
                    isDeleteMode = isDeleteMode,
                    isReorderMode = isReorderMode
                )
            },
            closeDrawer = { drawerState.close() },
            clearEditModes = {
                val clearedState = clearBoardManagementEditModes()
                isDeleteMode = clearedState.isDeleteMode
                isReorderMode = clearedState.isReorderMode
            }
        )
    }
    val scaffoldBindings = BoardManagementScaffoldBindings(
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
        onDismissDrawerTap = { scope.launch { drawerState.close() } },
        historyDrawerCallbacks = interactionBindings.historyDrawerCallbacks,
        topBarCallbacks = interactionBindings.topBarCallbacks,
        boardListCallbacks = interactionBindings.boardListCallbacks,
        onHistorySettingsClick = interactionBindings.onHistorySettingsClick
    )
    val overlayBindings = BoardManagementOverlayBindings(
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

    PlatformBackHandler(enabled = lifecycleBindings.backAction != BoardManagementBackAction.NONE) {
        lifecycleBindings.onBack()
    }

    BoardManagementScaffold(bindings = scaffoldBindings, modifier = modifier)

    BoardManagementOverlayHost(bindings = overlayBindings)
}
