package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ThreadScreenEntrySupportTest {
    @Test
    fun buildThreadScreenContentArgsFromContract_usesScreenContractCallbacksAndPreferences() {
        val historyEntrySelected: (ThreadHistoryEntry) -> Unit = {}
        val historyEntryDismissed: (ThreadHistoryEntry) -> Unit = {}
        val historyCleared: () -> Unit = {}
        val historyEntryUpdated: (ThreadHistoryEntry) -> Unit = {}
        val historyRefresh: suspend () -> Unit = {}
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
        val dependencies = ThreadScreenDependencies()
        val args = buildThreadScreenContentArgsFromContract(
            board = boardSummary(),
            screenContract = screenContract,
            threadId = "123",
            threadTitle = "thread",
            initialReplyCount = 10,
            onBack = {},
            dependencies = dependencies,
            modifier = Modifier
        )

        assertEquals(screenContract.history, args.history)
        assertSame(historyEntrySelected, args.historyCallbacks.onHistoryEntrySelected)
        assertSame(historyEntryDismissed, args.historyCallbacks.onHistoryEntryDismissed)
        assertSame(historyCleared, args.historyCallbacks.onHistoryCleared)
        assertSame(historyEntryUpdated, args.historyCallbacks.onHistoryEntryUpdated)
        assertSame(historyRefresh, args.historyCallbacks.onHistoryRefresh)
        assertSame(screenContract.preferencesState, args.preferencesState)
        assertSame(preferencesCallbacks, args.preferencesCallbacks)
        assertSame(dependencies.repository, args.dependencies.repository)
        assertSame(dependencies.services.stateStore, args.dependencies.services.stateStore)
    }

    @Test
    fun buildThreadScreenContentArgs_prefersExplicitCallbackAndDependencyOverrides() {
        val historyCallbacks = ScreenHistoryCallbacks(
            onHistoryEntrySelected = { error("default callback should not be used") },
            onHistoryRefresh = { error("default refresh should not be used") }
        )
        val explicitSelected: (ThreadHistoryEntry) -> Unit = {}
        val explicitDismissed: (ThreadHistoryEntry) -> Unit = {}
        val explicitCleared: () -> Unit = {}
        val explicitUpdated: (ThreadHistoryEntry) -> Unit = {}
        val explicitRefresh: suspend () -> Unit = {}
        val defaultRepository = FakeBoardRepository()
        val explicitRepository = FakeBoardRepository()
        val httpClient = HttpClient()
        val fileSystem = InMemoryFileSystem()
        val stateStore = AppStateStore(FakePlatformStateStorage())
        val savedThreadRepository = SavedThreadRepository(fileSystem)
        val args = buildThreadScreenContentArgs(
            board = boardSummary(),
            history = emptyList(),
            threadId = "123",
            threadTitle = null,
            initialReplyCount = null,
            onBack = {},
            historyCallbacks = historyCallbacks,
            onHistoryEntrySelected = explicitSelected,
            onHistoryEntryDismissed = explicitDismissed,
            onHistoryCleared = explicitCleared,
            onHistoryEntryUpdated = explicitUpdated,
            onHistoryRefresh = explicitRefresh,
            dependencies = ThreadScreenDependencies(repository = defaultRepository),
            repository = explicitRepository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            stateStore = stateStore,
            autoSavedThreadRepository = savedThreadRepository,
            preferencesState = ScreenPreferencesState(appVersion = "1.0"),
            modifier = Modifier
        )

        assertSame(explicitSelected, args.historyCallbacks.onHistoryEntrySelected)
        assertSame(explicitDismissed, args.historyCallbacks.onHistoryEntryDismissed)
        assertSame(explicitCleared, args.historyCallbacks.onHistoryCleared)
        assertSame(explicitUpdated, args.historyCallbacks.onHistoryEntryUpdated)
        assertSame(explicitRefresh, args.historyCallbacks.onHistoryRefresh)
        assertSame(explicitRepository, args.dependencies.repository)
        assertSame(httpClient, args.dependencies.services.httpClient)
        assertSame(fileSystem, args.dependencies.services.fileSystem)
        assertSame(stateStore, args.dependencies.services.stateStore)
        assertSame(savedThreadRepository, args.dependencies.services.autoSavedThreadRepository)
        httpClient.close()
    }

    @Test
    fun resolveContentContext_exposesResolvedCallbacksPreferencesAndDependencies() {
        val explicitSelected: (ThreadHistoryEntry) -> Unit = {}
        val explicitRefresh: suspend () -> Unit = {}
        val fileSystem = InMemoryFileSystem()
        val stateStore = AppStateStore(FakePlatformStateStorage())
        val savedThreadRepository = SavedThreadRepository(fileSystem)
        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = {}
        )
        val args = buildThreadScreenContentArgs(
            board = boardSummary(),
            history = listOf(historyEntry()),
            threadId = "123",
            threadTitle = "thread",
            initialReplyCount = 10,
            onBack = {},
            onHistoryEntrySelected = explicitSelected,
            onHistoryRefresh = explicitRefresh,
            fileSystem = fileSystem,
            stateStore = stateStore,
            autoSavedThreadRepository = savedThreadRepository,
            preferencesState = ScreenPreferencesState(appVersion = "1.0"),
            preferencesCallbacks = preferencesCallbacks,
            modifier = Modifier
        )

        val context = args.resolveContentContext()

        assertSame(explicitSelected, context.onHistoryEntrySelected)
        assertSame(explicitRefresh, context.onHistoryRefresh)
        assertSame(fileSystem, context.fileSystem)
        assertSame(stateStore, context.stateStore)
        assertSame(savedThreadRepository, context.autoSavedThreadRepository)
        assertSame(args.preferencesState, context.preferencesState)
        assertSame(preferencesCallbacks, context.preferencesCallbacks)
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
