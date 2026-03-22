package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal data class GlobalSettingsDerivedState(
    val resolvedManualPath: String,
    val saveDestinationModeLabel: String,
    val saveDestinationHint: String,
    val defaultAndroidSaveWarningText: String?,
    val settingsEntries: List<GlobalSettingsEntry>,
    val preferredFileManagerState: PreferredFileManagerSummaryState,
    val saveDirectoryPickerState: SaveDirectoryPickerState,
    val storageSummaryState: GlobalSettingsStorageSummaryState
)

@Composable
internal fun rememberGlobalSettingsDerivedState(
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    resolvedManualSaveDirectory: String?,
    saveDirectorySelection: SaveDirectorySelection,
    isAndroidPlatform: Boolean,
    hasCookieManager: Boolean,
    preferredFileManagerLabel: String?,
    hasPickerLauncher: Boolean,
    historyCount: Int,
    autoSavedCount: Int?,
    autoSavedSize: Long?
): GlobalSettingsDerivedState {
    val resolvedManualPath = remember(manualSaveDirectory, resolvedManualSaveDirectory) {
        resolvedManualSaveDirectory ?: resolveFallbackManualSavePathValue(manualSaveDirectory)
    }
    val saveDestinationModeLabel = remember(saveDirectorySelection, isAndroidPlatform) {
        buildSaveDestinationModeLabelValue(saveDirectorySelection, isAndroidPlatform)
    }
    val saveDestinationHint = remember(saveDirectorySelection, isAndroidPlatform) {
        buildSaveDestinationHintValue(saveDirectorySelection, isAndroidPlatform)
    }
    val defaultAndroidSaveWarningText = remember(
        manualSaveDirectory,
        manualSaveLocation,
        saveDirectorySelection,
        isAndroidPlatform
    ) {
        resolveDefaultAndroidSaveWarningText(
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation,
            saveDirectorySelection = saveDirectorySelection,
            isAndroidPlatform = isAndroidPlatform
        )
    }
    val settingsEntries = remember(hasCookieManager) {
        buildList {
            if (shouldShowCookieSettingsEntry(hasCookieManager)) {
                add(cookieSettingsEntry)
            }
            addAll(globalSettingsEntries)
        }
    }
    val preferredFileManagerState = remember(preferredFileManagerLabel) {
        resolvePreferredFileManagerSummaryState(preferredFileManagerLabel)
    }
    val saveDirectoryPickerState = remember(isAndroidPlatform, hasPickerLauncher) {
        resolveSaveDirectoryPickerState(
            isAndroidPlatform = isAndroidPlatform,
            hasPickerLauncher = hasPickerLauncher
        )
    }
    val storageSummaryState = remember(historyCount, autoSavedCount, autoSavedSize) {
        resolveGlobalSettingsStorageSummaryState(historyCount, autoSavedCount, autoSavedSize)
    }
    return GlobalSettingsDerivedState(
        resolvedManualPath = resolvedManualPath,
        saveDestinationModeLabel = saveDestinationModeLabel,
        saveDestinationHint = saveDestinationHint,
        defaultAndroidSaveWarningText = defaultAndroidSaveWarningText,
        settingsEntries = settingsEntries,
        preferredFileManagerState = preferredFileManagerState,
        saveDirectoryPickerState = saveDirectoryPickerState,
        storageSummaryState = storageSummaryState
    )
}
