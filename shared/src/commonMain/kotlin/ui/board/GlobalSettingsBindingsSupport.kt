package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsPresentValue
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
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class GlobalSettingsCatalogMenuCallbacks(
    val resetEntries: () -> Unit,
    val moveEntry: (CatalogNavEntryId, Int) -> Unit,
    val setPlacement: (CatalogNavEntryId, CatalogNavEntryPlacement) -> Unit
)

internal data class GlobalSettingsCallbackBundle(
    val saveCallbacks: GlobalSettingsSaveCallbacks,
    val catalogMenuCallbacks: GlobalSettingsCatalogMenuCallbacks,
    val threadMenuCallbacks: GlobalSettingsThreadMenuCallbacks,
    val linkCallbacks: GlobalSettingsLinkCallbacks,
    val cacheCallbacks: GlobalSettingsCacheCallbacks
)

internal data class GlobalSettingsCallbackBundleInputs(
    val currentManualSaveInput: () -> String,
    val setManualSaveInput: (String) -> Unit,
    val setIsFileManagerPickerVisible: (Boolean) -> Unit,
    val onManualSaveDirectoryChanged: (String) -> Unit,
    val onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    val onFileManagerSelected: ((packageName: String, label: String) -> Unit)?,
    val currentCatalogEntries: () -> List<CatalogNavEntryConfig>,
    val setLocalCatalogEntries: (List<CatalogNavEntryConfig>) -> Unit,
    val onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit,
    val currentThreadEntries: () -> List<ThreadMenuEntryConfig>,
    val setLocalThreadEntries: (List<ThreadMenuEntryConfig>) -> Unit,
    val onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit,
    val onOpenCookieManager: (() -> Unit)?,
    val urlLauncher: (String) -> Unit,
    val onBack: () -> Unit,
    val coroutineScope: CoroutineScope,
    val showSnackbar: suspend (String) -> Unit,
    val clearImageCache: suspend () -> Unit,
    val clearTemporaryCache: suspend () -> Unit,
    val refreshAutoSavedStats: suspend () -> Unit
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
        resetEntries = {
            AnalyticsTracker.event("menu_config_action", mapOf("menu" to "catalog", "action" to "reset"))
            updateCatalogEntries(defaultCatalogNavEntries())
        },
        moveEntry = { id, delta ->
            AnalyticsTracker.event(
                "menu_config_action",
                mapOf(
                    "menu" to "catalog",
                    "action" to "move",
                    "entry" to id.name.lowercase(),
                    "move_direction" to if (delta < 0) "up" else "down"
                )
            )
            updateCatalogEntries(moveCatalogMenuEntry(inputs.currentEntries(), id, delta))
        },
        setPlacement = { id, placement ->
            AnalyticsTracker.event(
                "menu_config_action",
                mapOf(
                    "menu" to "catalog",
                    "action" to "placement",
                    "entry" to id.name.lowercase(),
                    "placement" to placement.name.lowercase()
                )
            )
            updateCatalogEntries(setCatalogMenuEntryPlacement(inputs.currentEntries(), id, placement))
        }
    )
}

internal fun buildGlobalSettingsCallbackBundle(
    inputs: GlobalSettingsCallbackBundleInputs
): GlobalSettingsCallbackBundle {
    val saveCallbacks = buildGlobalSettingsSaveCallbacks(
        inputs = GlobalSettingsSaveInputs(
            currentManualSaveInput = inputs.currentManualSaveInput,
            setManualSaveInput = inputs.setManualSaveInput,
            setIsFileManagerPickerVisible = inputs.setIsFileManagerPickerVisible,
            onManualSaveDirectoryChanged = inputs.onManualSaveDirectoryChanged,
            onSaveDirectorySelectionChanged = inputs.onSaveDirectorySelectionChanged,
            onFileManagerSelected = inputs.onFileManagerSelected
        )
    )
    val catalogMenuCallbacks = buildGlobalSettingsCatalogMenuCallbacks(
        inputs = GlobalSettingsCatalogMenuInputs(
            currentEntries = inputs.currentCatalogEntries,
            setLocalEntries = inputs.setLocalCatalogEntries,
            onCatalogNavEntriesChanged = inputs.onCatalogNavEntriesChanged
        )
    )
    val threadMenuCallbacks = buildGlobalSettingsThreadMenuCallbacks(
        inputs = GlobalSettingsThreadMenuInputs(
            currentEntries = inputs.currentThreadEntries,
            setLocalEntries = inputs.setLocalThreadEntries,
            onThreadMenuEntriesChanged = inputs.onThreadMenuEntriesChanged
        )
    )
    val linkCallbacks = buildGlobalSettingsLinkCallbacks(
        inputs = GlobalSettingsLinkInputs(
            onOpenCookieManager = inputs.onOpenCookieManager,
            urlLauncher = inputs.urlLauncher,
            onBack = inputs.onBack
        )
    )
    val cacheCallbacks = buildGlobalSettingsCacheCallbacks(
        inputs = GlobalSettingsCacheInputs(
            coroutineScope = inputs.coroutineScope,
            showSnackbar = inputs.showSnackbar,
            clearImageCache = inputs.clearImageCache,
            clearTemporaryCache = inputs.clearTemporaryCache,
            refreshAutoSavedStats = inputs.refreshAutoSavedStats
        )
    )
    return GlobalSettingsCallbackBundle(
        saveCallbacks = saveCallbacks,
        catalogMenuCallbacks = catalogMenuCallbacks,
        threadMenuCallbacks = threadMenuCallbacks,
        linkCallbacks = linkCallbacks,
        cacheCallbacks = cacheCallbacks
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
        resetEntries = {
            AnalyticsTracker.event("menu_config_action", mapOf("menu" to "thread", "action" to "reset"))
            updateMenuEntries(defaultThreadMenuEntries())
        },
        moveWithinPlacement = { id, delta, placement ->
            AnalyticsTracker.event(
                "menu_config_action",
                mapOf(
                    "menu" to "thread",
                    "action" to "move",
                    "entry" to id.name.lowercase(),
                    "move_direction" to if (delta < 0) "up" else "down",
                    "placement" to placement.name.lowercase()
                )
            )
            updateMenuEntries(moveThreadMenuEntryWithinPlacement(inputs.currentEntries(), id, delta, placement))
        },
        setPlacement = { id, placement ->
            AnalyticsTracker.event(
                "menu_config_action",
                mapOf(
                    "menu" to "thread",
                    "action" to "placement",
                    "entry" to id.name.lowercase(),
                    "placement" to placement.name.lowercase()
                )
            )
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
            AnalyticsTracker.event("global_settings_entry_selected", mapOf("entry" to action.name.lowercase()))
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
    val refreshStorageStats: () -> Unit,
    val isCleanupInProgress: () -> Boolean = { false }
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
    var cleanupJob: Job? = null

    fun launchCleanup(
        target: GlobalSettingsCacheCleanupTarget,
        action: suspend () -> Unit
    ) {
        if (cleanupJob?.isActive == true) return
        cleanupJob = inputs.coroutineScope.launch {
            val result = runSuspendCatchingPreservingCancellation {
                withTimeout(GLOBAL_SETTINGS_CACHE_CLEANUP_TIMEOUT_MILLIS) {
                    action()
                }
            }
            inputs.showSnackbar(
                buildGlobalSettingsCacheCleanupMessage(
                    target = target,
                    result = result
                )
            )
        }
    }

    return GlobalSettingsCacheCallbacks(
        clearImageCache = {
            AnalyticsTracker.event("cache_cleanup_started", mapOf("target" to "image"))
            launchCleanup(GlobalSettingsCacheCleanupTarget.IMAGE_CACHE, inputs.clearImageCache)
        },
        clearTemporaryCache = {
            AnalyticsTracker.event("cache_cleanup_started", mapOf("target" to "temporary"))
            launchCleanup(GlobalSettingsCacheCleanupTarget.TEMPORARY_CACHE, inputs.clearTemporaryCache)
        },
        refreshStorageStats = {
            AnalyticsTracker.event("storage_stats_refreshed")
            inputs.coroutineScope.launch { inputs.refreshAutoSavedStats() }
        },
        isCleanupInProgress = { cleanupJob?.isActive == true }
    )
}

private const val GLOBAL_SETTINGS_CACHE_CLEANUP_TIMEOUT_MILLIS = 10_000L

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
        onOpenFileManagerPicker = {
            AnalyticsTracker.event("file_manager_picker_opened")
            inputs.setIsFileManagerPickerVisible(true)
        },
        onDismissFileManagerPicker = {
            AnalyticsTracker.event("file_manager_picker_dismissed")
            inputs.setIsFileManagerPickerVisible(false)
        },
        onFileManagerSelected = { packageName, label ->
            AnalyticsTracker.event(
                "file_manager_selected",
                mapOf("has_package" to analyticsPresentValue(packageName.takeIf { it.isNotBlank() }))
            )
            inputs.setIsFileManagerPickerVisible(false)
            inputs.onFileManagerSelected?.invoke(packageName, label)
        },
        onManualSaveInputChanged = {
            AnalyticsTracker.event("manual_save_input_changed")
            inputs.setManualSaveInput(it)
        },
        onResetManualSaveDirectory = {
            AnalyticsTracker.event("manual_save_directory_action", mapOf("action" to "reset"))
            inputs.setManualSaveInput(DEFAULT_MANUAL_SAVE_ROOT)
            inputs.onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
        },
        onUpdateManualSaveDirectory = {
            val normalized = normalizeManualSaveInputValue(inputs.currentManualSaveInput())
            AnalyticsTracker.event(
                "manual_save_directory_action",
                mapOf(
                    "action" to "update",
                    "input_state" to analyticsPresentValue(normalized.takeIf { it.isNotBlank() })
                )
            )
            inputs.setManualSaveInput(normalized)
            inputs.onManualSaveDirectoryChanged(normalized)
        },
        onFallbackToManualInput = {
            AnalyticsTracker.event("manual_save_directory_action", mapOf("action" to "fallback_manual"))
            inputs.setManualSaveInput(DEFAULT_MANUAL_SAVE_ROOT)
            inputs.onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
            inputs.onSaveDirectorySelectionChanged(SaveDirectorySelection.MANUAL_INPUT)
        }
    )
}
