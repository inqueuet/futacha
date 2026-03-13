package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.resolveImageCacheDirectory
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class GlobalSettingsAction {
    Cookies,
    Email,
    X,
    Developer,
    PrivacyPolicy
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSettingsScreen(
    onBack: () -> Unit,
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    isLightweightModeEnabled: Boolean,
    onLightweightModeChanged: (Boolean) -> Unit,
    manualSaveDirectory: String = DEFAULT_MANUAL_SAVE_ROOT,
    resolvedManualSaveDirectory: String? = null,
    onManualSaveDirectoryChanged: (String) -> Unit = {},
    saveDirectorySelection: SaveDirectorySelection = SaveDirectorySelection.MANUAL_INPUT,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit = {},
    onOpenSaveDirectoryPicker: (() -> Unit)? = null,
    onOpenCookieManager: (() -> Unit)? = null,
    preferredFileManagerLabel: String? = null,
    onFileManagerSelected: ((packageName: String, label: String) -> Unit)? = null,
    onClearPreferredFileManager: (() -> Unit)? = null,
    historyEntries: List<ThreadHistoryEntry>,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    threadMenuEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries(),
    onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit = {},
    catalogNavEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries(),
    onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit = {}
) {
    val urlLauncher = rememberUrlLauncher()
    val runtimeBundle = rememberGlobalSettingsRuntimeBundle()
    val mutableStateBundle = rememberGlobalSettingsMutableStateBundle(
        manualSaveDirectory = manualSaveDirectory,
        threadMenuEntries = threadMenuEntries,
        catalogNavEntries = catalogNavEntries
    )
    val snackbarHostState = runtimeBundle.snackbarHostState
    val coroutineScope = runtimeBundle.coroutineScope
    val imageLoader = LocalFutachaImageLoader.current
    val platformContext = LocalPlatformContext.current
    val historyCount = historyEntries.size
    var isFileManagerPickerVisible by mutableStateBundle.isFileManagerPickerVisible
    var autoSavedCount by mutableStateBundle.autoSavedCount
    var autoSavedSize by mutableStateBundle.autoSavedSize
    var manualSaveInput by mutableStateBundle.manualSaveInput
    var localThreadMenuEntries by mutableStateBundle.localThreadMenuEntries
    var localCatalogNavEntries by mutableStateBundle.localCatalogNavEntries

    val effectiveAutoSavedRepository = rememberGlobalSettingsAutoSavedRepository(
        fileSystem = fileSystem,
        autoSavedThreadRepository = autoSavedThreadRepository
    )
    LaunchedEffect(effectiveAutoSavedRepository) {
        val statsUpdate = loadGlobalSettingsAutoSavedStatsUpdate(effectiveAutoSavedRepository)
        if (statsUpdate.shouldApply) {
            autoSavedCount = statsUpdate.autoSavedCount
            autoSavedSize = statsUpdate.autoSavedSize
        }
    }

    val isAndroidPlatform = runtimeBundle.isAndroidPlatform
    val availableSaveDirectorySelections = runtimeBundle.availableSaveDirectorySelections
    val effectiveSaveDirectorySelection = saveDirectorySelection
    val derivedState = rememberGlobalSettingsDerivedState(
        manualSaveDirectory = manualSaveDirectory,
        resolvedManualSaveDirectory = resolvedManualSaveDirectory,
        saveDirectorySelection = effectiveSaveDirectorySelection,
        isAndroidPlatform = isAndroidPlatform,
        hasCookieManager = onOpenCookieManager != null,
        preferredFileManagerLabel = preferredFileManagerLabel,
        hasPickerLauncher = onOpenSaveDirectoryPicker != null,
        historyCount = historyCount,
        autoSavedCount = autoSavedCount,
        autoSavedSize = autoSavedSize
    )
    val interactionBindings = buildGlobalSettingsInteractionBindingsBundle(
        saveCallbacks = buildGlobalSettingsSaveCallbacks(
        currentManualSaveInput = { manualSaveInput },
        setManualSaveInput = { manualSaveInput = it },
        setIsFileManagerPickerVisible = { isFileManagerPickerVisible = it },
        onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
        onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
        onFileManagerSelected = onFileManagerSelected
        ),
        catalogMenuCallbacks = buildGlobalSettingsCatalogMenuCallbacks(
        currentEntries = { localCatalogNavEntries },
        setLocalEntries = { localCatalogNavEntries = it },
        onCatalogNavEntriesChanged = onCatalogNavEntriesChanged
        ),
        threadMenuCallbacks = buildGlobalSettingsThreadMenuCallbacks(
        currentEntries = { localThreadMenuEntries },
        setLocalEntries = { localThreadMenuEntries = it },
        onThreadMenuEntriesChanged = onThreadMenuEntriesChanged
        ),
        linkCallbacks = buildGlobalSettingsLinkCallbacks(
        onOpenCookieManager = onOpenCookieManager,
        urlLauncher = urlLauncher,
        onBack = onBack
        ),
        cacheCallbacks = buildGlobalSettingsCacheCallbacks(
        coroutineScope = coroutineScope,
        showSnackbar = snackbarHostState::showSnackbar,
        clearImageCache = {
            withContext(AppDispatchers.io) {
                imageLoader.diskCache?.clear()
                imageLoader.memoryCache?.clear()
                Unit
            }
        },
        clearTemporaryCache = {
            withContext(AppDispatchers.io) {
                val fs = fileSystem
                if (fs != null) {
                    resolveImageCacheDirectory(platformContext)
                        ?.toString()
                        ?.let { pathString ->
                            fs.deleteRecursively(pathString).getOrThrow()
                        }
                }
                Unit
            }
        },
        refreshAutoSavedStats = {
            val statsUpdate = loadGlobalSettingsAutoSavedStatsUpdate(effectiveAutoSavedRepository)
            if (statsUpdate.shouldApply) {
                autoSavedCount = statsUpdate.autoSavedCount
                autoSavedSize = statsUpdate.autoSavedSize
            }
        }
        )
    )

    PlatformBackHandler(onBack = onBack)
    GlobalSettingsScaffold(
        appVersion = appVersion,
        historyEntries = historyEntries,
        isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
        onBackgroundRefreshChanged = onBackgroundRefreshChanged,
        isLightweightModeEnabled = isLightweightModeEnabled,
        onLightweightModeChanged = onLightweightModeChanged,
        localCatalogNavEntries = localCatalogNavEntries,
        catalogMenuCallbacks = interactionBindings.catalogMenuCallbacks,
        localThreadMenuEntries = localThreadMenuEntries,
        threadMenuCallbacks = interactionBindings.threadMenuCallbacks,
        preferredFileManagerState = derivedState.preferredFileManagerState,
        onOpenFileManagerPicker = interactionBindings.saveCallbacks.onOpenFileManagerPicker,
        onClearPreferredFileManager = onClearPreferredFileManager,
        availableSaveDirectorySelections = availableSaveDirectorySelections,
        effectiveSaveDirectorySelection = effectiveSaveDirectorySelection,
        onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
        saveDestinationModeLabel = derivedState.saveDestinationModeLabel,
        resolvedManualPath = derivedState.resolvedManualPath,
        saveDestinationHint = derivedState.saveDestinationHint,
        manualSaveInput = manualSaveInput,
        onManualSaveInputChanged = interactionBindings.saveCallbacks.onManualSaveInputChanged,
        onResetManualSaveDirectory = interactionBindings.saveCallbacks.onResetManualSaveDirectory,
        onUpdateManualSaveDirectory = interactionBindings.saveCallbacks.onUpdateManualSaveDirectory,
        saveDirectoryPickerState = derivedState.saveDirectoryPickerState,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
        onFallbackToManualInput = interactionBindings.saveCallbacks.onFallbackToManualInput,
        cacheCallbacks = interactionBindings.cacheCallbacks,
        storageSummaryState = derivedState.storageSummaryState,
        settingsEntries = derivedState.settingsEntries,
        linkCallbacks = interactionBindings.linkCallbacks,
        snackbarHostState = snackbarHostState,
        onBack = onBack
    )

    GlobalSettingsFileManagerPickerHost(
        isVisible = isFileManagerPickerVisible,
        onDismiss = interactionBindings.saveCallbacks.onDismissFileManagerPicker,
        onFileManagerSelected = interactionBindings.saveCallbacks.onFileManagerSelected
    )
}

/**
 * Platform-specific file manager picker dialog
 */
@Composable
expect fun FileManagerPickerDialog(
    onDismiss: () -> Unit,
    onFileManagerSelected: (packageName: String, label: String) -> Unit
)
