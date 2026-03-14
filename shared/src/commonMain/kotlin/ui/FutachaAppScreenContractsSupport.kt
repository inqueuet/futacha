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
import com.valoser.futacha.shared.ui.board.ScreenHistoryCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreenDependencies
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

internal data class FutachaScreenContractContext(
    val history: List<ThreadHistoryEntry>,
    val historyCallbacks: ScreenHistoryCallbacks,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks
)

internal data class FutachaSavedThreadsDestinationProps(
    val repository: SavedThreadRepository,
    val onThreadClick: (SavedThread) -> Unit,
    val onBack: () -> Unit
)

internal data class FutachaBoardManagementDestinationProps(
    val boards: List<BoardSummary>,
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit,
    val dependencies: BoardManagementScreenDependencies,
    val screenContract: FutachaScreenContractContext
)

internal data class FutachaCatalogDestinationProps(
    val board: BoardSummary,
    val onBack: () -> Unit,
    val onThreadSelected: (FutachaThreadSelection) -> Unit,
    val dependencies: CatalogScreenDependencies,
    val screenContract: FutachaScreenContractContext,
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
    val dependencies: ThreadScreenDependencies,
    val screenContract: FutachaScreenContractContext,
    val onRegisteredThreadUrlClick: (String) -> Boolean
)

internal data class FutachaBoardManagementDestinationInputs(
    val boards: List<BoardSummary>,
    val boardCallbacks: FutachaBoardScreenCallbacks,
    val screenContract: FutachaScreenContractContext,
    val cookieRepository: CookieRepository?,
    val fileSystem: FileSystem?,
    val autoSavedThreadRepository: SavedThreadRepository?
)

internal data class FutachaCatalogDestinationInputs(
    val board: BoardSummary,
    val stateStore: AppStateStore,
    val sharedRepository: BoardRepository,
    val screenContract: FutachaScreenContractContext,
    val navigationCallbacks: FutachaNavigationCallbacks,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val cookieRepository: CookieRepository?,
    val httpClient: HttpClient?
)

internal data class FutachaThreadDestinationInputs(
    val board: BoardSummary,
    val threadId: String,
    val navigationState: FutachaNavigationState,
    val historyContext: FutachaThreadHistoryContext,
    val screenContract: FutachaScreenContractContext,
    val navigationCallbacks: FutachaNavigationCallbacks,
    val onScrollPositionPersist: (String, Int, Int) -> Unit,
    val sharedRepository: BoardRepository,
    val httpClient: HttpClient?,
    val fileSystem: FileSystem?,
    val cookieRepository: CookieRepository?,
    val stateStore: AppStateStore,
    val autoSavedThreadRepository: SavedThreadRepository?
)

internal fun buildFutachaScreenContractContext(
    history: List<ThreadHistoryEntry>,
    historyCallbacks: ScreenHistoryCallbacks,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks
): FutachaScreenContractContext {
    return FutachaScreenContractContext(
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
        cookieRepository = cookieRepository,
        fileSystem = fileSystem,
        autoSavedThreadRepository = autoSavedThreadRepository
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
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository,
        cookieRepository = cookieRepository,
        fileSystem = null,
        httpClient = httpClient
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
        httpClient = httpClient,
        fileSystem = fileSystem,
        cookieRepository = cookieRepository,
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository
    )
}

internal fun buildFutachaBoardManagementDestinationProps(
    inputs: FutachaBoardManagementDestinationInputs
): FutachaBoardManagementDestinationProps {
    return FutachaBoardManagementDestinationProps(
        boards = inputs.boards,
        onBoardSelected = inputs.boardCallbacks.onBoardSelected,
        onAddBoard = inputs.boardCallbacks.onAddBoard,
        onMenuAction = inputs.boardCallbacks.onMenuAction,
        onBoardDeleted = inputs.boardCallbacks.onBoardDeleted,
        onBoardsReordered = inputs.boardCallbacks.onBoardsReordered,
        dependencies = buildBoardManagementScreenDependencies(
            cookieRepository = inputs.cookieRepository,
            fileSystem = inputs.fileSystem,
            autoSavedThreadRepository = inputs.autoSavedThreadRepository
        ),
        screenContract = inputs.screenContract
    )
}

internal fun buildFutachaCatalogDestinationProps(
    inputs: FutachaCatalogDestinationInputs
): FutachaCatalogDestinationProps {
    return FutachaCatalogDestinationProps(
        board = inputs.board,
        onBack = inputs.navigationCallbacks.onBoardSelectionCleared,
        onThreadSelected = { selection ->
            inputs.navigationCallbacks.onCatalogThreadSelected(
                selection.threadId,
                selection.threadTitle,
                selection.threadReplies,
                selection.threadThumbnailUrl,
                selection.threadUrl
            )
        },
        dependencies = buildCatalogScreenDependencies(
            board = inputs.board,
            sharedRepository = inputs.sharedRepository,
            stateStore = inputs.stateStore,
            autoSavedThreadRepository = inputs.autoSavedThreadRepository,
            cookieRepository = inputs.cookieRepository,
            httpClient = inputs.httpClient
        ),
        screenContract = inputs.screenContract,
        saveableStateKey = "catalog-${inputs.board.id}"
    )
}

internal fun buildFutachaThreadDestinationProps(
    inputs: FutachaThreadDestinationInputs
): FutachaThreadDestinationProps {
    return FutachaThreadDestinationProps(
        board = inputs.board,
        threadId = inputs.threadId,
        historyContext = inputs.historyContext,
        threadTitle = inputs.navigationState.selectedThreadTitle,
        threadUrlOverride = inputs.navigationState.selectedThreadUrl,
        initialReplyCount = inputs.navigationState.selectedThreadReplies,
        onBack = inputs.navigationCallbacks.onThreadDismissed,
        onScrollPositionPersist = inputs.onScrollPositionPersist,
        dependencies = buildThreadScreenDependencies(
            board = inputs.board,
            sharedRepository = inputs.sharedRepository,
            httpClient = inputs.httpClient,
            fileSystem = inputs.fileSystem,
            cookieRepository = inputs.cookieRepository,
            stateStore = inputs.stateStore,
            autoSavedThreadRepository = inputs.autoSavedThreadRepository
        ),
        screenContract = inputs.screenContract,
        onRegisteredThreadUrlClick = inputs.navigationCallbacks.onRegisteredThreadUrlClick
    )
}
