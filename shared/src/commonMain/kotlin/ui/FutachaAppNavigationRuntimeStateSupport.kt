package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

internal data class FutachaNavigationRuntimeState(
    val assemblyContext: FutachaDestinationAssemblyContext,
    val resolvedDestinationContent: FutachaResolvedDestinationContent
)

@Composable
internal fun rememberFutachaNavigationRuntimeState(
    navigationState: FutachaNavigationState,
    updateNavigationState: (FutachaNavigationState) -> Unit,
    destination: FutachaDestination,
    persistedBoards: List<BoardSummary>,
    activeSavedThreadsRepository: SavedThreadRepository?,
    screenBindings: FutachaScreenBindingsBundle,
    stateStore: AppStateStore,
    sharedRepository: BoardRepository,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?,
    autoSavedThreadRepository: SavedThreadRepository?,
    coroutineScope: CoroutineScope
): FutachaNavigationRuntimeState {
    val assemblyContext = remember(
        screenBindings,
        updateNavigationState,
        stateStore,
        sharedRepository,
        httpClient,
        fileSystem,
        cookieRepository,
        autoSavedThreadRepository,
        navigationState
    ) {
        buildFutachaDestinationAssemblyContext(
            screenBindings = screenBindings,
            updateNavigationState = updateNavigationState,
            stateStore = stateStore,
            sharedRepository = sharedRepository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            cookieRepository = cookieRepository,
            autoSavedThreadRepository = autoSavedThreadRepository,
            navigationState = navigationState
        )
    }
    val resolvedDestinationContent = remember(
        destination,
        persistedBoards,
        activeSavedThreadsRepository,
        assemblyContext,
        coroutineScope
    ) {
        buildFutachaResolvedDestinationContent(
            destination = destination,
            boards = persistedBoards,
            activeSavedThreadsRepository = activeSavedThreadsRepository,
            assemblyContext = assemblyContext,
            coroutineScope = coroutineScope
        )
    }
    return remember(
        assemblyContext,
        resolvedDestinationContent
    ) {
        FutachaNavigationRuntimeState(
            assemblyContext = assemblyContext,
            resolvedDestinationContent = resolvedDestinationContent
        )
    }
}
