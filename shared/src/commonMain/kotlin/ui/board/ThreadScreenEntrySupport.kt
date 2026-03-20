package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import io.ktor.client.HttpClient

internal typealias ThreadScreenResolvedDependencies = ResolvedBoardRepositoryScreenDependencies

internal data class ThreadScreenContentArgs(
    val board: BoardSummary,
    val threadId: String,
    val threadTitle: String?,
    val initialReplyCount: Int?,
    val threadUrlOverride: String?,
    val onBack: () -> Unit,
    val onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit,
    val onScrollPositionPersistImmediately: (threadId: String, index: Int, offset: Int) -> Unit,
    override val screenContext: ResolvedScreenContext,
    val dependencies: ThreadScreenResolvedDependencies,
    val onRegisteredThreadUrlClick: (String) -> Boolean,
    val modifier: Modifier
) : ScreenContextOwner

internal fun resolveThreadScreenDependencies(
    dependencies: ThreadScreenDependencies = ThreadScreenDependencies(),
    repository: BoardRepository? = dependencies.repository,
    httpClient: HttpClient? = dependencies.httpClient,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = dependencies.fileSystem,
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository? = dependencies.cookieRepository,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: com.valoser.futacha.shared.repository.SavedThreadRepository? = dependencies.autoSavedThreadRepository
): ThreadScreenResolvedDependencies {
    return resolveBoardRepositoryScreenDependencies(
        repository = repository,
        dependencies = dependencies.services,
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository,
        cookieRepository = cookieRepository,
        fileSystem = fileSystem,
        httpClient = httpClient
    )
}

internal fun assembleThreadScreenContentArgs(
    board: BoardSummary,
    threadId: String,
    threadTitle: String?,
    initialReplyCount: Int?,
    threadUrlOverride: String?,
    onBack: () -> Unit,
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit,
    onScrollPositionPersistImmediately: (threadId: String, index: Int, offset: Int) -> Unit,
    screenContext: ResolvedScreenContext,
    dependencies: ThreadScreenResolvedDependencies,
    onRegisteredThreadUrlClick: (String) -> Boolean,
    modifier: Modifier = Modifier
): ThreadScreenContentArgs {
    return ThreadScreenContentArgs(
        board = board,
        threadId = threadId,
        threadTitle = threadTitle,
        initialReplyCount = initialReplyCount,
        threadUrlOverride = threadUrlOverride,
        onBack = onBack,
        onScrollPositionPersist = onScrollPositionPersist,
        onScrollPositionPersistImmediately = onScrollPositionPersistImmediately,
        screenContext = screenContext,
        dependencies = dependencies,
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick,
        modifier = modifier
    )
}

internal fun buildThreadScreenContentArgsFromContract(
    board: BoardSummary,
    screenContract: ScreenContract,
    threadId: String,
    threadTitle: String?,
    initialReplyCount: Int?,
    onBack: () -> Unit,
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    onScrollPositionPersistImmediately: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    threadUrlOverride: String? = null,
    dependencies: ThreadScreenDependencies = ThreadScreenDependencies(),
    onRegisteredThreadUrlClick: (String) -> Boolean = { false },
    modifier: Modifier = Modifier
): ThreadScreenContentArgs {
    return assembleThreadScreenContentArgs(
        board = board,
        threadId = threadId,
        threadTitle = threadTitle,
        initialReplyCount = initialReplyCount,
        threadUrlOverride = threadUrlOverride,
        onBack = onBack,
        onScrollPositionPersist = onScrollPositionPersist,
        onScrollPositionPersistImmediately = onScrollPositionPersistImmediately,
        screenContext = resolveScreenContext(screenContract),
        dependencies = resolveThreadScreenDependencies(
            dependencies = dependencies
        ),
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick,
        modifier = modifier
    )
}

internal fun buildThreadScreenContentArgs(
    board: BoardSummary,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    initialReplyCount: Int?,
    threadUrlOverride: String? = null,
    onBack: () -> Unit,
    historyCallbacks: ScreenHistoryCallbacks = ScreenHistoryCallbacks(),
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntrySelected,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryDismissed,
    onHistoryCleared: () -> Unit = historyCallbacks.onHistoryCleared,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryUpdated,
    onHistoryRefresh: suspend () -> Unit = historyCallbacks.onHistoryRefresh,
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    onScrollPositionPersistImmediately: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    dependencies: ThreadScreenDependencies = ThreadScreenDependencies(),
    repository: BoardRepository? = dependencies.repository,
    httpClient: HttpClient? = dependencies.httpClient,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = dependencies.fileSystem,
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository? = dependencies.cookieRepository,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: com.valoser.futacha.shared.repository.SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks(),
    onRegisteredThreadUrlClick: (String) -> Boolean = { false },
    modifier: Modifier = Modifier
): ThreadScreenContentArgs {
    return buildThreadScreenContentArgsFromContract(
        board = board,
        screenContract = buildScreenContract(
            history = history,
            historyCallbacks = historyCallbacks,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryCleared = onHistoryCleared,
            onHistoryEntryUpdated = onHistoryEntryUpdated,
            onHistoryRefresh = onHistoryRefresh,
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks
        ),
        threadId = threadId,
        threadTitle = threadTitle,
        initialReplyCount = initialReplyCount,
        threadUrlOverride = threadUrlOverride,
        onBack = onBack,
        onScrollPositionPersist = onScrollPositionPersist,
        onScrollPositionPersistImmediately = onScrollPositionPersistImmediately,
        dependencies = dependencies.withOverrides(
            repository = repository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            cookieRepository = cookieRepository,
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository
        ),
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick,
        modifier = modifier
    )
}
