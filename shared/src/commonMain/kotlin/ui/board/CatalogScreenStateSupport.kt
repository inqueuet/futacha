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
private const val DEFAULT_CATALOG_SCREEN_SAVEABLE_KEY = "__catalog_screen_default__"

internal fun resolveCatalogScreenSaveableKey(
    boardId: String?,
    boardUrl: String?
): String {
    val normalizedBoardId = boardId?.trim().orEmpty()
    if (normalizedBoardId.isNotEmpty()) {
        return "board:$normalizedBoardId"
    }
    val normalizedBoardUrl = boardUrl?.trim().orEmpty()
    if (normalizedBoardUrl.isNotEmpty()) {
        return "url:$normalizedBoardUrl"
    }
    return DEFAULT_CATALOG_SCREEN_SAVEABLE_KEY
}

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
    saveableKey: String
): CatalogScreenPersistentBindings {
    val catalogModeMapState = stateStore?.catalogModes?.collectAsState(initial = emptyMap())
    val persistedCatalogModes = catalogModeMapState?.value ?: emptyMap()
    val fallbackCatalogNgWordsState = rememberSaveable(saveableKey) { mutableStateOf<List<String>>(emptyList()) }
    val catalogNgWordsState = stateStore?.catalogNgWords?.collectAsState(initial = fallbackCatalogNgWordsState.value)
    val fallbackWatchWordsState = rememberSaveable(saveableKey) { mutableStateOf<List<String>>(emptyList()) }
    val watchWordsState = stateStore?.watchWords?.collectAsState(initial = fallbackWatchWordsState.value)
    val lastUsedDeleteKeyState = stateStore?.lastUsedDeleteKey?.collectAsState(initial = "")
    var fallbackDeleteKey by rememberSaveable(saveableKey) { mutableStateOf("") }
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

internal data class CatalogScreenUiRuntimeMutableStateRefs(
    val uiState: MutableState<CatalogUiState>,
    val catalogMode: MutableState<CatalogMode>,
    val isRefreshing: MutableState<Boolean>,
    val catalogLoadJob: MutableState<Job?>,
    val catalogLoadGeneration: MutableState<Long>,
    val isHistoryRefreshing: MutableState<Boolean>,
    val lastCatalogItems: MutableState<List<com.valoser.futacha.shared.model.CatalogItem>>
)

internal data class CatalogScreenSearchOverlayMutableStateRefs(
    val isSearchActive: MutableState<Boolean>,
    val searchQuery: MutableState<String>,
    val debouncedSearchQuery: MutableState<String>,
    val overlayState: MutableState<CatalogOverlayState>,
    val archiveSearchQuery: MutableState<String>,
    val catalogNgFilteringEnabled: MutableState<Boolean>,
    val pastSearchRuntimeState: MutableState<CatalogPastSearchRuntimeState>
)

internal data class CatalogScreenDraftDisplayMutableStateRefs(
    val createThreadDraft: MutableState<CreateThreadDraft>,
    val createThreadImage: MutableState<ImageData?>,
    val localCatalogDisplayStyle: MutableState<CatalogDisplayStyle>,
    val localCatalogGridColumns: MutableState<Int>
)

internal data class CatalogScreenMutableStateRefs(
    val uiRuntime: CatalogScreenUiRuntimeMutableStateRefs,
    val searchOverlay: CatalogScreenSearchOverlayMutableStateRefs,
    val draftDisplay: CatalogScreenDraftDisplayMutableStateRefs
)

internal data class CatalogScreenMutableStateHandles(
    val uiRuntimeStateRefs: CatalogScreenUiRuntimeMutableStateRefs,
    val searchOverlayStateRefs: CatalogScreenSearchOverlayMutableStateRefs,
    val draftDisplayStateRefs: CatalogScreenDraftDisplayMutableStateRefs
)

internal fun resolveCatalogScreenMutableStateHandles(
    refs: CatalogScreenMutableStateRefs
): CatalogScreenMutableStateHandles {
    return CatalogScreenMutableStateHandles(
        uiRuntimeStateRefs = refs.uiRuntime,
        searchOverlayStateRefs = refs.searchOverlay,
        draftDisplayStateRefs = refs.draftDisplay
    )
}

internal fun resolveCatalogScreenMutableStateRefs(
    bundle: CatalogScreenMutableStateBundle
): CatalogScreenMutableStateRefs {
    return CatalogScreenMutableStateRefs(
        uiRuntime = CatalogScreenUiRuntimeMutableStateRefs(
            uiState = bundle.uiState,
            catalogMode = bundle.catalogMode,
            isRefreshing = bundle.isRefreshing,
            catalogLoadJob = bundle.catalogLoadJob,
            catalogLoadGeneration = bundle.catalogLoadGeneration,
            isHistoryRefreshing = bundle.isHistoryRefreshing,
            lastCatalogItems = bundle.lastCatalogItems
        ),
        searchOverlay = CatalogScreenSearchOverlayMutableStateRefs(
            isSearchActive = bundle.isSearchActive,
            searchQuery = bundle.searchQuery,
            debouncedSearchQuery = bundle.debouncedSearchQuery,
            overlayState = bundle.overlayState,
            archiveSearchQuery = bundle.archiveSearchQuery,
            catalogNgFilteringEnabled = bundle.catalogNgFilteringEnabled,
            pastSearchRuntimeState = bundle.pastSearchRuntimeState
        ),
        draftDisplay = CatalogScreenDraftDisplayMutableStateRefs(
            createThreadDraft = bundle.createThreadDraft,
            createThreadImage = bundle.createThreadImage,
            localCatalogDisplayStyle = bundle.localCatalogDisplayStyle,
            localCatalogGridColumns = bundle.localCatalogGridColumns
        )
    )
}

@Composable
internal fun rememberCatalogScreenMutableStateBundle(
    saveableKey: String,
    initialCatalogMode: CatalogMode,
    initialArchiveSearchScope: ArchiveSearchScope?,
    initialDisplayStyle: CatalogDisplayStyle,
    initialGridColumns: Int
): CatalogScreenMutableStateBundle {
    return CatalogScreenMutableStateBundle(
        uiState = remember { mutableStateOf(CatalogUiState.Loading) },
        catalogMode = rememberSaveable(saveableKey) { mutableStateOf(initialCatalogMode) },
        isRefreshing = remember { mutableStateOf(false) },
        catalogLoadJob = remember { mutableStateOf(null as Job?) },
        catalogLoadGeneration = remember { mutableStateOf(0L) },
        isHistoryRefreshing = remember { mutableStateOf(false) },
        isSearchActive = rememberSaveable(saveableKey) { mutableStateOf(false) },
        searchQuery = rememberSaveable(saveableKey) { mutableStateOf("") },
        debouncedSearchQuery = rememberSaveable(saveableKey) { mutableStateOf("") },
        overlayState = remember { mutableStateOf(CatalogOverlayState()) },
        createThreadDraft = rememberSaveable(
            saveableKey,
            stateSaver = CreateThreadDraftSaver
        ) { mutableStateOf(emptyCreateThreadDraft()) },
        createThreadImage = remember(saveableKey) { mutableStateOf(null as ImageData?) },
        archiveSearchQuery = rememberSaveable(saveableKey) { mutableStateOf("") },
        catalogNgFilteringEnabled = rememberSaveable(saveableKey) { mutableStateOf(true) },
        pastSearchRuntimeState = remember {
            mutableStateOf(resetCatalogPastSearchRuntimeState(initialArchiveSearchScope))
        },
        lastCatalogItems = remember { mutableStateOf(emptyList()) },
        localCatalogDisplayStyle = rememberSaveable(saveableKey) { mutableStateOf(initialDisplayStyle) },
        localCatalogGridColumns = rememberSaveable(saveableKey) { mutableStateOf(initialGridColumns) }
    )
}
