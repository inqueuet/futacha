package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BoardManagementScreenEntrySupportTest {
    @Test
    fun buildBoardManagementScreenContentArgsFromContract_usesContractCallbacksAndPreferences() {
        val historyEntrySelected: (ThreadHistoryEntry) -> Unit = {}
        val historyEntryDismissed: (ThreadHistoryEntry) -> Unit = {}
        val historyEntryUpdated: (ThreadHistoryEntry) -> Unit = {}
        val historyRefresh: suspend () -> Unit = {}
        val historyCleared: () -> Unit = {}
        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = {}
        )
        val screenContract = ScreenContract(
            history = listOf(historyEntry()),
            historyCallbacks = ScreenHistoryCallbacks(
                onHistoryEntrySelected = historyEntrySelected,
                onHistoryEntryDismissed = historyEntryDismissed,
                onHistoryEntryUpdated = historyEntryUpdated,
                onHistoryRefresh = historyRefresh,
                onHistoryCleared = historyCleared
            ),
            preferencesState = ScreenPreferencesState(appVersion = "1.2.3"),
            preferencesCallbacks = preferencesCallbacks
        )
        val args = buildBoardManagementScreenContentArgsFromContract(
            boards = listOf(boardSummary()),
            screenContract = screenContract,
            onBoardSelected = {},
            onAddBoard = { _, _ -> },
            onMenuAction = {},
            modifier = Modifier
        )

        assertEquals(screenContract.history, args.history)
        assertSame(historyEntrySelected, args.historyCallbacks.onHistoryEntrySelected)
        assertSame(historyEntryDismissed, args.historyCallbacks.onHistoryEntryDismissed)
        assertSame(historyEntryUpdated, args.historyCallbacks.onHistoryEntryUpdated)
        assertSame(historyRefresh, args.historyCallbacks.onHistoryRefresh)
        assertSame(historyCleared, args.historyCallbacks.onHistoryCleared)
        assertSame(screenContract.preferencesState, args.preferencesState)
        assertSame(preferencesCallbacks, args.preferencesCallbacks)
    }

    @Test
    fun buildBoardManagementScreenContentArgs_prefersExplicitDependencyOverrides() {
        val fileSystem = InMemoryFileSystem()
        val savedThreadRepository = SavedThreadRepository(fileSystem)
        val onBoardSelected: (BoardSummary) -> Unit = {}
        val onAddBoard: (String, String) -> Unit = { _, _ -> }
        val onMenuAction: (BoardManagementMenuAction) -> Unit = {}
        val onBoardDeleted: (BoardSummary) -> Unit = {}
        val onBoardsReordered: (List<BoardSummary>) -> Unit = {}
        val args = buildBoardManagementScreenContentArgs(
            boards = listOf(boardSummary()),
            history = emptyList(),
            onBoardSelected = onBoardSelected,
            onAddBoard = onAddBoard,
            onMenuAction = onMenuAction,
            onBoardDeleted = onBoardDeleted,
            onBoardsReordered = onBoardsReordered,
            dependencies = BoardManagementScreenDependencies(),
            preferencesState = ScreenPreferencesState(appVersion = "1.0"),
            fileSystem = fileSystem,
            autoSavedThreadRepository = savedThreadRepository,
            modifier = Modifier
        )

        assertSame(onBoardSelected, args.onBoardSelected)
        assertSame(onAddBoard, args.onAddBoard)
        assertSame(onMenuAction, args.onMenuAction)
        assertSame(onBoardDeleted, args.onBoardDeleted)
        assertSame(onBoardsReordered, args.onBoardsReordered)
        assertSame(fileSystem, args.dependencies.fileSystem)
        assertSame(savedThreadRepository, args.dependencies.autoSavedThreadRepository)
    }

    @Test
    fun resolveContentContext_exposesResolvedCallbacksPreferencesAndDependencies() {
        val historyEntrySelected: (ThreadHistoryEntry) -> Unit = {}
        val historyRefresh: suspend () -> Unit = {}
        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = {}
        )
        val fileSystem = InMemoryFileSystem()
        val savedThreadRepository = SavedThreadRepository(fileSystem)
        val args = buildBoardManagementScreenContentArgs(
            boards = listOf(boardSummary()),
            history = listOf(historyEntry()),
            onBoardSelected = {},
            onAddBoard = { _, _ -> },
            onMenuAction = {},
            onHistoryEntrySelected = historyEntrySelected,
            onHistoryRefresh = historyRefresh,
            preferencesState = ScreenPreferencesState(appVersion = "1.0"),
            preferencesCallbacks = preferencesCallbacks,
            fileSystem = fileSystem,
            autoSavedThreadRepository = savedThreadRepository,
            modifier = Modifier
        )

        val context = args.resolveContentContext()

        assertEquals(args.boards, context.boards)
        assertEquals(args.history, context.history)
        assertSame(historyEntrySelected, context.onHistoryEntrySelected)
        assertSame(historyRefresh, context.onHistoryRefresh)
        assertSame(args.preferencesState, context.preferencesState)
        assertSame(preferencesCallbacks, context.preferencesCallbacks)
        assertSame(fileSystem, context.fileSystem)
        assertSame(savedThreadRepository, context.autoSavedThreadRepository)
    }

    private fun boardSummary(): BoardSummary {
        return BoardSummary(
            id = "img",
            name = "虹裏 img",
            category = "虹裏",
            url = "https://may.2chan.net/b/",
            description = "test board"
        )
    }

    private fun historyEntry(): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = "123",
            boardId = "img",
            title = "thread",
            titleImageUrl = "",
            boardName = "虹裏 img",
            boardUrl = "https://may.2chan.net/b/res/123.htm",
            lastVisitedEpochMillis = 1L,
            replyCount = 10
        )
    }
}
