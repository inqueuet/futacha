package com.valoser.futacha.shared.ui.futaber

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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

internal const val FUTABER_TOP_BAR_HEIGHT_DP = 48
internal const val FUTABER_BOTTOM_BAR_HEIGHT_DP = 48

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
    tabStrip: @Composable () -> Unit = {}
) {
    val colors = LocalFutaberColors.current
    val display = LocalFutaberDisplaySettings.current
    val load = view.load
    val searchOpen = search.open
    val query = search.query
    var sheetItem by remember(board.id) { mutableStateOf<CatalogItem?>(null) }
    // The word of a running 過去ログ検索; null = no archive search is open.
    var archiveQuery by remember(board.id) { mutableStateOf<String?>(null) }
    // The keyboard goes away when the results open, so they are not hidden behind it.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    com.valoser.futacha.shared.ui.util.PlatformBackHandler(enabled = archiveQuery != null) { archiveQuery = null }
    // The reloads of this board and sort: what to go back to, and which threads dropped out.
    var refreshSheet by remember(board.id, sortMode) { mutableStateOf(false) }
    var droppedSheet by remember(board.id, sortMode) { mutableStateOf(false) }

    LaunchedEffect(board.id, board.url, sortMode, view.refreshTick, refreshSignal) {
        val key = FutaberCatalogFetchKey(board.url, sortMode, view.refreshTick, refreshSignal)
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
            val page = repository.getCatalogPage(board.url, sortMode)
            view.history = futaberCatalogHistoryAfterLoad(view.history, view.load.items, page.items)
            view.load = FutaberCatalogLoad(items = page.items, isLoading = false)
            view.fetchedKey = key
            view.fetchedAtMillis = Clock.System.now().toEpochMilliseconds()
            if (explicitRefresh && display.scrollCatalogToTopOnRefresh) view.scrollToTopTick += 1
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            view.load = view.load.copy(isLoading = false, errorMessage = futaberFriendlyLoadError(error, "カタログ"))
        }
    }
    // The threads whose picture looks like a registered NG image (the hash rules of the other modes), found by hashing the
    // pictures: the ones already known first, then the rest as they arrive. Threads vanish from the list as hashes come in.
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
    val ngVisibleItems = remember(load.items, catalogNgWords, catalogNgIndex, phashHiddenIds) {
        futaberApplyCatalogRules(load.items, catalogNgWords, catalogNgIndex, phashHiddenIds)
    }
    val titleKeys = remember(ngVisibleItems) { futaberCatalogTitleKeys(ngVisibleItems) }
    val appliedQuery = rememberFutaberDebouncedQuery(query)
    val visibleItems = remember(ngVisibleItems, titleKeys, appliedQuery) {
        filterFutaberCatalogByKeys(ngVisibleItems, titleKeys, appliedQuery)
    }
    // "消えたスレ" lists the threads that left the catalog, without the ones the person has hidden since.
    val droppedItems = remember(view.history.dropped, catalogNgWords, catalogNgIndex, phashHiddenIds) {
        futaberApplyCatalogRules(view.history.dropped, catalogNgWords, catalogNgIndex, phashHiddenIds)
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FutaberCatalogTopBar(board, onOpenDrawer, onCreateThread, onOpenSettings)
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                visibleItems.isNotEmpty() -> FutaberCatalogList(
                    visibleItems, displayStyle, view, onOpenThread,
                    onLongPress = if (longPressActions != null) { item -> sheetItem = item } else null
                )
                load.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
                load.errorMessage != null -> FutaberMessage(
                    text = load.errorMessage.orEmpty(),
                    actionLabel = "再読み込み",
                    onAction = { view.refreshTick += 1 }
                )
                appliedQuery.isNotEmpty() -> FutaberMessage("「$appliedQuery」に一致するスレッドはありません")
                else -> FutaberMessage("スレッドがありません")
            }
            if (searchOpen && query.isNotBlank() && archiveHttpClient != null && archiveQuery == null) {
                FutaberArchiveSearchPill(query, onClick = { focusManager.clearFocus(); archiveQuery = query }, modifier = Modifier.align(Alignment.BottomEnd))
            }
            if (load.isLoading && visibleItems.isNotEmpty()) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                    color = colors.accent,
                    strokeWidth = 2.dp
                )
            }
            if (load.errorMessage != null && visibleItems.isNotEmpty()) {
                Text(
                    text = load.errorMessage.orEmpty(),
                    color = colors.onAction,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .background(colors.accent).fillMaxWidth().padding(8.dp)
                )
            }
        }
        tabStrip()
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
        if (searchOpen) {
            FutaberSearchBar(
                query = query,
                onQueryChange = { search.query = it },
                onCancel = { search.query = ""; search.open = false }
            )
        } else {
            FutaberCatalogBottomBar(
                displayStyle = displayStyle,
                sortMode = sortMode,
                onDisplayStyleChange = onDisplayStyleChange,
                onSortChange = onSortChange,
                onSearch = { search.open = true },
                onOpenManage = onOpenManage,
                onRefresh = { view.refreshTick += 1 },
                onRefreshLongPress = { refreshSheet = true }
            )
        }
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
    if (refreshSheet) {
        FutaberPostActionSheet(
            title = "カタログ",
            actions = listOf(
                // Not while a reload is running: its result would replace the list that was just brought back.
                FutaberPostAction(
                    "catalog-back", "更新前のカタログに戻す", enabled = view.history.earlier.isNotEmpty() && !load.isLoading
                ) {
                    refreshSheet = false
                    futaberCatalogGoBack(view.history)?.let { (items, rest) ->
                        view.history = rest
                        view.load = view.load.copy(items = items)
                    }
                },
                FutaberPostAction(
                    "catalog-dropped", "消えたスレ（${droppedItems.size}件）", enabled = droppedItems.isNotEmpty()
                ) { refreshSheet = false; droppedSheet = true },
                FutaberPostAction("cancel", "キャンセル") { refreshSheet = false }
            ),
            onDismiss = { refreshSheet = false },
            avoidNavigationBar = true
        )
    }
    if (droppedSheet) {
        // The sheet is not scrollable, so the newest few are listed; a tap opens the thread (an archive shows it if it fell off).
        FutaberPostActionSheet(
            title = "消えたスレ（新しい順）",
            actions = droppedItems.take(FUTABER_DROPPED_SHEET_MAX).map { item ->
                FutaberPostAction("dropped-${item.id}", futaberSafeTake(futaberCatalogTitle(item), 30)) {
                    droppedSheet = false
                    onOpenThread(item)
                }
            } + FutaberPostAction("cancel", "キャンセル") { droppedSheet = false },
            onDismiss = { droppedSheet = false },
            avoidNavigationBar = true
        )
    }
    sheetItem?.let { item ->
        val actions = longPressActions?.invoke(item).orEmpty()
        FutaberPostActionSheet(
            title = futaberCatalogTitle(item),
            actions = actions.map { action -> action.copy(onClick = { sheetItem = null; action.onClick() }) } +
                FutaberPostAction("cancel", "キャンセル") { sheetItem = null },
            onDismiss = { sheetItem = null },
            avoidNavigationBar = true
        )
    }
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
    Row(
        Modifier.fillMaxWidth().background(colors.topBar).statusBarsPadding().height(FUTABER_TOP_BAR_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("futaber-open-drawer")) {
            FutaberIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "板一覧を開く", tint = colors.onTopBar)
        }
        Text(
            text = futaberBoardAddress(board.url),
            color = colors.topBarTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).testTag("futaber-board-address")
        )
        IconButton(onClick = onCreateThread, modifier = Modifier.testTag("futaber-create-thread")) {
            FutaberIcon(Icons.Outlined.Add, contentDescription = "スレッドを立てる", tint = colors.onTopBar)
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("futaber-open-settings")) {
            FutaberIcon(Icons.Outlined.Settings, contentDescription = "設定", tint = colors.onTopBar)
        }
    }
}

@Composable
private fun FutaberCatalogBottomBar(
    displayStyle: FutaberCatalogDisplayStyle,
    sortMode: CatalogMode,
    onDisplayStyleChange: (FutaberCatalogDisplayStyle) -> Unit,
    onSortChange: (CatalogMode) -> Unit,
    onSearch: () -> Unit,
    onOpenManage: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshLongPress: () -> Unit = {}
) {
    val colors = LocalFutaberColors.current
    val longPressHaptic = rememberFutaberLongPressHaptic()
    var styleMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.futaberFloatingBar(colors)
            .height(FUTABER_BOTTOM_BAR_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            IconButton(onClick = { styleMenu = true }, modifier = Modifier.testTag("futaber-display-style")) {
                FutaberIcon(Icons.Outlined.GridView, contentDescription = "表示形式（現在: ${displayStyle.label}）", tint = colors.icon)
            }
            DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                FutaberMenuTitle("表示形式")
                FutaberCatalogDisplayStyle.entries.forEach { style ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                style.label,
                                color = if (style == displayStyle) colors.action else colors.body,
                                fontWeight = if (style == displayStyle) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        onClick = { styleMenu = false; onDisplayStyleChange(style) },
                        modifier = Modifier.testTag("futaber-style-${style.persistedValue}")
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("futaber-sort")) {
                val label = futaberCatalogSorts.firstOrNull { it.mode == sortMode }?.label ?: "通常"
                FutaberIcon(Icons.Outlined.BarChart, contentDescription = "ソート（現在: $label）", tint = colors.icon)
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                FutaberMenuTitle("表示順序")
                futaberCatalogSorts.forEach { sort ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                sort.label,
                                color = if (sort.mode == sortMode) colors.action else colors.body,
                                fontWeight = if (sort.mode == sortMode) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        onClick = { sortMenu = false; onSortChange(sort.mode) },
                        modifier = Modifier.testTag("futaber-sort-${sort.mode.name}")
                    )
                }
            }
        }
        IconButton(onClick = onSearch, modifier = Modifier.testTag("futaber-search")) {
            FutaberIcon(Icons.Outlined.Search, contentDescription = "カタログを検索", tint = colors.icon)
        }
        IconButton(onClick = onOpenManage, modifier = Modifier.testTag("futaber-manage")) {
            FutaberIcon(FutaberTwoPaneIcon, contentDescription = "履歴とタブ", tint = colors.icon)
        }
        // A tap reloads; a long press offers going back to the catalog before the reload and the threads that dropped out.
        Box(
            Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .combinedClickable(
                    onClickLabel = "カタログを更新",
                    onLongClickLabel = "更新前のカタログと消えたスレ",
                    onClick = onRefresh,
                    onLongClick = { longPressHaptic(); onRefreshLongPress() }
                )
                .testTag("futaber-refresh"),
            contentAlignment = Alignment.Center
        ) {
            FutaberIcon(Icons.Outlined.Refresh, contentDescription = "カタログを更新", tint = colors.icon)
        }
    }
}

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
        Modifier.imePadding().futaberFloatingBar(colors)
            .heightIn(min = FUTABER_BOTTOM_BAR_HEIGHT_DP.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.body, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The search key puts the keyboard away (the list is already filtered as typed).
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .semantics { contentDescription = "カタログ内を検索" }
                    .testTag("futaber-search-field"),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("スレッドを検索", color = colors.meta, fontSize = 15.sp)
                    inner()
                }
            )
        }
        TextButton(onClick = onCancel, modifier = Modifier.testTag("futaber-search-cancel")) {
            Text("キャンセル", color = colors.link)
        }
    }
}

@Composable
private fun FutaberCatalogList(
    items: List<CatalogItem>,
    style: FutaberCatalogDisplayStyle,
    view: FutaberCatalogViewState,
    onOpenThread: (CatalogItem) -> Unit,
    onLongPress: ((CatalogItem) -> Unit)?
) {
    val colors = LocalFutaberColors.current
    val columns = style.columns
    val scrollToTopTick = view.scrollToTopTick
    if (columns != null) {
        val state = view.gridState
        // Only a refresh that has not been carried out yet scrolls up; coming back to a kept catalog does not.
        LaunchedEffect(scrollToTopTick) {
            if (scrollToTopTick > view.scrollToTopHandled) { view.scrollToTopHandled = scrollToTopTick; state.scrollToItem(0) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize().background(colors.catalogGap).testTag("futaber-catalog-grid"),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            gridItems(items, key = { it.id }) { item -> FutaberGridCell(item, columns, onOpenThread, onLongPress) }
        }
    } else {
        val state = view.listState
        LaunchedEffect(scrollToTopTick) {
            if (scrollToTopTick > view.scrollToTopHandled) { view.scrollToTopHandled = scrollToTopTick; state.scrollToItem(0) }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("futaber-catalog-list"), state = state) {
            items(items, key = { it.id }) { item ->
                FutaberRowCell(item, style.titleLines, onOpenThread, onLongPress)
                HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun FutaberGridCell(item: CatalogItem, columns: Int, onOpen: (CatalogItem) -> Unit, onLongPress: ((CatalogItem) -> Unit)?) {
    val haptic = rememberFutaberLongPressHaptic()
    val colors = LocalFutaberColors.current
    val titleSize = if (columns >= 6) 10.sp else 12.sp
    val title = futaberCatalogTitle(item)
    Column(
        Modifier.fillMaxWidth().background(colors.background)
            .combinedClickable(
                onClickLabel = "スレッドを開く",
                onLongClickLabel = if (onLongPress != null) "スレッドの操作を開く" else null,
                onLongClick = onLongPress?.let { handler -> { haptic(); handler(item) } },
                onClick = { onOpen(item) }
            )
            .semantics { contentDescription = "$title、${item.replyCount}レス" }
            .testTag("futaber-catalog-item")
    ) {
        CatalogPreviewImage(
            thumbnailUrl = item.thumbnailUrl,
            fullImageUrl = item.fullImageUrl,
            targetSizePx = 250,
            contentDescription = "",
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(colors.catalogGap),
            fallbackTint = colors.meta
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, color = colors.body, fontSize = titleSize, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text(
                item.replyCount.toString(), color = colors.accent, fontSize = titleSize,
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 3.dp)
            )
        }
    }
}

@Composable
private fun FutaberRowCell(item: CatalogItem, titleLines: Int, onOpen: (CatalogItem) -> Unit, onLongPress: ((CatalogItem) -> Unit)?) {
    val haptic = rememberFutaberLongPressHaptic()
    val colors = LocalFutaberColors.current
    val title = futaberCatalogTitle(item)
    val wide = LocalFutaberDisplaySettings.current.catalogRowWide
    val lines = if (wide) maxOf(titleLines, 2) else titleLines
    val thumbSize = if (wide) 68.dp else if (titleLines > 1) 56.dp else 48.dp
    Row(
        Modifier.fillMaxWidth().background(colors.background)
            .combinedClickable(
                onClickLabel = "スレッドを開く",
                onLongClickLabel = if (onLongPress != null) "スレッドの操作を開く" else null,
                onLongClick = onLongPress?.let { handler -> { haptic(); handler(item) } },
                onClick = { onOpen(item) }
            )
            .semantics { contentDescription = "$title、${item.replyCount}レス" }
            .testTag("futaber-catalog-item"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CatalogPreviewImage(
            thumbnailUrl = item.thumbnailUrl,
            fullImageUrl = item.fullImageUrl,
            targetSizePx = 250,
            contentDescription = "",
            modifier = Modifier.size(thumbSize).background(colors.catalogGap),
            fallbackTint = colors.meta
        )
        Text(
            title, color = colors.body, fontSize = 13.sp, maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(
            item.replyCount.toString(), color = colors.accent, fontSize = 13.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 10.dp)
        )
    }
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

/** The heading of a catalog menu, as in the original app: a bold centred title over a rule. */
@Composable
private fun FutaberMenuTitle(text: String) {
    val colors = LocalFutaberColors.current
    Text(
        text, color = colors.body, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
    )
    HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
}

private const val FUTABER_DROPPED_SHEET_MAX = 8
