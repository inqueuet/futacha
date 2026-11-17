package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.SnackbarHostState
import com.valoser.futacha.shared.model.DEFAULT_CATALOG_FETCH_ROWS
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.ThreadGalleryTapAction
import com.valoser.futacha.shared.model.ThreadGalleryThumbnailMode
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadPostImageSize
import com.valoser.futacha.shared.ai.AiAvailability
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal data class GlobalSettingsBehaviorSectionBindings(
    val text: GlobalSettingsBehaviorText,
    val isUpdateCheckEnabled: Boolean = true,
    val onUpdateCheckChanged: (Boolean) -> Unit = {},
    val isBackgroundRefreshEnabled: Boolean,
    val onBackgroundRefreshChanged: (Boolean) -> Unit,
    val isWatchAlertEnabled: Boolean = false,
    val onWatchAlertChanged: (Boolean) -> Unit = {},
    val isLightweightModeEnabled: Boolean,
    val onLightweightModeChanged: (Boolean) -> Unit,
    val isThreadSummaryModeEnabled: Boolean = false,
    val onThreadSummaryModeChanged: (Boolean) -> Unit = {},
    val isAiPostFilterEnabled: Boolean = false,
    val onAiPostFilterChanged: (Boolean) -> Unit = {},
    val isAiCommandEnabled: Boolean = false,
    val onAiCommandChanged: (Boolean) -> Unit = {},
    val isTelemetryCollectionEnabled: Boolean = true,
    val onTelemetryCollectionChanged: (Boolean) -> Unit = {},
    val isAppLockEnabled: Boolean = false,
    val onAppLockPasswordChanged: (String) -> Unit = {},
    val onAppLockCleared: () -> Unit = {},
    val aiAvailability: AiAvailability = AiAvailability(
        isAvailable = false,
        unavailableReason = "端末AIを確認中です。"
    ),
    val threadGalleryTapAction: ThreadGalleryTapAction = ThreadGalleryTapAction.OpenMedia,
    val onThreadGalleryTapActionChanged: (ThreadGalleryTapAction) -> Unit = {},
    val threadGalleryThumbnailMode: ThreadGalleryThumbnailMode = ThreadGalleryThumbnailMode.CropSquare,
    val onThreadGalleryThumbnailModeChanged: (ThreadGalleryThumbnailMode) -> Unit = {},
    val themeMode: ThemeMode = ThemeMode.System,
    val onThemeModeChanged: (ThemeMode) -> Unit = {},
    val themePalette: ThemePalette = ThemePalette.FutabaClassic,
    val onThemePaletteChanged: (ThemePalette) -> Unit = {},
    val appIconVariant: AppIconVariant = AppIconVariant.Current,
    val onAppIconVariantChanged: (AppIconVariant) -> Unit = {},
    val threadDisplayMode: ThreadDisplayMode = ThreadDisplayMode.Flat,
    val onThreadDisplayModeChanged: (ThreadDisplayMode) -> Unit = {},
    val threadBodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard,
    val onThreadBodyTextSizeChanged: (ThreadBodyTextSize) -> Unit = {},
    val threadPostImageSize: ThreadPostImageSize = ThreadPostImageSize.Small,
    val onThreadPostImageSizeChanged: (ThreadPostImageSize) -> Unit = {},
    val isCompactThreadHeaderEnabled: Boolean = false,
    val onCompactThreadHeaderChanged: (Boolean) -> Unit = {},
    val catalogFetchRows: Int = DEFAULT_CATALOG_FETCH_ROWS,
    val onCatalogFetchRowsChanged: (Int) -> Unit = {}
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
    val isAndroidPlatform: Boolean,
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
    val isAndroidPlatform: Boolean,
    val onBack: () -> Unit
)

internal fun buildGlobalSettingsBehaviorSectionBindings(
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    derivedState: GlobalSettingsDerivedState
): GlobalSettingsBehaviorSectionBindings {
    return GlobalSettingsBehaviorSectionBindings(
        text = derivedState.behaviorText,
        isUpdateCheckEnabled = preferencesState.isUpdateCheckEnabled,
        onUpdateCheckChanged = preferencesCallbacks.onUpdateCheckChanged,
        isBackgroundRefreshEnabled = preferencesState.isBackgroundRefreshEnabled,
        onBackgroundRefreshChanged = preferencesCallbacks.onBackgroundRefreshChanged,
        isWatchAlertEnabled = preferencesState.isWatchAlertEnabled,
        onWatchAlertChanged = preferencesCallbacks.onWatchAlertChanged,
        isLightweightModeEnabled = preferencesState.isLightweightModeEnabled,
        onLightweightModeChanged = preferencesCallbacks.onLightweightModeChanged,
        isThreadSummaryModeEnabled = preferencesState.isThreadSummaryModeEnabled,
        onThreadSummaryModeChanged = preferencesCallbacks.onThreadSummaryModeChanged,
        isAiPostFilterEnabled = preferencesState.isAiPostFilterEnabled,
        onAiPostFilterChanged = preferencesCallbacks.onAiPostFilterChanged,
        isAiCommandEnabled = preferencesState.isAiCommandEnabled,
        onAiCommandChanged = preferencesCallbacks.onAiCommandChanged,
        isTelemetryCollectionEnabled = preferencesState.isTelemetryCollectionEnabled,
        onTelemetryCollectionChanged = preferencesCallbacks.onTelemetryCollectionChanged,
        isAppLockEnabled = preferencesState.isAppLockEnabled,
        onAppLockPasswordChanged = preferencesCallbacks.onAppLockPasswordChanged,
        onAppLockCleared = preferencesCallbacks.onAppLockCleared,
        aiAvailability = preferencesState.aiAvailability,
        threadGalleryTapAction = preferencesState.threadGalleryTapAction,
        onThreadGalleryTapActionChanged = preferencesCallbacks.onThreadGalleryTapActionChanged,
        threadGalleryThumbnailMode = preferencesState.threadGalleryThumbnailMode,
        onThreadGalleryThumbnailModeChanged = preferencesCallbacks.onThreadGalleryThumbnailModeChanged,
        themeMode = preferencesState.themeMode,
        onThemeModeChanged = preferencesCallbacks.onThemeModeChanged,
        themePalette = preferencesState.themePalette,
        onThemePaletteChanged = preferencesCallbacks.onThemePaletteChanged,
        appIconVariant = preferencesState.appIconVariant,
        onAppIconVariantChanged = preferencesCallbacks.onAppIconVariantChanged,
        threadDisplayMode = preferencesState.threadDisplayMode,
        onThreadDisplayModeChanged = preferencesCallbacks.onThreadDisplayModeChanged,
        threadBodyTextSize = preferencesState.threadBodyTextSize,
        onThreadBodyTextSizeChanged = preferencesCallbacks.onThreadBodyTextSizeChanged,
        threadPostImageSize = preferencesState.threadPostImageSize,
        onThreadPostImageSizeChanged = preferencesCallbacks.onThreadPostImageSizeChanged,
        isCompactThreadHeaderEnabled = preferencesState.isCompactThreadHeaderEnabled,
        onCompactThreadHeaderChanged = preferencesCallbacks.onCompactThreadHeaderChanged,
        catalogFetchRows = preferencesState.catalogFetchRows,
        onCatalogFetchRowsChanged = preferencesCallbacks.onCatalogFetchRowsChanged
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
        isAndroidPlatform = inputs.isAndroidPlatform,
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
