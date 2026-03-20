package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.audio.TextSpeaker
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

internal data class ThreadScreenContentContext(
    val board: BoardSummary,
    val threadId: String,
    val threadTitle: String?,
    val initialReplyCount: Int?,
    val threadUrlOverride: String?,
    val onBack: () -> Unit,
    val onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit,
    val onScrollPositionPersistImmediately: (threadId: String, index: Int, offset: Int) -> Unit,
    val repository: BoardRepository?,
    override val screenContext: ResolvedScreenContext,
    override val services: ResolvedScreenServiceDependencies,
    val onRegisteredThreadUrlClick: (String) -> Boolean,
    val modifier: Modifier
) : ScreenContextOwner, ResolvedScreenServiceDependenciesOwner

internal fun ThreadScreenContentArgs.resolveContentContext(): ThreadScreenContentContext {
    return ThreadScreenContentContext(
        board = board,
        threadId = threadId,
        threadTitle = threadTitle,
        initialReplyCount = initialReplyCount,
        threadUrlOverride = threadUrlOverride,
        onBack = onBack,
        onScrollPositionPersist = onScrollPositionPersist,
        onScrollPositionPersistImmediately = onScrollPositionPersistImmediately,
        repository = dependencies.repository,
        screenContext = screenContext,
        services = dependencies.services,
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick,
        modifier = modifier
    )
}

internal data class ThreadScreenPreparedSetupBundle(
    val context: ThreadScreenContentContext,
    val mutableStateRefs: ThreadScreenMutableStateRefs,
    val coreSetupBundle: ThreadScreenCoreSetupBundle
)

internal data class ThreadScreenCoreSetupBundle(
    val environmentBundle: ThreadScreenEnvironmentBundle,
    val runtimeObjectBundle: ThreadScreenRuntimeObjectBundle,
    val platformRuntimeBindings: ThreadScreenPlatformRuntimeBindings,
    val persistentBindings: ThreadScreenPersistentBindings,
    val isPrivacyFilterEnabled: Boolean
)

internal data class ThreadScreenPreparedSetupHandles(
    val contextHandles: ThreadScreenContextHandles,
    val runtimeStateRefs: ThreadScreenRuntimeMutableStateRefs,
    val readAloudStateRefs: ThreadScreenReadAloudMutableStateRefs,
    val saveJobStateRefs: ThreadScreenSaveJobMutableStateRefs,
    val interactionStateRefs: ThreadScreenInteractionMutableStateRefs,
    val formStateRefs: ThreadScreenFormMutableStateRefs,
    val refreshStateRefs: ThreadScreenRefreshMutableStateRefs,
    val searchStateRefs: ThreadScreenSearchMutableStateRefs,
    val runtimeHandles: ThreadScreenRuntimeHandles,
    val setupHandles: ThreadScreenSetupHandles
)

internal data class ThreadScreenContextHandles(
    val board: BoardSummary,
    val history: List<ThreadHistoryEntry>,
    val threadId: String,
    val threadTitle: String?,
    val initialReplyCount: Int?,
    val threadUrlOverride: String?,
    val onBack: () -> Unit,
    val onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit,
    val onScrollPositionPersistImmediately: (threadId: String, index: Int, offset: Int) -> Unit,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryCleared: () -> Unit,
    val onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit,
    val onHistoryRefresh: suspend () -> Unit,
    val httpClient: HttpClient?,
    val fileSystem: FileSystem?,
    val cookieRepository: CookieRepository?,
    val stateStore: AppStateStore?,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val modifier: Modifier
)

internal data class ThreadScreenRuntimeHandles(
    val snackbarHostState: SnackbarHostState,
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState,
    val isDrawerOpen: State<Boolean>,
    val lazyListState: LazyListState,
    val archiveSearchJson: Json,
    val externalUrlLauncher: (String) -> Unit,
    val handleUrlClick: (String) -> Unit,
    val textSpeaker: TextSpeaker
)

internal data class ThreadScreenSetupHandles(
    val activeRepository: BoardRepository,
    val effectiveBoardUrl: String,
    val initialHistoryEntry: ThreadHistoryEntry?,
    val autoSaveRepository: SavedThreadRepository?,
    val manualSaveRepository: SavedThreadRepository?,
    val legacyManualSaveRepository: SavedThreadRepository?,
    val offlineLookupContext: OfflineThreadLookupContext,
    val offlineSources: List<OfflineThreadSource>,
    val persistentBindings: ThreadScreenPersistentBindings,
    val isPrivacyFilterEnabled: Boolean
)

internal fun resolveThreadScreenPreparedSetupHandles(
    preparedSetup: ThreadScreenPreparedSetupBundle
): ThreadScreenPreparedSetupHandles {
    val context = preparedSetup.context
    val mutableStateRefs = preparedSetup.mutableStateRefs
    val coreSetupBundle = preparedSetup.coreSetupBundle
    val environmentBundle = coreSetupBundle.environmentBundle
    val runtimeObjectBundle = coreSetupBundle.runtimeObjectBundle
    val platformRuntimeBindings = coreSetupBundle.platformRuntimeBindings
    return ThreadScreenPreparedSetupHandles(
        contextHandles = ThreadScreenContextHandles(
            board = context.board,
            history = context.history,
            threadId = context.threadId,
            threadTitle = context.threadTitle,
            initialReplyCount = context.initialReplyCount,
            threadUrlOverride = context.threadUrlOverride,
            onBack = context.onBack,
            onScrollPositionPersist = context.onScrollPositionPersist,
            onScrollPositionPersistImmediately = context.onScrollPositionPersistImmediately,
            onHistoryEntrySelected = context.onHistoryEntrySelected,
            onHistoryEntryDismissed = context.onHistoryEntryDismissed,
            onHistoryCleared = context.onHistoryCleared,
            onHistoryEntryUpdated = context.onHistoryEntryUpdated,
            onHistoryRefresh = context.onHistoryRefresh,
            httpClient = context.httpClient,
            fileSystem = context.fileSystem,
            cookieRepository = context.cookieRepository,
            stateStore = context.stateStore,
            preferencesState = context.preferencesState,
            preferencesCallbacks = context.preferencesCallbacks,
            modifier = context.modifier
        ),
        runtimeStateRefs = mutableStateRefs.runtime,
        readAloudStateRefs = mutableStateRefs.readAloud,
        saveJobStateRefs = mutableStateRefs.saveJobs,
        interactionStateRefs = mutableStateRefs.interaction,
        formStateRefs = mutableStateRefs.form,
        refreshStateRefs = mutableStateRefs.refresh,
        searchStateRefs = mutableStateRefs.search,
        runtimeHandles = ThreadScreenRuntimeHandles(
            snackbarHostState = runtimeObjectBundle.snackbarHostState,
            coroutineScope = runtimeObjectBundle.coroutineScope,
            drawerState = runtimeObjectBundle.drawerState,
            isDrawerOpen = runtimeObjectBundle.isDrawerOpen,
            lazyListState = runtimeObjectBundle.lazyListState,
            archiveSearchJson = platformRuntimeBindings.archiveSearchJson,
            externalUrlLauncher = platformRuntimeBindings.externalUrlLauncher,
            handleUrlClick = platformRuntimeBindings.handleUrlClick,
            textSpeaker = platformRuntimeBindings.textSpeaker
        ),
        setupHandles = ThreadScreenSetupHandles(
            activeRepository = environmentBundle.activeRepository,
            effectiveBoardUrl = environmentBundle.effectiveBoardUrl,
            initialHistoryEntry = environmentBundle.initialHistoryEntry,
            autoSaveRepository = environmentBundle.autoSaveRepository,
            manualSaveRepository = environmentBundle.manualSaveRepository,
            legacyManualSaveRepository = environmentBundle.legacyManualSaveRepository,
            offlineLookupContext = environmentBundle.offlineLookupContext,
            offlineSources = environmentBundle.offlineSources,
            persistentBindings = coreSetupBundle.persistentBindings,
            isPrivacyFilterEnabled = coreSetupBundle.isPrivacyFilterEnabled
        )
    )
}

@Composable
internal fun rememberThreadScreenPreparedSetupHandles(
    preparedSetup: ThreadScreenPreparedSetupBundle
): ThreadScreenPreparedSetupHandles {
    return rememberResolvedSetupHandles(
        preparedSetup = preparedSetup,
        resolver = ::resolveThreadScreenPreparedSetupHandles
    )
}

@Composable
internal fun rememberThreadScreenPreparedSetupBundle(
    args: ThreadScreenContentArgs
): ThreadScreenPreparedSetupBundle {
    val context = args.resolveContentContext()
    val mutableStateBundle = rememberThreadScreenMutableStateBundle(
        boardId = context.board.id,
        threadId = context.threadId,
        threadUrlOverride = context.threadUrlOverride
    )
    val mutableStateRefs = rememberResolvedStateRefs(
        bundle = mutableStateBundle,
        resolver = ::resolveThreadScreenMutableStateRefs
    )
    val coreSetupBundle = rememberThreadScreenCoreSetupBundle(
        board = context.board,
        history = context.history,
        threadId = context.threadId,
        repository = context.repository,
        fileSystem = context.fileSystem,
        stateStore = context.stateStore,
        autoSavedThreadRepository = context.autoSavedThreadRepository,
        manualSaveDirectory = context.preferencesState.manualSaveDirectory,
        manualSaveLocation = context.preferencesState.manualSaveLocation,
        resolvedThreadUrlOverride = mutableStateRefs.runtime.resolvedThreadUrlOverride.value,
        onRegisteredThreadUrlClick = context.onRegisteredThreadUrlClick
    )
    return remember(context, mutableStateRefs, coreSetupBundle) {
        ThreadScreenPreparedSetupBundle(
            context = context,
            mutableStateRefs = mutableStateRefs,
            coreSetupBundle = coreSetupBundle
        )
    }
}

@Composable
internal fun rememberThreadScreenCoreSetupBundle(
    board: BoardSummary,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    repository: BoardRepository?,
    fileSystem: FileSystem?,
    stateStore: AppStateStore?,
    autoSavedThreadRepository: SavedThreadRepository?,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    resolvedThreadUrlOverride: String?,
    onRegisteredThreadUrlClick: (String) -> Boolean
): ThreadScreenCoreSetupBundle {
    val environmentBundle = remember(
        repository,
        autoSavedThreadRepository,
        fileSystem,
        manualSaveDirectory,
        manualSaveLocation,
        history,
        threadId,
        board,
        resolvedThreadUrlOverride
    ) {
        buildThreadScreenEnvironmentBundle(
            repository = repository,
            autoSavedThreadRepository = autoSavedThreadRepository,
            fileSystem = fileSystem,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation,
            history = history,
            threadId = threadId,
            board = board,
            resolvedThreadUrlOverride = resolvedThreadUrlOverride
        )
    }
    val runtimeObjectBundle = rememberThreadScreenRuntimeObjectBundle(
        threadId = threadId,
        initialHistoryEntry = environmentBundle.initialHistoryEntry
    )
    val platformRuntimeBindings = rememberThreadScreenPlatformRuntimeBindings(
        platformContext = coil3.compose.LocalPlatformContext.current,
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick
    )
    val persistentBindings = rememberThreadScreenPersistentBindings(
        stateStore = stateStore,
        coroutineScope = runtimeObjectBundle.coroutineScope,
        boardId = board.id,
        threadId = threadId
    )
    val isPrivacyFilterEnabled by stateStore?.isPrivacyFilterEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    return remember(
        environmentBundle,
        runtimeObjectBundle,
        platformRuntimeBindings,
        persistentBindings,
        isPrivacyFilterEnabled
    ) {
        ThreadScreenCoreSetupBundle(
            environmentBundle = environmentBundle,
            runtimeObjectBundle = runtimeObjectBundle,
            platformRuntimeBindings = platformRuntimeBindings,
            persistentBindings = persistentBindings,
            isPrivacyFilterEnabled = isPrivacyFilterEnabled
        )
    }
}
