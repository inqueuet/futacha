package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.ui.board.BoardManagementMenuAction
import com.valoser.futacha.shared.ui.board.ScreenContract
import com.valoser.futacha.shared.ui.board.ScreenHistoryCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.createCustomBoardSummary
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal data class FutachaPreferenceMutationInputs(
    val setBackgroundRefreshEnabled: suspend (Boolean) -> Unit,
    val setAdsEnabled: suspend (Boolean) -> Unit = {},
    val setLightweightModeEnabled: suspend (Boolean) -> Unit,
    val setManualSaveDirectory: suspend (String) -> Unit,
    val setAttachmentPickerPreference: suspend (AttachmentPickerPreference) -> Unit,
    val setSaveDirectorySelection: suspend (SaveDirectorySelection) -> Unit,
    val setManualSaveLocation: suspend (SaveLocation) -> Unit,
    val setPreferredFileManager: suspend (String?, String?) -> Unit,
    val setThreadMenuEntries: suspend (List<ThreadMenuEntryConfig>) -> Unit,
    val setCatalogNavEntries: suspend (List<CatalogNavEntryConfig>) -> Unit
)

internal data class FutachaPreferenceMutationCallbacks(
    val onBackgroundRefreshChanged: (Boolean) -> Unit,
    val onAdsEnabledChanged: (Boolean) -> Unit,
    val onLightweightModeChanged: (Boolean) -> Unit,
    val onManualSaveDirectoryChanged: (String) -> Unit,
    val onAttachmentPickerPreferenceChanged: (AttachmentPickerPreference) -> Unit,
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    val onManualSaveLocationChanged: (SaveLocation) -> Unit,
    val onFileManagerSelected: (packageName: String, label: String) -> Unit,
    val onClearPreferredFileManager: () -> Unit,
    val onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit,
    val onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit
)

internal data class FutachaBoardScreenCallbackInputs(
    val currentNavigationState: () -> FutachaNavigationState,
    val setNavigationState: (FutachaNavigationState) -> Unit,
    val updateBoards: suspend ((List<BoardSummary>) -> List<BoardSummary>) -> Unit
)

internal data class FutachaBoardScreenCallbacks(
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit
)

internal data class FutachaScreenPreferencesStateInputs(
    val appVersion: String,
    val isBackgroundRefreshEnabled: Boolean,
    val isAdsEnabled: Boolean = false,
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

private fun launchFutachaCallbackMutation(
    coroutineScope: CoroutineScope,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend () -> Unit
) {
    coroutineScope.launch(start = start) { block() }
}

internal fun buildFutachaPreferenceMutationCallbacks(
    coroutineScope: CoroutineScope,
    inputs: FutachaPreferenceMutationInputs
): FutachaPreferenceMutationCallbacks {
    return FutachaPreferenceMutationCallbacks(
        onBackgroundRefreshChanged = { enabled ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setBackgroundRefreshEnabled(enabled)
            }
        },
        onAdsEnabledChanged = { enabled ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setAdsEnabled(enabled)
            }
        },
        onLightweightModeChanged = { enabled ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setLightweightModeEnabled(enabled)
            }
        },
        onManualSaveDirectoryChanged = { directory ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setManualSaveDirectory(directory)
            }
        },
        onAttachmentPickerPreferenceChanged = { preference ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setAttachmentPickerPreference(preference)
            }
        },
        onSaveDirectorySelectionChanged = { selection ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setSaveDirectorySelection(selection)
            }
        },
        onManualSaveLocationChanged = { location ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setManualSaveLocation(location)
            }
        },
        onFileManagerSelected = { packageName, label ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setPreferredFileManager(packageName, label)
            }
        },
        onClearPreferredFileManager = {
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setPreferredFileManager(null, null)
            }
        },
        onThreadMenuEntriesChanged = { entries ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setThreadMenuEntries(entries)
            }
        },
        onCatalogNavEntriesChanged = { entries ->
            launchFutachaCallbackMutation(coroutineScope) {
                inputs.setCatalogNavEntries(entries)
            }
        }
    )
}

internal fun buildFutachaBoardScreenCallbacks(
    coroutineScope: CoroutineScope,
    inputs: FutachaBoardScreenCallbackInputs
): FutachaBoardScreenCallbacks {
    return FutachaBoardScreenCallbacks(
        onBoardSelected = { board ->
            inputs.setNavigationState(selectFutachaBoard(inputs.currentNavigationState(), board.id))
        },
        onAddBoard = { name, url ->
            launchFutachaCallbackMutation(coroutineScope, start = CoroutineStart.UNDISPATCHED) {
                val normalizedUrl = normalizeBoardUrl(url)
                inputs.updateBoards { boards ->
                    if (boards.any { it.url.equals(normalizedUrl, ignoreCase = true) }) {
                        boards
                    } else {
                        boards + createCustomBoardSummary(
                            name = name,
                            url = normalizedUrl,
                            existingBoards = boards
                        )
                    }
                }
            }
        },
        onMenuAction = { action ->
            if (action == BoardManagementMenuAction.SAVED_THREADS) {
                inputs.setNavigationState(
                    inputs.currentNavigationState().copy(isSavedThreadsVisible = true)
                )
            }
        },
        onBoardDeleted = { board ->
            launchFutachaCallbackMutation(coroutineScope, start = CoroutineStart.UNDISPATCHED) {
                inputs.updateBoards { boards ->
                    boards.filter { it.id != board.id }
                }
            }
        },
        onBoardsReordered = { reorderedBoards ->
            launchFutachaCallbackMutation(coroutineScope, start = CoroutineStart.UNDISPATCHED) {
                inputs.updateBoards {
                    reorderedBoards
                }
            }
        }
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
