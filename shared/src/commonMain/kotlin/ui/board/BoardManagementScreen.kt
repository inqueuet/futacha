package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    modifier: Modifier = Modifier,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isAddDialogVisible by rememberSaveable { mutableStateOf(false) }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isDrawerOpen,
        drawerContent = {
            HistoryDrawerContent(
                history = history,
                onHistoryEntryDismissed = onHistoryEntryDismissed,
                onHistoryEntrySelected = handleHistorySelection
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = "履歴を開く"
                            )
                        }
                    },
                    title = { Text("ふたば") },
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
                                        if (action == BoardManagementMenuAction.ADD) {
                                            isAddDialogVisible = true
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("${action.label} はモック動作です")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                items(
                    items = boards,
                    key = { it.id }
                ) { board ->
                    BoardSummaryCard(
                        board = board,
                        onClick = { onBoardSelected(board) }
                    )
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
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit
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
            HistoryBottomBar()
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
private fun HistoryBottomBar() {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryBottomIcon(Icons.Rounded.Home, "板")
            HistoryBottomIcon(Icons.Rounded.WatchLater, "未読")
            HistoryBottomIcon(Icons.Rounded.Timeline, "勢い")
            HistoryBottomIcon(Icons.Rounded.Settings, "設定")
        }
    }
}

@Composable
private fun HistoryBottomIcon(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

    PlatformBackHandler(enabled = isDrawerOpen) {
        coroutineScope.launch { drawerState.close() }
    }
    PlatformBackHandler(enabled = !isDrawerOpen) {
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
    val currentState = uiState.value

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isDrawerOpen,
        drawerContent = {
            HistoryDrawerContent(
                history = history,
                onHistoryEntryDismissed = onHistoryEntryDismissed,
                onHistoryEntrySelected = handleHistorySelection
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
                    onNavigationClick = { coroutineScope.launch { drawerState.open() } },
                    onModeSelected = { catalogMode = it },
                    onSearchClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("検索はモック動作です")
                        }
                    },
                    onMenuAction = { action ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("${action.label} はモック動作です")
                        }
                    }
                )
            },
            bottomBar = {
                CatalogNavigationBar(
                    current = CatalogNavDestination.Catalog,
                    onNavigate = { destination ->
                        when (destination) {
                            CatalogNavDestination.Boards -> onBack()
                            CatalogNavDestination.Catalog -> Unit
                            else -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("${destination.label} は未実装です")
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
                is CatalogUiState.Success -> CatalogSuccessContent(
                    items = catalogMode.applyLocalSort(state.items),
                    onThreadSelected = onThreadSelected,
                    modifier = contentModifier
                )
            }
        }
    }
}

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
    onThreadSelected: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier
) {
    CatalogGrid(
        items = items,
        onThreadSelected = onThreadSelected,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogGrid(
    items: List<CatalogItem>,
    onThreadSelected: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        modifier = modifier.padding(horizontal = 8.dp),
        columns = GridCells.Adaptive(120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)
    ) {
        items(
            items = items,
            key = { it.id }
        ) { catalogItem ->
            CatalogCard(
                item = catalogItem,
                onClick = { onThreadSelected(catalogItem) }
            )
        }
    }
}

@Composable
private fun CatalogCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
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
            Box {
                if (item.thumbnailUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(platformContext)
                            .data(item.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title ?: "サムネイル",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item.title ?: "無題",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
    onNavigationClick: () -> Unit,
    onModeSelected: (CatalogMode) -> Unit,
    onSearchClick: () -> Unit,
    onMenuAction: (CatalogMenuAction) -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isSortExpanded by remember { mutableStateOf(false) }

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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = board?.name ?: "カタログ",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    board?.category
                        ?.takeIf { it.isNotBlank() }
                        ?.let { category ->
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    Box {
                        AssistChip(
                            onClick = { isSortExpanded = true },
                            label = { Text("モード") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Timeline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                                labelColor = MaterialTheme.colorScheme.onPrimary,
                                leadingIconContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        DropdownMenu(
                            expanded = isSortExpanded,
                            onDismissRequest = { isSortExpanded = false }
                        ) {
                            CatalogMode.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        isSortExpanded = false
                                        onModeSelected(option)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
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
private fun CatalogNavigationBar(
    current: CatalogNavDestination,
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

private enum class CatalogMenuAction(val label: String) {
    Display("表示オプション"),
    Toolbar("ツールバー編集"),
    Settings("設定"),
    Help("ヘルプ")
}

private enum class CatalogNavDestination(val label: String, val icon: ImageVector) {
    Boards("板一覧", Icons.Outlined.Menu),
    Catalog("ｶﾀﾛｸﾞ", Icons.Outlined.Image),
    Trend("勢い", Icons.Rounded.Timeline),
    Settings("設定", Icons.Rounded.Settings),
    Favorites("お気に入り", Icons.Rounded.Favorite)
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
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    repository: BoardRepository? = null,
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
                    val page = activeRepository.getThread(board.url, threadId)
                    // Check if still active before updating state
                    if (isActive) {
                        uiState.value = ThreadUiState.Success(page)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Rethrow cancellation to properly cancel the coroutine
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
        is ThreadUiState.Success -> initialReplyCount ?: currentState.page.posts.size
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

    LaunchedEffect(threadId) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .debounce(500) // Increased from 250ms to reduce write frequency
            .collect { (index, offset) ->
                // Only persist if position has meaningfully changed
                if (index > 0 || offset > 0) {
                    onScrollPositionPersist(threadId, index, offset)
                }
            }
    }

    val handleHistorySelection: (ThreadHistoryEntry) -> Unit = { entry ->
        coroutineScope.launch { drawerState.close() }
        onHistoryEntrySelected(entry)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isDrawerOpen,
        drawerContent = {
            HistoryDrawerContent(
                history = history,
                onHistoryEntryDismissed = onHistoryEntryDismissed,
                onHistoryEntrySelected = handleHistorySelection
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
                    onBack = onBack,
                    onOpenHistory = { coroutineScope.launch { drawerState.open() } },
                    onSearch = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("検索はモック動作です")
                        }
                    }
                )
            },
            bottomBar = {
                ThreadActionBar(
                    onAction = { action ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("${action.label} はモック動作です")
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

            when (val state = uiState.value) {
                ThreadUiState.Loading -> ThreadLoading(modifier = contentModifier)
                is ThreadUiState.Error -> ThreadError(
                    message = state.message,
                    modifier = contentModifier,
                    onRetry = refreshThread
                )

                is ThreadUiState.Success -> {
                    val displayedReplies = resolvedReplyCount ?: state.page.posts.size
                    ThreadContent(
                        page = state.page,
                        listState = lazyListState,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadTopBar(
    boardName: String,
    threadTitle: String,
    replyCount: Int?,
    statusLabel: String?,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onSearch: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "戻る"
                )
            }
        },
        title = {
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
        },
        actions = {
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

@Composable
private fun ThreadContent(
    page: ThreadPage,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val posterIdLabels = remember(page.posts) {
        buildPosterIdLabels(page.posts)
    }
    val postIndex = remember(page.posts) { page.posts.associateBy { it.id } }
    val referencedByMap = remember(page.posts) { buildReferencedPostsMap(page.posts) }
    val postsByPosterId = remember(page.posts) { buildPostsByPosterId(page.posts) }
    var quotePreviewState by remember(page.posts) { mutableStateOf<QuotePreviewState?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
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
                        }
                )
                if (index != page.posts.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        thickness = 0.5.dp
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

    val firstVisibleSize = visibleItems.first().size.coerceAtLeast(1)
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

@Composable
private fun ThreadPostCard(
    post: Post,
    isOp: Boolean,
    posterIdLabel: PosterIdLabel?,
    posterIdValue: String?,
    onQuoteClick: (QuoteReference) -> Unit,
    onPosterIdClick: (() -> Unit)? = null,
    onReferencedByClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var saidaneLabel by remember(post.id, post.saidaneLabel) { mutableStateOf(post.saidaneLabel) }
    val platformContext = LocalPlatformContext.current
    val backgroundColor = when {
        post.isDeleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
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
            onSaidaneClick = {
                saidaneLabel = incrementSaidaneLabel(saidaneLabel)
            },
            onPosterIdClick = onPosterIdClick,
            onReferencedByClick = onReferencedByClick
        )
        post.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = ImageRequest.Builder(platformContext)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "添付画像",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        }
        ThreadMessageText(
            messageHtml = post.messageHtml,
            isDeleted = post.isDeleted,
            quoteReferences = post.quoteReferences,
            onQuoteClick = onQuoteClick
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
    onSaidaneClick: () -> Unit,
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
                saidaneLabel?.let { label ->
                    SaidaneLink(
                        label = label,
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
    modifier: Modifier = Modifier
) {
    val annotated: AnnotatedString = remember(messageHtml, quoteReferences) {
        buildAnnotatedMessage(messageHtml, quoteReferences)
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

private const val QUOTE_ANNOTATION_TAG = "quote"

private fun buildAnnotatedMessage(
    html: String,
    quoteReferences: List<QuoteReference>
): AnnotatedString {
    val lines = messageHtmlToLines(html)
    var quoteIndex = 0
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            val content = line.trimEnd()
            val isQuote = content.startsWith(">") || content.startsWith("＞")
            if (isQuote) {
                val spanStyle = SpanStyle(color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                val annotationIndex = quoteIndex
                val reference = quoteReferences.getOrNull(annotationIndex)
                if (reference != null && reference.targetPostIds.isNotEmpty()) {
                    pushStringAnnotation(QUOTE_ANNOTATION_TAG, annotationIndex.toString())
                    withStyle(spanStyle) { append(content) }
                    pop()
                } else {
                    withStyle(spanStyle) { append(content) }
                }
                quoteIndex += 1
            } else {
                append(content)
            }
            if (index != lines.lastIndex) {
                append("\n")
            }
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

private fun extractTimestampWithoutId(timestamp: String): String {
    val idx = timestamp.indexOf("ID:")
    if (idx == -1) return timestamp.trim()
    return timestamp.substring(0, idx).trimEnd()
}

@Composable
private fun QuotePreviewDialog(
    state: QuotePreviewState,
    onDismiss: () -> Unit,
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                            posterIdValue = normalizePosterIdValue(post.posterId),
                            posterIdLabel = state.posterIdLabels[post.id],
                            onQuoteClick = onQuoteClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        if (index != state.targetPosts.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                thickness = 0.5.dp
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
    Refresh("更新", Icons.Rounded.Refresh),
    Gallery("画像", Icons.Outlined.Image),
    Share("共有", Icons.Rounded.Share),
    Favorite("お気に入り", Icons.Rounded.BookmarkAdd),
    Settings("設定", Icons.Rounded.Settings)
}

internal fun incrementSaidaneLabel(current: String?): String {
    val normalized = current?.trim().orEmpty()
    val existing = normalized.takeIf { it.isNotBlank() }?.let {
        Regex("(\\d+)$").find(it)?.value?.toIntOrNull()
    } ?: 0
    val next = (existing + 1).coerceAtLeast(1)
    return "そうだねx$next"
}
