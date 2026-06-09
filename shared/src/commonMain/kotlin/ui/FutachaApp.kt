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
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateSeedDefaults
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.ui.board.GlobalSettingsScreen
import com.valoser.futacha.shared.ui.board.PlatformBackgroundLifecycleEffect
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.rememberFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.util.applyAppIconVariant
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.detectDevicePerformanceProfile
import com.valoser.futacha.shared.version.UpdateInfo
import com.valoser.futacha.shared.version.VersionChecker
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime

private const val TAG = "FutachaApp"
private const val APP_LOCK_HASH_LOADING = "__futacha_app_lock_loading__"

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
    platformAiDeepLink: String? = null,
    onPlatformAiDeepLinkConsumed: (String) -> Unit = {}
) {
    val platformContext = LocalPlatformContext.current
    val devicePerformanceProfile = remember(platformContext) {
        detectDevicePerformanceProfile(platformContext)
    }
    val startupAppLockHash by produceState<String?>(
        initialValue = APP_LOCK_HASH_LOADING,
        key1 = stateStore
    ) {
        stateStore.appLockPasswordHash.collect { storedHash ->
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
        FutachaTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                FutachaAppLockLoadingScreen()
            }
        }
        return
    }
    if (startupAppLockHash != null && !isUnlockedForSession) {
        FutachaTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
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
    val observedRuntimeState = rememberFutachaObservedRuntimeState(
        stateStore = stateStore,
        boardList = boardList,
        history = history,
        versionChecker = versionChecker,
        fileSystem = fileSystem,
        platformContext = platformContext
    )
    FutachaTheme(
        themeMode = observedRuntimeState.themeMode,
        themePalette = observedRuntimeState.themePalette
    ) {
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
            LaunchedEffect(platformContext, observedRuntimeState.appIconVariant) {
                applyAppIconVariant(
                    platformContext = platformContext,
                    variant = observedRuntimeState.appIconVariant
                )
            }
            Surface(modifier = Modifier.fillMaxSize()) {
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
                    shouldUseLightweightMode = shouldUseLightweightMode,
                    coroutineScope = coroutineScope
                )
                val resolvedDestinationContent = navigationRuntimeState.resolvedDestinationContent
                var pendingAiConfirmation by remember { mutableStateOf<FutachaAiConfirmationRequest?>(null) }
                var pendingAiScreenCommand by remember { mutableStateOf<FutachaAiCommand?>(null) }
                var aiResultMessage by remember { mutableStateOf<String?>(null) }
                var isAiGlobalSettingsVisible by remember { mutableStateOf(false) }
                var aiFileManagerPickerRequest by remember { mutableStateOf(0) }
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
                            pendingAiConfirmation = outcome.request
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
                        isAiCommandEnabled = observedRuntimeState.isAiCommandEnabled
                    )
                )
                val currentHandleAiCommand by rememberUpdatedState<suspend (FutachaAiCommand) -> Unit> { command ->
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
                        pendingAiScreenCommand = command
                    }
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

                LaunchedEffect(Unit) {
                    FutachaAiCommandBridge.commands.collect { command ->
                        currentHandleAiCommand(command)
                    }
                }

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
                                                    isAiCommandEnabled = observedRuntimeState.isAiCommandEnabled
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
                                            pendingAiScreenCommand = confirmedRequest.command
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
                        onDismissRequest = { aiResultMessage = null },
                        title = { Text("AI操作") },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = { aiResultMessage = null }) {
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

private fun buildAiCommandUnexpectedFailureMessage(error: Throwable): String {
    val detail = error.message?.takeIf { it.isNotBlank() }
    return if (detail == null) {
        "AI操作に失敗しました"
    } else {
        "AI操作に失敗しました: $detail"
    }
}
