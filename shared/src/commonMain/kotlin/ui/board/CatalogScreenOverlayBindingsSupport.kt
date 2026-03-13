package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal data class CatalogScreenOverlayBindingsBundle(
    val modeDialogCallbacks: CatalogModeDialogCallbacks,
    val displayStyleDialogCallbacks: CatalogDisplayStyleDialogCallbacks,
    val pastThreadSearchDialogCallbacks: CatalogPastThreadSearchDialogCallbacks,
    val pastThreadSearchResultCallbacks: CatalogPastThreadSearchResultCallbacks,
    val settingsMenuCallbacks: CatalogSettingsMenuCallbacks,
    val globalSettingsCallbacks: CatalogGlobalSettingsCallbacks,
    val ngManagementCallbacks: CatalogNgManagementCallbacks,
    val watchWordsCallbacks: CatalogWatchWordsCallbacks,
    val onCookieManagementBack: () -> Unit
)

internal fun buildCatalogScreenOverlayBindingsBundle(
    persistCatalogMode: (CatalogMode) -> Unit,
    updateCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit,
    updateCatalogGridColumns: (Int) -> Unit,
    currentArchiveSearchScope: () -> ArchiveSearchScope?,
    setLastArchiveSearchScope: (ArchiveSearchScope?) -> Unit,
    setArchiveSearchQuery: (String) -> Unit,
    setShowPastThreadSearchDialog: (Boolean) -> Unit,
    setIsPastSearchSheetVisible: (Boolean) -> Unit,
    runPastThreadSearch: (String, ArchiveSearchScope?) -> Boolean,
    currentPastSearchGeneration: () -> Long,
    currentPastSearchJob: () -> Job?,
    setPastSearchGeneration: (Long) -> Unit,
    setPastSearchJob: (Job?) -> Unit,
    currentArchiveSearchQuery: () -> String,
    currentLastArchiveSearchScope: () -> ArchiveSearchScope?,
    onThreadSelected: (CatalogItem) -> Unit,
    board: () -> BoardSummary?,
    catalogMode: () -> CatalogMode,
    urlLauncher: (String) -> Unit,
    stateStore: AppStateStore?,
    isPrivacyFilterEnabled: () -> Boolean,
    coroutineScope: CoroutineScope,
    scrollCatalogToTop: () -> Unit,
    setShowModeDialog: (Boolean) -> Unit,
    setShowDisplayStyleDialog: (Boolean) -> Unit,
    setIsNgManagementVisible: (Boolean) -> Unit,
    setIsWatchWordsVisible: (Boolean) -> Unit,
    setShowSettingsMenu: (Boolean) -> Unit,
    cookieRepository: CookieRepository?,
    setIsGlobalSettingsVisible: (Boolean) -> Unit,
    setIsCookieManagementVisible: (Boolean) -> Unit,
    onAddNgWord: (String) -> Unit,
    onRemoveNgWord: (String) -> Unit,
    onToggleNgFiltering: () -> Unit,
    onAddWatchWord: (String) -> Unit,
    onRemoveWatchWord: (String) -> Unit
): CatalogScreenOverlayBindingsBundle {
    return CatalogScreenOverlayBindingsBundle(
        modeDialogCallbacks = buildCatalogModeDialogCallbacks(
            persistCatalogMode = persistCatalogMode,
            setShowModeDialog = setShowModeDialog
        ),
        displayStyleDialogCallbacks = buildCatalogDisplayStyleDialogCallbacks(
            updateCatalogDisplayStyle = updateCatalogDisplayStyle,
            updateCatalogGridColumns = updateCatalogGridColumns,
            setShowDisplayStyleDialog = setShowDisplayStyleDialog
        ),
        pastThreadSearchDialogCallbacks = buildCatalogPastThreadSearchDialogCallbacks(
            currentArchiveSearchScope = currentArchiveSearchScope,
            setLastArchiveSearchScope = setLastArchiveSearchScope,
            setArchiveSearchQuery = setArchiveSearchQuery,
            setShowPastThreadSearchDialog = setShowPastThreadSearchDialog,
            setIsPastSearchSheetVisible = setIsPastSearchSheetVisible,
            runPastThreadSearch = runPastThreadSearch
        ),
        pastThreadSearchResultCallbacks = buildCatalogPastThreadSearchResultCallbacks(
            currentPastSearchGeneration = currentPastSearchGeneration,
            currentPastSearchJob = currentPastSearchJob,
            setPastSearchGeneration = setPastSearchGeneration,
            setPastSearchJob = setPastSearchJob,
            setIsPastSearchSheetVisible = setIsPastSearchSheetVisible,
            runPastThreadSearch = runPastThreadSearch,
            currentArchiveSearchQuery = currentArchiveSearchQuery,
            currentLastArchiveSearchScope = currentLastArchiveSearchScope,
            onThreadSelected = onThreadSelected
        ),
        settingsMenuCallbacks = buildCatalogSettingsMenuCallbacks(
            board = board,
            catalogMode = catalogMode,
            urlLauncher = urlLauncher,
            stateStore = stateStore,
            isPrivacyFilterEnabled = isPrivacyFilterEnabled,
            coroutineScope = coroutineScope,
            scrollCatalogToTop = scrollCatalogToTop,
            setShowDisplayStyleDialog = setShowDisplayStyleDialog,
            setIsNgManagementVisible = setIsNgManagementVisible,
            setIsWatchWordsVisible = setIsWatchWordsVisible,
            setShowSettingsMenu = setShowSettingsMenu
        ),
        globalSettingsCallbacks = buildCatalogGlobalSettingsCallbacks(
            cookieRepository = cookieRepository,
            setIsGlobalSettingsVisible = setIsGlobalSettingsVisible,
            setIsCookieManagementVisible = setIsCookieManagementVisible
        ),
        ngManagementCallbacks = buildCatalogNgManagementCallbacks(
            setIsNgManagementVisible = setIsNgManagementVisible,
            onAddWord = onAddNgWord,
            onRemoveWord = onRemoveNgWord,
            onToggleFiltering = onToggleNgFiltering
        ),
        watchWordsCallbacks = buildCatalogWatchWordsCallbacks(
            onAddWord = onAddWatchWord,
            onRemoveWord = onRemoveWatchWord,
            setIsWatchWordsVisible = setIsWatchWordsVisible
        ),
        onCookieManagementBack = { setIsCookieManagementVisible(false) }
    )
}
