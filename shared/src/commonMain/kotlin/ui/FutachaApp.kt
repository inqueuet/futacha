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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.state.AppStateSeedDefaults
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.ScreenHistoryCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.ui.board.rememberDirectoryPickerLauncher
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.rememberFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.util.detectDevicePerformanceProfile
import com.valoser.futacha.shared.version.UpdateInfo
import com.valoser.futacha.shared.version.VersionChecker
import kotlinx.coroutines.flow.first
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

                val repositoryHolder = remember(httpClient, cookieRepository) {
                    buildFutachaRepositoryHolder(
                        FutachaRepositoryHolderInputs(
                            httpClient = httpClient,
                            cookieRepository = cookieRepository
                        )
                    )
                }

                val effectiveAutoSavedThreadRepository = remember(fileSystem, autoSavedThreadRepository) {
                    buildFutachaAutoSavedThreadRepository(
                        FutachaAutoSavedThreadRepositoryInputs(
                            fileSystem = fileSystem,
                            existingRepository = autoSavedThreadRepository
                        )
                    )
                }

                val historyRefresher = remember(
                    repositoryHolder.repository,
                    effectiveAutoSavedThreadRepository,
                    httpClient,
                    fileSystem,
                    shouldUseLightweightMode
                ) {
                    buildFutachaHistoryRefresher(
                        FutachaHistoryRefresherInputs(
                            stateStore = stateStore,
                            repository = repositoryHolder.repository,
                            autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                            httpClient = httpClient,
                            fileSystem = fileSystem,
                            shouldUseLightweightMode = shouldUseLightweightMode
                        )
                    )
                }

                DisposableEffect(repositoryHolder) {
                    onDispose {
                        closeOwnedFutachaRepository(repositoryHolder) { error ->
                            Logger.e(TAG, "Failed to close repository", error)
                        }
                    }
                }

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

                val persistedBoards by stateStore.boards.collectAsState(initial = boardList)
                val persistedHistory by stateStore.history.collectAsState(initial = history)
                val threadMenuEntries by stateStore.threadMenuEntries.collectAsState(initial = defaultThreadMenuEntries())
                val catalogNavEntries by stateStore.catalogNavEntries.collectAsState(initial = defaultCatalogNavEntries())
                val isBackgroundRefreshEnabled by stateStore.isBackgroundRefreshEnabled.collectAsState(initial = false)
                val isAdsEnabled by stateStore.isAdsEnabled.collectAsState(initial = false)
                val manualSaveDirectory by stateStore.manualSaveDirectory.collectAsState(initial = DEFAULT_MANUAL_SAVE_ROOT)
                val manualSaveLocation = remember(manualSaveDirectory) {
                    com.valoser.futacha.shared.model.SaveLocation.fromString(manualSaveDirectory)
                }
                LaunchedEffect(manualSaveLocation, fileSystem) {
                    if (shouldResetInaccessibleManualSaveBookmark(fileSystem, manualSaveLocation)) {
                        Logger.w(TAG, "Manual save bookmark is not accessible. Falling back to default path.")
                        stateStore.setManualSaveDirectory(DEFAULT_MANUAL_SAVE_ROOT)
                        stateStore.setSaveDirectorySelection(SaveDirectorySelection.MANUAL_INPUT)
                    }
                }
                val savedThreadsRepositories = remember(fileSystem, manualSaveDirectory, manualSaveLocation) {
                    buildFutachaSavedThreadsRepositories(
                        FutachaSavedThreadsRepositoryInputs(
                            fileSystem = fileSystem,
                            manualSaveDirectory = manualSaveDirectory,
                            manualSaveLocation = manualSaveLocation
                        )
                    )
                }
                val activeSavedThreadsRepository by produceState<SavedThreadRepository?>(
                    initialValue = savedThreadsRepositories.currentRepository ?: savedThreadsRepositories.legacyRepository,
                    key1 = savedThreadsRepositories.currentRepository,
                    key2 = savedThreadsRepositories.legacyRepository
                ) {
                    value = resolveActiveSavedThreadsRepository(
                        FutachaActiveSavedThreadsRepositoryInputs(
                            currentRepository = savedThreadsRepositories.currentRepository,
                            legacyRepository = savedThreadsRepositories.legacyRepository
                        )
                    )
                }
                val attachmentPickerPreference by stateStore.attachmentPickerPreference.collectAsState(initial = AttachmentPickerPreference.MEDIA)
                val saveDirectorySelection by stateStore.saveDirectorySelection.collectAsState(initial = SaveDirectorySelection.MANUAL_INPUT)
                val preferredFileManagerFlow = remember(stateStore) { stateStore.getPreferredFileManager() }
                val preferredFileManager by preferredFileManagerFlow.collectAsState(initial = null)
                val appVersion = remember(versionChecker) {
                    versionChecker?.getCurrentVersion() ?: "1.0"
                }
                val resolvedManualSaveDirectory = remember(fileSystem, manualSaveDirectory, manualSaveLocation) {
                    resolveFutachaManualSaveDirectoryDisplay(
                        fileSystem = fileSystem,
                        manualSaveDirectory = manualSaveDirectory,
                        manualSaveLocation = manualSaveLocation
                    )
                }

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

                val destination = remember(navigationState, persistedBoards) {
                    resolveFutachaDestination(navigationState, persistedBoards)
                }
                val refreshHistoryEntries: suspend () -> Unit = {
                    historyRefresher.refresh(
                        boardsSnapshot = persistedBoards,
                        historySnapshot = persistedHistory
                    )
                }
                var openSaveDirectoryPicker: () -> Unit = {}
                val preferenceMutations = buildFutachaPreferenceMutationCallbacks(
                    coroutineScope = coroutineScope,
                    inputs = FutachaPreferenceMutationInputs(
                        setBackgroundRefreshEnabled = stateStore::setBackgroundRefreshEnabled,
                        setAdsEnabled = stateStore::setAdsEnabled,
                        setLightweightModeEnabled = stateStore::setLightweightModeEnabled,
                        setManualSaveDirectory = stateStore::setManualSaveDirectory,
                        setAttachmentPickerPreference = stateStore::setAttachmentPickerPreference,
                        setSaveDirectorySelection = stateStore::setSaveDirectorySelection,
                        setManualSaveLocation = stateStore::setManualSaveLocation,
                        setPreferredFileManager = stateStore::setPreferredFileManager,
                        setThreadMenuEntries = stateStore::setThreadMenuEntries,
                        setCatalogNavEntries = stateStore::setCatalogNavEntries
                    )
                )
                val directoryPickerLauncher = rememberDirectoryPickerLauncher(
                    onDirectorySelected = { pickedLocation ->
                        preferenceMutations.onManualSaveLocationChanged(pickedLocation)
                        preferenceMutations.onSaveDirectorySelectionChanged(SaveDirectorySelection.PICKER)
                    },
                    preferredFileManagerPackage = preferredFileManager?.packageName
                )
                openSaveDirectoryPicker = directoryPickerLauncher
                val historyMutations = buildFutachaHistoryMutationCallbacks(
                    coroutineScope = coroutineScope,
                    dismissHistoryEntry = { entry ->
                        dismissHistoryEntry(
                            stateStore = stateStore,
                            autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                            entry = entry,
                            onAutoSavedThreadDeleteFailure = {
                                Logger.e(TAG, "Failed to delete auto-saved thread ${entry.threadId}", it)
                            }
                        )
                    },
                    updateHistoryEntry = stateStore::upsertHistoryEntry,
                    clearHistory = {
                        clearHistory(
                            stateStore = stateStore,
                            autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                            onSkippedThreadsCleared = historyRefresher::clearSkippedThreads,
                            onAutoSavedThreadDeleteFailure = {
                                Logger.e(TAG, "Failed to clear auto saved threads", it)
                            }
                        )
                    }
                )
                val screenBindings = buildFutachaScreenBindingsBundle(
                    coroutineScope = coroutineScope,
                    inputs = FutachaScreenBindingsInputs(
                        history = persistedHistory,
                        currentBoards = { persistedBoards },
                        currentNavigationState = { navigationState },
                        setNavigationState = { navigationState = it },
                        updateBoards = stateStore::updateBoards,
                        preferenceMutations = preferenceMutations,
                        historyMutations = historyMutations,
                        preferencesStateInputs = FutachaScreenPreferencesStateInputs(
                            appVersion = appVersion,
                            isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
                            isAdsEnabled = isAdsEnabled,
                            isLightweightModeEnabled = shouldUseLightweightMode,
                            manualSaveDirectory = manualSaveDirectory,
                            manualSaveLocation = manualSaveLocation,
                            resolvedManualSaveDirectory = resolvedManualSaveDirectory,
                            attachmentPickerPreference = attachmentPickerPreference,
                            saveDirectorySelection = saveDirectorySelection,
                            preferredFileManagerPackage = preferredFileManager?.packageName,
                            preferredFileManagerLabel = preferredFileManager?.label,
                            threadMenuEntries = threadMenuEntries,
                            catalogNavEntries = catalogNavEntries
                        ),
                        onOpenSaveDirectoryPicker = { openSaveDirectoryPicker() },
                        onHistoryRefresh = refreshHistoryEntries
                    )
                )
                val assemblyContext = remember(
                    screenBindings,
                    stateStore,
                    repositoryHolder.repository,
                    httpClient,
                    fileSystem,
                    cookieRepository,
                    effectiveAutoSavedThreadRepository,
                    navigationState
                ) {
                    buildFutachaDestinationAssemblyContext(
                        screenBindings = screenBindings,
                        stateStore = stateStore,
                        sharedRepository = repositoryHolder.repository,
                        httpClient = httpClient,
                        fileSystem = fileSystem,
                        cookieRepository = cookieRepository,
                        autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                        navigationState = navigationState
                    )
                }

                when (destination) {
                    FutachaDestination.SavedThreads -> FutachaSavedThreadsDestination(
                        props = activeSavedThreadsRepository?.let {
                            buildFutachaSavedThreadsDestinationProps(
                                repository = it,
                                navigationCallbacks = assemblyContext.navigationCallbacks
                            )
                        },
                        onUnavailable = assemblyContext.navigationCallbacks.onSavedThreadsDismissed
                    )

                    FutachaDestination.BoardManagement -> FutachaBoardManagementDestination(
                        buildFutachaBoardManagementDestinationProps(
                            boards = persistedBoards,
                            context = assemblyContext
                        )
                    )

                    is FutachaDestination.MissingBoard -> FutachaMissingBoardDestination(
                        missingBoardId = destination.missingBoardId,
                        navigationState = navigationState,
                        boards = persistedBoards,
                        onRecovered = { navigationState = it }
                    )

                    is FutachaDestination.Catalog -> {
                        FutachaCatalogDestination(
                            props = buildFutachaCatalogDestinationProps(
                                board = destination.board,
                                context = assemblyContext
                            ),
                            saveableStateHolder = saveableStateHolder
                        )
                    }

                    is FutachaDestination.Thread -> {
                        val currentBoard = destination.board
                        val activeThreadId = destination.threadId
                        val historyContext = remember(currentBoard, navigationState) {
                            buildFutachaThreadHistoryContext(
                                board = currentBoard,
                                navigationState = navigationState
                            )
                        }
                        val threadMutations = buildFutachaThreadMutationCallbacks(
                            coroutineScope = coroutineScope,
                            stateStore = stateStore,
                            board = currentBoard,
                            historyContext = historyContext
                        )
                        FutachaThreadDestination(
                            props = buildFutachaThreadDestinationProps(
                                board = currentBoard,
                                threadId = activeThreadId,
                                historyContext = historyContext,
                                onScrollPositionPersist = threadMutations.onScrollPositionPersist,
                                context = assemblyContext
                            )
                        )
                    }
                }
            }
        }
    }
}
