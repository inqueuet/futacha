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

internal data class CatalogScreenOverlayDialogInputs(
    val persistCatalogMode: (CatalogMode) -> Unit,
    val updateCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit,
    val updateCatalogGridColumns: (Int) -> Unit,
    val setShowModeDialog: (Boolean) -> Unit,
    val setShowDisplayStyleDialog: (Boolean) -> Unit
)

internal data class CatalogScreenOverlaySearchInputs(
    val currentArchiveSearchScope: () -> ArchiveSearchScope?,
    val setLastArchiveSearchScope: (ArchiveSearchScope?) -> Unit,
    val setArchiveSearchQuery: (String) -> Unit,
    val setShowPastThreadSearchDialog: (Boolean) -> Unit,
    val setIsPastSearchSheetVisible: (Boolean) -> Unit,
    val runPastThreadSearch: (String, ArchiveSearchScope?) -> Boolean,
    val currentPastSearchGeneration: () -> Long,
    val currentPastSearchJob: () -> Job?,
    val setPastSearchGeneration: (Long) -> Unit,
    val setPastSearchJob: (Job?) -> Unit,
    val currentArchiveSearchQuery: () -> String,
    val currentLastArchiveSearchScope: () -> ArchiveSearchScope?,
    val onThreadSelected: (CatalogItem) -> Unit
)

internal data class CatalogScreenOverlaySettingsInputs(
    val board: () -> BoardSummary?,
    val catalogMode: () -> CatalogMode,
    val urlLauncher: (String) -> Unit,
    val stateStore: AppStateStore?,
    val isPrivacyFilterEnabled: () -> Boolean,
    val coroutineScope: CoroutineScope,
    val scrollCatalogToTop: () -> Unit,
    val setIsNgManagementVisible: (Boolean) -> Unit,
    val setIsWatchWordsVisible: (Boolean) -> Unit,
    val setShowSettingsMenu: (Boolean) -> Unit,
    val cookieRepository: CookieRepository?,
    val setIsGlobalSettingsVisible: (Boolean) -> Unit,
    val setIsCookieManagementVisible: (Boolean) -> Unit,
    val onAddNgWord: (String) -> Unit,
    val onRemoveNgWord: (String) -> Unit,
    val onToggleNgFiltering: () -> Unit,
    val onAddWatchWord: (String) -> Unit,
    val onRemoveWatchWord: (String) -> Unit
)

internal fun buildCatalogScreenOverlayBindingsBundle(
    dialogInputs: CatalogScreenOverlayDialogInputs,
    searchInputs: CatalogScreenOverlaySearchInputs,
    settingsInputs: CatalogScreenOverlaySettingsInputs
): CatalogScreenOverlayBindingsBundle {
    return CatalogScreenOverlayBindingsBundle(
        modeDialogCallbacks = buildCatalogModeDialogCallbacks(
            persistCatalogMode = dialogInputs.persistCatalogMode,
            setShowModeDialog = dialogInputs.setShowModeDialog
        ),
        displayStyleDialogCallbacks = buildCatalogDisplayStyleDialogCallbacks(
            updateCatalogDisplayStyle = dialogInputs.updateCatalogDisplayStyle,
            updateCatalogGridColumns = dialogInputs.updateCatalogGridColumns,
            setShowDisplayStyleDialog = dialogInputs.setShowDisplayStyleDialog
        ),
        pastThreadSearchDialogCallbacks = buildCatalogPastThreadSearchDialogCallbacks(
            currentArchiveSearchScope = searchInputs.currentArchiveSearchScope,
            setLastArchiveSearchScope = searchInputs.setLastArchiveSearchScope,
            setArchiveSearchQuery = searchInputs.setArchiveSearchQuery,
            setShowPastThreadSearchDialog = searchInputs.setShowPastThreadSearchDialog,
            setIsPastSearchSheetVisible = searchInputs.setIsPastSearchSheetVisible,
            runPastThreadSearch = searchInputs.runPastThreadSearch
        ),
        pastThreadSearchResultCallbacks = buildCatalogPastThreadSearchResultCallbacks(
            currentPastSearchGeneration = searchInputs.currentPastSearchGeneration,
            currentPastSearchJob = searchInputs.currentPastSearchJob,
            setPastSearchGeneration = searchInputs.setPastSearchGeneration,
            setPastSearchJob = searchInputs.setPastSearchJob,
            setIsPastSearchSheetVisible = searchInputs.setIsPastSearchSheetVisible,
            runPastThreadSearch = searchInputs.runPastThreadSearch,
            currentArchiveSearchQuery = searchInputs.currentArchiveSearchQuery,
            currentLastArchiveSearchScope = searchInputs.currentLastArchiveSearchScope,
            onThreadSelected = searchInputs.onThreadSelected
        ),
        settingsMenuCallbacks = buildCatalogSettingsMenuCallbacks(
            board = settingsInputs.board,
            catalogMode = settingsInputs.catalogMode,
            urlLauncher = settingsInputs.urlLauncher,
            stateStore = settingsInputs.stateStore,
            isPrivacyFilterEnabled = settingsInputs.isPrivacyFilterEnabled,
            coroutineScope = settingsInputs.coroutineScope,
            scrollCatalogToTop = settingsInputs.scrollCatalogToTop,
            setShowDisplayStyleDialog = dialogInputs.setShowDisplayStyleDialog,
            setIsNgManagementVisible = settingsInputs.setIsNgManagementVisible,
            setIsWatchWordsVisible = settingsInputs.setIsWatchWordsVisible,
            setShowSettingsMenu = settingsInputs.setShowSettingsMenu
        ),
        globalSettingsCallbacks = buildCatalogGlobalSettingsCallbacks(
            cookieRepository = settingsInputs.cookieRepository,
            setIsGlobalSettingsVisible = settingsInputs.setIsGlobalSettingsVisible,
            setIsCookieManagementVisible = settingsInputs.setIsCookieManagementVisible
        ),
        ngManagementCallbacks = buildCatalogNgManagementCallbacks(
            setIsNgManagementVisible = settingsInputs.setIsNgManagementVisible,
            onAddWord = settingsInputs.onAddNgWord,
            onRemoveWord = settingsInputs.onRemoveNgWord,
            onToggleFiltering = settingsInputs.onToggleNgFiltering
        ),
        watchWordsCallbacks = buildCatalogWatchWordsCallbacks(
            onAddWord = settingsInputs.onAddWatchWord,
            onRemoveWord = settingsInputs.onRemoveWatchWord,
            setIsWatchWordsVisible = settingsInputs.setIsWatchWordsVisible
        ),
        onCookieManagementBack = { settingsInputs.setIsCookieManagementVisible(false) }
    )
}
