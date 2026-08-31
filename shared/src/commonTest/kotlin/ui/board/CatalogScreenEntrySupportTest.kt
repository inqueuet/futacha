package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
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

class CatalogScreenEntrySupportTest {
    @Test
    fun buildCatalogScreenContentArgsFromContract_usesContractCallbacksAndPreferences() {
        val historyEntrySelected: (ThreadHistoryEntry) -> Unit = {}
        val historyEntryDismissed: (ThreadHistoryEntry) -> Unit = {}
        val historyEntryUpdated: (ThreadHistoryEntry) -> Unit = {}
        val historyRefresh: suspend () -> Unit = {}
        val historyCleared: () -> Unit = {}
        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = {}
        )
        val screenContract = buildScreenContract(
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
        val args = buildCatalogScreenContentArgsFromContract(
            board = boardSummary(),
            screenContract = screenContract,
            onBack = {},
            onThreadSelected = {},
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
    fun buildCatalogScreenContentArgs_prefersExplicitDependencyOverrides() {
        val repository = FakeBoardRepository()
        val stateStore = AppStateStore(FakePlatformStateStorage())
        val fileSystem = InMemoryFileSystem()
        val savedThreadRepository = SavedThreadRepository(fileSystem)
        val httpClient = HttpClient()
        val onThreadSelected: (CatalogItem) -> Unit = {}
        val args = buildCatalogScreenContentArgs(
            board = boardSummary(),
            history = emptyList(),
            onBack = {},
            onThreadSelected = onThreadSelected,
            dependencies = CatalogScreenDependencies(repository = FakeBoardRepository()),
            repository = repository,
            stateStore = stateStore,
            autoSavedThreadRepository = savedThreadRepository,
            preferencesState = ScreenPreferencesState(appVersion = "1.0"),
            fileSystem = fileSystem,
            modifier = Modifier,
            httpClient = httpClient
        )

        assertSame(onThreadSelected, args.onThreadSelected)
        assertSame(repository, args.dependencies.repository)
        assertSame(stateStore, args.dependencies.stateStore)
        assertSame(savedThreadRepository, args.dependencies.autoSavedThreadRepository)
        assertSame(fileSystem, args.dependencies.fileSystem)
        assertSame(httpClient, args.dependencies.httpClient)
        httpClient.close()
    }

    @Test
    fun resolveContentContext_exposesResolvedCallbacksPreferencesAndDependencies() {
        val explicitSelected: (ThreadHistoryEntry) -> Unit = {}
        val explicitDismissed: (ThreadHistoryEntry) -> Unit = {}
        val explicitUpdated: (ThreadHistoryEntry) -> Unit = {}
        val explicitRefresh: suspend () -> Unit = {}
        val explicitCleared: () -> Unit = {}
        val repository = FakeBoardRepository()
        val stateStore = AppStateStore(FakePlatformStateStorage())
        val fileSystem = InMemoryFileSystem()
        val savedThreadRepository = SavedThreadRepository(fileSystem)
        val httpClient = HttpClient()
        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = {}
        )
        val args = buildCatalogScreenContentArgs(
            board = boardSummary(),
            history = listOf(historyEntry()),
            onBack = {},
            onThreadSelected = {},
            onHistoryEntrySelected = explicitSelected,
            onHistoryEntryDismissed = explicitDismissed,
            onHistoryEntryUpdated = explicitUpdated,
            onHistoryRefresh = explicitRefresh,
            onHistoryCleared = explicitCleared,
            repository = repository,
            stateStore = stateStore,
            autoSavedThreadRepository = savedThreadRepository,
            fileSystem = fileSystem,
            httpClient = httpClient,
            preferencesState = ScreenPreferencesState(appVersion = "1.0"),
            preferencesCallbacks = preferencesCallbacks,
            modifier = Modifier
        )

        val context = args.resolveContentContext()

        assertSame(explicitSelected, context.onHistoryEntrySelected)
        assertSame(explicitDismissed, context.onHistoryEntryDismissed)
        assertSame(explicitUpdated, context.onHistoryEntryUpdated)
        assertSame(explicitRefresh, context.onHistoryRefresh)
        assertSame(explicitCleared, context.onHistoryCleared)
        assertSame(stateStore, context.stateStore)
        assertSame(savedThreadRepository, context.autoSavedThreadRepository)
        assertSame(fileSystem, context.fileSystem)
        assertSame(httpClient, context.httpClient)
        assertSame(args.preferencesState, context.preferencesState)
        assertSame(preferencesCallbacks, context.preferencesCallbacks)
        httpClient.close()
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
