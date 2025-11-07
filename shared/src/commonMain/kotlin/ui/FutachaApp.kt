package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.BoardManagementScreen
import com.valoser.futacha.shared.ui.board.CatalogScreen
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import kotlinx.coroutines.launch

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
            val selectedBoard = persistedBoards.firstOrNull { it.id == selectedBoardId }
            if (selectedBoard == null) {
                BoardManagementScreen(
                    boards = persistedBoards,
                    history = persistedHistory,
                    onBoardSelected = { board -> selectedBoardId = board.id },
                    onMenuAction = { },
                    onHistoryEntryDismissed = { entry ->
                        val updatedHistory = persistedHistory.filterNot { it.threadId == entry.threadId }
                        coroutineScope.launch {
                            stateStore.setHistory(updatedHistory)
                        }
                    }
                )
            } else {
                CatalogScreen(
                    board = selectedBoard,
                    onBack = { selectedBoardId = null }
                )
            }
        }
    }
}
