package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.SaveDirectorySelection

@Composable
internal fun AddBoardDialog(
    existingBoards: List<BoardSummary>,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val validationState = remember(name, url, existingBoards) {
        buildAddBoardValidationState(
            name = name,
            url = url,
            existingBoards = existingBoards
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "板を追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("板の名前") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = !validationState.hasName && name.isNotEmpty()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("板のURL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = validationState.hasUrl && (!validationState.isValidUrl || validationState.isDuplicateUrl)
                )
                validationState.helperText?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validationState.canSubmit,
                onClick = {
                    onSubmit(validationState.trimmedName, validationState.normalizedInputUrl)
                    name = ""
                    url = ""
                }
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardManagementTopBar(
    chromeState: BoardManagementChromeState,
    isMenuExpanded: Boolean,
    topBarCallbacks: BoardManagementTopBarCallbacks
) {
    CenterAlignedTopAppBar(
        navigationIcon = {
            if (chromeState.showsBackButton) {
                IconButton(onClick = topBarCallbacks.onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "戻る"
                    )
                }
            } else {
                IconButton(onClick = topBarCallbacks.onNavigationClick) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "履歴を開く"
                    )
                }
            }
        },
        title = {
            Text(chromeState.title)
        },
        actions = {
            IconButton(onClick = topBarCallbacks.onOpenMenu) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "メニュー"
                )
            }
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = topBarCallbacks.onDismissMenu
            ) {
                BoardManagementMenuAction.entries.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = { topBarCallbacks.onMenuActionSelected(action) }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
internal fun BoardManagementBoardList(
    boards: List<BoardSummary>,
    isDeleteMode: Boolean,
    isReorderMode: Boolean,
    isDrawerOpen: Boolean,
    onDismissDrawerTap: () -> Unit,
    boardListCallbacks: BoardManagementBoardListCallbacks,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp)
) {
    LazyColumn(
        modifier = modifier.pointerInput(isDrawerOpen) {
            if (!isDrawerOpen) return@pointerInput
            awaitPointerEventScope {
                awaitFirstDown()
                onDismissDrawerTap()
            }
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        itemsIndexed(
            items = boards,
            key = { _, board -> board.id }
        ) { index, board ->
            when {
                isDeleteMode -> {
                    BoardSummaryCardWithDelete(
                        board = board,
                        onDelete = { boardListCallbacks.onDeleteClick(board) }
                    )
                }
                isReorderMode -> {
                    BoardSummaryCardWithReorder(
                        board = board,
                        onMoveUp = { boardListCallbacks.onMoveUp(boards, index) },
                        onMoveDown = { boardListCallbacks.onMoveDown(boards, index) },
                        canMoveUp = index > 0,
                        canMoveDown = index < boards.size - 1
                    )
                }
                else -> {
                    BoardSummaryCard(
                        board = board,
                        onClick = { boardListCallbacks.onBoardClick(board) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoardManagementScaffold(
    bindings: BoardManagementScaffoldBindings,
    modifier: Modifier = Modifier
) {
    ModalNavigationDrawer(
        drawerState = bindings.drawerState,
        gesturesEnabled = true,
        drawerContent = {
            HistoryDrawerContent(
                history = bindings.history,
                onHistoryEntryDismissed = bindings.onHistoryEntryDismissed,
                onHistoryEntrySelected = bindings.historyDrawerCallbacks.onHistoryEntrySelected,
                onBoardClick = bindings.historyDrawerCallbacks.onBoardClick,
                onRefreshClick = bindings.historyDrawerCallbacks.onRefreshClick,
                onBatchDeleteClick = bindings.historyDrawerCallbacks.onBatchDeleteClick,
                onSettingsClick = bindings.onHistorySettingsClick
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(bindings.snackbarHostState) },
            topBar = {
                BoardManagementTopBar(
                    chromeState = bindings.chromeState,
                    isMenuExpanded = bindings.isMenuExpanded,
                    topBarCallbacks = bindings.topBarCallbacks
                )
            }
        ) { innerPadding ->
            BoardManagementBoardList(
                boards = bindings.boards,
                isDeleteMode = bindings.isDeleteMode,
                isReorderMode = bindings.isReorderMode,
                isDrawerOpen = bindings.isDrawerOpen,
                onDismissDrawerTap = bindings.onDismissDrawerTap,
                boardListCallbacks = bindings.boardListCallbacks,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
internal fun BoardManagementOverlayHost(
    bindings: BoardManagementOverlayBindings
) {
    if (bindings.overlayState.isAddDialogVisible) {
        AddBoardDialog(
            existingBoards = bindings.boards,
            onDismiss = bindings.onDismissAddDialog,
            onSubmit = bindings.onAddBoardSubmitted
        )
    }

    bindings.overlayState.boardToDelete?.let { board ->
        DeleteBoardDialog(
            board = board,
            onDismiss = bindings.onDismissDeleteDialog,
            onConfirm = { bindings.onDeleteBoardConfirmed(board) }
        )
    }

    if (bindings.overlayState.isGlobalSettingsVisible) {
        GlobalSettingsScreen(
            onBack = bindings.onGlobalSettingsBack,
            preferencesState = bindings.preferencesState,
            preferencesCallbacks = bindings.preferencesCallbacks,
            onOpenCookieManager = bindings.onOpenCookieManagement,
            historyEntries = bindings.history,
            fileSystem = bindings.fileSystem,
            autoSavedThreadRepository = bindings.autoSavedThreadRepository
        )
    }

    if (bindings.overlayState.isCookieManagementVisible && bindings.cookieRepository != null) {
        CookieManagementScreen(
            onBack = bindings.onCookieManagementBack,
            repository = bindings.cookieRepository
        )
    }
}

@Composable
internal fun BoardSummaryCard(
    board: BoardSummary,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        BoardSummaryCardContent(
            board = board,
            leadingContent = { BoardSummaryLeadingIcon(board = board) }
        )
    }
}

@Composable
internal fun DeleteBoardDialog(
    board: BoardSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("板を削除") },
        text = {
            Text("「${board.name}」を削除してもよろしいですか？")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
internal fun BoardSummaryCardWithDelete(
    board: BoardSummary,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        BoardSummaryCardContent(
            board = board,
            leadingContent = { BoardSummaryLeadingIcon(board = board) },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "削除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }
}

@Composable
internal fun BoardSummaryCardWithReorder(
    board: BoardSummary,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        BoardSummaryCardContent(
            board = board,
            leadingContent = { BoardSummaryLeadingIcon(board = board) },
            trailingContent = {
                Column {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "上へ移動"
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = "下へ移動"
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun BoardSummaryCardContent(
    board: BoardSummary,
    leadingContent: @Composable () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leadingContent()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = board.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = board.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun BoardSummaryLeadingIcon(board: BoardSummary) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = if (board.pinned) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (board.pinned) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (board.pinned) {
                    Icons.Outlined.PushPin
                } else {
                    Icons.Outlined.Folder
                },
                contentDescription = null
            )
        }
    }
}
