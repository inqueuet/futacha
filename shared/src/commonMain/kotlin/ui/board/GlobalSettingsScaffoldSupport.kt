package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.SnackbarHostState
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadGalleryTapAction
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal data class GlobalSettingsBehaviorSectionBindings(
    val text: GlobalSettingsBehaviorText,
    val isBackgroundRefreshEnabled: Boolean,
    val onBackgroundRefreshChanged: (Boolean) -> Unit,
    val isAdsEnabled: Boolean,
    val onAdsEnabledChanged: (Boolean) -> Unit,
    val isLightweightModeEnabled: Boolean,
    val onLightweightModeChanged: (Boolean) -> Unit,
    val threadGalleryTapAction: ThreadGalleryTapAction = ThreadGalleryTapAction.OpenMedia,
    val onThreadGalleryTapActionChanged: (ThreadGalleryTapAction) -> Unit = {}
)

internal data class GlobalSettingsCatalogMenuSectionBindings(
    val localCatalogNavEntries: List<CatalogNavEntryConfig>,
    val catalogMenuCallbacks: GlobalSettingsCatalogMenuCallbacks
)

internal data class GlobalSettingsThreadMenuSectionBindings(
    val localThreadMenuEntries: List<ThreadMenuEntryConfig>,
    val threadMenuCallbacks: GlobalSettingsThreadMenuCallbacks
)

internal data class GlobalSettingsSaveSectionBindings(
    val state: GlobalSettingsSaveSectionState,
    val callbacks: GlobalSettingsSaveSectionCallbacks
)

internal data class GlobalSettingsSaveSectionState(
    val text: GlobalSettingsSaveText,
    val preferredFileManagerState: PreferredFileManagerSummaryState,
    val availableSaveDirectorySelections: List<SaveDirectorySelection>,
    val effectiveSaveDirectorySelection: SaveDirectorySelection,
    val saveDestinationModeLabel: String,
    val resolvedManualPath: String,
    val saveDestinationHint: String,
    val defaultSaveWarningText: String?,
    val manualSaveInput: String,
    val saveDirectoryPickerState: SaveDirectoryPickerState
)

internal data class GlobalSettingsSaveSectionCallbacks(
    val onOpenFileManagerPicker: () -> Unit,
    val onClearPreferredFileManager: (() -> Unit)?,
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    val onManualSaveInputChanged: (String) -> Unit,
    val onResetManualSaveDirectory: () -> Unit,
    val onUpdateManualSaveDirectory: () -> Unit,
    val onOpenSaveDirectoryPicker: (() -> Unit)?,
    val onFallbackToManualInput: () -> Unit
)

internal data class GlobalSettingsStorageSectionBindings(
    val storageSummaryState: GlobalSettingsStorageSummaryState,
    val onRefreshStorageStats: () -> Unit
)

internal data class GlobalSettingsLinksSectionBindings(
    val settingsEntries: List<GlobalSettingsEntry>,
    val linkCallbacks: GlobalSettingsLinkCallbacks
)

internal data class GlobalSettingsScaffoldBindings(
    val appVersion: String,
    val behavior: GlobalSettingsBehaviorSectionBindings,
    val catalogMenu: GlobalSettingsCatalogMenuSectionBindings,
    val threadMenu: GlobalSettingsThreadMenuSectionBindings,
    val save: GlobalSettingsSaveSectionBindings,
    val cacheCallbacks: GlobalSettingsCacheCallbacks,
    val storage: GlobalSettingsStorageSectionBindings,
    val links: GlobalSettingsLinksSectionBindings,
    val snackbarHostState: SnackbarHostState,
    val onBack: () -> Unit
)

internal data class GlobalSettingsScaffoldBindingInputs(
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val derivedState: GlobalSettingsDerivedState,
    val localCatalogNavEntries: List<CatalogNavEntryConfig>,
    val catalogMenuCallbacks: GlobalSettingsCatalogMenuCallbacks,
    val localThreadMenuEntries: List<ThreadMenuEntryConfig>,
    val threadMenuCallbacks: GlobalSettingsThreadMenuCallbacks,
    val availableSaveDirectorySelections: List<SaveDirectorySelection>,
    val effectiveSaveDirectorySelection: SaveDirectorySelection,
    val manualSaveInput: String,
    val saveCallbacks: GlobalSettingsSaveCallbacks,
    val cacheCallbacks: GlobalSettingsCacheCallbacks,
    val linkCallbacks: GlobalSettingsLinkCallbacks,
    val snackbarHostState: SnackbarHostState,
    val onBack: () -> Unit
)

internal fun buildGlobalSettingsBehaviorSectionBindings(
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    derivedState: GlobalSettingsDerivedState
): GlobalSettingsBehaviorSectionBindings {
    return GlobalSettingsBehaviorSectionBindings(
        text = derivedState.behaviorText,
        isBackgroundRefreshEnabled = preferencesState.isBackgroundRefreshEnabled,
        onBackgroundRefreshChanged = preferencesCallbacks.onBackgroundRefreshChanged,
        isAdsEnabled = preferencesState.isAdsEnabled,
        onAdsEnabledChanged = preferencesCallbacks.onAdsEnabledChanged,
        isLightweightModeEnabled = preferencesState.isLightweightModeEnabled,
        onLightweightModeChanged = preferencesCallbacks.onLightweightModeChanged,
        threadGalleryTapAction = preferencesState.threadGalleryTapAction,
        onThreadGalleryTapActionChanged = preferencesCallbacks.onThreadGalleryTapActionChanged
    )
}

internal fun buildGlobalSettingsSaveSectionBindings(
    preferencesCallbacks: ScreenPreferencesCallbacks,
    derivedState: GlobalSettingsDerivedState,
    availableSaveDirectorySelections: List<SaveDirectorySelection>,
    effectiveSaveDirectorySelection: SaveDirectorySelection,
    manualSaveInput: String,
    saveCallbacks: GlobalSettingsSaveCallbacks
): GlobalSettingsSaveSectionBindings {
    return GlobalSettingsSaveSectionBindings(
        state = GlobalSettingsSaveSectionState(
            text = derivedState.saveText,
            preferredFileManagerState = derivedState.preferredFileManagerState,
            availableSaveDirectorySelections = availableSaveDirectorySelections,
            effectiveSaveDirectorySelection = effectiveSaveDirectorySelection,
            saveDestinationModeLabel = derivedState.saveDestinationModeLabel,
            resolvedManualPath = derivedState.resolvedManualPath,
            saveDestinationHint = derivedState.saveDestinationHint,
            defaultSaveWarningText = derivedState.defaultAndroidSaveWarningText,
            manualSaveInput = manualSaveInput,
            saveDirectoryPickerState = derivedState.saveDirectoryPickerState
        ),
        callbacks = GlobalSettingsSaveSectionCallbacks(
            onOpenFileManagerPicker = saveCallbacks.onOpenFileManagerPicker,
            onClearPreferredFileManager = preferencesCallbacks.onClearPreferredFileManager,
            onSaveDirectorySelectionChanged = preferencesCallbacks.onSaveDirectorySelectionChanged,
            onManualSaveInputChanged = saveCallbacks.onManualSaveInputChanged,
            onResetManualSaveDirectory = saveCallbacks.onResetManualSaveDirectory,
            onUpdateManualSaveDirectory = saveCallbacks.onUpdateManualSaveDirectory,
            onOpenSaveDirectoryPicker = preferencesCallbacks.onOpenSaveDirectoryPicker,
            onFallbackToManualInput = saveCallbacks.onFallbackToManualInput
        )
    )
}

internal fun buildGlobalSettingsScaffoldBindings(
    inputs: GlobalSettingsScaffoldBindingInputs
): GlobalSettingsScaffoldBindings {
    return GlobalSettingsScaffoldBindings(
        appVersion = inputs.preferencesState.appVersion,
        behavior = buildGlobalSettingsBehaviorSectionBindings(
            preferencesState = inputs.preferencesState,
            preferencesCallbacks = inputs.preferencesCallbacks,
            derivedState = inputs.derivedState
        ),
        catalogMenu = GlobalSettingsCatalogMenuSectionBindings(
            localCatalogNavEntries = inputs.localCatalogNavEntries,
            catalogMenuCallbacks = inputs.catalogMenuCallbacks
        ),
        threadMenu = GlobalSettingsThreadMenuSectionBindings(
            localThreadMenuEntries = inputs.localThreadMenuEntries,
            threadMenuCallbacks = inputs.threadMenuCallbacks
        ),
        save = buildGlobalSettingsSaveSectionBindings(
            preferencesCallbacks = inputs.preferencesCallbacks,
            derivedState = inputs.derivedState,
            availableSaveDirectorySelections = inputs.availableSaveDirectorySelections,
            effectiveSaveDirectorySelection = inputs.effectiveSaveDirectorySelection,
            manualSaveInput = inputs.manualSaveInput,
            saveCallbacks = inputs.saveCallbacks
        ),
        cacheCallbacks = inputs.cacheCallbacks,
        storage = GlobalSettingsStorageSectionBindings(
            storageSummaryState = inputs.derivedState.storageSummaryState,
            onRefreshStorageStats = inputs.cacheCallbacks.refreshStorageStats
        ),
        links = GlobalSettingsLinksSectionBindings(
            settingsEntries = inputs.derivedState.settingsEntries,
            linkCallbacks = inputs.linkCallbacks
        ),
        snackbarHostState = inputs.snackbarHostState,
        onBack = inputs.onBack
    )
}
