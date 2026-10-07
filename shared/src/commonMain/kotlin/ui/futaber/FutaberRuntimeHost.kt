package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import coil3.PlatformContext
import com.valoser.futacha.shared.compat.COMPAT_IMAGE_CACHE_LOCATION_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.COMPAT_IMAGE_CACHE_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.COMPAT_IMAGE_PARALLEL_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.parseCompatImageCacheQuotaBytes
import com.valoser.futacha.shared.compat.parseCompatImageParallelism
import com.valoser.futacha.shared.media.MediaFeatureGate
import com.valoser.futacha.shared.media.MediaFeatureSettings
import com.valoser.futacha.shared.media.prompt.PromptMediaSource
import com.valoser.futacha.shared.media.source.OriginalMediaSession
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.FutachaImageTransport
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateSeedDefaults
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.FutachaAppLockLoadingScreen
import com.valoser.futacha.shared.ui.image.ConfigureOriginalMediaCache
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalHighQualityThumbnailMode
import com.valoser.futacha.shared.ui.image.LocalMediaFeatureGate
import com.valoser.futacha.shared.ui.image.LocalMediaFeatureSettings
import com.valoser.futacha.shared.ui.image.LocalOriginalMediaSource
import com.valoser.futacha.shared.ui.image.LocalPromptContentVisible
import com.valoser.futacha.shared.ui.image.parseCompatCacheLocation
import com.valoser.futacha.shared.ui.image.rememberFutachaImageLoader
import com.valoser.futacha.shared.ui.image.rememberHighQualityThumbnailMode
import com.valoser.futacha.shared.ui.image.splitImageDiskBudget
import com.valoser.futacha.shared.ui.rememberFutachaCoreRuntimeState
import com.valoser.futacha.shared.util.DevicePerformanceProfile
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val FUTABER_RUNTIME_TAG = "FutaberRuntime"
private const val FUTABER_LIGHTWEIGHT_IMAGE_CACHE_BYTES = 128L * 1024 * 1024
// The same bounds ふたちゃ puts on the history refresh it runs from its drawer.
private const val FUTABER_HISTORY_REFRESH_TIMEOUT_MILLIS = 60_000L
private const val FUTABER_HISTORY_REFRESH_MAX_THREADS = 5
private const val FUTABER_HISTORY_REFRESH_THREAD_TIMEOUT_MILLIS = 6_000L

/**
 * Builds what the ふたばー screens need from the shared runtime (repository, image loader,
 * stored settings) and hands it to [FutaberApp]. It does not wait on, or change, the other
 * modes' stores beyond the shared boards and history.
 *
 * This repeats a small part of the ふたちゃ root. It is meant to move into a shared
 * mode-runtime host once the other modes can use it too.
 */
@Composable
internal fun FutaberRuntimeHost(
    stateStore: AppStateStore,
    boardList: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    httpClient: HttpClient?,
    imageTransport: FutachaImageTransport?,
    originalMediaSession: OriginalMediaSession?,
    sharedRepository: BoardRepository?,
    sharedHistoryRefresher: HistoryRefresher?,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?,
    cookieRepository: CookieRepository?,
    autoSavedThreadRepository: SavedThreadRepository?,
    compatibilityStore: CompatibilityStore?,
    promptMediaSource: PromptMediaSource?,
    mediaFeatureSettings: MediaFeatureSettings,
    mediaFeatureGate: MediaFeatureGate,
    promptPrivacyEnabled: Boolean,
    devicePerformanceProfile: DevicePerformanceProfile,
    platformContext: PlatformContext,
    appVersion: String = "1.0",
    initialThreadDeepLink: String? = null,
    onThreadDeepLinkConsumed: (String) -> Unit = {},
    initialBoardDeepLink: String? = null,
    onBoardDeepLinkConsumed: (String) -> Unit = {},
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    // Stored settings, once loaded: building the image loader from the empty
    // placeholder would rebuild it right after.
    val loadedPreferences by remember(compatibilityStore) {
        kotlinx.coroutines.flow.flow {
            if (compatibilityStore == null) {
                emit(emptyMap<String, String>())
            } else {
                compatibilityStore.isLoaded.first { it }
                compatibilityStore.preferences.collect { emit(it) }
            }
        }
    }.collectAsState<Map<String, String>, Map<String, String>?>(null)
    val persistedLightweightMode by remember(stateStore) { stateStore.isLightweightModeEnabled }
        .collectAsState<Boolean, Boolean?>(null)
    val observedBoards by stateStore.observedBoards
        .collectAsState<List<BoardSummary>, List<BoardSummary>?>(null)
    val observedHistory by stateStore.observedHistory
        .collectAsState<List<ThreadHistoryEntry>, List<ThreadHistoryEntry>?>(null)
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

    val ngRules by remember(compatibilityStore) {
        compatibilityStore?.ngRules ?: kotlinx.coroutines.flow.flowOf(emptyList<com.valoser.futacha.shared.compat.CompatNgRule>())
    }.collectAsState(emptyList<com.valoser.futacha.shared.compat.CompatNgRule>())
    val preferences = loadedPreferences
    val lightweight = persistedLightweightMode
    val boards = observedBoards
    val currentHistory = observedHistory
    if (preferences == null || lightweight == null || boards == null || currentHistory == null) {
        androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) { FutachaAppLockLoadingScreen() }
        return
    }

    val shouldUseLightweightMode = lightweight || devicePerformanceProfile.isLowSpec
    val coreRuntimeState = rememberFutachaCoreRuntimeState(
        stateStore = stateStore,
        httpClient = httpClient,
        sharedRepository = sharedRepository,
        sharedHistoryRefresher = sharedHistoryRefresher,
        fileSystem = fileSystem,
        cookieRepository = cookieRepository,
        autoSavedThreadRepository = autoSavedThreadRepository,
        shouldUseLightweightMode = shouldUseLightweightMode,
        onRepositoryCloseFailure = { Logger.e(FUTABER_RUNTIME_TAG, "Failed to close repository", it) },
        onHistoryRefresherCloseFailure = { Logger.e(FUTABER_RUNTIME_TAG, "Failed to close history refresher", it) }
    )

    // The image cache settings are shared by every mode.
    val sharedImageCacheBytes = preferences[COMPAT_IMAGE_CACHE_PREFERENCE_KEY]
        ?.let(::parseCompatImageCacheQuotaBytes)
        ?: if (shouldUseLightweightMode) FUTABER_LIGHTWEIGHT_IMAGE_CACHE_BYTES else parseCompatImageCacheQuotaBytes(null)
    val cacheLocation = parseCompatCacheLocation(preferences[COMPAT_IMAGE_CACHE_LOCATION_PREFERENCE_KEY])
    val imageParallelism = parseCompatImageParallelism(preferences[COMPAT_IMAGE_PARALLEL_PREFERENCE_KEY])
    val diskBudget = splitImageDiskBudget(sharedImageCacheBytes)
    ConfigureOriginalMediaCache(originalMediaSession, platformContext, diskBudget.originals, cacheLocation)
    val imageLoader = rememberFutachaImageLoader(
        lightweightMode = shouldUseLightweightMode,
        performanceProfile = devicePerformanceProfile,
        httpClient = httpClient,
        imageTransport = imageTransport,
        originalMediaStore = promptMediaSource,
        diskCacheBytesOverride = if (originalMediaSession == null) sharedImageCacheBytes else diskBudget.images,
        cacheLocation = cacheLocation,
        parallelismOverride = imageParallelism
    )
    DisposableEffect(imageLoader) {
        onDispose {
            runCatching { imageLoader.shutdown() }
                .onFailure { Logger.e(FUTABER_RUNTIME_TAG, "Failed to shutdown ImageLoader", it) }
        }
    }
    val highQualityThumbnailMode = rememberHighQualityThumbnailMode(compatibilityStore)
    val currentStore by rememberUpdatedState(compatibilityStore)
    // One object for as long as its parts stay: it is a key of the effects that fetch and hash the thread's pictures
    // (image NG), which must not restart every time the history is saved and this host is composed again.
    val mediaServices = remember(compatibilityStore, httpClient, fileSystem, cookieRepository) {
        compatibilityStore?.let { FutaberMediaServices(it, httpClient, fileSystem, cookieRepository) }
    }

    CompositionLocalProvider(
        LocalFutachaImageLoader provides imageLoader,
        // The shared settings and viewer screens read the catalog loader; this mode uses one loader for both.
        LocalFutachaCatalogImageLoader provides imageLoader,
        LocalHighQualityThumbnailMode provides highQualityThumbnailMode,
        LocalOriginalMediaSource provides promptMediaSource,
        LocalMediaFeatureSettings provides mediaFeatureSettings,
        LocalMediaFeatureGate provides mediaFeatureGate,
        LocalPromptContentVisible provides !promptPrivacyEnabled
    ) {
        val app: @Composable () -> Unit = {
            FutaberApp(
                boards = boards,
                history = currentHistory,
                repository = coreRuntimeState.repositoryHolder.repository,
                stateStore = stateStore,
                preferences = preferences,
                ngRules = ngRules,
                mediaServices = mediaServices,
                autoSavedThreadRepository = coreRuntimeState.effectiveAutoSavedThreadRepository,
                appVersion = appVersion,
                initialThreadDeepLink = initialThreadDeepLink,
                onThreadDeepLinkConsumed = onThreadDeepLinkConsumed,
                initialBoardDeepLink = initialBoardDeepLink,
                onBoardDeepLinkConsumed = onBoardDeepLinkConsumed,
                onWatchAlertSettingChangeRequested = onWatchAlertSettingChangeRequested,
                // The history refresh ふたちゃ runs from its drawer (no analytics here), bounded the same way.
                onRefreshHistory = {
                    val completed = kotlinx.coroutines.withTimeoutOrNull(FUTABER_HISTORY_REFRESH_TIMEOUT_MILLIS) {
                        coreRuntimeState.historyRefresher.refresh(
                            boardsSnapshot = boards,
                            historySnapshot = currentHistory,
                            autoSaveBudgetMillis = 0L,
                            maxThreadsPerRun = FUTABER_HISTORY_REFRESH_MAX_THREADS,
                            maxAutoSavesPerRun = 0,
                            threadFetchTimeoutMillisOverride = FUTABER_HISTORY_REFRESH_THREAD_TIMEOUT_MILLIS
                        )
                    }
                    if (completed == null) throw IllegalStateException("時間内に終わりませんでした")
                },
                savePreference = { key, value ->
                    scope.launch {
                        try {
                            currentStore?.savePreference(key, value)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Logger.e(FUTABER_RUNTIME_TAG, "Failed to save $key", error)
                        }
                    }
                }
            )
        }
        // The privacy display (dim / mesh) the settings page offers. It draws only while the user has turned it on
        // (the flag is off by default), over the whole mode like it does in ふたちゃ.
        if (compatibilityStore != null) {
            com.valoser.futacha.shared.ui.privacy.PrivacyModeHost(compatibilityStore, stateStore, compatibilityMode = false, content = app)
        } else {
            app()
        }
    }
}
