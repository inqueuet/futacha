package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.ThreadGalleryTapAction
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.model.ThreadPostImageSize
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
import com.valoser.futacha.shared.ai.AiAvailability
import com.valoser.futacha.shared.ai.createOnDeviceAiService
import com.valoser.futacha.shared.ui.board.ALPHA_AI_COMMAND_ENABLED
import com.valoser.futacha.shared.ui.board.ALPHA_AI_POST_FILTER_ENABLED
import kotlinx.coroutines.delay

private const val FUTACHA_APP_STATE_TAG = "FutachaApp"
private const val AI_AVAILABILITY_REFRESH_INTERVAL_MILLIS = 60_000L

internal fun shouldContinuouslyRefreshFutachaAiAvailability(
    isThreadSummaryModeEnabled: Boolean,
    isAiPostFilterEnabled: Boolean,
    isThreadScreenVisible: Boolean
): Boolean {
    return isThreadScreenVisible && (isThreadSummaryModeEnabled || isAiPostFilterEnabled)
}

internal data class FutachaObservedRuntimeState(
    val persistedBoards: List<BoardSummary>,
    val persistedHistory: List<ThreadHistoryEntry>,
    val threadMenuEntries: List<com.valoser.futacha.shared.model.ThreadMenuEntryConfig>,
    val catalogNavEntries: List<com.valoser.futacha.shared.model.CatalogNavEntryConfig>,
    val isBackgroundRefreshEnabled: Boolean,
    val isAdsEnabled: Boolean,
    val isThreadSummaryModeEnabled: Boolean,
    val isAiPostFilterEnabled: Boolean,
    val isAiCommandEnabled: Boolean,
    val appLockPasswordHash: String?,
    val aiAvailability: AiAvailability,
    val manualSaveDirectory: String,
    val manualSaveLocation: SaveLocation,
    val activeSavedThreadsRepository: SavedThreadRepository?,
    val attachmentPickerPreference: AttachmentPickerPreference,
    val saveDirectorySelection: SaveDirectorySelection,
    val threadGalleryTapAction: ThreadGalleryTapAction = ThreadGalleryTapAction.OpenMedia,
    val themeMode: ThemeMode = ThemeMode.System,
    val themePalette: ThemePalette = ThemePalette.FutabaClassic,
    val appIconVariant: AppIconVariant = AppIconVariant.Current,
    val threadDisplayMode: ThreadDisplayMode = ThreadDisplayMode.Flat,
    val threadBodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard,
    val threadPostImageSize: ThreadPostImageSize = ThreadPostImageSize.Small,
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
    fileSystem: FileSystem?,
    platformContext: Any?,
    isThreadScreenVisible: Boolean
): FutachaObservedRuntimeState {
    val persistedBoards by stateStore.boards.collectAsState(initial = boardList)
    val persistedHistory by stateStore.history.collectAsState(initial = history)
    val threadMenuEntries by stateStore.threadMenuEntries.collectAsState(initial = defaultThreadMenuEntries())
    val catalogNavEntries by stateStore.catalogNavEntries.collectAsState(initial = defaultCatalogNavEntries())
    val isBackgroundRefreshEnabled by stateStore.isBackgroundRefreshEnabled.collectAsState(initial = false)
    val isAdsEnabled by stateStore.isAdsEnabled.collectAsState(initial = true)
    val isThreadSummaryModeEnabled by stateStore.isThreadSummaryModeEnabled.collectAsState(initial = false)
    val isAiPostFilterEnabled by stateStore.isAiPostFilterEnabled.collectAsState(initial = false)
    val isAiCommandEnabled by stateStore.isAiCommandEnabled.collectAsState(initial = false)
    val appLockPasswordHash by stateStore.appLockPasswordHash.collectAsState(initial = null)
    val aiService = remember(platformContext) { createOnDeviceAiService(platformContext) }
    val shouldContinuouslyRefreshAiAvailability = shouldContinuouslyRefreshFutachaAiAvailability(
        isThreadSummaryModeEnabled = isThreadSummaryModeEnabled,
        isAiPostFilterEnabled = isAiPostFilterEnabled,
        isThreadScreenVisible = isThreadScreenVisible
    )
    val aiAvailability by produceState(
        initialValue = AiAvailability(
            isAvailable = false,
            unavailableReason = "端末AIを確認中です。"
        ),
        key1 = aiService,
        key2 = shouldContinuouslyRefreshAiAvailability
    ) {
        suspend fun refreshAvailability() {
            runCatching {
                value = aiService.getAvailability()
            }.getOrElse { error ->
                value = AiAvailability(
                    isAvailable = false,
                    unavailableReason = error.message ?: "端末AIの確認に失敗しました。"
                )
            }
        }

        refreshAvailability()
        while (shouldContinuouslyRefreshAiAvailability) {
            delay(AI_AVAILABILITY_REFRESH_INTERVAL_MILLIS)
            refreshAvailability()
        }
    }
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
    val threadGalleryTapAction by stateStore.threadGalleryTapAction.collectAsState(
        initial = ThreadGalleryTapAction.OpenMedia
    )
    val themeMode by stateStore.themeMode.collectAsState(
        initial = ThemeMode.System
    )
    val themePalette by stateStore.themePalette.collectAsState(
        initial = ThemePalette.FutabaClassic
    )
    val appIconVariant by stateStore.appIconVariant.collectAsState(
        initial = AppIconVariant.Current
    )
    val threadDisplayMode by stateStore.threadDisplayMode.collectAsState(
        initial = ThreadDisplayMode.Flat
    )
    val threadBodyTextSize by stateStore.threadBodyTextSize.collectAsState(
        initial = ThreadBodyTextSize.Standard
    )
    val threadPostImageSize by stateStore.threadPostImageSize.collectAsState(
        initial = ThreadPostImageSize.Small
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
        isThreadSummaryModeEnabled,
        isAiPostFilterEnabled,
        isAiCommandEnabled,
        appLockPasswordHash,
        aiAvailability,
        manualSaveDirectory,
        manualSaveLocation,
        activeSavedThreadsRepository,
        attachmentPickerPreference,
        saveDirectorySelection,
        threadGalleryTapAction,
        themeMode,
        themePalette,
        appIconVariant,
        threadDisplayMode,
        threadBodyTextSize,
        threadPostImageSize,
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
            isThreadSummaryModeEnabled = isThreadSummaryModeEnabled,
            isAiPostFilterEnabled = isAiPostFilterEnabled && ALPHA_AI_POST_FILTER_ENABLED,
            isAiCommandEnabled = isAiCommandEnabled && ALPHA_AI_COMMAND_ENABLED,
            appLockPasswordHash = appLockPasswordHash,
            aiAvailability = aiAvailability,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation,
            activeSavedThreadsRepository = activeSavedThreadsRepository,
            attachmentPickerPreference = attachmentPickerPreference,
            saveDirectorySelection = saveDirectorySelection,
            threadGalleryTapAction = threadGalleryTapAction,
            themeMode = themeMode,
            themePalette = themePalette,
            appIconVariant = appIconVariant,
            threadDisplayMode = threadDisplayMode,
            threadBodyTextSize = threadBodyTextSize,
            threadPostImageSize = threadPostImageSize,
            preferredFileManager = preferredFileManager,
            appVersion = appVersion,
            resolvedManualSaveDirectory = resolvedManualSaveDirectory
        )
    }
}
