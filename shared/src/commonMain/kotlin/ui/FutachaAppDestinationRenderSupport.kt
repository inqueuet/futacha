package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.ThreadScreenDependencies
import com.valoser.futacha.shared.repository.SavedThreadRepository
import kotlinx.coroutines.CoroutineScope

internal sealed interface FutachaResolvedDestinationContent {
    data class SavedThreads(
        val props: FutachaSavedThreadsDestinationProps?,
        val onUnavailable: () -> Unit
    ) : FutachaResolvedDestinationContent

    data class BoardManagement(
        val props: FutachaBoardManagementDestinationProps
    ) : FutachaResolvedDestinationContent

    data class MissingBoard(
        val missingBoardId: String,
        val navigationState: FutachaNavigationState,
        val boards: List<BoardSummary>,
        val onRecovered: (FutachaNavigationState) -> Unit
    ) : FutachaResolvedDestinationContent

    data class Catalog(
        val props: FutachaCatalogDestinationProps
    ) : FutachaResolvedDestinationContent

    data class Thread(
        val props: FutachaThreadDestinationProps
    ) : FutachaResolvedDestinationContent
}

internal fun buildFutachaResolvedDestinationContent(
    destination: FutachaDestination,
    boards: List<BoardSummary>,
    activeSavedThreadsRepository: SavedThreadRepository?,
    assemblyContext: FutachaDestinationAssemblyContext,
    coroutineScope: CoroutineScope
): FutachaResolvedDestinationContent {
    return when (destination) {
        FutachaDestination.SavedThreads -> {
            FutachaResolvedDestinationContent.SavedThreads(
                props = activeSavedThreadsRepository?.let {
                    buildFutachaSavedThreadsDestinationProps(
                        repository = it,
                        navigationCallbacks = assemblyContext.navigationCallbacks,
                        preferencesState = assemblyContext.screenContract.preferencesState,
                        mht = assemblyContext.fileSystem?.let { fileSystem ->
                            FutachaMhtDestinationProps(
                                library = com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtLibrary(fileSystem, assemblyContext.httpClient),
                                boards = boards,
                                screenContract = assemblyContext.screenContract,
                                dependenciesFor = { board, page ->
                                    // Read-only: the page is served by the file, nothing is auto-saved, and the thread is not fetched.
                                    ThreadScreenDependencies(
                                        repository = com.valoser.futacha.shared.ui.board.FutachaStaticThreadRepository(
                                            resolveFutachaBoardRepository(board, assemblyContext.sharedRepository) ?: assemblyContext.sharedRepository, page
                                        ),
                                        longRunningScope = assemblyContext.longRunningScope,
                                        services = buildScreenServiceDependencies(
                                            stateStore = assemblyContext.stateStore,
                                            autoSavedThreadRepository = null,
                                            cookieRepository = assemblyContext.cookieRepository,
                                            fileSystem = fileSystem,
                                            httpClient = null,
                                            compatibilityStore = assemblyContext.compatibilityStore
                                        )
                                    )
                                }
                            )
                        }
                    )
                },
                onUnavailable = assemblyContext.navigationCallbacks.onSavedThreadsDismissed
            )
        }

        FutachaDestination.BoardManagement -> {
            FutachaResolvedDestinationContent.BoardManagement(
                props = buildFutachaBoardManagementDestinationProps(
                    boards = boards,
                    context = assemblyContext
                )
            )
        }

        is FutachaDestination.MissingBoard -> {
            FutachaResolvedDestinationContent.MissingBoard(
                missingBoardId = destination.missingBoardId,
                navigationState = assemblyContext.navigationState,
                boards = boards,
                onRecovered = assemblyContext.updateNavigationState
            )
        }

        is FutachaDestination.Catalog -> {
            FutachaResolvedDestinationContent.Catalog(
                props = buildFutachaCatalogDestinationProps(
                    board = destination.board,
                    context = assemblyContext
                )
            )
        }

        is FutachaDestination.Thread -> {
            val historyContext = buildFutachaThreadHistoryContext(
                board = destination.board,
                navigationState = assemblyContext.navigationState
            )
            val threadMutations = buildFutachaThreadMutationCallbacks(
                coroutineScope = coroutineScope,
                stateStore = assemblyContext.stateStore,
                board = destination.board,
                historyContext = historyContext
            )
            FutachaResolvedDestinationContent.Thread(
                props = buildFutachaThreadDestinationProps(
                    board = destination.board,
                    threadId = destination.threadId,
                    historyContext = historyContext,
                    onScrollPositionPersist = threadMutations.onScrollPositionPersist,
                    onScrollPositionPersistImmediately = threadMutations.onScrollPositionPersistImmediately,
                    context = assemblyContext
                )
            )
        }
    }
}
