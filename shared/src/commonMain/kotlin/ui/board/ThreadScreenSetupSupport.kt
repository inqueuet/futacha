package com.valoser.futacha.shared.ui.board

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
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

internal data class ThreadScreenContentContext(
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
    val repository: BoardRepository?,
    val httpClient: HttpClient?,
    val fileSystem: FileSystem?,
    val cookieRepository: CookieRepository?,
    val stateStore: AppStateStore?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val onRegisteredThreadUrlClick: (String) -> Boolean,
    val modifier: Modifier
)

internal fun ThreadScreenContentArgs.resolveContentContext(): ThreadScreenContentContext {
    return ThreadScreenContentContext(
        board = board,
        history = history,
        threadId = threadId,
        threadTitle = threadTitle,
        initialReplyCount = initialReplyCount,
        threadUrlOverride = threadUrlOverride,
        onBack = onBack,
        onScrollPositionPersist = onScrollPositionPersist,
        onScrollPositionPersistImmediately = onScrollPositionPersistImmediately,
        onHistoryEntrySelected = historyCallbacks.onHistoryEntrySelected,
        onHistoryEntryDismissed = historyCallbacks.onHistoryEntryDismissed,
        onHistoryCleared = historyCallbacks.onHistoryCleared,
        onHistoryEntryUpdated = historyCallbacks.onHistoryEntryUpdated,
        onHistoryRefresh = historyCallbacks.onHistoryRefresh,
        repository = dependencies.repository,
        httpClient = dependencies.httpClient,
        fileSystem = dependencies.fileSystem,
        cookieRepository = dependencies.cookieRepository,
        stateStore = dependencies.stateStore,
        autoSavedThreadRepository = dependencies.autoSavedThreadRepository,
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
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
    val mutableStateRefs = remember(mutableStateBundle) {
        resolveThreadScreenMutableStateRefs(mutableStateBundle)
    }
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
