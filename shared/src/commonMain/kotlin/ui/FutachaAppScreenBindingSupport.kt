package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.ui.board.buildScreenContract
import com.valoser.futacha.shared.ui.board.ScreenContract
import com.valoser.futacha.shared.ui.board.ScreenHistoryCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope

internal data class FutachaScreenPreferencesStateInputs(
    val appVersion: String,
    val isBackgroundRefreshEnabled: Boolean,
    val isAdsEnabled: Boolean = true,
    val isLightweightModeEnabled: Boolean,
    val manualSaveDirectory: String,
    val manualSaveLocation: SaveLocation,
    val resolvedManualSaveDirectory: String?,
    val attachmentPickerPreference: AttachmentPickerPreference,
    val saveDirectorySelection: SaveDirectorySelection,
    val preferredFileManagerPackage: String?,
    val preferredFileManagerLabel: String?,
    val threadMenuEntries: List<ThreadMenuEntryConfig>,
    val catalogNavEntries: List<CatalogNavEntryConfig>
)

internal data class FutachaScreenPreferencesCallbackInputs(
    val preferenceMutations: FutachaPreferenceMutationCallbacks,
    val onOpenSaveDirectoryPicker: () -> Unit
)

internal data class FutachaScreenHistoryCallbackInputs(
    val navigationCallbacks: FutachaNavigationCallbacks,
    val historyMutations: FutachaHistoryMutationCallbacks,
    val onHistoryRefresh: suspend () -> Unit
)

internal data class FutachaScreenBindingsInputs(
    val history: List<ThreadHistoryEntry>,
    val currentBoards: () -> List<BoardSummary>,
    val currentNavigationState: () -> FutachaNavigationState,
    val setNavigationState: (FutachaNavigationState) -> Unit,
    val updateBoards: suspend ((List<BoardSummary>) -> List<BoardSummary>) -> Unit,
    val preferenceMutations: FutachaPreferenceMutationCallbacks,
    val historyMutations: FutachaHistoryMutationCallbacks,
    val preferencesStateInputs: FutachaScreenPreferencesStateInputs,
    val onOpenSaveDirectoryPicker: () -> Unit,
    val onHistoryRefresh: suspend () -> Unit
)

internal data class FutachaScreenBindingsBundle(
    val navigationCallbacks: FutachaNavigationCallbacks,
    val boardScreenCallbacks: FutachaBoardScreenCallbacks,
    val screenPreferencesState: ScreenPreferencesState,
    val screenPreferencesCallbacks: ScreenPreferencesCallbacks,
    val screenHistoryCallbacks: ScreenHistoryCallbacks,
    val screenContract: ScreenContract
)

internal fun buildFutachaScreenContractContext(
    history: List<ThreadHistoryEntry>,
    historyCallbacks: ScreenHistoryCallbacks,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks
): ScreenContract {
    return buildScreenContract(
        history = history,
        historyCallbacks = historyCallbacks,
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks
    )
}

internal fun buildFutachaScreenPreferencesState(
    inputs: FutachaScreenPreferencesStateInputs
): ScreenPreferencesState {
    return ScreenPreferencesState(
        appVersion = inputs.appVersion,
        isBackgroundRefreshEnabled = inputs.isBackgroundRefreshEnabled,
        isAdsEnabled = inputs.isAdsEnabled,
        isLightweightModeEnabled = inputs.isLightweightModeEnabled,
        manualSaveDirectory = inputs.manualSaveDirectory,
        manualSaveLocation = inputs.manualSaveLocation,
        resolvedManualSaveDirectory = inputs.resolvedManualSaveDirectory,
        attachmentPickerPreference = inputs.attachmentPickerPreference,
        saveDirectorySelection = inputs.saveDirectorySelection,
        preferredFileManagerPackage = inputs.preferredFileManagerPackage,
        preferredFileManagerLabel = inputs.preferredFileManagerLabel,
        threadMenuEntries = inputs.threadMenuEntries,
        catalogNavEntries = inputs.catalogNavEntries
    )
}

internal fun buildFutachaScreenPreferencesCallbacks(
    inputs: FutachaScreenPreferencesCallbackInputs
): ScreenPreferencesCallbacks {
    return ScreenPreferencesCallbacks(
        onBackgroundRefreshChanged = inputs.preferenceMutations.onBackgroundRefreshChanged,
        onAdsEnabledChanged = inputs.preferenceMutations.onAdsEnabledChanged,
        onLightweightModeChanged = inputs.preferenceMutations.onLightweightModeChanged,
        onManualSaveDirectoryChanged = inputs.preferenceMutations.onManualSaveDirectoryChanged,
        onAttachmentPickerPreferenceChanged = inputs.preferenceMutations.onAttachmentPickerPreferenceChanged,
        onSaveDirectorySelectionChanged = inputs.preferenceMutations.onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = inputs.onOpenSaveDirectoryPicker,
        onFileManagerSelected = inputs.preferenceMutations.onFileManagerSelected,
        onClearPreferredFileManager = inputs.preferenceMutations.onClearPreferredFileManager,
        onThreadMenuEntriesChanged = inputs.preferenceMutations.onThreadMenuEntriesChanged,
        onCatalogNavEntriesChanged = inputs.preferenceMutations.onCatalogNavEntriesChanged
    )
}

internal fun buildFutachaScreenHistoryCallbacks(
    inputs: FutachaScreenHistoryCallbackInputs
): ScreenHistoryCallbacks {
    return ScreenHistoryCallbacks(
        onHistoryEntrySelected = inputs.navigationCallbacks.onHistoryEntrySelected,
        onHistoryEntryDismissed = inputs.historyMutations.onDismissHistoryEntry,
        onHistoryEntryUpdated = inputs.historyMutations.onUpdateHistoryEntry,
        onHistoryRefresh = inputs.onHistoryRefresh,
        onHistoryCleared = inputs.historyMutations.onClearHistory
    )
}

internal fun buildFutachaScreenBindingsBundle(
    coroutineScope: CoroutineScope,
    inputs: FutachaScreenBindingsInputs
): FutachaScreenBindingsBundle {
    val navigationCallbacks = buildFutachaNavigationCallbacks(
        currentBoards = inputs.currentBoards,
        currentNavigationState = inputs.currentNavigationState,
        setNavigationState = inputs.setNavigationState
    )
    val boardScreenCallbacks = buildFutachaBoardScreenCallbacks(
        coroutineScope = coroutineScope,
        inputs = FutachaBoardScreenCallbackInputs(
            currentNavigationState = inputs.currentNavigationState,
            setNavigationState = inputs.setNavigationState,
            updateBoards = inputs.updateBoards
        )
    )
    val screenPreferencesState = buildFutachaScreenPreferencesState(inputs.preferencesStateInputs)
    val screenPreferencesCallbacks = buildFutachaScreenPreferencesCallbacks(
        FutachaScreenPreferencesCallbackInputs(
            preferenceMutations = inputs.preferenceMutations,
            onOpenSaveDirectoryPicker = inputs.onOpenSaveDirectoryPicker
        )
    )
    val screenHistoryCallbacks = buildFutachaScreenHistoryCallbacks(
        FutachaScreenHistoryCallbackInputs(
            navigationCallbacks = navigationCallbacks,
            historyMutations = inputs.historyMutations,
            onHistoryRefresh = inputs.onHistoryRefresh
        )
    )
    return FutachaScreenBindingsBundle(
        navigationCallbacks = navigationCallbacks,
        boardScreenCallbacks = boardScreenCallbacks,
        screenPreferencesState = screenPreferencesState,
        screenPreferencesCallbacks = screenPreferencesCallbacks,
        screenHistoryCallbacks = screenHistoryCallbacks,
        screenContract = buildFutachaScreenContractContext(
            history = inputs.history,
            historyCallbacks = screenHistoryCallbacks,
            preferencesState = screenPreferencesState,
            preferencesCallbacks = screenPreferencesCallbacks
        )
    )
}
