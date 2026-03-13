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

internal fun buildGlobalSettingsCatalogMenuCallbacks(
    currentEntries: () -> List<CatalogNavEntryConfig>,
    setLocalEntries: (List<CatalogNavEntryConfig>) -> Unit,
    onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit
): GlobalSettingsCatalogMenuCallbacks {
    fun updateCatalogEntries(newConfig: List<CatalogNavEntryConfig>) {
        val normalized = resolveCatalogMenuConfigState(newConfig).allEntries
        setLocalEntries(normalized)
        onCatalogNavEntriesChanged(normalized)
    }

    return GlobalSettingsCatalogMenuCallbacks(
        resetEntries = { updateCatalogEntries(defaultCatalogNavEntries()) },
        moveEntry = { id, delta ->
            updateCatalogEntries(moveCatalogMenuEntry(currentEntries(), id, delta))
        },
        setPlacement = { id, placement ->
            updateCatalogEntries(setCatalogMenuEntryPlacement(currentEntries(), id, placement))
        }
    )
}

internal data class GlobalSettingsThreadMenuCallbacks(
    val resetEntries: () -> Unit,
    val moveWithinPlacement: (ThreadMenuEntryId, Int, ThreadMenuEntryPlacement) -> Unit,
    val setPlacement: (ThreadMenuEntryId, ThreadMenuEntryPlacement) -> Unit
)

internal fun buildGlobalSettingsThreadMenuCallbacks(
    currentEntries: () -> List<ThreadMenuEntryConfig>,
    setLocalEntries: (List<ThreadMenuEntryConfig>) -> Unit,
    onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit
): GlobalSettingsThreadMenuCallbacks {
    fun updateMenuEntries(newConfig: List<ThreadMenuEntryConfig>) {
        val normalized = resolveThreadMenuConfigState(newConfig).allEntries
        setLocalEntries(normalized)
        onThreadMenuEntriesChanged(normalized)
    }

    return GlobalSettingsThreadMenuCallbacks(
        resetEntries = { updateMenuEntries(defaultThreadMenuEntries()) },
        moveWithinPlacement = { id, delta, placement ->
            updateMenuEntries(moveThreadMenuEntryWithinPlacement(currentEntries(), id, delta, placement))
        },
        setPlacement = { id, placement ->
            updateMenuEntries(setThreadMenuEntryPlacement(currentEntries(), id, placement))
        }
    )
}

internal data class GlobalSettingsLinkCallbacks(
    val onEntrySelected: (GlobalSettingsAction) -> Unit
)

internal fun buildGlobalSettingsLinkCallbacks(
    onOpenCookieManager: (() -> Unit)?,
    urlLauncher: (String) -> Unit,
    onBack: () -> Unit
): GlobalSettingsLinkCallbacks {
    return GlobalSettingsLinkCallbacks(
        onEntrySelected = { action ->
            val selection = resolveGlobalSettingsEntrySelection(action)
            if (selection.shouldOpenCookieManager) {
                onOpenCookieManager?.invoke()
            }
            selection.externalUrl?.let(urlLauncher)
            if (selection.shouldCloseScreen) {
                onBack()
            }
        }
    )
}

internal data class GlobalSettingsCacheCallbacks(
    val clearImageCache: () -> Unit,
    val clearTemporaryCache: () -> Unit,
    val refreshStorageStats: () -> Unit
)

internal data class GlobalSettingsInteractionBindingsBundle(
    val saveCallbacks: GlobalSettingsSaveCallbacks,
    val catalogMenuCallbacks: GlobalSettingsCatalogMenuCallbacks,
    val threadMenuCallbacks: GlobalSettingsThreadMenuCallbacks,
    val linkCallbacks: GlobalSettingsLinkCallbacks,
    val cacheCallbacks: GlobalSettingsCacheCallbacks
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

internal fun buildGlobalSettingsCacheCallbacks(
    coroutineScope: CoroutineScope,
    showSnackbar: suspend (String) -> Unit,
    clearImageCache: suspend () -> Unit,
    clearTemporaryCache: suspend () -> Unit,
    refreshAutoSavedStats: suspend () -> Unit
): GlobalSettingsCacheCallbacks {
    return GlobalSettingsCacheCallbacks(
        clearImageCache = {
            coroutineScope.launch {
                val result = runCatching { clearImageCache() }
                showSnackbar(
                    buildGlobalSettingsCacheCleanupMessage(
                        target = GlobalSettingsCacheCleanupTarget.IMAGE_CACHE,
                        result = result
                    )
                )
            }
        },
        clearTemporaryCache = {
            coroutineScope.launch {
                val result = runCatching { clearTemporaryCache() }
                showSnackbar(
                    buildGlobalSettingsCacheCleanupMessage(
                        target = GlobalSettingsCacheCleanupTarget.TEMPORARY_CACHE,
                        result = result
                    )
                )
            }
        },
        refreshStorageStats = {
            coroutineScope.launch { refreshAutoSavedStats() }
        }
    )
}

internal fun buildGlobalSettingsSaveCallbacks(
    currentManualSaveInput: () -> String,
    setManualSaveInput: (String) -> Unit,
    setIsFileManagerPickerVisible: (Boolean) -> Unit,
    onManualSaveDirectoryChanged: (String) -> Unit,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    onFileManagerSelected: ((packageName: String, label: String) -> Unit)?
): GlobalSettingsSaveCallbacks {
    return GlobalSettingsSaveCallbacks(
        onOpenFileManagerPicker = { setIsFileManagerPickerVisible(true) },
        onDismissFileManagerPicker = { setIsFileManagerPickerVisible(false) },
        onFileManagerSelected = { packageName, label ->
            setIsFileManagerPickerVisible(false)
            onFileManagerSelected?.invoke(packageName, label)
        },
        onManualSaveInputChanged = setManualSaveInput,
        onResetManualSaveDirectory = {
            setManualSaveInput(DEFAULT_MANUAL_SAVE_ROOT)
            onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
        },
        onUpdateManualSaveDirectory = {
            val normalized = normalizeManualSaveInputValue(currentManualSaveInput())
            setManualSaveInput(normalized)
            onManualSaveDirectoryChanged(normalized)
        },
        onFallbackToManualInput = {
            setManualSaveInput(DEFAULT_MANUAL_SAVE_ROOT)
            onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
            onSaveDirectorySelectionChanged(SaveDirectorySelection.MANUAL_INPUT)
        }
    )
}

internal fun buildGlobalSettingsInteractionBindingsBundle(
    saveCallbacks: GlobalSettingsSaveCallbacks,
    catalogMenuCallbacks: GlobalSettingsCatalogMenuCallbacks,
    threadMenuCallbacks: GlobalSettingsThreadMenuCallbacks,
    linkCallbacks: GlobalSettingsLinkCallbacks,
    cacheCallbacks: GlobalSettingsCacheCallbacks
): GlobalSettingsInteractionBindingsBundle {
    return GlobalSettingsInteractionBindingsBundle(
        saveCallbacks = saveCallbacks,
        catalogMenuCallbacks = catalogMenuCallbacks,
        threadMenuCallbacks = threadMenuCallbacks,
        linkCallbacks = linkCallbacks,
        cacheCallbacks = cacheCallbacks
    )
}
