package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsEnabledValue
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
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
    val addGlobalWatchWordEntry: (String) -> Unit,
    val removeGlobalWatchWordEntry: (String) -> Unit,
    val addBoardWatchWordEntry: (String) -> Unit,
    val removeBoardWatchWordEntry: (String) -> Unit,
    val clearBoardWatchWordsOverride: () -> Unit,
    val updateCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit,
    val updateCatalogGridColumns: (Int) -> Unit,
    val scrollCatalogToTop: () -> Unit
)

internal fun buildCatalogScreenMutationBindings(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore?,
    currentBoardId: () -> String?,
    currentBoardWatchWordKey: () -> String?,
    setCatalogMode: (CatalogMode) -> Unit,
    currentCatalogNgWords: () -> List<String>,
    currentGlobalWatchWords: () -> List<String>,
    currentBoardWatchWords: () -> List<String>,
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
        currentBoardWatchWordKey = currentBoardWatchWordKey,
        onFallbackCatalogNgWordsChanged = onFallbackCatalogNgWordsChanged,
        onFallbackWatchWordsChanged = onFallbackWatchWordsChanged
    )
    fun showMutationMessage(message: String) {
        coroutineScope.launch { showSnackbar(message) }
    }
    fun catalogAnalyticsContext(): Map<String, String> = mapOf(
        "board_context" to analyticsSessionContextId("board", currentBoardId())
    )
    return CatalogScreenMutationBindings(
        persistCatalogMode = { mode ->
            AnalyticsTracker.event(
                "catalog_mode_changed",
                catalogAnalyticsContext() + mapOf(
                    "mode" to mode.name.lowercase(),
                    "mode_label" to mode.label
                )
            )
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
            AnalyticsTracker.event(
                "ng_word_changed",
                catalogAnalyticsContext() + mapOf(
                    "scope" to "catalog",
                    "scope_label" to "カタログNGワード",
                    "action" to "add",
                    "result" to if (mutation.shouldPersist) "accepted" else "rejected",
                    "entry_count_bucket" to analyticsCountBucket(mutation.updatedWords.size)
                )
            )
            if (mutation.shouldPersist) {
                persistenceBindings.persistCatalogNgWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        removeCatalogNgWordEntry = { entry ->
            val mutation = removeCatalogNgWord(currentCatalogNgWords(), entry)
            AnalyticsTracker.event(
                "ng_word_changed",
                catalogAnalyticsContext() + mapOf(
                    "scope" to "catalog",
                    "scope_label" to "カタログNGワード",
                    "action" to "remove",
                    "result" to if (mutation.shouldPersist) "accepted" else "rejected",
                    "entry_count_bucket" to analyticsCountBucket(mutation.updatedWords.size)
                )
            )
            if (mutation.shouldPersist) {
                persistenceBindings.persistCatalogNgWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        handleCatalogNgFilteringToggle = {
            val toggleState = toggleCatalogNgFiltering(currentCatalogNgFilteringEnabled())
            AnalyticsTracker.event(
                "ng_filter_toggled",
                catalogAnalyticsContext() + mapOf(
                    "scope" to "catalog",
                    "scope_label" to "カタログNGフィルター",
                    "state" to analyticsEnabledValue(toggleState.isEnabled)
                )
            )
            setCatalogNgFilteringEnabled(toggleState.isEnabled)
            showMutationMessage(toggleState.message)
        },
        addGlobalWatchWordEntry = { value ->
            val mutation = addWatchWord(currentGlobalWatchWords(), value)
            AnalyticsTracker.event(
                "watch_word_changed",
                catalogAnalyticsContext() + mapOf(
                    "scope" to "global",
                    "scope_label" to "共通監視ワード",
                    "action" to "add",
                    "result" to if (mutation.shouldPersist) "accepted" else "rejected",
                    "entry_count_bucket" to analyticsCountBucket(mutation.updatedWords.size)
                )
            )
            if (mutation.shouldPersist) {
                persistenceBindings.persistGlobalWatchWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        removeGlobalWatchWordEntry = { entry ->
            val mutation = removeWatchWord(currentGlobalWatchWords(), entry)
            AnalyticsTracker.event(
                "watch_word_changed",
                catalogAnalyticsContext() + mapOf(
                    "scope" to "global",
                    "scope_label" to "共通監視ワード",
                    "action" to "remove",
                    "result" to if (mutation.shouldPersist) "accepted" else "rejected",
                    "entry_count_bucket" to analyticsCountBucket(mutation.updatedWords.size)
                )
            )
            if (mutation.shouldPersist) {
                persistenceBindings.persistGlobalWatchWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        addBoardWatchWordEntry = { value ->
            val mutation = addWatchWord(currentBoardWatchWords(), value)
            AnalyticsTracker.event(
                "watch_word_changed",
                catalogAnalyticsContext() + mapOf(
                    "scope" to "board",
                    "scope_label" to "板別監視ワード",
                    "action" to "add",
                    "result" to if (mutation.shouldPersist) "accepted" else "rejected",
                    "entry_count_bucket" to analyticsCountBucket(mutation.updatedWords.size)
                )
            )
            if (mutation.shouldPersist) {
                persistenceBindings.persistBoardWatchWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        removeBoardWatchWordEntry = { entry ->
            val mutation = removeWatchWord(currentBoardWatchWords(), entry)
            AnalyticsTracker.event(
                "watch_word_changed",
                catalogAnalyticsContext() + mapOf(
                    "scope" to "board",
                    "scope_label" to "板別監視ワード",
                    "action" to "remove",
                    "result" to if (mutation.shouldPersist) "accepted" else "rejected",
                    "entry_count_bucket" to analyticsCountBucket(mutation.updatedWords.size)
                )
            )
            if (mutation.shouldPersist) {
                persistenceBindings.persistBoardWatchWords(mutation.updatedWords)
            }
            showMutationMessage(mutation.message)
        },
        clearBoardWatchWordsOverride = {
            AnalyticsTracker.event(
                "watch_word_changed",
                catalogAnalyticsContext() + mapOf(
                    "scope" to "board",
                    "scope_label" to "板別監視ワード",
                    "action" to "inherit",
                    "result" to "accepted"
                )
            )
            persistenceBindings.clearBoardWatchWordsOverride()
            showMutationMessage("この板の監視ワードを共通設定に戻しました")
        },
        updateCatalogDisplayStyle = { style ->
            AnalyticsTracker.event(
                "catalog_display_style_changed",
                catalogAnalyticsContext() + mapOf(
                    "style" to style.name.lowercase(),
                    "style_label" to style.label
                )
            )
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
            AnalyticsTracker.event(
                "catalog_grid_columns_changed",
                catalogAnalyticsContext() + mapOf("columns" to clamped.toString())
            )
            if (stateStore != null) {
                coroutineScope.launch {
                    stateStore.setCatalogGridColumns(clamped)
                }
            } else {
                setLocalCatalogGridColumns(clamped)
            }
        },
        scrollCatalogToTop = {
            AnalyticsTracker.event(
                "catalog_scroll_to_top",
                catalogAnalyticsContext() + mapOf(
                    "style" to currentCatalogDisplayStyle().name.lowercase(),
                    "style_label" to currentCatalogDisplayStyle().label
                )
            )
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
