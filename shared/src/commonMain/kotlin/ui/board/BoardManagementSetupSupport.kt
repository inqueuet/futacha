package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.CoroutineScope

internal data class BoardManagementContentContext(
    val boards: List<BoardSummary>,
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit,
    override val screenContext: ResolvedScreenContext,
    override val services: ResolvedScreenServiceDependencies,
    val modifier: Modifier
) : ScreenContextOwner, ResolvedScreenServiceDependenciesOwner

internal fun BoardManagementScreenContentArgs.resolveContentContext(): BoardManagementContentContext {
    return BoardManagementContentContext(
        boards = boards,
        onBoardSelected = onBoardSelected,
        onAddBoard = onAddBoard,
        onMenuAction = onMenuAction,
        onBoardDeleted = onBoardDeleted,
        onBoardsReordered = onBoardsReordered,
        screenContext = screenContext,
        services = dependencies,
        modifier = modifier
    )
}

internal data class BoardManagementPreparedSetupBundle(
    val context: BoardManagementContentContext,
    val runtimeObjects: BoardManagementRuntimeObjectsBundle,
    val mutableStateRefs: BoardManagementMutableStateRefs
)

internal data class BoardManagementContextHandles(
    val boards: List<BoardSummary>,
    val history: List<ThreadHistoryEntry>,
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onHistoryRefresh: suspend () -> Unit,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryCleared: () -> Unit,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val cookieRepository: CookieRepository?,
    val fileSystem: FileSystem?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val modifier: Modifier
)

internal data class BoardManagementRuntimeHandles(
    val snackbarHostState: SnackbarHostState,
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState,
    val isDrawerOpen: Boolean
)

internal data class BoardManagementPreparedSetupHandles(
    val contextHandles: BoardManagementContextHandles,
    val runtimeHandles: BoardManagementRuntimeHandles,
    val mutableStateHandles: BoardManagementMutableStateHandles
)

internal fun resolveBoardManagementPreparedSetupHandles(
    preparedSetup: BoardManagementPreparedSetupBundle
): BoardManagementPreparedSetupHandles {
    val context = preparedSetup.context
    val runtimeObjects = preparedSetup.runtimeObjects
    return BoardManagementPreparedSetupHandles(
        contextHandles = BoardManagementContextHandles(
            boards = context.boards,
            history = context.history,
            onBoardSelected = context.onBoardSelected,
            onAddBoard = context.onAddBoard,
            onMenuAction = context.onMenuAction,
            onBoardDeleted = context.onBoardDeleted,
            onBoardsReordered = context.onBoardsReordered,
            onHistoryEntrySelected = context.onHistoryEntrySelected,
            onHistoryRefresh = context.onHistoryRefresh,
            onHistoryEntryDismissed = context.onHistoryEntryDismissed,
            onHistoryCleared = context.onHistoryCleared,
            preferencesState = context.preferencesState,
            preferencesCallbacks = context.preferencesCallbacks,
            cookieRepository = context.cookieRepository,
            fileSystem = context.fileSystem,
            autoSavedThreadRepository = context.autoSavedThreadRepository,
            modifier = context.modifier
        ),
        runtimeHandles = BoardManagementRuntimeHandles(
            snackbarHostState = runtimeObjects.snackbarHostState,
            coroutineScope = runtimeObjects.coroutineScope,
            drawerState = runtimeObjects.drawerState,
            isDrawerOpen = runtimeObjects.isDrawerOpen
        ),
        mutableStateHandles = resolveBoardManagementMutableStateHandles(preparedSetup.mutableStateRefs)
    )
}

@Composable
internal fun rememberBoardManagementPreparedSetupHandles(
    preparedSetup: BoardManagementPreparedSetupBundle
): BoardManagementPreparedSetupHandles {
    return rememberResolvedSetupHandles(
        preparedSetup = preparedSetup,
        resolver = ::resolveBoardManagementPreparedSetupHandles
    )
}

@Composable
internal fun rememberBoardManagementPreparedSetupBundle(
    args: BoardManagementScreenContentArgs
): BoardManagementPreparedSetupBundle {
    val context = args.resolveContentContext()
    val runtimeObjects = rememberBoardManagementRuntimeObjectsBundle()
    val mutableStateBundle = rememberBoardManagementMutableStateBundle()
    val mutableStateRefs = rememberResolvedStateRefs(
        bundle = mutableStateBundle,
        resolver = ::resolveBoardManagementMutableStateRefs
    )
    return remember(context, runtimeObjects, mutableStateRefs) {
        BoardManagementPreparedSetupBundle(
            context = context,
            runtimeObjects = runtimeObjects,
            mutableStateRefs = mutableStateRefs
        )
    }
}
