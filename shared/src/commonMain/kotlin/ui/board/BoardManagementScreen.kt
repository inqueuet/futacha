package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.ReplyAll
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Divider
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.audio.createTextSpeaker
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.text.RegexOption
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
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean = false,
    onBackgroundRefreshChanged: (Boolean) -> Unit = {},
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
    var isGlobalSettingsVisible by remember { mutableStateOf(false) }
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
                    isGlobalSettingsVisible = true
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
                                            BoardManagementMenuAction.SETTINGS -> {
                                                isGlobalSettingsVisible = true
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

    if (isGlobalSettingsVisible) {
        GlobalSettingsScreen(
            onBack = { isGlobalSettingsVisible = false },
            appVersion = appVersion,
            isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
            onBackgroundRefreshChanged = onBackgroundRefreshChanged
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
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(entry, dismissState) {
        snapshotFlow { dismissState.currentValue }
            .filter { it == SwipeToDismissBoxValue.StartToEnd }
            .collect {
                onDismissed(entry)
            }
    }
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
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean = false,
    onBackgroundRefreshChanged: (Boolean) -> Unit = {},
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
    var showDisplayStyleDialog by remember { mutableStateOf(false) }
    var showCreateThreadDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var isGlobalSettingsVisible by remember { mutableStateOf(false) }
    var isNgManagementVisible by remember { mutableStateOf(false) }
    var isWatchWordsVisible by remember { mutableStateOf(false) }
    var catalogNgFilteringEnabled by rememberSaveable(board?.id) { mutableStateOf(true) }
    val fallbackCatalogNgWordsState = rememberSaveable(board?.id) { mutableStateOf<List<String>>(emptyList()) }
    val catalogNgWordsState = stateStore?.catalogNgWords?.collectAsState(initial = fallbackCatalogNgWordsState.value)
    val catalogNgWords = catalogNgWordsState?.value ?: fallbackCatalogNgWordsState.value
    val fallbackWatchWordsState = rememberSaveable(board?.id) { mutableStateOf<List<String>>(emptyList()) }
    val watchWordsState = stateStore?.watchWords?.collectAsState(initial = fallbackWatchWordsState.value)
    val watchWords = watchWordsState?.value ?: fallbackWatchWordsState.value
    val showNgMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    var lastCatalogItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    suspend fun handleWatchWordMatches(
        catalog: List<CatalogItem>,
        filters: List<String> = watchWords
    ) {
        val normalizedFilters = filters
            .mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
            .distinct()
        if (normalizedFilters.isEmpty()) return
        if (board == null || stateStore == null) return

        val timestamp = Clock.System.now().toEpochMilliseconds()

        catalog.forEach { item ->
            val titleText = item.title?.lowercase().orEmpty()
            if (titleText.isEmpty()) return@forEach
            if (normalizedFilters.any { titleText.contains(it) }) {
                val entry = ThreadHistoryEntry(
                    threadId = item.id,
                    boardId = board.id,
                    title = item.title?.takeIf { it.isNotBlank() } ?: "無題",
                    titleImageUrl = item.thumbnailUrl ?: "",
                    boardName = board.name,
                    boardUrl = board.url,
                    lastVisitedEpochMillis = timestamp,
                    replyCount = item.replyCount
                )
                try {
                    stateStore.upsertHistoryEntry(entry)
                } catch (_: Exception) {
                    // Ignore failures to keep catalog refresh resilient
                }
            }
        }
    }
    val persistCatalogNgWords: (List<String>) -> Unit = { updated ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setCatalogNgWords(updated)
            }
        } else {
            fallbackCatalogNgWordsState.value = updated
        }
    }
    val addCatalogNgWordEntry: (String) -> Unit = { value ->
        val trimmed = value.trim()
        when {
            trimmed.isEmpty() -> showNgMessage("NGワードに含める文字列を入力してください")
            catalogNgWords.any { it.equals(trimmed, ignoreCase = true) } -> showNgMessage("そのNGワードはすでに登録されています")
            else -> {
                persistCatalogNgWords(catalogNgWords + trimmed)
                showNgMessage("NGワードを追加しました")
            }
        }
    }
    val removeCatalogNgWordEntry: (String) -> Unit = { entry ->
        persistCatalogNgWords(catalogNgWords.filterNot { it == entry })
        showNgMessage("NGワードを削除しました")
    }
    val toggleCatalogNgFiltering: () -> Unit = {
        catalogNgFilteringEnabled = !catalogNgFilteringEnabled
        showNgMessage(if (catalogNgFilteringEnabled) "NG表示を有効にしました" else "NG表示を無効にしました")
    }
    val persistWatchWords: (List<String>) -> Unit = { updated ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setWatchWords(updated)
            }
        } else {
            fallbackWatchWordsState.value = updated
        }
    }
    val addWatchWordEntry: (String) -> Unit = { value ->
        val trimmed = value.trim()
        when {
            trimmed.isEmpty() -> showNgMessage("監視ワードを入力してください")
            watchWords.any { it.equals(trimmed, ignoreCase = true) } -> showNgMessage("そのワードはすでに登録されています")
            else -> {
                persistWatchWords(watchWords + trimmed)
                showNgMessage("監視ワードを追加しました")
                coroutineScope.launch {
                    handleWatchWordMatches(lastCatalogItems, watchWords + trimmed)
                }
            }
        }
    }
    val removeWatchWordEntry: (String) -> Unit = { entry ->
        persistWatchWords(watchWords.filterNot { it == entry })
        showNgMessage("監視ワードを削除しました")
    }
    val isPrivacyFilterEnabled by stateStore?.isPrivacyFilterEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val catalogGridState = rememberLazyGridState()
    val catalogListState = rememberLazyListState()
    val persistentDisplayStyleState = stateStore?.catalogDisplayStyle?.collectAsState(initial = CatalogDisplayStyle.Grid)
    var localCatalogDisplayStyle by rememberSaveable { mutableStateOf(CatalogDisplayStyle.Grid) }
    val catalogDisplayStyle = persistentDisplayStyleState?.value ?: localCatalogDisplayStyle
    val updateCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit = { style ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setCatalogDisplayStyle(style)
            }
        } else {
            localCatalogDisplayStyle = style
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

    val performRefresh: () -> Unit = {
        if (!isRefreshing && board != null) {
            coroutineScope.launch {
                isRefreshing = true
                try {
                    val catalog = activeRepository.getCatalog(board.url, catalogMode)
                    uiState.value = CatalogUiState.Success(catalog)
                    lastCatalogItems = catalog
                    handleWatchWordMatches(catalog)
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
                    lastCatalogItems = catalog
                    handleWatchWordMatches(catalog)
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

    val scrollCatalogToTop: () -> Unit = {
        coroutineScope.launch {
            when (catalogDisplayStyle) {
                CatalogDisplayStyle.Grid -> {
                    if (catalogGridState.layoutInfo.totalItemsCount > 0) {
                        catalogGridState.animateScrollToItem(0)
                    }
                }
                CatalogDisplayStyle.List -> {
                    if (catalogListState.layoutInfo.totalItemsCount > 0) {
                        catalogListState.animateScrollToItem(0)
                    }
                }
            }
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
                    isGlobalSettingsVisible = true
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
                        if (action == CatalogMenuAction.Settings) {
                            isGlobalSettingsVisible = true
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("${action.label} はモック動作です")
                            }
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
                            CatalogNavDestination.ScrollToTop -> scrollCatalogToTop()
                            CatalogNavDestination.RefreshCatalog -> performRefresh()
                            CatalogNavDestination.Mode -> showModeDialog = true
                            CatalogNavDestination.Settings -> showSettingsMenu = true
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
            val currentState = uiState.value
            when (val state = currentState) {
                CatalogUiState.Loading -> LoadingCatalog(modifier = contentModifier)
                is CatalogUiState.Error -> CatalogError(message = state.message, modifier = contentModifier)
                is CatalogUiState.Success -> {
                    val sortedItems = catalogMode.applyLocalSort(state.items)
                    val ngFilteredItems = sortedItems.filterByCatalogNgWords(
                        catalogNgWords = catalogNgWords,
                        enabled = catalogNgFilteringEnabled
                    )
                    val visibleItems = ngFilteredItems.filterByQuery(searchQuery)
                    CatalogSuccessContent(
                        items = visibleItems,
                        board = board,
                        repository = activeRepository,
                        isSearching = searchQuery.isNotBlank(),
                        onThreadSelected = onThreadSelected,
                        onRefresh = performRefresh,
                        isRefreshing = isRefreshing,
                        displayStyle = catalogDisplayStyle,
                        gridState = catalogGridState,
                        listState = catalogListState,
                        modifier = contentModifier
                    )
                }
            }
        }

        // Privacy filter overlay - allows interactions to pass through
        if (isPrivacyFilterEnabled) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawRect(color = Color.White.copy(alpha = 0.5f))
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

        if (showDisplayStyleDialog) {
            DisplayStyleDialog(
                currentStyle = catalogDisplayStyle,
                onStyleSelected = { style ->
                    updateCatalogDisplayStyle(style)
                    showDisplayStyleDialog = false
                },
                onDismiss = { showDisplayStyleDialog = false }
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

        val urlLauncher = rememberUrlLauncher()

        if (showSettingsMenu) {
            CatalogSettingsSheet(
                onDismiss = { showSettingsMenu = false },
                onAction = { menuItem ->
                    when (menuItem) {
                        CatalogSettingsMenuItem.ScrollToTop -> scrollCatalogToTop()
                        CatalogSettingsMenuItem.DisplayStyle -> showDisplayStyleDialog = true
                        CatalogSettingsMenuItem.NgManagement -> {
                            isNgManagementVisible = true
                        }
                        CatalogSettingsMenuItem.WatchWords -> {
                            isWatchWordsVisible = true
                        }
                        CatalogSettingsMenuItem.ExternalApp -> {
                            board?.let { b ->
                                val catalogUrl = if (catalogMode.sortParam != null) {
                                    "${b.url.trimEnd('/')}/futaba.php?mode=cat&sort=${catalogMode.sortParam}"
                                } else {
                                    "${b.url.trimEnd('/')}/futaba.php?mode=cat"
                                }
                                urlLauncher(catalogUrl)
                            }
                        }
                        CatalogSettingsMenuItem.Privacy -> {
                            coroutineScope.launch {
                                stateStore?.setPrivacyFilterEnabled(!isPrivacyFilterEnabled)
                            }
                        }
                    }
                    showSettingsMenu = false
                }
            )
        }

        if (isGlobalSettingsVisible) {
            GlobalSettingsScreen(
                onBack = { isGlobalSettingsVisible = false },
                appVersion = appVersion,
                isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
                onBackgroundRefreshChanged = onBackgroundRefreshChanged
            )
        }

        if (isNgManagementVisible) {
            NgManagementSheet(
                ngHeaders = emptyList(),
                ngWords = catalogNgWords,
                ngFilteringEnabled = catalogNgFilteringEnabled,
                onDismiss = { isNgManagementVisible = false },
                onAddHeader = {},
                onAddWord = addCatalogNgWordEntry,
                onRemoveHeader = {},
                onRemoveWord = removeCatalogNgWordEntry,
                onToggleFiltering = toggleCatalogNgFiltering,
                includeHeaderSection = false
            )
        }
        if (isWatchWordsVisible) {
            WatchWordsSheet(
                watchWords = watchWords,
                onAddWord = addWatchWordEntry,
                onRemoveWord = removeWatchWordEntry,
                onDismiss = { isWatchWordsVisible = false }
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
                            trailingIcon = {
                                if (comment.isNotBlank()) {
                                    IconButton(onClick = { onCommentChange("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "コメントをクリア"
                                        )
                                    }
                                }
                            },
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
                                imageVector = Icons.AutoMirrored.Rounded.Send,
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
    displayStyle: CatalogDisplayStyle,
    gridState: LazyGridState,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        CatalogEmptyContent(
            isSearching = isSearching,
            modifier = modifier
        )
    } else {
        when (displayStyle) {
            CatalogDisplayStyle.Grid -> CatalogGrid(
                items = items,
                board = board,
                repository = repository,
                onThreadSelected = onThreadSelected,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing,
                gridState = gridState,
                modifier = modifier
            )
            CatalogDisplayStyle.List -> CatalogList(
                items = items,
                board = board,
                repository = repository,
                onThreadSelected = onThreadSelected,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing,
                listState = listState,
                modifier = modifier
            )
        }
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
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }

    // アニメーション用のオフセット
    val overscrollOffset = remember { Animatable(0f) }

    val isAtTop by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisibleItem != null &&
            firstVisibleItem.index == 0 &&
            firstVisibleItem.offset.y.toFloat() <= edgeOffsetTolerancePx
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (lastVisibleItem == null || totalItems == 0) return@derivedStateOf false
            if (lastVisibleItem.index < totalItems - 1) return@derivedStateOf false
            val viewportEnd = layoutInfo.viewportEndOffset
            val lastItemEnd = lastVisibleItem.offset.y + lastVisibleItem.size.height
            val remainingSpace = viewportEnd - lastItemEnd
            remainingSpace.toFloat() <= edgeOffsetTolerancePx ||
                layoutInfo.visibleItemsInfo.size >= totalItems
        }
    }

    // リフレッシュ完了時にアニメーションで空間を戻す
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    LazyVerticalGrid(
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .offset { IntOffset(0, overscrollOffset.value.toInt()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = 0f

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val dragEvent = event.changes.firstOrNull()

                        if (dragEvent != null && dragEvent.pressed) {
                            val delta = dragEvent.positionChangeIgnoreConsumed().y

                            // 上端で下向きにドラッグ
                            if (isAtTop && delta > 0 && !isRefreshing) {
                                totalDrag += delta
                                val newOffset = (totalDrag * 0.4f).coerceIn(0f, maxOverscrollPx)
                                coroutineScope.launch {
                                    overscrollOffset.snapTo(newOffset)
                                }
                                dragEvent.consume()
                            }
                            // 下端で上向きにドラッグ
                            else if (isAtBottom && delta < 0 && !isRefreshing) {
                                totalDrag += delta
                                val newOffset = (totalDrag * 0.4f).coerceIn(-maxOverscrollPx, 0f)
                                coroutineScope.launch {
                                    overscrollOffset.snapTo(newOffset)
                                }
                                dragEvent.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // ドラッグ終了
                    if (totalDrag > refreshTriggerPx) {
                        coroutineScope.launch { onRefresh() }
                    } else if (totalDrag < -refreshTriggerPx) {
                        coroutineScope.launch { onRefresh() }
                    }

                    totalDrag = 0f
                    coroutineScope.launch {
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            },
        columns = GridCells.Fixed(5),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(
            start = 4.dp,
            end = 4.dp,
            top = 8.dp,
            bottom = 8.dp
        )
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
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CatalogList(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }

    // アニメーション用のオフセット
    val overscrollOffset = remember { Animatable(0f) }

    val isAtTop by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisibleItem != null &&
            firstVisibleItem.index == 0 &&
            firstVisibleItem.offset.toFloat() <= edgeOffsetTolerancePx
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (lastVisibleItem == null || totalItems == 0) return@derivedStateOf false
            if (lastVisibleItem.index < totalItems - 1) return@derivedStateOf false
            val viewportEnd = layoutInfo.viewportEndOffset
            val lastItemEnd = lastVisibleItem.offset + lastVisibleItem.size
            val remainingSpace = viewportEnd - lastItemEnd
            remainingSpace.toFloat() <= edgeOffsetTolerancePx ||
                layoutInfo.visibleItemsInfo.size >= totalItems
        }
    }

    // リフレッシュ完了時にアニメーションで空間を戻す
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, overscrollOffset.value.toInt()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = 0f

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val dragEvent = event.changes.firstOrNull()

                        if (dragEvent != null && dragEvent.pressed) {
                            val delta = dragEvent.positionChangeIgnoreConsumed().y

                            // 上端で下向きにドラッグ
                            if (isAtTop && delta > 0 && !isRefreshing) {
                                totalDrag += delta
                                val newOffset = (totalDrag * 0.4f).coerceIn(0f, maxOverscrollPx)
                                coroutineScope.launch {
                                    overscrollOffset.snapTo(newOffset)
                                }
                                dragEvent.consume()
                            }
                            // 下端で上向きにドラッグ
                            else if (isAtBottom && delta < 0 && !isRefreshing) {
                                totalDrag += delta
                                val newOffset = (totalDrag * 0.4f).coerceIn(-maxOverscrollPx, 0f)
                                coroutineScope.launch {
                                    overscrollOffset.snapTo(newOffset)
                                }
                                dragEvent.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // ドラッグ終了
                    if (totalDrag > refreshTriggerPx) {
                        coroutineScope.launch { onRefresh() }
                    } else if (totalDrag < -refreshTriggerPx) {
                        coroutineScope.launch { onRefresh() }
                    }

                    totalDrag = 0f
                    coroutineScope.launch {
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 8.dp
        )
    ) {
        items(
            items = items,
            key = { it.id }
        ) { catalogItem ->
            CatalogListItem(
                item = catalogItem,
                boardUrl = board?.url,
                repository = repository,
                onClick = { onThreadSelected(catalogItem) }
            )
        }
    }
}

@Composable
private fun rememberResolvedCatalogThumbnailUrl(
    item: CatalogItem,
    boardUrl: String?,
    repository: BoardRepository
): String? {
    // サムネイル画像を保持（初期値）
    val thumbnailUrl = item.thumbnailUrl

    // フルサイズ画像のURLを保持
    var fullsizeUrl by remember(item.id, boardUrl) {
        mutableStateOf<String?>(null)
    }
    var opImageRequested by remember(item.id, boardUrl) {
        mutableStateOf(false)
    }

    // フルサイズ画像を非同期で取得
    LaunchedEffect(item.id, boardUrl, repository) {
        if (boardUrl.isNullOrBlank() || item.id.isBlank() || opImageRequested) return@LaunchedEffect
        opImageRequested = true
        val fetchedUrl = repository.fetchOpImageUrl(boardUrl, item.id)
        if (!fetchedUrl.isNullOrBlank() && fetchedUrl != thumbnailUrl) {
            fullsizeUrl = fetchedUrl
        }
    }

    // フルサイズ画像が取得できていればそれを使用、そうでなければサムネイルを使用
    return fullsizeUrl ?: thumbnailUrl
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
    val resolvedThumbnailUrl = rememberResolvedCatalogThumbnailUrl(
        item = item,
        boardUrl = boardUrl,
        repository = repository
    )

    // 4列グリッドでの推定カードサイズ（画面幅360dpの場合約75dp）
    // 1.5倍程度の拡大率に抑えるため、50dpでリクエスト
    val targetSizePx = with(density) { 50.dp.toPx().toInt() }

    // サムネイル画像（初期表示用）
    val thumbnailUrl = item.thumbnailUrl ?: ""

    // 表示する画像URL（フルサイズまたはサムネイル）
    val imageUrlToShow = resolvedThumbnailUrl ?: ""

    // 画像リクエストを作成（resolvedThumbnailUrlが変わったときのみ再作成）
    val imageRequest = remember(imageUrlToShow, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(imageUrlToShow.takeIf { it.isNotBlank() })
            .crossfade(true)
            .size(targetSizePx, targetSizePx)
            .precision(Precision.INEXACT)
            .scale(Scale.FIT)
            .build()
    }

    // サムネイル画像のリクエスト（フォールバック用）
    val thumbnailRequest = remember(thumbnailUrl, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(thumbnailUrl.takeIf { it.isNotBlank() })
            .size(targetSizePx, targetSizePx)
            .precision(Precision.INEXACT)
            .scale(Scale.FIT)
            .build()
    }

    val imageLoader = LocalFutachaImageLoader.current
    val imagePainter = rememberAsyncImagePainter(
        model = imageRequest,
        imageLoader = imageLoader
    )
    val thumbnailPainter = rememberAsyncImagePainter(
        model = thumbnailRequest,
        imageLoader = imageLoader
    )

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
                if (imageUrlToShow.isBlank()) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // フルサイズ画像がロード中またはエラーの場合はサムネイルを表示
                    val painterState = imagePainter.state
                    @Suppress("USELESS_IS_CHECK", "KotlinConstantConditions")
                    val shouldShowThumbnail = thumbnailUrl.isNotBlank() &&
                                             imageUrlToShow != thumbnailUrl &&
                                             (painterState is AsyncImagePainter.State.Loading ||
                                              painterState is AsyncImagePainter.State.Error)

                    when {
                        shouldShowThumbnail -> {
                            // サムネイル画像を表示（フルサイズ画像のロード中）
                            Image(
                                painter = thumbnailPainter,
                                contentDescription = item.title ?: "サムネイル",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            // フルサイズ画像またはサムネイルのみの場合
                            Image(
                                painter = imagePainter,
                                contentDescription = item.title ?: "サムネイル",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
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

@Composable
private fun CatalogListItem(
    item: CatalogItem,
    boardUrl: String?,
    repository: BoardRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current
    val resolvedThumbnailUrl = rememberResolvedCatalogThumbnailUrl(
        item = item,
        boardUrl = boardUrl,
        repository = repository
    )
    val targetSizePx = with(density) { 72.dp.toPx().toInt() }

    // サムネイル画像（初期表示用）
    val thumbnailUrl = item.thumbnailUrl ?: ""

    // 表示する画像URL（フルサイズまたはサムネイル）
    val imageUrlToShow = resolvedThumbnailUrl ?: ""

    // 画像リクエストを作成
    val imageRequest = remember(imageUrlToShow, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(imageUrlToShow.takeIf { it.isNotBlank() })
            .crossfade(true)
            .size(targetSizePx, targetSizePx)
            .precision(Precision.INEXACT)
            .scale(Scale.FIT)
            .build()
    }

    // サムネイル画像のリクエスト（フォールバック用）
    val thumbnailRequest = remember(thumbnailUrl, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(thumbnailUrl.takeIf { it.isNotBlank() })
            .size(targetSizePx, targetSizePx)
            .precision(Precision.INEXACT)
            .scale(Scale.FIT)
            .build()
    }

    val imageLoader = LocalFutachaImageLoader.current
    val imagePainter = rememberAsyncImagePainter(
        model = imageRequest,
        imageLoader = imageLoader
    )
    val thumbnailPainter = rememberAsyncImagePainter(
        model = thumbnailRequest,
        imageLoader = imageLoader
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrlToShow.isBlank()) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // フルサイズ画像がロード中またはエラーの場合はサムネイルを表示
                    val painterState = imagePainter.state
                    @Suppress("USELESS_IS_CHECK", "KotlinConstantConditions")
                    val shouldShowThumbnail = thumbnailUrl.isNotBlank() &&
                                             imageUrlToShow != thumbnailUrl &&
                                             (painterState is AsyncImagePainter.State.Loading ||
                                              painterState is AsyncImagePainter.State.Error)

                    when {
                        shouldShowThumbnail -> {
                            // サムネイル画像を表示（フルサイズ画像のロード中）
                            Image(
                                painter = thumbnailPainter,
                                contentDescription = item.title ?: "サムネイル",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            // フルサイズ画像またはサムネイルのみの場合
                            Image(
                                painter = imagePainter,
                                contentDescription = item.title ?: "サムネイル",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title?.takeIf { it.isNotBlank() } ?: "無題",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No.${item.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${item.replyCount}レス",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSettingsSheet(
    onDismiss: () -> Unit,
    onAction: (CatalogSettingsMenuItem) -> Unit
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
                text = "設定メニュー",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            CatalogSettingsMenuItem.entries.forEach { menuItem ->
                ListItem(
                    leadingContent = { Icon(imageVector = menuItem.icon, contentDescription = null) },
                    headlineContent = {
                        Column {
                            Text(menuItem.label)
                            menuItem.description?.let { description ->
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onAction(menuItem) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchWordsSheet(
    watchWords: List<String>,
    onAddWord: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "監視ワード",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "タイトルが一致したスレを履歴に追加します",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "閉じる")
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("追加するワード") },
                placeholder = { Text("例: 夏休み") },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                onAddWord(input)
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "追加")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (watchWords.isEmpty()) {
                Text(
                    text = "まだ監視ワードは登録されていません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(watchWords) { entry ->
                        ListItem(
                            headlineContent = { Text(entry) },
                            trailingContent = {
                                IconButton(onClick = { onRemoveWord(entry) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "削除")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayStyleDialog(
    currentStyle: CatalogDisplayStyle,
    onStyleSelected: (CatalogDisplayStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("表示スタイル") },
        text = {
            Column {
                CatalogDisplayStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStyleSelected(style) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = style == currentStyle,
                            onClick = { onStyleSelected(style) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
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

private fun List<CatalogItem>.filterByCatalogNgWords(
    catalogNgWords: List<String>,
    enabled: Boolean
): List<CatalogItem> {
    if (!enabled) return this
    val wordFilters = catalogNgWords.mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    if (wordFilters.isEmpty()) return this
    return filterNot { item ->
        matchesCatalogNgWords(item, wordFilters)
    }
}

private fun matchesCatalogNgWords(
    item: CatalogItem,
    wordFilters: List<String>
): Boolean {
    val titleText = item.title?.lowercase().orEmpty()
    if (titleText.isEmpty()) return false
    return wordFilters.any { titleText.contains(it) }
}

private enum class CatalogMenuAction(val label: String) {
    Settings("設定")
}

private enum class CatalogSettingsMenuItem(
    val label: String,
    val icon: ImageVector,
    val description: String?
) {
    WatchWords("監視ワード", Icons.Rounded.WatchLater, "監視中のワードを編集"),
    NgManagement("NG管理", Icons.Rounded.Block, "NGワード・IDを管理"),
    ExternalApp("外部アプリ", Icons.AutoMirrored.Rounded.OpenInNew, "外部アプリ連携を設定"),
    DisplayStyle("表示の切り替え", Icons.Rounded.ViewModule, "カタログ表示方法を変更"),
    ScrollToTop("一番上に行く", Icons.Rounded.VerticalAlignTop, "グリッドの先頭へ移動"),
    Privacy("プライバシー", Icons.Rounded.Lock, "プライバシー設定を確認")
}

private enum class CatalogNavDestination(val label: String, val icon: ImageVector) {
    CreateThread("スレッド作成", Icons.Rounded.Add),
    ScrollToTop("一番上に行く", Icons.Rounded.VerticalAlignTop),
    RefreshCatalog("カタログ更新", Icons.Rounded.Refresh),
    Mode("モード", Icons.AutoMirrored.Rounded.Sort),
    Settings("設定", Icons.Rounded.Settings)
}

private const val AUTO_SAVE_INTERVAL_MS = 60_000L
private const val THREAD_AUTO_SAVE_TAG = "ThreadAutoSave"

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
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean = false,
    onBackgroundRefreshChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeRepository = remember(repository) {
        repository ?: FakeBoardRepository()
    }
    val uiState = remember { mutableStateOf<ThreadUiState>(ThreadUiState.Loading) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val platformContext = LocalPlatformContext.current
    val textSpeaker = remember(platformContext) { createTextSpeaker(platformContext) }
    var readAloudJob by remember { mutableStateOf<Job?>(null) }
    var readAloudStatus by remember { mutableStateOf<ReadAloudStatus>(ReadAloudStatus.Idle) }
    var isReadAloudControlsVisible by remember { mutableStateOf(false) }
    var currentReadAloudIndex by rememberSaveable(threadId) { mutableStateOf(0) }
    val cancelActiveReadAloud: () -> Unit = {
        readAloudJob?.cancel()
        textSpeaker.stop()
    }
    val stopReadAloud: () -> Unit = {
        cancelActiveReadAloud()
        readAloudJob = null
        readAloudStatus = ReadAloudStatus.Idle
        currentReadAloudIndex = 0
    }
    val pauseReadAloud: () -> Unit = pauseReadAloud@{
        val segment = (readAloudStatus as? ReadAloudStatus.Speaking)?.segment ?: return@pauseReadAloud
        readAloudStatus = ReadAloudStatus.Paused(segment)
        cancelActiveReadAloud()
        readAloudJob = null
        coroutineScope.launch {
            snackbarHostState.showSnackbar("読み上げを一時停止しました")
        }
    }
    DisposableEffect(textSpeaker) {
        onDispose {
            stopReadAloud()
            textSpeaker.close()
        }
    }
    val autoSaveRepository = autoSavedThreadRepository
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }
    val lastAutoSaveTimestamp = rememberSaveable(threadId) { mutableStateOf(0L) }
    var isShowingOfflineCopy by rememberSaveable(threadId) { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            autoSaveJob?.cancel()
        }
    }
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
    var quoteSelectionTarget by remember { mutableStateOf<Post?>(null) }
    var isActionSheetVisible by remember { mutableStateOf(false) }
    var pendingDeletePassword by remember { mutableStateOf("") }
    var pendingDeleteImageOnly by remember { mutableStateOf(false) }
    var lastUsedDeleteKey by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var isReplyDialogVisible by remember { mutableStateOf(false) }
    var isThreadSettingsSheetVisible by remember { mutableStateOf(false) }
    var isThreadFilterSheetVisible by remember { mutableStateOf(false) }
    var selectedThreadFilterOptions by remember { mutableStateOf(emptySet<ThreadFilterOption>()) }
    var selectedThreadSortOption by rememberSaveable { mutableStateOf<ThreadFilterSortOption?>(null) }
    var threadFilterKeyword by rememberSaveable { mutableStateOf("") }
    val persistedSelfPostIdentifiersState = stateStore?.selfPostIdentifiers?.collectAsState(initial = emptyList())
    val persistedSelfPostIdentifiers = persistedSelfPostIdentifiersState?.value ?: emptyList()
    var isGlobalSettingsVisible by remember { mutableStateOf(false) }
    var isNgManagementVisible by remember { mutableStateOf(false) }
    var ngHeaderPrefill by remember(board.id, threadId) { mutableStateOf<String?>(null) }
    var ngFilteringEnabled by rememberSaveable(board.id, threadId) { mutableStateOf(true) }
    val fallbackNgHeadersState = rememberSaveable(board.id, threadId) { mutableStateOf<List<String>>(emptyList()) }
    val fallbackNgWordsState = rememberSaveable(board.id, threadId) { mutableStateOf<List<String>>(emptyList()) }
    val ngHeadersState = stateStore?.ngHeaders?.collectAsState(initial = fallbackNgHeadersState.value)
    val ngWordsState = stateStore?.ngWords?.collectAsState(initial = fallbackNgWordsState.value)
    val ngHeaders = ngHeadersState?.value ?: fallbackNgHeadersState.value
    val ngWords = ngWordsState?.value ?: fallbackNgWordsState.value
    val showMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    val persistNgHeaders: (List<String>) -> Unit = { updated ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setNgHeaders(updated)
            }
        } else {
            fallbackNgHeadersState.value = updated
        }
    }
    val persistNgWords: (List<String>) -> Unit = { updated ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setNgWords(updated)
            }
        } else {
            fallbackNgWordsState.value = updated
        }
    }
    val urlLauncher = rememberUrlLauncher()

    val handleThreadFilterToggle: (ThreadFilterOption) -> Unit = { option ->
        val currentlySelected = option in selectedThreadFilterOptions
        var updatedOptions = if (currentlySelected) {
            selectedThreadFilterOptions - option
        } else {
            selectedThreadFilterOptions + option
        }
        if (option.sortOption != null && !currentlySelected) {
            updatedOptions = updatedOptions.filter { it.sortOption == null || it == option }.toSet()
        }
        selectedThreadFilterOptions = updatedOptions

        option.sortOption?.let { sortOption ->
            selectedThreadSortOption = if (currentlySelected) null else sortOption
        }
    }

    val addNgHeaderEntry: (String) -> Unit = { value ->
        val trimmed = value.trim()
        when {
            trimmed.isEmpty() -> showMessage("NGヘッダーに含める文字列を入力してください")
            ngHeaders.any { it.equals(trimmed, ignoreCase = true) } -> showMessage("そのNGヘッダーはすでに登録されています")
            else -> {
                persistNgHeaders(ngHeaders + trimmed)
                showMessage("NGヘッダーを追加しました")
            }
        }
    }
    val addNgWordEntry: (String) -> Unit = { value ->
        val trimmed = value.trim()
        when {
            trimmed.isEmpty() -> showMessage("NGワードに含める文字列を入力してください")
            ngWords.any { it.equals(trimmed, ignoreCase = true) } -> showMessage("そのNGワードはすでに登録されています")
            else -> {
                persistNgWords(ngWords + trimmed)
                showMessage("NGワードを追加しました")
            }
        }
    }
    val removeNgHeaderEntry: (String) -> Unit = { entry ->
        persistNgHeaders(ngHeaders.filterNot { it == entry })
        showMessage("NGヘッダーを削除しました")
    }
    val removeNgWordEntry: (String) -> Unit = { entry ->
        persistNgWords(ngWords.filterNot { it == entry })
        showMessage("NGワードを削除しました")
    }
    val toggleNgFiltering: () -> Unit = {
        ngFilteringEnabled = !ngFilteringEnabled
        showMessage(if (ngFilteringEnabled) "NG表示を有効にしました" else "NG表示を無効にしました")
    }
    var replyName by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replyEmail by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replySubject by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replyComment by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replyPassword by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replyImageData by remember { mutableStateOf<com.valoser.futacha.shared.util.ImageData?>(null) }
    var isGalleryVisible by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isHistoryRefreshing by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableStateOf<com.valoser.futacha.shared.model.SaveProgress?>(null) }
    val isPrivacyFilterEnabled by stateStore?.isPrivacyFilterEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
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

    suspend fun loadOfflineThread(): ThreadPage? {
        val repository = autoSaveRepository ?: return null
        val localFileSystem = fileSystem ?: return null
        val metadata = repository.loadThreadMetadata(threadId).getOrNull() ?: return null
        return metadata.toThreadPage(localFileSystem, AUTO_SAVE_DIRECTORY)
    }

    suspend fun loadThreadWithOfflineFallback(allowOfflineFallback: Boolean): Pair<ThreadPage, Boolean> {
        try {
            isShowingOfflineCopy = false
            val page = activeRepository.getThread(board.url, threadId)
            return page to false
        } catch (e: Exception) {
            if (!allowOfflineFallback) throw e
            val offlinePage = loadOfflineThread()
            if (offlinePage != null) {
                isShowingOfflineCopy = true
                return offlinePage to true
            }
            throw e
        }
    }

    fun startAutoSave(page: ThreadPage) {
        val repository = autoSaveRepository ?: return
        val client = httpClient ?: return
        val localFileSystem = fileSystem ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastAutoSaveTimestamp.value < AUTO_SAVE_INTERVAL_MS) return
        lastAutoSaveTimestamp.value = now
        autoSaveJob?.cancel()
        autoSaveJob = coroutineScope.launch {
            val result = runCatching {
                val saveService = ThreadSaveService(client, localFileSystem)
                saveService.saveThread(
                    threadId = threadId,
                    boardId = board.id,
                    boardName = board.name,
                    boardUrl = board.url,
                    title = page.posts.firstOrNull()?.subject ?: threadTitle ?: "無題",
                    expiresAtLabel = page.expiresAtLabel,
                    posts = page.posts,
                    baseDirectory = AUTO_SAVE_DIRECTORY
                )
            }
            result.onSuccess { saveResult ->
                saveResult.onSuccess { savedThread ->
                    repository.addThreadToIndex(savedThread)
                        .onFailure {
                            Logger.e(THREAD_AUTO_SAVE_TAG, "Failed to index auto-saved thread $threadId", it)
                        }
                }.onFailure {
                    Logger.e(THREAD_AUTO_SAVE_TAG, "Auto-save failed for thread $threadId", it)
                }
            }.onFailure {
                Logger.e(THREAD_AUTO_SAVE_TAG, "Auto-save job failed unexpectedly for thread $threadId", it)
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
                    val (page, usedOffline) = loadThreadWithOfflineFallback(allowOfflineFallback = true)
                    if (isActive) {
                        uiState.value = ThreadUiState.Success(page)
                        onHistoryEntryUpdated(
                            buildHistoryEntryFromPage(
                                page = page,
                                history = history,
                                threadId = threadId,
                                threadTitle = threadTitle,
                                board = board
                            )
                        )
                        if (usedOffline) {
                            snackbarHostState.showSnackbar("ローカルコピーを表示しています")
                        }
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
    val currentSuccessState = currentState as? ThreadUiState.Success
    val mediaPreviewEntries = remember(currentSuccessState?.page?.posts) {
        buildMediaPreviewEntries(currentSuccessState?.page?.posts ?: emptyList())
    }
    var previewMediaIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(mediaPreviewEntries.size) {
        if (previewMediaIndex != null && previewMediaIndex !in mediaPreviewEntries.indices) {
            previewMediaIndex = null
        }
    }
    val handleMediaClick: (String, MediaType) -> Unit = { url, mediaType ->
        val targetIndex = mediaPreviewEntries.indexOfFirst { it.url == url && it.mediaType == mediaType }
        if (targetIndex >= 0) {
            previewMediaIndex = targetIndex
        }
    }
    LaunchedEffect(currentState, isShowingOfflineCopy, httpClient, fileSystem) {
        if (currentState is ThreadUiState.Success && !isShowingOfflineCopy) {
            startAutoSave(currentState.page)
        }
    }

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

    val readAloudSegments = remember(currentState) {
        if (currentState is ThreadUiState.Success) {
            buildReadAloudSegments(currentState.page.posts)
        } else {
            emptyList()
        }
    }
    LaunchedEffect(readAloudSegments.size) {
        if (currentReadAloudIndex > readAloudSegments.size) {
            currentReadAloudIndex = readAloudSegments.size
        }
    }

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

    val startReadAloud: () -> Unit = startReadAloud@{
        if (readAloudSegments.isEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("読み上げ対象がありません")
            }
            return@startReadAloud
        }
        if (readAloudJob != null) return@startReadAloud
        if (currentReadAloudIndex >= readAloudSegments.size) {
            currentReadAloudIndex = 0
        }
        readAloudJob = coroutineScope.launch {
            var completedNormally = false
            var index = currentReadAloudIndex
            try {
                while (index < readAloudSegments.size && isActive) {
                    val segment = readAloudSegments[index]
                    readAloudStatus = ReadAloudStatus.Speaking(segment)
                    lazyListState.animateScrollToItem(segment.postIndex)
                    currentReadAloudIndex = index
                    textSpeaker.speak(segment.body)
                    index++
                }
                if (index >= readAloudSegments.size && isActive) {
                    completedNormally = true
                    currentReadAloudIndex = 0
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // ユーザー操作で中断された場合は何もしない
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("読み上げ中にエラーが発生しました: ${e.message ?: "不明なエラー"}")
            } finally {
                if (completedNormally) {
                    readAloudStatus = ReadAloudStatus.Idle
                    snackbarHostState.showSnackbar("読み上げを完了しました")
                } else if (readAloudStatus !is ReadAloudStatus.Paused) {
                    readAloudStatus = ReadAloudStatus.Idle
                }
                readAloudJob = null
            }
        }
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

    fun <T> launchThreadAction(
        successMessage: String,
        failurePrefix: String,
        onSuccess: (T) -> Unit = {},
        block: suspend () -> T
    ) {
        coroutineScope.launch {
            if (actionInProgress) {
                snackbarHostState.showSnackbar("処理中です…")
                return@launch
            }
            actionInProgress = true
            Logger.d(THREAD_ACTION_LOG_TAG, "Starting thread action: success='$successMessage', failure='$failurePrefix'")
            try {
                val result = block()
                Logger.i(THREAD_ACTION_LOG_TAG, "Thread action succeeded: $successMessage")
                onSuccess(result)
                snackbarHostState.showSnackbar(successMessage)
            } catch (e: Exception) {
                Logger.e(THREAD_ACTION_LOG_TAG, "Thread action failed: $failurePrefix", e)
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

    val openQuoteSelection: (Post) -> Unit = { post ->
        isActionSheetVisible = false
        actionTargetPost = null
        quoteSelectionTarget = post
    }

    val handleNgRegistration: (Post) -> Unit = { post ->
        isActionSheetVisible = false
        actionTargetPost = null
        val prefillValue = buildNgHeaderPrefillValue(post)
        ngHeaderPrefill = prefillValue
        if (prefillValue == null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("IDが見つかりませんでした") }
        }
        isNgManagementVisible = true
    }

    val performRefresh: () -> Unit = {
        if (!isRefreshing) {
            coroutineScope.launch {
                isRefreshing = true
                val savedIndex = lazyListState.firstVisibleItemIndex
                val savedOffset = lazyListState.firstVisibleItemScrollOffset
                try {
                    val (page, usedOffline) = loadThreadWithOfflineFallback(allowOfflineFallback = true)
                    uiState.value = ThreadUiState.Success(page)
                    lazyListState.scrollToItem(savedIndex, savedOffset)

                    onHistoryEntryUpdated(buildHistoryEntryFromPage(
                        page = page,
                        history = history,
                        threadId = threadId,
                        threadTitle = threadTitle,
                        board = board
                    ))

                    val successMessage = if (usedOffline) {
                        "ネットワーク接続不可: ローカルコピーを表示しています"
                    } else {
                        "スレッドを更新しました"
                    }
                    snackbarHostState.showSnackbar(successMessage)
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
                    isGlobalSettingsVisible = true
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
                    onSearch = { isSearchActive = true },
                    onMenuSettings = { isGlobalSettingsVisible = true }
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
                            ThreadActionBarItem.Filter -> {
                                isThreadFilterSheetVisible = true
                            }
                            ThreadActionBarItem.Settings -> {
                                isThreadSettingsSheetVisible = true
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
                        val threadFilterCriteria = remember(
                            selectedThreadFilterOptions,
                            threadFilterKeyword,
                            selectedThreadSortOption,
                            persistedSelfPostIdentifiers
                        ) {
                            ThreadFilterCriteria(
                                options = selectedThreadFilterOptions,
                                keyword = threadFilterKeyword,
                                selfPostIdentifiers = persistedSelfPostIdentifiers,
                                sortOption = selectedThreadSortOption
                            )
                        }
                        val filteredPage = remember(
                            state.page,
                            ngHeaders,
                            ngWords,
                            ngFilteringEnabled,
                            threadFilterCriteria
                        ) {
                            val ngFiltered = applyNgFilters(state.page, ngHeaders, ngWords, ngFilteringEnabled)
                            applyThreadFilters(ngFiltered, threadFilterCriteria)
                        }
                        ThreadContent(
                            page = filteredPage,
                            listState = lazyListState,
                            saidaneOverrides = saidaneOverrides,
                            searchHighlightRanges = postHighlightRanges,
                            onPostLongPress = { post ->
                                actionTargetPost = post
                                isActionSheetVisible = true
                            },
                            onSaidaneClick = handleSaidaneAction,
                            onMediaClick = handleMediaClick,
                            onUrlClick = urlLauncher,
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
                if (readAloudStatus is ReadAloudStatus.Speaking) {
                    val segment = (readAloudStatus as ReadAloudStatus.Speaking).segment
                    ReadAloudIndicator(
                        segment = segment,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
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
            onQuote = { openQuoteSelection(sheetTarget) },
            onNgRegister = { handleNgRegistration(sheetTarget) },
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

    val quoteTarget = quoteSelectionTarget
    if (quoteTarget != null) {
        QuoteSelectionDialog(
            post = quoteTarget,
            onDismiss = { quoteSelectionTarget = null },
            onConfirm = { selectedLines ->
                val quoteBody = selectedLines.joinToString("\n").trimEnd()
                if (quoteBody.isNotBlank()) {
                    val existing = replyComment.trimEnd()
                    val builder = StringBuilder()
                    if (existing.isNotBlank()) {
                        builder.append(existing)
                        builder.append("\n")
                    }
                    builder.append(quoteBody)
                    builder.append("\n")
                    replyComment = builder.toString()
                    isReplyDialogVisible = true
                }
                quoteSelectionTarget = null
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
                    onSuccess = { thisNo ->
                        if (!thisNo.isNullOrBlank()) {
                            stateStore?.let { store ->
                                coroutineScope.launch {
                                    store.addSelfPostIdentifier(threadId, thisNo)
                                }
                            }
                        }
                        refreshThread()
                    }
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

    val totalMediaCount = mediaPreviewEntries.size
    val previewMediaEntry = previewMediaIndex?.let { mediaPreviewEntries.getOrNull(it) }
    val navigateToNextMedia: () -> Unit = next@{
        if (totalMediaCount == 0) return@next
        val currentIndex = previewMediaIndex ?: 0
        previewMediaIndex = (currentIndex + 1) % totalMediaCount
    }
    val navigateToPreviousMedia: () -> Unit = previous@{
        if (totalMediaCount == 0) return@previous
        val currentIndex = previewMediaIndex ?: 0
        previewMediaIndex = (currentIndex + totalMediaCount - 1) % totalMediaCount
    }
    previewMediaEntry?.let { entry ->
        when (entry.mediaType) {
            MediaType.Image -> ImagePreviewDialog(
                entry = entry,
                currentIndex = previewMediaIndex ?: 0,
                totalCount = totalMediaCount,
                onDismiss = { previewMediaIndex = null },
                onNavigateNext = navigateToNextMedia,
                onNavigatePrevious = navigateToPreviousMedia
            )
            MediaType.Video -> VideoPreviewDialog(
                entry = entry,
                currentIndex = previewMediaIndex ?: 0,
                totalCount = totalMediaCount,
                onDismiss = { previewMediaIndex = null },
                onNavigateNext = navigateToNextMedia,
                onNavigatePrevious = navigateToPreviousMedia
            )
        }
    }

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
    if (isThreadSettingsSheetVisible) {
        ThreadSettingsSheet(
            onDismiss = { isThreadSettingsSheetVisible = false },
            onAction = { menuItem ->
                isThreadSettingsSheetVisible = false
                when (menuItem) {
                    ThreadSettingsMenuItem.NgManagement -> {
                        ngHeaderPrefill = null
                        isNgManagementVisible = true
                    }
                    ThreadSettingsMenuItem.ExternalApp -> {
                        // 外部アプリで開く
                        // board.urlからfutaba.phpを削除してからres/xxx.htmを追加
                        val baseUrl = board.url.trimEnd('/').removeSuffix("/futaba.php")
                        val threadUrl = "$baseUrl/res/${threadId}.htm"
                        urlLauncher(threadUrl)
                    }
                    ThreadSettingsMenuItem.Privacy -> {
                        coroutineScope.launch {
                            stateStore?.setPrivacyFilterEnabled(!isPrivacyFilterEnabled)
                        }
                    }
                    ThreadSettingsMenuItem.ReadAloud -> {
                        isReadAloudControlsVisible = true
                    }
                }
            }
        )
    }

    if (isThreadFilterSheetVisible) {
            ThreadFilterSheet(
                selectedOptions = selectedThreadFilterOptions,
                activeSortOption = selectedThreadSortOption,
                keyword = threadFilterKeyword,
                onOptionToggle = handleThreadFilterToggle,
                onKeywordChange = { threadFilterKeyword = it },
                onClear = {
                    selectedThreadFilterOptions = emptySet()
                    selectedThreadSortOption = null
                    threadFilterKeyword = ""
                },
                onDismiss = {
                    isThreadFilterSheetVisible = false
                }
            )
        }

    if (isReadAloudControlsVisible) {
        ReadAloudControlSheet(
            segments = readAloudSegments,
            currentIndex = currentReadAloudIndex,
            status = readAloudStatus,
            onPlay = {
                startReadAloud()
            },
            onPause = pauseReadAloud,
            onStop = {
                stopReadAloud()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("読み上げを停止しました")
                }
            },
            onDismiss = {
                isReadAloudControlsVisible = false
            }
        )
    }

    if (isNgManagementVisible) {
        NgManagementSheet(
            onDismiss = {
                isNgManagementVisible = false
                ngHeaderPrefill = null
            },
            ngHeaders = ngHeaders,
            ngWords = ngWords,
            ngFilteringEnabled = ngFilteringEnabled,
            onAddHeader = addNgHeaderEntry,
            onAddWord = addNgWordEntry,
            onRemoveHeader = removeNgHeaderEntry,
            onRemoveWord = removeNgWordEntry,
            onToggleFiltering = toggleNgFiltering,
            initialInput = ngHeaderPrefill
        )
    }

    // Privacy filter overlay - allows interactions to pass through
    if (isPrivacyFilterEnabled) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawRect(color = Color.White.copy(alpha = 0.5f))
        }
    }

    // 保存進捗ダイアログ
    SaveProgressDialog(
        progress = saveProgress,
        onDismissRequest = {
            saveProgress = null
        }
    )

    if (isGlobalSettingsVisible) {
        GlobalSettingsScreen(
            onBack = { isGlobalSettingsVisible = false },
            appVersion = appVersion,
            isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
            onBackgroundRefreshChanged = onBackgroundRefreshChanged
        )
    }
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
    onSearch: () -> Unit,
    onMenuSettings: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }
    var isMenuExpanded by remember { mutableStateOf(false) }
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
                        imageVector = Icons.Rounded.History,
                        contentDescription = "履歴を開く"
                    )
                }
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "その他"
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(ThreadMenuAction.Settings.label) },
                        onClick = {
                            isMenuExpanded = false
                            onMenuSettings()
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
    onUrlClick: (String) -> Unit,
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
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }
    val showQuotePreview: (String, List<Post>) -> Unit = preview@ { quoteText, targets ->
        if (isScrolling || targets.isEmpty()) return@preview
        quotePreviewState = QuotePreviewState(
            quoteText = quoteText,
            targetPosts = targets,
            posterIdLabels = posterIdLabels
        )
    }

    // アニメーション用のオフセット
    val overscrollOffset = remember { Animatable(0f) }

    val isAtTop by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisibleItem != null &&
            firstVisibleItem.index == 0 &&
            firstVisibleItem.offset.toFloat() <= edgeOffsetTolerancePx
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (lastVisibleItem == null || totalItems == 0) return@derivedStateOf false
            if (lastVisibleItem.index < totalItems - 1) return@derivedStateOf false
            val viewportEnd = layoutInfo.viewportEndOffset
            val lastItemEnd = lastVisibleItem.offset + lastVisibleItem.size
            val remainingSpace = viewportEnd - lastItemEnd
            remainingSpace.toFloat() <= edgeOffsetTolerancePx ||
                layoutInfo.visibleItemsInfo.size >= totalItems
        }
    }

    // リフレッシュ完了時にアニメーションで空間を戻す
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, overscrollOffset.value.toInt()) }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDrag = 0f

                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val dragEvent = event.changes.firstOrNull()

                                if (dragEvent != null && dragEvent.pressed) {
                                    val delta = dragEvent.positionChangeIgnoreConsumed().y

                                    // 上端で下向きにドラッグ
                                    if (isAtTop && delta > 0 && !isRefreshing) {
                                        totalDrag += delta
                                        val newOffset = (totalDrag * 0.4f).coerceIn(0f, maxOverscrollPx)
                                        coroutineScope.launch {
                                            overscrollOffset.snapTo(newOffset)
                                        }
                                        dragEvent.consume()
                                    }
                                    // 下端で上向きにドラッグ
                                    else if (isAtBottom && delta < 0 && !isRefreshing) {
                                        totalDrag += delta
                                        val newOffset = (totalDrag * 0.4f).coerceIn(-maxOverscrollPx, 0f)
                                        coroutineScope.launch {
                                            overscrollOffset.snapTo(newOffset)
                                        }
                                        dragEvent.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // ドラッグ終了
                            if (totalDrag > refreshTriggerPx) {
                                coroutineScope.launch { onRefresh() }
                            } else if (totalDrag < -refreshTriggerPx) {
                                coroutineScope.launch { onRefresh() }
                            }

                            totalDrag = 0f
                            coroutineScope.launch {
                                overscrollOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    },
                state = listState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 24.dp
                )
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
                            showQuotePreview(reference.text, targets)
                        },
                        onUrlClick = onUrlClick,
                        onPosterIdClick = normalizedPosterId
                            ?.let { normalizedId ->
                                postsByPosterId[normalizedId]
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { sameIdPosts ->
                                        {
                                            showQuotePreview("ID:$normalizedId のレス", sameIdPosts)
                                        }
                                    }
                            },
                        onReferencedByClick = referencedByMap[post.id]
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { referencingPosts ->
                                {
                                    showQuotePreview(">>${post.id} を引用したレス", referencingPosts)
                                }
                        },
                        onSaidaneClick = { onSaidaneClick(post) },
                        onMediaClick = onMediaClick,
                        onLongPress = {
                            if (quotePreviewState == null) {
                                onPostLongPress(post)
                            }
                        },
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

            ThreadScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            )
        }
    }
    quotePreviewState?.let { state ->
        QuotePreviewDialog(
            state = state,
            onDismiss = { quotePreviewState = null },
            onMediaClick = onMediaClick,
            onUrlClick = onUrlClick,
            onQuoteClick = { reference ->
                val targets = reference.targetPostIds.mapNotNull { postIndex[it] }
                showQuotePreview(reference.text, targets)
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
    onUrlClick: (String) -> Unit,
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
        modifier.pointerInput(onLongPress) {
            detectTapGestures(onLongPress = { onLongPress() })
        }
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
            onUrlClick = onUrlClick,
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
    onUrlClick: (String) -> Unit,
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
        modifier = modifier.pointerInput(annotated, quoteReferences, onUrlClick) {
            awaitEachGesture {
                val layout = textLayoutResult ?: return@awaitEachGesture
                var downChange: PointerInputChange? = null
                // Listen in Initial pass to beat selection/long-press handlers
                while (downChange == null) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    downChange = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                    if (event.changes.none { it.pressed }) return@awaitEachGesture
                }
                val downOffset = layout.getOffsetForPosition(downChange.position)
                val urlOnDown = annotated
                    .getStringAnnotations(URL_ANNOTATION_TAG, downOffset, downOffset)
                    .firstOrNull()
                    ?.item
                val quoteIndexOnDown = annotated
                    .getStringAnnotations(QUOTE_ANNOTATION_TAG, downOffset, downOffset)
                    .firstOrNull()
                    ?.item
                    ?.toIntOrNull()
                if (urlOnDown != null) {
                    downChange.consume()
                    var upChange: PointerInputChange? = null
                    while (upChange == null) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        upChange = event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }
                        if (event.changes.none { it.pressed }) break
                    }
                    val upPosition = upChange?.position ?: downChange.position
                    val upOffset = layout.getOffsetForPosition(upPosition)
                    val urlOnUp = annotated
                        .getStringAnnotations(URL_ANNOTATION_TAG, upOffset, upOffset)
                        .firstOrNull()
                        ?.item
                    if (urlOnUp != null) {
                        onUrlClick(urlOnUp)
                        upChange?.consume()
                    }
                    return@awaitEachGesture
                }
                if (quoteIndexOnDown != null) {
                    val reference = quoteReferences
                        .getOrNull(quoteIndexOnDown)
                        ?.takeIf { it.targetPostIds.isNotEmpty() }
                    if (reference != null) {
                        val touchSlop = viewConfiguration.touchSlop
                        val holdSucceeded = withTimeoutOrNull(QUOTE_PREVIEW_HOLD_MS) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == downChange.id } ?: continue
                                if (change.changedToUpIgnoreConsumed()) {
                                    return@withTimeoutOrNull false
                                }
                                val distance = (change.position - downChange.position).getDistance()
                                if (distance > touchSlop) {
                                    return@withTimeoutOrNull false
                                }
                            }
                        } != false
                        if (holdSucceeded) {
                            onQuoteClick(reference)
                            downChange.consume()
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == downChange.id }
                                if (change == null || change.changedToUpIgnoreConsumed() || !change.pressed) {
                                    break
                                }
                            }
                        }
                    }
                    return@awaitEachGesture
                }
                var upChange: PointerInputChange? = null
                while (upChange == null) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    upChange = event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }
                    if (event.changes.none { it.pressed }) break
                }
                val upPosition = upChange?.position ?: downChange.position
                val offset = layout.getOffsetForPosition(upPosition)
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
    onQuote: () -> Unit,
    onNgRegister: () -> Unit,
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
                    Icon(Icons.Outlined.FormatQuote, contentDescription = null)
                },
                headlineContent = { Text("引用") },
                supportingContent = { Text("レス内容を返信欄にコピー") },
                modifier = Modifier.clickable { onQuote() }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Rounded.Block, contentDescription = null)
                },
                headlineContent = { Text("NG登録") },
                supportingContent = { Text("IDやワードをNG管理に追加") },
                modifier = Modifier.clickable { onNgRegister() }
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

@Composable
private fun QuoteSelectionDialog(
    post: Post,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selectionItems = remember(post.id) { buildQuoteSelectionItems(post) }
    var selectedIds by remember(post.id) {
        mutableStateOf(
            selectionItems
                .filter { it.isDefault }
                .map { it.id }
                .toSet()
                .ifEmpty { selectionItems.firstOrNull()?.let { setOf(it.id) } ?: emptySet() }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val lines = selectionItems
                        .filter { selectedIds.contains(it.id) }
                        .map { it.content }
                    onConfirm(lines)
                },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("コピー")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
        title = { Text("引用する内容を選択") },
        text = {
            if (selectionItems.isEmpty()) {
                Text(
                    text = "引用できる内容がありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectionItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    selectedIds = if (selectedIds.contains(item.id)) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(item.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + item.id
                                    } else {
                                        selectedIds - item.id
                                    }
                                }
                            )
                            Column(
                                modifier = Modifier.padding(start = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
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
private const val URL_ANNOTATION_TAG = "url"
private const val QUOTE_PREVIEW_HOLD_MS = 200L
private val URL_REGEX = Regex("""https?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val SCHEMELESS_URL_REGEX = Regex("""ttps?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val URL_LINK_TEXT_REGEX = Regex("""URL(?:ﾘﾝｸ|リンク)\(([^)]+)\)""", RegexOption.IGNORE_CASE)
private const val THREAD_ACTION_LOG_TAG = "ThreadActions"

private val urlSpanStyle = SpanStyle(
    color = Color(0xFF1E88E5),
    textDecoration = TextDecoration.Underline
)

private data class UrlMatch(
    val url: String,
    val range: IntRange
)

private fun buildAnnotatedMessage(
    html: String,
    quoteReferences: List<QuoteReference>,
    highlightRanges: List<IntRange>,
    highlightStyle: SpanStyle
): AnnotatedString {
    val lines = messageHtmlToLines(html)
    var referenceIndex = 0
    val urlMatches = mutableListOf<UrlMatch>()
    val built = buildAnnotatedString {
        val normalizedHighlights = highlightRanges
            .filter { it.first >= 0 && it.last >= it.first }
            .sortedBy { it.first }
        lines.forEachIndexed { index, line ->
            val content = line.trimEnd()
            val isQuote = content.startsWith(">") || content.startsWith("＞")
            if (isQuote) {
                val spanStyle = SpanStyle(color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                val reference = quoteReferences.getOrNull(referenceIndex)
                if (reference != null && reference.targetPostIds.isNotEmpty()) {
                    pushStringAnnotation(QUOTE_ANNOTATION_TAG, referenceIndex.toString())
                    appendWithHighlights(content, spanStyle, normalizedHighlights, highlightStyle)
                    pop()
                    referenceIndex += 1
                } else {
                    appendWithHighlights(content, spanStyle, normalizedHighlights, highlightStyle)
                }
            } else {
                appendWithHighlights(content, SpanStyle(), normalizedHighlights, highlightStyle)
            }
            if (index != lines.lastIndex) {
                append("\n")
            }
        }
    }
    val builtText = built.toString()
    urlMatches += URL_REGEX.findAll(builtText).map { match ->
        UrlMatch(url = match.value, range = match.range)
    }
    urlMatches += SCHEMELESS_URL_REGEX.findAll(builtText).map { match ->
        UrlMatch(url = "h${match.value}", range = match.range)
    }
    urlMatches += URL_LINK_TEXT_REGEX.findAll(builtText).mapNotNull { match ->
        val target = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val normalized = if (target.startsWith("http")) target else "https://$target"
        UrlMatch(url = normalized, range = match.range)
    }
    val distinct = urlMatches
        .sortedBy { it.range.first }
        .distinctBy { it.range.first to it.range.last }
    val builder = AnnotatedString.Builder(built)
    distinct.forEach { match ->
        builder.addStringAnnotation(
            tag = URL_ANNOTATION_TAG,
            annotation = match.url,
            start = match.range.first,
            end = match.range.last + 1
        )
        builder.addStyle(
            style = urlSpanStyle,
            start = match.range.first,
            end = match.range.last + 1
        )
    }
    return builder.toAnnotatedString()
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
    val decoded = decodeAllHtmlEntities(withoutTags)
    return decoded.lines()
}

private fun decodeAllHtmlEntities(value: String): String {
    var result = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&nbsp;", " ")

    // Decode hexadecimal entities like &#x1F43B; (🐻)
    result = Regex("&#x([0-9a-fA-F]+);").replace(result) { match ->
        val hexValue = match.groupValues.getOrNull(1) ?: return@replace match.value
        val codePoint = runCatching { hexValue.toInt(16) }.getOrNull()
        if (codePoint != null && codePoint in 0x20..0x10FFFF) {
            codePointToString(codePoint)
        } else {
            match.value
        }
    }

    // Decode numeric entities like &#128059; (🐻)
    result = Regex("&#(\\d+);").replace(result) { match ->
        val numValue = match.groupValues.getOrNull(1) ?: return@replace match.value
        val codePoint = runCatching { numValue.toInt() }.getOrNull()
        if (codePoint != null && codePoint in 0x20..0x10FFFF) {
            codePointToString(codePoint)
        } else {
            match.value
        }
    }

    return result
}

private fun codePointToString(codePoint: Int): String {
    return if (codePoint <= 0xFFFF) {
        codePoint.toChar().toString()
    } else {
        // Handle surrogate pairs for code points > 0xFFFF
        val high = ((codePoint - 0x10000) shr 10) + 0xD800
        val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
        "${high.toChar()}${low.toChar()}"
    }
}

private fun messageHtmlToPlainText(html: String): String {
    return messageHtmlToLines(html)
        .map { it.trimEnd() }
        .joinToString("\n")
}

private fun buildQuoteSelectionItems(post: Post): List<QuoteSelectionItem> {
    val items = mutableListOf<QuoteSelectionItem>()
    items += QuoteSelectionItem(
        id = "number-${post.id}",
        title = "レスNo.",
        preview = "No.${post.id}",
        content = ">No.${post.id}",
        isDefault = true
    )
    extractFileNameFromUrl(post.imageUrl)?.let { fileName ->
        items += QuoteSelectionItem(
            id = "file-${post.id}",
            title = "ファイル名",
        preview = fileName,
        content = ">$fileName"
    )
    }
    val bodyLines = messageHtmlToLines(post.messageHtml)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    bodyLines.forEachIndexed { index, line ->
        items += QuoteSelectionItem(
            id = "line-$index",
            title = "本文 ${index + 1}行目",
            preview = line,
            content = ">$line"
        )
    }
    return items
}

private fun extractFileNameFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val sanitized = url.substringBefore('#').substringBefore('?')
    val name = sanitized.substringAfterLast('/', "")
    return name.takeIf { it.isNotBlank() }
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
    onUrlClick: (String) -> Unit,
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
                        onUrlClick = onUrlClick,
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

private fun applyNgFilters(
    page: ThreadPage,
    ngHeaders: List<String>,
    ngWords: List<String>,
    enabled: Boolean
): ThreadPage {
    if (!enabled) return page
    val headerFilters = ngHeaders.mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    val wordFilters = ngWords.mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    if (headerFilters.isEmpty() && wordFilters.isEmpty()) return page
    val filteredPosts = page.posts.filterNot { post ->
        matchesNgFilters(post, headerFilters, wordFilters)
    }
    return page.copy(posts = filteredPosts)
}

private fun applyThreadFilters(
    page: ThreadPage,
    criteria: ThreadFilterCriteria
): ThreadPage {
    if (criteria.options.isEmpty()) return page
    val filteredPosts = page.posts.filter { post ->
        matchesThreadFilters(post, criteria)
    }
    val sortedPosts = sortThreadPosts(filteredPosts, criteria.sortOption)
    return page.copy(posts = sortedPosts)
}

private fun matchesThreadFilters(post: Post, criteria: ThreadFilterCriteria): Boolean {
    val filterOptions = criteria.options.filter { it.sortOption == null }
    if (filterOptions.isEmpty()) return true
    val lowerText = messageHtmlToPlainText(post.messageHtml).lowercase()
    val headerText = buildPostHeaderText(post)
    return filterOptions.any { option ->
        when (option) {
            ThreadFilterOption.SelfPosts ->
                matchesSelfFilter(post, criteria.selfPostIdentifiers)
            ThreadFilterOption.Deleted -> post.isDeleted
            ThreadFilterOption.Url -> THREAD_FILTER_URL_REGEX.containsMatchIn(lowerText)
            ThreadFilterOption.Image -> post.imageUrl?.isNotBlank() == true
            ThreadFilterOption.Keyword -> matchesKeyword(lowerText, post.subject ?: "", criteria.keyword)
            else -> true
        }
    }
}

private fun matchesSelfFilter(
    post: Post,
    storedIdentifiers: List<String>
): Boolean {
    val normalizedStored = storedIdentifiers
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (normalizedStored.isEmpty()) return false
    return normalizedStored.any { storedId ->
        post.id == storedId
    }
}

private fun parseSaidaneCount(label: String?): Int? {
    val source = label ?: return null
    return Regex("""\d+""").find(source)?.value?.toIntOrNull()
}

private fun sortThreadPosts(
    posts: List<Post>,
    sortOption: ThreadFilterSortOption?
): List<Post> {
    return when (sortOption) {
        ThreadFilterSortOption.Saidane -> posts.sortedByDescending { parseSaidaneCount(it.saidaneLabel) ?: 0 }
        ThreadFilterSortOption.Replies -> posts.sortedByDescending { it.referencedCount }
        null -> posts
    }
}

private fun matchesKeyword(lowerText: String, subject: String, keywordInput: String): Boolean {
    val keywords = keywordInput
        .split(',')
        .mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    if (keywords.isEmpty()) return false
    val lowerSubject = subject.lowercase()
    return keywords.any { keyword ->
        lowerText.contains(keyword) || lowerSubject.contains(keyword)
    }
}

private val THREAD_FILTER_URL_REGEX =
    Regex("""https?://[^\s"'<>]+|www\.[^\s"'<>]+""", RegexOption.IGNORE_CASE)

private fun matchesNgFilters(post: Post, headerFilters: List<String>, wordFilters: List<String>): Boolean {
    if (headerFilters.isNotEmpty()) {
        val headerText = buildPostHeaderText(post)
        if (headerFilters.any { headerText.contains(it) }) {
            return true
        }
    }
    if (wordFilters.isNotEmpty()) {
        val bodyText = messageHtmlToPlainText(post.messageHtml).lowercase()
        if (wordFilters.any { bodyText.contains(it) }) {
            return true
        }
    }
    return false
}

private fun buildPostHeaderText(post: Post): String {
    return listOfNotNull(
        post.subject,
        post.author,
        post.posterId,
        "No.${post.id}",
        post.timestamp
    ).joinToString(" ") { it.lowercase() }
}

private data class QuotePreviewState(
    val quoteText: String,
    val targetPosts: List<Post>,
    val posterIdLabels: Map<String, PosterIdLabel>
)

private data class QuoteSelectionItem(
    val id: String,
    val title: String,
    val preview: String,
    val content: String,
    val isDefault: Boolean = false
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
    entry: MediaPreviewEntry,
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = LocalFutachaImageLoader.current
    val urlLauncher = rememberUrlLauncher()
    var scale by remember { mutableStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    var swipeDistance by remember { mutableStateOf(0f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(entry.url) {
        scale = 1f
        translation = Offset.Zero
        swipeDistance = 0f
    }
    val previewRequest = remember(entry.url) {
        ImageRequest.Builder(platformContext)
            .data(entry.url)
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = previewRequest,
        imageLoader = imageLoader
    )
    val painterState = painter.state
    @Suppress("USELESS_IS_CHECK", "KotlinConstantConditions")
    val isLoadingState = painterState is AsyncImagePainter.State.Loading
    @Suppress("USELESS_IS_CHECK", "KotlinConstantConditions")
    val isErrorState = painterState is AsyncImagePainter.State.Error

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
                .onSizeChanged { previewSize = it }
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
                .pointerInput(entry.url, previewSize.width) {
                    detectTapGestures { offset ->
                        val width = previewSize.width.toFloat()
                        if (width <= 0f) return@detectTapGestures
                        if (offset.x < width / 2f) {
                            onNavigatePrevious()
                        } else {
                            onNavigateNext()
                        }
                    }
                }
        ) {
            Image(
                painter = painter,
                contentDescription = "プレビュー画像",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = translation.x
                        translationY = translation.y
                        alpha = if (isErrorState) 0f else 1f
                    }
            )
            if (isLoadingState) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isErrorState) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "画像を読み込めませんでした",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(onClick = { urlLauncher(entry.url) }) {
                        Text("ブラウザで開く")
                    }
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentIndex + 1}/${totalCount}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
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
    entry: MediaPreviewEntry,
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit
) {
    var swipeDistance by remember { mutableStateOf(0f) }
    var playbackState by remember { mutableStateOf(VideoPlayerState.Buffering) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.9f) }
    val urlLauncher = rememberUrlLauncher()

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
                .onSizeChanged { previewSize = it }
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
                .pointerInput(entry.url, previewSize.width) {
                    detectTapGestures { offset ->
                        val width = previewSize.width.toFloat()
                        if (width <= 0f) return@detectTapGestures
                        if (offset.x < width / 2f) {
                            onNavigatePrevious()
                        } else {
                            onNavigateNext()
                        }
                    }
                }
        ) {
            PlatformVideoPlayer(
                videoUrl = entry.url,
                modifier = Modifier.fillMaxSize(),
                onStateChanged = { playbackState = it },
                volume = volume,
                isMuted = isMuted
            )
            val isBuffering = playbackState == VideoPlayerState.Buffering || playbackState == VideoPlayerState.Idle
            if (isBuffering) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (playbackState == VideoPlayerState.Error) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "動画を再生できませんでした",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(onClick = { urlLauncher(entry.url) }) {
                        Text("ブラウザで開く")
                    }
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentIndex + 1}/${totalCount}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
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
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isMuted || volume <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = if (isMuted) "ミュート解除" else "ミュート",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = if (isMuted) "ミュート中" else "音量 ${(volume * 100).roundToInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        TextButton(onClick = { volume = 1f; isMuted = false }) {
                            Text(
                                text = "リセット",
                                color = Color.White
                            )
                        }
                    }
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            if (isMuted && it > 0f) {
                                isMuted = false
                            }
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                }
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

private data class MediaPreviewEntry(
    val url: String,
    val mediaType: MediaType,
    val postId: String,
    val title: String
)

private fun buildMediaPreviewEntries(posts: List<Post>): List<MediaPreviewEntry> {
    return posts.mapNotNull { post ->
        val targetUrl = post.imageUrl?.takeIf { it.isNotBlank() }
            ?: post.thumbnailUrl?.takeIf { it.isNotBlank() }
        if (targetUrl.isNullOrBlank()) return@mapNotNull null
        MediaPreviewEntry(
            url = targetUrl,
            mediaType = determineMediaType(targetUrl),
            postId = post.id,
            title = extractPreviewTitle(post)
        )
    }
}

private fun extractPreviewTitle(post: Post): String {
    val firstLine = messageHtmlToLines(post.messageHtml).firstOrNull()?.trim()
    if (!firstLine.isNullOrBlank()) return firstLine
    val subject = post.subject?.trim()
    if (!subject.isNullOrBlank()) return subject
    return "No.${post.id}"
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

private fun buildNgHeaderPrefillValue(post: Post): String? {
    val normalized = normalizePosterIdValue(post.posterId) ?: return null
    val withoutSlip = normalized.substringBefore('/')
    return withoutSlip.takeIf { it.isNotBlank() }
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
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .navigationBarsPadding(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadSettingsSheet(
    onDismiss: () -> Unit,
    onAction: (ThreadSettingsMenuItem) -> Unit
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
                text = "設定メニュー",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ThreadSettingsMenuItem.entries.forEach { menuItem ->
                ListItem(
                    leadingContent = { Icon(imageVector = menuItem.icon, contentDescription = null) },
                    headlineContent = { Text(menuItem.label) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onAction(menuItem) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadFilterSheet(
    selectedOptions: Set<ThreadFilterOption>,
    activeSortOption: ThreadFilterSortOption?,
    keyword: String,
    onOptionToggle: (ThreadFilterOption) -> Unit,
    onKeywordChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "レスフィルター",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "絞り込みたい条件をタップしてオン／オフしてください",
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThreadFilterOption.entries.forEachIndexed { index, option ->
                    val selected = option in selectedOptions
                    val isActiveSort = option.sortOption != null && activeSortOption == option.sortOption
                    ListItem(
                        leadingContent = {
                            Icon(imageVector = option.icon, contentDescription = null)
                        },
                        headlineContent = {
                            Text(option.label)
                        },
                        supportingContent = {
                            if (isActiveSort) {
                                Text(
                                    text = "表示: ${option.sortOption?.displayLabel}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "選択済み",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (isActiveSort) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .clickable {
                                onOptionToggle(option)
                            }
                    )
                    if (index < ThreadFilterOption.entries.lastIndex) {
                        HorizontalDivider()
                    }
                    if (option == ThreadFilterOption.Keyword && selected) {
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = onKeywordChange,
                            label = { Text("キーワード") },
                            placeholder = { Text("表示したいキーワードをカンマ区切りで") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onClear) {
                    Text("フィルターをクリア")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("閉じる")
                }
            }
        }
    }
}

@Composable
private fun SectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(label)
    }
}

private data class ReadAloudSegment(
    val postIndex: Int,
    val postId: String,
    val body: String
)

private sealed interface ReadAloudStatus {
    object Idle : ReadAloudStatus
    data class Speaking(val segment: ReadAloudSegment) : ReadAloudStatus
    data class Paused(val segment: ReadAloudSegment) : ReadAloudStatus
}

@Composable
private fun ReadAloudIndicator(
    segment: ReadAloudSegment,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .wrapContentHeight(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
    ) {
        Text(
            text = "読み上げ中: No.${segment.postId}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

private val READ_ALOUD_SKIPPED_PHRASES = listOf(
    "スレッドを立てた人によって削除されました",
    "書き込みをした人によって削除されました",
    "管理者によって削除されました"
)
private val READ_ALOUD_URL_REGEX = Regex("(?i)\\b(?:https?|ftp)://\\S+|\\bttps?://\\S+|\\bttp://\\S+")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadAloudControlSheet(
    segments: List<ReadAloudSegment>,
    currentIndex: Int,
    status: ReadAloudStatus,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totalSegments = segments.size
    val completedSegments = currentIndex.coerceIn(0, totalSegments)
    val currentSegment = when {
        status is ReadAloudStatus.Speaking -> status.segment
        status is ReadAloudStatus.Paused -> status.segment
        segments.isNotEmpty() -> segments.getOrNull(currentIndex.coerceIn(0, segments.lastIndex))
        else -> null
    }
    val isPlaying = status is ReadAloudStatus.Speaking
    val isPaused = status is ReadAloudStatus.Paused
    val playLabel = if (isPaused) "再開" else "再生"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "読み上げプレーヤー",
                style = MaterialTheme.typography.titleMedium
            )
            if (totalSegments > 0) {
                Text(
                    text = "進捗 ${completedSegments} / $totalSegments",
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = { completedSegments / totalSegments.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                currentSegment?.let {
                    Text(
                        text = "現在: No.${it.postId}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    text = "読み上げ対象スレッドがありません",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlay,
                    enabled = totalSegments > 0 && !isPlaying,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(playLabel)
                }
                OutlinedButton(
                    onClick = onPause,
                    enabled = isPlaying,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("一時停止")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = status !is ReadAloudStatus.Idle,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止")
                }
            }
        }
    }
}

private fun buildReadAloudSegments(posts: List<Post>): List<ReadAloudSegment> {
    return posts.mapIndexedNotNull { index, post ->
        if (post.isDeleted) return@mapIndexedNotNull null
        val lines = messageHtmlToLines(post.messageHtml)
            .map { stripUrlsForReadAloud(it).trim() }
            .filter { it.isNotBlank() && !it.startsWith(">") && !it.startsWith("＞") }
        if (lines.isEmpty()) return@mapIndexedNotNull null
        if (containsDeletionNotice(lines)) return@mapIndexedNotNull null
        val body = lines.joinToString("\n")
        ReadAloudSegment(index, post.id, body)
    }
}

private fun stripUrlsForReadAloud(value: String): String {
    val withoutUrls = READ_ALOUD_URL_REGEX.replace(value, "")
    return withoutUrls.replace(Regex("\\s{2,}"), " ")
}

private fun containsDeletionNotice(lines: List<String>): Boolean {
    if (lines.isEmpty()) return false
    return lines.any { line ->
        READ_ALOUD_SKIPPED_PHRASES.any { phrase ->
            line.contains(phrase)
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
    Filter("レスフィルター", Icons.Rounded.FilterList),
    Settings("設定", Icons.Rounded.Settings)
}

private enum class ThreadMenuAction(val label: String) {
    Settings("設定")
}

private enum class ThreadSettingsMenuItem(
    val label: String,
    val icon: ImageVector
) {
    NgManagement("NG管理", Icons.Rounded.Block),
    ExternalApp("外部アプリ", Icons.AutoMirrored.Rounded.OpenInNew),
    ReadAloud("読み上げ", Icons.AutoMirrored.Rounded.VolumeUp),
    Privacy("プライバシー", Icons.Rounded.Lock)
}

private enum class ThreadFilterSortOption(val displayLabel: String) {
    Saidane("そうだね数が多い順"),
    Replies("返信数が多い順")
}

private enum class ThreadFilterOption(
    val label: String,
    val icon: ImageVector,
    val sortOption: ThreadFilterSortOption? = null
) {
    SelfPosts("自分の書き込み", Icons.Rounded.Person),
    HighSaidane("そうだねが多い", Icons.Rounded.ThumbUp, ThreadFilterSortOption.Saidane),
    HighReplies("返信が多い", Icons.AutoMirrored.Rounded.ReplyAll, ThreadFilterSortOption.Replies),
    Deleted("削除されたレス", Icons.Rounded.DeleteSweep),
    Url("URLを含むレス", Icons.Rounded.Link),
    Image("画像レス", Icons.Outlined.Image),
    Keyword("キーワード", Icons.Rounded.Search);

    companion object {
        val entries = values().toList()
    }
}

private data class ThreadFilterCriteria(
    val options: Set<ThreadFilterOption>,
    val keyword: String,
    val selfPostIdentifiers: List<String>,
    val sortOption: ThreadFilterSortOption?
)

private enum class NgManagementSection {
    Header,
    Word
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NgManagementSheet(
    onDismiss: () -> Unit,
    ngHeaders: List<String>,
    ngWords: List<String>,
    ngFilteringEnabled: Boolean,
    onAddHeader: (String) -> Unit,
    onAddWord: (String) -> Unit,
    onRemoveHeader: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onToggleFiltering: () -> Unit,
    initialInput: String? = null,
    includeHeaderSection: Boolean = true,
    includeWordSection: Boolean = true
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allowedSections = remember(includeHeaderSection, includeWordSection) {
        buildList {
            if (includeHeaderSection) add(NgManagementSection.Header)
            if (includeWordSection) add(NgManagementSection.Word)
        }.ifEmpty { listOf(NgManagementSection.Word) }
    }
    val defaultSection = remember(includeHeaderSection, includeWordSection) {
        when {
            includeHeaderSection -> NgManagementSection.Header
            else -> NgManagementSection.Word
        }
    }
    var section by rememberSaveable(includeHeaderSection, includeWordSection) {
        mutableStateOf(defaultSection)
    }
    LaunchedEffect(allowedSections) {
        if (section !in allowedSections) {
            section = allowedSections.first()
        }
    }
    var input by rememberSaveable(section) { mutableStateOf("") }
    LaunchedEffect(section, initialInput) {
        input = when (section) {
            NgManagementSection.Header -> if (includeHeaderSection) {
                initialInput?.takeIf { it.isNotBlank() } ?: ""
            } else {
                ""
            }
            NgManagementSection.Word -> ""
        }
    }
    val entries = when (section) {
        NgManagementSection.Header -> ngHeaders
        NgManagementSection.Word -> ngWords
    }
    val hint = when (section) {
        NgManagementSection.Header -> "ヘッダーに含めたい文字列"
        NgManagementSection.Word -> "本文に含めたい文字列"
    }
    val sectionLabel = when (section) {
        NgManagementSection.Header -> "NGヘッダー"
        NgManagementSection.Word -> "NGワード"
    }
    val descriptionText = if (includeHeaderSection) {
        "一致したレスが即座に非表示になります"
    } else {
        "一致したスレッドが即座に非表示になります"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "NG管理",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = descriptionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "閉じる")
                }
            }

            if (allowedSections.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allowedSections.forEach { availableSection ->
                        SectionChip(
                            label = if (availableSection == NgManagementSection.Header) "NGヘッダー" else "NGワード",
                            selected = section == availableSection,
                            onClick = { section = availableSection }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("$sectionLabel を追加") },
                placeholder = { Text(hint) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val trimmed = input.trim()
                            if (trimmed.isEmpty()) return@IconButton
                            when (section) {
                                NgManagementSection.Header -> onAddHeader(trimmed)
                                NgManagementSection.Word -> onAddWord(trimmed)
                            }
                            input = ""
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "追加")
                    }
                }
            )

            if (entries.isEmpty()) {
                Text(
                    text = "まだ登録されていません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries) { entry ->
                        ListItem(
                            headlineContent = { Text(entry) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        when (section) {
                                            NgManagementSection.Header -> onRemoveHeader(entry)
                                            NgManagementSection.Word -> onRemoveWord(entry)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "削除"
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onToggleFiltering,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (ngFilteringEnabled) "NGを無効にする" else "NGを有効にする")
            }
        }
    }
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

private data class GlobalSettingsEntry(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val action: GlobalSettingsAction
)

private val globalSettingsEntries = listOf(
    GlobalSettingsEntry(
        label = "作者",
        description = "X (旧Twitter) で最新の動作報告を確認",
        icon = Icons.Rounded.Person,
        action = GlobalSettingsAction.X
    ),
    GlobalSettingsEntry(
        label = "お問い合わせ",
        description = "admin@valoser.com 宛にメールを送信します",
        icon = Icons.Rounded.Email,
        action = GlobalSettingsAction.Email
    ),
    GlobalSettingsEntry(
        label = "開発元",
        description = "GitHub でソースコードと issue を確認",
        icon = Icons.Rounded.Link,
        action = GlobalSettingsAction.Developer
    ),
    GlobalSettingsEntry(
        label = "プライバシーポリシー",
        description = "外部サイトでプライバシーポリシーを表示",
        icon = Icons.Rounded.PrivacyTip,
        action = GlobalSettingsAction.PrivacyPolicy
    )
)

private enum class GlobalSettingsAction {
    Email,
    X,
    Developer,
    PrivacyPolicy
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalSettingsScreen(
    onBack: () -> Unit,
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean,
    onBackgroundRefreshChanged: (Boolean) -> Unit
) {
    val urlLauncher = rememberUrlLauncher()
    PlatformBackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                ListItem(
                    headlineContent = { Text("バックグラウンド更新 (15分)") },
                    supportingContent = {
                        Text(
                            text = "アプリ起動中は15分ごとに履歴を更新します（通知あり）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = isBackgroundRefreshEnabled,
                            onCheckedChange = { onBackgroundRefreshChanged(it) }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                )
                HorizontalDivider()
            }
            items(globalSettingsEntries) { entry ->
                ListItem(
                    leadingContent = { Icon(imageVector = entry.icon, contentDescription = null) },
                    headlineContent = { Text(entry.label) },
                    supportingContent = {
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            when (entry.action) {
                                GlobalSettingsAction.Email -> {
                                    urlLauncher("mailto:admin@valoser.com?subject=お問い合わせ")
                                }
                                GlobalSettingsAction.X -> {
                                    urlLauncher("https://x.com/may_012345")
                                }
                                GlobalSettingsAction.Developer -> {
                                    urlLauncher("https://github.com/inqueuet/futacha")
                                }
                                GlobalSettingsAction.PrivacyPolicy -> {
                                    urlLauncher("https://note.com/inqueuet/n/nc6ebcc1d6a67")
                                }
                            }
                            onBack()
                        }
                )
                HorizontalDivider()
            }
            item {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("アプリバージョン") },
                    trailingContent = { Text(appVersion) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                )
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun buildHistoryEntryFromPage(
    page: ThreadPage,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    board: BoardSummary
): ThreadHistoryEntry {
    val existingEntry = history.firstOrNull { it.threadId == threadId }
    val firstPost = page.posts.firstOrNull()
    val firstLineFromBody = firstPost
        ?.let { post ->
            messageHtmlToLines(post.messageHtml)
                .firstOrNull { it.isNotBlank() }
                ?.trim()
        }
        .orEmpty()
        .ifBlank { null }
    val candidateTitle = firstLineFromBody
        ?: firstPost?.subject?.takeIf { it.isNotBlank() }
        ?: threadTitle?.takeIf { it.isNotBlank() }
        ?: existingEntry?.title
        ?: "無題"
    val resolvedImageUrl = existingEntry?.titleImageUrl?.takeIf { it.isNotBlank() }
        ?: page.posts.firstOrNull()?.thumbnailUrl.orEmpty()
    val timestamp = Clock.System.now().toEpochMilliseconds()
    return existingEntry?.copy(
        title = candidateTitle,
        titleImageUrl = resolvedImageUrl,
        replyCount = page.posts.size,
        lastVisitedEpochMillis = timestamp
    ) ?: ThreadHistoryEntry(
        threadId = threadId,
        boardId = board.id,
        title = candidateTitle,
        titleImageUrl = resolvedImageUrl,
        boardName = board.name,
        boardUrl = board.url,
        lastVisitedEpochMillis = timestamp,
        replyCount = page.posts.size
    )
}
