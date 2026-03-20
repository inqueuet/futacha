package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlin.time.ExperimentalTime

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
    BoardManagementScreenContent(
        args = buildBoardManagementScreenContentArgsFromContract(
            boards = boards,
            screenContract = screenContract,
            onBoardSelected = onBoardSelected,
            onAddBoard = onAddBoard,
            onMenuAction = onMenuAction,
            modifier = modifier,
            onBoardDeleted = onBoardDeleted,
            onBoardsReordered = onBoardsReordered,
            dependencies = dependencies
        )
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
    BoardManagementScreen(
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun BoardManagementScreenContent(
    args: BoardManagementScreenContentArgs
) {
    val preparedSetup = rememberBoardManagementPreparedSetupBundle(args)
    val setupHandles = rememberBoardManagementPreparedSetupHandles(preparedSetup)
    val contextHandles = setupHandles.contextHandles
    val boards = contextHandles.boards
    val history = contextHandles.history
    val onBoardSelected = contextHandles.onBoardSelected
    val onAddBoard = contextHandles.onAddBoard
    val onMenuAction = contextHandles.onMenuAction
    val onBoardDeleted = contextHandles.onBoardDeleted
    val onBoardsReordered = contextHandles.onBoardsReordered
    val onHistoryEntrySelected = contextHandles.onHistoryEntrySelected
    val onHistoryRefresh = contextHandles.onHistoryRefresh
    val onHistoryEntryDismissed = contextHandles.onHistoryEntryDismissed
    val onHistoryCleared = contextHandles.onHistoryCleared
    val preferencesState = contextHandles.preferencesState
    val preferencesCallbacks = contextHandles.preferencesCallbacks
    val cookieRepository = contextHandles.cookieRepository
    val fileSystem = contextHandles.fileSystem
    val autoSavedThreadRepository = contextHandles.autoSavedThreadRepository
    val modifier = contextHandles.modifier

    val runtimeHandles = setupHandles.runtimeHandles
    val mutableStateHandles = setupHandles.mutableStateHandles
    val modeStateRefs = mutableStateHandles.modeStateRefs
    val overlayStateRefs = mutableStateHandles.overlayStateRefs
    val snackbarHostState = runtimeHandles.snackbarHostState
    val scope = runtimeHandles.coroutineScope
    val drawerState = runtimeHandles.drawerState
    val isDrawerOpen = runtimeHandles.isDrawerOpen
    var isMenuExpanded by modeStateRefs.isMenuExpanded
    var isDeleteMode by modeStateRefs.isDeleteMode
    var isReorderMode by modeStateRefs.isReorderMode
    var overlayState by overlayStateRefs.overlayState
    var isHistoryRefreshing by modeStateRefs.isHistoryRefreshing
    val chromeState = resolveBoardManagementChromeState(
        isDeleteMode = isDeleteMode,
        isReorderMode = isReorderMode
    )
    val wiringBundle = rememberBoardManagementScreenWiringBundle(
        BoardManagementScreenWiringInputs(
            boards = boards,
            history = history,
            drawerState = drawerState,
            snackbarHostState = snackbarHostState,
            coroutineScope = scope,
            isDrawerOpen = isDrawerOpen,
            isDeleteMode = isDeleteMode,
            isReorderMode = isReorderMode,
            isMenuExpanded = isMenuExpanded,
            overlayState = overlayState,
            isHistoryRefreshing = isHistoryRefreshing,
            onBoardSelected = onBoardSelected,
            onAddBoard = onAddBoard,
            onMenuAction = onMenuAction,
            onBoardDeleted = onBoardDeleted,
            onBoardsReordered = onBoardsReordered,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryRefresh = onHistoryRefresh,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryCleared = onHistoryCleared,
            setIsDeleteMode = { isDeleteMode = it },
            setIsReorderMode = { isReorderMode = it },
            setIsHistoryRefreshing = { isHistoryRefreshing = it },
            setIsMenuExpanded = { isMenuExpanded = it },
            setOverlayState = { overlayState = it },
            chromeState = chromeState,
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks,
            autoSavedThreadRepository = autoSavedThreadRepository,
            fileSystem = fileSystem,
            cookieRepository = cookieRepository
        )
    )
    val lifecycleBindings = wiringBundle.lifecycleBindings
    val scaffoldBindings = wiringBundle.scaffoldBindings
    val overlayBindings = wiringBundle.overlayBindings

    PlatformBackHandler(enabled = lifecycleBindings.backAction != BoardManagementBackAction.NONE) {
        lifecycleBindings.onBack()
    }

    BoardManagementScaffold(bindings = scaffoldBindings, modifier = modifier)

    BoardManagementOverlayHost(bindings = overlayBindings)
}
