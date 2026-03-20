package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.rememberDirectoryPickerLauncher
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope

private const val FUTACHA_APP_BINDINGS_TAG = "FutachaApp"

internal data class FutachaBindingsRuntimeState(
    val screenBindings: FutachaScreenBindingsBundle
)

@Composable
internal fun rememberFutachaBindingsRuntimeState(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore,
    persistedBoards: List<BoardSummary>,
    persistedHistory: List<ThreadHistoryEntry>,
    observedRuntimeState: FutachaObservedRuntimeState,
    shouldUseLightweightMode: Boolean,
    historyRefresher: HistoryRefresher,
    effectiveAutoSavedThreadRepository: SavedThreadRepository?,
    navigationState: FutachaNavigationState,
    updateNavigationState: (FutachaNavigationState) -> Unit
): FutachaBindingsRuntimeState {
    val refreshHistoryEntries: suspend () -> Unit = {
        historyRefresher.refresh(
            boardsSnapshot = persistedBoards,
            historySnapshot = persistedHistory
        )
    }
    val preferenceMutations = buildFutachaPreferenceMutationCallbacks(
        coroutineScope = coroutineScope,
        inputs = FutachaPreferenceMutationInputs(
            setBackgroundRefreshEnabled = stateStore::setBackgroundRefreshEnabled,
            setAdsEnabled = stateStore::setAdsEnabled,
            setLightweightModeEnabled = stateStore::setLightweightModeEnabled,
            setManualSaveDirectory = stateStore::setManualSaveDirectory,
            setAttachmentPickerPreference = stateStore::setAttachmentPickerPreference,
            setSaveDirectorySelection = stateStore::setSaveDirectorySelection,
            setManualSaveLocation = stateStore::setManualSaveLocation,
            setPreferredFileManager = stateStore::setPreferredFileManager,
            setThreadMenuEntries = stateStore::setThreadMenuEntries,
            setCatalogNavEntries = stateStore::setCatalogNavEntries
        )
    )
    val directoryPickerLauncher = rememberDirectoryPickerLauncher(
        onDirectorySelected = { pickedLocation ->
            preferenceMutations.onManualSaveLocationChanged(pickedLocation)
            preferenceMutations.onSaveDirectorySelectionChanged(SaveDirectorySelection.PICKER)
        },
        preferredFileManagerPackage = observedRuntimeState.preferredFileManager?.packageName
    )
    val historyMutations = buildFutachaHistoryMutationCallbacks(
        coroutineScope = coroutineScope,
        dismissHistoryEntry = { entry ->
            dismissHistoryEntry(
                stateStore = stateStore,
                autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                entry = entry,
                onAutoSavedThreadDeleteFailure = {
                    Logger.e(FUTACHA_APP_BINDINGS_TAG, "Failed to delete auto-saved thread ${entry.threadId}", it)
                }
            )
        },
        updateHistoryEntry = stateStore::upsertHistoryEntry,
        clearHistory = {
            clearHistory(
                stateStore = stateStore,
                autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                onSkippedThreadsCleared = historyRefresher::clearSkippedThreads,
                onAutoSavedThreadDeleteFailure = {
                    Logger.e(FUTACHA_APP_BINDINGS_TAG, "Failed to clear auto saved threads", it)
                }
            )
        }
    )
    val screenBindings = buildFutachaScreenBindingsBundle(
        coroutineScope = coroutineScope,
        inputs = FutachaScreenBindingsInputs(
            history = persistedHistory,
            currentBoards = { persistedBoards },
            currentNavigationState = { navigationState },
            setNavigationState = updateNavigationState,
            updateBoards = stateStore::updateBoards,
            preferenceMutations = preferenceMutations,
            historyMutations = historyMutations,
            preferencesStateInputs = FutachaScreenPreferencesStateInputs(
                appVersion = observedRuntimeState.appVersion,
                isBackgroundRefreshEnabled = observedRuntimeState.isBackgroundRefreshEnabled,
                isAdsEnabled = observedRuntimeState.isAdsEnabled,
                isLightweightModeEnabled = shouldUseLightweightMode,
                manualSaveDirectory = observedRuntimeState.manualSaveDirectory,
                manualSaveLocation = observedRuntimeState.manualSaveLocation,
                resolvedManualSaveDirectory = observedRuntimeState.resolvedManualSaveDirectory,
                attachmentPickerPreference = observedRuntimeState.attachmentPickerPreference,
                saveDirectorySelection = observedRuntimeState.saveDirectorySelection,
                preferredFileManagerPackage = observedRuntimeState.preferredFileManager?.packageName,
                preferredFileManagerLabel = observedRuntimeState.preferredFileManager?.label,
                threadMenuEntries = observedRuntimeState.threadMenuEntries,
                catalogNavEntries = observedRuntimeState.catalogNavEntries
            ),
            onOpenSaveDirectoryPicker = directoryPickerLauncher,
            onHistoryRefresh = refreshHistoryEntries
        )
    )
    return remember(screenBindings) {
        FutachaBindingsRuntimeState(
            screenBindings = screenBindings
        )
    }
}
