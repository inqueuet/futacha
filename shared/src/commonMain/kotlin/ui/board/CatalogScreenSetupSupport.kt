package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.rememberUrlLauncher
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

internal data class CatalogScreenContentContext(
    val board: BoardSummary?,
    val onBack: () -> Unit,
    val onThreadSelected: (CatalogItem) -> Unit,
    val repository: BoardRepository?,
    override val screenContext: ResolvedScreenContext,
    override val services: ResolvedScreenServiceDependencies,
    val modifier: Modifier
) : ScreenContextOwner, ResolvedScreenServiceDependenciesOwner

internal fun CatalogScreenContentArgs.resolveContentContext(): CatalogScreenContentContext {
    return CatalogScreenContentContext(
        board = board,
        onBack = onBack,
        onThreadSelected = onThreadSelected,
        repository = dependencies.repository,
        screenContext = screenContext,
        services = dependencies.services,
        modifier = modifier
    )
}

internal data class CatalogScreenSetupBundle(
    val activeRepository: BoardRepository,
    val archiveSearchScope: com.valoser.futacha.shared.network.ArchiveSearchScope?,
    val coreBindings: CatalogScreenCoreBindingsBundle,
    val urlLauncher: (String) -> Unit
)

internal data class CatalogScreenPreparedSetupBundle(
    val context: CatalogScreenContentContext,
    val activeRepository: BoardRepository,
    val archiveSearchScope: ArchiveSearchScope?,
    val runtimeObjects: CatalogScreenRuntimeObjectsBundle,
    val persistentBindings: CatalogScreenPersistentBindings,
    val mutableStateRefs: CatalogScreenMutableStateRefs,
    val urlLauncher: (String) -> Unit
)

internal data class CatalogScreenContextHandles(
    val board: BoardSummary?,
    val history: List<ThreadHistoryEntry>,
    val onBack: () -> Unit,
    val onThreadSelected: (CatalogItem) -> Unit,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit,
    val onHistoryRefresh: suspend () -> Unit,
    val onHistoryCleared: () -> Unit,
    val repository: BoardRepository?,
    val stateStore: AppStateStore?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val cookieRepository: CookieRepository?,
    val fileSystem: FileSystem?,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val httpClient: HttpClient?,
    val modifier: Modifier
)

internal data class CatalogScreenRuntimeHandles(
    val snackbarHostState: SnackbarHostState,
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState,
    val isDrawerOpen: Boolean,
    val archiveSearchJson: Json,
    val catalogGridState: LazyGridState,
    val catalogListState: LazyListState
)

internal data class CatalogScreenSetupHandles(
    val activeRepository: BoardRepository,
    val archiveSearchScope: ArchiveSearchScope?,
    val persistentBindings: CatalogScreenPersistentBindings,
    val urlLauncher: (String) -> Unit
)

internal data class CatalogScreenPreparedSetupHandles(
    val contextHandles: CatalogScreenContextHandles,
    val runtimeHandles: CatalogScreenRuntimeHandles,
    val setupHandles: CatalogScreenSetupHandles,
    val mutableStateHandles: CatalogScreenMutableStateHandles
)

internal fun resolveCatalogScreenPreparedSetupHandles(
    preparedSetup: CatalogScreenPreparedSetupBundle
): CatalogScreenPreparedSetupHandles {
    val context = preparedSetup.context
    val runtimeObjects = preparedSetup.runtimeObjects
    return CatalogScreenPreparedSetupHandles(
        contextHandles = CatalogScreenContextHandles(
            board = context.board,
            history = context.history,
            onBack = context.onBack,
            onThreadSelected = context.onThreadSelected,
            onHistoryEntrySelected = context.onHistoryEntrySelected,
            onHistoryEntryDismissed = context.onHistoryEntryDismissed,
            onHistoryEntryUpdated = context.onHistoryEntryUpdated,
            onHistoryRefresh = context.onHistoryRefresh,
            onHistoryCleared = context.onHistoryCleared,
            repository = context.repository,
            stateStore = context.stateStore,
            autoSavedThreadRepository = context.autoSavedThreadRepository,
            cookieRepository = context.cookieRepository,
            fileSystem = context.fileSystem,
            preferencesState = context.preferencesState,
            preferencesCallbacks = context.preferencesCallbacks,
            httpClient = context.httpClient,
            modifier = context.modifier
        ),
        runtimeHandles = CatalogScreenRuntimeHandles(
            snackbarHostState = runtimeObjects.snackbarHostState,
            coroutineScope = runtimeObjects.coroutineScope,
            drawerState = runtimeObjects.drawerState,
            isDrawerOpen = runtimeObjects.isDrawerOpen,
            archiveSearchJson = runtimeObjects.archiveSearchJson,
            catalogGridState = runtimeObjects.catalogGridState,
            catalogListState = runtimeObjects.catalogListState
        ),
        setupHandles = CatalogScreenSetupHandles(
            activeRepository = preparedSetup.activeRepository,
            archiveSearchScope = preparedSetup.archiveSearchScope,
            persistentBindings = preparedSetup.persistentBindings,
            urlLauncher = preparedSetup.urlLauncher
        ),
        mutableStateHandles = resolveCatalogScreenMutableStateHandles(preparedSetup.mutableStateRefs)
    )
}

@Composable
internal fun rememberCatalogScreenPreparedSetupHandles(
    preparedSetup: CatalogScreenPreparedSetupBundle
): CatalogScreenPreparedSetupHandles {
    return rememberResolvedSetupHandles(
        preparedSetup = preparedSetup,
        resolver = ::resolveCatalogScreenPreparedSetupHandles
    )
}

@Composable
internal fun rememberCatalogScreenPreparedSetupBundle(
    args: CatalogScreenContentArgs
): CatalogScreenPreparedSetupBundle {
    val context = args.resolveContentContext()
    val setupBundle = rememberCatalogScreenSetupBundle(
        board = context.board,
        repository = context.repository,
        stateStore = context.stateStore
    )
    val mutableStateRefs = rememberResolvedStateRefs(
        bundle = setupBundle.coreBindings.mutableStateBundle,
        resolver = ::resolveCatalogScreenMutableStateRefs
    )
    return remember(context, setupBundle, mutableStateRefs) {
        CatalogScreenPreparedSetupBundle(
            context = context,
            activeRepository = setupBundle.activeRepository,
            archiveSearchScope = setupBundle.archiveSearchScope,
            runtimeObjects = setupBundle.coreBindings.runtimeObjects,
            persistentBindings = setupBundle.coreBindings.persistentBindings,
            mutableStateRefs = mutableStateRefs,
            urlLauncher = setupBundle.urlLauncher
        )
    }
}

@Composable
internal fun rememberCatalogScreenSetupBundle(
    board: BoardSummary?,
    repository: BoardRepository?,
    stateStore: AppStateStore?
): CatalogScreenSetupBundle {
    val activeRepository = remember(repository) {
        repository ?: FakeBoardRepository()
    }
    val archiveSearchScope = remember(board?.url) {
        com.valoser.futacha.shared.network.extractArchiveSearchScope(board)
    }
    val coreBindings = rememberCatalogScreenCoreBindingsBundle(
        stateStore = stateStore,
        boardId = board?.id,
        boardUrl = board?.url,
        initialArchiveSearchScope = archiveSearchScope
    )
    val urlLauncher = rememberUrlLauncher()
    return remember(activeRepository, archiveSearchScope, coreBindings, urlLauncher) {
        CatalogScreenSetupBundle(
            activeRepository = activeRepository,
            archiveSearchScope = archiveSearchScope,
            coreBindings = coreBindings,
            urlLauncher = urlLauncher
        )
    }
}
