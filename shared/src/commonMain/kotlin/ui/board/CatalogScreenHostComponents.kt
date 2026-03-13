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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.CatalogMode
import kotlinx.coroutines.launch

@Composable
internal fun CatalogScreenScaffold(
    bindings: CatalogScreenScaffoldBindings,
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
                onSettingsClick = bindings.historyDrawerCallbacks.onSettingsClick
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(bindings.snackbarHostState) },
            topBar = {
                CatalogTopBar(
                    board = bindings.board,
                    mode = bindings.catalogMode,
                    searchQuery = bindings.searchQuery,
                    isSearchActive = bindings.isSearchActive,
                    onSearchQueryChange = bindings.topBarCallbacks.onSearchQueryChange,
                    onSearchActiveChange = bindings.topBarCallbacks.onSearchActiveChange,
                    onNavigationClick = bindings.topBarCallbacks.onNavigationClick,
                    onModeSelected = bindings.topBarCallbacks.onModeSelected,
                    onMenuAction = bindings.topBarCallbacks.onMenuAction
                )
            },
            bottomBar = {
                CatalogNavigationBar(
                    menuEntries = bindings.catalogNavEntries,
                    onNavigate = bindings.navigationCallbacks.onNavigate
                )
            }
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(bindings.isDrawerOpen) {
                    if (!bindings.isDrawerOpen) return@pointerInput
                    awaitPointerEventScope {
                        awaitFirstDown()
                        bindings.coroutineScope.launch { bindings.drawerState.close() }
                    }
                }
            when (val state = bindings.uiState) {
                CatalogUiState.Loading -> LoadingCatalog(modifier = contentModifier)
                is CatalogUiState.Error -> CatalogError(message = state.message, modifier = contentModifier)
                is CatalogUiState.Success -> {
                    val visibleItems by rememberCatalogVisibleItemsState(
                        buildCatalogVisibleItemsRequest(
                            items = state.items,
                            mode = bindings.catalogMode,
                            watchWords = bindings.watchWords,
                            catalogNgWords = bindings.catalogNgWords,
                            catalogNgFilteringEnabled = bindings.catalogNgFilteringEnabled,
                            query = bindings.debouncedSearchQuery
                        )
                    )
                    CatalogSuccessContent(
                        items = visibleItems,
                        board = bindings.board,
                        repository = bindings.activeRepository,
                        isSearching = bindings.searchQuery.isNotBlank(),
                        onThreadSelected = bindings.onThreadSelected,
                        onRefresh = bindings.performRefresh,
                        isRefreshing = bindings.isRefreshing,
                        displayStyle = bindings.catalogDisplayStyle,
                        gridColumns = bindings.catalogGridColumns,
                        gridState = bindings.catalogGridState,
                        listState = bindings.catalogListState,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}

@Composable
internal fun CatalogScreenOverlayHost(
    bindings: CatalogScreenOverlayHostBindings
) {
    if (bindings.overlayState.showCreateThreadDialog) {
        val isCreateThreadSubmitEnabled = canSubmitCreateThread(
            bindings.createThreadDraft.title,
            bindings.createThreadDraft.comment
        )
        val createThreadDialogCallbacks = buildCatalogCreateThreadDialogCallbacks(
            currentDraft = { bindings.createThreadDraft },
            setDraft = bindings.setCreateThreadDraft,
            setImage = bindings.setCreateThreadImage,
            setShowCreateThreadDialog = bindings.setCreateThreadDialogVisible,
            onSubmit = bindings.createThreadBindings.submitCreateThread,
            onClear = bindings.createThreadBindings.resetCreateThreadDraft
        )
        CreateThreadDialog(
            boardName = bindings.board?.name,
            attachmentPickerPreference = bindings.preferencesState.attachmentPickerPreference,
            preferredFileManagerPackage = bindings.preferencesState.preferredFileManagerPackage,
            name = bindings.createThreadDraft.name,
            onNameChange = createThreadDialogCallbacks.onNameChange,
            email = bindings.createThreadDraft.email,
            onEmailChange = createThreadDialogCallbacks.onEmailChange,
            title = bindings.createThreadDraft.title,
            onTitleChange = createThreadDialogCallbacks.onTitleChange,
            comment = bindings.createThreadDraft.comment,
            onCommentChange = createThreadDialogCallbacks.onCommentChange,
            password = bindings.createThreadDraft.password,
            onPasswordChange = createThreadDialogCallbacks.onPasswordChange,
            selectedImage = bindings.createThreadImage,
            onImageSelected = createThreadDialogCallbacks.onImageSelected,
            isSubmitEnabled = isCreateThreadSubmitEnabled,
            onDismiss = createThreadDialogCallbacks.onDismiss,
            onSubmit = createThreadDialogCallbacks.onSubmit,
            onClear = createThreadDialogCallbacks.onClear
        )
    }

    if (bindings.isPrivacyFilterEnabled) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color.White.copy(alpha = 0.5f))
        }
    }

    if (bindings.overlayState.showModeDialog) {
        AlertDialog(
            onDismissRequest = bindings.overlayBindings.modeDialogCallbacks.onDismiss,
            title = { Text("モード選択") },
            text = {
                Column {
                    CatalogMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    bindings.overlayBindings.modeDialogCallbacks.onModeSelected(mode)
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = bindings.catalogMode == mode,
                                onClick = { bindings.overlayBindings.modeDialogCallbacks.onModeSelected(mode) }
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
                TextButton(onClick = bindings.overlayBindings.modeDialogCallbacks.onDismiss) {
                    Text("閉じる")
                }
            }
        )
    }

    if (bindings.overlayState.showDisplayStyleDialog) {
        DisplayStyleDialog(
            currentStyle = bindings.catalogDisplayStyle,
            currentGridColumns = bindings.catalogGridColumns,
            onStyleSelected = bindings.overlayBindings.displayStyleDialogCallbacks.onStyleSelected,
            onGridColumnsSelected = bindings.overlayBindings.displayStyleDialogCallbacks.onGridColumnsSelected,
            onDismiss = bindings.overlayBindings.displayStyleDialogCallbacks.onDismiss
        )
    }

    if (bindings.overlayState.showPastThreadSearchDialog) {
        PastThreadSearchDialog(
            initialQuery = buildPastThreadSearchDialogInitialQuery(
                archiveSearchQuery = bindings.archiveSearchQuery,
                searchQuery = bindings.searchQuery
            ),
            onDismiss = bindings.overlayBindings.pastThreadSearchDialogCallbacks.onDismiss,
            onSearch = bindings.overlayBindings.pastThreadSearchDialogCallbacks.onSearch
        )
    }

    if (bindings.overlayState.isPastSearchSheetVisible) {
        PastThreadSearchResultSheet(
            state = bindings.pastSearchRuntimeState.state,
            onDismiss = bindings.overlayBindings.pastThreadSearchResultCallbacks.onDismiss,
            onRetry = bindings.overlayBindings.pastThreadSearchResultCallbacks.onRetry,
            onItemSelected = bindings.overlayBindings.pastThreadSearchResultCallbacks.onItemSelected
        )
    }

    if (bindings.overlayState.showSettingsMenu) {
        CatalogSettingsSheet(
            onDismiss = bindings.overlayBindings.settingsMenuCallbacks.onDismiss,
            onAction = bindings.overlayBindings.settingsMenuCallbacks.onAction
        )
    }

    if (bindings.overlayState.isGlobalSettingsVisible) {
        GlobalSettingsScreen(
            onBack = bindings.overlayBindings.globalSettingsCallbacks.onBack,
            preferencesState = bindings.preferencesState,
            preferencesCallbacks = bindings.preferencesCallbacks,
            onOpenCookieManager = bindings.overlayBindings.globalSettingsCallbacks.onOpenCookieManager,
            historyEntries = bindings.history,
            fileSystem = bindings.fileSystem,
            autoSavedThreadRepository = bindings.autoSavedThreadRepository,
        )
    }

    if (bindings.overlayState.isCookieManagementVisible && bindings.cookieRepository != null) {
        CookieManagementScreen(
            onBack = bindings.overlayBindings.onCookieManagementBack,
            repository = bindings.cookieRepository
        )
    }

    if (bindings.overlayState.isNgManagementVisible) {
        val ngSheetState = resolveCatalogNgManagementSheetState(
            ngWords = bindings.catalogNgWords,
            ngFilteringEnabled = bindings.catalogNgFilteringEnabled
        )
        NgManagementSheet(
            ngHeaders = ngSheetState.ngHeaders,
            ngWords = ngSheetState.ngWords,
            ngFilteringEnabled = ngSheetState.ngFilteringEnabled,
            onDismiss = bindings.overlayBindings.ngManagementCallbacks.onDismiss,
            onAddHeader = {},
            onAddWord = bindings.overlayBindings.ngManagementCallbacks.onAddWord,
            onRemoveHeader = {},
            onRemoveWord = bindings.overlayBindings.ngManagementCallbacks.onRemoveWord,
            onToggleFiltering = bindings.overlayBindings.ngManagementCallbacks.onToggleFiltering,
            includeHeaderSection = ngSheetState.includeHeaderSection
        )
    }

    if (bindings.overlayState.isWatchWordsVisible) {
        WatchWordsSheet(
            watchWords = bindings.watchWords,
            onAddWord = bindings.overlayBindings.watchWordsCallbacks.onAddWord,
            onRemoveWord = bindings.overlayBindings.watchWordsCallbacks.onRemoveWord,
            onDismiss = bindings.overlayBindings.watchWordsCallbacks.onDismiss
        )
    }
}
