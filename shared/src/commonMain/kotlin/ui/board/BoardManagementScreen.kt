package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun BoardManagementScreen(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    onBoardSelected: (BoardSummary) -> Unit,
    onAddBoard: (String, String) -> Unit,
    onMenuAction: (BoardManagementMenuAction) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = {},
    onHistoryRefresh: suspend () -> Unit = {},
    modifier: Modifier = Modifier,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = {},
    onHistoryCleared: () -> Unit = {},
    onBoardDeleted: (BoardSummary) -> Unit = {},
    onBoardsReordered: (List<BoardSummary>) -> Unit = {},
    httpClient: io.ktor.client.HttpClient? = null,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isAddDialogVisible by rememberSaveable { mutableStateOf(false) }
    var isDeleteMode by rememberSaveable { mutableStateOf(false) }
    var isReorderMode by rememberSaveable { mutableStateOf(false) }
    var boardToDelete by remember { mutableStateOf<BoardSummary?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerOpen by remember {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
        }
    }
    val handleHistorySelection: (ThreadHistoryEntry) -> Unit = { entry ->
        scope.launch { drawerState.close() }
        onHistoryEntrySelected(entry)
    }

    var isHistoryRefreshing by remember { mutableStateOf(false) }
    val handleHistoryRefresh: () -> Unit = handleHistoryRefresh@{
        if (isHistoryRefreshing) return@handleHistoryRefresh
        scope.launch {
            isHistoryRefreshing = true
            snackbarHostState.showSnackbar("履歴を更新中...")
            try {
                onHistoryRefresh()
                snackbarHostState.showSnackbar("履歴を更新しました")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("履歴の更新に失敗しました: ${e.message ?: "不明なエラー"}")
            } finally {
            isHistoryRefreshing = false
            }
        }
    }

    val handleBatchDelete: () -> Unit = {
        scope.launch {
            onHistoryCleared()
            snackbarHostState.showSnackbar("履歴を一括削除しました")
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isDrawerOpen,
        drawerContent = {
            HistoryDrawerContent(
                history = history,
                onHistoryEntryDismissed = onHistoryEntryDismissed,
                onHistoryEntrySelected = handleHistorySelection,
                onBoardClick = {
                    scope.launch {
                        drawerState.close()
                    }
                },
                onRefreshClick = handleHistoryRefresh,
                onBatchDeleteClick = handleBatchDelete,
                onSettingsClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("設定はモック動作です")
                    }
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        if (isDeleteMode || isReorderMode) {
                            IconButton(onClick = {
                                isDeleteMode = false
                                isReorderMode = false
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "戻る"
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Outlined.Menu,
                                    contentDescription = "履歴を開く"
                                )
                            }
                        }
                    },
                    title = {
                        Text(
                            when {
                                isDeleteMode -> "削除する板を選択"
                                isReorderMode -> "板の順序を変更"
                                else -> "ふたば"
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = { isMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "メニュー"
                            )
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            BoardManagementMenuAction.entries.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        isMenuExpanded = false
                                        onMenuAction(action)
                                        when (action) {
                                            BoardManagementMenuAction.ADD -> {
                                                isAddDialogVisible = true
                                            }
                                            BoardManagementMenuAction.DELETE -> {
                                                isDeleteMode = !isDeleteMode
                                                isReorderMode = false
                                            }
                                            BoardManagementMenuAction.REORDER -> {
                                                isReorderMode = !isReorderMode
                                                isDeleteMode = false
                                            }
                                            else -> {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("${action.label} はモック動作です")
                                                }
                                            }
                                        }
                                    }
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
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .pointerInput(isDrawerOpen) {
                        if (!isDrawerOpen) return@pointerInput
                        awaitPointerEventScope {
                            awaitFirstDown()
                            scope.launch { drawerState.close() }
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                itemsIndexed(
                    items = boards,
                    key = { _, board -> board.id }
                ) { index, board ->
                    when {
                        isDeleteMode -> {
                            BoardSummaryCardWithDelete(
                                board = board,
                                onDelete = {
                                    boardToDelete = board
                                }
                            )
                        }
                        isReorderMode -> {
                            BoardSummaryCardWithReorder(
                                board = board,
                                onMoveUp = {
                                    if (index > 0) {
                                        val newBoards = boards.toMutableList()
                                        newBoards.removeAt(index)
                                        newBoards.add(index - 1, board)
                                        onBoardsReordered(newBoards)
                                    }
                                },
                                onMoveDown = {
                                    if (index < boards.size - 1) {
                                        val newBoards = boards.toMutableList()
                                        newBoards.removeAt(index)
                                        newBoards.add(index + 1, board)
                                        onBoardsReordered(newBoards)
                                    }
                                },
                                canMoveUp = index > 0,
                                canMoveDown = index < boards.size - 1
                            )
                        }
                        else -> {
                            BoardSummaryCard(
                                board = board,
                                onClick = { onBoardSelected(board) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (isAddDialogVisible) {
        AddBoardDialog(
            existingBoards = boards,
            onDismiss = { isAddDialogVisible = false },
            onSubmit = { name, url ->
                onAddBoard(name, url)
                isAddDialogVisible = false
                scope.launch {
                    snackbarHostState.showSnackbar("\"$name\" を追加しました")
                }
            }
        )
    }

    boardToDelete?.let { board ->
        DeleteBoardDialog(
            board = board,
            onDismiss = { boardToDelete = null },
            onConfirm = {
                onBoardDeleted(board)
                boardToDelete = null
                scope.launch {
                    snackbarHostState.showSnackbar("\"${board.name}\" を削除しました")
                }
            }
        )
    }
}

@Composable
private fun AddBoardDialog(
    existingBoards: List<BoardSummary>,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val trimmedName = name.trim()
    val trimmedUrl = url.trim()
    val hasName = trimmedName.isNotEmpty()
    val hasUrl = trimmedUrl.isNotEmpty()
    val urlHasScheme = trimmedUrl.startsWith("http://", ignoreCase = true) ||
        trimmedUrl.startsWith("https://", ignoreCase = true)

    // Enhanced URL validation
    val isValidUrl = hasUrl && urlHasScheme && run {
        val urlWithoutScheme = trimmedUrl.removePrefix("https://").removePrefix("http://")
        // Check for valid domain/host structure
        val hostPart = urlWithoutScheme.substringBefore("/").substringBefore("?").substringBefore("#")

        hostPart.isNotEmpty() &&
            !hostPart.startsWith(".") &&
            !hostPart.endsWith(".") &&
            !hostPart.contains("..") && // No consecutive dots
            !hostPart.contains(" ") && // No spaces
            (hostPart.contains(".") || // Must have at least one dot for domain
             hostPart.equals("localhost", ignoreCase = true) || // Allow localhost
             hostPart.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) // Allow IP addresses
    }

    val isDuplicateUrl = existingBoards.any { it.url.equals(trimmedUrl, ignoreCase = true) }
    val canSubmit = hasName && isValidUrl && !isDuplicateUrl

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
                    isError = !hasName && name.isNotEmpty()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("板のURL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = hasUrl && (!isValidUrl || isDuplicateUrl)
                )
                val helperText = when {
                    isDuplicateUrl -> "同じURLの板が既に登録されています"
                    hasUrl && !urlHasScheme -> "http:// もしくは https:// から始まるURLを入力してください"
                    hasUrl && !isValidUrl -> "有効なURLを入力してください（例: https://example.com/board）"
                    else -> null
                }
                helperText?.let { message ->
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
                enabled = canSubmit,
                onClick = {
                    onSubmit(trimmedName, trimmedUrl)
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
private fun HistoryDrawerContent(
    history: List<ThreadHistoryEntry>,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBoardClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onThreadRefreshClick: (() -> Unit)? = null,
    onBatchDeleteClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val drawerWidth = 320.dp
    ModalDrawerSheet(
        modifier = Modifier
            .width(drawerWidth)
            .fillMaxHeight(),
        drawerShape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item { HistoryListHeader() }
                items(
                    items = history,
                    key = { it.threadId }
                ) { entry ->
                    DismissibleHistoryEntry(
                        entry = entry,
                        onDismissed = onHistoryEntryDismissed,
                        onClicked = { onHistoryEntrySelected(entry) }
                    )
                }
            }
            HistoryBottomBar(
                onBoardClick = onBoardClick,
                onRefreshClick = onRefreshClick,
                onThreadRefreshClick = onThreadRefreshClick,
                onBatchDeleteClick = onBatchDeleteClick,
                onSettingsClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun HistoryListHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = "閲覧中のスレッド",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun HistoryBottomBar(
    onBoardClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onThreadRefreshClick: (() -> Unit)? = null,
    onBatchDeleteClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryBottomIcon(Icons.Rounded.Home, "板", onBoardClick)
            HistoryBottomIcon(
                icon = Icons.Rounded.Refresh,
                label = "更新"
            ) {
                onRefreshClick()
                onThreadRefreshClick?.invoke()
            }
            HistoryBottomIcon(Icons.Rounded.DeleteSweep, "一括削除", onBatchDeleteClick)
            HistoryBottomIcon(Icons.Rounded.Settings, "設定", onSettingsClick)
        }
    }
}

@Composable
private fun HistoryBottomIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleHistoryEntry(
    entry: ThreadHistoryEntry,
    onDismissed: (ThreadHistoryEntry) -> Unit,
    onClicked: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onDismissed(entry)
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = { HistoryDismissBackground() }
    ) {
        HistoryEntryCard(entry = entry, onClick = onClicked)
    }
}

@Composable
private fun HistoryDismissBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "削除",
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "削除",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: ThreadHistoryEntry,
    onClick: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    val formattedLastVisited = remember(entry.lastVisitedEpochMillis) {
        formatLastVisited(entry.lastVisitedEpochMillis)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(platformContext)
                    .data(entry.titleImageUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = LocalFutachaImageLoader.current,
                contentDescription = "${entry.title} のタイトル画像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.boardUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "最終閲覧: $formattedLastVisited",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = entry.replyCount.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "レス数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun formatLastVisited(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val monthValue = localDateTime.month.ordinal + 1
    return "${localDateTime.year}/${monthValue.toString().padStart(2, '0')}/${localDateTime.day.toString().padStart(2, '0')} " +
            "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
}

@Composable
private fun BoardSummaryCard(
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
        }
    }
}

@Composable
private fun DeleteBoardDialog(
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
private fun BoardSummaryCardWithDelete(
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BoardSummaryCardWithReorder(
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
    }
}

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val items: List<CatalogItem>) : CatalogUiState
    data class Error(val message: String = "カタログを読み込めませんでした") : CatalogUiState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun CatalogScreen(
    board: BoardSummary?,
    history: List<ThreadHistoryEntry>,
    onBack: () -> Unit,
    onThreadSelected: (CatalogItem) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = {},
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = {},
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = {},
    onHistoryRefresh: suspend () -> Unit = {},
    onHistoryCleared: () -> Unit = {},
    repository: BoardRepository? = null,
    modifier: Modifier = Modifier
) {
    val activeRepository = remember(repository) {
        repository ?: FakeBoardRepository()
    }
    val uiState: MutableState<CatalogUiState> =
        remember { mutableStateOf(CatalogUiState.Loading) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerOpen by remember {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
        }
    }
    var catalogMode by rememberSaveable { mutableStateOf(CatalogMode.default) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isHistoryRefreshing by remember { mutableStateOf(false) }
    var isSearchActive by rememberSaveable(board?.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(board?.id) { mutableStateOf("") }
    var showModeDialog by remember { mutableStateOf(false) }
    var showCreateThreadDialog by remember { mutableStateOf(false) }
    val handleHistoryRefresh: () -> Unit = handleHistoryRefresh@{
        if (isHistoryRefreshing) return@handleHistoryRefresh
        coroutineScope.launch {
            isHistoryRefreshing = true
            snackbarHostState.showSnackbar("履歴を更新中...")
            try {
                onHistoryRefresh()
                snackbarHostState.showSnackbar("履歴を更新しました")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("履歴の更新に失敗しました: ${e.message ?: "不明なエラー"}")
            } finally {
                isHistoryRefreshing = false
            }
        }
    }

    val performRefresh: () -> Unit = {
        if (!isRefreshing && board != null) {
            coroutineScope.launch {
                isRefreshing = true
                try {
                    val catalog = activeRepository.getCatalog(board.url, catalogMode)
                    uiState.value = CatalogUiState.Success(catalog)
                    snackbarHostState.showSnackbar("カタログを更新しました")
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        snackbarHostState.showSnackbar("更新に失敗しました")
                    }
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    PlatformBackHandler(enabled = isDrawerOpen) {
        coroutineScope.launch { drawerState.close() }
    }
    PlatformBackHandler(enabled = !isDrawerOpen && isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }
    PlatformBackHandler(enabled = !isDrawerOpen && !isSearchActive) {
        onBack()
    }

    LaunchedEffect(board?.url, catalogMode) {
        if (board == null) {
            uiState.value = CatalogUiState.Error("板が選択されていません")
            return@LaunchedEffect
        }
        uiState.value = CatalogUiState.Loading

        // Use try-finally to ensure cleanup even on cancellation
        try {
            val catalog = activeRepository.getCatalog(board.url, catalogMode)
            // Check if still active before updating state
            if (isActive) {
                uiState.value = CatalogUiState.Success(catalog)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Rethrow cancellation to properly cancel the coroutine
            throw e
        } catch (e: Exception) {
            if (isActive) {
                val message = when {
                    e.message?.contains("timeout", ignoreCase = true) == true -> "タイムアウト: サーバーが応答しません"
                    e.message?.contains("404") == true -> "板が見つかりません (404)"
                    e.message?.contains("500") == true -> "サーバーエラー (500)"
                    e.message?.contains("HTTP error") == true -> "ネットワークエラー: ${e.message}"
                    e.message?.contains("exceeds maximum") == true -> "データサイズが大きすぎます"
                    else -> "カタログを読み込めませんでした: ${e.message ?: "不明なエラー"}"
                }
                uiState.value = CatalogUiState.Error(message)
            }
        }
    }

    val handleHistorySelection: (ThreadHistoryEntry) -> Unit = { entry ->
        coroutineScope.launch { drawerState.close() }
        onHistoryEntrySelected(entry)
    }

    val handleBatchDelete: () -> Unit = {
        coroutineScope.launch {
            onHistoryCleared()
            snackbarHostState.showSnackbar("履歴を一括削除しました")
            drawerState.close()
        }
    }

    val currentState = uiState.value

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isDrawerOpen,
        drawerContent = {
            HistoryDrawerContent(
                history = history,
                onHistoryEntryDismissed = onHistoryEntryDismissed,
                onHistoryEntrySelected = handleHistorySelection,
                onBoardClick = {
                    coroutineScope.launch {
                        drawerState.close()
                        onBack()
                    }
                },
                onRefreshClick = handleHistoryRefresh,
                onThreadRefreshClick = {
                    performRefresh()
                },
                onBatchDeleteClick = handleBatchDelete,
                onSettingsClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("設定はモック動作です")
                    }
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CatalogTopBar(
                    board = board,
                    mode = catalogMode,
                    searchQuery = searchQuery,
                    isSearchActive = isSearchActive,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchActiveChange = { active ->
                        isSearchActive = active
                        if (!active) {
                            searchQuery = ""
                        }
                    },
                    onNavigationClick = { coroutineScope.launch { drawerState.open() } },
                    onModeSelected = { catalogMode = it },
                    onMenuAction = { action ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("${action.label} はモック動作です")
                        }
                    }
                )
            },
            bottomBar = {
                CatalogNavigationBar(
                    current = null,
                    onNavigate = { destination ->
                        when (destination) {
                            CatalogNavDestination.CreateThread -> showCreateThreadDialog = true
                            CatalogNavDestination.RefreshCatalog -> performRefresh()
                            CatalogNavDestination.Mode -> showModeDialog = true
                            CatalogNavDestination.Settings -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("設定は未実装です")
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(isDrawerOpen) {
                    if (!isDrawerOpen) return@pointerInput
                    awaitPointerEventScope {
                        awaitFirstDown()
                        coroutineScope.launch { drawerState.close() }
                    }
                }
            when (val state = currentState) {
                CatalogUiState.Loading -> LoadingCatalog(modifier = contentModifier)
                is CatalogUiState.Error -> CatalogError(message = state.message, modifier = contentModifier)
                is CatalogUiState.Success -> {
                    val sortedItems = catalogMode.applyLocalSort(state.items)
                    val visibleItems = sortedItems.filterByQuery(searchQuery)
                    CatalogSuccessContent(
                        items = visibleItems,
                        board = board,
                        repository = activeRepository,
                        isSearching = searchQuery.isNotBlank(),
                        onThreadSelected = onThreadSelected,
                        onRefresh = performRefresh,
                        isRefreshing = isRefreshing,
                        modifier = contentModifier
                    )
                }
            }
        }

        if (showModeDialog) {
            AlertDialog(
                onDismissRequest = { showModeDialog = false },
                title = { Text("モード選択") },
                text = {
                    Column {
                        CatalogMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        catalogMode = mode
                                        showModeDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = catalogMode == mode,
                                    onClick = {
                                        catalogMode = mode
                                        showModeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModeDialog = false }) {
                        Text("閉じる")
                    }
                }
            )
        }

        if (showCreateThreadDialog) {
            CreateThreadDialog(
                boardName = board?.name,
                onDismiss = { showCreateThreadDialog = false },
                onSubmit = { name, email, title, comment, password, imageData ->
                    showCreateThreadDialog = false
                    if (board == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("板が選択されていません")
                        }
                        return@CreateThreadDialog
                    }
                    coroutineScope.launch {
                        try {
                            snackbarHostState.showSnackbar("スレッドを作成中...")
                            val threadId = activeRepository.createThread(
                                board = board.url,
                                name = name,
                                email = email,
                                subject = title,
                                comment = comment,
                                password = password,
                                imageFile = imageData?.bytes,
                                imageFileName = imageData?.fileName,
                                textOnly = imageData == null
                            )
                            snackbarHostState.showSnackbar("スレッドを作成しました (ID: $threadId)")
                            // Refresh catalog to show the new thread
                            performRefresh()
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("スレッド作成に失敗しました: ${e.message ?: "不明なエラー"}")
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateThreadDialog(
    boardName: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, email: String, title: String, comment: String, password: String, imageData: com.valoser.futacha.shared.util.ImageData?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<com.valoser.futacha.shared.util.ImageData?>(null) }
    val emailPresets = remember { listOf("ID表示", "IP表示", "sage") }

    ThreadFormDialog(
        title = "スレ立て",
        subtitle = boardName?.takeIf { it.isNotBlank() },
        emailPresets = emailPresets,
        comment = comment,
        onCommentChange = { comment = it },
        name = name,
        onNameChange = { name = it },
        email = email,
        onEmailChange = { email = it },
        subject = title,
        onSubjectChange = { title = it },
        password = password,
        onPasswordChange = { password = it },
        selectedImage = selectedImage,
        onImageSelected = { selectedImage = it },
        onDismiss = onDismiss,
        onSubmit = {
            onSubmit(name, email, title, comment, password, selectedImage)
        },
        onClear = {
            name = ""
            email = ""
            title = ""
            comment = ""
            password = ""
            selectedImage = null
        },
        isSubmitEnabled = title.isNotBlank() || comment.isNotBlank(),
        sendDescription = "スレ立て",
        showSubject = true,
        showPassword = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadReplyDialog(
    boardName: String,
    threadTitle: String,
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    subject: String,
    onSubjectChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    selectedImage: com.valoser.futacha.shared.util.ImageData?,
    onImageSelected: (com.valoser.futacha.shared.util.ImageData?) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit
) {
    ThreadFormDialog(
        title = threadTitle.ifBlank { "返信" },
        subtitle = boardName,
        emailPresets = listOf("ID表示", "IP表示", "sage"),
        comment = comment,
        onCommentChange = onCommentChange,
        name = name,
        onNameChange = onNameChange,
        email = email,
        onEmailChange = onEmailChange,
        subject = subject,
        onSubjectChange = onSubjectChange,
        password = password,
        onPasswordChange = onPasswordChange,
        selectedImage = selectedImage,
        onImageSelected = onImageSelected,
        onDismiss = onDismiss,
        onSubmit = onSubmit,
        onClear = onClear,
        isSubmitEnabled = comment.isNotBlank(),
        sendDescription = "返信する",
        showSubject = true,
        showPassword = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadFormDialog(
    title: String,
    subtitle: String?,
    emailPresets: List<String>,
    comment: String,
    onCommentChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    subject: String,
    onSubjectChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    selectedImage: com.valoser.futacha.shared.util.ImageData?,
    onImageSelected: (com.valoser.futacha.shared.util.ImageData?) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    isSubmitEnabled: Boolean,
    sendDescription: String,
    showSubject: Boolean = true,
    showPassword: Boolean = true
) {
    val commentLineCount = remember(comment) {
        if (comment.isBlank()) 0 else comment.count { it == '\n' } + 1
    }
    val commentByteCount = remember(comment) { comment.encodeToByteArray().size }
    val scrollState = rememberScrollState()
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
    )
    val imagePickerLauncher = rememberImagePickerLauncher(onImageSelected = { image ->
        onImageSelected(image)
    })
    val videoPickerLauncher = rememberImagePickerLauncher(
        mimeType = "video/*",
        onImageSelected = { image ->
            onImageSelected(image)
        }
    )
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "閉じる"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "その他"
                            )
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("キャンセル") },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onDismiss()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        TextField(
                            value = comment,
                            onValueChange = onCommentChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("コメント") },
                            minLines = 2,
                            maxLines = 2,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                            keyboardActions = KeyboardActions.Default
                        )
                        Text(
                            text = "${commentLineCount}行 ${commentByteCount}バイト",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextField(
                        value = name,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("おなまえ") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextField(
                            value = email,
                            onValueChange = onEmailChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("メール") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            emailPresets.forEachIndexed { index, preset ->
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onEmailChange(preset) }
                                        .padding(start = if (index == 0) 0.dp else 8.dp)
                                )
                            }
                        }
                    }

                    if (showSubject) {
                        TextField(
                            value = subject,
                            onValueChange = onSubjectChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("題名") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                        )
                    }

                    if (showPassword) {
                        TextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("削除キー") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            visualTransformation = PasswordVisualTransformation(),
                            supportingText = {
                                Text("削除用. 英数字で8字以内", style = MaterialTheme.typography.bodySmall)
                            },
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
                        )
                    }

                    selectedImage?.let { image ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = image.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${image.bytes.size / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onImageSelected(null) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "添付を削除"
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 4.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onSubmit,
                            enabled = isSubmitEnabled
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Send,
                                contentDescription = sendDescription,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = { imagePickerLauncher() }) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "画像を選択",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = { videoPickerLauncher() }) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = "動画を選択",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "入力をクリア",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "その他",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
expect fun ImagePickerButton(
    onImageSelected: (com.valoser.futacha.shared.util.ImageData) -> Unit
)

@Composable
expect fun rememberImagePickerLauncher(
    mimeType: String = "image/*",
    onImageSelected: (com.valoser.futacha.shared.util.ImageData) -> Unit
): () -> Unit

@Composable
private fun LoadingCatalog(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun CatalogError(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CatalogSuccessContent(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    isSearching: Boolean,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        CatalogEmptyContent(
            isSearching = isSearching,
            modifier = modifier
        )
    } else {
        CatalogGrid(
            items = items,
            board = board,
            repository = repository,
            onThreadSelected = onThreadSelected,
            onRefresh = onRefresh,
            isRefreshing = isRefreshing,
            modifier = modifier
        )
    }
}

@Composable
private fun CatalogEmptyContent(
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSearching) "検索に一致するスレッドがありません" else "表示できるスレッドがありません",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CatalogGrid(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    var dragOffset by remember { mutableStateOf(0f) }
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                modifier = Modifier.padding(horizontal = 8.dp),
                columns = GridCells.Fixed(5),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                items(
                    items = items,
                    key = { it.id }
                ) { catalogItem ->
                    CatalogCard(
                        item = catalogItem,
                        boardUrl = board?.url,
                        repository = repository,
                        onClick = { onThreadSelected(catalogItem) }
                    )
                }
            }

            if (isAtBottom && !isRefreshing) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.BottomCenter)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { dragOffset = 0f },
                                onDragEnd = {
                                    if (dragOffset > 100) {
                                        coroutineScope.launch { onRefresh() }
                                    }
                                    dragOffset = 0f
                                },
                                onDragCancel = { dragOffset = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount < 0) {
                                        dragOffset += -dragAmount
                                    }
                                }
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun CatalogCard(
    item: CatalogItem,
    boardUrl: String?,
    repository: BoardRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current
    var resolvedThumbnailUrl by remember(item.id, item.thumbnailUrl, boardUrl) {
        mutableStateOf(item.thumbnailUrl)
    }
    var opImageRequested by remember(item.id, boardUrl) {
        mutableStateOf(false)
    }

    LaunchedEffect(item.id, boardUrl, repository) {
        if (boardUrl.isNullOrBlank() || item.id.isBlank() || opImageRequested) return@LaunchedEffect
        opImageRequested = true
        val fetchedUrl = repository.fetchOpImageUrl(boardUrl, item.id)
        if (!fetchedUrl.isNullOrBlank()) {
            resolvedThumbnailUrl = fetchedUrl
        }
    }

    // 4列グリッドでの推定カードサイズ（画面幅360dpの場合約75dp）
    // 1.5倍程度の拡大率に抑えるため、50dpでリクエスト
    val targetSizePx = with(density) { 50.dp.toPx().toInt() }
    val thumbnailToShow = resolvedThumbnailUrl ?: ""
    val imageRequest = remember(thumbnailToShow, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(thumbnailToShow.takeIf { it.isNotBlank() })
            .crossfade(true)
            .size(targetSizePx, targetSizePx)
            .precision(Precision.INEXACT)
            .scale(Scale.FIT)
            .build()
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                shape = MaterialTheme.shapes.small
            ),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailToShow.isBlank()) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AsyncImage(
                        model = imageRequest,
                        imageLoader = LocalFutachaImageLoader.current,
                        contentDescription = item.title ?: "サムネイル",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (item.replyCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = Color.White,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "${item.replyCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Text(
                    text = item.title ?: "無題",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.bodySmall.fontSize
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogTopBar(
    board: BoardSummary?,
    mode: CatalogMode,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onNavigationClick: () -> Unit,
    onModeSelected: (CatalogMode) -> Unit,
    onMenuAction: (CatalogMenuAction) -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "履歴を開く"
                )
            }
        },
        title = {
            if (isSearchActive) {
                CatalogSearchTextField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = board?.name ?: "カタログ",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            if (isSearchActive) {
                IconButton(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            onSearchQueryChange("")
                        } else {
                            onSearchActiveChange(false)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "検索を閉じる"
                    )
                }
            } else {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "検索"
                    )
                }
                Box {
                    IconButton(onClick = { isMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "メニュー"
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        CatalogMenuAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = {
                                    isMenuExpanded = false
                                    onMenuAction(action)
                                }
                            )
                        }
                    }
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
private fun CatalogSearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .padding(end = 8.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        placeholder = { Text("スレタイ検索") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null
            )
        },
        trailingIcon = null,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
            unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
            cursorColor = MaterialTheme.colorScheme.onPrimary,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun CatalogNavigationBar(
    current: CatalogNavDestination?,
    onNavigate: (CatalogNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        CatalogNavDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label) }
            )
        }
    }
}

private fun List<CatalogItem>.filterByQuery(query: String): List<CatalogItem> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return this
    val normalizedQuery = trimmedQuery.lowercase()
    return filter { item ->
        val titleMatch = item.title?.lowercase()?.contains(normalizedQuery) == true
        val idMatch = item.id.lowercase().contains(normalizedQuery)
        val threadMatch = item.threadUrl.lowercase().contains(normalizedQuery)
        titleMatch || idMatch || threadMatch
    }
}

private enum class CatalogMenuAction(val label: String) {
    Display("表示オプション"),
    Toolbar("ツールバー編集"),
    Settings("設定"),
    Help("ヘルプ")
}

private enum class CatalogNavDestination(val label: String, val icon: ImageVector) {
    CreateThread("スレッド作成", Icons.Rounded.Add),
    RefreshCatalog("カタログ更新", Icons.Rounded.Refresh),
    Mode("モード", Icons.Rounded.Sort),
    Settings("設定", Icons.Rounded.Settings)
}

sealed interface ThreadUiState {
    data object Loading : ThreadUiState
    data class Error(val message: String = "スレッドを読み込めませんでした") : ThreadUiState
    data class Success(val page: ThreadPage) : ThreadUiState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FlowPreview::class)
@Composable
fun ThreadScreen(
    board: BoardSummary,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    initialReplyCount: Int?,
    onBack: () -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = {},
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = {},
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = {},
    onHistoryRefresh: suspend () -> Unit = {},
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    repository: BoardRepository? = null,
    httpClient: io.ktor.client.HttpClient? = null,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    modifier: Modifier = Modifier
) {
    val activeRepository = remember(repository) {
        repository ?: FakeBoardRepository()
    }
    val uiState = remember { mutableStateOf<ThreadUiState>(ThreadUiState.Loading) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerOpen by remember {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
        }
    }
    var actionInProgress by remember { mutableStateOf(false) }
    val saidaneOverrides = remember(threadId) { mutableStateMapOf<String, String>() }
    var actionTargetPost by remember { mutableStateOf<Post?>(null) }
    var deleteDialogTarget by remember { mutableStateOf<Post?>(null) }
    var isActionSheetVisible by remember { mutableStateOf(false) }
    var pendingDeletePassword by remember { mutableStateOf("") }
    var pendingDeleteImageOnly by remember { mutableStateOf(false) }
    var lastUsedDeleteKey by rememberSaveable(board.id) { mutableStateOf("") }
    var isReplyDialogVisible by remember { mutableStateOf(false) }
    var replyName by rememberSaveable(board.id) { mutableStateOf("") }
    var replyEmail by rememberSaveable(board.id) { mutableStateOf("") }
    var replySubject by remember { mutableStateOf("") }
    var replyComment by remember { mutableStateOf("") }
    var replyPassword by rememberSaveable(board.id) { mutableStateOf("") }
    var replyImageData by remember { mutableStateOf<com.valoser.futacha.shared.util.ImageData?>(null) }
    var isGalleryVisible by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isHistoryRefreshing by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewVideoUrl by remember { mutableStateOf<String?>(null) }
    var saveProgress by remember { mutableStateOf<com.valoser.futacha.shared.model.SaveProgress?>(null) }
    val handleMediaClick: (String, MediaType) -> Unit = { url, mediaType ->
        when (mediaType) {
            MediaType.Image -> previewImageUrl = url
            MediaType.Video -> previewVideoUrl = url
        }
    }
    val handleHistoryRefresh: () -> Unit = handleHistoryRefresh@{
        if (isHistoryRefreshing) return@handleHistoryRefresh
        coroutineScope.launch {
            isHistoryRefreshing = true
            snackbarHostState.showSnackbar("履歴を更新中...")
            try {
                onHistoryRefresh()
                snackbarHostState.showSnackbar("履歴を更新しました")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("履歴の更新に失敗しました: ${e.message ?: "不明なエラー"}")
            } finally {
                isHistoryRefreshing = false
            }
        }
    }

    PlatformBackHandler(enabled = isDrawerOpen) {
        coroutineScope.launch { drawerState.close() }
    }
    PlatformBackHandler(enabled = !isDrawerOpen) {
        onBack()
    }

            val refreshThread: () -> Unit = remember(board.url, threadId, activeRepository) {
                {
                    coroutineScope.launch {
                        uiState.value = ThreadUiState.Loading
                        try {
                            val page = withContext(Dispatchers.IO) {
                                activeRepository.getThread(board.url, threadId)
                            }
                            if (isActive) {
                                uiState.value = ThreadUiState.Success(page)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (isActive) {
                                val message = when {
                                    e.message?.contains("timeout", ignoreCase = true) == true -> "タイムアウト: サーバーが応答しません"
                                    e.message?.contains("404") == true -> "スレッドが見つかりません (404)"
                                    e.message?.contains("500") == true -> "サーバーエラー (500)"
                                    e.message?.contains("HTTP error") == true -> "ネットワークエラー: ${e.message}"
                                    e.message?.contains("exceeds maximum") == true -> "データサイズが大きすぎます"
                                    else -> "スレッドを読み込めませんでした: ${e.message ?: "不明なエラー"}"
                                }
                                uiState.value = ThreadUiState.Error(message)
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    }
                    Unit
                }
            }

    LaunchedEffect(board.url, threadId) {
        refreshThread()
    }

    val currentState = uiState.value
    val resolvedReplyCount: Int? = when (currentState) {
        is ThreadUiState.Success -> currentState.page.posts.size
        else -> initialReplyCount
    }
    val resolvedThreadTitle = when (currentState) {
        is ThreadUiState.Success -> threadTitle ?: currentState.page.posts.firstOrNull()?.subject ?: "スレッド"
        else -> threadTitle ?: "スレッド"
    }
    val expiresLabel = (currentState as? ThreadUiState.Success)
        ?.page
        ?.expiresAtLabel
        ?.takeIf { it.isNotBlank() }
    val statusLabel = buildString {
        resolvedReplyCount?.let { append("${it}レス") }
        if (!expiresLabel.isNullOrBlank()) {
            if (isNotEmpty()) append(" / ")
            append(expiresLabel)
        }
    }.ifBlank { null }

    val density = LocalDensity.current
    val backSwipeEdgePx = remember(density) { with(density) { 48.dp.toPx() } }
    val backSwipeTriggerPx = remember(density) { with(density) { 96.dp.toPx() } }
    val initialHistoryEntry = remember(threadId) {
        history.firstOrNull { it.threadId == threadId }
    }
    val lazyListState = remember(threadId, initialHistoryEntry) {
        LazyListState(
            initialHistoryEntry?.lastReadItemIndex ?: 0,
            initialHistoryEntry?.lastReadItemOffset ?: 0
        )
    }

    var isSearchActive by rememberSaveable(threadId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(threadId) { mutableStateOf("") }
    var currentSearchResultIndex by remember(threadId) { mutableStateOf(0) }
    val currentPage = (currentState as? ThreadUiState.Success)?.page
    val searchMatches = if (isSearchActive && searchQuery.isNotBlank() && currentPage != null) {
        buildThreadSearchMatches(currentPage.posts, searchQuery)
    } else {
        emptyList()
    }
    val postHighlightRanges = if (isSearchActive) {
        searchMatches.associate { it.postId to it.highlightRanges }
    } else {
        emptyMap()
    }

    LaunchedEffect(searchQuery, threadId) {
        currentSearchResultIndex = 0
    }

    LaunchedEffect(searchMatches) {
        if (searchMatches.isEmpty()) {
            currentSearchResultIndex = 0
        } else if (currentSearchResultIndex !in searchMatches.indices) {
            currentSearchResultIndex = 0
        }
    }

    fun scrollToSearchMatch(targetIndex: Int) {
        val target = searchMatches.getOrNull(targetIndex) ?: return
        coroutineScope.launch {
            lazyListState.animateScrollToItem(target.postIndex)
        }
    }

    fun focusCurrentSearchMatch() {
        if (searchMatches.isNotEmpty()) {
            scrollToSearchMatch(currentSearchResultIndex)
        }
    }

    fun moveToNextSearchMatch() {
        if (searchMatches.isEmpty()) return
        val nextIndex = if (currentSearchResultIndex + 1 >= searchMatches.size) 0 else currentSearchResultIndex + 1
        currentSearchResultIndex = nextIndex
        scrollToSearchMatch(nextIndex)
    }

    fun moveToPreviousSearchMatch() {
        if (searchMatches.isEmpty()) return
        val previousIndex = if (currentSearchResultIndex - 1 < 0) searchMatches.lastIndex else currentSearchResultIndex - 1
        currentSearchResultIndex = previousIndex
        scrollToSearchMatch(previousIndex)
    }

    LaunchedEffect(threadId) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .debounce(500)
            .collect { (index, offset) ->
                if (index > 0 || offset > 0) {
                    onScrollPositionPersist(threadId, index, offset)
                }
            }
    }

    val handleHistorySelection: (ThreadHistoryEntry) -> Unit = { entry ->
        coroutineScope.launch { drawerState.close() }
        onHistoryEntrySelected(entry)
    }

    fun launchThreadAction(
        successMessage: String,
        failurePrefix: String,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit
    ) {
        coroutineScope.launch {
            if (actionInProgress) {
                snackbarHostState.showSnackbar("処理中です…")
                return@launch
            }
            actionInProgress = true
            try {
                block()
                onSuccess()
                snackbarHostState.showSnackbar(successMessage)
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                snackbarHostState.showSnackbar("$failurePrefix$detail")
            } finally {
                actionInProgress = false
            }
        }
    }

    val handleSaidaneAction: (Post) -> Unit = { post ->
        isActionSheetVisible = false
        val baseLabel = saidaneOverrides[post.id] ?: post.saidaneLabel
        launchThreadAction(
            successMessage = "そうだねを送信しました",
            failurePrefix = "そうだねに失敗しました",
            onSuccess = {
                saidaneOverrides[post.id] = incrementSaidaneLabel(baseLabel)
            }
        ) {
            activeRepository.voteSaidane(board.url, threadId, post.id)
        }
    }

    val handleDelRequest: (Post) -> Unit = { post ->
        isActionSheetVisible = false
        launchThreadAction(
            successMessage = "DEL依頼を送信しました",
            failurePrefix = "DEL依頼に失敗しました"
        ) {
            activeRepository.requestDeletion(board.url, threadId, post.id, DEFAULT_DEL_REASON_CODE)
        }
    }

    val openDeleteDialog: (Post) -> Unit = { post ->
        isActionSheetVisible = false
        deleteDialogTarget = post
        pendingDeletePassword = lastUsedDeleteKey
        pendingDeleteImageOnly = false
    }

    val performRefresh: () -> Unit = {
        if (!isRefreshing) {
            coroutineScope.launch {
                isRefreshing = true
                val savedIndex = lazyListState.firstVisibleItemIndex
                val savedOffset = lazyListState.firstVisibleItemScrollOffset
                try {
                    val page = activeRepository.getThread(board.url, threadId)
                    uiState.value = ThreadUiState.Success(page)
                    lazyListState.scrollToItem(savedIndex, savedOffset)

                    // 履歴のエントリーを更新
                    val currentEntry = history.firstOrNull { it.threadId == threadId }
                    if (currentEntry != null) {
                        val updatedEntry = currentEntry.copy(
                            replyCount = page.posts.size,
                            title = page.posts.firstOrNull()?.subject ?: currentEntry.title,
                            lastVisitedEpochMillis = Clock.System.now().toEpochMilliseconds()
                        )
                        onHistoryEntryUpdated(updatedEntry)
                    }

                    snackbarHostState.showSnackbar("スレッドを更新しました")
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        snackbarHostState.showSnackbar("更新に失敗しました")
                    }
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    val handleBatchDelete: () -> Unit = {
        coroutineScope.launch {
            history.forEach { entry ->
                onHistoryEntryDismissed(entry)
            }
            snackbarHostState.showSnackbar("履歴を一括削除しました")
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isDrawerOpen,
        drawerContent = {
            HistoryDrawerContent(
                history = history,
                onHistoryEntryDismissed = onHistoryEntryDismissed,
                onHistoryEntrySelected = handleHistorySelection,
                onBoardClick = {
                    coroutineScope.launch {
                        drawerState.close()
                        onBack()
                    }
                },
                onRefreshClick = handleHistoryRefresh,
                onThreadRefreshClick = {
                    performRefresh()
                },
                onBatchDeleteClick = handleBatchDelete,
                onSettingsClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("設定はモック動作です")
                    }
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ThreadTopBar(
                    boardName = board.name,
                    threadTitle = resolvedThreadTitle,
                    replyCount = resolvedReplyCount,
                    statusLabel = statusLabel,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    currentSearchIndex = currentSearchResultIndex,
                    totalSearchMatches = searchMatches.size,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchPrev = { moveToPreviousSearchMatch() },
                    onSearchNext = { moveToNextSearchMatch() },
                    onSearchSubmit = { focusCurrentSearchMatch() },
                    onSearchClose = { isSearchActive = false },
                    onBack = onBack,
                    onOpenHistory = { coroutineScope.launch { drawerState.open() } },
                    onSearch = { isSearchActive = true }
                )
            },
            bottomBar = {
                ThreadActionBar(
                    onAction = { action ->
                        when (action) {
                            ThreadActionBarItem.Reply -> {
                                if (replyPassword.isBlank()) {
                                    replyPassword = lastUsedDeleteKey
                                }
                                isReplyDialogVisible = true
                            }
                            ThreadActionBarItem.ScrollToTop -> {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(0)
                                }
                            }
                            ThreadActionBarItem.ScrollToBottom -> {
                                coroutineScope.launch {
                                    val currentState = uiState.value
                                    if (currentState is ThreadUiState.Success) {
                                        val lastIndex = currentState.page.posts.size - 1
                                        if (lastIndex >= 0) {
                                            lazyListState.animateScrollToItem(lastIndex)
                                        }
                                    }
                                }
                            }
                            ThreadActionBarItem.Refresh -> {
                                val savedIndex = lazyListState.firstVisibleItemIndex
                                val savedOffset = lazyListState.firstVisibleItemScrollOffset
                                coroutineScope.launch {
                                    try {
                                        val page = activeRepository.getThread(board.url, threadId)
                                        if (isActive) {
                                            uiState.value = ThreadUiState.Success(page)
                                            snackbarHostState.showSnackbar("スレッドを更新しました")
                                            lazyListState.scrollToItem(savedIndex, savedOffset)
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("更新に失敗しました: ${e.message ?: "不明なエラー"}")
                                    }
                                }
                            }
                            ThreadActionBarItem.Gallery -> {
                                isGalleryVisible = true
                            }
                            ThreadActionBarItem.Save -> {
                                val currentStateValue = currentState
                                if (currentStateValue is ThreadUiState.Success) {
                                    if (httpClient != null && fileSystem != null) {
                                        coroutineScope.launch {
                                            try {
                                                // ThreadSaveServiceを作成
                                                val saveService = com.valoser.futacha.shared.service.ThreadSaveService(
                                                    httpClient = httpClient,
                                                    fileSystem = fileSystem
                                                )

                                                // 進捗を監視
                                                val progressJob = launch {
                                                    saveService.saveProgress.collect { progress ->
                                                        saveProgress = progress
                                                    }
                                                }

                                                // スレッドを保存
                                                val page = currentStateValue.page
                                                val result = saveService.saveThread(
                                                    threadId = threadId,
                                                    boardId = board.id,
                                                    boardName = board.name,
                                                    boardUrl = board.url,
                                                    title = page.posts.firstOrNull()?.subject ?: threadTitle ?: "無題",
                                                    expiresAtLabel = page.expiresAtLabel,
                                                    posts = page.posts
                                                )

                                                progressJob.cancel()

                                                result.onSuccess { savedThread ->
                                                    // SavedThreadRepositoryに追加
                                                    val repository = com.valoser.futacha.shared.repository.SavedThreadRepository(fileSystem)
                                                    repository.addThreadToIndex(savedThread)

                                                    saveProgress = null
                                                    snackbarHostState.showSnackbar("スレッドを保存しました")
                                                }.onFailure { error ->
                                                    saveProgress = null
                                                    snackbarHostState.showSnackbar("保存に失敗しました: ${error.message}")
                                                }
                                            } catch (e: Exception) {
                                                saveProgress = null
                                                snackbarHostState.showSnackbar("エラーが発生しました: ${e.message}")
                                            }
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("保存機能が利用できません")
                                        }
                                    }
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("スレッドの読み込みが完了していません")
                                    }
                                }
                            }
                            else -> {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("${action.label} はモック動作です")
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(isDrawerOpen) {
                    if (!isDrawerOpen) return@pointerInput
                    awaitPointerEventScope {
                        awaitFirstDown()
                        coroutineScope.launch { drawerState.close() }
                    }
                }
                .pointerInput(onBack, isDrawerOpen, backSwipeEdgePx, backSwipeTriggerPx) {
                    if (isDrawerOpen) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x > backSwipeEdgePx) {
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        var totalDx = 0f
                        var totalDy = 0f
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            totalDx = (totalDx + delta.x).coerceAtLeast(0f)
                            totalDy += abs(delta.y)
                            if (totalDx > backSwipeTriggerPx && totalDx > totalDy) {
                                change.consume()
                                onBack()
                                return@awaitEachGesture
                            }
                        }
                    }
                }
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 0.dp)

            Box(modifier = contentModifier) {
                when (val state = uiState.value) {
                    ThreadUiState.Loading -> ThreadLoading(modifier = Modifier.matchParentSize())
                    is ThreadUiState.Error -> ThreadError(
                        message = state.message,
                        modifier = Modifier.matchParentSize(),
                        onRetry = refreshThread
                    )

                    is ThreadUiState.Success -> {
                    ThreadContent(
                        page = state.page,
                        listState = lazyListState,
                        saidaneOverrides = saidaneOverrides,
                        searchHighlightRanges = postHighlightRanges,
                        onPostLongPress = { post ->
                                actionTargetPost = post
                                isActionSheetVisible = true
                            },
                            onSaidaneClick = handleSaidaneAction,
                            onMediaClick = handleMediaClick,
                            onRefresh = performRefresh,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                if (actionInProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    val sheetTarget = actionTargetPost
    if (isActionSheetVisible && sheetTarget != null) {
        ThreadPostActionSheet(
            post = sheetTarget,
            onDismiss = {
                isActionSheetVisible = false
                actionTargetPost = null
            },
            onSaidane = { handleSaidaneAction(sheetTarget) },
            onDelRequest = { handleDelRequest(sheetTarget) },
            onDelete = { openDeleteDialog(sheetTarget) }
        )
    }

    val deleteTarget = deleteDialogTarget
    if (deleteTarget != null) {
        DeleteByUserDialog(
            post = deleteTarget,
            password = pendingDeletePassword,
            onPasswordChange = { pendingDeletePassword = it },
            imageOnly = pendingDeleteImageOnly,
            onImageOnlyChange = { pendingDeleteImageOnly = it },
            onDismiss = {
                deleteDialogTarget = null
                pendingDeletePassword = ""
                pendingDeleteImageOnly = false
            },
            onConfirm = {
                val trimmed = pendingDeletePassword.trim()
                if (trimmed.isBlank()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("削除キーを入力してください") }
                    return@DeleteByUserDialog
                }
                val imageOnly = pendingDeleteImageOnly
                deleteDialogTarget = null
                pendingDeletePassword = ""
                pendingDeleteImageOnly = false
                lastUsedDeleteKey = trimmed
                launchThreadAction(
                    successMessage = "本人削除を実行しました",
                    failurePrefix = "本人削除に失敗しました",
                    onSuccess = { refreshThread() }
                ) {
                    activeRepository.deleteByUser(
                        board.url,
                        threadId,
                        deleteTarget.id,
                        trimmed,
                        imageOnly
                    )
                }
            }
        )
    }

    if (isReplyDialogVisible) {
        ThreadReplyDialog(
            boardName = board.name,
            threadTitle = resolvedThreadTitle,
            name = replyName,
            onNameChange = { replyName = it },
            email = replyEmail,
            onEmailChange = { replyEmail = it },
            subject = replySubject,
            onSubjectChange = { replySubject = it },
            comment = replyComment,
            onCommentChange = { replyComment = it },
            password = replyPassword,
            onPasswordChange = { replyPassword = it },
            selectedImage = replyImageData,
            onImageSelected = { replyImageData = it },
            onDismiss = { isReplyDialogVisible = false },
            onSubmit = {
                val trimmedPassword = replyPassword.trim()
                if (trimmedPassword.isBlank()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("削除キーを入力してください") }
                    return@ThreadReplyDialog
                }
                if (replyComment.trim().isBlank()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("コメントを入力してください") }
                    return@ThreadReplyDialog
                }
                isReplyDialogVisible = false
                val name = replyName
                val email = replyEmail
                val subject = replySubject
                val comment = replyComment
                val imageData = replyImageData
                val textOnly = imageData == null
                replySubject = ""
                replyComment = ""
                replyImageData = null
                lastUsedDeleteKey = trimmedPassword
                launchThreadAction(
                    successMessage = "返信を送信しました",
                    failurePrefix = "返信の送信に失敗しました",
                    onSuccess = { refreshThread() }
                ) {
                    activeRepository.replyToThread(
                        board.url,
                        threadId,
                        name,
                        email,
                        subject,
                        comment,
                        trimmedPassword,
                        imageData?.bytes,
                        imageData?.fileName,
                        textOnly
                    )
                }
            },
            onClear = {
                replyName = ""
                replyEmail = ""
                replySubject = ""
                replyComment = ""
                replyPassword = ""
                replyImageData = null
            }
        )
    }

    previewImageUrl?.let { imageUrl ->
        ImagePreviewDialog(
            imageUrl = imageUrl,
            onDismiss = {
                previewImageUrl = null
            }
        )
    }

    previewVideoUrl?.let { videoUrl ->
        VideoPreviewDialog(
            videoUrl = videoUrl,
            onDismiss = {
                previewVideoUrl = null
            }
        )
    }

    val currentSuccessState = uiState.value as? ThreadUiState.Success
    if (isGalleryVisible && currentSuccessState != null) {
        ThreadImageGallery(
            posts = currentSuccessState.page.posts,
            onDismiss = { isGalleryVisible = false },
            onImageClick = { post ->
                val index = currentSuccessState.page.posts.indexOfFirst { it.id == post.id }
                if (index >= 0) {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(index)
                    }
                }
            }
        )
    }

    // 保存進捗ダイアログ
    SaveProgressDialog(
        progress = saveProgress,
        onDismissRequest = {
            saveProgress = null
        }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadTopBar(
    boardName: String,
    threadTitle: String,
    replyCount: Int?,
    statusLabel: String?,
    isSearchActive: Boolean,
    searchQuery: String,
    currentSearchIndex: Int,
    totalSearchMatches: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onSearch: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }
    val displayIndex = if (totalSearchMatches <= 0) {
        0
    } else {
        (currentSearchIndex.coerceIn(0, totalSearchMatches - 1) + 1)
    }
    TopAppBar(
        navigationIcon = {
            if (!isSearchActive) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "戻る"
                    )
                }
            }
        },
        title = {
            if (isSearchActive) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("スレ内検索") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .focusRequester(focusRequester),
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.onPrimary,
                                focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            trailingIcon = {
                                IconButton(onClick = onSearchClose) {
                                    Icon(Icons.Rounded.Close, contentDescription = "検索を閉じる")
                                }
                            }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$displayIndex / $totalSearchMatches",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onSearchPrev, enabled = totalSearchMatches > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "前の検索結果")
                        }
                        IconButton(onClick = onSearchNext, enabled = totalSearchMatches > 0) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "次の検索結果")
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = threadTitle,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = boardName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                        if (statusLabel == null) {
                            replyCount?.let {
                                Text(
                                    text = "  /  ${it}レス",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    statusLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "スレ内検索"
                    )
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "履歴を開く"
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
private fun ThreadLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun ThreadError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("再読み込み")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadContent(
    page: ThreadPage,
    listState: LazyListState,
    saidaneOverrides: Map<String, String>,
    searchHighlightRanges: Map<String, List<IntRange>>,
    onPostLongPress: (Post) -> Unit,
    onSaidaneClick: (Post) -> Unit,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val posterIdLabels = remember(page.posts) {
        buildPosterIdLabels(page.posts)
    }
    val postIndex = remember(page.posts) { page.posts.associateBy { it.id } }
    val referencedByMap = remember(page.posts) { buildReferencedPostsMap(page.posts) }
    val postsByPosterId = remember(page.posts) { buildPostsByPosterId(page.posts) }
    var quotePreviewState by remember(page.posts) { mutableStateOf<QuotePreviewState?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var dragOffset by remember { mutableStateOf(0f) }
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            page.deletedNotice?.takeIf { it.isNotBlank() }?.let { notice ->
                item(key = "thread-notice") {
                    ThreadNoticeCard(message = notice)
                }
            }
            itemsIndexed(
                items = page.posts,
                key = { _, post -> post.id }
            ) { index, post ->
                val normalizedPosterId = normalizePosterIdValue(post.posterId)
                ThreadPostCard(
                    post = post,
                    isOp = index == 0,
                    posterIdLabel = posterIdLabels[post.id],
                    posterIdValue = normalizedPosterId,
                    saidaneLabelOverride = saidaneOverrides[post.id],
                    highlightRanges = searchHighlightRanges[post.id] ?: emptyList(),
                    onQuoteClick = { reference ->
                        val targets = reference.targetPostIds.mapNotNull { postIndex[it] }
                        if (targets.isNotEmpty()) {
                            quotePreviewState = QuotePreviewState(
                                quoteText = reference.text,
                                targetPosts = targets,
                                posterIdLabels = posterIdLabels
                            )
                        }
                    },
                    onPosterIdClick = normalizedPosterId
                        ?.let { normalizedId ->
                            postsByPosterId[normalizedId]
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { sameIdPosts ->
                                    {
                                        quotePreviewState = QuotePreviewState(
                                            quoteText = "ID:$normalizedId のレス",
                                            targetPosts = sameIdPosts,
                                            posterIdLabels = posterIdLabels
                                        )
                                    }
                                }
                        },
                    onReferencedByClick = referencedByMap[post.id]
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { referencingPosts ->
                            {
                                quotePreviewState = QuotePreviewState(
                                    quoteText = ">>${post.id} を引用したレス",
                                    targetPosts = referencingPosts,
                                    posterIdLabels = posterIdLabels
                                )
                            }
                        },
                    onSaidaneClick = { onSaidaneClick(post) },
                    onMediaClick = onMediaClick,
                    onLongPress = { onPostLongPress(post) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (index != page.posts.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                }
            }
            page.expiresAtLabel?.takeIf { it.isNotBlank() }?.let { footerLabel ->
                item(key = "thread-expires-label") {
                    Text(
                        text = footerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    )
                }
            }
        }

            if (isAtBottom && !isRefreshing) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.BottomCenter)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { dragOffset = 0f },
                                onDragEnd = {
                                    if (dragOffset > 100) {
                                        coroutineScope.launch { onRefresh() }
                                    }
                                    dragOffset = 0f
                                },
                                onDragCancel = { dragOffset = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount < 0) {
                                        dragOffset += -dragAmount
                                    }
                                }
                            )
                        }
                )
            }
        }
        }
        ThreadScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(vertical = 12.dp, horizontal = 4.dp)
        )
    }
    quotePreviewState?.let { state ->
        QuotePreviewDialog(
            state = state,
            onDismiss = { quotePreviewState = null },
            onMediaClick = onMediaClick,
            onQuoteClick = { reference ->
                val targets = reference.targetPostIds.mapNotNull { postIndex[it] }
                if (targets.isNotEmpty()) {
                    quotePreviewState = state.copy(
                        quoteText = reference.text,
                        targetPosts = targets
                    )
                }
            }
        )
    }
}

@Composable
private fun ThreadScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 0) return
    val viewportHeightPx = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    if (viewportHeightPx <= 0) return
    val avgItemSizePx = visibleItems
        .map { it.size }
        .filter { it > 0 }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toFloat()
        ?: return
    val contentHeightPx = avgItemSizePx * totalItems
    if (contentHeightPx <= viewportHeightPx) return

    val firstVisibleSize = visibleItems.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
    val partialIndex = listState.firstVisibleItemScrollOffset / firstVisibleSize.toFloat()
    val totalScrollableItems = (totalItems - visibleItems.size).coerceAtLeast(1)
    val scrollFraction = ((listState.firstVisibleItemIndex + partialIndex) / totalScrollableItems)
        .coerceIn(0f, 1f)
    val thumbHeightFraction = (viewportHeightPx / contentHeightPx).coerceIn(0.05f, 1f)

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(6.dp)
    ) {
        val trackWidth = size.width
        val trackHeight = size.height
        val trackCornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
        drawRoundRect(
            color = trackColor,
            size = Size(trackWidth, trackHeight),
            cornerRadius = trackCornerRadius
        )
        val thumbHeightPx = (trackHeight * thumbHeightFraction).coerceAtLeast(trackWidth)
        val thumbOffsetPx = (trackHeight - thumbHeightPx) * scrollFraction
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(x = 0f, y = thumbOffsetPx),
            size = Size(trackWidth, thumbHeightPx),
            cornerRadius = trackCornerRadius
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadPostCard(
    post: Post,
    isOp: Boolean,
    posterIdLabel: PosterIdLabel?,
    posterIdValue: String?,
    saidaneLabelOverride: String?,
    highlightRanges: List<IntRange> = emptyList(),
    onQuoteClick: (QuoteReference) -> Unit,
    onPosterIdClick: (() -> Unit)? = null,
    onReferencedByClick: (() -> Unit)? = null,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onSaidaneClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val backgroundColor = when {
        post.isDeleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }
    val saidaneLabel = saidaneLabelOverride ?: post.saidaneLabel
    val cardModifier = if (onLongPress != null) {
        modifier.combinedClickable(
            onClick = {},
            onLongClick = onLongPress
        )
    } else {
        modifier
    }
    val imageLoader = LocalFutachaImageLoader.current
    val previewUrl = post.imageUrl?.takeIf { it.isNotBlank() }
    if (previewUrl != null) {
        LaunchedEffect(previewUrl) {
            imageLoader.enqueue(
                ImageRequest.Builder(platformContext)
                    .data(previewUrl)
                    .crossfade(false)
                    .build()
            )
        }
    }

    Column(
        modifier = cardModifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThreadPostMetadata(
            post = post,
            isOp = isOp,
            posterIdLabel = posterIdLabel,
            posterIdValue = posterIdValue,
            saidaneLabel = saidaneLabel,
            onSaidaneClick = onSaidaneClick,
            onPosterIdClick = onPosterIdClick,
            onReferencedByClick = onReferencedByClick
        )
        val thumbnailForDisplay = post.thumbnailUrl ?: post.imageUrl
            thumbnailForDisplay?.let { displayUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(platformContext)
                        .data(displayUrl)
                        .crossfade(true)
                        .build(),
                    imageLoader = LocalFutachaImageLoader.current,
                    contentDescription = "添付画像",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(backgroundColor)
                    .clickable {
                        val targetUrl = post.imageUrl ?: displayUrl
                        onMediaClick?.invoke(targetUrl, determineMediaType(targetUrl))
                    },
                contentScale = ContentScale.Fit
            )
        }
        ThreadMessageText(
            messageHtml = post.messageHtml,
            isDeleted = post.isDeleted,
            quoteReferences = post.quoteReferences,
            onQuoteClick = onQuoteClick,
            highlightRanges = highlightRanges
        )
    }
}

@Composable
private fun ThreadPostMetadata(
    post: Post,
    isOp: Boolean,
    posterIdLabel: PosterIdLabel?,
    posterIdValue: String?,
    saidaneLabel: String?,
    onSaidaneClick: (() -> Unit)? = null,
    onPosterIdClick: (() -> Unit)? = null,
    onReferencedByClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val subjectText = post.subject?.ifBlank { "無題" } ?: "無題"
        val authorText = post.author?.ifBlank { "名無し" } ?: "名無し"
        val subjectColor = when {
            subjectText.contains("無念") || subjectText.contains("株") -> Color(0xFFD32F2F)
            isOp -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = (post.order ?: 0).toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F)
            )
            Text(
                text = subjectText,
                style = MaterialTheme.typography.titleMedium,
                color = subjectColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = authorText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF2E7D32)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (post.referencedCount > 0) {
                ReplyCountLabel(
                    count = post.referencedCount,
                    onClick = onReferencedByClick
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val timestampText = remember(post.timestamp) {
                extractTimestampWithoutId(post.timestamp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = timestampText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                posterIdLabel?.let { label ->
                    val idModifier = if (posterIdValue != null && onPosterIdClick != null) {
                        Modifier.clickable(onClick = onPosterIdClick)
                    } else {
                        Modifier
                    }
                    Text(
                        modifier = idModifier,
                        text = label.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (label.highlight) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (saidaneLabel != null && onSaidaneClick != null) {
                    SaidaneLink(
                        label = saidaneLabel,
                        onClick = onSaidaneClick
                    )
                }
            }
            Text(
                text = "No.${post.id}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThreadMessageText(
    messageHtml: String,
    isDeleted: Boolean,
    quoteReferences: List<QuoteReference>,
    onQuoteClick: (QuoteReference) -> Unit,
    highlightRanges: List<IntRange> = emptyList(),
    modifier: Modifier = Modifier
) {
    val highlightStyle = SpanStyle(
        background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f)
    )
    val annotated: AnnotatedString = remember(messageHtml, quoteReferences, highlightRanges) {
        buildAnnotatedMessage(messageHtml, quoteReferences, highlightRanges, highlightStyle)
    }
    val textColor = if (isDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        modifier = modifier.pointerInput(annotated, quoteReferences) {
            detectTapGestures { position ->
                val offset = textLayoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                annotated
                    .getStringAnnotations(QUOTE_ANNOTATION_TAG, offset, offset)
                    .firstOrNull()
                    ?.item
                    ?.toIntOrNull()
                    ?.let { index ->
                        quoteReferences
                            .getOrNull(index)
                            ?.takeIf { it.targetPostIds.isNotEmpty() }
                            ?.let(onQuoteClick)
                    }
            }
        },
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        onTextLayout = { textLayoutResult = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadPostActionSheet(
    post: Post,
    onDismiss: () -> Unit,
    onSaidane: () -> Unit,
    onDelRequest: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "No.${post.id} の操作",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.ThumbUp, contentDescription = null)
                },
                headlineContent = { Text("そうだね") },
                supportingContent = { Text("レスにそうだねを送信") },
                modifier = Modifier.clickable { onSaidane() }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.Flag, contentDescription = null)
                },
                headlineContent = { Text("DEL 依頼") },
                supportingContent = { Text("管理人へ削除依頼を送信") },
                modifier = Modifier.clickable { onDelRequest() }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                },
                headlineContent = { Text("削除 (本人)") },
                supportingContent = { Text("削除キーでレスまたは画像を削除") },
                modifier = Modifier.clickable { onDelete() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeleteByUserDialog(
    post: Post,
    password: String,
    onPasswordChange: (String) -> Unit,
    imageOnly: Boolean,
    onImageOnlyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
        title = { Text("No.${post.id} を削除") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("削除キー") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = imageOnly,
                        onCheckedChange = onImageOnlyChange
                    )
                    Text("画像だけ消す")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadImageGallery(
    posts: List<Post>,
    onDismiss: () -> Unit,
    onImageClick: (Post) -> Unit
) {
    val imagesWithPosts = remember(posts) {
        posts.filter { it.imageUrl != null && it.thumbnailUrl != null }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "画像一覧 (${imagesWithPosts.size}枚)",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (imagesWithPosts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "画像がありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                ) {
                    items(imagesWithPosts) { post ->
                        GalleryImageItem(
                            post = post,
                            onClick = {
                                onDismiss()
                                onImageClick(post)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GalleryImageItem(
    post: Post,
    onClick: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(platformContext)
                    .data(post.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = LocalFutachaImageLoader.current,
                contentDescription = "No.${post.id}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = "No.${post.id}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private const val QUOTE_ANNOTATION_TAG = "quote"

private fun buildAnnotatedMessage(
    html: String,
    quoteReferences: List<QuoteReference>,
    highlightRanges: List<IntRange>,
    highlightStyle: SpanStyle
): AnnotatedString {
    val lines = messageHtmlToLines(html)
    var quoteIndex = 0
    return buildAnnotatedString {
        val normalizedHighlights = highlightRanges
            .filter { it.first >= 0 && it.last >= it.first }
            .sortedBy { it.first }
        lines.forEachIndexed { index, line ->
            val content = line.trimEnd()
            val isQuote = content.startsWith(">") || content.startsWith("＞")
            if (isQuote) {
                val spanStyle = SpanStyle(color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                val annotationIndex = quoteIndex
                val reference = quoteReferences.getOrNull(annotationIndex)
                if (reference != null && reference.targetPostIds.isNotEmpty()) {
                    pushStringAnnotation(QUOTE_ANNOTATION_TAG, annotationIndex.toString())
                    appendWithHighlights(content, spanStyle, normalizedHighlights, highlightStyle)
                    pop()
                } else {
                    appendWithHighlights(content, spanStyle, normalizedHighlights, highlightStyle)
                }
                quoteIndex += 1
            } else {
                appendWithHighlights(content, SpanStyle(), normalizedHighlights, highlightStyle)
            }
            if (index != lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendWithHighlights(
    text: String,
    baseStyle: SpanStyle,
    highlightRanges: List<IntRange>,
    highlightStyle: SpanStyle
) {
    if (text.isEmpty()) {
        return
    }
    val textOffset = this.length
    val relevant = highlightRanges.filter { it.last >= textOffset && it.first < textOffset + text.length }
    if (relevant.isEmpty()) {
        withStyle(baseStyle) {
            append(text)
        }
        return
    }
    var cursor = 0
    val textEndOffset = textOffset + text.length
    relevant.forEach { range ->
        val startInText = maxOf(range.first, textOffset) - textOffset
        val endInText = minOf(range.last, textEndOffset - 1) - textOffset + 1
        if (cursor < startInText) {
            withStyle(baseStyle) {
                append(text.substring(cursor, startInText))
            }
        }
        withStyle(baseStyle.merge(highlightStyle)) {
            append(text.substring(startInText, endInText))
        }
        cursor = endInText
    }
    if (cursor < text.length) {
        withStyle(baseStyle) {
            append(text.substring(cursor))
        }
    }
}

private fun messageHtmlToLines(html: String): List<String> {
    val normalized = html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n\n")
    val withoutTags = normalized.replace(Regex("<[^>]+>"), "")
    val decoded = withoutTags
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
    return decoded.lines()
}

private fun messageHtmlToPlainText(html: String): String {
    return messageHtmlToLines(html)
        .map { it.trimEnd() }
        .joinToString("\n")
}

private fun extractTimestampWithoutId(timestamp: String): String {
    val idx = timestamp.indexOf("ID:")
    if (idx == -1) return timestamp.trim()
    return timestamp.substring(0, idx).trimEnd()
}

@Composable
private fun QuotePreviewDialog(
    state: QuotePreviewState,
    onDismiss: () -> Unit,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onQuoteClick: (QuoteReference) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    text = state.quoteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(scrollState)
                ) {
                    state.targetPosts.forEachIndexed { index, post ->
                    ThreadPostCard(
                        post = post,
                        isOp = post.order == 0,
                        posterIdLabel = state.posterIdLabels[post.id],
                        posterIdValue = normalizePosterIdValue(post.posterId),
                        saidaneLabelOverride = null,
                        onQuoteClick = onQuoteClick,
                        onSaidaneClick = null,
                        onLongPress = null,
                        onMediaClick = onMediaClick,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        if (index != state.targetPosts.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class QuotePreviewState(
    val quoteText: String,
    val targetPosts: List<Post>,
    val posterIdLabels: Map<String, PosterIdLabel>
)

private data class ThreadSearchMatch(
    val postId: String,
    val postIndex: Int,
    val highlightRanges: List<IntRange>
)

private fun buildPosterIdLabels(posts: List<Post>): Map<String, PosterIdLabel> {
    if (posts.isEmpty()) return emptyMap()
    val totals = mutableMapOf<String, Int>()
    posts.forEach { post ->
        normalizePosterIdValue(post.posterId)?.let { normalized ->
            totals[normalized] = (totals[normalized] ?: 0) + 1
        }
    }
    if (totals.isEmpty()) return emptyMap()
    val runningCounts = mutableMapOf<String, Int>()
    val labels = mutableMapOf<String, PosterIdLabel>()
    posts.forEach { post ->
        val normalized = normalizePosterIdValue(post.posterId) ?: return@forEach
        val nextIndex = (runningCounts[normalized] ?: 0) + 1
        runningCounts[normalized] = nextIndex
        val total = totals.getValue(normalized)
        labels[post.id] = PosterIdLabel(
            text = formatPosterIdLabel(normalized, nextIndex, total),
            highlight = total > 1 && nextIndex > 1
        )
    }
    return labels
}

private fun buildPostsByPosterId(posts: List<Post>): Map<String, List<Post>> {
    if (posts.isEmpty()) return emptyMap()
    val groups = mutableMapOf<String, MutableList<Post>>()
    posts.forEach { post ->
        val normalized = normalizePosterIdValue(post.posterId) ?: return@forEach
        groups.getOrPut(normalized) { mutableListOf() }.add(post)
    }
    if (groups.isEmpty()) return emptyMap()
    return groups.mapValues { (_, value) -> value.toList() }
}

private fun buildReferencedPostsMap(posts: List<Post>): Map<String, List<Post>> {
    if (posts.isEmpty()) return emptyMap()
    val orderIndex = posts.mapIndexed { index, post -> post.id to index }.toMap()
    val referencedBy = mutableMapOf<String, MutableList<Post>>()
    posts.forEach { source ->
        source.quoteReferences.forEach { reference ->
            reference.targetPostIds.forEach { targetId ->
                val bucket = referencedBy.getOrPut(targetId) { mutableListOf() }
                if (bucket.none { it.id == source.id }) {
                    bucket.add(source)
                }
            }
        }
    }
    if (referencedBy.isEmpty()) return emptyMap()
    return referencedBy.mapValues { (_, value) ->
        value
            .distinctBy { it.id }
            .sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
    }
}

private fun buildThreadSearchMatches(posts: List<Post>, query: String): List<ThreadSearchMatch> {
    if (posts.isEmpty()) return emptyList()
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return emptyList()
    return posts.mapIndexedNotNull { index, post ->
        val haystack = buildSearchTextForPost(post)
        if (haystack.contains(normalizedQuery)) {
            val messagePlain = messageHtmlToPlainText(post.messageHtml)
            val ranges = computeHighlightRanges(messagePlain, normalizedQuery)
            ThreadSearchMatch(post.id, index, ranges)
        } else {
            null
        }
    }
}

private fun buildSearchTextForPost(post: Post): String {
    val builder = StringBuilder()
    post.subject?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    post.author?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    post.posterId?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    builder.appendLine(post.id)
    builder.append(messageHtmlToPlainText(post.messageHtml))
    return builder.toString().lowercase()
}

private fun computeHighlightRanges(text: String, normalizedQuery: String): List<IntRange> {
    if (normalizedQuery.isEmpty()) return emptyList()
    val normalizedText = text.lowercase()
    val ranges = mutableListOf<IntRange>()
    var startIndex = normalizedText.indexOf(normalizedQuery)
    while (startIndex >= 0) {
        val endIndex = startIndex + normalizedQuery.length - 1
        ranges.add(startIndex..endIndex)
        startIndex = normalizedText.indexOf(normalizedQuery, startIndex + normalizedQuery.length)
    }
    return ranges
}

@Composable
private fun ImagePreviewDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    var scale by remember { mutableStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    var swipeDistance by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeDistance += dragAmount
                            if (swipeDistance < -200f) {
                                swipeDistance = 0f
                                onDismiss()
                            }
                        },
                        onDragEnd = { swipeDistance = 0f },
                        onDragCancel = { swipeDistance = 0f }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan: Offset, zoom: Float, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        translation += pan
                    }
                }
        ) {
            val previewRequest = remember(imageUrl) {
                ImageRequest.Builder(platformContext)
                    .data(imageUrl)
                    .crossfade(true)
                    .build()
            }

            AsyncImage(
                model = previewRequest,
                imageLoader = LocalFutachaImageLoader.current,
                contentDescription = "プレビュー画像",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = translation.x,
                        translationY = translation.y
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "プレビューを閉じる"
                )
            }
        }
    }
}

@Composable
private fun VideoPreviewDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    var swipeDistance by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeDistance += dragAmount
                            if (swipeDistance < -200f) {
                                swipeDistance = 0f
                                onDismiss()
                            }
                        },
                        onDragEnd = { swipeDistance = 0f },
                        onDragCancel = { swipeDistance = 0f }
                    )
                }
        ) {
            PlatformVideoPlayer(
                videoUrl = videoUrl,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "プレビューを閉じる"
                )
            }
        }
    }
}

private enum class MediaType {
    Image,
    Video
}

private fun determineMediaType(url: String): MediaType {
    val cleaned = url.substringBefore('?')
    val extension = cleaned.substringAfterLast('.', "").lowercase()
    return if (extension in setOf("mp4", "webm", "mkv", "mov", "avi", "ts", "flv")) {
        MediaType.Video
    } else {
        MediaType.Image
    }
}

private fun normalizePosterIdValue(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val withoutPrefix = if (trimmed.startsWith("ID:", ignoreCase = true)) {
        trimmed.substring(3)
    } else {
        trimmed
    }
    return withoutPrefix.trim().takeIf { it.isNotBlank() }
}

private fun formatPosterIdLabel(value: String, index: Int, total: Int): String {
    val safeIndex = index.coerceAtLeast(1)
    val safeTotal = total.coerceAtLeast(safeIndex)
    return "ID:$value(${safeIndex}/${safeTotal})"
}

private data class PosterIdLabel(
    val text: String,
    val highlight: Boolean
)

@Composable
private fun ThreadNoticeCard(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ReplyCountLabel(
    count: Int,
    onClick: (() -> Unit)? = null
) {
    val labelModifier = onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Text(
        modifier = labelModifier,
        text = "${count}レス",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFD32F2F)
    )
}

@Composable
private fun SaidaneLink(
    label: String,
    onClick: () -> Unit
) {
    val normalized = if (label == "+") "そうだね" else label
    Text(
        text = normalized,
        style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        ),
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ThreadActionBar(
    onAction: (ThreadActionBarItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThreadActionBarItem.entries.forEach { action ->
                IconButton(onClick = { onAction(action) }) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private enum class ThreadActionBarItem(
    val label: String,
    val icon: ImageVector
) {
    Reply("返信", Icons.Rounded.Edit),
    ScrollToTop("最上部", Icons.Filled.ArrowUpward),
    ScrollToBottom("最下部", Icons.Filled.ArrowDownward),
    Refresh("更新", Icons.Rounded.Refresh),
    Gallery("画像", Icons.Outlined.Image),
    Save("保存", Icons.Rounded.Archive),
    Settings("設定", Icons.Rounded.Settings)
}

private const val DEFAULT_DEL_REASON_CODE = "110"

internal fun incrementSaidaneLabel(current: String?): String {
    val normalized = current?.trim().orEmpty()
    val existing = normalized.takeIf { it.isNotBlank() }?.let {
        Regex("(\\d+)$").find(it)?.value?.toIntOrNull()
    } ?: 0
    val next = (existing + 1).coerceAtLeast(1)
    return "そうだねx$next"
}
