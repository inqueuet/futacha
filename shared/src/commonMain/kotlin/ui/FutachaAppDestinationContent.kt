package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.BoardManagementScreen
import com.valoser.futacha.shared.ui.board.CatalogScreen
import com.valoser.futacha.shared.ui.board.SavedThreadsScreen
import com.valoser.futacha.shared.ui.board.ThreadScreen
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
internal fun FutachaSavedThreadsDestination(
    props: FutachaSavedThreadsDestinationProps?,
    onUnavailable: () -> Unit
) {
    if (props != null) {
        SavedThreadsScreen(
            repository = props.repository,
            onThreadClick = props.onThreadClick,
            onBack = props.onBack
        )
        return
    }

    LaunchedEffect(Unit) {
        onUnavailable()
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("保存済みスレッドは利用できません")
    }
}

@Composable
internal fun FutachaBoardManagementDestination(
    props: FutachaBoardManagementDestinationProps
) {
    BoardManagementScreen(
        boards = props.boards,
        history = props.history,
        onBoardSelected = props.onBoardSelected,
        onAddBoard = props.onAddBoard,
        onMenuAction = props.onMenuAction,
        historyCallbacks = props.historyCallbacks,
        onBoardDeleted = props.onBoardDeleted,
        onBoardsReordered = props.onBoardsReordered,
        dependencies = props.dependencies,
        preferencesState = props.preferencesState,
        preferencesCallbacks = props.preferencesCallbacks
    )
}

@Composable
internal fun FutachaMissingBoardDestination(
    missingBoardId: String,
    navigationState: FutachaNavigationState,
    boards: List<BoardSummary>,
    onRecovered: (FutachaNavigationState) -> Unit
) {
    LaunchedEffect(missingBoardId, navigationState.selectedThreadId, boards) {
        delay(2_000L)
        resolveMissingBoardRecoveryState(
            state = navigationState,
            missingBoardId = missingBoardId,
            boards = boards
        )?.let(onRecovered)
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun FutachaCatalogDestination(
    props: FutachaCatalogDestinationProps,
    saveableStateHolder: SaveableStateHolder
) {
    saveableStateHolder.SaveableStateProvider(props.saveableStateKey) {
        CatalogScreen(
            board = props.board,
            history = props.history,
            onBack = props.onBack,
            onThreadSelected = { item ->
                props.onThreadSelected(
                    FutachaThreadSelection(
                        boardId = props.board.id,
                        threadId = item.id,
                        threadTitle = item.title,
                        threadReplies = item.replyCount,
                        threadThumbnailUrl = item.thumbnailUrl,
                        threadUrl = item.threadUrl
                    )
                )
            },
            historyCallbacks = props.historyCallbacks,
            dependencies = props.dependencies,
            preferencesState = props.preferencesState,
            preferencesCallbacks = props.preferencesCallbacks,
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
internal fun FutachaThreadDestination(
    props: FutachaThreadDestinationProps
) {
    LaunchedEffect(props.threadId, props.board.id) {
        recordFutachaVisitedThread(
            stateStore = requireNotNull(props.dependencies.stateStore),
            history = props.history,
            threadId = props.threadId,
            board = props.board,
            context = props.historyContext,
            currentTimeMillis = Clock.System.now().toEpochMilliseconds()
        )
    }

    ThreadScreen(
        board = props.board,
        history = props.history,
        threadId = props.threadId,
        threadTitle = props.threadTitle,
        threadUrlOverride = props.threadUrlOverride,
        initialReplyCount = props.initialReplyCount,
        onBack = props.onBack,
        historyCallbacks = props.historyCallbacks,
        onScrollPositionPersist = props.onScrollPositionPersist,
        dependencies = props.dependencies,
        preferencesState = props.preferencesState,
        preferencesCallbacks = props.preferencesCallbacks,
        onRegisteredThreadUrlClick = props.onRegisteredThreadUrlClick
    )
}
