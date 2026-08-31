package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.DragHandle
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.theme.LocalFutachaChromeColors
import com.valoser.futacha.shared.ui.theme.LocalFutachaThemePalette
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.ui.compat.fetchDefaultCompatBoardsFromMenu
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

@Composable
internal fun AddBoardDialog(
    existingBoards: List<BoardSummary>,
    httpClient: HttpClient? = null,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onBulkSubmit: (List<Pair<String, String>>) -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var bulkMode by rememberSaveable { mutableStateOf(false) }
    var bulkLoading by remember { mutableStateOf(false) }
    var bulkError by remember { mutableStateOf<String?>(null) }
    val nameInputState = rememberStableTextInputState(
        text = name,
        onTextChange = { name = it.take(BOARD_MANAGEMENT_NAME_MAX_CHARS) },
        analyticsFieldLabel = "板の名前"
    )
    val urlInputState = rememberStableTextInputState(
        text = url,
        onTextChange = { url = it.take(BOARD_MANAGEMENT_URL_MAX_CHARS) },
        analyticsFieldLabel = "板のURL"
    )
    val validationState = remember(name, url, existingBoards) {
        buildAddBoardValidationState(
            name = name,
            url = url,
            existingBoards = existingBoards
        )
    }
    val focusedFieldColor = if (LocalFutachaThemePalette.current == ThemePalette.FutabaClassic) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = focusedFieldColor,
        focusedLabelColor = focusedFieldColor,
        cursorColor = focusedFieldColor
    )

    AlertDialog(
        onDismissRequest = {
            AnalyticsTracker.uiControl("board_add_dismiss", "板追加を閉じる")
            onDismiss()
        },
        title = { Text(text = "板を追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (bulkMode) {
                    Text(
                        "未登録の板をまとめて追加します。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    bulkError?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                OutlinedTextField(
                    value = nameInputState.value,
                    onValueChange = { nextValue ->
                        val wasFilled = nameInputState.value.text.isNotBlank()
                        val isFilled = nextValue.text.isNotBlank()
                        if (wasFilled != isFilled) {
                            AnalyticsTracker.uiControl(
                                "board_add_field_state",
                                if (isFilled) "板の名前の入力を開始" else "板の名前を消去",
                                mapOf("field_label" to "板の名前", "input_state" to if (isFilled) "入力あり" else "空")
                            )
                        }
                        nameInputState.onValueChange(nextValue)
                    },
                    label = { Text("板の名前") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = !validationState.hasName && name.isNotEmpty(),
                    colors = textFieldColors
                )
                OutlinedTextField(
                    value = urlInputState.value,
                    onValueChange = { nextValue ->
                        val wasFilled = urlInputState.value.text.isNotBlank()
                        val isFilled = nextValue.text.isNotBlank()
                        if (wasFilled != isFilled) {
                            AnalyticsTracker.uiControl(
                                "board_add_field_state",
                                if (isFilled) "板のURLの入力を開始" else "板のURLを消去",
                                mapOf("field_label" to "板のURL", "input_state" to if (isFilled) "入力あり" else "空")
                            )
                        }
                        urlInputState.onValueChange(nextValue)
                    },
                    label = { Text("板のURL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = validationState.hasUrl && (!validationState.isValidUrl || validationState.isDuplicateUrl),
                    colors = textFieldColors
                )
                validationState.helperText?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                }
                if (httpClient != null) {
                    TextButton(
                        onClick = { bulkMode = !bulkMode; bulkError = null },
                        enabled = !bulkLoading,
                        colors = futachaDialogTextButtonColors()
                    ) {
                        Text(if (bulkMode) "1件ずつ追加" else "板一覧から一括追加")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = if (bulkMode) !bulkLoading else validationState.canSubmit,
                colors = futachaDialogTextButtonColors(),
                onClick = {
                    if (bulkMode) {
                        val client = httpClient ?: return@TextButton
                        bulkLoading = true
                        bulkError = null
                        scope.launch {
                            fetchDefaultCompatBoardsFromMenu(
                                httpClient = client,
                                existingBoards = modernBoardsToCompatibility(existingBoards)
                            ).onSuccess { discovered ->
                                val existingUrls = modernBoardsToCompatibility(existingBoards)
                                    .mapTo(mutableSetOf()) { it.canonicalUrl }
                                val additions = discovered.filterNot { it.canonicalUrl in existingUrls }
                                if (additions.isEmpty()) {
                                    bulkError = "追加できる新しい板はありませんでした"
                                } else {
                                    onBulkSubmit(additions.map { board -> board.name to board.originalUrl })
                                }
                            }.onFailure {
                                // Transport exceptions can contain the requested address.
                                // Keep the hidden discovery endpoint out of user-visible errors.
                                bulkError = "板一覧を取得できませんでした"
                            }
                            bulkLoading = false
                        }
                        return@TextButton
                    }
                    AnalyticsTracker.uiControl("board_add_confirm", "板を追加")
                    onSubmit(validationState.trimmedName, validationState.normalizedInputUrl)
                    name = ""
                    url = ""
                }
            ) {
                Text(if (bulkMode) if (bulkLoading) "取得中…" else "一括追加" else "追加")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    AnalyticsTracker.uiControl("board_add_cancel", "板追加をキャンセル")
                    onDismiss()
                },
                colors = futachaDialogTextButtonColors()
            ) {
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
    val chromeColors = LocalFutachaChromeColors.current
    CenterAlignedTopAppBar(
        navigationIcon = {
            if (chromeState.showsBackButton) {
                IconButton(onClick = {
                    AnalyticsTracker.uiControl("board_management_back", "板一覧操作を戻る")
                    topBarCallbacks.onBackClick()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "戻る"
                    )
                }
            } else {
                IconButton(onClick = {
                    AnalyticsTracker.uiControl("board_history_open", "履歴を開く")
                    topBarCallbacks.onNavigationClick()
                }) {
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
            IconButton(onClick = {
                AnalyticsTracker.uiControl("board_menu_open", "板一覧メニューを開く")
                topBarCallbacks.onOpenMenu()
            }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "メニュー"
                )
            }
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = topBarCallbacks.onDismissMenu,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                BoardManagementMenuAction.entries.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            AnalyticsTracker.uiControl("board_menu_action", action.label)
                            topBarCallbacks.onMenuActionSelected(action)
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = chromeColors.topBar,
            titleContentColor = chromeColors.onBar,
            navigationIconContentColor = chromeColors.onBar,
            actionIconContentColor = chromeColors.onBar
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
    val listState = rememberLazyListState()
    var draggedBoardId by remember { mutableStateOf<String?>(null) }
    val currentBoards = rememberUpdatedState(boards)
    val currentBoardListCallbacks = rememberUpdatedState(boardListCallbacks)

    LazyColumn(
        state = listState,
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
            val reorderDragModifier = if (isReorderMode) {
                Modifier.pointerInput(isReorderMode, board.id) {
                    var accumulatedDrag = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggedBoardId = board.id },
                        onDragCancel = { draggedBoardId = null },
                        onDragEnd = { draggedBoardId = null },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (draggedBoardId != board.id) return@detectDragGesturesAfterLongPress
                            accumulatedDrag += dragAmount.y
                            val draggedItem = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                it.key == board.id
                            } ?: return@detectDragGesturesAfterLongPress
                            val center = draggedItem.offset + draggedItem.size / 2 + accumulatedDrag
                            val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                item.key != board.id &&
                                    center >= item.offset && center <= item.offset + item.size
                            } ?: return@detectDragGesturesAfterLongPress
                            val latestBoards = currentBoards.value
                            val currentIndex = latestBoards.indexOfFirst { it.id == board.id }
                            val targetIndex = latestBoards.indexOfFirst { it.id == targetItem.key }
                            if (currentIndex < 0 || targetIndex < 0 || currentIndex == targetIndex) {
                                return@detectDragGesturesAfterLongPress
                            }
                            if (targetIndex < currentIndex) {
                                currentBoardListCallbacks.value.onMoveUp(latestBoards, currentIndex)
                            } else {
                                currentBoardListCallbacks.value.onMoveDown(latestBoards, currentIndex)
                            }
                            accumulatedDrag = 0f
                        }
                    )
                }
            } else {
                Modifier
            }
            Box(modifier = reorderDragModifier.fillMaxWidth()) {
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
                            onPinToggle = { boardListCallbacks.onPinClick(boards, index) },
                            canMoveUp = index > 0,
                            canMoveDown = index < boards.size - 1
                        )
                    }
                    else -> {
                        BoardSummaryCard(
                            board = board,
                            onClick = { boardListCallbacks.onBoardClick(board) },
                            onPinToggle = { boardListCallbacks.onPinClick(boards, index) }
                        )
                    }
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
                isHistoryRefreshing = bindings.isHistoryRefreshing,
                onBoardClick = bindings.historyDrawerCallbacks.onBoardClick,
                onRefreshClick = bindings.historyDrawerCallbacks.onRefreshClick,
                onBatchDeleteClick = bindings.historyDrawerCallbacks.onBatchDeleteClick,
                onExportClick = bindings.historyDrawerCallbacks.onExportClick,
                onExportThenClearClick = bindings.historyDrawerCallbacks.onExportThenClearClick,
                onExportSelectedClick = bindings.historyDrawerCallbacks.onExportSelectedClick,
                onLoadImportPreview = bindings.historyDrawerCallbacks.onLoadImportPreview,
                onImportClick = bindings.historyDrawerCallbacks.onImportClick,
                onImportSelectedClick = bindings.historyDrawerCallbacks.onImportSelectedClick,
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
            httpClient = bindings.httpClient,
            onDismiss = bindings.onDismissAddDialog,
            onSubmit = bindings.onAddBoardSubmitted,
            onBulkSubmit = bindings.onAddBoardsSubmitted
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
