package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.rememberUrlLauncher

internal data class CatalogScreenSetupBundle(
    val activeRepository: BoardRepository,
    val archiveSearchScope: com.valoser.futacha.shared.network.ArchiveSearchScope?,
    val coreBindings: CatalogScreenCoreBindingsBundle,
    val urlLauncher: (String) -> Unit
)

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
