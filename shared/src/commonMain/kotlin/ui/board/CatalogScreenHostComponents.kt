package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun CatalogScreenScaffold(
    history: List<ThreadHistoryEntry>,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    historyDrawerCallbacks: CatalogHistoryDrawerCallbacks,
    drawerState: DrawerState,
    isDrawerOpen: Boolean,
    coroutineScope: CoroutineScope,
    modifier: Modifier,
    snackbarHostState: SnackbarHostState,
    board: BoardSummary?,
    catalogMode: CatalogMode,
    searchQuery: String,
    isSearchActive: Boolean,
    topBarCallbacks: CatalogTopBarCallbacks,
    catalogNavEntries: List<com.valoser.futacha.shared.model.CatalogNavEntryConfig>,
    navigationCallbacks: CatalogNavigationCallbacks,
    uiState: CatalogUiState,
    watchWords: List<String>,
    catalogNgWords: List<String>,
    catalogNgFilteringEnabled: Boolean,
    debouncedSearchQuery: String,
    activeRepository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    performRefresh: () -> Unit,
    isRefreshing: Boolean,
    catalogDisplayStyle: com.valoser.futacha.shared.model.CatalogDisplayStyle,
    catalogGridColumns: Int,
    catalogGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    catalogListState: androidx.compose.foundation.lazy.LazyListState
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            HistoryDrawerContent(
                history = history,
                onHistoryEntryDismissed = onHistoryEntryDismissed,
                onHistoryEntrySelected = historyDrawerCallbacks.onHistoryEntrySelected,
                onBoardClick = historyDrawerCallbacks.onBoardClick,
                onRefreshClick = historyDrawerCallbacks.onRefreshClick,
                onBatchDeleteClick = historyDrawerCallbacks.onBatchDeleteClick,
                onSettingsClick = historyDrawerCallbacks.onSettingsClick
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
                    onSearchQueryChange = topBarCallbacks.onSearchQueryChange,
                    onSearchActiveChange = topBarCallbacks.onSearchActiveChange,
                    onNavigationClick = topBarCallbacks.onNavigationClick,
                    onModeSelected = topBarCallbacks.onModeSelected,
                    onMenuAction = topBarCallbacks.onMenuAction
                )
            },
            bottomBar = {
                CatalogNavigationBar(
                    menuEntries = catalogNavEntries,
                    onNavigate = navigationCallbacks.onNavigate
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
            when (val state = uiState) {
                CatalogUiState.Loading -> LoadingCatalog(modifier = contentModifier)
                is CatalogUiState.Error -> CatalogError(message = state.message, modifier = contentModifier)
                is CatalogUiState.Success -> {
                    val visibleItems by rememberCatalogVisibleItemsState(
                        buildCatalogVisibleItemsRequest(
                            items = state.items,
                            mode = catalogMode,
                            watchWords = watchWords,
                            catalogNgWords = catalogNgWords,
                            catalogNgFilteringEnabled = catalogNgFilteringEnabled,
                            query = debouncedSearchQuery
                        )
                    )
                    CatalogSuccessContent(
                        items = visibleItems,
                        board = board,
                        repository = activeRepository,
                        isSearching = searchQuery.isNotBlank(),
                        onThreadSelected = onThreadSelected,
                        onRefresh = performRefresh,
                        isRefreshing = isRefreshing,
                        displayStyle = catalogDisplayStyle,
                        gridColumns = catalogGridColumns,
                        gridState = catalogGridState,
                        listState = catalogListState,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}

@Composable
internal fun CatalogScreenOverlayHost(
    overlayState: CatalogOverlayState,
    overlayBindings: CatalogScreenOverlayBindingsBundle,
    createThreadDraft: CreateThreadDraft,
    setCreateThreadDraft: (CreateThreadDraft) -> Unit,
    createThreadImage: ImageData?,
    setCreateThreadImage: (ImageData?) -> Unit,
    setCreateThreadDialogVisible: (Boolean) -> Unit,
    attachmentPickerPreference: AttachmentPickerPreference,
    preferredFileManagerPackage: String?,
    board: BoardSummary?,
    archiveSearchQuery: String,
    searchQuery: String,
    catalogMode: CatalogMode,
    catalogDisplayStyle: com.valoser.futacha.shared.model.CatalogDisplayStyle,
    catalogGridColumns: Int,
    pastSearchRuntimeState: CatalogPastSearchRuntimeState,
    watchWords: List<String>,
    catalogNgWords: List<String>,
    catalogNgFilteringEnabled: Boolean,
    isPrivacyFilterEnabled: Boolean,
    createThreadBindings: CatalogCreateThreadBindings,
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    isLightweightModeEnabled: Boolean,
    onLightweightModeChanged: (Boolean) -> Unit,
    manualSaveDirectory: String,
    resolvedManualSaveDirectory: String?,
    onManualSaveDirectoryChanged: (String) -> Unit,
    saveDirectorySelection: SaveDirectorySelection,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    onOpenSaveDirectoryPicker: (() -> Unit)?,
    history: List<ThreadHistoryEntry>,
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?,
    threadMenuEntries: List<com.valoser.futacha.shared.model.ThreadMenuEntryConfig>,
    onThreadMenuEntriesChanged: (List<com.valoser.futacha.shared.model.ThreadMenuEntryConfig>) -> Unit,
    catalogNavEntries: List<com.valoser.futacha.shared.model.CatalogNavEntryConfig>,
    onCatalogNavEntriesChanged: (List<com.valoser.futacha.shared.model.CatalogNavEntryConfig>) -> Unit,
    preferredFileManagerLabel: String?,
    onFileManagerSelected: ((packageName: String, label: String) -> Unit)?,
    onClearPreferredFileManager: (() -> Unit)?,
    cookieRepository: CookieRepository?
) {
    if (overlayState.showCreateThreadDialog) {
        val isCreateThreadSubmitEnabled = canSubmitCreateThread(createThreadDraft.title, createThreadDraft.comment)
        val createThreadDialogCallbacks = buildCatalogCreateThreadDialogCallbacks(
            currentDraft = { createThreadDraft },
            setDraft = setCreateThreadDraft,
            setImage = setCreateThreadImage,
            setShowCreateThreadDialog = setCreateThreadDialogVisible,
            onSubmit = createThreadBindings.submitCreateThread,
            onClear = createThreadBindings.resetCreateThreadDraft
        )
        CreateThreadDialog(
            boardName = board?.name,
            attachmentPickerPreference = attachmentPickerPreference,
            preferredFileManagerPackage = preferredFileManagerPackage,
            name = createThreadDraft.name,
            onNameChange = createThreadDialogCallbacks.onNameChange,
            email = createThreadDraft.email,
            onEmailChange = createThreadDialogCallbacks.onEmailChange,
            title = createThreadDraft.title,
            onTitleChange = createThreadDialogCallbacks.onTitleChange,
            comment = createThreadDraft.comment,
            onCommentChange = createThreadDialogCallbacks.onCommentChange,
            password = createThreadDraft.password,
            onPasswordChange = createThreadDialogCallbacks.onPasswordChange,
            selectedImage = createThreadImage,
            onImageSelected = createThreadDialogCallbacks.onImageSelected,
            isSubmitEnabled = isCreateThreadSubmitEnabled,
            onDismiss = createThreadDialogCallbacks.onDismiss,
            onSubmit = createThreadDialogCallbacks.onSubmit,
            onClear = createThreadDialogCallbacks.onClear
        )
    }

    if (isPrivacyFilterEnabled) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color.White.copy(alpha = 0.5f))
        }
    }

    if (overlayState.showModeDialog) {
        AlertDialog(
            onDismissRequest = overlayBindings.modeDialogCallbacks.onDismiss,
            title = { Text("モード選択") },
            text = {
                Column {
                    CatalogMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    overlayBindings.modeDialogCallbacks.onModeSelected(mode)
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = catalogMode == mode,
                                onClick = { overlayBindings.modeDialogCallbacks.onModeSelected(mode) }
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
                TextButton(onClick = overlayBindings.modeDialogCallbacks.onDismiss) {
                    Text("閉じる")
                }
            }
        )
    }

    if (overlayState.showDisplayStyleDialog) {
        DisplayStyleDialog(
            currentStyle = catalogDisplayStyle,
            currentGridColumns = catalogGridColumns,
            onStyleSelected = overlayBindings.displayStyleDialogCallbacks.onStyleSelected,
            onGridColumnsSelected = overlayBindings.displayStyleDialogCallbacks.onGridColumnsSelected,
            onDismiss = overlayBindings.displayStyleDialogCallbacks.onDismiss
        )
    }

    if (overlayState.showPastThreadSearchDialog) {
        PastThreadSearchDialog(
            initialQuery = buildPastThreadSearchDialogInitialQuery(
                archiveSearchQuery = archiveSearchQuery,
                searchQuery = searchQuery
            ),
            onDismiss = overlayBindings.pastThreadSearchDialogCallbacks.onDismiss,
            onSearch = overlayBindings.pastThreadSearchDialogCallbacks.onSearch
        )
    }

    if (overlayState.isPastSearchSheetVisible) {
        PastThreadSearchResultSheet(
            state = pastSearchRuntimeState.state,
            onDismiss = overlayBindings.pastThreadSearchResultCallbacks.onDismiss,
            onRetry = overlayBindings.pastThreadSearchResultCallbacks.onRetry,
            onItemSelected = overlayBindings.pastThreadSearchResultCallbacks.onItemSelected
        )
    }

    if (overlayState.showSettingsMenu) {
        CatalogSettingsSheet(
            onDismiss = overlayBindings.settingsMenuCallbacks.onDismiss,
            onAction = overlayBindings.settingsMenuCallbacks.onAction
        )
    }

    if (overlayState.isGlobalSettingsVisible) {
        GlobalSettingsScreen(
            onBack = overlayBindings.globalSettingsCallbacks.onBack,
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
            onOpenCookieManager = overlayBindings.globalSettingsCallbacks.onOpenCookieManager,
            historyEntries = history,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSavedThreadRepository,
            threadMenuEntries = threadMenuEntries,
            onThreadMenuEntriesChanged = onThreadMenuEntriesChanged,
            catalogNavEntries = catalogNavEntries,
            onCatalogNavEntriesChanged = onCatalogNavEntriesChanged,
            preferredFileManagerLabel = preferredFileManagerLabel,
            onFileManagerSelected = onFileManagerSelected,
            onClearPreferredFileManager = onClearPreferredFileManager
        )
    }

    if (overlayState.isCookieManagementVisible && cookieRepository != null) {
        CookieManagementScreen(
            onBack = overlayBindings.onCookieManagementBack,
            repository = cookieRepository
        )
    }

    if (overlayState.isNgManagementVisible) {
        val ngSheetState = resolveCatalogNgManagementSheetState(
            ngWords = catalogNgWords,
            ngFilteringEnabled = catalogNgFilteringEnabled
        )
        NgManagementSheet(
            ngHeaders = ngSheetState.ngHeaders,
            ngWords = ngSheetState.ngWords,
            ngFilteringEnabled = ngSheetState.ngFilteringEnabled,
            onDismiss = overlayBindings.ngManagementCallbacks.onDismiss,
            onAddHeader = {},
            onAddWord = overlayBindings.ngManagementCallbacks.onAddWord,
            onRemoveHeader = {},
            onRemoveWord = overlayBindings.ngManagementCallbacks.onRemoveWord,
            onToggleFiltering = overlayBindings.ngManagementCallbacks.onToggleFiltering,
            includeHeaderSection = ngSheetState.includeHeaderSection
        )
    }

    if (overlayState.isWatchWordsVisible) {
        WatchWordsSheet(
            watchWords = watchWords,
            onAddWord = overlayBindings.watchWordsCallbacks.onAddWord,
            onRemoveWord = overlayBindings.watchWordsCallbacks.onRemoveWord,
            onDismiss = overlayBindings.watchWordsCallbacks.onDismiss
        )
    }
}
