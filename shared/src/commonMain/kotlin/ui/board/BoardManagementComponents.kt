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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableFloatStateOf
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
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

@Composable
internal fun AddBoardDialog(
    existingBoards: List<BoardSummary>,
    httpClient: HttpClient? = null,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onBulkSubmit: (List<Pair<String, String>>) -> Unit,
    loadAimogeBoards: (suspend () -> List<com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo>)? = null
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var bulkMode by rememberSaveable { mutableStateOf(false) }
    var bulkLoading by remember { mutableStateOf(false) }
    var bulkError by remember { mutableStateOf<String?>(null) }
    val aimogeAdder = com.valoser.futacha.shared.ui.aimoge.rememberAimogeBoardUrlAdder(
        onAdd = { boardName, boardUrl -> onSubmit(boardName, boardUrl); name = ""; url = "" },
        registeredUrls = existingBoards.mapNotNull { com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf(it.url) }.toSet(),
        loadBoards = loadAimogeBoards,
        onAddBoards = { boards -> onBulkSubmit(boards.map { it.name to it.url }); name = ""; url = "" }
    )
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
    val aimogeInput = remember(url) { com.valoser.futacha.shared.ui.aimoge.classifyAimogeBoardUrlInput(url) }
    val validationState = remember(name, url, existingBoards, aimogeInput) {
        buildAddBoardValidationState(
            name = name,
            url = url,
            existingBoards = existingBoards
        ).withAimoge(aimogeInput, existingBoards)
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

    FutachaAppLockAwareWindow { AlertDialog(
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
                if (!bulkMode && com.valoser.futacha.shared.ui.InAppThreadLinks.normalized(url) != null) {
                    TextButton(
                        onClick = { com.valoser.futacha.shared.ui.InAppThreadLinks.open(url); onDismiss() },
                        colors = futachaDialogTextButtonColors(),
                        modifier = Modifier.testTag("board-add-open-thread")
                    ) { Text("このスレを開く") }
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
                enabled = if (bulkMode) !bulkLoading else validationState.canSubmit && !aimogeAdder.busy,
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
                    if (aimogeInput == com.valoser.futacha.shared.ui.aimoge.AimogeBoardUrlInput.SelectBoards) {
                        aimogeAdder.selectBoards()
                        return@TextButton
                    }
                    (aimogeInput as? com.valoser.futacha.shared.ui.aimoge.AimogeBoardUrlInput.Board)?.let { board ->
                        aimogeAdder.start(board.url, validationState.trimmedName)
                        return@TextButton
                    }
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
    ) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardManagementTopBar(
    chromeState: BoardManagementChromeState,
    isMenuExpanded: Boolean,
    topBarCallbacks: BoardManagementTopBarCallbacks,
    onOpenAimogeCalendar: (() -> Unit)? = null,
    mixOffered: Boolean = true
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
            var showOpenThreadUrl by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            if (showOpenThreadUrl) com.valoser.futacha.shared.ui.OpenThreadUrlDialog(onDismiss = { showOpenThreadUrl = false })
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = topBarCallbacks.onDismissMenu,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                com.valoser.futacha.shared.ui.media.DeviceImageEditorMenuItem(topBarCallbacks.onDismissMenu)
                com.valoser.futacha.shared.ui.media.DeviceVideoEditorMenuItem(topBarCallbacks.onDismissMenu)
                if (onOpenAimogeCalendar != null) {
                    DropdownMenuItem(
                        text = { Text("あいもげのイベントカレンダー") },
                        onClick = {
                            topBarCallbacks.onDismissMenu()
                            onOpenAimogeCalendar()
                        },
                        modifier = Modifier.testTag("futacha-aimoge-calendar")
                    )
                }
                DropdownMenuItem(
                    text = { Text("URLから開く") },
                    onClick = {
                        topBarCallbacks.onDismissMenu()
                        showOpenThreadUrl = true
                    },
                    modifier = Modifier.testTag("futacha-open-thread-url")
                )
                BoardManagementMenuAction.entries.filter { it != BoardManagementMenuAction.MIX || (mixOffered && com.valoser.futacha.shared.compat.MixAvailability.enabled) }.forEach { action ->
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

/**
 * The board lists key their rows by id, and a board stored twice crashed ふたちゃ at start (2026-10-13 全体検証 7.6).
 * The stores remove such copies; the list also shows each id once, so a copy left anywhere cannot crash it.
 * Returns [boards] itself when every id is distinct.
 */
internal fun distinctBoardsById(boards: List<BoardSummary>): List<BoardSummary> {
    val ids = HashSet<String>(boards.size)
    return if (boards.all { ids.add(it.id) }) boards else boards.distinctBy(BoardSummary::id)
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
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp),
    bottomAligned: Boolean = boardsFromBottom(LocalFutachaSharedFeatures.current?.preferences.orEmpty())
) {
    val listState = rememberLazyListState()
    var draggedBoardId by remember { mutableStateOf<String?>(null) }
    // Order shown while dragging, persisted once on drop. Each step used to be
    // persisted and computed from the not-yet-updated list, dropping steps.
    // Kept after the drop until the persisted list arrives, so it doesn't flash back.
    var dragOrder by remember { mutableStateOf<List<BoardSummary>?>(null) }
    var draggedBoardOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(boards, isReorderMode) {
        if (draggedBoardId == null) dragOrder = null
    }
    val displayedBoards = remember(dragOrder, boards) { distinctBoardsById(dragOrder ?: boards) }
    var renamingBoardId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamedBoardName by rememberSaveable { mutableStateOf("") }
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
        verticalArrangement = Arrangement.spacedBy(8.dp,
            if (bottomAligned) Alignment.Bottom else Alignment.Top),
        contentPadding = contentPadding
    ) {
        itemsIndexed(
            items = displayedBoards,
            key = { _, board -> board.id }
        ) { index, board ->
            val reorderDragModifier = if (isReorderMode) {
                Modifier.pointerInput(isReorderMode, board.id) {
                    val spacingPx = 8.dp.toPx()
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggedBoardId = board.id
                            draggedBoardOffset = 0f
                            dragOrder = dragOrder ?: currentBoards.value
                        },
                        onDragCancel = {
                            draggedBoardId = null
                            draggedBoardOffset = 0f
                            dragOrder = null
                        },
                        onDragEnd = {
                            draggedBoardId = null
                            draggedBoardOffset = 0f
                            val finalOrder = dragOrder
                            if (finalOrder == null || finalOrder == currentBoards.value) {
                                dragOrder = null
                            } else {
                                currentBoardListCallbacks.value.onReorder(finalOrder)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (draggedBoardId != board.id) return@detectDragGesturesAfterLongPress
                            // The row's own height, not the list's layout: the layout is a frame
                            // late after a swap, which used to undo or drop the next steps.
                            val step = size.height + spacingPx
                            val result = boardDragSteps(
                                order = dragOrder ?: currentBoards.value,
                                boardId = board.id,
                                offset = draggedBoardOffset + dragAmount.y,
                                step = step
                            )
                            dragOrder = result.first
                            draggedBoardOffset = result.second
                        }
                    )
                }.graphicsLayer {
                    if (draggedBoardId == board.id) {
                        translationY = draggedBoardOffset
                        shadowElevation = 8.dp.toPx()
                    }
                }.zIndex(if (draggedBoardId == board.id) 1f else 0f)
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
                            onMoveUp = { boardListCallbacks.onMoveUp(displayedBoards, index) },
                            onMoveDown = { boardListCallbacks.onMoveDown(displayedBoards, index) },
                            onPinToggle = { boardListCallbacks.onPinClick(displayedBoards, index) },
                            canMoveUp = index > 0,
                            canMoveDown = index < displayedBoards.size - 1
                        )
                    }
                    else -> {
                        BoardSummaryCard(
                            board = board,
                            onClick = { boardListCallbacks.onBoardClick(board) },
                            onPinToggle = { boardListCallbacks.onPinClick(displayedBoards, index) },
                            onRename = { renamedBoardName = board.name; renamingBoardId = board.id }
                        )
                    }
                }
            }
        }
    }
    renamingBoardId?.let { boardId ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { renamingBoardId = null },
            title = { Text("板の名前を変更") },
            text = {
                OutlinedTextField(
                    value = renamedBoardName,
                    onValueChange = { renamedBoardName = it.take(BOARD_MANAGEMENT_NAME_MAX_CHARS) },
                    label = { Text("板の名前") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = renamedBoardName.isNotBlank(), onClick = {
                    currentBoardListCallbacks.value.onRename(currentBoards.value, boardId, renamedBoardName)
                    renamingBoardId = null
                }) { Text("変更") }
            },
            dismissButton = { TextButton(onClick = { renamingBoardId = null }) { Text("キャンセル") } }
        ) }
    }
}

@Composable
internal fun BoardManagementScaffold(
    bindings: BoardManagementScaffoldBindings,
    modifier: Modifier = Modifier
) {
    var aimogeCalendarOpen by remember { mutableStateOf(false) }
    var aimogeCalendarNotice by remember { mutableStateOf<String?>(null) }
    if (aimogeCalendarOpen) {
        com.valoser.futacha.shared.ui.aimoge.AimogeEventCalendarDialog(
            onOpenThread = { row ->
                val threadId = row.threadId
                val board = bindings.boards.firstOrNull {
                    com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf(it.url) == row.boardUrl
                }
                if (threadId == null || board == null) {
                    aimogeCalendarNotice = "この板（/${row.slug}/）は登録されていません。板一覧の「新規追加」から追加してください"
                } else {
                    aimogeCalendarOpen = false
                    bindings.historyDrawerCallbacks.onHistoryEntrySelected(
                        ThreadHistoryEntry(
                            threadId = threadId,
                            boardId = board.id,
                            title = row.title,
                            titleImageUrl = row.thumbnailUrl.orEmpty(),
                            boardName = board.name,
                            boardUrl = row.threadUrl ?: board.url,
                            lastVisitedEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                            replyCount = 0
                        )
                    )
                }
            },
            onDismiss = { aimogeCalendarOpen = false }
        )
    }
    aimogeCalendarNotice?.let { notice ->
        com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { aimogeCalendarNotice = null },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = { aimogeCalendarNotice = null }) { Text("OK") } }
        ) }
    }
    ModalNavigationDrawer(
        drawerState = bindings.drawerState,
        gesturesEnabled = true,
        drawerContent = {
            HistoryDrawerContent(
                history = bindings.history,
                isVisible = bindings.isDrawerOpen,
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
                    topBarCallbacks = bindings.topBarCallbacks,
                    onOpenAimogeCalendar = { aimogeCalendarOpen = true }
                        .takeIf { com.valoser.futacha.shared.ui.aimoge.aimogeEventCalendarAvailable(bindings.boards.map { it.url }) },
                    mixOffered = LocalHasExistingMix.current || mixOfferable(bindings.boards.map { it.url })
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

/**
 * Moves [boardId] one row per [step] of [offset] (a drag can cross several rows at once)
 * and returns the new order and what is left of the offset.
 */
internal fun boardDragSteps(order: List<BoardSummary>, boardId: String, offset: Float, step: Float): Pair<List<BoardSummary>, Float> {
    if (step <= 0f) return order to offset
    var list = order
    var left = offset
    while (true) {
        val index = list.indexOfFirst { it.id == boardId }
        if (index < 0) return list to left
        val down = left > step / 2f && index < list.lastIndex
        val up = left < -step / 2f && index > 0
        if (!down && !up) return list to left
        list = moveBoardSummary(list, index, moveUp = up)
        left += if (down) -step else step
    }
}
