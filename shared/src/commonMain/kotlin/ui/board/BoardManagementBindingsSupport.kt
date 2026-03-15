package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.service.HistoryRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun buildBoardManagementHistoryRefreshSuccessMessage(): String = "履歴を更新しました"

internal fun buildBoardManagementHistoryRefreshBusyMessage(): String = "履歴更新はすでに実行中です"

internal fun buildBoardManagementHistoryRefreshFailureMessage(error: Throwable): String {
    return "履歴の更新に失敗しました: ${error.message ?: "不明なエラー"}"
}

internal fun buildBoardManagementHistoryBatchDeleteMessage(): String = "履歴を一括削除しました"

internal fun buildBoardManagementAddBoardSuccessMessage(name: String): String = "\"$name\" を追加しました"

internal fun buildBoardManagementDeleteBoardSuccessMessage(board: BoardSummary): String {
    return "\"${board.name}\" を削除しました"
}

internal data class BoardManagementHistoryDrawerCallbacks(
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onBoardClick: () -> Unit,
    val onRefreshClick: () -> Unit,
    val onBatchDeleteClick: () -> Unit
)

internal fun buildBoardManagementHistoryDrawerCallbacks(
    coroutineScope: CoroutineScope,
    closeDrawer: suspend () -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onHistoryRefresh: suspend () -> Unit,
    onHistoryCleared: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
    currentIsHistoryRefreshing: () -> Boolean,
    setIsHistoryRefreshing: (Boolean) -> Unit
): BoardManagementHistoryDrawerCallbacks {
    return BoardManagementHistoryDrawerCallbacks(
        onHistoryEntrySelected = { entry ->
            coroutineScope.launch { closeDrawer() }
            onHistoryEntrySelected(entry)
        },
        onBoardClick = {
            coroutineScope.launch { closeDrawer() }
        },
        onRefreshClick = refresh@{
            if (currentIsHistoryRefreshing()) return@refresh
            setIsHistoryRefreshing(true)
            coroutineScope.launch {
                try {
                    onHistoryRefresh()
                    showSnackbar(buildBoardManagementHistoryRefreshSuccessMessage())
                } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
                    showSnackbar(buildBoardManagementHistoryRefreshBusyMessage())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showSnackbar(buildBoardManagementHistoryRefreshFailureMessage(e))
                } finally {
                    setIsHistoryRefreshing(false)
                }
            }
        },
        onBatchDeleteClick = {
            coroutineScope.launch {
                onHistoryCleared()
                showSnackbar(buildBoardManagementHistoryBatchDeleteMessage())
                closeDrawer()
            }
        }
    )
}

internal data class BoardManagementMenuActionCallbacks(
    val onBackClick: () -> Unit,
    val onNavigationClick: () -> Unit,
    val onMenuActionSelected: (BoardManagementMenuAction) -> Unit
)

internal fun buildBoardManagementMenuActionCallbacks(
    coroutineScope: CoroutineScope,
    openDrawer: suspend () -> Unit,
    onExternalMenuAction: (BoardManagementMenuAction) -> Unit,
    currentIsDeleteMode: () -> Boolean,
    currentIsReorderMode: () -> Boolean,
    setIsDeleteMode: (Boolean) -> Unit,
    setIsReorderMode: (Boolean) -> Unit,
    setIsAddDialogVisible: (Boolean) -> Unit,
    setIsGlobalSettingsVisible: (Boolean) -> Unit
): BoardManagementMenuActionCallbacks {
    return BoardManagementMenuActionCallbacks(
        onBackClick = {
            val clearedState = clearBoardManagementEditModes()
            setIsDeleteMode(clearedState.isDeleteMode)
            setIsReorderMode(clearedState.isReorderMode)
        },
        onNavigationClick = {
            coroutineScope.launch { openDrawer() }
        },
        onMenuActionSelected = actionSelection@{ action ->
            onExternalMenuAction(action)
            val actionState = resolveBoardManagementMenuActionState(
                isDeleteMode = currentIsDeleteMode(),
                isReorderMode = currentIsReorderMode(),
                action = action
            )
            if (!actionState.shouldHandleInternally) {
                return@actionSelection
            }
            setIsDeleteMode(actionState.editModeState.isDeleteMode)
            setIsReorderMode(actionState.editModeState.isReorderMode)
            if (actionState.shouldShowAddDialog) {
                setIsAddDialogVisible(true)
            }
            if (actionState.shouldShowGlobalSettings) {
                setIsGlobalSettingsVisible(true)
            }
        }
    )
}

internal data class BoardManagementDialogCallbacks(
    val onAddBoardSubmitted: (String, String) -> Unit,
    val onDeleteBoardConfirmed: (BoardSummary) -> Unit
)

internal fun buildBoardManagementDialogCallbacks(
    coroutineScope: CoroutineScope,
    onAddBoard: (String, String) -> Unit,
    onBoardDeleted: (BoardSummary) -> Unit,
    setIsAddDialogVisible: (Boolean) -> Unit,
    clearBoardToDelete: () -> Unit,
    showSnackbar: suspend (String) -> Unit
): BoardManagementDialogCallbacks {
    return BoardManagementDialogCallbacks(
        onAddBoardSubmitted = { name, url ->
            onAddBoard(name, url)
            setIsAddDialogVisible(false)
            coroutineScope.launch {
                showSnackbar(buildBoardManagementAddBoardSuccessMessage(name))
            }
        },
        onDeleteBoardConfirmed = { board ->
            onBoardDeleted(board)
            clearBoardToDelete()
            coroutineScope.launch {
                showSnackbar(buildBoardManagementDeleteBoardSuccessMessage(board))
            }
        }
    )
}
