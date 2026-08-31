package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsBoardKind
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.ArchiveSearchScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

internal fun resolveCatalogSearchDebounceMillis(query: String): Long {
    return if (query.isEmpty()) 0L else 200L
}

/**
 * Records the range of catalog cards that became visible. It does not inspect
 * titles or URLs, and records only each 20-card boundary to avoid scroll spam.
 */
@Composable
internal fun CatalogBrowseProgressObservationEffect(
    board: BoardSummary?,
    displayStyle: CatalogDisplayStyle,
    catalogItemCount: Int,
    gridState: LazyGridState,
    listState: LazyListState
) {
    val activeBoard = board ?: return
    LaunchedEffect(activeBoard.id, activeBoard.url, displayStyle, catalogItemCount, gridState, listState) {
        snapshotFlow {
            when (displayStyle) {
                CatalogDisplayStyle.Grid -> gridState.firstVisibleItemIndex
                CatalogDisplayStyle.List -> listState.firstVisibleItemIndex
            }
        }
            .map { index -> (index.coerceAtLeast(0) / CATALOG_BROWSE_PROGRESS_STEP) * CATALOG_BROWSE_PROGRESS_STEP }
            .distinctUntilChanged()
            .collect { startIndex ->
                AnalyticsTracker.event(
                    "catalog_browse_progress",
                    mapOf(
                        "board_context" to analyticsSessionContextId("board", activeBoard.id, activeBoard.url),
                        "board_kind" to analyticsBoardKind(activeBoard.url),
                        "display_style" to displayStyle.name.lowercase(),
                        "visible_card_range" to "${startIndex}_${startIndex + CATALOG_BROWSE_PROGRESS_STEP - 1}",
                        "catalog_item_count_bucket" to analyticsCountBucket(catalogItemCount)
                    )
                )
            }
    }
}

private const val CATALOG_BROWSE_PROGRESS_STEP = 20
