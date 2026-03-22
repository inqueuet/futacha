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
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.resolveImageCacheDirectory
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
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
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks(),
    onOpenCookieManager: (() -> Unit)? = null,
    historyEntries: List<ThreadHistoryEntry>,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null
) {
    val urlLauncher = rememberUrlLauncher()
    val runtimeBundle = rememberGlobalSettingsRuntimeBundle()
    val mutableStateBundle = rememberGlobalSettingsMutableStateBundle(
        manualSaveDirectory = preferencesState.manualSaveDirectory,
        threadMenuEntries = preferencesState.threadMenuEntries,
        catalogNavEntries = preferencesState.catalogNavEntries
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
    val effectiveSaveDirectorySelection = preferencesState.saveDirectorySelection
    val derivedState = rememberGlobalSettingsDerivedState(
        manualSaveDirectory = preferencesState.manualSaveDirectory,
        manualSaveLocation = preferencesState.manualSaveLocation,
        resolvedManualSaveDirectory = preferencesState.resolvedManualSaveDirectory,
        saveDirectorySelection = effectiveSaveDirectorySelection,
        isAndroidPlatform = isAndroidPlatform,
        hasCookieManager = onOpenCookieManager != null,
        preferredFileManagerLabel = preferencesState.preferredFileManagerLabel,
        hasPickerLauncher = preferencesCallbacks.onOpenSaveDirectoryPicker != null,
        historyCount = historyCount,
        autoSavedCount = autoSavedCount,
        autoSavedSize = autoSavedSize
    )
    val saveCallbacks = buildGlobalSettingsSaveCallbacks(
        inputs = GlobalSettingsSaveInputs(
            currentManualSaveInput = { manualSaveInput },
            setManualSaveInput = { manualSaveInput = it },
            setIsFileManagerPickerVisible = { isFileManagerPickerVisible = it },
            onManualSaveDirectoryChanged = preferencesCallbacks.onManualSaveDirectoryChanged,
            onSaveDirectorySelectionChanged = preferencesCallbacks.onSaveDirectorySelectionChanged,
            onFileManagerSelected = preferencesCallbacks.onFileManagerSelected
        )
    )
    val catalogMenuCallbacks = buildGlobalSettingsCatalogMenuCallbacks(
        inputs = GlobalSettingsCatalogMenuInputs(
            currentEntries = { localCatalogNavEntries },
            setLocalEntries = { localCatalogNavEntries = it },
            onCatalogNavEntriesChanged = preferencesCallbacks.onCatalogNavEntriesChanged
        )
    )
    val threadMenuCallbacks = buildGlobalSettingsThreadMenuCallbacks(
        inputs = GlobalSettingsThreadMenuInputs(
            currentEntries = { localThreadMenuEntries },
            setLocalEntries = { localThreadMenuEntries = it },
            onThreadMenuEntriesChanged = preferencesCallbacks.onThreadMenuEntriesChanged
        )
    )
    val linkCallbacks = buildGlobalSettingsLinkCallbacks(
        inputs = GlobalSettingsLinkInputs(
            onOpenCookieManager = onOpenCookieManager,
            urlLauncher = urlLauncher,
            onBack = onBack
        )
    )
    val cacheCallbacks = buildGlobalSettingsCacheCallbacks(
        inputs = GlobalSettingsCacheInputs(
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
    val scaffoldBindings = GlobalSettingsScaffoldBindings(
        appVersion = preferencesState.appVersion,
        behavior = GlobalSettingsBehaviorSectionBindings(
            isBackgroundRefreshEnabled = preferencesState.isBackgroundRefreshEnabled,
            onBackgroundRefreshChanged = preferencesCallbacks.onBackgroundRefreshChanged,
            isAdsEnabled = preferencesState.isAdsEnabled,
            onAdsEnabledChanged = preferencesCallbacks.onAdsEnabledChanged,
            isLightweightModeEnabled = preferencesState.isLightweightModeEnabled,
            onLightweightModeChanged = preferencesCallbacks.onLightweightModeChanged
        ),
        catalogMenu = GlobalSettingsCatalogMenuSectionBindings(
            localCatalogNavEntries = localCatalogNavEntries,
            catalogMenuCallbacks = catalogMenuCallbacks
        ),
        threadMenu = GlobalSettingsThreadMenuSectionBindings(
            localThreadMenuEntries = localThreadMenuEntries,
            threadMenuCallbacks = threadMenuCallbacks
        ),
        save = GlobalSettingsSaveSectionBindings(
            preferredFileManagerState = derivedState.preferredFileManagerState,
            onOpenFileManagerPicker = saveCallbacks.onOpenFileManagerPicker,
            onClearPreferredFileManager = preferencesCallbacks.onClearPreferredFileManager,
            availableSaveDirectorySelections = availableSaveDirectorySelections,
            effectiveSaveDirectorySelection = effectiveSaveDirectorySelection,
            onSaveDirectorySelectionChanged = preferencesCallbacks.onSaveDirectorySelectionChanged,
            saveDestinationModeLabel = derivedState.saveDestinationModeLabel,
            resolvedManualPath = derivedState.resolvedManualPath,
            saveDestinationHint = derivedState.saveDestinationHint,
            defaultAndroidSaveWarningText = derivedState.defaultAndroidSaveWarningText,
            manualSaveInput = manualSaveInput,
            onManualSaveInputChanged = saveCallbacks.onManualSaveInputChanged,
            onResetManualSaveDirectory = saveCallbacks.onResetManualSaveDirectory,
            onUpdateManualSaveDirectory = saveCallbacks.onUpdateManualSaveDirectory,
            saveDirectoryPickerState = derivedState.saveDirectoryPickerState,
            onOpenSaveDirectoryPicker = preferencesCallbacks.onOpenSaveDirectoryPicker,
            onFallbackToManualInput = saveCallbacks.onFallbackToManualInput
        ),
        cacheCallbacks = cacheCallbacks,
        storage = GlobalSettingsStorageSectionBindings(
            storageSummaryState = derivedState.storageSummaryState,
            onRefreshStorageStats = cacheCallbacks.refreshStorageStats
        ),
        links = GlobalSettingsLinksSectionBindings(
            settingsEntries = derivedState.settingsEntries,
            linkCallbacks = linkCallbacks
        ),
        snackbarHostState = snackbarHostState,
        onBack = onBack
    )

    PlatformBackHandler(onBack = onBack)
    GlobalSettingsScaffold(bindings = scaffoldBindings)

    GlobalSettingsFileManagerPickerHost(
        isVisible = isFileManagerPickerVisible,
        onDismiss = saveCallbacks.onDismissFileManagerPicker,
        onFileManagerSelected = saveCallbacks.onFileManagerSelected
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
