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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
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
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.model.normalizeThreadMenuEntries
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.normalizeCatalogNavEntries
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.ui.image.resolveImageCacheDirectory
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.audio.createTextSpeaker
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.util.resolveThreadTitle
import com.valoser.futacha.shared.util.rememberUrlLauncher
import com.valoser.futacha.shared.ui.normalizeBoardUrl
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlin.math.round
import kotlin.text.RegexOption
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import com.valoser.futacha.shared.repository.CookieRepository
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// Import extracted components
import com.valoser.futacha.shared.ui.board.CatalogScreen
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.board.GlobalSettingsScreen
import com.valoser.futacha.shared.ui.board.HistoryDrawerContent
import com.valoser.futacha.shared.ui.board.NgManagementSheet
import com.valoser.futacha.shared.ui.board.formatLastVisited
import com.valoser.futacha.shared.ui.board.buildHistoryEntryFromPage
import com.valoser.futacha.shared.ui.board.resolveEffectiveBoardUrl

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun BoardManagementScreen(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    cookieRepository: CookieRepository? = null,
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
    isLightweightModeEnabled: Boolean = false,
    onLightweightModeChanged: (Boolean) -> Unit = {},
    manualSaveDirectory: String = DEFAULT_MANUAL_SAVE_ROOT,
    manualSaveLocation: com.valoser.futacha.shared.model.SaveLocation? = null,
    resolvedManualSaveDirectory: String? = null,
    onManualSaveDirectoryChanged: (String) -> Unit = {},
    attachmentPickerPreference: AttachmentPickerPreference = AttachmentPickerPreference.MEDIA,
    saveDirectorySelection: SaveDirectorySelection = SaveDirectorySelection.MANUAL_INPUT,
    onAttachmentPickerPreferenceChanged: (AttachmentPickerPreference) -> Unit = {},
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit = {},
    onOpenSaveDirectoryPicker: (() -> Unit)? = null,
    httpClient: io.ktor.client.HttpClient? = null,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    preferredFileManagerPackage: String? = null,
    preferredFileManagerLabel: String? = null,
    onFileManagerSelected: ((packageName: String, label: String) -> Unit)? = null,
    onClearPreferredFileManager: (() -> Unit)? = null,
    threadMenuEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries(),
    onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit = {},
    catalogNavEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries(),
    onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isAddDialogVisible by rememberSaveable { mutableStateOf(false) }
    var isDeleteMode by rememberSaveable { mutableStateOf(false) }
    var isReorderMode by rememberSaveable { mutableStateOf(false) }
    var boardToDelete by remember { mutableStateOf<BoardSummary?>(null) }
    var isGlobalSettingsVisible by remember { mutableStateOf(false) }
    var isCookieManagementVisible by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerOpen by remember {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
        }
    }
    val onHistoryEntrySelectedState = rememberUpdatedState(onHistoryEntrySelected)
    val onHistoryRefreshState = rememberUpdatedState(onHistoryRefresh)
    val onHistoryClearedState = rememberUpdatedState(onHistoryCleared)
    val handleHistorySelection: (ThreadHistoryEntry) -> Unit = remember(drawerState, scope) {
        { entry ->
            scope.launch { drawerState.close() }
            onHistoryEntrySelectedState.value(entry)
        }
    }

    var isHistoryRefreshing by remember { mutableStateOf(false) }
    val handleHistoryRefresh: () -> Unit = remember(scope, snackbarHostState) {
        handleHistoryRefresh@{
            if (isHistoryRefreshing) return@handleHistoryRefresh
            isHistoryRefreshing = true
            scope.launch {
                try {
                    onHistoryRefreshState.value()
                    snackbarHostState.showSnackbar("履歴を更新しました")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("履歴の更新に失敗しました: ${e.message ?: "不明なエラー"}")
                } finally {
                    isHistoryRefreshing = false
                }
            }
        }
    }

    val handleBatchDelete: () -> Unit = remember(scope, snackbarHostState, drawerState) {
        {
            scope.launch {
                onHistoryClearedState.value()
                snackbarHostState.showSnackbar("履歴を一括削除しました")
                drawerState.close()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
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
                                            BoardManagementMenuAction.SAVED_THREADS -> {
                                                // Handled by parent via onMenuAction callback.
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
            onBackgroundRefreshChanged = onBackgroundRefreshChanged,
            isLightweightModeEnabled = isLightweightModeEnabled,
            onLightweightModeChanged = onLightweightModeChanged,
            manualSaveDirectory = manualSaveDirectory,
            resolvedManualSaveDirectory = resolvedManualSaveDirectory,
            onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
            saveDirectorySelection = saveDirectorySelection,
            onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
            onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
            onOpenCookieManager = cookieRepository?.let {
                {
                    isGlobalSettingsVisible = false
                    isCookieManagementVisible = true
                }
            },
            preferredFileManagerLabel = preferredFileManagerLabel,
            onFileManagerSelected = onFileManagerSelected,
            onClearPreferredFileManager = onClearPreferredFileManager,
            historyEntries = history,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSavedThreadRepository,
            threadMenuEntries = threadMenuEntries,
            onThreadMenuEntriesChanged = onThreadMenuEntriesChanged,
            catalogNavEntries = catalogNavEntries,
            onCatalogNavEntriesChanged = onCatalogNavEntriesChanged
        )
    }

    if (isCookieManagementVisible && cookieRepository != null) {
        CookieManagementScreen(
            onBack = { isCookieManagementVisible = false },
            repository = cookieRepository
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
    val normalizedInputUrl = remember(trimmedUrl) {
        runCatching { normalizeBoardUrl(trimmedUrl) }.getOrDefault(trimmedUrl)
    }
    val existingBoardUrls = remember(existingBoards) {
        existingBoards.map { it.url.trim() }
    }
    val normalizedExistingUrls = remember(existingBoardUrls) {
        existingBoardUrls.map { urlValue ->
            runCatching { normalizeBoardUrl(urlValue) }.getOrDefault(urlValue)
        }
    }

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

    val isDuplicateUrl = hasUrl && isValidUrl &&
        normalizedExistingUrls.any { it.equals(normalizedInputUrl, ignoreCase = true) }
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
                    onSubmit(trimmedName, normalizedInputUrl)
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
