package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.BoardManagementMenuAction
import com.valoser.futacha.shared.ui.board.BoardManagementScreenDependencies
import com.valoser.futacha.shared.ui.board.CatalogScreenDependencies
import com.valoser.futacha.shared.ui.board.ScreenContract
import com.valoser.futacha.shared.ui.board.ScreenServiceDependencies
import com.valoser.futacha.shared.ui.board.ScreenHistoryCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreenDependencies
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

internal data class FutachaSavedThreadsDestinationProps(
    val repository: SavedThreadRepository,
    val onThreadClick: (SavedThread) -> Unit,
    val onBack: () -> Unit
)

internal data class FutachaDestinationAssemblyContext(
    val screenContract: ScreenContract,
    val navigationCallbacks: FutachaNavigationCallbacks,
    val boardCallbacks: FutachaBoardScreenCallbacks,
    val stateStore: AppStateStore,
    val sharedRepository: BoardRepository,
    val httpClient: HttpClient?,
    val fileSystem: FileSystem?,
    val cookieRepository: CookieRepository?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val navigationState: FutachaNavigationState
)

internal data class FutachaBoardManagementDestinationProps(
    val boards: List<BoardSummary>,
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit,
    val dependencies: BoardManagementScreenDependencies,
    val screenContract: ScreenContract
)

internal data class FutachaCatalogDestinationProps(
    val board: BoardSummary,
    val onBack: () -> Unit,
    val onThreadSelected: (FutachaThreadSelection) -> Unit,
    val dependencies: CatalogScreenDependencies,
    val screenContract: ScreenContract,
    val saveableStateKey: String
)

internal data class FutachaThreadDestinationProps(
    val board: BoardSummary,
    val threadId: String,
    val historyContext: FutachaThreadHistoryContext,
    val threadTitle: String?,
    val threadUrlOverride: String?,
    val initialReplyCount: Int?,
    val onBack: () -> Unit,
    val onScrollPositionPersist: (String, Int, Int) -> Unit,
    val onScrollPositionPersistImmediately: (String, Int, Int) -> Unit,
    val dependencies: ThreadScreenDependencies,
    val screenContract: ScreenContract,
    val onRegisteredThreadUrlClick: (String) -> Boolean
)

internal fun buildFutachaScreenContractContext(
    history: List<ThreadHistoryEntry>,
    historyCallbacks: ScreenHistoryCallbacks,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks
): ScreenContract {
    return ScreenContract(
        history = history,
        historyCallbacks = historyCallbacks,
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks
    )
}

internal fun buildBoardManagementScreenDependencies(
    cookieRepository: CookieRepository?,
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?
): BoardManagementScreenDependencies {
    return BoardManagementScreenDependencies(
        services = buildScreenServiceDependencies(
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem
        )
    )
}

internal fun buildFutachaSavedThreadsDestinationProps(
    repository: SavedThreadRepository,
    navigationCallbacks: FutachaNavigationCallbacks
): FutachaSavedThreadsDestinationProps {
    return FutachaSavedThreadsDestinationProps(
        repository = repository,
        onThreadClick = navigationCallbacks.onSavedThreadSelected,
        onBack = navigationCallbacks.onSavedThreadsDismissed
    )
}

internal fun buildFutachaDestinationAssemblyContext(
    screenBindings: FutachaScreenBindingsBundle,
    stateStore: AppStateStore,
    sharedRepository: BoardRepository,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?,
    autoSavedThreadRepository: SavedThreadRepository?,
    navigationState: FutachaNavigationState
): FutachaDestinationAssemblyContext {
    return FutachaDestinationAssemblyContext(
        screenContract = screenBindings.screenContract,
        navigationCallbacks = screenBindings.navigationCallbacks,
        boardCallbacks = screenBindings.boardScreenCallbacks,
        stateStore = stateStore,
        sharedRepository = sharedRepository,
        httpClient = httpClient,
        fileSystem = fileSystem,
        cookieRepository = cookieRepository,
        autoSavedThreadRepository = autoSavedThreadRepository,
        navigationState = navigationState
    )
}

internal fun buildScreenServiceDependencies(
    stateStore: AppStateStore? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    cookieRepository: CookieRepository? = null,
    fileSystem: FileSystem? = null,
    httpClient: HttpClient? = null
): ScreenServiceDependencies {
    return ScreenServiceDependencies(
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository,
        cookieRepository = cookieRepository,
        fileSystem = fileSystem,
        httpClient = httpClient
    )
}

internal fun buildCatalogScreenDependencies(
    board: BoardSummary,
    sharedRepository: BoardRepository,
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    cookieRepository: CookieRepository?,
    httpClient: HttpClient?
): CatalogScreenDependencies {
    return CatalogScreenDependencies(
        repository = resolveFutachaBoardRepository(board, sharedRepository),
        services = buildScreenServiceDependencies(
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            httpClient = httpClient
        )
    )
}

internal fun buildThreadScreenDependencies(
    board: BoardSummary,
    sharedRepository: BoardRepository,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?,
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?
): ThreadScreenDependencies {
    return ThreadScreenDependencies(
        repository = resolveFutachaBoardRepository(board, sharedRepository),
        services = buildScreenServiceDependencies(
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            httpClient = httpClient
        )
    )
}

internal fun buildFutachaBoardManagementDestinationProps(
    boards: List<BoardSummary>,
    context: FutachaDestinationAssemblyContext
): FutachaBoardManagementDestinationProps {
    return FutachaBoardManagementDestinationProps(
        boards = boards,
        onBoardSelected = context.boardCallbacks.onBoardSelected,
        onAddBoard = context.boardCallbacks.onAddBoard,
        onMenuAction = context.boardCallbacks.onMenuAction,
        onBoardDeleted = context.boardCallbacks.onBoardDeleted,
        onBoardsReordered = context.boardCallbacks.onBoardsReordered,
        dependencies = buildBoardManagementScreenDependencies(
            cookieRepository = context.cookieRepository,
            fileSystem = context.fileSystem,
            autoSavedThreadRepository = context.autoSavedThreadRepository
        ),
        screenContract = context.screenContract
    )
}

internal fun buildFutachaCatalogDestinationProps(
    board: BoardSummary,
    context: FutachaDestinationAssemblyContext
): FutachaCatalogDestinationProps {
    return FutachaCatalogDestinationProps(
        board = board,
        onBack = context.navigationCallbacks.onBoardSelectionCleared,
        onThreadSelected = { selection ->
            context.navigationCallbacks.onCatalogThreadSelected(
                selection.threadId,
                selection.threadTitle,
                selection.threadReplies,
                selection.threadThumbnailUrl,
                selection.threadUrl
            )
        },
        dependencies = buildCatalogScreenDependencies(
            board = board,
            sharedRepository = context.sharedRepository,
            stateStore = context.stateStore,
            autoSavedThreadRepository = context.autoSavedThreadRepository,
            cookieRepository = context.cookieRepository,
            httpClient = context.httpClient
        ),
        screenContract = context.screenContract,
        saveableStateKey = "catalog-${board.id}"
    )
}

internal fun buildFutachaThreadDestinationProps(
    board: BoardSummary,
    threadId: String,
    historyContext: FutachaThreadHistoryContext,
    onScrollPositionPersist: (String, Int, Int) -> Unit,
    onScrollPositionPersistImmediately: (String, Int, Int) -> Unit,
    context: FutachaDestinationAssemblyContext
): FutachaThreadDestinationProps {
    return FutachaThreadDestinationProps(
        board = board,
        threadId = threadId,
        historyContext = historyContext,
        threadTitle = context.navigationState.selectedThreadTitle,
        threadUrlOverride = context.navigationState.selectedThreadUrl,
        initialReplyCount = context.navigationState.selectedThreadReplies,
        onBack = context.navigationCallbacks.onThreadDismissed,
        onScrollPositionPersist = onScrollPositionPersist,
        onScrollPositionPersistImmediately = onScrollPositionPersistImmediately,
        dependencies = buildThreadScreenDependencies(
            board = board,
            sharedRepository = context.sharedRepository,
            httpClient = context.httpClient,
            fileSystem = context.fileSystem,
            cookieRepository = context.cookieRepository,
            stateStore = context.stateStore,
            autoSavedThreadRepository = context.autoSavedThreadRepository
        ),
        screenContract = context.screenContract,
        onRegisteredThreadUrlClick = context.navigationCallbacks.onRegisteredThreadUrlClick
    )
}
