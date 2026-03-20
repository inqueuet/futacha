package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repository.SavedThreadRepository
import kotlinx.coroutines.CoroutineScope

internal sealed interface FutachaResolvedDestinationContent {
    val isAdBannerVisible: Boolean
    val adSyncLabel: String

    data class SavedThreads(
        val props: FutachaSavedThreadsDestinationProps?,
        val onUnavailable: () -> Unit,
        override val isAdBannerVisible: Boolean = false,
        override val adSyncLabel: String = "SavedThreads"
    ) : FutachaResolvedDestinationContent

    data class BoardManagement(
        val props: FutachaBoardManagementDestinationProps,
        override val isAdBannerVisible: Boolean = false,
        override val adSyncLabel: String = "BoardManagement"
    ) : FutachaResolvedDestinationContent

    data class MissingBoard(
        val missingBoardId: String,
        val navigationState: FutachaNavigationState,
        val boards: List<BoardSummary>,
        val onRecovered: (FutachaNavigationState) -> Unit,
        override val isAdBannerVisible: Boolean = false,
        override val adSyncLabel: String = "MissingBoard"
    ) : FutachaResolvedDestinationContent

    data class Catalog(
        val props: FutachaCatalogDestinationProps,
        override val isAdBannerVisible: Boolean = false,
        override val adSyncLabel: String = "Catalog(${props.board.id})"
    ) : FutachaResolvedDestinationContent

    data class Thread(
        val props: FutachaThreadDestinationProps,
        override val isAdBannerVisible: Boolean = true,
        override val adSyncLabel: String = "Thread(board=${props.board.id}, thread=${props.threadId})"
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
                        navigationCallbacks = assemblyContext.navigationCallbacks
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
