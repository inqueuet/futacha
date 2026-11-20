package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ui.util.ScrollableToolbarRow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.ui.compat.CompatFastScrollbar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.compat.CompatCatalogRuleIndex
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatImagePhashCachePreferenceKey
import com.valoser.futacha.shared.compat.isValidCompatImagePhash
import com.valoser.futacha.shared.ui.compat.collectCompatImagePhashes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** What the catalog screen currently holds. Items survive a failed refresh. */
internal data class FutaberCatalogLoad(
    val items: List<CatalogItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * What the catalog of one board and sort holds while a thread is open over it: the list, the reloads' history, the
 * scroll positions and the refresh counter. [FutaberApp] keeps it, so coming back from a thread finds the catalog as
 * it was left (the screen itself leaves the composition while a thread is shown).
 */
@Stable
internal class FutaberCatalogViewState {
    var load by mutableStateOf(FutaberCatalogLoad())
    var history by mutableStateOf(FutaberCatalogHistory())
    var refreshTick by mutableIntStateOf(0)
    var scrollToTopTick by mutableIntStateOf(0)
    /** The last "scroll to the top" this state already carried out (not state: nothing draws from it). */
    var scrollToTopHandled: Int = 0
    var lastAttemptKey: FutaberCatalogFetchKey? = null
    var viewedFirstId by mutableStateOf<String?>(null)
    /**
     * Set when a thread was created on this board: the reload it causes does not put the last viewed thread above the
     * new one (L37). Another order has its own state, so a change of the order drops the placement already.
     */
    var skipViewedFirstOnce: Boolean = false
    fun afterThreadCreated() {
        viewedFirstId = null
        skipViewedFirstOnce = true
    }
    var fetchedKey: FutaberCatalogFetchKey? = null
    var fetchedAtMillis: Long = 0L
    val gridState = LazyGridState()
    val listState = LazyListState()
}

/** The search of one board's catalog: whether the bar is open and what is typed (kept across a change of the sort). */
@Stable
internal class FutaberCatalogSearchState {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
}


@OptIn(ExperimentalTime::class)
@Composable
internal fun FutaberCatalogScreen(
    board: BoardSummary,
    repository: BoardRepository,
    displayStyle: FutaberCatalogDisplayStyle,
    sortMode: CatalogMode,
    onDisplayStyleChange: (FutaberCatalogDisplayStyle) -> Unit,
    onSortChange: (CatalogMode) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManage: () -> Unit,
    onCreateThread: () -> Unit,
    refreshSignal: Int,
    /** Threads whose title holds one of these are left out (the catalog NG words shared with ふたちゃ). */
    catalogNgWords: List<String> = emptyList(),
    catalogSharedNg: com.valoser.futacha.shared.ui.ftutter.FtutterNgMatcher? = null,
    /** The NG threads / words / images ふたちゃ and としあき(仮) registered for this board; null = none. */
    catalogNgIndex: CompatCatalogRuleIndex? = null,
    onOpenThread: (CatalogItem) -> Unit,
    /** Runs the typed search word against the archives too ("過去ログ検索"); null leaves the search as it was. */
    archiveHttpClient: io.ktor.client.HttpClient? = null,
    /** The actions of a long-pressed thread (the original app opens an action sheet); null turns the long press off. */
    longPressActions: ((CatalogItem) -> List<FutaberPostAction>)? = null,
    /** The perceptual-hash NG rules ([CompatNgKind.CATALOG_IMAGE_PHASH]) of ふたちゃ and としあき(仮) that apply to this board. */
    phashRules: List<CompatNgRule> = emptyList(),
    phashThreshold: Int = CompatImagePhash.DEFAULT_THRESHOLD,
    /** Where the pictures' hashes are kept between runs (the cache the other modes share); null keeps them in memory only. */
    phashStore: CompatibilityStore? = null,
    /** Kept by the caller so the list, the scroll and the search survive a thread being opened over the catalog. */
    view: FutaberCatalogViewState = remember(board.id, sortMode) { FutaberCatalogViewState() },
    search: FutaberCatalogSearchState = remember(board.id) { FutaberCatalogSearchState() },
    tabStrip: @Composable () -> Unit = {},
    /** B7a (FutaberParityB7a.kt): "そ順" / "監視" in the sort menu, and the words "監視" fetches with. */
    extraSorts: Boolean = false,
    watchWords: List<String> = emptyList(),
    /** B7a: the watch-word badges / order and the reply priority; null leaves the list as it is. */
    projection: FutaberCatalogProjection? = null,
    /** B7a: rows added to the refresh button's sheet, and going back several reloads at once. */
    refreshActions: List<FutaberPostAction> = emptyList(),
    perGenerationBack: Boolean = false,
    viewedFirstEnabled: Boolean = false,
    lastViewedThreadId: String? = null,
    onViewedFirstChange: (Boolean) -> Unit = {},
    /** Swipes on the list, as in the original: to the right the board list, to the left [onSwipeForward]. Off while the host has something on top. */
    swipeEnabled: Boolean = false,
    /** The left swipe: back into the thread last read on this board; null does nothing. */
    onSwipeForward: (() -> Unit)? = null,
    /** V12: the threads of this board in the shared history (any mode read them); their rows are grey. */
    readThreadIds: Set<String> = emptySet(),
    /** S22 (L-B7): a row's thread loads first (the row blue, "Loading..."); null opens at once as before. */
    opener: FutaberCatalogOpener? = null,
    /** S22: what a row's tap does with [opener] (the archive and dropped sheets keep [onOpenThread]). */
    onOpenRow: ((CatalogItem) -> Unit)? = null
) {
    val colors = LocalFutaberColors.current
    val display = LocalFutaberDisplaySettings.current
    LaunchedEffect(viewedFirstEnabled) { if (!viewedFirstEnabled) view.viewedFirstId = null }
    val load = view.load
    var serverSearchOpen by remember(board.id) { mutableStateOf(false) }
    val canServerSearch = archiveHttpClient != null && com.valoser.futacha.shared.network.serverPostSearchTitle(board.url) != null
    if (serverSearchOpen && archiveHttpClient != null) com.valoser.futacha.shared.ui.board.FutabaServerSearchDialog(
        archiveHttpClient, board.url, { serverSearchOpen = false }, { serverSearchOpen = false; onOpenThread(it) }
    )
    val searchOpen = search.open
    val query = search.query
    var sheetItem by remember(board.id) { mutableStateOf<CatalogItem?>(null) }
    // The word of a running 過去ログ検索; null = no archive search is open.
    var archiveQuery by remember(board.id) { mutableStateOf<String?>(null) }
    // The keyboard goes away when the results open, so they are not hidden behind it.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // Android's back closes the search bar like its cancel (L27: the app went to the background instead). The archive
    // results, registered after, close first. iOS keeps the original: the left edge does nothing here.
    // S25: cancel empties the word and goes back to the top with every thread (futaber's).
    val cancelSearch: () -> Unit = { search.query = ""; search.open = false; view.scrollToTopTick += 1 }
    com.valoser.futacha.shared.ui.util.PlatformBackHandler(enabled = searchOpen && archiveQuery == null, iosEdgeGestureEnabled = false) {
        cancelSearch()
    }
    // V29: the display-style or sort popover, pointing at its button.
    var menu by remember { mutableStateOf<FutaberCatalogMenuRequest?>(null) }
    com.valoser.futacha.shared.ui.util.PlatformBackHandler(enabled = archiveQuery != null) { archiveQuery = null }
    // The reloads of this board and sort: what to go back to, and which threads dropped out.
    var refreshSheet by remember(board.id, sortMode) { mutableStateOf(false) }
    // S22: another board (or leaving the catalog) stops a row's load.
    DisposableEffect(board.id, opener) { onDispose { opener?.cancel() } }
    var droppedSheet by remember(board.id, sortMode) { mutableStateOf(false) }

    // The "監視" order lists the threads matching the watch words, so a change of the words fetches it again (audit L23).
    val fetchWatchWords = if (sortMode == CatalogMode.WatchWords) watchWords else emptyList()
    LaunchedEffect(board.id, board.url, sortMode, view.refreshTick, refreshSignal, fetchWatchWords, display.catalogThreadCount) {
        val key = FutaberCatalogFetchKey(board.url, sortMode, view.refreshTick, refreshSignal, fetchWatchWords)
        val startedAt = Clock.System.now().toEpochMilliseconds()
        // Coming back from a thread: the catalog on hand is still current, so keep it (and its scroll) as it is.
        if (futaberCatalogCanReuse(view.fetchedKey, key, view.load.items.isNotEmpty(), startedAt - view.fetchedAtMillis)) {
            if (view.load.isLoading || view.load.errorMessage != null) view.load = view.load.copy(isLoading = false, errorMessage = null)
            return@LaunchedEffect
        }
        val explicitRefresh = futaberCatalogIsExplicitRefresh(view.lastAttemptKey, key)
        view.lastAttemptKey = key
        view.load = view.load.copy(isLoading = true, errorMessage = null)
        try {
            val page = futaberFetchCatalogPage(repository, board.url, sortMode, watchWords, display.catalogThreadCount)
            view.history = futaberCatalogHistoryAfterLoad(
                view.history, view.load.items, page.items,
                sameQuery = view.fetchedKey == null || view.fetchedKey?.watchWords == key.watchWords
            )
            view.load = FutaberCatalogLoad(items = page.items, isLoading = false)
            if (view.skipViewedFirstOnce) {
                view.skipViewedFirstOnce = false
                view.viewedFirstId = null
            } else if (explicitRefresh) view.viewedFirstId = lastViewedThreadId.takeIf { viewedFirstEnabled }
            view.fetchedKey = key
            view.fetchedAtMillis = Clock.System.now().toEpochMilliseconds()
            if (explicitRefresh && display.scrollCatalogToTopOnRefresh) view.scrollToTopTick += 1
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            view.skipViewedFirstOnce = false
            view.load = view.load.copy(isLoading = false, errorMessage = futaberFriendlyLoadError(error, "カタログ"))
        }
    }
    // The threads whose picture looks like a registered NG image (the hash rules of the other modes), found by hashing the
    // pictures: the ones already known first, then the rest as they arrive. Threads vanish from the list as hashes come in.
    val cachedImageLoader = com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader.current
    val phashHiddenIds by produceState(emptySet<String>(), load.items, phashRules, phashThreshold, archiveHttpClient, phashStore) {
        val client = archiveHttpClient
        if (phashRules.isEmpty() || client == null) {
            value = emptySet()
            return@produceState
        }
        val candidates = futaberCatalogPhashCandidates(load.items)
        val stored = try {
            phashStore?.loadImagePhashes(candidates.map { compatImagePhashCachePreferenceKey(it.second) }).orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e("FutaberCatalog", "Failed to load image hashes", error)
            emptyMap()
        }
        val known = candidates.mapNotNull { (id, url) ->
            stored[compatImagePhashCachePreferenceKey(url)]?.takeIf(::isValidCompatImagePhash)?.let { id to it }
        }.toMap()
        value = futaberPhashHiddenIds(known, phashRules, phashThreshold)
        val missing = candidates.filterNot { it.first in known }
        if (missing.isEmpty()) return@produceState
        val computed = linkedMapOf<String, String>()
        try {
            collectCompatImagePhashes(
                client, missing,
                cachedImageLoader = cachedImageLoader,
                fallbackImageUrl = { id -> load.items.firstOrNull { it.id == id }?.thumbnailUrl },
                resolveMissingUrl = { id -> com.valoser.futacha.shared.ui.compat.resolveCompatCatalogPhashUrl(load.items.firstOrNull { it.id == id }?.thumbnailUrl) {
                    repository.fetchOpImageUrl(board.url, id)
                } },
                onComputed = { id, hash -> computed[id] = hash },
                onPartial = { value = futaberPhashHiddenIds(known + computed, phashRules, phashThreshold) }
            )
            value = futaberPhashHiddenIds(known + computed, phashRules, phashThreshold)
        } finally {
            // Hashes found before a reload or a close are kept for next time.
            val urlById = missing.toMap()
            val toSave = computed.mapNotNull { (id, hash) -> urlById[id]?.let { compatImagePhashCachePreferenceKey(it) to hash } }.toMap()
            if (phashStore != null && toSave.isNotEmpty()) {
                withContext(NonCancellable) {
                    try {
                        phashStore.saveImagePhashes(toSave)
                    } catch (error: Throwable) {
                        Logger.e("FutaberCatalog", "Failed to save image hashes", error)
                    }
                }
            }
        }
    }
    // The NG rules are applied when the list or the rules change, not at every key of the search; the search then only
    // compares the typed word (a moment after the last key) with the titles, folded once.
    val ngInput = listOf(board.id, load.items, catalogNgWords, catalogNgIndex, phashHiddenIds, catalogSharedNg)
    val ngResult by produceState<Pair<List<Any?>, List<CatalogItem>>?>(null, ngInput) {
        val filtered = withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
            futaberApplyCatalogRules(load.items, catalogNgWords, catalogNgIndex, phashHiddenIds, catalogSharedNg)
        }
        value = ngInput to filtered
    }
    // Keep this board's last filtered list until the next result is ready. Clearing it between partial image-hash
    // updates would reset the LazyList position; another board must never borrow the previous board's rows.
    val ngVisibleItems = ngResult?.takeIf { it.first.first() == board.id }?.second.orEmpty()
    // The B7a reordering (watch words first, few replies later / hidden) runs once per list, before the search.
    // The watch-word matching (every thread against every word and extraction rule) runs on the parsing dispatcher, once
    // per list and settings (audit L24: it ran in composition, again whenever any setting changed). Until it is ready the
    // list shows in its own order without badges; the reordering alone (no matching) stays immediate.
    val needsMatching = projection?.highlight == true && !projection.watch.isEmpty
    val projectedNow = remember(ngVisibleItems, projection, needsMatching) {
        if (needsMatching) null else futaberProjectCatalog(ngVisibleItems, projection)
    }
    val projectedLater by produceState<Triple<List<CatalogItem>, FutaberCatalogProjection?, FutaberProjectedCatalog>?>(
        null, ngVisibleItems, projection, needsMatching
    ) {
        if (!needsMatching) return@produceState
        value = Triple(ngVisibleItems, projection, withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) { futaberProjectCatalog(ngVisibleItems, projection) })
    }
    // While a new list (a step of the loading, an image NG result) is matched, the last result stays in its order with the
    // threads no longer listed taken out (re-audit M12: the list went back to its own order, badges gone and hidden threads
    // shown, then jumped back once matched). A list with other settings starts from its own order as before.
    val carried = remember(ngVisibleItems, projectedLater, projection) {
        projectedLater?.takeIf { it.first !== ngVisibleItems && it.second == projection }?.let { futaberCarryProjection(it.third, ngVisibleItems) }
    }
    val projected = projectedNow
        ?: projectedLater?.takeIf { it.first === ngVisibleItems }?.third
        ?: carried
        ?: FutaberProjectedCatalog(ngVisibleItems)
    val titleKeys = remember(projected) { futaberCatalogTitleKeys(projected.items) }
    val appliedQuery = rememberFutaberDebouncedQuery(query)
    val filteredItems = remember(projected, titleKeys, appliedQuery) {
        filterFutaberCatalogByKeys(projected.items, titleKeys, appliedQuery)
    }
    val visibleItems = com.valoser.futacha.shared.ui.board.catalogWithViewedThreadFirst(
        filteredItems, view.viewedFirstId.takeIf { viewedFirstEnabled })

    // "消えたスレ" lists the threads that left the catalog, without the ones the person has hidden since.
    val droppedItems = remember(view.history.dropped, catalogNgWords, catalogNgIndex, phashHiddenIds, catalogSharedNg) {
        futaberApplyCatalogRules(view.history.dropped, catalogNgWords, catalogNgIndex, phashHiddenIds, catalogSharedNg)
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FutaberCatalogTopBar(board, onOpenDrawer, onCreateThread, onOpenSettings)
        HorizontalDivider(color = colors.navSeparator, thickness = 0.5.dp)
        // Not while the search bar, a row's sheet or the archive results are open: the swipe would leave them behind.
        val swipeModifier = rememberFutaberSwipeModifier(swipeEnabled && !searchOpen && sheetItem == null && archiveQuery == null) { swipe ->
            when (swipe) {
                FutaberSwipe.EdgeRight, FutaberSwipe.Right -> onOpenDrawer()
                FutaberSwipe.Left -> onSwipeForward?.invoke()
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().then(swipeModifier).testTag("futaber-catalog-body")) {
            val searchBlank = futaberCatalogSearchShowsNothing(searchOpen, query)
            when {
                // The board's reply-priority setting is still being read: wait rather than show another order first.
                projection?.ready == false -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
                // S25: the search just opened, nothing typed: futaber lists nothing until a word is typed.
                searchBlank -> Box(Modifier.fillMaxSize().background(colors.settingsPage).testTag("futaber-catalog-search-blank"))
                visibleItems.isNotEmpty() -> FutaberCatalogList(
                    visibleItems, displayStyle, view, onOpenRow ?: onOpenThread,
                    onLongPress = if (longPressActions != null) { item -> sheetItem = item } else null,
                    badges = projected.badges,
                    readIds = readThreadIds,
                    openingId = opener?.pressedThreadId(board.id)
                )
                load.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
                load.errorMessage != null -> FutaberMessage(
                    text = load.errorMessage.orEmpty(),
                    actionLabel = "再読み込み",
                    onAction = { view.refreshTick += 1 }
                )
                appliedQuery.isNotEmpty() -> FutaberMessage("「$appliedQuery」に一致するスレッドはありません")
                else -> FutaberMessage(futaberWatchModeEmptyMessage(sortMode, watchWords) ?: "スレッドがありません")
            }
            if (searchOpen && query.isNotBlank() && archiveHttpClient != null && archiveQuery == null) {
                FutaberArchiveSearchPill(query, onClick = { focusManager.clearFocus(); archiveQuery = query }, modifier = Modifier.align(Alignment.BottomEnd))
            }
            if (load.isLoading && visibleItems.isNotEmpty() && !searchBlank) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                    color = colors.accent,
                    strokeWidth = 2.dp
                )
            }
            if (load.errorMessage != null && visibleItems.isNotEmpty() && !searchBlank) {
                Text(
                    text = load.errorMessage.orEmpty(),
                    color = colors.onAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .background(colors.accent).fillMaxWidth().padding(8.dp)
                )
            }
        }
        tabStrip()
        if (searchOpen) {
            FutaberSearchBar(
                query = query,
                onQueryChange = { search.query = it },
                onCancel = cancelSearch
            )
        } else {
            FutaberCatalogBottomBar(
                displayStyle = displayStyle,
                sortMode = sortMode,
                sorts = futaberCatalogSortsFor(extraSorts),
                onOpenMenu = { menu = it },
                onSearch = { search.open = true },
                onOpenManage = onOpenManage,
                onRefresh = { view.refreshTick += 1 },
                onRefreshLongPress = { refreshSheet = true }
            )
        }
    }
    // S22: "Loading..." over the catalog while a row's thread loads (taps go through: another row, back cancel it).
    FutaberCatalogOpeningLayer(opener)
    menu?.let { request ->
        FutaberCatalogMenuPopover(request, displayStyle, sortMode, futaberCatalogSortsFor(extraSorts), onDisplayStyleChange, onSortChange) { menu = null }
    }
    archiveQuery?.let { word ->
        if (archiveHttpClient != null) {
            FutaberArchiveSearchSheet(
                query = word, board = board, httpClient = archiveHttpClient,
                onOpen = { item -> archiveQuery = null; onOpenThread(item) },
                onDismiss = { archiveQuery = null }
            )
        }
    }
    FutaberCatalogRefreshSheets(
        refreshSheet, { refreshSheet = it }, droppedSheet, { droppedSheet = it }, view, perGenerationBack, droppedItems,
        viewedFirstEnabled, onViewedFirstChange, refreshActions + listOfNotNull(
            if (canServerSearch) FutaberPostAction("server-search", com.valoser.futacha.shared.network.serverPostSearchTitle(board.url).orEmpty()) { serverSearchOpen = true } else null
        ), onOpenThread
    )
    sheetItem?.let { item ->
        val actions = longPressActions?.invoke(item).orEmpty()
        FutaberActionSheet(
            title = futaberCatalogTitle(item),
            actions = actions.map { action -> action.copy(onClick = { sheetItem = null; action.onClick() }) },
            onDismiss = { sheetItem = null }
        )
    }
    }
}

/** The refresh button's sheet (back to an earlier catalog, the dropped threads, …) and the dropped threads' sheet. */
@Composable
private fun FutaberCatalogRefreshSheets(
    refreshSheet: Boolean,
    setRefreshSheet: (Boolean) -> Unit,
    droppedSheet: Boolean,
    setDroppedSheet: (Boolean) -> Unit,
    view: FutaberCatalogViewState,
    perGenerationBack: Boolean,
    droppedItems: List<CatalogItem>,
    viewedFirstEnabled: Boolean,
    onViewedFirstChange: (Boolean) -> Unit,
    refreshActions: List<FutaberPostAction>,
    onOpenThread: (CatalogItem) -> Unit
) {
    val load = view.load
    if (refreshSheet) {
        FutaberActionSheet(
            title = "カタログ",
            // Not while a reload is running: its result would replace the list that was just brought back.
            actions = futaberCatalogBackActions(
                view.history, load.isLoading, perGenerationBack, history = { view.history }, onClose = { setRefreshSheet(false) }
            ) { items, rest ->
                view.history = rest
                view.load = view.load.copy(items = items)
            } + listOf(
                FutaberPostAction(
                    "catalog-dropped", "消えたスレ（${droppedItems.size}件）", enabled = droppedItems.isNotEmpty()
                ) { setRefreshSheet(false); setDroppedSheet(true) }
            ) + listOf(FutaberPostAction("viewed-first", "更新時に閲覧スレを先頭へ：" + if (viewedFirstEnabled) "ON" else "OFF") {
                setRefreshSheet(false); onViewedFirstChange(!viewedFirstEnabled)
            }) + refreshActions.map { action -> action.copy(onClick = { setRefreshSheet(false); action.onClick() }) },
            onDismiss = { setRefreshSheet(false) }
        )
    }
    if (droppedSheet) {
        // Every thread the catalog remembers as gone (as many as the heading counts) is listed, newest first; the sheet scrolls.
        // A tap opens the thread (an archive shows it if it fell off).
        FutaberActionSheet(
            title = "消えたスレ（新しい順）",
            actions = droppedItems.take(FUTABER_DROPPED_SHEET_MAX).map { item ->
                FutaberPostAction("dropped-${item.id}", futaberSafeTake(futaberCatalogTitle(item), 30)) {
                    setDroppedSheet(false)
                    onOpenThread(item)
                }
            },
            onDismiss = { setDroppedSheet(false) }
        )
    }
}

@Composable
private fun FutaberCatalogTopBar(
    board: BoardSummary,
    onOpenDrawer: () -> Unit,
    onCreateThread: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LocalFutaberColors.current
    // The original's navigation bar: the board list at the left (60 wide), the address in the middle, "＋" and the gear
    // (50 wide each) at the right, on the 44-high bar under the status area.
    Row(
        Modifier.fillMaxWidth().background(colors.navBar).statusBarsPadding().height(FUTABER_TOP_BAR_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FutaberNavButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "板一覧を開く", "futaber-open-drawer", 60, onOpenDrawer)
        Text(
            text = futaberCatalogHeading(board.url),
            color = colors.navTitle,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).testTag("futaber-board-address")
        )
        FutaberNavButton(Icons.Outlined.Add, "スレッドを立てる", "futaber-create-thread", 50, onCreateThread)
        FutaberNavButton(Icons.Outlined.Settings, "設定", "futaber-open-settings", 50, onOpenSettings)
    }
}

/** Which catalog popover is open and the button it points at (V29). */
internal data class FutaberCatalogMenuRequest(val sort: Boolean, val anchor: FutaberAnchor)

@Composable
private fun FutaberCatalogBottomBar(
    displayStyle: FutaberCatalogDisplayStyle,
    sortMode: CatalogMode,
    sorts: List<FutaberCatalogSort>,
    onOpenMenu: (FutaberCatalogMenuRequest) -> Unit,
    onSearch: () -> Unit,
    onOpenManage: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshLongPress: () -> Unit = {}
) {
    val longPressHaptic = rememberFutaberLongPressHaptic()
    var styleAnchor by remember { mutableStateOf(FutaberAnchor(0f, 0f, 0f)) }
    var sortAnchor by remember { mutableStateOf(FutaberAnchor(0f, 0f, 0f)) }
    fun Modifier.anchored(set: (FutaberAnchor) -> Unit) = onGloballyPositioned {
        val b = it.boundsInRoot()
        set(FutaberAnchor(b.left + b.width / 2f, b.top, b.bottom))
    }
    // The original's bar: display style, sort, search, panel, refresh in five equal slots on a white band.
    FutaberToolbarRow {
        FutaberToolbarButton(
            Icons.Outlined.GridView, "表示形式（現在: ${displayStyle.label}）", "futaber-display-style",
            Modifier.weight(1f).anchored { styleAnchor = it }, onClick = { onOpenMenu(FutaberCatalogMenuRequest(false, styleAnchor)) }
        )
        val label = sorts.firstOrNull { it.mode == sortMode }?.label ?: "通常"
        FutaberToolbarButton(
            Icons.Outlined.BarChart, "ソート（現在: $label）", "futaber-sort",
            Modifier.weight(1f).anchored { sortAnchor = it }, onClick = { onOpenMenu(FutaberCatalogMenuRequest(true, sortAnchor)) }
        )
        FutaberToolbarButton(Icons.Outlined.Search, "カタログを検索", "futaber-search", Modifier.weight(1f), onClick = onSearch)
        FutaberToolbarButton(FutaberTwoPaneIcon, "履歴とタブ", "futaber-manage", Modifier.weight(1f), onClick = onOpenManage)
        // A tap reloads; a long press offers going back to the catalog before the reload and the threads that dropped out.
        FutaberToolbarButton(
            Icons.Outlined.Refresh, "カタログを更新", "futaber-refresh", Modifier.weight(1f),
            onClickLabel = "カタログを更新", onLongClickLabel = "更新前のカタログと消えたスレ",
            onLongClick = { longPressHaptic(); onRefreshLongPress() },
            onClick = onRefresh
        )
    }
}

/**
 * V29・M52: futaber's popovers for 「表示形式」 and 「表示順序」 (left-aligned light labels, full-width digits, the chosen
 * one in the light blue); a tap outside only closes them. The rows keep their tags (`futaber-style-row1`, `futaber-sort-New`).
 */
@Composable
private fun FutaberCatalogMenuPopover(
    request: FutaberCatalogMenuRequest,
    displayStyle: FutaberCatalogDisplayStyle,
    sortMode: CatalogMode,
    sorts: List<FutaberCatalogSort>,
    onDisplayStyleChange: (FutaberCatalogDisplayStyle) -> Unit,
    onSortChange: (CatalogMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = if (request.sort) sorts.map { sort ->
        FutaberPopoverOption(sort.mode.name, futaberPopoverLabel(sort.label), sort.mode == sortMode, tag = "futaber-sort-${sort.mode.name}") {
            onDismiss(); onSortChange(sort.mode)
        }
    } else FutaberCatalogDisplayStyle.entries.map { style ->
        FutaberPopoverOption(style.persistedValue, futaberPopoverLabel(style.label), style == displayStyle, tag = "futaber-style-${style.persistedValue}") {
            onDismiss(); onDisplayStyleChange(style)
        }
    }
    FutaberPopover(request.anchor, if (request.sort) "表示順序" else "表示形式", options, onDismiss)
}

/**
 * V30: futaber's search bar: iOS's grey rounded field with the magnifier, 「検索ワード」 and a clear button, and a black
 * 「キャンセル」 outside it; the keyboard's key reads 「search」 and puts the keyboard away.
 */
@Composable
private fun FutaberSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        // The bar rises with the keyboard (outermost, so the system bar's share of the inset is not counted twice).
        Modifier.imePadding().futaberToolbar(colors)
            .heightIn(min = FUTABER_BOTTOM_BAR_HEIGHT_DP.dp).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).padding(vertical = 7.dp).heightIn(min = 32.dp).clip(FutaberShapes.field).background(colors.menuSearchField)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FutaberIcon(Icons.Outlined.Search, contentDescription = null, tint = colors.menuSearchPlaceholder, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.body, fontSize = 15.sp, fontFamily = LocalFutaberCustomFont.current),
                cursorBrush = SolidColor(colors.iosBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The search key puts the keyboard away (the list is already filtered as typed).
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.weight(1f).padding(start = 6.dp).focusRequester(focus)
                    .semantics { contentDescription = "カタログ内を検索" }
                    .testTag("futaber-search-field"),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("検索ワード", color = colors.menuSearchPlaceholder, fontSize = 15.sp)
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(28.dp).clickable(onClickLabel = "検索ワードを消す", role = androidx.compose.ui.semantics.Role.Button) { onQueryChange("") }
                        .testTag("futaber-search-clear"),
                    contentAlignment = Alignment.Center
                ) {
                    // iOS's clear button: a grey disc with a white cross.
                    Box(Modifier.size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.menuSearchPlaceholder),
                        contentAlignment = Alignment.Center) {
                        FutaberIcon(androidx.compose.material.icons.Icons.Outlined.Close, contentDescription = null, tint = colors.menuSearchField, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.testTag("futaber-search-cancel")) {
            Text("キャンセル", color = colors.catalogTitle, fontSize = 15.sp)
        }
    }
}

@Composable
private fun FutaberCatalogList(
    items: List<CatalogItem>,
    style: FutaberCatalogDisplayStyle,
    view: FutaberCatalogViewState,
    onOpenThread: (CatalogItem) -> Unit,
    onLongPress: ((CatalogItem) -> Unit)?,
    badges: Map<String, String> = emptyMap(),
    readIds: Set<String> = emptySet(),
    openingId: String? = null
) {
    val colors = LocalFutaberColors.current
    val columns = futaberCatalogColumns(style, LocalFutaberDisplaySettings.current.catalogFine)
    val scrollToTopTick = view.scrollToTopTick
    // F-B4 E6: the volume keys page whichever of the grid and the list is shown.
    FutaberCatalogVolumeKeyEffect(
        LocalFutaberDisplaySettings.current.extCatalogVolumeKey,
        { if (columns != null) view.gridState.layoutInfo.viewportSize.height else view.listState.layoutInfo.viewportSize.height },
        { delta -> if (columns != null) view.gridState.animateScrollBy(delta) else view.listState.animateScrollBy(delta) }
    )
    val fast = LocalFutaberDisplaySettings.current.catalogFastScroll
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    if (columns != null) {
        val state = view.gridState
        // Only a refresh that has not been carried out yet scrolls up; coming back to a kept catalog does not.
        LaunchedEffect(scrollToTopTick) {
            if (scrollToTopTick > view.scrollToTopHandled) { view.scrollToTopHandled = scrollToTopTick; state.scrollToItem(0) }
        }
        com.valoser.futacha.shared.ui.KeepCatalogPlaceOnNewItems(items, grid = state)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize().background(colors.gridGap).then(rememberFutaberScrollIndicator(state, columns))
                .testTag("futaber-catalog-grid"),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            gridItems(items.distinctBy { it.id }, key = { it.id }) { item -> FutaberGridCell(item, columns, onOpenThread, onLongPress, badges[item.id], item.id in readIds, item.id == openingId) }
        }
    } else {
        val state = view.listState
        LaunchedEffect(scrollToTopTick) {
            if (scrollToTopTick > view.scrollToTopHandled) { view.scrollToTopHandled = scrollToTopTick; state.scrollToItem(0) }
        }
        com.valoser.futacha.shared.ui.KeepCatalogPlaceOnNewItems(items, list = state)
        LazyColumn(Modifier.fillMaxSize().then(rememberFutaberScrollIndicator(state)).testTag("futaber-catalog-list"), state = state) {
            items(items.distinctBy { it.id }, key = { it.id }) { item ->
                FutaberRowCell(item, style.titleLines, onOpenThread, onLongPress, badges[item.id], item.id in readIds, item.id == openingId)
            }
        }
    }
    // F-B5 E5: the fast scroll bar over the list or grid (off by default).
    if (fast && items.isNotEmpty()) {
        if (columns != null) CompatFastScrollbar(true, items.size, view.gridState)
        else CompatFastScrollbar(true, items.size, view.listState)
    }
    }
}

@Composable
private fun FutaberGridCell(
    item: CatalogItem, columns: Int, onOpen: (CatalogItem) -> Unit, onLongPress: ((CatalogItem) -> Unit)?, badge: String? = null,
    read: Boolean = false, opening: Boolean = false
) {
    val haptic = rememberFutaberLongPressHaptic()
    val colors = LocalFutaberColors.current
    val titleSize = if (columns >= 6) 10.sp else 12.sp
    val title = futaberCatalogTitle(item)
    Column(
        // V12: a thread in the history: the strip under its picture is grey, as in futaber's grid.
        Modifier.fillMaxWidth().background(futaberCatalogRowGround(colors, read, opening))
            .combinedClickable(
                onClickLabel = "スレッドを開く",
                onLongClickLabel = if (onLongPress != null) "スレッドの操作を開く" else null,
                onLongClick = onLongPress?.let { handler -> { haptic(); handler(item) } },
                onClick = { onOpen(item) }
            )
            .semantics { contentDescription = futaberCatalogItemDescription(title, item.replyCount, read); selected = opening }
            .testTag("futaber-catalog-item")
    ) {
        Box {
            CatalogPreviewImage(
                thumbnailUrl = item.thumbnailUrl,
                fullImageUrl = item.fullImageUrl,
                targetSizePx = 250,
                contentDescription = "",
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(colors.fill),
                fallbackTint = colors.meta
            )
            FutaberCatalogWatchBadge(badge, Modifier.align(Alignment.TopStart))
        }
        val fine = LocalFutaberDisplaySettings.current.catalogFine
        Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            // C06 (FutaberParityB9.kt): the title's lines and letters; with nothing set, one line as before.
            val shownTitle = futaberGridTitle(title, fine)
            if (shownTitle != null) Text(
                shownTitle, color = if (opening) colors.onSelection else colors.catalogTitle, fontSize = titleSize, maxLines = fine.gridTitleLines,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            ) else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(
                item.replyCount.toString(), color = if (opening) colors.onSelection else colors.accent, fontSize = titleSize,
                modifier = Modifier.padding(start = 3.dp)
            )
        }
    }
}

/**
 * V27: a catalog row as futaber draws it: 40 high, the picture fitted (whole, white around it) in a 40 box 4 from the
 * left, the title (12, black) from 56 and the count (12, red) at the right; a thread in the history is grey (V12). The
 * rule between rows is drawn inside the row. "やや広め" makes the row and the picture 56 and gives the title two lines.
 */
@Composable
private fun FutaberRowCell(
    item: CatalogItem, titleLines: Int, onOpen: (CatalogItem) -> Unit, onLongPress: ((CatalogItem) -> Unit)?, badge: String? = null,
    read: Boolean = false, opening: Boolean = false
) {
    val haptic = rememberFutaberLongPressHaptic()
    val colors = LocalFutaberColors.current
    val title = futaberCatalogTitle(item)
    val wide = LocalFutaberDisplaySettings.current.catalogRowWide
    val lines = if (wide) maxOf(titleLines, 2) else titleLines
    val rowHeight = futaberCatalogRowHeightDp(wide)
    Row(
        Modifier.fillMaxWidth().heightIn(min = rowHeight.dp).background(futaberCatalogRowGround(colors, read, opening))
            .drawBehind {
                val stroke = 0.5.dp.toPx()
                drawLine(colors.rowSeparator, Offset(0f, size.height - stroke / 2f), Offset(size.width, size.height - stroke / 2f), stroke)
            }
            .combinedClickable(
                onClickLabel = "スレッドを開く",
                onLongClickLabel = if (onLongPress != null) "スレッドの操作を開く" else null,
                onLongClick = onLongPress?.let { handler -> { haptic(); handler(item) } },
                onClick = { onOpen(item) }
            )
            .semantics { contentDescription = futaberCatalogItemDescription(title, item.replyCount, read); selected = opening }
            .testTag("futaber-catalog-item"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The picture's box keeps the page's white around a fitted picture, also on a grey (read) row.
        Box(Modifier.padding(start = FUTABER_CATALOG_ROW_THUMB_START_DP.dp).size(rowHeight.dp).background(colors.background)) {
            CatalogPreviewImage(
                thumbnailUrl = item.thumbnailUrl,
                fullImageUrl = item.fullImageUrl,
                targetSizePx = 250,
                contentDescription = "",
                modifier = Modifier.fillMaxSize(),
                fallbackTint = colors.meta,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            FutaberCatalogWatchBadge(badge, Modifier.align(Alignment.TopStart))
        }
        Text(
            title, color = if (opening) colors.onSelection else colors.catalogTitle, fontSize = 12.sp, maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = FUTABER_CATALOG_ROW_TITLE_GAP_DP.dp, end = 8.dp)
                .testTag("futaber-catalog-item-title")
        )
        Text(
            item.replyCount.toString(), color = if (opening) colors.onSelection else colors.accent, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.padding(end = 10.dp).testTag("futaber-catalog-item-count")
        )
    }
}

/** S22・V12: a row's ground: blue while its thread loads before opening, grey once read, else the page. */
internal fun futaberCatalogRowGround(colors: FutaberColors, read: Boolean, opening: Boolean): androidx.compose.ui.graphics.Color =
    when {
        opening -> colors.selection
        read -> colors.readRow
        else -> colors.background
    }

@Composable
internal fun FutaberMessage(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val colors = LocalFutaberColors.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = colors.body, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = colors.link) }
        }
    }
}

/** As many as the catalog remembers, so the list is as long as the number in the menu says. */
private const val FUTABER_DROPPED_SHEET_MAX = FUTABER_CATALOG_DROPPED_MAX
