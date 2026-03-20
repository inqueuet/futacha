package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import io.ktor.client.HttpClient

internal typealias CatalogScreenResolvedDependencies = ResolvedBoardRepositoryScreenDependencies

internal data class CatalogScreenContentArgs(
    val board: BoardSummary?,
    val onBack: () -> Unit,
    val onThreadSelected: (CatalogItem) -> Unit,
    override val screenContext: ResolvedScreenContext,
    val dependencies: CatalogScreenResolvedDependencies,
    val modifier: Modifier
) : ScreenContextOwner

internal fun resolveCatalogScreenDependencies(
    dependencies: CatalogScreenDependencies = CatalogScreenDependencies(),
    repository: BoardRepository? = dependencies.repository,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: com.valoser.futacha.shared.repository.SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository? = dependencies.cookieRepository,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = dependencies.fileSystem,
    httpClient: HttpClient? = dependencies.httpClient
): CatalogScreenResolvedDependencies {
    return resolveBoardRepositoryScreenDependencies(
        repository = repository,
        dependencies = dependencies.services,
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository,
        cookieRepository = cookieRepository,
        fileSystem = fileSystem,
        httpClient = httpClient
    )
}

internal fun assembleCatalogScreenContentArgs(
    board: BoardSummary?,
    onBack: () -> Unit,
    onThreadSelected: (CatalogItem) -> Unit,
    screenContext: ResolvedScreenContext,
    dependencies: CatalogScreenResolvedDependencies,
    modifier: Modifier = Modifier
): CatalogScreenContentArgs {
    return CatalogScreenContentArgs(
        board = board,
        onBack = onBack,
        onThreadSelected = onThreadSelected,
        screenContext = screenContext,
        dependencies = dependencies,
        modifier = modifier
    )
}

internal fun buildCatalogScreenContentArgsFromContract(
    board: BoardSummary?,
    screenContract: ScreenContract,
    onBack: () -> Unit,
    onThreadSelected: (CatalogItem) -> Unit,
    dependencies: CatalogScreenDependencies = CatalogScreenDependencies(),
    modifier: Modifier = Modifier
): CatalogScreenContentArgs {
    return assembleCatalogScreenContentArgs(
        board = board,
        onBack = onBack,
        onThreadSelected = onThreadSelected,
        screenContext = resolveScreenContext(screenContract),
        dependencies = resolveCatalogScreenDependencies(
            dependencies = dependencies
        ),
        modifier = modifier
    )
}

internal fun buildCatalogScreenContentArgs(
    board: BoardSummary?,
    history: List<ThreadHistoryEntry>,
    onBack: () -> Unit,
    onThreadSelected: (CatalogItem) -> Unit,
    historyCallbacks: ScreenHistoryCallbacks = ScreenHistoryCallbacks(),
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntrySelected,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryDismissed,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryUpdated,
    onHistoryRefresh: suspend () -> Unit = historyCallbacks.onHistoryRefresh,
    onHistoryCleared: () -> Unit = historyCallbacks.onHistoryCleared,
    dependencies: CatalogScreenDependencies = CatalogScreenDependencies(),
    repository: BoardRepository? = dependencies.repository,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: com.valoser.futacha.shared.repository.SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository? = dependencies.cookieRepository,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks(),
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = dependencies.fileSystem,
    modifier: Modifier = Modifier,
    httpClient: HttpClient? = dependencies.httpClient
): CatalogScreenContentArgs {
    return buildCatalogScreenContentArgsFromContract(
        board = board,
        screenContract = buildScreenContract(
            history = history,
            historyCallbacks = historyCallbacks,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryCleared = onHistoryCleared,
            onHistoryEntryUpdated = onHistoryEntryUpdated,
            onHistoryRefresh = onHistoryRefresh,
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks
        ),
        onBack = onBack,
        onThreadSelected = onThreadSelected,
        dependencies = dependencies.withOverrides(
            repository = repository,
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            httpClient = httpClient
        ),
        modifier = modifier
    )
}
