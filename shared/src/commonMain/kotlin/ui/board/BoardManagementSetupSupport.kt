package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem

internal data class BoardManagementContentContext(
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

internal fun BoardManagementScreenContentArgs.resolveContentContext(): BoardManagementContentContext {
    return BoardManagementContentContext(
        boards = boards,
        history = history,
        onBoardSelected = onBoardSelected,
        onAddBoard = onAddBoard,
        onMenuAction = onMenuAction,
        onBoardDeleted = onBoardDeleted,
        onBoardsReordered = onBoardsReordered,
        onHistoryEntrySelected = historyCallbacks.onHistoryEntrySelected,
        onHistoryRefresh = historyCallbacks.onHistoryRefresh,
        onHistoryEntryDismissed = historyCallbacks.onHistoryEntryDismissed,
        onHistoryCleared = historyCallbacks.onHistoryCleared,
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
        cookieRepository = dependencies.cookieRepository,
        fileSystem = dependencies.fileSystem,
        autoSavedThreadRepository = dependencies.autoSavedThreadRepository,
        modifier = modifier
    )
}

internal data class BoardManagementPreparedSetupBundle(
    val context: BoardManagementContentContext,
    val runtimeObjects: BoardManagementRuntimeObjectsBundle,
    val mutableStateRefs: BoardManagementMutableStateRefs
)

@Composable
internal fun rememberBoardManagementPreparedSetupBundle(
    args: BoardManagementScreenContentArgs
): BoardManagementPreparedSetupBundle {
    val context = args.resolveContentContext()
    val runtimeObjects = rememberBoardManagementRuntimeObjectsBundle()
    val mutableStateBundle = rememberBoardManagementMutableStateBundle()
    val mutableStateRefs = remember(mutableStateBundle) {
        resolveBoardManagementMutableStateRefs(mutableStateBundle)
    }
    return remember(context, runtimeObjects, mutableStateRefs) {
        BoardManagementPreparedSetupBundle(
            context = context,
            runtimeObjects = runtimeObjects,
            mutableStateRefs = mutableStateRefs
        )
    }
}
