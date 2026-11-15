package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.BoardSite
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.boardSiteOf
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.futaber.futaberFetchCatalogPage

/** The MIX keeps each mode's request contract and title-completion behavior. */
internal enum class MixModePolicy(val completeTitles: Boolean) {
    FUTACHA(true), COMPAT(true), FUTABER(false), FTUTTER(false)
}

internal suspend fun fetchMixCatalogPageForMode(
    repository: BoardRepository, store: CompatibilityStore, board: String, mode: CatalogMode, policy: MixModePolicy
): CatalogPageContent {
    if (boardSiteOf(board) == BoardSite.AIMOGE) return repository.getCatalogPage(board, mode)
    return when (policy) {
        MixModePolicy.FUTACHA, MixModePolicy.COMPAT -> fetchMixCatalogPage(repository, store, board, mode)
        MixModePolicy.FUTABER -> futaberFetchCatalogPage(repository, board, mode, emptyList())
        MixModePolicy.FTUTTER -> repository.getCatalogPage(com.valoser.futacha.shared.ui.ftutter.ftutterNetworkUrl(board), mode)
    }
}
