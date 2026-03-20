package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.rememberUrlLauncher
import io.ktor.client.HttpClient

internal data class CatalogScreenContentContext(
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

internal fun CatalogScreenContentArgs.resolveContentContext(): CatalogScreenContentContext {
    return CatalogScreenContentContext(
        board = board,
        history = history,
        onBack = onBack,
        onThreadSelected = onThreadSelected,
        onHistoryEntrySelected = historyCallbacks.onHistoryEntrySelected,
        onHistoryEntryDismissed = historyCallbacks.onHistoryEntryDismissed,
        onHistoryEntryUpdated = historyCallbacks.onHistoryEntryUpdated,
        onHistoryRefresh = historyCallbacks.onHistoryRefresh,
        onHistoryCleared = historyCallbacks.onHistoryCleared,
        repository = dependencies.repository,
        stateStore = dependencies.stateStore,
        autoSavedThreadRepository = dependencies.autoSavedThreadRepository,
        cookieRepository = dependencies.cookieRepository,
        fileSystem = dependencies.fileSystem,
        preferencesState = preferencesState,
        preferencesCallbacks = preferencesCallbacks,
        httpClient = dependencies.httpClient,
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
    val archiveSearchScope: com.valoser.futacha.shared.network.ArchiveSearchScope?,
    val runtimeObjects: CatalogScreenRuntimeObjectsBundle,
    val persistentBindings: CatalogScreenPersistentBindings,
    val mutableStateRefs: CatalogScreenMutableStateRefs,
    val urlLauncher: (String) -> Unit
)

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
    val mutableStateRefs = remember(setupBundle.coreBindings.mutableStateBundle) {
        resolveCatalogScreenMutableStateRefs(setupBundle.coreBindings.mutableStateBundle)
    }
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
