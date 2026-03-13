package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.SnackbarHostState
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal data class GlobalSettingsBehaviorSectionBindings(
    val isBackgroundRefreshEnabled: Boolean,
    val onBackgroundRefreshChanged: (Boolean) -> Unit,
    val isLightweightModeEnabled: Boolean,
    val onLightweightModeChanged: (Boolean) -> Unit
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
    val preferredFileManagerState: PreferredFileManagerSummaryState,
    val onOpenFileManagerPicker: () -> Unit,
    val onClearPreferredFileManager: (() -> Unit)?,
    val availableSaveDirectorySelections: List<SaveDirectorySelection>,
    val effectiveSaveDirectorySelection: SaveDirectorySelection,
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    val saveDestinationModeLabel: String,
    val resolvedManualPath: String,
    val saveDestinationHint: String,
    val manualSaveInput: String,
    val onManualSaveInputChanged: (String) -> Unit,
    val onResetManualSaveDirectory: () -> Unit,
    val onUpdateManualSaveDirectory: () -> Unit,
    val saveDirectoryPickerState: SaveDirectoryPickerState,
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

internal fun buildGlobalSettingsScaffoldBindings(
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    derivedState: GlobalSettingsDerivedState,
    interactionBindings: GlobalSettingsInteractionBindingsBundle,
    localCatalogNavEntries: List<CatalogNavEntryConfig>,
    localThreadMenuEntries: List<ThreadMenuEntryConfig>,
    manualSaveInput: String,
    availableSaveDirectorySelections: List<SaveDirectorySelection>,
    effectiveSaveDirectorySelection: SaveDirectorySelection,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
): GlobalSettingsScaffoldBindings {
    return GlobalSettingsScaffoldBindings(
        appVersion = preferencesState.appVersion,
        behavior = GlobalSettingsBehaviorSectionBindings(
            isBackgroundRefreshEnabled = preferencesState.isBackgroundRefreshEnabled,
            onBackgroundRefreshChanged = preferencesCallbacks.onBackgroundRefreshChanged,
            isLightweightModeEnabled = preferencesState.isLightweightModeEnabled,
            onLightweightModeChanged = preferencesCallbacks.onLightweightModeChanged
        ),
        catalogMenu = GlobalSettingsCatalogMenuSectionBindings(
            localCatalogNavEntries = localCatalogNavEntries,
            catalogMenuCallbacks = interactionBindings.catalogMenuCallbacks
        ),
        threadMenu = GlobalSettingsThreadMenuSectionBindings(
            localThreadMenuEntries = localThreadMenuEntries,
            threadMenuCallbacks = interactionBindings.threadMenuCallbacks
        ),
        save = GlobalSettingsSaveSectionBindings(
            preferredFileManagerState = derivedState.preferredFileManagerState,
            onOpenFileManagerPicker = interactionBindings.saveCallbacks.onOpenFileManagerPicker,
            onClearPreferredFileManager = preferencesCallbacks.onClearPreferredFileManager,
            availableSaveDirectorySelections = availableSaveDirectorySelections,
            effectiveSaveDirectorySelection = effectiveSaveDirectorySelection,
            onSaveDirectorySelectionChanged = preferencesCallbacks.onSaveDirectorySelectionChanged,
            saveDestinationModeLabel = derivedState.saveDestinationModeLabel,
            resolvedManualPath = derivedState.resolvedManualPath,
            saveDestinationHint = derivedState.saveDestinationHint,
            manualSaveInput = manualSaveInput,
            onManualSaveInputChanged = interactionBindings.saveCallbacks.onManualSaveInputChanged,
            onResetManualSaveDirectory = interactionBindings.saveCallbacks.onResetManualSaveDirectory,
            onUpdateManualSaveDirectory = interactionBindings.saveCallbacks.onUpdateManualSaveDirectory,
            saveDirectoryPickerState = derivedState.saveDirectoryPickerState,
            onOpenSaveDirectoryPicker = preferencesCallbacks.onOpenSaveDirectoryPicker,
            onFallbackToManualInput = interactionBindings.saveCallbacks.onFallbackToManualInput
        ),
        cacheCallbacks = interactionBindings.cacheCallbacks,
        storage = GlobalSettingsStorageSectionBindings(
            storageSummaryState = derivedState.storageSummaryState,
            onRefreshStorageStats = interactionBindings.cacheCallbacks.refreshStorageStats
        ),
        links = GlobalSettingsLinksSectionBindings(
            settingsEntries = derivedState.settingsEntries,
            linkCallbacks = interactionBindings.linkCallbacks
        ),
        snackbarHostState = snackbarHostState,
        onBack = onBack
    )
}
