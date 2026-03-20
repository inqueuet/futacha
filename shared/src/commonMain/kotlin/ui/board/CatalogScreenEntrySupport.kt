package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import io.ktor.client.HttpClient

internal data class CatalogScreenResolvedDependencies(
    val repository: BoardRepository?,
    val services: ResolvedScreenServiceDependencies
)

internal data class CatalogScreenContentArgs(
    val board: BoardSummary?,
    val history: List<ThreadHistoryEntry>,
    val onBack: () -> Unit,
    val onThreadSelected: (CatalogItem) -> Unit,
    val historyCallbacks: ResolvedScreenHistoryCallbacks,
    val dependencies: CatalogScreenResolvedDependencies,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val modifier: Modifier
)

internal fun resolveCatalogScreenDependencies(
    dependencies: CatalogScreenDependencies = CatalogScreenDependencies(),
    repository: BoardRepository? = dependencies.repository,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: com.valoser.futacha.shared.repository.SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    cookieRepository: com.valoser.futacha.shared.repository.CookieRepository? = dependencies.cookieRepository,
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = dependencies.fileSystem,
    httpClient: HttpClient? = dependencies.httpClient
): CatalogScreenResolvedDependencies {
    return CatalogScreenResolvedDependencies(
        repository = repository,
        services = resolveScreenServiceDependencies(
            dependencies = dependencies.services,
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            httpClient = httpClient
        )
    )
}

internal fun assembleCatalogScreenContentArgs(
    board: BoardSummary?,
    history: List<ThreadHistoryEntry>,
    onBack: () -> Unit,
    onThreadSelected: (CatalogItem) -> Unit,
    historyCallbacks: ResolvedScreenHistoryCallbacks,
    dependencies: CatalogScreenResolvedDependencies,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    modifier: Modifier = Modifier
): CatalogScreenContentArgs {
    return CatalogScreenContentArgs(
        board = board,
        history = history,
        onBack = onBack,
        onThreadSelected = onThreadSelected,
        historyCallbacks = historyCallbacks,
        dependencies = dependencies,
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
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
        history = screenContract.history,
        onBack = onBack,
        onThreadSelected = onThreadSelected,
        historyCallbacks = resolveScreenHistoryCallbacks(
            historyCallbacks = screenContract.historyCallbacks
        ),
        dependencies = resolveCatalogScreenDependencies(
            dependencies = dependencies
        ),
        preferencesState = screenContract.preferencesState,
        preferencesCallbacks = screenContract.preferencesCallbacks,
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
    return assembleCatalogScreenContentArgs(
        board = board,
        history = history,
        onBack = onBack,
        onThreadSelected = onThreadSelected,
        historyCallbacks = resolveScreenHistoryCallbacks(
            historyCallbacks = historyCallbacks,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryCleared = onHistoryCleared,
            onHistoryEntryUpdated = onHistoryEntryUpdated,
            onHistoryRefresh = onHistoryRefresh
        ),
        dependencies = resolveCatalogScreenDependencies(
            dependencies = dependencies,
            repository = repository,
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            httpClient = httpClient
        ),
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
        modifier = modifier
    )
}
