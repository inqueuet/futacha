package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

internal data class ResolvedScreenHistoryCallbacks(
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryCleared: () -> Unit,
    val onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit,
    val onHistoryRefresh: suspend () -> Unit
)

internal data class ResolvedScreenContext(
    val history: List<ThreadHistoryEntry>,
    val historyCallbacks: ResolvedScreenHistoryCallbacks,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks
)

internal data class ResolvedScreenServiceDependencies(
    val stateStore: AppStateStore?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val cookieRepository: CookieRepository?,
    val fileSystem: FileSystem?,
    val httpClient: HttpClient?
)

internal data class ResolvedBoardRepositoryScreenDependencies(
    val repository: BoardRepository?,
    val services: ResolvedScreenServiceDependencies
) {
    val stateStore: AppStateStore? get() = services.stateStore
    val autoSavedThreadRepository: SavedThreadRepository? get() = services.autoSavedThreadRepository
    val cookieRepository: CookieRepository? get() = services.cookieRepository
    val fileSystem: FileSystem? get() = services.fileSystem
    val httpClient: HttpClient? get() = services.httpClient
}

internal fun resolveScreenHistoryCallbacks(
    historyCallbacks: ScreenHistoryCallbacks = ScreenHistoryCallbacks(),
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntrySelected,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryDismissed,
    onHistoryCleared: () -> Unit = historyCallbacks.onHistoryCleared,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryUpdated,
    onHistoryRefresh: suspend () -> Unit = historyCallbacks.onHistoryRefresh
): ResolvedScreenHistoryCallbacks {
    return ResolvedScreenHistoryCallbacks(
        onHistoryEntrySelected = onHistoryEntrySelected,
        onHistoryEntryDismissed = onHistoryEntryDismissed,
        onHistoryCleared = onHistoryCleared,
        onHistoryEntryUpdated = onHistoryEntryUpdated,
        onHistoryRefresh = onHistoryRefresh
    )
}

internal fun resolveScreenContext(
    history: List<ThreadHistoryEntry>,
    historyCallbacks: ScreenHistoryCallbacks = ScreenHistoryCallbacks(),
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntrySelected,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryDismissed,
    onHistoryCleared: () -> Unit = historyCallbacks.onHistoryCleared,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryUpdated,
    onHistoryRefresh: suspend () -> Unit = historyCallbacks.onHistoryRefresh,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks()
): ResolvedScreenContext {
    return ResolvedScreenContext(
        history = history,
        historyCallbacks = resolveScreenHistoryCallbacks(
            historyCallbacks = historyCallbacks,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryCleared = onHistoryCleared,
            onHistoryEntryUpdated = onHistoryEntryUpdated,
            onHistoryRefresh = onHistoryRefresh
        ),
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks
    )
}

internal fun resolveScreenContext(
    screenContract: ScreenContract
): ResolvedScreenContext {
    return ResolvedScreenContext(
        history = screenContract.history,
        historyCallbacks = resolveScreenHistoryCallbacks(
            historyCallbacks = screenContract.historyCallbacks
        ),
        preferencesState = screenContract.preferencesState,
        preferencesCallbacks = screenContract.preferencesCallbacks
    )
}

internal fun resolveScreenServiceDependencies(
    dependencies: ScreenServiceDependencies = ScreenServiceDependencies(),
    stateStore: AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    cookieRepository: CookieRepository? = dependencies.cookieRepository,
    fileSystem: FileSystem? = dependencies.fileSystem,
    httpClient: HttpClient? = dependencies.httpClient
): ResolvedScreenServiceDependencies {
    return ResolvedScreenServiceDependencies(
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository,
        cookieRepository = cookieRepository,
        fileSystem = fileSystem,
        httpClient = httpClient
    )
}

internal fun resolveBoardRepositoryScreenDependencies(
    repository: BoardRepository?,
    dependencies: ScreenServiceDependencies = ScreenServiceDependencies(),
    stateStore: AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    cookieRepository: CookieRepository? = dependencies.cookieRepository,
    fileSystem: FileSystem? = dependencies.fileSystem,
    httpClient: HttpClient? = dependencies.httpClient
): ResolvedBoardRepositoryScreenDependencies {
    return ResolvedBoardRepositoryScreenDependencies(
        repository = repository,
        services = resolveScreenServiceDependencies(
            dependencies = dependencies,
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            httpClient = httpClient
        )
    )
}
