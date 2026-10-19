package com.valoser.futacha.shared.ui.ftutter

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
import com.valoser.futacha.shared.ui.futaber.FutaberAiInbox
import com.valoser.futacha.shared.ui.futaber.FutaberMediaServices
import com.valoser.futacha.shared.compat.COMPAT_IMAGE_CACHE_LOCATION_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.COMPAT_IMAGE_CACHE_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.COMPAT_IMAGE_PARALLEL_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.parseCompatImageCacheQuotaBytes
import com.valoser.futacha.shared.compat.parseCompatImageParallelism
import com.valoser.futacha.shared.media.MediaFeatureGate
import com.valoser.futacha.shared.media.MediaFeatureSettings
import com.valoser.futacha.shared.media.analysis.ModelStore
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
import com.valoser.futacha.shared.ui.image.LocalMediaFeatureUpdater
import com.valoser.futacha.shared.ui.image.LocalOriginalMediaSource
import com.valoser.futacha.shared.ui.image.LocalPromptContentVisible
import com.valoser.futacha.shared.ui.image.parseCompatCacheLocation
import com.valoser.futacha.shared.ui.image.rememberFutachaImageLoader
import com.valoser.futacha.shared.ui.image.rememberHighQualityThumbnailMode
import com.valoser.futacha.shared.ui.image.splitImageDiskBudget
import com.valoser.futacha.shared.ui.media.DeviceImageEditingHost
import com.valoser.futacha.shared.ui.media.LocalAnalysisModelStore
import com.valoser.futacha.shared.ui.rememberFutachaCoreRuntimeState
import com.valoser.futacha.shared.util.DevicePerformanceProfile
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first

private const val FTUTTER_RUNTIME_TAG = "FtutterRuntime"
private const val FTUTTER_LIGHTWEIGHT_IMAGE_CACHE_BYTES = 128L * 1024 * 1024
// The same bounds ふたちゃ puts on the history refresh it runs from its drawer.
private const val FTUTTER_HISTORY_REFRESH_TIMEOUT_MILLIS = 60_000L
private const val FTUTTER_HISTORY_REFRESH_MAX_THREADS = 5
private const val FTUTTER_HISTORY_REFRESH_THREAD_TIMEOUT_MILLIS = 6_000L

/**
 * ふたったー風モードのホスト。共有の実行基盤（リポジトリ、画像ローダー、保存済みの設定）を組み立てて [FtutterApp] に渡す。
 * ふたばー風の [com.valoser.futacha.shared.ui.futaber.FutaberRuntimeHost] と同じ作りで、板と履歴は AppState をふたちゃと共有する。
 */
@Composable
internal fun FtutterRuntimeHost(
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
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)? = null,
    /** Writes the media feature settings (prompt display, image / video editing); the media settings page needs it. */
    updateMediaFeatures: (suspend ((MediaFeatureSettings) -> MediaFeatureSettings) -> Unit)? = null,
    /** The analysis models the media editors use. */
    analysisModels: ModelStore? = null,
    speechRuntime: com.valoser.futacha.shared.audio.SpeechRuntime? = null,
    /** A line for the person from the host (an AI command this mode does not run). */
    externalNotice: String? = null,
    onExternalNoticeShown: () -> Unit = {},
    /** "閲覧スレ通知": the host's sender and its reaction to the switch. */
    onArchiveReportEnqueued: ((Int) -> Unit)? = null,
    onArchiveReportEnabledChanged: (Boolean) -> Unit = {},
    /** The AI commands received at the app root, answered by the mode's screen. */
    aiInbox: FutaberAiInbox? = null
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
        onRepositoryCloseFailure = { Logger.e(FTUTTER_RUNTIME_TAG, "Failed to close repository", it) },
        onHistoryRefresherCloseFailure = { Logger.e(FTUTTER_RUNTIME_TAG, "Failed to close history refresher", it) }
    )

    // The image cache settings are shared by every mode.
    val sharedImageCacheBytes = preferences[COMPAT_IMAGE_CACHE_PREFERENCE_KEY]
        ?.let(::parseCompatImageCacheQuotaBytes)
        ?: if (shouldUseLightweightMode) FTUTTER_LIGHTWEIGHT_IMAGE_CACHE_BYTES else parseCompatImageCacheQuotaBytes(null)
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
                .onFailure { Logger.e(FTUTTER_RUNTIME_TAG, "Failed to shutdown ImageLoader", it) }
        }
    }
    val highQualityThumbnailMode = rememberHighQualityThumbnailMode(compatibilityStore)
    val currentStore by rememberUpdatedState(compatibilityStore)
    // The settings of this mode are written one at a time, in order; a change of a list or a count is applied to the value
    // stored at that moment (audit M2).
    val preferenceWriter = remember(scope) {
        FtutterPreferenceWriter(
            scope = scope,
            load = { key -> currentStore?.loadPreference(key) },
            store = { key, value -> currentStore?.savePreference(key, value) },
            onFailure = { key, error -> Logger.e(FTUTTER_RUNTIME_TAG, "Failed to save $key", error) }
        )
    }
    // One object for as long as its parts stay: it is a key of the effects that fetch and hash the thread's pictures
    // (image NG), which must not restart every time the history is saved and this host is composed again.
    val mediaServices = remember(compatibilityStore, httpClient, fileSystem, cookieRepository) {
        compatibilityStore?.let { FutaberMediaServices(it, httpClient, fileSystem, cookieRepository) }
    }

    val customFont = com.valoser.futacha.shared.ui.futaber.rememberFutaberCustomFontFamily(true, fileSystem, preferences)
    CompositionLocalProvider(
        LocalFtutterFont provides customFont,
        LocalFutachaImageLoader provides imageLoader,
        // The shared settings and viewer screens read the catalog loader; this mode uses one loader for both.
        LocalFutachaCatalogImageLoader provides imageLoader,
        LocalHighQualityThumbnailMode provides highQualityThumbnailMode,
        LocalOriginalMediaSource provides promptMediaSource,
        LocalMediaFeatureSettings provides mediaFeatureSettings,
        LocalMediaFeatureGate provides mediaFeatureGate,
        // The settings of the media features (a setting page of its own in this mode) and the editors' models.
        LocalMediaFeatureUpdater provides updateMediaFeatures,
        LocalAnalysisModelStore provides analysisModels,
        com.valoser.futacha.shared.audio.LocalSpeechRuntime provides speechRuntime,
        LocalPromptContentVisible provides !promptPrivacyEnabled
    ) {
        val app: @Composable () -> Unit = {
            FtutterApp(
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
                externalNotice = externalNotice,
                onExternalNoticeShown = onExternalNoticeShown,
                onArchiveReportEnqueued = onArchiveReportEnqueued,
                onArchiveReportEnabledChanged = onArchiveReportEnabledChanged,
                aiInbox = aiInbox,
                // The image / video editors (opened from the operation menu or the settings) go inside the mode's theme.
                mediaHost = { content -> DeviceImageEditingHost(fileSystem, stateStore, content) },
                // The history refresh ふたちゃ runs from its drawer (no analytics here), bounded the same way.
                onRefreshHistory = {
                    val completed = kotlinx.coroutines.withTimeoutOrNull(FTUTTER_HISTORY_REFRESH_TIMEOUT_MILLIS) {
                        coreRuntimeState.historyRefresher.refresh(
                            boardsSnapshot = boards,
                            historySnapshot = currentHistory,
                            autoSaveBudgetMillis = 0L,
                            maxThreadsPerRun = FTUTTER_HISTORY_REFRESH_MAX_THREADS,
                            maxAutoSavesPerRun = 0,
                            threadFetchTimeoutMillisOverride = FTUTTER_HISTORY_REFRESH_THREAD_TIMEOUT_MILLIS
                        )
                    }
                    if (completed == null) throw IllegalStateException("時間内に終わりませんでした")
                },
                savePreference = preferenceWriter::save,
                editPreference = preferenceWriter::edit,
                importPreferences = { edits ->
                    preferenceWriter.editAllNow(edits) { values ->
                        currentStore?.savePreferences(values) ?: values.forEach { (k, v) -> preferenceWriter.save(k, v) }
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
