package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.ArchiveSearchScope

internal data class CatalogPastSearchResetState(
    val runtimeState: CatalogPastSearchRuntimeState,
    val overlayState: CatalogOverlayState
)

internal fun resolveCatalogModeSyncValue(
    boardId: String?,
    persistedCatalogModes: Map<String, CatalogMode>
): CatalogMode? {
    val normalizedBoardId = boardId?.trim().orEmpty()
    if (normalizedBoardId.isBlank()) return null
    return persistedCatalogModes[normalizedBoardId]
}

internal fun resolveCatalogPastSearchResetState(
    scope: ArchiveSearchScope?,
    overlayState: CatalogOverlayState
): CatalogPastSearchResetState {
    return CatalogPastSearchResetState(
        runtimeState = resetCatalogPastSearchRuntimeState(scope),
        overlayState = resetCatalogPastSearchOverlayState(overlayState)
    )
}

internal fun resolveCatalogDebouncedSearchQuery(query: String): String {
    return query.trim()
}
