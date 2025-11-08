package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.BoardManagementScreen
import com.valoser.futacha.shared.ui.board.CatalogScreen
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
fun FutachaApp(
    stateStore: AppStateStore,
    boardList: List<BoardSummary> = mockBoardSummaries,
    history: List<ThreadHistoryEntry> = mockThreadHistory
) {
    FutachaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(stateStore, boardList, history) {
                stateStore.seedIfEmpty(boardList, history)
            }

            val persistedBoards by stateStore.boards.collectAsState(initial = boardList)
            val persistedHistory by stateStore.history.collectAsState(initial = history)

            var selectedBoardId by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedThreadId by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedThreadTitle by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedThreadReplies by rememberSaveable { mutableStateOf<Int?>(null) }
            var selectedThreadThumbnailUrl by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedThreadUrl by rememberSaveable { mutableStateOf<String?>(null) }

            val selectedBoard = persistedBoards.firstOrNull { it.id == selectedBoardId }
            val dismissHistoryEntry: (ThreadHistoryEntry) -> Unit = { entry ->
                val updatedHistory = persistedHistory.filterNot { it.threadId == entry.threadId }
                coroutineScope.launch {
                    stateStore.setHistory(updatedHistory)
                }
            }
            val openHistoryEntry: (ThreadHistoryEntry) -> Unit = { entry ->
                val targetBoard = persistedBoards.firstOrNull { entry.boardId.isNotBlank() && it.id == entry.boardId }
                    ?: persistedBoards.firstOrNull { it.name == entry.boardName }
                targetBoard?.let { board ->
                    selectedBoardId = board.id
                    selectedThreadId = entry.threadId
                    selectedThreadTitle = entry.title
                    selectedThreadReplies = entry.replyCount
                    selectedThreadThumbnailUrl = entry.titleImageUrl
                    selectedThreadUrl = entry.boardUrl
                }
            }

            when {
                selectedBoard == null -> {
                    selectedThreadId = null
                    selectedThreadTitle = null
                    selectedThreadReplies = null
                    selectedThreadThumbnailUrl = null
                    selectedThreadUrl = null
                    BoardManagementScreen(
                        boards = persistedBoards,
                        history = persistedHistory,
                        onBoardSelected = { board -> selectedBoardId = board.id },
                        onMenuAction = { },
                        onHistoryEntrySelected = openHistoryEntry,
                        onHistoryEntryDismissed = dismissHistoryEntry
                    )
                }

                selectedThreadId == null -> {
                    CatalogScreen(
                        board = selectedBoard,
                        history = persistedHistory,
                        onBack = {
                            selectedThreadId = null
                            selectedThreadTitle = null
                            selectedThreadReplies = null
                            selectedThreadThumbnailUrl = null
                            selectedThreadUrl = null
                            selectedBoardId = null
                        },
                        onThreadSelected = { item ->
                            selectedThreadId = item.id
                            selectedThreadTitle = item.title
                            selectedThreadReplies = item.replyCount
                            selectedThreadThumbnailUrl = item.thumbnailUrl
                            selectedThreadUrl = item.threadUrl
                        },
                        onHistoryEntrySelected = openHistoryEntry,
                        onHistoryEntryDismissed = dismissHistoryEntry
                    )
                }

                else -> {
                    val activeThreadId = selectedThreadId!!
                    val historyTitle = selectedThreadTitle ?: "無題"
                    val historyThreadUrl = selectedThreadUrl ?: selectedBoard.url
                    val historyReplies = selectedThreadReplies ?: 0
                    val historyThumbnail = selectedThreadThumbnailUrl.orEmpty()

                    LaunchedEffect(
                        activeThreadId,
                        historyTitle,
                        historyThreadUrl,
                        historyReplies,
                        historyThumbnail,
                        selectedBoard.id
                    ) {
                        val entry = ThreadHistoryEntry(
                            threadId = activeThreadId,
                            boardId = selectedBoard.id,
                            title = historyTitle,
                            titleImageUrl = historyThumbnail,
                            boardName = selectedBoard.name,
                            boardUrl = historyThreadUrl,
                            lastVisitedEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            replyCount = historyReplies
                        )
                        val updatedHistory = buildList {
                            add(entry)
                            addAll(persistedHistory.filterNot { it.threadId == activeThreadId })
                        }
                        stateStore.setHistory(updatedHistory)
                    }

                    ThreadScreen(
                        board = selectedBoard,
                        history = persistedHistory,
                        threadId = activeThreadId,
                        threadTitle = selectedThreadTitle,
                        initialReplyCount = selectedThreadReplies,
                        onBack = {
                            selectedThreadId = null
                            selectedThreadTitle = null
                            selectedThreadReplies = null
                            selectedThreadThumbnailUrl = null
                            selectedThreadUrl = null
                        },
                        onHistoryEntrySelected = openHistoryEntry,
                        onHistoryEntryDismissed = dismissHistoryEntry
                    )
                }
            }
        }
    }
}
