package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val DEFAULT_CATALOG_SCREEN_GRID_COLUMNS = 5

internal fun resolveInitialCatalogMode(
    boardId: String?,
    persistedModes: Map<String, CatalogMode>
): CatalogMode {
    return persistedModes[boardId.orEmpty()] ?: CatalogMode.default
}

internal data class CatalogScreenPersistentBindings(
    val persistedCatalogModes: Map<String, CatalogMode>,
    val catalogNgWords: List<String>,
    val watchWords: List<String>,
    val lastUsedDeleteKey: String,
    val updateLastUsedDeleteKey: (String) -> Unit,
    val onFallbackCatalogNgWordsChanged: (List<String>) -> Unit,
    val onFallbackWatchWordsChanged: (List<String>) -> Unit,
    val isPrivacyFilterEnabled: Boolean,
    val persistentDisplayStyle: CatalogDisplayStyle,
    val persistentGridColumns: Int
)

@Composable
internal fun rememberCatalogScreenPersistentBindings(
    stateStore: AppStateStore?,
    coroutineScope: CoroutineScope,
    boardId: String?
): CatalogScreenPersistentBindings {
    val catalogModeMapState = stateStore?.catalogModes?.collectAsState(initial = emptyMap())
    val persistedCatalogModes = catalogModeMapState?.value ?: emptyMap()
    val fallbackCatalogNgWordsState = rememberSaveable(boardId) { mutableStateOf<List<String>>(emptyList()) }
    val catalogNgWordsState = stateStore?.catalogNgWords?.collectAsState(initial = fallbackCatalogNgWordsState.value)
    val fallbackWatchWordsState = rememberSaveable(boardId) { mutableStateOf<List<String>>(emptyList()) }
    val watchWordsState = stateStore?.watchWords?.collectAsState(initial = fallbackWatchWordsState.value)
    val lastUsedDeleteKeyState = stateStore?.lastUsedDeleteKey?.collectAsState(initial = "")
    var fallbackDeleteKey by rememberSaveable { mutableStateOf("") }
    val isPrivacyFilterEnabled by stateStore?.isPrivacyFilterEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val persistentDisplayStyleState =
        stateStore?.catalogDisplayStyle?.collectAsState(initial = CatalogDisplayStyle.Grid)
    val persistentGridColumnsState =
        stateStore?.catalogGridColumns?.collectAsState(initial = DEFAULT_CATALOG_SCREEN_GRID_COLUMNS)

    return CatalogScreenPersistentBindings(
        persistedCatalogModes = persistedCatalogModes,
        catalogNgWords = catalogNgWordsState?.value ?: fallbackCatalogNgWordsState.value,
        watchWords = watchWordsState?.value ?: fallbackWatchWordsState.value,
        lastUsedDeleteKey = lastUsedDeleteKeyState?.value ?: fallbackDeleteKey,
        updateLastUsedDeleteKey = { value ->
            val sanitized = sanitizeStoredDeleteKey(value)
            val store = stateStore
            if (store != null) {
                coroutineScope.launch { store.setLastUsedDeleteKey(sanitized) }
            } else {
                fallbackDeleteKey = sanitized
            }
        },
        onFallbackCatalogNgWordsChanged = { fallbackCatalogNgWordsState.value = it },
        onFallbackWatchWordsChanged = { fallbackWatchWordsState.value = it },
        isPrivacyFilterEnabled = isPrivacyFilterEnabled,
        persistentDisplayStyle = persistentDisplayStyleState?.value ?: CatalogDisplayStyle.Grid,
        persistentGridColumns = persistentGridColumnsState?.value ?: DEFAULT_CATALOG_SCREEN_GRID_COLUMNS
    )
}

internal data class CatalogScreenMutableStateBundle(
    val uiState: MutableState<CatalogUiState>,
    val catalogMode: MutableState<CatalogMode>,
    val isRefreshing: MutableState<Boolean>,
    val catalogLoadJob: MutableState<Job?>,
    val catalogLoadGeneration: MutableState<Long>,
    val isHistoryRefreshing: MutableState<Boolean>,
    val isSearchActive: MutableState<Boolean>,
    val searchQuery: MutableState<String>,
    val debouncedSearchQuery: MutableState<String>,
    val overlayState: MutableState<CatalogOverlayState>,
    val createThreadDraft: MutableState<CreateThreadDraft>,
    val createThreadImage: MutableState<ImageData?>,
    val archiveSearchQuery: MutableState<String>,
    val catalogNgFilteringEnabled: MutableState<Boolean>,
    val pastSearchRuntimeState: MutableState<CatalogPastSearchRuntimeState>,
    val lastCatalogItems: MutableState<List<com.valoser.futacha.shared.model.CatalogItem>>,
    val localCatalogDisplayStyle: MutableState<CatalogDisplayStyle>,
    val localCatalogGridColumns: MutableState<Int>
)

@Composable
internal fun rememberCatalogScreenMutableStateBundle(
    boardId: String?,
    initialCatalogMode: CatalogMode,
    initialArchiveSearchScope: ArchiveSearchScope?,
    initialDisplayStyle: CatalogDisplayStyle,
    initialGridColumns: Int
): CatalogScreenMutableStateBundle {
    return CatalogScreenMutableStateBundle(
        uiState = remember { mutableStateOf(CatalogUiState.Loading) },
        catalogMode = rememberSaveable(boardId) { mutableStateOf(initialCatalogMode) },
        isRefreshing = remember { mutableStateOf(false) },
        catalogLoadJob = remember { mutableStateOf(null as Job?) },
        catalogLoadGeneration = remember { mutableStateOf(0L) },
        isHistoryRefreshing = remember { mutableStateOf(false) },
        isSearchActive = rememberSaveable(boardId) { mutableStateOf(false) },
        searchQuery = rememberSaveable(boardId) { mutableStateOf("") },
        debouncedSearchQuery = rememberSaveable(boardId) { mutableStateOf("") },
        overlayState = remember { mutableStateOf(CatalogOverlayState()) },
        createThreadDraft = rememberSaveable(
            boardId,
            stateSaver = CreateThreadDraftSaver
        ) { mutableStateOf(emptyCreateThreadDraft()) },
        createThreadImage = remember(boardId) { mutableStateOf(null as ImageData?) },
        archiveSearchQuery = rememberSaveable(boardId) { mutableStateOf("") },
        catalogNgFilteringEnabled = rememberSaveable(boardId) { mutableStateOf(true) },
        pastSearchRuntimeState = remember {
            mutableStateOf(resetCatalogPastSearchRuntimeState(initialArchiveSearchScope))
        },
        lastCatalogItems = remember { mutableStateOf(emptyList()) },
        localCatalogDisplayStyle = rememberSaveable { mutableStateOf(initialDisplayStyle) },
        localCatalogGridColumns = rememberSaveable { mutableStateOf(initialGridColumns) }
    )
}
