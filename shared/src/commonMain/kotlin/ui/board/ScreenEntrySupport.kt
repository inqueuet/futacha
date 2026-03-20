package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

internal interface ScreenContextOwner {
    val screenContext: ResolvedScreenContext
}

internal val ScreenContextOwner.history: List<ThreadHistoryEntry>
    get() = screenContext.history

internal val ScreenContextOwner.historyCallbacks: ResolvedScreenHistoryCallbacks
    get() = screenContext.historyCallbacks

internal val ScreenContextOwner.onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit
    get() = historyCallbacks.onHistoryEntrySelected

internal val ScreenContextOwner.onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit
    get() = historyCallbacks.onHistoryEntryDismissed

internal val ScreenContextOwner.onHistoryCleared: () -> Unit
    get() = historyCallbacks.onHistoryCleared

internal val ScreenContextOwner.onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit
    get() = historyCallbacks.onHistoryEntryUpdated

internal val ScreenContextOwner.onHistoryRefresh: suspend () -> Unit
    get() = historyCallbacks.onHistoryRefresh

internal val ScreenContextOwner.preferencesState: ScreenPreferencesState
    get() = screenContext.preferencesState

internal val ScreenContextOwner.preferencesCallbacks: ScreenPreferencesCallbacks
    get() = screenContext.preferencesCallbacks

internal data class ResolvedScreenServiceDependencies(
    val stateStore: AppStateStore?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val cookieRepository: CookieRepository?,
    val fileSystem: FileSystem?,
    val httpClient: HttpClient?
)

internal interface ResolvedScreenServiceDependenciesOwner {
    val services: ResolvedScreenServiceDependencies
}

internal val ResolvedScreenServiceDependenciesOwner.stateStore: AppStateStore?
    get() = services.stateStore

internal val ResolvedScreenServiceDependenciesOwner.autoSavedThreadRepository: SavedThreadRepository?
    get() = services.autoSavedThreadRepository

internal val ResolvedScreenServiceDependenciesOwner.cookieRepository: CookieRepository?
    get() = services.cookieRepository

internal val ResolvedScreenServiceDependenciesOwner.fileSystem: FileSystem?
    get() = services.fileSystem

internal val ResolvedScreenServiceDependenciesOwner.httpClient: HttpClient?
    get() = services.httpClient

@Composable
internal fun <Bundle, Refs> rememberResolvedStateRefs(
    bundle: Bundle,
    resolver: (Bundle) -> Refs
): Refs {
    return remember(bundle) {
        resolver(bundle)
    }
}

@Composable
internal fun <PreparedSetup, Handles> rememberResolvedSetupHandles(
    preparedSetup: PreparedSetup,
    resolver: (PreparedSetup) -> Handles
): Handles {
    return remember(preparedSetup) {
        resolver(preparedSetup)
    }
}

internal data class ResolvedBoardRepositoryScreenDependencies(
    val repository: BoardRepository?,
    override val services: ResolvedScreenServiceDependencies
) : ResolvedScreenServiceDependenciesOwner

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

internal fun buildScreenContract(
    history: List<ThreadHistoryEntry>,
    historyCallbacks: ScreenHistoryCallbacks = ScreenHistoryCallbacks(),
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntrySelected,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryDismissed,
    onHistoryCleared: () -> Unit = historyCallbacks.onHistoryCleared,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryUpdated,
    onHistoryRefresh: suspend () -> Unit = historyCallbacks.onHistoryRefresh,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks()
): ScreenContract {
    return ScreenContract(
        history = history,
        historyCallbacks = ScreenHistoryCallbacks(
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
