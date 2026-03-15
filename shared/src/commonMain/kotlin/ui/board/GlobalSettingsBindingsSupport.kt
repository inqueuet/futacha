package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class GlobalSettingsCatalogMenuCallbacks(
    val resetEntries: () -> Unit,
    val moveEntry: (CatalogNavEntryId, Int) -> Unit,
    val setPlacement: (CatalogNavEntryId, CatalogNavEntryPlacement) -> Unit
)

internal data class GlobalSettingsCatalogMenuInputs(
    val currentEntries: () -> List<CatalogNavEntryConfig>,
    val setLocalEntries: (List<CatalogNavEntryConfig>) -> Unit,
    val onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit
)

internal fun buildGlobalSettingsCatalogMenuCallbacks(
    inputs: GlobalSettingsCatalogMenuInputs
): GlobalSettingsCatalogMenuCallbacks {
    fun updateCatalogEntries(newConfig: List<CatalogNavEntryConfig>) {
        val normalized = resolveCatalogMenuConfigState(newConfig).allEntries
        inputs.setLocalEntries(normalized)
        inputs.onCatalogNavEntriesChanged(normalized)
    }

    return GlobalSettingsCatalogMenuCallbacks(
        resetEntries = { updateCatalogEntries(defaultCatalogNavEntries()) },
        moveEntry = { id, delta ->
            updateCatalogEntries(moveCatalogMenuEntry(inputs.currentEntries(), id, delta))
        },
        setPlacement = { id, placement ->
            updateCatalogEntries(setCatalogMenuEntryPlacement(inputs.currentEntries(), id, placement))
        }
    )
}

internal data class GlobalSettingsThreadMenuCallbacks(
    val resetEntries: () -> Unit,
    val moveWithinPlacement: (ThreadMenuEntryId, Int, ThreadMenuEntryPlacement) -> Unit,
    val setPlacement: (ThreadMenuEntryId, ThreadMenuEntryPlacement) -> Unit
)

internal data class GlobalSettingsThreadMenuInputs(
    val currentEntries: () -> List<ThreadMenuEntryConfig>,
    val setLocalEntries: (List<ThreadMenuEntryConfig>) -> Unit,
    val onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit
)

internal fun buildGlobalSettingsThreadMenuCallbacks(
    inputs: GlobalSettingsThreadMenuInputs
): GlobalSettingsThreadMenuCallbacks {
    fun updateMenuEntries(newConfig: List<ThreadMenuEntryConfig>) {
        val normalized = resolveThreadMenuConfigState(newConfig).allEntries
        inputs.setLocalEntries(normalized)
        inputs.onThreadMenuEntriesChanged(normalized)
    }

    return GlobalSettingsThreadMenuCallbacks(
        resetEntries = { updateMenuEntries(defaultThreadMenuEntries()) },
        moveWithinPlacement = { id, delta, placement ->
            updateMenuEntries(moveThreadMenuEntryWithinPlacement(inputs.currentEntries(), id, delta, placement))
        },
        setPlacement = { id, placement ->
            updateMenuEntries(setThreadMenuEntryPlacement(inputs.currentEntries(), id, placement))
        }
    )
}

internal data class GlobalSettingsLinkCallbacks(
    val onEntrySelected: (GlobalSettingsAction) -> Unit
)

internal data class GlobalSettingsLinkInputs(
    val onOpenCookieManager: (() -> Unit)?,
    val urlLauncher: (String) -> Unit,
    val onBack: () -> Unit
)

internal fun buildGlobalSettingsLinkCallbacks(
    inputs: GlobalSettingsLinkInputs
): GlobalSettingsLinkCallbacks {
    return GlobalSettingsLinkCallbacks(
        onEntrySelected = { action ->
            val selection = resolveGlobalSettingsEntrySelection(action)
            if (selection.shouldOpenCookieManager) {
                inputs.onOpenCookieManager?.invoke()
            }
            selection.externalUrl?.let(inputs.urlLauncher)
            if (selection.shouldCloseScreen) {
                inputs.onBack()
            }
        }
    )
}

internal data class GlobalSettingsCacheCallbacks(
    val clearImageCache: () -> Unit,
    val clearTemporaryCache: () -> Unit,
    val refreshStorageStats: () -> Unit
)

internal data class GlobalSettingsSaveCallbacks(
    val onOpenFileManagerPicker: () -> Unit,
    val onDismissFileManagerPicker: () -> Unit,
    val onFileManagerSelected: (String, String) -> Unit,
    val onManualSaveInputChanged: (String) -> Unit,
    val onResetManualSaveDirectory: () -> Unit,
    val onUpdateManualSaveDirectory: () -> Unit,
    val onFallbackToManualInput: () -> Unit
)

internal data class GlobalSettingsCacheInputs(
    val coroutineScope: CoroutineScope,
    val showSnackbar: suspend (String) -> Unit,
    val clearImageCache: suspend () -> Unit,
    val clearTemporaryCache: suspend () -> Unit,
    val refreshAutoSavedStats: suspend () -> Unit
)

internal fun buildGlobalSettingsCacheCallbacks(
    inputs: GlobalSettingsCacheInputs
): GlobalSettingsCacheCallbacks {
    return GlobalSettingsCacheCallbacks(
        clearImageCache = {
            inputs.coroutineScope.launch {
                val result = runCatching { inputs.clearImageCache() }
                inputs.showSnackbar(
                    buildGlobalSettingsCacheCleanupMessage(
                        target = GlobalSettingsCacheCleanupTarget.IMAGE_CACHE,
                        result = result
                    )
                )
            }
        },
        clearTemporaryCache = {
            inputs.coroutineScope.launch {
                val result = runCatching { inputs.clearTemporaryCache() }
                inputs.showSnackbar(
                    buildGlobalSettingsCacheCleanupMessage(
                        target = GlobalSettingsCacheCleanupTarget.TEMPORARY_CACHE,
                        result = result
                    )
                )
            }
        },
        refreshStorageStats = {
            inputs.coroutineScope.launch { inputs.refreshAutoSavedStats() }
        }
    )
}

internal data class GlobalSettingsSaveInputs(
    val currentManualSaveInput: () -> String,
    val setManualSaveInput: (String) -> Unit,
    val setIsFileManagerPickerVisible: (Boolean) -> Unit,
    val onManualSaveDirectoryChanged: (String) -> Unit,
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    val onFileManagerSelected: ((packageName: String, label: String) -> Unit)?
)

internal fun buildGlobalSettingsSaveCallbacks(
    inputs: GlobalSettingsSaveInputs
): GlobalSettingsSaveCallbacks {
    return GlobalSettingsSaveCallbacks(
        onOpenFileManagerPicker = { inputs.setIsFileManagerPickerVisible(true) },
        onDismissFileManagerPicker = { inputs.setIsFileManagerPickerVisible(false) },
        onFileManagerSelected = { packageName, label ->
            inputs.setIsFileManagerPickerVisible(false)
            inputs.onFileManagerSelected?.invoke(packageName, label)
        },
        onManualSaveInputChanged = inputs.setManualSaveInput,
        onResetManualSaveDirectory = {
            inputs.setManualSaveInput(DEFAULT_MANUAL_SAVE_ROOT)
            inputs.onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
        },
        onUpdateManualSaveDirectory = {
            val normalized = normalizeManualSaveInputValue(inputs.currentManualSaveInput())
            inputs.setManualSaveInput(normalized)
            inputs.onManualSaveDirectoryChanged(normalized)
        },
        onFallbackToManualInput = {
            inputs.setManualSaveInput(DEFAULT_MANUAL_SAVE_ROOT)
            inputs.onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
            inputs.onSaveDirectorySelectionChanged(SaveDirectorySelection.MANUAL_INPUT)
        }
    )
}
