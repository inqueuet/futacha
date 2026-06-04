package com.valoser.futacha.shared.ui.board

import coil3.compose.LocalPlatformContext
import androidx.compose.runtime.getValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.resolveImageCacheDirectory
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.isAndroid
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope

private const val GLOBAL_SETTINGS_AUTO_SAVE_STATS_FETCH_TIMEOUT_MS = 4_000L

internal data class GlobalSettingsMutableStateBundle(
    val isFileManagerPickerVisible: MutableState<Boolean>,
    val autoSavedCount: MutableState<Int?>,
    val autoSavedSize: MutableState<Long?>,
    val manualSaveInput: MutableState<String>,
    val localThreadMenuEntries: MutableState<List<ThreadMenuEntryConfig>>,
    val localCatalogNavEntries: MutableState<List<CatalogNavEntryConfig>>
)

@Composable
internal fun rememberGlobalSettingsMutableStateBundle(
    manualSaveDirectory: String,
    threadMenuEntries: List<ThreadMenuEntryConfig>,
    catalogNavEntries: List<CatalogNavEntryConfig>
): GlobalSettingsMutableStateBundle {
    return GlobalSettingsMutableStateBundle(
        isFileManagerPickerVisible = rememberSaveable { mutableStateOf(false) },
        autoSavedCount = remember { mutableStateOf(null) },
        autoSavedSize = remember { mutableStateOf(null) },
        manualSaveInput = rememberSaveable(manualSaveDirectory) {
            mutableStateOf(manualSaveDirectory)
        },
        localThreadMenuEntries = remember(threadMenuEntries) {
            mutableStateOf(resolveThreadMenuConfigState(threadMenuEntries).allEntries)
        },
        localCatalogNavEntries = remember(catalogNavEntries) {
            mutableStateOf(resolveCatalogMenuConfigState(catalogNavEntries).allEntries)
        }
    )
}

internal data class GlobalSettingsRuntimeBundle(
    val snackbarHostState: SnackbarHostState,
    val coroutineScope: CoroutineScope,
    val isAndroidPlatform: Boolean,
    val availableSaveDirectorySelections: List<com.valoser.futacha.shared.util.SaveDirectorySelection>
)

internal data class GlobalSettingsScreenRuntime(
    val scaffoldBindings: GlobalSettingsScaffoldBindings,
    val isFileManagerPickerVisible: Boolean,
    val onDismissFileManagerPicker: () -> Unit,
    val onFileManagerSelected: (String, String) -> Unit
)

@Composable
internal fun rememberGlobalSettingsRuntimeBundle(): GlobalSettingsRuntimeBundle {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val isAndroidPlatform = remember { isAndroid() }
    val availableSaveDirectorySelections = remember {
        com.valoser.futacha.shared.util.SaveDirectorySelection.entries.toList()
    }
    return GlobalSettingsRuntimeBundle(
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        isAndroidPlatform = isAndroidPlatform,
        availableSaveDirectorySelections = availableSaveDirectorySelections
    )
}

@Composable
internal fun rememberGlobalSettingsAutoSavedRepository(
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?
): SavedThreadRepository? {
    return remember(fileSystem, autoSavedThreadRepository) {
        autoSavedThreadRepository ?: fileSystem?.let {
            SavedThreadRepository(it, baseDirectory = AUTO_SAVE_DIRECTORY)
        }
    }
}

@Composable
internal fun rememberGlobalSettingsScreenRuntime(
    onBack: () -> Unit,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    onOpenCookieManager: (() -> Unit)?,
    historyEntries: List<ThreadHistoryEntry>,
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?,
    openFileManagerPickerRequest: Int = 0
): GlobalSettingsScreenRuntime {
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

    LaunchedEffect(openFileManagerPickerRequest) {
        if (openFileManagerPickerRequest > 0) {
            isFileManagerPickerVisible = true
        }
    }

    val effectiveAutoSavedRepository = rememberGlobalSettingsAutoSavedRepository(
        fileSystem = fileSystem,
        autoSavedThreadRepository = autoSavedThreadRepository
    )
    val applyAutoSavedStatsUpdate: (GlobalSettingsAutoSavedStatsUpdate) -> Unit = { update ->
        applyGlobalSettingsAutoSavedStatsUpdate(
            update = update,
            setAutoSavedCount = { autoSavedCount = it },
            setAutoSavedSize = { autoSavedSize = it }
        )
    }
    LaunchedEffect(effectiveAutoSavedRepository) {
        applyAutoSavedStatsUpdate(
            loadGlobalSettingsAutoSavedStatsUpdate(effectiveAutoSavedRepository)
        )
    }

    val effectiveSaveDirectorySelection = preferencesState.saveDirectorySelection
    val derivedState = rememberGlobalSettingsDerivedState(
        manualSaveDirectory = preferencesState.manualSaveDirectory,
        manualSaveLocation = preferencesState.manualSaveLocation,
        resolvedManualSaveDirectory = preferencesState.resolvedManualSaveDirectory,
        saveDirectorySelection = effectiveSaveDirectorySelection,
        isAndroidPlatform = runtimeBundle.isAndroidPlatform,
        hasCookieManager = onOpenCookieManager != null,
        preferredFileManagerLabel = preferencesState.preferredFileManagerLabel,
        hasPickerLauncher = preferencesCallbacks.onOpenSaveDirectoryPicker != null,
        historyCount = historyCount,
        autoSavedCount = autoSavedCount,
        autoSavedSize = autoSavedSize
    )
    val callbackBundle = buildGlobalSettingsCallbackBundle(
        inputs = GlobalSettingsCallbackBundleInputs(
            currentManualSaveInput = { manualSaveInput },
            setManualSaveInput = { manualSaveInput = it },
            setIsFileManagerPickerVisible = { isFileManagerPickerVisible = it },
            onManualSaveDirectoryChanged = preferencesCallbacks.onManualSaveDirectoryChanged,
            onSaveDirectorySelectionChanged = preferencesCallbacks.onSaveDirectorySelectionChanged,
            onFileManagerSelected = preferencesCallbacks.onFileManagerSelected,
            currentCatalogEntries = { localCatalogNavEntries },
            setLocalCatalogEntries = { localCatalogNavEntries = it },
            onCatalogNavEntriesChanged = preferencesCallbacks.onCatalogNavEntriesChanged,
            currentThreadEntries = { localThreadMenuEntries },
            setLocalThreadEntries = { localThreadMenuEntries = it },
            onThreadMenuEntriesChanged = preferencesCallbacks.onThreadMenuEntriesChanged,
            onOpenCookieManager = onOpenCookieManager,
            urlLauncher = urlLauncher,
            onBack = onBack,
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
                    fileSystem
                        ?.let { fs ->
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
                applyAutoSavedStatsUpdate(
                    loadGlobalSettingsAutoSavedStatsUpdate(effectiveAutoSavedRepository)
                )
            }
        )
    )
    val scaffoldBindings = buildGlobalSettingsScaffoldBindings(
        inputs = GlobalSettingsScaffoldBindingInputs(
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks,
            derivedState = derivedState,
            localCatalogNavEntries = localCatalogNavEntries,
            catalogMenuCallbacks = callbackBundle.catalogMenuCallbacks,
            localThreadMenuEntries = localThreadMenuEntries,
            threadMenuCallbacks = callbackBundle.threadMenuCallbacks,
            availableSaveDirectorySelections = runtimeBundle.availableSaveDirectorySelections,
            effectiveSaveDirectorySelection = effectiveSaveDirectorySelection,
            manualSaveInput = manualSaveInput,
            saveCallbacks = callbackBundle.saveCallbacks,
            cacheCallbacks = callbackBundle.cacheCallbacks,
            linkCallbacks = callbackBundle.linkCallbacks,
            snackbarHostState = snackbarHostState,
            onBack = onBack
        )
    )

    return GlobalSettingsScreenRuntime(
        scaffoldBindings = scaffoldBindings,
        isFileManagerPickerVisible = isFileManagerPickerVisible,
        onDismissFileManagerPicker = callbackBundle.saveCallbacks.onDismissFileManagerPicker,
        onFileManagerSelected = callbackBundle.saveCallbacks.onFileManagerSelected
    )
}

internal data class GlobalSettingsAutoSavedStatsUpdate(
    val autoSavedCount: Int?,
    val autoSavedSize: Long?,
    val shouldApply: Boolean
)

internal fun resolveGlobalSettingsAutoSavedStatsUpdate(
    hasRepository: Boolean,
    stats: SavedThreadRepository.SavedThreadStats?
): GlobalSettingsAutoSavedStatsUpdate {
    if (!hasRepository) {
        return GlobalSettingsAutoSavedStatsUpdate(
            autoSavedCount = null,
            autoSavedSize = null,
            shouldApply = true
        )
    }
    return if (stats != null) {
        GlobalSettingsAutoSavedStatsUpdate(
            autoSavedCount = stats.threadCount,
            autoSavedSize = stats.totalSize,
            shouldApply = true
        )
    } else {
        GlobalSettingsAutoSavedStatsUpdate(
            autoSavedCount = null,
            autoSavedSize = null,
            shouldApply = false
        )
    }
}

internal suspend fun loadGlobalSettingsAutoSavedStatsUpdate(
    repository: SavedThreadRepository?
): GlobalSettingsAutoSavedStatsUpdate {
    if (repository == null) {
        return resolveGlobalSettingsAutoSavedStatsUpdate(
            hasRepository = false,
            stats = null
        )
    }
    val stats = runCatching {
        withTimeoutOrNull(GLOBAL_SETTINGS_AUTO_SAVE_STATS_FETCH_TIMEOUT_MS) {
            withContext(AppDispatchers.io) {
                repository.getStats()
            }
        }
    }.getOrNull()
    return resolveGlobalSettingsAutoSavedStatsUpdate(
        hasRepository = true,
        stats = stats
    )
}

internal fun applyGlobalSettingsAutoSavedStatsUpdate(
    update: GlobalSettingsAutoSavedStatsUpdate,
    setAutoSavedCount: (Int?) -> Unit,
    setAutoSavedSize: (Long?) -> Unit
) {
    if (!update.shouldApply) return
    setAutoSavedCount(update.autoSavedCount)
    setAutoSavedSize(update.autoSavedSize)
}
