package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class BoardManagementOverlayState(
    val isAddDialogVisible: Boolean = false,
    val boardToDelete: BoardSummary? = null,
    val isGlobalSettingsVisible: Boolean = false,
    val isCookieManagementVisible: Boolean = false
)

internal data class BoardManagementMutableStateBundle(
    val isMenuExpanded: MutableState<Boolean>,
    val isDeleteMode: MutableState<Boolean>,
    val isReorderMode: MutableState<Boolean>,
    val overlayState: MutableState<BoardManagementOverlayState>,
    val isHistoryRefreshing: MutableState<Boolean>
)

@Composable
internal fun rememberBoardManagementMutableStateBundle(): BoardManagementMutableStateBundle {
    return BoardManagementMutableStateBundle(
        isMenuExpanded = remember { mutableStateOf(false) },
        isDeleteMode = rememberSaveable { mutableStateOf(false) },
        isReorderMode = rememberSaveable { mutableStateOf(false) },
        overlayState = remember { mutableStateOf(BoardManagementOverlayState()) },
        isHistoryRefreshing = remember { mutableStateOf(false) }
    )
}

internal fun openBoardManagementAddDialog(
    currentState: BoardManagementOverlayState
): BoardManagementOverlayState {
    return currentState.copy(isAddDialogVisible = true)
}

internal fun dismissBoardManagementAddDialog(
    currentState: BoardManagementOverlayState
): BoardManagementOverlayState {
    return currentState.copy(isAddDialogVisible = false)
}

internal fun openBoardManagementDeleteDialog(
    currentState: BoardManagementOverlayState,
    board: BoardSummary
): BoardManagementOverlayState {
    return currentState.copy(boardToDelete = board)
}

internal fun dismissBoardManagementDeleteDialog(
    currentState: BoardManagementOverlayState
): BoardManagementOverlayState {
    return currentState.copy(boardToDelete = null)
}

internal fun openBoardManagementGlobalSettings(
    currentState: BoardManagementOverlayState
): BoardManagementOverlayState {
    return currentState.copy(isGlobalSettingsVisible = true)
}

internal fun closeBoardManagementGlobalSettings(
    currentState: BoardManagementOverlayState
): BoardManagementOverlayState {
    return currentState.copy(isGlobalSettingsVisible = false)
}

internal fun openBoardManagementCookieManagement(
    currentState: BoardManagementOverlayState
): BoardManagementOverlayState {
    return currentState.copy(
        isGlobalSettingsVisible = false,
        isCookieManagementVisible = true
    )
}

internal fun closeBoardManagementCookieManagement(
    currentState: BoardManagementOverlayState
): BoardManagementOverlayState {
    return currentState.copy(isCookieManagementVisible = false)
}

internal data class BoardManagementRuntimeObjectsBundle(
    val snackbarHostState: SnackbarHostState,
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState,
    val isDrawerOpen: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberBoardManagementRuntimeObjectsBundle(): BoardManagementRuntimeObjectsBundle {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerOpen by remember {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
        }
    }
    return BoardManagementRuntimeObjectsBundle(
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        drawerState = drawerState,
        isDrawerOpen = isDrawerOpen
    )
}

internal enum class BoardManagementBackAction {
    NONE,
    CLOSE_DRAWER,
    CLEAR_EDIT_MODES
}

internal fun resolveBoardManagementBackAction(
    isDrawerOpen: Boolean,
    isDeleteMode: Boolean,
    isReorderMode: Boolean
): BoardManagementBackAction {
    return when {
        isDrawerOpen -> BoardManagementBackAction.CLOSE_DRAWER
        isDeleteMode || isReorderMode -> BoardManagementBackAction.CLEAR_EDIT_MODES
        else -> BoardManagementBackAction.NONE
    }
}

internal data class BoardManagementLifecycleBindings(
    val backAction: BoardManagementBackAction,
    val onBack: () -> Unit
)

internal fun buildBoardManagementLifecycleBindings(
    coroutineScope: CoroutineScope,
    currentBackAction: () -> BoardManagementBackAction,
    closeDrawer: suspend () -> Unit,
    clearEditModes: () -> Unit
): BoardManagementLifecycleBindings {
    return BoardManagementLifecycleBindings(
        backAction = currentBackAction(),
        onBack = {
            when (currentBackAction()) {
                BoardManagementBackAction.CLOSE_DRAWER -> {
                    coroutineScope.launch { closeDrawer() }
                }
                BoardManagementBackAction.CLEAR_EDIT_MODES -> clearEditModes()
                BoardManagementBackAction.NONE -> Unit
            }
        }
    )
}
