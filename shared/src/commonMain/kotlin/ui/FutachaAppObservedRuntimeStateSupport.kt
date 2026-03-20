package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.PreferredFileManager
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.version.VersionChecker
import androidx.compose.runtime.remember as composeRemember
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT

private const val FUTACHA_APP_STATE_TAG = "FutachaApp"

internal data class FutachaObservedRuntimeState(
    val persistedBoards: List<BoardSummary>,
    val persistedHistory: List<ThreadHistoryEntry>,
    val threadMenuEntries: List<com.valoser.futacha.shared.model.ThreadMenuEntryConfig>,
    val catalogNavEntries: List<com.valoser.futacha.shared.model.CatalogNavEntryConfig>,
    val isBackgroundRefreshEnabled: Boolean,
    val isAdsEnabled: Boolean,
    val manualSaveDirectory: String,
    val manualSaveLocation: SaveLocation,
    val activeSavedThreadsRepository: SavedThreadRepository?,
    val attachmentPickerPreference: AttachmentPickerPreference,
    val saveDirectorySelection: SaveDirectorySelection,
    val preferredFileManager: PreferredFileManager?,
    val appVersion: String,
    val resolvedManualSaveDirectory: String?
)

@Composable
internal fun rememberFutachaObservedRuntimeState(
    stateStore: AppStateStore,
    boardList: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    versionChecker: VersionChecker?,
    fileSystem: FileSystem?
): FutachaObservedRuntimeState {
    val persistedBoards by stateStore.boards.collectAsState(initial = boardList)
    val persistedHistory by stateStore.history.collectAsState(initial = history)
    val threadMenuEntries by stateStore.threadMenuEntries.collectAsState(initial = defaultThreadMenuEntries())
    val catalogNavEntries by stateStore.catalogNavEntries.collectAsState(initial = defaultCatalogNavEntries())
    val isBackgroundRefreshEnabled by stateStore.isBackgroundRefreshEnabled.collectAsState(initial = false)
    val isAdsEnabled by stateStore.isAdsEnabled.collectAsState(initial = true)
    val manualSaveDirectory by stateStore.manualSaveDirectory.collectAsState(initial = DEFAULT_MANUAL_SAVE_ROOT)
    val manualSaveLocation = composeRemember(manualSaveDirectory) {
        SaveLocation.fromString(manualSaveDirectory)
    }
    LaunchedEffect(manualSaveLocation, fileSystem) {
        if (shouldResetInaccessibleManualSaveBookmark(fileSystem, manualSaveLocation)) {
            Logger.w(FUTACHA_APP_STATE_TAG, "Manual save bookmark is not accessible. Falling back to default path.")
            stateStore.setManualSaveDirectory(DEFAULT_MANUAL_SAVE_ROOT)
            stateStore.setSaveDirectorySelection(SaveDirectorySelection.MANUAL_INPUT)
        }
    }
    val savedThreadsRepositories = remember(fileSystem, manualSaveDirectory, manualSaveLocation) {
        buildFutachaSavedThreadsRepositories(
            FutachaSavedThreadsRepositoryInputs(
                fileSystem = fileSystem,
                manualSaveDirectory = manualSaveDirectory,
                manualSaveLocation = manualSaveLocation
            )
        )
    }
    val activeSavedThreadsRepository by produceState<SavedThreadRepository?>(
        initialValue = savedThreadsRepositories.currentRepository ?: savedThreadsRepositories.legacyRepository,
        key1 = savedThreadsRepositories.currentRepository,
        key2 = savedThreadsRepositories.legacyRepository
    ) {
        value = resolveActiveSavedThreadsRepository(
            FutachaActiveSavedThreadsRepositoryInputs(
                currentRepository = savedThreadsRepositories.currentRepository,
                legacyRepository = savedThreadsRepositories.legacyRepository
            )
        )
    }
    val attachmentPickerPreference by stateStore.attachmentPickerPreference.collectAsState(
        initial = AttachmentPickerPreference.MEDIA
    )
    val saveDirectorySelection by stateStore.saveDirectorySelection.collectAsState(
        initial = SaveDirectorySelection.MANUAL_INPUT
    )
    val preferredFileManagerFlow = remember(stateStore) { stateStore.getPreferredFileManager() }
    val preferredFileManager by preferredFileManagerFlow.collectAsState(initial = null)
    val appVersion = remember(versionChecker) {
        versionChecker?.getCurrentVersion() ?: "1.0"
    }
    val resolvedManualSaveDirectory = remember(fileSystem, manualSaveDirectory, manualSaveLocation) {
        resolveFutachaManualSaveDirectoryDisplay(
            fileSystem = fileSystem,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation
        )
    }
    return remember(
        persistedBoards,
        persistedHistory,
        threadMenuEntries,
        catalogNavEntries,
        isBackgroundRefreshEnabled,
        isAdsEnabled,
        manualSaveDirectory,
        manualSaveLocation,
        activeSavedThreadsRepository,
        attachmentPickerPreference,
        saveDirectorySelection,
        preferredFileManager,
        appVersion,
        resolvedManualSaveDirectory
    ) {
        FutachaObservedRuntimeState(
            persistedBoards = persistedBoards,
            persistedHistory = persistedHistory,
            threadMenuEntries = threadMenuEntries,
            catalogNavEntries = catalogNavEntries,
            isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
            isAdsEnabled = isAdsEnabled,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation,
            activeSavedThreadsRepository = activeSavedThreadsRepository,
            attachmentPickerPreference = attachmentPickerPreference,
            saveDirectorySelection = saveDirectorySelection,
            preferredFileManager = preferredFileManager,
            appVersion = appVersion,
            resolvedManualSaveDirectory = resolvedManualSaveDirectory
        )
    }
}
