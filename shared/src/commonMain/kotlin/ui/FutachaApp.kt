package com.valoser.futacha.shared.ui

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

import com.valoser.futacha.shared.ui.compat.compatManualSaveLocation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandArrivals
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.ai.FutachaAiCommandOutcome
import com.valoser.futacha.shared.ai.isWatchRelayLink
import com.valoser.futacha.shared.ai.FutachaAiConfirmationRequest
import com.valoser.futacha.shared.ai.FutachaAiQueuedCommand
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
import com.valoser.futacha.shared.ui.image.ConfigureOriginalMediaCache
import com.valoser.futacha.shared.ui.image.LocalOriginalMediaSource
import com.valoser.futacha.shared.ui.image.CompatibilityCacheLocation
import com.valoser.futacha.shared.ui.image.splitImageDiskBudget
import com.valoser.futacha.shared.media.source.OriginalMediaSession
import com.valoser.futacha.shared.media.MediaFeatureSettings
import com.valoser.futacha.shared.media.MediaFeatureGate
import com.valoser.futacha.shared.ui.image.LocalMediaFeatureSettings
import com.valoser.futacha.shared.ui.image.LocalMediaFeatureUpdater
import com.valoser.futacha.shared.media.prompt.PromptMediaSource
import com.valoser.futacha.shared.ui.image.LocalMediaFeatureGate
import com.valoser.futacha.shared.ui.theme.FutachaTheme
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
import kotlin.time.TimeSource

private const val TAG = "FutachaApp"
private const val APP_LOCK_HASH_LOADING = "__futacha_app_lock_loading__"
private const val APP_LOCK_HASH_ERROR = "__futacha_app_lock_error__"
private const val AI_COMMAND_ID_MAX_BYTES = 128
private const val AI_HANDLED_COMMAND_ID_MAX_COUNT = 128
private const val LIGHTWEIGHT_SHARED_IMAGE_CACHE_BYTES = 128L * 1024 * 1024

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
    imageTransport: com.valoser.futacha.shared.network.FutachaImageTransport? = null,
    originalMediaSession: OriginalMediaSession? = imageTransport?.originalMediaSession,
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
    onExitApplication: () -> Unit = {},
    /**
     * Reports whether the app content is visible (lock state resolved and
     * unlocked). iOS uses it to keep its native saved-HTML sheet, which is
     * presented outside Compose, behind the app lock (H7).
     */
    onAppUnlockedChanged: (Boolean) -> Unit = {}
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
    // Read the app-lock state concurrently with the theme; reading it only after
    // the theme arrived added a frame (and a spinner) to every cold start.
    // A read failure fails closed with a retry button instead of leaving the
    // loading spinner on screen forever.
    var appLockLoadAttempt by remember { mutableStateOf(0) }
    val startupAppLockHash by produceState<String?>(
        initialValue = APP_LOCK_HASH_LOADING,
        key1 = stateStore,
        key2 = appLockLoadAttempt
    ) {
        value = APP_LOCK_HASH_LOADING
        stateStore.appLockPasswordHash
            .catch { error ->
                if (error is CancellationException) throw error
                Logger.e(TAG, "Failed to load app lock password hash", error)
                value = APP_LOCK_HASH_ERROR
            }
            .collect { storedHash ->
                value = storedHash
            }
    }
    // The session flag lives outside composition: Android pauses
    // recomposition after ON_STOP, so command handlers read the holder (C-1).
    val appLockHolder = remember { FutachaAppLockHolder() }
    val isUnlockedForSession by appLockHolder.sessionUnlocked.collectAsState()
    LaunchedEffect(startupAppLockHash) {
        if (startupAppLockHash == null) {
            appLockHolder.openSessionWithoutLock()
        }
    }
    // Once the app tree has been shown it stays composed while relocked: tearing
    // it down on every ON_STOP (which Android also sends for the app's own image
    // and folder pickers) discarded drafts, attachments, picker results and the
    // open thread.  The lock is drawn as an opaque, input-blocking overlay.
    val appTreeShown = remember { mutableStateOf(false) }
    val appLockGate = resolveFutachaAppLockGate(
        storedHash = startupAppLockHash,
        isUnlockedForSession = isUnlockedForSession,
        loadingSentinel = APP_LOCK_HASH_LOADING,
        errorSentinel = APP_LOCK_HASH_ERROR
    )
    val currentOnAppUnlockedChanged by rememberUpdatedState(onAppUnlockedChanged)
    val isAppUnlocked = startupTheme != null && appLockGate == FutachaAppLockGate.Unlocked
    SideEffect { appLockHolder.setContentVisible(isAppUnlocked) }
    DisposableEffect(isAppUnlocked) {
        currentOnAppUnlockedChanged(isAppUnlocked)
        onDispose { if (isAppUnlocked) currentOnAppUnlockedChanged(false) }
    }
    if (startupTheme == null) return
    val resolvedStartupTheme = startupTheme ?: return
    if (!appTreeShown.value && appLockGate != FutachaAppLockGate.Unlocked) {
        // Compatibility owns a separate persisted palette. Painting the
        // modern loading surface before that palette is available produces a
        // clearly visible Futacha/classic flash during every cold start (#53).
        // Keep the preview window untouched until both the lock state and the
        // compatibility preferences can be resolved.
        if (appLockGate == FutachaAppLockGate.Loading &&
            experienceProfile == ExperienceProfile.TOSHIAKI_COMPAT
        ) return
        FutachaTheme(
            themeMode = resolvedStartupTheme.mode,
            themePalette = resolvedStartupTheme.palette
        ) {
            Surface(modifier = Modifier.fillMaxSize().analyticsGestureSurface()) {
                FutachaAppLockGateContent(
                    gate = appLockGate,
                    passwordHash = startupAppLockHash.orEmpty(),
                    onUnlocked = appLockHolder::unlockSession,
                    onRetry = { appLockLoadAttempt += 1 }
                )
            }
        }
        return
    }
    if (!appTreeShown.value) {
        SideEffect { appTreeShown.value = true }
    }
    // LOADING/ERROR sentinels are non-null too, so this stays registered
    // while the lock state is being re-read.
    if (startupAppLockHash != null) {
        PlatformBackgroundLifecycleEffect {
            // Runs synchronously in ON_STOP / didEnterBackground, before any
            // command delivered in the background can read the lock state.
            appLockHolder.lockSession()
        }
    }
    val isAppHidden = appLockGate != FutachaAppLockGate.Unlocked
    Box(modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isAppHidden) Modifier.clearAndSetSemantics { } else Modifier)
    ) {
        CompositionLocalProvider(
            LocalFutachaAppUnlocked provides !isAppHidden,
            LocalFutachaAppLockHolder provides appLockHolder
        ) {
        FutachaAppContent(
            stateStore = stateStore,
            boardList = boardList,
            history = history,
            versionChecker = versionChecker,
            httpClient = httpClient,
            imageTransport = imageTransport,
            originalMediaSession = originalMediaSession,
            sharedRepository = sharedRepository,
            sharedHistoryRefresher = sharedHistoryRefresher,
            fileSystem = fileSystem,
            cookieRepository = cookieRepository,
            autoSavedThreadRepository = autoSavedThreadRepository,
            compatibilityHistoryRefresh = compatibilityHistoryRefresh,
            platformAiDeepLink = platformAiDeepLink,
            onPlatformAiDeepLinkConsumed = onPlatformAiDeepLinkConsumed,
            platformAiCommand = platformAiCommand,
            onPlatformAiCommandConsumed = onPlatformAiCommandConsumed,
            consumeAiCommandBridge = consumeAiCommandBridge,
            platformThreadDeepLink = platformThreadDeepLink,
            platformThreadDeepLinkPreapprovedBoardRegistration = platformThreadDeepLinkPreapprovedBoardRegistration,
            onPlatformThreadDeepLinkConsumed = onPlatformThreadDeepLinkConsumed,
            platformBoardDeepLink = platformBoardDeepLink,
            onPlatformBoardDeepLinkConsumed = onPlatformBoardDeepLinkConsumed,
            onWatchAlertSettingChangeRequested = onWatchAlertSettingChangeRequested,
            onArchiveReportEnqueued = onArchiveReportEnqueued,
            onArchiveReportEnabledChanged = onArchiveReportEnabledChanged,
            onCurrentThreadChanged = onCurrentThreadChanged,
            experienceProfile = experienceProfile,
            compatibilityStore = compatibilityStore,
            onExitApplication = onExitApplication,
            platformContext = platformContext,
            devicePerformanceProfile = devicePerformanceProfile,
            resolvedStartupTheme = resolvedStartupTheme
        )
        // Reclaims ZIP staging files left by a save that was killed (E-5).
        fileSystem?.let { fs ->
            LaunchedEffect(fs) { com.valoser.futacha.shared.service.sweepStaleZipStagingFiles(fs) }
        }
        }
    }
    if (isAppHidden) {
        FutachaTheme(
            themeMode = resolvedStartupTheme.mode,
            themePalette = resolvedStartupTheme.palette
        ) {
            FutachaAppLockOverlay {
                FutachaAppLockGateContent(
                    gate = appLockGate,
                    passwordHash = startupAppLockHash.orEmpty(),
                    onUnlocked = appLockHolder::unlockSession,
                    onRetry = { appLockLoadAttempt += 1 }
                )
            }
        }
    }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun FutachaAppContent(
    stateStore: AppStateStore,
    boardList: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    versionChecker: VersionChecker?,
    httpClient: io.ktor.client.HttpClient?,
    imageTransport: com.valoser.futacha.shared.network.FutachaImageTransport?,
    originalMediaSession: OriginalMediaSession?,
    sharedRepository: BoardRepository?,
    sharedHistoryRefresher: HistoryRefresher?,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?,
    cookieRepository: CookieRepository?,
    autoSavedThreadRepository: SavedThreadRepository?,
    compatibilityHistoryRefresh: (suspend () -> Result<String>)?,
    platformAiDeepLink: String?,
    onPlatformAiDeepLinkConsumed: (String) -> Unit,
    platformAiCommand: FutachaAiCommand?,
    onPlatformAiCommandConsumed: (FutachaAiCommand) -> Unit,
    consumeAiCommandBridge: Boolean,
    platformThreadDeepLink: String?,
    platformThreadDeepLinkPreapprovedBoardRegistration: Boolean,
    onPlatformThreadDeepLinkConsumed: (String) -> Unit,
    platformBoardDeepLink: String?,
    onPlatformBoardDeepLinkConsumed: (String) -> Unit,
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)?,
    onArchiveReportEnqueued: (Int) -> Unit,
    onArchiveReportEnabledChanged: (Boolean) -> Unit,
    onCurrentThreadChanged: (String?) -> Unit,
    experienceProfile: ExperienceProfile,
    compatibilityStore: CompatibilityStore?,
    onExitApplication: () -> Unit,
    platformContext: coil3.PlatformContext,
    devicePerformanceProfile: DevicePerformanceProfile,
    resolvedStartupTheme: FutachaStartupTheme
) {
    val isAppUnlocked = LocalFutachaAppUnlocked.current
    val appLock = LocalFutachaAppLockHolder.current ?: remember {
        FutachaAppLockHolder().apply {
            openSessionWithoutLock()
            setContentVisible(true)
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
    updateInfo?.takeIf { isAppUnlocked }?.let { info ->
        LaunchedEffect(info, versionChecker) { versionChecker?.onUpdateShown(info) }
        UpdateNotificationDialog(
            updateInfo = info,
            onDismiss = { updateInfo = null }
        )
    }

    val promptPrivacyEnabled by stateStore.isPrivacyFilterEnabled.collectAsState(true)
    val mediaFeatureSettings by stateStore.mediaFeatureSettings.collectAsState(MediaFeatureSettings.Disabled)
    val mediaFeatureGate = remember(stateStore) { MediaFeatureGate() }
    SideEffect { mediaFeatureGate.update(mediaFeatureSettings) }
    val analysisModels = remember(mediaFeatureGate, platformContext) {
        com.valoser.futacha.shared.media.analysis.ModelStore(mediaFeatureGate,
            directory = { com.valoser.futacha.shared.media.analysis.modelStoreDirectory(platformContext) },
            downloader = { com.valoser.futacha.shared.media.analysis.KtorModelDownloader() })
    }
    DisposableEffect(analysisModels) { onDispose { analysisModels.close() } }
    val promptMediaSource = remember(originalMediaSession, mediaFeatureGate, platformContext) {
        originalMediaSession?.let { PromptMediaSource(it, mediaFeatureGate,
            readLocalMetadata = { url -> com.valoser.futacha.shared.media.prompt.readLocalGenerationMetadata(url, platformContext) }) }
    }
    DisposableEffect(promptMediaSource) { onDispose { promptMediaSource?.close() } }
    val updateMediaFeatures: suspend ((MediaFeatureSettings) -> MediaFeatureSettings) -> Unit = { change ->
        stateStore.updateMediaFeatureSettings(change)
        mediaFeatureGate.update(stateStore.mediaFeatureSettings.first())
    }
    DisposableEffect(mediaFeatureGate) {
        onDispose { mediaFeatureGate.update(MediaFeatureSettings.Disabled) }
    }

    if (experienceProfile == ExperienceProfile.TOSHIAKI_COMPAT && compatibilityStore != null) {
        // The compatibility thread menu saves into the manual-save location,
        // so its saved-thread index must not point at the background auto-save
        // directory used by the modern history refresher.
        // Build the image loaders from the stored settings, not from the
        // placeholder shown before the store has loaded (they were rebuilt).
        val loadedCompatibilityPreferences by remember(compatibilityStore) {
            kotlinx.coroutines.flow.flow {
                compatibilityStore.isLoaded.first { it }
                compatibilityStore.preferences.collect { emit(it) }
            }
        }.collectAsState<Map<String, String>, Map<String, String>?>(null)
        val compatibilityPreferences = loadedCompatibilityPreferences ?: return
        val compatibilitySaveLocation = compatibilityPreferences.compatManualSaveLocation()
        val compatibilitySavedThreadRepository = remember(fileSystem, compatibilitySaveLocation) {
            fileSystem?.let {
                com.valoser.futacha.shared.ui.compat.createCompatSavedThreadRepository(it, compatibilitySaveLocation)
            }
        }
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
        val diskBudget = splitImageDiskBudget(compatibilityImageCacheBytes)
        ConfigureOriginalMediaCache(originalMediaSession, platformContext, diskBudget.originals, compatibilityCacheLocation)
        val compatibilityImageLoader = rememberFutachaImageLoader(
            lightweightMode = devicePerformanceProfile.isLowSpec,
            performanceProfile = devicePerformanceProfile,
            httpClient = httpClient,
            imageTransport = imageTransport,
            diskCacheBytesOverride = if (originalMediaSession == null) compatibilityImageCacheBytes else diskBudget.images,
            originalMediaStore = promptMediaSource,
            cacheLocation = compatibilityCacheLocation,
            parallelismOverride = compatibilityImageParallelism
        )
        val compatibilityCatalogImageLoader = rememberFutachaImageLoader(
            lightweightMode = devicePerformanceProfile.isLowSpec,
            performanceProfile = devicePerformanceProfile,
            httpClient = httpClient,
            imageTransport = imageTransport,
            originalMediaStore = promptMediaSource,
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
        val highQualityThumbnailMode = com.valoser.futacha.shared.ui.image.rememberHighQualityThumbnailMode(compatibilityStore)
        // C-3: on Android nothing else consumes the AI bridge in this mode, so
        // watch/AppFunctions commands piled up and replayed after a switch. This
        // branch returns before the modern collector, so only one is active.
        // Received only while unlocked; each keeps its arrival time so one that
        // waited behind a relock is dropped like in the modern branch (C-1).
        var compatBridgeQueue by remember { mutableStateOf<List<FutachaAiQueuedCommand>>(emptyList()) }
        if (consumeAiCommandBridge) {
            LaunchedEffect(Unit) {
                while (true) {
                    appLock.awaitUnlocked()
                    val queued = FutachaAiCommandBridge.receiveQueued()
                    compatBridgeQueue = enqueuePlatformAiCommand(compatBridgeQueue, queued)
                }
            }
        }
        val compatBridgeHead = compatBridgeQueue.firstOrNull()
        LaunchedEffect(compatBridgeHead, isAppUnlocked) {
            val head = compatBridgeHead ?: return@LaunchedEffect
            if (!isAppUnlocked) return@LaunchedEffect
            appLock.awaitUnlocked()
            val decision = resolveAiCommandHoldDecision(
                ageMillis = head.ageMillis(),
                maxAgeMillis = head.maxAgeMillis,
                wasHeldByLock = appLock.wasHeldByLock(head.enqueuedAt)
            )
            if (decision != AiCommandHoldDecision.Run) {
                Logger.w(TAG, "Dropped held AI command in compatibility mode: ${head.command.action.id}")
                compatBridgeQueue = consumePlatformAiCommand(compatBridgeQueue, head)
            }
        }
        val compatPlatformAiCommand = platformAiCommand ?: compatBridgeHead
            ?.takeIf {
                resolveAiCommandHoldDecision(
                    ageMillis = it.ageMillis(),
                    maxAgeMillis = it.maxAgeMillis,
                    wasHeldByLock = appLock.wasHeldByLock(it.enqueuedAt)
                ) == AiCommandHoldDecision.Run
            }
            ?.command
        CompositionLocalProvider(
            LocalFutachaImageLoader provides compatibilityImageLoader,
            com.valoser.futacha.shared.ui.image.LocalHighQualityThumbnailMode provides highQualityThumbnailMode,
            LocalOriginalMediaSource provides promptMediaSource,
            LocalMediaFeatureSettings provides mediaFeatureSettings,
            LocalMediaFeatureUpdater provides updateMediaFeatures,
            com.valoser.futacha.shared.ui.image.LocalPromptContentVisible provides !promptPrivacyEnabled,
            LocalMediaFeatureGate provides mediaFeatureGate,
            com.valoser.futacha.shared.ui.media.LocalAnalysisModelStore provides analysisModels
        ) {
            com.valoser.futacha.shared.ui.media.DeviceImageEditingHost(fileSystem, stateStore) {
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
                initialThreadDeepLink = platformThreadDeepLink.takeIf { isAppUnlocked },
                initialThreadDeepLinkPreapprovedBoardRegistration =
                    platformThreadDeepLinkPreapprovedBoardRegistration,
                onThreadDeepLinkConsumed = onPlatformThreadDeepLinkConsumed,
                initialBoardDeepLink = platformBoardDeepLink.takeIf { isAppUnlocked },
                onBoardDeepLinkConsumed = onPlatformBoardDeepLinkConsumed,
                // Android hands `futacha://ai` links over as links; they were
                // never consumed in this mode (C4-4).
                platformAiDeepLink = platformAiDeepLink,
                onPlatformAiDeepLinkConsumed = onPlatformAiDeepLinkConsumed,
                // Passed while locked too: CompatibilityApp records the arrival,
                // waits for the unlock itself and applies the 60 s lock expiry.
                platformAiCommand = compatPlatformAiCommand,
                onPlatformAiCommandConsumed = { consumed ->
                    if (consumed === platformAiCommand) {
                        onPlatformAiCommandConsumed(consumed)
                    } else {
                        val head = compatBridgeQueue.firstOrNull()
                        if (head != null && head.command === consumed) {
                            compatBridgeQueue = consumePlatformAiCommand(compatBridgeQueue, head)
                        }
                    }
                },
                onArchiveReportEnqueued = onArchiveReportEnqueued,
                onArchiveReportEnabledChanged = onArchiveReportEnabledChanged,
                onExitApplication = onExitApplication
            )
            }
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
        // Notices share the active screen's theme. A separate theme host also
        // paints system bars, even when the notice itself has nothing to show.
        SettingsRecoveryNoticeDialog()
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
        // Wait for the stored image settings as well: loaders built from the
        // placeholder before the store has loaded were rebuilt right after.
        val imagePreferences = remember(compatibilityStore) {
            (compatibilityStore?.let { store ->
                kotlinx.coroutines.flow.flow {
                    store.isLoaded.first { it }
                    store.preferences.collect { emit(it) }
                }
            } ?: kotlinx.coroutines.flow.flowOf(emptyMap<String, String>()))
                .map { preferences -> preferences.filterKeys { it in setOf(
                    COMPAT_IMAGE_CACHE_PREFERENCE_KEY, COMPAT_IMAGE_CACHE_LOCATION_PREFERENCE_KEY,
                    COMPAT_IMAGE_PARALLEL_PREFERENCE_KEY, COMPAT_CATALOG_IMAGE_CACHE_PREFERENCE_KEY,
                    COMPAT_CATALOG_IMAGE_CACHE_LOCATION_PREFERENCE_KEY
                ) } }.distinctUntilChanged()
        }
        val loadedSharedFeaturePreferences by imagePreferences.collectAsState<Map<String, String>, Map<String, String>?>(
            if (compatibilityStore == null) emptyMap() else null
        )
        val sharedFeaturePreferences = loadedSharedFeaturePreferences
        if (persistedLightweightMode == null || sharedFeaturePreferences == null) {
            LaunchedEffect(Unit) {
                AnalyticsTracker.screen("app_loading")
            }
            Surface(modifier = Modifier.fillMaxSize().analyticsGestureSurface()) {
                FutachaAppLockLoadingScreen()
            }
            return@FutachaTheme
        }
        val shouldUseLightweightMode = persistedLightweightMode == true || devicePerformanceProfile.isLowSpec
        // An explicit quota wins; otherwise Lightweight mode keeps its smaller budget.
        val sharedImageCacheBytes = sharedFeaturePreferences[COMPAT_IMAGE_CACHE_PREFERENCE_KEY]
            ?.let(::parseCompatImageCacheQuotaBytes)
            ?: if (shouldUseLightweightMode) LIGHTWEIGHT_SHARED_IMAGE_CACHE_BYTES else parseCompatImageCacheQuotaBytes(null)
        val sharedCacheLocation = parseCompatCacheLocation(sharedFeaturePreferences[COMPAT_IMAGE_CACHE_LOCATION_PREFERENCE_KEY])
        val sharedImageParallelism = parseCompatImageParallelism(sharedFeaturePreferences[COMPAT_IMAGE_PARALLEL_PREFERENCE_KEY])
        val diskBudget = splitImageDiskBudget(sharedImageCacheBytes)
        ConfigureOriginalMediaCache(originalMediaSession, platformContext, diskBudget.originals, sharedCacheLocation)
        val imageLoader = rememberFutachaImageLoader(
            lightweightMode = shouldUseLightweightMode,
            performanceProfile = devicePerformanceProfile,
            httpClient = httpClient,
            imageTransport = imageTransport,
            originalMediaStore = promptMediaSource,
            diskCacheBytesOverride = if (originalMediaSession == null) sharedImageCacheBytes else diskBudget.images,
            cacheLocation = sharedCacheLocation,
            parallelismOverride = sharedImageParallelism
        )
        val catalogImageLoader = rememberFutachaImageLoader(
            lightweightMode = shouldUseLightweightMode, performanceProfile = devicePerformanceProfile,
            httpClient = httpClient, imageTransport = imageTransport, originalMediaStore = promptMediaSource,
            diskCacheBytesOverride = parseCompatCatalogImageCacheQuotaBytes(sharedFeaturePreferences[COMPAT_CATALOG_IMAGE_CACHE_PREFERENCE_KEY]),
            cacheLocation = parseCompatCacheLocation(sharedFeaturePreferences[COMPAT_CATALOG_IMAGE_CACHE_LOCATION_PREFERENCE_KEY]),
            parallelismOverride = sharedImageParallelism, diskCacheDirectoryName = CATALOG_IMAGE_DISK_CACHE_DIR
        )
        // Key only on the catalog loader: replacing the thread loader alone must
        // not shut down the catalog loader that is still in use.
        val latestImageLoader = androidx.compose.runtime.rememberUpdatedState(imageLoader)
        DisposableEffect(catalogImageLoader) {
            onDispose { if (catalogImageLoader !== latestImageLoader.value) catalogImageLoader.shutdown() }
        }
        DisposableEffect(imageLoader) {
            onDispose {
                runCatching {
                    imageLoader.shutdown()
                }.onFailure { e ->
                    Logger.e("FutachaApp", "Failed to shutdown ImageLoader", e)
                }
            }
        }
        val historyImageRepositories = com.valoser.futacha.shared.ui.image.rememberHistoryImageRepositories(
            fileSystem, autoSavedThreadRepository
        )
        val highQualityThumbnailMode = com.valoser.futacha.shared.ui.image.rememberHighQualityThumbnailMode(compatibilityStore)
        CompositionLocalProvider(
            com.valoser.futacha.shared.ui.image.LocalHistoryImageRepositories provides historyImageRepositories,
            LocalFutachaImageLoader provides imageLoader,
            com.valoser.futacha.shared.ui.image.LocalHighQualityThumbnailMode provides highQualityThumbnailMode,
            LocalOriginalMediaSource provides promptMediaSource,
            LocalMediaFeatureSettings provides mediaFeatureSettings,
            LocalMediaFeatureUpdater provides updateMediaFeatures,
            com.valoser.futacha.shared.ui.image.LocalPromptContentVisible provides !promptPrivacyEnabled,
            LocalMediaFeatureGate provides mediaFeatureGate,
            com.valoser.futacha.shared.ui.media.LocalAnalysisModelStore provides analysisModels,
            LocalHistoryViewSettingsBinding provides HistoryViewSettingsBinding(
                settings = historyViewSettings,
                onSettingsChanged = { historyViewSettings = it }
            )
        ) {
            com.valoser.futacha.shared.ui.media.DeviceImageEditingHost(fileSystem, stateStore) {
            com.valoser.futacha.shared.ui.board.ProvideFutachaSharedFeatures(
                store = compatibilityStore, httpClient = httpClient, repository = sharedRepository,
                fileSystem = fileSystem, cookieRepository = cookieRepository,
                appStateStore = stateStore,
                catalogImageLoader = catalogImageLoader,
                appVersion = remember(versionChecker) { versionChecker?.getCurrentVersion() ?: "1.0" }
            ) {
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
                val arePersistedListsLoaded = observedRuntimeState.arePersistedListsLoaded
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
                var unregisteredBoardMessage by remember { mutableStateOf<String?>(null) }
                val profileController = LocalExperienceProfileUiController.current
                // Resolve only against the stored boards: the seed defaults would report a
                // registered board as unregistered at startup.
                LaunchedEffect(platformThreadDeepLink, persistedBoards, persistedHistory, arePersistedListsLoaded, isAppUnlocked) {
                    if (!isAppUnlocked || !appLock.isUnlocked) return@LaunchedEffect
                    if (!arePersistedListsLoaded) return@LaunchedEffect
                    val raw = platformThreadDeepLink?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
                    when (val resolution = resolveFutachaThreadDeepLink(raw, persistedBoards, persistedHistory)) {
                        is FutachaThreadDeepLinkResolution.Open -> {
                            pendingCompatThreadDeepLink = null
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
                LaunchedEffect(platformBoardDeepLink, persistedBoards, arePersistedListsLoaded, isAppUnlocked) {
                    if (!isAppUnlocked || !appLock.isUnlocked) return@LaunchedEffect
                    if (!arePersistedListsLoaded) return@LaunchedEffect
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
                pendingCompatThreadDeepLink?.takeIf { isAppUnlocked }?.let { raw ->
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
                unregisteredBoardMessage?.takeIf { isAppUnlocked }?.let { message ->
                    AlertDialog(
                        onDismissRequest = { unregisteredBoardMessage = null },
                        title = { Text("スレッドを開けませんでした") },
                        text = { Text(message) },
                        confirmButton = { TextButton(onClick = { unregisteredBoardMessage = null }) { Text("OK") } }
                    )
                }
                threadDeepLinkError?.takeIf { isAppUnlocked }?.let { message ->
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
                LaunchedEffect(destination, persistedBoards.size, persistedHistory.size, shouldUseLightweightMode, arePersistedListsLoaded) {
                    if (!arePersistedListsLoaded) return@LaunchedEffect
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
                    onWatchAlertSettingChangeRequested = onWatchAlertSettingChangeRequested,
                    onUnregisteredBoard = { boardName ->
                        unregisteredBoardMessage = buildFutachaUnregisteredBoardMessage(boardName)
                    }
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
                var pendingAiScreenCommands by remember { mutableStateOf<List<FutachaAiCommand>>(emptyList()) }
                val pendingAiScreenCommand = pendingAiScreenCommands.firstOrNull()
                var pendingAiScreenTargets by remember {
                    mutableStateOf<Map<AiCommandEffectKey, Pair<String?, String?>>>(emptyMap())
                }
                // When each command was forwarded, as a real monotonic mark (C4-1).
                val pendingAiScreenForwardedAt = remember {
                    HashMap<AiCommandEffectKey, TimeSource.Monotonic.ValueTimeMark>()
                }
                // The head the expiry effect has checked since the last unlock; the
                // screens receive only that one, so none runs a command that is
                // too old or held behind the lock before the check (C4-1/E4-1).
                var releasedAiScreenCommandKey by remember { mutableStateOf<AiCommandEffectKey?>(null) }
                val releasedAiScreenCommand = pendingAiScreenCommand?.takeIf {
                    isAppUnlocked && releasedAiScreenCommandKey == AiCommandEffectKey(it)
                }
                fun enqueueAiScreenCommand(command: FutachaAiCommand) {
                    pendingAiScreenCommands = enqueuePlatformAiCommand(pendingAiScreenCommands, command)
                    val retained = pendingAiScreenCommands.mapTo(HashSet()) { AiCommandEffectKey(it) }
                    pendingAiScreenTargets = (pendingAiScreenTargets +
                        (AiCommandEffectKey(command) to (navigationState.selectedBoardId to navigationState.selectedThreadId)))
                        .filterKeys { it in retained }
                    pendingAiScreenForwardedAt[AiCommandEffectKey(command)] = TimeSource.Monotonic.markNow()
                    pendingAiScreenForwardedAt.keys.retainAll(retained)
                }
                var aiResultMessage by remember { mutableStateOf<String?>(null) }
                var isAiGlobalSettingsVisible by remember { mutableStateOf(false) }
                var aiFileManagerPickerRequest by remember { mutableStateOf(0) }
                var isAiHistoryRefreshCommandRunning by remember { mutableStateOf(false) }
                val handledAiCommandIds = remember { LinkedHashSet<String>() }
                val onAiScreenCommandConsumed: (FutachaAiCommand) -> Unit = { consumedCommand ->
                    pendingAiScreenCommands = consumePlatformAiCommand(pendingAiScreenCommands, consumedCommand)
                }
                LaunchedEffect(navigationState.selectedBoardId, navigationState.selectedThreadId) {
                    val target = navigationState.selectedBoardId to navigationState.selectedThreadId
                    pendingAiScreenCommands = pendingAiScreenCommands.filter {
                        pendingAiScreenTargets[AiCommandEffectKey(it)] == target
                    }
                }
                // A command the screen never consumes must not block later ones,
                // but the wait covers a slow thread load (up to ~75 s), and the
                // user is told when one is dropped (C-5). The age counts from the
                // forwarding, also while stopped or locked, and a command that
                // waited behind the lock follows the 60 s rule (C4-1).
                LaunchedEffect(AiCommandEffectKey(pendingAiScreenCommand), isAppUnlocked) {
                    // Withdrawn on every restart (a relock restarts it), so after
                    // the unlock the screens wait for the check below.
                    releasedAiScreenCommandKey = null
                    val command = pendingAiScreenCommand ?: return@LaunchedEffect
                    val key = AiCommandEffectKey(command)
                    val decision = superviseAiScreenCommand(
                        forwardedAt = pendingAiScreenForwardedAt[key] ?: TimeSource.Monotonic.markNow(),
                        appLock = appLock,
                        onRelease = { releasedAiScreenCommandKey = key }
                    )
                    releasedAiScreenCommandKey = null
                    if (pendingAiScreenCommands.firstOrNull() !== command) return@LaunchedEffect
                    onAiScreenCommandConsumed(command)
                    aiResultMessage = buildAiScreenCommandDropMessage(command, decision)
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
                        appLock = appLock,
                        compatibilityStore = compatibilityStore,
                        importedHistoryRepository = aiImportedHistoryRepository
                    )
                )
                // C4-2: until the store emits, the boards/history are the seed
                // lists; a command resolved against them failed and was lost.
                val aiCommandInputsLoaded = rememberUpdatedState(arePersistedListsLoaded)
                suspend fun awaitAiCommandInputsLoaded() {
                    snapshotFlow { aiCommandInputsLoaded.value }.first { it }
                }
                // [arrivedAt]/[maxAgeMillis]: when the command reached the app and
                // how long its sender wants it to stay valid (watch taps).
                val currentHandleAiCommand by rememberUpdatedState<
                    suspend (FutachaAiCommand, TimeSource.Monotonic.ValueTimeMark, Long?) -> Unit
                > { command, arrivedAt, maxAgeMillis ->
                    // Held, not executed, while locked; read outside composition (C-1).
                    appLock.awaitUnlocked()
                    awaitAiCommandInputsLoaded()
                    when (
                        resolveAiCommandHoldDecision(
                            ageMillis = arrivedAt.elapsedNow().inWholeMilliseconds,
                            maxAgeMillis = maxAgeMillis,
                            wasHeldByLock = appLock.wasHeldByLock(arrivedAt)
                        )
                    ) {
                        AiCommandHoldDecision.Run -> Unit
                        AiCommandHoldDecision.DropExpired -> {
                            Logger.w(TAG, "Dropped expired AI command: ${command.action.id} from ${command.source}")
                            return@rememberUpdatedState
                        }
                        AiCommandHoldDecision.DropHeldByLock -> {
                            aiResultMessage = buildAiCommandHeldByLockMessage(command)
                            return@rememberUpdatedState
                        }
                    }
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
                        val inputs = currentAiRouterInputs
                        executeFutachaAiCommand(
                            command = command,
                            inputs = inputs.copy(
                                isAiCommandEnabled = readPersistedAiCommandEnabled(stateStore, inputs.isAiCommandEnabled)
                            )
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
                        enqueueAiScreenCommand(command)
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

                // Not keyed on the lock: the link stays unconsumed while the
                // effect waits for the unlock, and keeps its arrival time.
                LaunchedEffect(platformAiDeepLink) {
                    val rawDeepLink = platformAiDeepLink?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
                    val arrivedAt = TimeSource.Monotonic.markNow()
                    appLock.awaitUnlocked()
                    // Kept unconsumed until the stored lists are loaded (C4-2).
                    awaitAiCommandInputsLoaded()
                    val command = parseFutachaAiDeepLink(rawDeepLink, source = "platform")
                    onPlatformAiDeepLinkConsumed(rawDeepLink)
                    if (command == null) {
                        aiResultMessage = "AI操作のURLを解釈できませんでした"
                        return@LaunchedEffect
                    }
                    // Consuming the link restarts this effect; run the command
                    // outside it so the restart cannot cancel it half-way.
                    coroutineScope.launch {
                        // C4-3: a Wear OS link only opened the app. While links are
                        // not allowed, the same command from the watch's Data Layer
                        // (same commandId, allowed while OFF) does the work; its id
                        // must not be recorded here as handled.
                        if (command.isWatchRelayLink() &&
                            !readPersistedAiCommandEnabled(stateStore, currentAiRouterInputs.isAiCommandEnabled)
                        ) {
                            return@launch
                        }
                        currentHandleAiCommand(command, arrivedAt, null)
                    }
                }

                LaunchedEffect(AiCommandEffectKey(platformAiCommand)) {
                    val command = platformAiCommand ?: return@LaunchedEffect
                    val arrivedAt = FutachaAiCommandArrivals.arrivalOf(command) ?: TimeSource.Monotonic.markNow()
                    appLock.awaitUnlocked()
                    // Mirror the bridge collector below: a long history refresh
                    // runs outside this effect so the next queued platform
                    // command (which restarts the effect) cannot cancel it.
                    if (shouldLaunchAiCommandFromBridge(command)) {
                        if (shouldStartAiBridgeCommand(command, isAiHistoryRefreshCommandRunning)) {
                            isAiHistoryRefreshCommandRunning = true
                            coroutineScope.launch {
                                try {
                                    currentHandleAiCommand(command, arrivedAt, null)
                                } finally {
                                    isAiHistoryRefreshCommandRunning = false
                                }
                            }
                        }
                    } else {
                        currentHandleAiCommand(command, arrivedAt, null)
                    }
                    onPlatformAiCommandConsumed(command)
                }

                if (consumeAiCommandBridge) {
                    // Stays collecting across a relock (removing it cancelled a
                    // command it was holding) but receives only while unlocked,
                    // so commands sent meanwhile wait in the bridge (C-1).
                    LaunchedEffect(Unit) {
                        while (true) {
                            appLock.awaitUnlocked()
                            val queued = FutachaAiCommandBridge.receiveQueued()
                            val command = queued.command
                            if (shouldLaunchAiCommandFromBridge(command)) {
                                if (!shouldStartAiBridgeCommand(command, isAiHistoryRefreshCommandRunning)) {
                                    continue
                                }
                                isAiHistoryRefreshCommandRunning = true
                                launch {
                                    try {
                                        currentHandleAiCommand(command, queued.enqueuedAt, queued.maxAgeMillis)
                                    } finally {
                                        isAiHistoryRefreshCommandRunning = false
                                    }
                                }
                            } else {
                                currentHandleAiCommand(command, queued.enqueuedAt, queued.maxAgeMillis)
                            }
                        }
                    }
                }

                // Until the store emits, persistedBoards/history are the mock seed lists;
                // rendering them would flash fixture boards and history for a frame.
                if (!arePersistedListsLoaded) {
                    Box(modifier = Modifier.fillMaxSize())
                } else when (val content = resolvedDestinationContent) {
                    is FutachaResolvedDestinationContent.SavedThreads -> {
                        FutachaSavedThreadsDestination(
                            props = content.props,
                            onUnavailable = content.onUnavailable
                        )
                    }

                    is FutachaResolvedDestinationContent.BoardManagement -> {
                        FutachaBoardManagementDestination(
                            props = content.props,
                            aiCommand = releasedAiScreenCommand,
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
                            aiCommand = releasedAiScreenCommand,
                            onAiCommandConsumed = onAiScreenCommandConsumed
                        )
                    }

                    is FutachaResolvedDestinationContent.Thread -> {
                        FutachaThreadDestination(
                            props = content.props,
                            aiCommand = releasedAiScreenCommand,
                            onAiCommandConsumed = onAiScreenCommandConsumed
                        )
                    }
                }

                pendingAiConfirmation?.takeIf { isAppUnlocked }?.let { request ->
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
                                    if (!appLock.isUnlocked) return@TextButton
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
                                                    appLock = appLock,
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
                                            enqueueAiScreenCommand(confirmedRequest.command)
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

                aiResultMessage?.takeIf { isAppUnlocked }?.let { message ->
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

                if (isAiGlobalSettingsVisible && isAppUnlocked) {
                    GlobalSettingsScreen(
                        onBack = {
                            isAiGlobalSettingsVisible = false
                            aiFileManagerPickerRequest = 0
                        },
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
