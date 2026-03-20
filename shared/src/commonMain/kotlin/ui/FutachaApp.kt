package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateSeedDefaults
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.rememberFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.detectDevicePerformanceProfile
import com.valoser.futacha.shared.version.UpdateInfo
import com.valoser.futacha.shared.version.VersionChecker
import kotlin.time.ExperimentalTime

private const val TAG = "FutachaApp"

@OptIn(ExperimentalTime::class)
@Composable
fun FutachaApp(
    stateStore: AppStateStore,
    boardList: List<BoardSummary> = mockBoardSummaries,
    history: List<ThreadHistoryEntry> = mockThreadHistory,
    versionChecker: VersionChecker? = null,
    httpClient: io.ktor.client.HttpClient? = null,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    cookieRepository: CookieRepository? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null
) {
    FutachaTheme {
        val devicePerformanceProfile = remember {
            detectDevicePerformanceProfile(null)
        }
        val observedRuntimeState = rememberFutachaObservedRuntimeState(
            stateStore = stateStore,
            boardList = boardList,
            history = history,
            versionChecker = versionChecker,
            fileSystem = fileSystem
        )
        val isLightweightModeEnabled by stateStore.isLightweightModeEnabled.collectAsState(
            initial = devicePerformanceProfile.isLowSpec
        )
        val shouldUseLightweightMode = isLightweightModeEnabled || devicePerformanceProfile.isLowSpec
        val imageLoader = rememberFutachaImageLoader(lightweightMode = shouldUseLightweightMode)
        DisposableEffect(imageLoader) {
            onDispose {
                runCatching {
                    imageLoader.shutdown()
                }.onFailure { e ->
                    Logger.e("FutachaApp", "Failed to shutdown ImageLoader", e)
                }
            }
        }
        CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
            Surface(modifier = Modifier.fillMaxSize()) {
                val coroutineScope = rememberCoroutineScope()
                val saveableStateHolder = rememberSaveableStateHolder()

                LaunchedEffect(Unit) {
                    stateStore.setScrollDebounceScope(coroutineScope)
                }

                val coreRuntimeState = rememberFutachaCoreRuntimeState(
                    stateStore = stateStore,
                    httpClient = httpClient,
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

                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                LaunchedEffect(versionChecker) {
                    updateInfo = fetchFutachaUpdateInfo(versionChecker) {
                        Logger.e(TAG, "Version check failed", it)
                    }
                }

                updateInfo?.let { info ->
                    UpdateNotificationDialog(
                        updateInfo = info,
                        onDismiss = { updateInfo = null }
                    )
                }

                val persistedBoards = observedRuntimeState.persistedBoards
                val persistedHistory = observedRuntimeState.persistedHistory
                var navigationState by rememberSaveable(stateSaver = FutachaNavigationState.Saver) {
                    mutableStateOf(FutachaNavigationState())
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
                val bindingsRuntimeState = rememberFutachaBindingsRuntimeState(
                    coroutineScope = coroutineScope,
                    stateStore = stateStore,
                    persistedBoards = persistedBoards,
                    persistedHistory = persistedHistory,
                    observedRuntimeState = observedRuntimeState,
                    shouldUseLightweightMode = shouldUseLightweightMode,
                    historyRefresher = historyRefresher,
                    effectiveAutoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                    navigationState = navigationState,
                    updateNavigationState = updateNavigationState
                )
                val screenBindings = bindingsRuntimeState.screenBindings
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
                    coroutineScope = coroutineScope
                )
                val resolvedDestinationContent = navigationRuntimeState.resolvedDestinationContent
                LaunchedEffect(
                    resolvedDestinationContent.adSyncLabel,
                    resolvedDestinationContent.isAdBannerVisible
                ) {
                    Logger.d(
                        TAG,
                        "syncAdBannerVisibility(${resolvedDestinationContent.isAdBannerVisible}) for ${resolvedDestinationContent.adSyncLabel}"
                    )
                    syncAdBannerVisibility(resolvedDestinationContent.isAdBannerVisible)
                }

                when (val content = resolvedDestinationContent) {
                    is FutachaResolvedDestinationContent.SavedThreads -> {
                        FutachaSavedThreadsDestination(
                            props = content.props,
                            onUnavailable = content.onUnavailable
                        )
                    }

                    is FutachaResolvedDestinationContent.BoardManagement -> {
                        FutachaBoardManagementDestination(content.props)
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
                            saveableStateHolder = saveableStateHolder
                        )
                    }

                    is FutachaResolvedDestinationContent.Thread -> {
                        FutachaThreadDestination(
                            props = content.props
                        )
                    }
                }
            }
        }
    }
}
