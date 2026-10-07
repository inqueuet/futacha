package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.BoardManagementScreen
import com.valoser.futacha.shared.ui.board.CatalogScreen
import com.valoser.futacha.shared.ui.board.FutachaMhtSection
import com.valoser.futacha.shared.ui.board.FutachaMhtViewer
import com.valoser.futacha.shared.ui.board.SavedThreadsScreen
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtOpened
import com.valoser.futacha.shared.ui.futaber.mht.futaberBoardForMht
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.board.stateStore
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
internal fun FutachaSavedThreadsDestination(
    props: FutachaSavedThreadsDestinationProps?,
    onUnavailable: () -> Unit
) {
    if (props != null) {
        val mht = props.mht
        // An MHT file opened from the list is shown in the thread screen until the person goes back.
        var viewing by remember(props.repository) { mutableStateOf<FutaberMhtOpened?>(null) }
        val opened = viewing
        if (mht != null && opened != null) {
            // Built once per opened file: the parent builds `mht` (and its library and callbacks) again whenever the
            // screens' inputs change, and a new set of dependencies would make the thread screen load the thread again.
            val board = remember(opened, mht.boards) { futaberBoardForMht(opened.thread, mht.boards) }
            val dependencies = remember(opened, board) { mht.dependenciesFor(board, opened.page) }
            FutachaMhtViewer(
                board = board,
                opened = opened,
                screenContract = mht.screenContract,
                dependencies = dependencies,
                onBack = { viewing = null }
            )
            return
        }
        SavedThreadsScreen(
            repository = props.repository,
            onThreadClick = props.onThreadClick,
            onBack = props.onBack,
            bodyTextSize = props.preferencesState.threadBodyTextSize,
            extraContent = mht?.let { { FutachaMhtSection(it.library, onOpened = { file -> viewing = file }) } }
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
    props: FutachaBoardManagementDestinationProps,
    aiCommand: FutachaAiCommand? = null,
    onAiCommandConsumed: (FutachaAiCommand) -> Unit = {}
) {
    BoardManagementScreen(
        boards = props.boards,
        screenContract = props.screenContract,
        onBoardSelected = props.onBoardSelected,
        onAddBoard = props.onAddBoard,
        onMenuAction = props.onMenuAction,
        onBoardDeleted = props.onBoardDeleted,
        onBoardsReordered = props.onBoardsReordered,
        dependencies = props.dependencies,
        aiCommand = aiCommand,
        onAiCommandConsumed = onAiCommandConsumed
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
    saveableStateHolder: SaveableStateHolder,
    aiCommand: FutachaAiCommand? = null,
    onAiCommandConsumed: (FutachaAiCommand) -> Unit = {}
) {
    saveableStateHolder.SaveableStateProvider(props.saveableStateKey) {
        CatalogScreen(
            board = props.board,
            screenContract = props.screenContract,
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
            dependencies = props.dependencies,
            aiCommand = aiCommand,
            onAiCommandConsumed = onAiCommandConsumed,
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
internal fun FutachaThreadDestination(
    props: FutachaThreadDestinationProps,
    aiCommand: FutachaAiCommand? = null,
    onAiCommandConsumed: (FutachaAiCommand) -> Unit = {}
) {
    LaunchedEffect(props.threadId, props.board.id) {
        recordFutachaVisitedThread(
            stateStore = requireNotNull(props.dependencies.stateStore),
            history = props.screenContract.history,
            threadId = props.threadId,
            board = props.board,
            context = props.historyContext,
            currentTimeMillis = Clock.System.now().toEpochMilliseconds()
        )
    }

    // One composition per thread. Switching threads in place (history drawer,
    // in-body thread links) otherwise kept the previous thread's reply image,
    // open reply dialog, filters and in-flight jobs, so thread A's attachment
    // could be posted to thread B and A's reply success could cancel B's load.
    // Manual page saves run in the app-scoped longRunningScope and survive this.
    key(props.board.id, props.threadId) {
        ThreadScreen(
            board = props.board,
            screenContract = props.screenContract,
            threadId = props.threadId,
            threadTitle = props.threadTitle,
            initialReplyCount = props.initialReplyCount,
            onBack = props.onBack,
            onScrollPositionPersist = props.onScrollPositionPersist,
            onScrollPositionPersistImmediately = props.onScrollPositionPersistImmediately,
            threadUrlOverride = props.threadUrlOverride,
            dependencies = props.dependencies,
            onRegisteredThreadUrlClick = props.onRegisteredThreadUrlClick,
            aiCommand = aiCommand,
            onAiCommandConsumed = onAiCommandConsumed
        )
    }
}
