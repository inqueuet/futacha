package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class CatalogScreenMutationBindings(
    val persistCatalogMode: (CatalogMode) -> Unit,
    val addCatalogNgWordEntry: (String) -> Unit,
    val removeCatalogNgWordEntry: (String) -> Unit,
    val handleCatalogNgFilteringToggle: () -> Unit,
    val addWatchWordEntry: (String) -> Unit,
    val removeWatchWordEntry: (String) -> Unit,
    val updateCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit,
    val updateCatalogGridColumns: (Int) -> Unit,
    val scrollCatalogToTop: () -> Unit
)

internal fun buildCatalogScreenMutationBindings(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore?,
    currentBoardId: () -> String?,
    setCatalogMode: (CatalogMode) -> Unit,
    currentCatalogNgWords: () -> List<String>,
    currentWatchWords: () -> List<String>,
    currentCatalogNgFilteringEnabled: () -> Boolean,
    setCatalogNgFilteringEnabled: (Boolean) -> Unit,
    onFallbackCatalogNgWordsChanged: (List<String>) -> Unit,
    onFallbackWatchWordsChanged: (List<String>) -> Unit,
    showSnackbar: suspend (String) -> Unit,
    setLocalCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit,
    setLocalCatalogGridColumns: (Int) -> Unit,
    currentCatalogDisplayStyle: () -> CatalogDisplayStyle,
    catalogGridState: LazyGridState,
    catalogListState: LazyListState
): CatalogScreenMutationBindings {
    val persistenceBindings = buildCatalogPersistenceBindings(
        coroutineScope = coroutineScope,
        stateStore = stateStore,
        onFallbackCatalogNgWordsChanged = onFallbackCatalogNgWordsChanged,
        onFallbackWatchWordsChanged = onFallbackWatchWordsChanged
    )
    fun showMutationMessage(message: String) {
        coroutineScope.launch { showSnackbar(message) }
    }
    return CatalogScreenMutationBindings(
        persistCatalogMode = { mode ->
            setCatalogMode(mode)
            val boardId = currentBoardId()
            if (boardId != null && stateStore != null) {
                coroutineScope.launch {
                    stateStore.setCatalogMode(boardId, mode)
                }
            }
        },
        addCatalogNgWordEntry = { value ->
            val mutation = addCatalogNgWord(currentCatalogNgWords(), value)
            if (mutation.shouldPersist) {
                persistenceBindings.persistCatalogNgWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        removeCatalogNgWordEntry = { entry ->
            val mutation = removeCatalogNgWord(currentCatalogNgWords(), entry)
            if (mutation.shouldPersist) {
                persistenceBindings.persistCatalogNgWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        handleCatalogNgFilteringToggle = {
            val toggleState = toggleCatalogNgFiltering(currentCatalogNgFilteringEnabled())
            setCatalogNgFilteringEnabled(toggleState.isEnabled)
            showMutationMessage(toggleState.message)
        },
        addWatchWordEntry = { value ->
            val mutation = addWatchWord(currentWatchWords(), value)
            if (mutation.shouldPersist) {
                persistenceBindings.persistWatchWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        removeWatchWordEntry = { entry ->
            val mutation = removeWatchWord(currentWatchWords(), entry)
            if (mutation.shouldPersist) {
                persistenceBindings.persistWatchWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        updateCatalogDisplayStyle = { style ->
            if (stateStore != null) {
                coroutineScope.launch {
                    stateStore.setCatalogDisplayStyle(style)
                }
            } else {
                setLocalCatalogDisplayStyle(style)
            }
        },
        updateCatalogGridColumns = { columns ->
            val clamped = columns.coerceIn(MIN_CATALOG_GRID_COLUMNS, MAX_CATALOG_GRID_COLUMNS)
            if (stateStore != null) {
                coroutineScope.launch {
                    stateStore.setCatalogGridColumns(clamped)
                }
            } else {
                setLocalCatalogGridColumns(clamped)
            }
        },
        scrollCatalogToTop = {
            coroutineScope.launch {
                when (currentCatalogDisplayStyle()) {
                    CatalogDisplayStyle.Grid -> {
                        if (catalogGridState.layoutInfo.totalItemsCount > 0) {
                            catalogGridState.animateScrollToItem(0)
                        }
                    }
                    CatalogDisplayStyle.List -> {
                        if (catalogListState.layoutInfo.totalItemsCount > 0) {
                            catalogListState.animateScrollToItem(0)
                        }
                    }
                }
            }
        }
    )
}
