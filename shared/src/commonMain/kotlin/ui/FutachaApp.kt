package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.ai.FutachaAiCommandOutcome
import com.valoser.futacha.shared.ai.FutachaAiConfirmationRequest
import com.valoser.futacha.shared.ai.parseFutachaAiDeepLink
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.CrashReporter
import com.valoser.futacha.shared.analytics.PerformanceTracker
import com.valoser.futacha.shared.analytics.analyticsBoardKind
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsEnabledValue
import com.valoser.futacha.shared.analytics.analyticsPresentValue
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.analytics.analyticsTextHasUrl
import com.valoser.futacha.shared.analytics.analyticsTextLengthBucket
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.state.AppStateSeedDefaults
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.ui.board.GlobalSettingsScreen
import com.valoser.futacha.shared.ui.board.HistoryViewSettings
import com.valoser.futacha.shared.ui.board.HistoryViewSettingsBinding
import com.valoser.futacha.shared.ui.board.LocalHistoryViewSettingsBinding
import com.valoser.futacha.shared.ui.board.PlatformBackgroundLifecycleEffect
import com.valoser.futacha.shared.ui.image.CATALOG_IMAGE_DISK_CACHE_DIR
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.rememberFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.util.applyAppIconVariant
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.COMPAT_IMAGE_CACHE_LOCATION_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.COMPAT_IMAGE_CACHE_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.COMPAT_CATALOG_IMAGE_CACHE_LOCATION_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.COMPAT_CATALOG_IMAGE_CACHE_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.COMPAT_IMAGE_PARALLEL_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.parseCompatCatalogImageCacheQuotaBytes
import com.valoser.futacha.shared.compat.parseCompatImageCacheQuotaBytes
import com.valoser.futacha.shared.compat.parseCompatImageParallelism
import com.valoser.futacha.shared.ui.image.parseCompatCacheLocation
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.detectDevicePerformanceProfile
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.DevicePerformanceProfile
import com.valoser.futacha.shared.version.UpdateInfo
import com.valoser.futacha.shared.version.VersionChecker
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime

private const val TAG = "FutachaApp"
private const val APP_LOCK_HASH_LOADING = "__futacha_app_lock_loading__"
private const val AI_COMMAND_ID_MAX_BYTES = 128
private const val AI_HANDLED_COMMAND_ID_MAX_COUNT = 128

private data class FutachaStartupTheme(
    val mode: ThemeMode,
    val palette: ThemePalette
)

@OptIn(ExperimentalTime::class)
@Composable
fun FutachaApp(
    stateStore: AppStateStore,
    boardList: List<BoardSummary> = mockBoardSummaries,
    history: List<ThreadHistoryEntry> = mockThreadHistory,
    versionChecker: VersionChecker? = null,
    httpClient: io.ktor.client.HttpClient? = null,
    sharedRepository: BoardRepository? = null,
    sharedHistoryRefresher: HistoryRefresher? = null,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    cookieRepository: CookieRepository? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    /**
     * iOS owns its compatibility SQLite store in the native host.  Supplying
     * this optional handler lets only that host perform a manual compat-history
     * refresh without changing Android's existing CompatibilityApp behavior.
     */
    compatibilityHistoryRefresh: (suspend () -> Result<String>)? = null,
    platformAiDeepLink: String? = null,
    onPlatformAiDeepLinkConsumed: (String) -> Unit = {},
    /** iOS can own the single-consumer bridge and inject commands here. */
    platformAiCommand: FutachaAiCommand? = null,
    onPlatformAiCommandConsumed: (FutachaAiCommand) -> Unit = {},
    consumeAiCommandBridge: Boolean = true,
    platformThreadDeepLink: String? = null,
    platformThreadDeepLinkPreapprovedBoardRegistration: Boolean = false,
    onPlatformThreadDeepLinkConsumed: (String) -> Unit = {},
    platformBoardDeepLink: String? = null,
    onPlatformBoardDeepLinkConsumed: (String) -> Unit = {},
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)? = null,
    onArchiveReportEnqueued: (Int) -> Unit = {},
    onArchiveReportEnabledChanged: (Boolean) -> Unit = {},
    /**
     * Keeps the currently visible modern-mode thread available to the Android
     * profile switch bridge.  The compatibility profile is hosted by a new
     * Activity after a mode switch, so a purely Compose-local navigation state
     * cannot carry the active thread across that boundary.
     */
    onCurrentThreadChanged: (String?) -> Unit = {},
    experienceProfile: ExperienceProfile = ExperienceProfile.FUTACHA,
    compatibilityStore: CompatibilityStore? = null,
    onExitApplication: () -> Unit = {}
) {
    val platformContext = LocalPlatformContext.current
    LaunchedEffect(platformContext) {
        // Firebase initialization may synchronously touch SharedPreferences or
        // Remote Config.  Keep it off the Compose/Main dispatcher; rendering
        // the first screen must not depend on analytics being ready.
        withContext(AppDispatchers.io) {
            AnalyticsTracker.configure(platformContext)
            PerformanceTracker.configure(platformContext)
            CrashReporter.configure(platformContext)
        }
    }
    val devicePerformanceProfile by produceState(
        initialValue = DevicePerformanceProfile(isLowRam = false, isLowStorage = false),
        key1 = platformContext
    ) {
        value = withContext(AppDispatchers.io) {
            detectDevicePerformanceProfile(platformContext)
        }
    }
    // Resolve the persisted palette before drawing any app-owned surface.
    // Initializing collectAsState with Classic produced a visible classic/light
    // frame before a saved dark/custom palette arrived (#53).
    val startupTheme by produceState<FutachaStartupTheme?>(
        initialValue = null,
        key1 = stateStore
    ) {
        value = combine(stateStore.themeMode, stateStore.themePalette) { mode, palette ->
            FutachaStartupTheme(mode, palette)
        }.first()
    }
    if (startupTheme == null) return
    val resolvedStartupTheme = startupTheme ?: return
    val startupAppLockHash by produceState<String?>(
        initialValue = APP_LOCK_HASH_LOADING,
        key1 = stateStore
    ) {
        stateStore.appLockPasswordHash
            .catch { error ->
                if (error is CancellationException) throw error
                Logger.e(TAG, "Failed to load app lock password hash", error)
                value = APP_LOCK_HASH_LOADING
            }
            .collect { storedHash ->
                value = storedHash
            }
    }
    var isUnlockedForSession by remember { mutableStateOf(false) }
    LaunchedEffect(startupAppLockHash) {
        if (startupAppLockHash == null) {
            isUnlockedForSession = true
        }
    }
    if (startupAppLockHash == APP_LOCK_HASH_LOADING) {
        // Compatibility owns a separate persisted palette. Painting the
        // modern loading surface before that palette is available produces a
        // clearly visible Futacha/classic flash during every cold start (#53).
        // Keep the preview window untouched until both the lock state and the
        // compatibility preferences can be resolved.
        if (experienceProfile == ExperienceProfile.TOSHIAKI_COMPAT) return
        FutachaTheme(
            themeMode = resolvedStartupTheme.mode,
            themePalette = resolvedStartupTheme.palette
        ) {
            Surface(modifier = Modifier.fillMaxSize().analyticsGestureSurface()) {
                FutachaAppLockLoadingScreen()
            }
        }
        return
    }
    if (startupAppLockHash != null && !isUnlockedForSession) {
        FutachaTheme(
            themeMode = resolvedStartupTheme.mode,
            themePalette = resolvedStartupTheme.palette
        ) {
            LaunchedEffect(Unit) {
                AnalyticsTracker.screen("app_lock")
            }
            Surface(modifier = Modifier.fillMaxSize().analyticsGestureSurface()) {
                FutachaAppLockScreen(
                    passwordHash = startupAppLockHash.orEmpty(),
                    onUnlocked = { isUnlockedForSession = true }
                )
            }
        }
        return
    }
    if (startupAppLockHash != null) {
        PlatformBackgroundLifecycleEffect {
            isUnlockedForSession = false
        }
    }

    // Keep the update check above the profile split so compatibility-mode
    // users receive the same notification as modern-mode users.
    val updateCheckEnabled by produceState<Boolean?>(initialValue = null, stateStore) {
        stateStore.isUpdateCheckEnabled.collect { value = it }
    }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(versionChecker, updateCheckEnabled) {
        updateInfo = fetchFutachaUpdateInfoIfEnabled(
            enabled = updateCheckEnabled == true,
            versionChecker = versionChecker
        ) {
            Logger.e(TAG, "Version check failed", it)
        }
    }
    updateInfo?.let { info ->
        UpdateNotificationDialog(
            updateInfo = info,
            onDismiss = { updateInfo = null }
        )
    }

    if (experienceProfile == ExperienceProfile.TOSHIAKI_COMPAT && compatibilityStore != null) {
        // The compatibility thread menu saves into the manual-save location,
        // so its saved-thread index must not point at the background auto-save
        // directory used by the modern history refresher.
        val compatibilitySavedThreadRepository = remember(fileSystem) {
            fileSystem?.let {
                SavedThreadRepository(it, baseDirectory = MANUAL_SAVE_DIRECTORY)
            }
        }
        val compatibilityPreferences by compatibilityStore.preferences.collectAsState(emptyMap())
        val compatibilityImageCacheBytes = remember(
            compatibilityPreferences[COMPAT_IMAGE_CACHE_PREFERENCE_KEY]
        ) {
            parseCompatImageCacheQuotaBytes(
                compatibilityPreferences[COMPAT_IMAGE_CACHE_PREFERENCE_KEY]
            )
        }
        val compatibilityCatalogImageCacheBytes = remember(
            compatibilityPreferences[COMPAT_CATALOG_IMAGE_CACHE_PREFERENCE_KEY]
        ) {
            parseCompatCatalogImageCacheQuotaBytes(
                compatibilityPreferences[COMPAT_CATALOG_IMAGE_CACHE_PREFERENCE_KEY]
            )
        }
        val compatibilityImageParallelism = remember(
            compatibilityPreferences[COMPAT_IMAGE_PARALLEL_PREFERENCE_KEY]
        ) {
            parseCompatImageParallelism(
                compatibilityPreferences[COMPAT_IMAGE_PARALLEL_PREFERENCE_KEY]
            )
        }
        val compatibilityCacheLocation = remember(
            compatibilityPreferences[COMPAT_IMAGE_CACHE_LOCATION_PREFERENCE_KEY]
        ) {
            parseCompatCacheLocation(
                compatibilityPreferences[COMPAT_IMAGE_CACHE_LOCATION_PREFERENCE_KEY]
            )
        }
        val compatibilityCatalogCacheLocation = remember(
            compatibilityPreferences[COMPAT_CATALOG_IMAGE_CACHE_LOCATION_PREFERENCE_KEY]
        ) {
            parseCompatCacheLocation(
                compatibilityPreferences[COMPAT_CATALOG_IMAGE_CACHE_LOCATION_PREFERENCE_KEY]
            )
        }
        val compatibilityImageLoader = rememberFutachaImageLoader(
            lightweightMode = devicePerformanceProfile.isLowSpec,
            performanceProfile = devicePerformanceProfile,
            httpClient = httpClient,
            diskCacheBytesOverride = compatibilityImageCacheBytes,
            cacheLocation = compatibilityCacheLocation,
            parallelismOverride = compatibilityImageParallelism
        )
        val compatibilityCatalogImageLoader = rememberFutachaImageLoader(
            lightweightMode = devicePerformanceProfile.isLowSpec,
            performanceProfile = devicePerformanceProfile,
            httpClient = httpClient,
            diskCacheBytesOverride = compatibilityCatalogImageCacheBytes,
            cacheLocation = compatibilityCatalogCacheLocation,
            parallelismOverride = compatibilityImageParallelism,
            diskCacheDirectoryName = CATALOG_IMAGE_DISK_CACHE_DIR
        )
        DisposableEffect(compatibilityImageLoader) {
            onDispose {
                runCatching { compatibilityImageLoader.shutdown() }
                    .onFailure { error -> Logger.e(TAG, "Failed to shutdown compatibility ImageLoader", error) }
            }
        }
        DisposableEffect(compatibilityCatalogImageLoader) {
            onDispose {
                runCatching { compatibilityCatalogImageLoader.shutdown() }
                    .onFailure { error -> Logger.e(TAG, "Failed to shutdown catalog ImageLoader", error) }
            }
        }
        CompositionLocalProvider(LocalFutachaImageLoader provides compatibilityImageLoader) {
            CompatibilityApp(
                store = compatibilityStore,
                repository = sharedRepository,
                stateStore = stateStore,
                historyAutoSavedThreadRepository = autoSavedThreadRepository,
                httpClient = httpClient,
                fileSystem = fileSystem,
                cookieRepository = cookieRepository,
                savedThreadRepository = compatibilitySavedThreadRepository,
                compatibilityHistoryRefresh = compatibilityHistoryRefresh,
                appVersion = remember(versionChecker) { versionChecker?.getCurrentVersion() ?: "1.0" },
                imageLoader = compatibilityImageLoader,
                catalogImageLoader = compatibilityCatalogImageLoader,
                initialThreadDeepLink = platformThreadDeepLink,
                initialThreadDeepLinkPreapprovedBoardRegistration =
                    platformThreadDeepLinkPreapprovedBoardRegistration,
                onThreadDeepLinkConsumed = onPlatformThreadDeepLinkConsumed,
                initialBoardDeepLink = platformBoardDeepLink,
                onBoardDeepLinkConsumed = onPlatformBoardDeepLinkConsumed,
                platformAiCommand = platformAiCommand,
                onPlatformAiCommandConsumed = onPlatformAiCommandConsumed,
                onArchiveReportEnqueued = onArchiveReportEnqueued,
                onArchiveReportEnabledChanged = onArchiveReportEnabledChanged,
                onExitApplication = onExitApplication
            )
        }
        return
    }
    var navigationState by rememberSaveable(stateSaver = FutachaNavigationState.Saver) {
        mutableStateOf(FutachaNavigationState())
    }
    var historyViewSettings by rememberSaveable(stateSaver = HistoryViewSettings.Saver) {
        mutableStateOf(HistoryViewSettings.Default)
    }
    val observedRuntimeState = rememberFutachaObservedRuntimeState(
        stateStore = stateStore,
        boardList = boardList,
        history = history,
        versionChecker = versionChecker,
        fileSystem = fileSystem,
        platformContext = platformContext,
        isThreadScreenVisible = navigationState.selectedThreadId != null,
        initialThemeMode = resolvedStartupTheme.mode,
        initialThemePalette = resolvedStartupTheme.palette
    )
    LaunchedEffect(observedRuntimeState.isTelemetryCollectionEnabled) {
        val enabled = observedRuntimeState.isTelemetryCollectionEnabled
        AnalyticsTracker.setQualityCollectionEnabled(enabled)
        PerformanceTracker.setCollectionEnabled(enabled)
        CrashReporter.setCollectionEnabled(enabled)
        CrashReporter.setKey("telemetry_enabled", analyticsEnabledValue(enabled))
    }
    FutachaTheme(
        themeMode = observedRuntimeState.themeMode,
        themePalette = observedRuntimeState.themePalette
    ) {
        val persistedLightweightMode by produceState<Boolean?>(
            initialValue = null,
            key1 = stateStore
        ) {
            stateStore.isLightweightModeEnabled
                .catch { error ->
                    if (error is CancellationException) throw error
                    Logger.e(TAG, "Failed to load lightweight mode preference", error)
                    value = devicePerformanceProfile.isLowSpec
                }
                .collect { enabled ->
                    value = enabled
                }
        }
        if (persistedLightweightMode == null) {
            LaunchedEffect(Unit) {
                AnalyticsTracker.screen("app_loading")
            }
            Surface(modifier = Modifier.fillMaxSize().analyticsGestureSurface()) {
                FutachaAppLockLoadingScreen()
            }
            return@FutachaTheme
        }
        val shouldUseLightweightMode = persistedLightweightMode == true || devicePerformanceProfile.isLowSpec
        val imageLoader = rememberFutachaImageLoader(
            lightweightMode = shouldUseLightweightMode,
            performanceProfile = devicePerformanceProfile,
            httpClient = httpClient
        )
        DisposableEffect(imageLoader) {
            onDispose {
                runCatching {
                    imageLoader.shutdown()
                }.onFailure { e ->
                    Logger.e("FutachaApp", "Failed to shutdown ImageLoader", e)
                }
            }
        }
        CompositionLocalProvider(
            LocalFutachaImageLoader provides imageLoader,
            LocalHistoryViewSettingsBinding provides HistoryViewSettingsBinding(
                settings = historyViewSettings,
                onSettingsChanged = { historyViewSettings = it }
            )
        ) {
            LaunchedEffect(platformContext, observedRuntimeState.appIconVariant) {
                applyAppIconVariant(
                    platformContext = platformContext,
                    variant = observedRuntimeState.appIconVariant
                )
            }
            Surface(modifier = Modifier.fillMaxSize().analyticsGestureSurface()) {
                val coroutineScope = rememberCoroutineScope()
                val saveableStateHolder = rememberSaveableStateHolder()

                LaunchedEffect(Unit) {
                    stateStore.setScrollDebounceScope(coroutineScope)
                }

                val coreRuntimeState = rememberFutachaCoreRuntimeState(
                    stateStore = stateStore,
                    httpClient = httpClient,
                    sharedRepository = sharedRepository,
                    sharedHistoryRefresher = sharedHistoryRefresher,
                    fileSystem = fileSystem,
                    cookieRepository = cookieRepository,
                    autoSavedThreadRepository = autoSavedThreadRepository,
                    shouldUseLightweightMode = shouldUseLightweightMode,
                    onRepositoryCloseFailure = { error ->
                        Logger.e(TAG, "Failed to close repository", error)
                    },
                    onHistoryRefresherCloseFailure = { error ->
                        Logger.e(TAG, "Failed to close history refresher", error)
                    }
                )
                val repositoryHolder = coreRuntimeState.repositoryHolder
                val effectiveAutoSavedThreadRepository = coreRuntimeState.effectiveAutoSavedThreadRepository
                val historyRefresher = coreRuntimeState.historyRefresher

                LaunchedEffect(stateStore, boardList, history) {
                    stateStore.seedIfEmpty(
                        AppStateSeedDefaults(
                            boards = boardList,
                            history = history,
                            selfPostIdentifierMap = emptyMap(),
                            catalogModeMap = emptyMap(),
                            lastUsedDeleteKey = ""
                        )
                    )
                }

                val persistedBoards = observedRuntimeState.persistedBoards
                val persistedHistory = observedRuntimeState.persistedHistory
                LaunchedEffect(
                    navigationState.selectedBoardId,
                    navigationState.selectedThreadId,
                    navigationState.selectedThreadUrl,
                    persistedBoards
                ) {
                    val currentUrl = navigationState.selectedThreadId
                        ?.let { threadId ->
                            navigationState.selectedThreadUrl
                                ?.takeIf(String::isNotBlank)
                                ?: persistedBoards.firstOrNull {
                                    it.id == navigationState.selectedBoardId
                                }?.url?.trimEnd('/')?.let { boardUrl ->
                                    "$boardUrl/res/$threadId.htm"
                                }
                        }
                    onCurrentThreadChanged(currentUrl)
                }
                var pendingCompatThreadDeepLink by remember { mutableStateOf<String?>(null) }
                var threadDeepLinkError by remember { mutableStateOf<String?>(null) }
                val profileController = LocalExperienceProfileUiController.current
                LaunchedEffect(platformThreadDeepLink, persistedBoards, persistedHistory) {
                    val raw = platformThreadDeepLink?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
                    when (val resolution = resolveFutachaThreadDeepLink(raw, persistedBoards, persistedHistory)) {
                        is FutachaThreadDeepLinkResolution.Open -> {
                            navigationState = applyFutachaThreadSelection(navigationState, resolution.selection)
                            onPlatformThreadDeepLinkConsumed(raw)
                        }
                        is FutachaThreadDeepLinkResolution.UnregisteredBoard -> {
                            pendingCompatThreadDeepLink = raw
                        }
                        FutachaThreadDeepLinkResolution.Invalid -> {
                            threadDeepLinkError = "スレッドURLを解釈できませんでした"
                            onPlatformThreadDeepLinkConsumed(raw)
                        }
                    }
                }
                LaunchedEffect(platformBoardDeepLink, persistedBoards) {
                    val raw = platformBoardDeepLink?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
                    val board = persistedBoards.firstOrNull { candidate ->
                        candidate.url.trimEnd('/').equals(raw.trimEnd('/'), ignoreCase = true)
                    }
                    if (board != null) {
                        navigationState = selectFutachaBoard(navigationState, board.id)
                    } else {
                        threadDeepLinkError = "板URLを解釈できませんでした"
                    }
                    onPlatformBoardDeepLinkConsumed(raw)
                }
                pendingCompatThreadDeepLink?.let { raw ->
                    AlertDialog(
                        onDismissRequest = {
                            pendingCompatThreadDeepLink = null
                            onPlatformThreadDeepLinkConsumed(raw)
                        },
                        title = { Text("としあき(仮)モードで開く") },
                        text = { Text("この板はふたちゃに登録されていません。としあき(仮)モードへ切り替えて開きますか？") },
                        confirmButton = {
                            TextButton(
                                enabled = !profileController.switchInProgress,
                                onClick = { profileController.requestSwitch(ExperienceProfile.TOSHIAKI_COMPAT) }
                            ) { Text("切り替えて開く") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                pendingCompatThreadDeepLink = null
                                onPlatformThreadDeepLinkConsumed(raw)
                            }) { Text("キャンセル") }
                        }
                    )
                }
                threadDeepLinkError?.let { message ->
                    AlertDialog(
                        onDismissRequest = { threadDeepLinkError = null },
                        title = { Text("URLを開けませんでした") },
                        text = { Text(message) },
                        confirmButton = { TextButton(onClick = { threadDeepLinkError = null }) { Text("OK") } }
                    )
                }
                LaunchedEffect(navigationState.selectedBoardId) {
                    if (navigationState.selectedBoardId == null) {
                        navigationState = clearFutachaThreadSelection(
                            state = navigationState,
                            clearBoardSelection = true
                        )
                    }
                }
                val updateNavigationState: (FutachaNavigationState) -> Unit = { navigationState = it }
                val destination = remember(navigationState, persistedBoards) {
                    resolveFutachaDestination(navigationState, persistedBoards)
                }
                LaunchedEffect(destination, persistedBoards.size, persistedHistory.size, shouldUseLightweightMode) {
                    recordFutachaDestinationScreenView(
                        destination = destination,
                        boardCount = persistedBoards.size,
                        historyCount = persistedHistory.size,
                        lightweightMode = shouldUseLightweightMode
                    )
                }
                val bindingsRuntimeState = rememberFutachaBindingsRuntimeState(
                    coroutineScope = coroutineScope,
                    stateStore = stateStore,
                    persistedBoards = persistedBoards,
                    persistedHistory = persistedHistory,
                    observedRuntimeState = observedRuntimeState,
                    shouldUseLightweightMode = shouldUseLightweightMode,
                    historyRefresher = historyRefresher,
                    effectiveAutoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                    fileSystem = fileSystem,
                    compatibilityStore = compatibilityStore,
                    navigationState = navigationState,
                    updateNavigationState = updateNavigationState,
                    onWatchAlertSettingChangeRequested = onWatchAlertSettingChangeRequested
                )
                val screenBindings = bindingsRuntimeState.screenBindings
                val aiImportedHistoryRepository = remember(fileSystem) {
                    buildImportedHistoryRepository(fileSystem)
                }
                val navigationRuntimeState = rememberFutachaNavigationRuntimeState(
                    navigationState = navigationState,
                    updateNavigationState = updateNavigationState,
                    destination = destination,
                    persistedBoards = persistedBoards,
                    activeSavedThreadsRepository = observedRuntimeState.activeSavedThreadsRepository,
                    screenBindings = screenBindings,
                    stateStore = stateStore,
                    sharedRepository = repositoryHolder.repository,
                    httpClient = httpClient,
                    fileSystem = fileSystem,
                    cookieRepository = cookieRepository,
                    autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                    compatibilityStore = compatibilityStore,
                    shouldUseLightweightMode = shouldUseLightweightMode,
                    coroutineScope = coroutineScope
                )
                val resolvedDestinationContent = navigationRuntimeState.resolvedDestinationContent
                var pendingAiConfirmation by remember { mutableStateOf<FutachaAiConfirmationRequest?>(null) }
                var pendingAiScreenCommand by remember { mutableStateOf<FutachaAiCommand?>(null) }
                var aiResultMessage by remember { mutableStateOf<String?>(null) }
                var isAiGlobalSettingsVisible by remember { mutableStateOf(false) }
                var aiFileManagerPickerRequest by remember { mutableStateOf(0) }
                var isAiHistoryRefreshCommandRunning by remember { mutableStateOf(false) }
                val handledAiCommandIds = remember { LinkedHashSet<String>() }
                val onAiScreenCommandConsumed: (FutachaAiCommand) -> Unit = { consumedCommand ->
                    if (pendingAiScreenCommand == consumedCommand) {
                        pendingAiScreenCommand = null
                    }
                }

                suspend fun handleAiOutcome(
                    outcome: FutachaAiCommandOutcome,
                    suppressResultDialog: Boolean = false
                ) {
                    when (outcome) {
                        is FutachaAiCommandOutcome.Completed -> {
                            if (!suppressResultDialog) {
                                aiResultMessage = outcome.message
                            }
                        }
                        is FutachaAiCommandOutcome.Failed -> {
                            aiResultMessage = outcome.message
                        }
                        is FutachaAiCommandOutcome.NeedsConfirmation -> {
                            if (shouldReplacePendingAiConfirmation(pendingAiConfirmation, outcome)) {
                                pendingAiConfirmation = outcome.request
                            }
                        }
                        is FutachaAiCommandOutcome.NeedsForeground -> {
                            if (!suppressResultDialog) {
                                aiResultMessage = outcome.message
                            }
                        }
                    }
                }

                val currentAiRouterInputs by rememberUpdatedState(
                    FutachaAiRouterInputs(
                        stateStore = stateStore,
                        boards = persistedBoards,
                        history = persistedHistory,
                        navigationState = navigationState,
                        updateNavigationState = updateNavigationState,
                        historyRefresher = historyRefresher,
                        savedThreadRepository = observedRuntimeState.activeSavedThreadsRepository,
                        autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                        isCookieManagementAvailable = cookieRepository != null,
                        appVersion = observedRuntimeState.appVersion,
                        isAiCommandEnabled = observedRuntimeState.isAiCommandEnabled,
                        compatibilityStore = compatibilityStore,
                        importedHistoryRepository = aiImportedHistoryRepository
                    )
                )
                val currentHandleAiCommand by rememberUpdatedState<suspend (FutachaAiCommand) -> Unit> { command ->
                    if (handledAiCommandIds.isDuplicateAiCommand(command)) {
                        return@rememberUpdatedState
                    }
                    AnalyticsTracker.event(
                        "ai_command_received",
                        mapOf(
                            "action" to command.action.id,
                            "source" to command.source
                        )
                    )
                    val outcome = try {
                        executeFutachaAiCommand(
                            command = command,
                            inputs = currentAiRouterInputs
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (error: Throwable) {
                        Logger.e(TAG, "AI command failed: ${command.action}", error)
                        FutachaAiCommandOutcome.Failed(buildAiCommandUnexpectedFailureMessage(error))
                    }
                    if (shouldOpenAiGlobalSettings(command, outcome)) {
                        isAiGlobalSettingsVisible = true
                        if (shouldRequestAiFileManagerPicker(command, outcome)) {
                            aiFileManagerPickerRequest += 1
                        }
                    }
                    val shouldForward = shouldForwardAiCommandToScreen(command, outcome)
                    if (shouldForward) {
                        pendingAiScreenCommand = resolvePendingAiScreenCommand(
                            current = pendingAiScreenCommand,
                            incoming = command
                        )
                    }
                    AnalyticsTracker.event(
                        "ai_command_result",
                        mapOf(
                            "action" to command.action.id,
                            "source" to command.source,
                            "outcome" to outcome.analyticsName(),
                            "forwarded_to_screen" to analyticsEnabledValue(shouldForward)
                        )
                    )
                    handleAiOutcome(outcome, suppressResultDialog = shouldForward)
                }

                LaunchedEffect(platformAiDeepLink) {
                    val rawDeepLink = platformAiDeepLink?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
                    val command = parseFutachaAiDeepLink(rawDeepLink, source = "platform")
                    onPlatformAiDeepLinkConsumed(rawDeepLink)
                    if (command == null) {
                        aiResultMessage = "AI操作のURLを解釈できませんでした"
                        return@LaunchedEffect
                    }
                    currentHandleAiCommand(command)
                }

                LaunchedEffect(platformAiCommand) {
                    val command = platformAiCommand ?: return@LaunchedEffect
                    currentHandleAiCommand(command)
                    onPlatformAiCommandConsumed(command)
                }

                if (consumeAiCommandBridge) {
                    LaunchedEffect(Unit) {
                        FutachaAiCommandBridge.commands.collect { command ->
                            if (shouldLaunchAiCommandFromBridge(command)) {
                                if (!shouldStartAiBridgeCommand(command, isAiHistoryRefreshCommandRunning)) {
                                    return@collect
                                }
                                isAiHistoryRefreshCommandRunning = true
                                launch {
                                    try {
                                        currentHandleAiCommand(command)
                                    } finally {
                                        isAiHistoryRefreshCommandRunning = false
                                    }
                                }
                            } else {
                                currentHandleAiCommand(command)
                            }
                        }
                    }
                }

                when (val content = resolvedDestinationContent) {
                    is FutachaResolvedDestinationContent.SavedThreads -> {
                        FutachaSavedThreadsDestination(
                            props = content.props,
                            onUnavailable = content.onUnavailable
                        )
                    }

                    is FutachaResolvedDestinationContent.BoardManagement -> {
                        FutachaBoardManagementDestination(
                            props = content.props,
                            aiCommand = pendingAiScreenCommand,
                            onAiCommandConsumed = onAiScreenCommandConsumed
                        )
                    }

                    is FutachaResolvedDestinationContent.MissingBoard -> {
                        FutachaMissingBoardDestination(
                            missingBoardId = content.missingBoardId,
                            navigationState = content.navigationState,
                            boards = content.boards,
                            onRecovered = content.onRecovered
                        )
                    }

                    is FutachaResolvedDestinationContent.Catalog -> {
                        FutachaCatalogDestination(
                            props = content.props,
                            saveableStateHolder = saveableStateHolder,
                            aiCommand = pendingAiScreenCommand,
                            onAiCommandConsumed = onAiScreenCommandConsumed
                        )
                    }

                    is FutachaResolvedDestinationContent.Thread -> {
                        FutachaThreadDestination(
                            props = content.props,
                            aiCommand = pendingAiScreenCommand,
                            onAiCommandConsumed = onAiScreenCommandConsumed
                        )
                    }
                }

                pendingAiConfirmation?.let { request ->
                    fun dismissAiConfirmation() {
                        AnalyticsTracker.uiControl("ai_confirmation", "AI操作の確認をキャンセル")
                        pendingAiConfirmation = null
                        aiResultMessage = "AI操作をキャンセルしました"
                    }
                    AlertDialog(
                        onDismissRequest = ::dismissAiConfirmation,
                        title = { Text(request.title) },
                        text = { Text(request.message) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    AnalyticsTracker.uiControl("ai_confirmation", "AI操作の確認を実行")
                                    val confirmedRequest = request
                                    pendingAiConfirmation = null
                                    coroutineScope.launch {
                                        val outcome = try {
                                            executeFutachaAiCommand(
                                                command = confirmedRequest.command,
                                                inputs = FutachaAiRouterInputs(
                                                    stateStore = stateStore,
                                                    boards = persistedBoards,
                                                    history = persistedHistory,
                                                    navigationState = navigationState,
                                                    updateNavigationState = updateNavigationState,
                                                    historyRefresher = historyRefresher,
                                                    savedThreadRepository = observedRuntimeState.activeSavedThreadsRepository,
                                                    autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                                                    isCookieManagementAvailable = cookieRepository != null,
                                                    appVersion = observedRuntimeState.appVersion,
                                                    isAiCommandEnabled = observedRuntimeState.isAiCommandEnabled,
                                                    compatibilityStore = compatibilityStore,
                                                    importedHistoryRepository = aiImportedHistoryRepository
                                                ),
                                                confirmed = true
                                            )
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (error: Throwable) {
                                            Logger.e(
                                                TAG,
                                                "Confirmed AI command failed: ${confirmedRequest.command.action}",
                                                error
                                            )
                                            FutachaAiCommandOutcome.Failed(
                                                buildAiCommandUnexpectedFailureMessage(error)
                                            )
                                        }
                                        val shouldForward = shouldForwardAiCommandToScreen(
                                            confirmedRequest.command,
                                            outcome
                                        )
                                        if (shouldForward) {
                                            pendingAiScreenCommand = resolvePendingAiScreenCommand(
                                                current = pendingAiScreenCommand,
                                                incoming = confirmedRequest.command
                                            )
                                        }
                                        handleAiOutcome(outcome, suppressResultDialog = shouldForward)
                                    }
                                }
                            ) {
                                Text(request.confirmLabel)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = ::dismissAiConfirmation) {
                                Text(request.dismissLabel)
                            }
                        }
                    )
                }

                aiResultMessage?.let { message ->
                    AlertDialog(
                        onDismissRequest = {
                            AnalyticsTracker.uiControl("ai_result", "AI操作の結果を閉じる")
                            aiResultMessage = null
                        },
                        title = { Text("AI操作") },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = {
                                AnalyticsTracker.uiControl("ai_result", "AI操作の結果を確認")
                                aiResultMessage = null
                            }) {
                                Text("OK")
                            }
                        }
                    )
                }

                if (isAiGlobalSettingsVisible) {
                    GlobalSettingsScreen(
                        onBack = { isAiGlobalSettingsVisible = false },
                        preferencesState = screenBindings.screenPreferencesState,
                        preferencesCallbacks = screenBindings.screenPreferencesCallbacks,
                        historyEntries = persistedHistory,
                        fileSystem = fileSystem,
                        autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                        openFileManagerPickerRequest = aiFileManagerPickerRequest
                    )
                }
            }
        }
    }
}

private fun recordFutachaDestinationScreenView(
    destination: FutachaDestination,
    boardCount: Int,
    historyCount: Int,
    lightweightMode: Boolean
) {
    val params = mutableMapOf(
        "board_count_bucket" to analyticsCountBucket(boardCount),
        "history_count_bucket" to analyticsCountBucket(historyCount),
        "lightweight_mode" to analyticsEnabledValue(lightweightMode)
    )
    val screenName = when (destination) {
        FutachaDestination.BoardManagement -> "board_management"
        FutachaDestination.SavedThreads -> "saved_threads"
        is FutachaDestination.MissingBoard -> "missing_board"
        is FutachaDestination.Catalog -> {
            params["board_kind"] = analyticsBoardKind(destination.board.url)
            params["board_context"] = analyticsSessionContextId(
                "board",
                destination.board.id,
                destination.board.url
            )
            params["board_name_length_bucket"] = analyticsTextLengthBucket(destination.board.name)
            params["board_name_has_url"] = analyticsTextHasUrl(destination.board.name)
            "catalog"
        }
        is FutachaDestination.Thread -> {
            params["board_kind"] = analyticsBoardKind(destination.board.url)
            params["thread_present"] = analyticsPresentValue(destination.threadId)
            params["board_context"] = analyticsSessionContextId(
                "board",
                destination.board.id,
                destination.board.url
            )
            params["thread_context"] = analyticsSessionContextId(
                "thread",
                destination.board.url,
                destination.threadId
            )
            "thread"
        }
    }
    AnalyticsTracker.screen(screenName, params)
}

private fun FutachaAiCommandOutcome.analyticsName(): String {
    return when (this) {
        is FutachaAiCommandOutcome.Completed -> "completed"
        is FutachaAiCommandOutcome.Failed -> "failed"
        is FutachaAiCommandOutcome.NeedsConfirmation -> "needs_confirmation"
        is FutachaAiCommandOutcome.NeedsForeground -> "needs_foreground"
    }
}

internal fun shouldForwardAiCommandToScreen(
    command: FutachaAiCommand,
    outcome: FutachaAiCommandOutcome
): Boolean {
    if (outcome is FutachaAiCommandOutcome.Failed ||
        outcome is FutachaAiCommandOutcome.NeedsConfirmation
    ) {
        return false
    }
    return when (command.action) {
        FutachaAiAction.RefreshCurrentBoard,
        FutachaAiAction.RefreshCatalog,
        FutachaAiAction.OpenHistoryDrawer,
        FutachaAiAction.RefreshCurrentThread,
        FutachaAiAction.ScrollThreadToTop,
        FutachaAiAction.ScrollThreadToBottom,
        FutachaAiAction.StartThreadReadAloud,
        FutachaAiAction.PauseThreadReadAloud,
        FutachaAiAction.StopThreadReadAloud,
        FutachaAiAction.NextThreadReadAloud,
        FutachaAiAction.PreviousThreadReadAloud,
        FutachaAiAction.ScrollCatalogToTop,
        FutachaAiAction.StartCatalogSearch,
        FutachaAiAction.SearchCatalog,
        FutachaAiAction.StartThreadSearch,
        FutachaAiAction.SearchThread,
        FutachaAiAction.NextSearchResult,
        FutachaAiAction.PreviousSearchResult,
        FutachaAiAction.OpenGallery,
        FutachaAiAction.OpenCatalogSettings,
        FutachaAiAction.OpenThreadSettings,
        FutachaAiAction.OpenCookieManagement,
        FutachaAiAction.OpenCatalogDisplaySettings,
        FutachaAiAction.OpenNgManagement,
        FutachaAiAction.OpenWatchWords,
        FutachaAiAction.OpenBoardExternally,
        FutachaAiAction.OpenThreadExternally,
        FutachaAiAction.SaveCurrentThread,
        FutachaAiAction.SaveThread,
        FutachaAiAction.DraftReply,
        FutachaAiAction.DraftThread -> true
        else -> false
    }
}

internal fun shouldOpenAiGlobalSettings(
    command: FutachaAiCommand,
    outcome: FutachaAiCommandOutcome
): Boolean {
    if (outcome is FutachaAiCommandOutcome.Failed ||
        outcome is FutachaAiCommandOutcome.NeedsConfirmation
    ) {
        return false
    }
    return when (command.action) {
        FutachaAiAction.OpenGlobalSettings,
        FutachaAiAction.OpenVersionInfo,
        FutachaAiAction.OpenFileManagerSettings -> true
        else -> false
    }
}

internal fun shouldRequestAiFileManagerPicker(
    command: FutachaAiCommand,
    outcome: FutachaAiCommandOutcome
): Boolean {
    return command.action == FutachaAiAction.OpenFileManagerSettings &&
        shouldOpenAiGlobalSettings(command, outcome)
}

internal fun shouldLaunchAiCommandFromBridge(command: FutachaAiCommand): Boolean {
    return command.action == FutachaAiAction.RefreshHistory
}

private fun buildAiCommandUnexpectedFailureMessage(error: Throwable): String {
    val detail = error.message?.takeIf { it.isNotBlank() }
    return if (detail == null) {
        "AI操作に失敗しました"
    } else {
        "AI操作に失敗しました: $detail"
    }
}

private fun LinkedHashSet<String>.isDuplicateAiCommand(command: FutachaAiCommand): Boolean {
    val commandId = command.parameters["commandId"]
        ?.takeIf { it.isNotBlank() && it.encodeToByteArray().size <= AI_COMMAND_ID_MAX_BYTES }
        ?: return false
    if (!add(commandId)) {
        return true
    }
    while (size > AI_HANDLED_COMMAND_ID_MAX_COUNT) {
        val oldestCommandId = firstOrNull() ?: break
        remove(oldestCommandId)
    }
    return false
}
