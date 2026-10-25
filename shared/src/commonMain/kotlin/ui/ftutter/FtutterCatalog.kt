package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WebAsset
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.board.EdgeSwipeRefreshBinding
import com.valoser.futacha.shared.ui.board.ScrollEdgeStateReader
import com.valoser.futacha.shared.ui.board.edgeSwipeRefresh
import com.valoser.futacha.shared.ui.board.rememberEdgeSwipeRefreshBinding
import com.valoser.futacha.shared.ui.board.rememberEdgeSwipeRefreshMetrics
import com.valoser.futacha.shared.ui.board.rememberEdgeSwipeRefreshVisualState
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** 取得したカタログ（板と並び順ごと）。スレから戻っても取り直さないよう、画面の外で持つ。 */
internal data class FtutterCatalogLoad(
    val key: String,
    /** 取得した時の更新ボタンの回数。同じ回数なら取り直さない。 */
    val tick: Int = 0,
    val items: List<CatalogItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val viewedFirstId: String? = null
)

/**
 * A fetch cancelled by leaving the screen leaves no 「取得中」 record behind (L144): while its own record is still
 * the current one, the rows that were there before come back, so returning fetches again and pull-to-refresh works.
 * A record written by a newer fetch is kept.
 */
/**
 * 一覧に出すスレ（件数の上限と、閲覧スレを先頭へ）。元の一覧・上限・先頭のスレが変わった時だけ作る（L25：合成のたびに新しい
 * リストを渡していたので、一覧の部品が設定の保存などのたびに再合成された）。
 */
@Composable
internal fun rememberFtutterVisibleCatalog(items: List<CatalogItem>, maxCount: Int, viewedFirstId: String?): List<CatalogItem> =
    remember(items, maxCount, viewedFirstId) {
        com.valoser.futacha.shared.ui.board.catalogWithViewedThreadFirst(items.take(maxCount), viewedFirstId)
    }

internal fun ftutterCatalogLoadAfterCancel(current: FtutterCatalogLoad?, started: FtutterCatalogLoad,
    before: FtutterCatalogLoad): FtutterCatalogLoad? = if (current === started) before else current

internal class FtutterCatalogHolder {
    /**
     * A thread was created on this board: the last viewed thread is no longer put first (L37: the reload after it, or the
     * list on return, kept it above the new thread). Another order already drops it (its own key).
     */
    fun afterThreadCreated() {
        load = load?.copy(viewedFirstId = null)
    }
    private var sessionOrder: MutableState<FtutterOrder>? = null
    /** Apply the startup default once; thread/settings children must not reset the current choice. */
    fun orderState(initialOrder: FtutterOrder): MutableState<FtutterOrder> =
        sessionOrder ?: mutableStateOf(initialOrder).also { sessionOrder = it }

    private val searches = LinkedHashMap<String, com.valoser.futacha.shared.ui.futaber.FutaberCatalogSearchState>()
    fun search(boardId: String): com.valoser.futacha.shared.ui.futaber.FutaberCatalogSearchState {
        val state = searches.remove(boardId) ?: com.valoser.futacha.shared.ui.futaber.FutaberCatalogSearchState()
        searches[boardId] = state
        while (searches.size > 12) searches.remove(searches.keys.first())
        return state
    }
    private val loaded = LinkedHashMap<String, FtutterCatalogLoad>()
    private var currentLoad by mutableStateOf<FtutterCatalogLoad?>(null)
    var load: FtutterCatalogLoad?
        get() = currentLoad
        set(value) {
            currentLoad = value
            if (value != null && !value.loading && value.error == null) {
                loaded.remove(value.key); loaded[value.key] = value
                while (loaded.size > 12) loaded.remove(loaded.keys.first())
            }
        }
    fun cached(key: String) = loaded[key]
    private var cachedTabBoardId: String? = null
    /** Only a move between favorite board tabs may reuse the previous catalog. */
    fun openBoard(boardId: String, tabMove: Boolean = false, refreshOnTabMove: Boolean = false) {
        cachedTabBoardId = boardId.takeIf { tabMove && !refreshOnTabMove }
        refreshTick++
    }
    fun takeTabCache(boardId: String, key: String): FtutterCatalogLoad? {
        val reuse = cachedTabBoardId == boardId
        cachedTabBoardId = null
        return if (reuse) cached(key) else null
    }
    fun reusableLoad(boardId: String, key: String, tick: Int): FtutterCatalogLoad? {
        val current = load
        takeTabCache(boardId, key)?.let { return it.copy(tick = tick) }
        // A record still marked 「取得中」 is never reused: its fetch did not finish (L144).
        return current?.takeIf { it.key == key && it.tick == tick && !it.loading && it.items.isNotEmpty() && it.error == null }
    }
    var manualRefreshKey: String? = null
    var refreshTick by mutableIntStateOf(0)
    /** 直近のカタログでNGタイトルに当たって消えたスレ（「非表示・NG」の画面に出す）。 */
    var ngRemoved by mutableStateOf<List<CatalogItem>>(emptyList())
    var ngReasons by mutableStateOf<Map<String, String>>(emptyMap())
    val gridState = LazyGridState()
    val staggeredState = LazyStaggeredGridState()
    /** スクロール位置が属する板（監査L6：板を変えると先頭から。スレッドから戻った時は同じ板なので位置を保つ）。 */
    var scrolledBoardId: String? = null
    /**
     * NGの照合器・題名の照合の結果・最後に出した一覧。カタログは開き直すたびに作り直されるので、ここに持って使い回す
     * （再監査M1：照合が終わるまで0件の一覧を描き、スクロール位置が先頭に戻っていた）。
     */
    var matcher: FtutterNgMatcher? = null
    var matcherInputs: Pair<List<FtutterNgRule>, String>? = null
    var titleNg: FtutterCatalogTitleNg? = null
    var lastNgResult: Pair<String, FtutterCatalogNgResult>? = null
    /** 履歴から作る、この板のスレの表（再監査M9：開き直すたびにメインで2万件から作っていた）。 */
    val historyIndexMemo = com.valoser.futacha.shared.ui.OffMainMemo<List<Any?>, Map<String, ThreadHistoryEntry>>()
    /**
     * カタログの検索中・過去ログ検索の表示中に、戻るでそれを閉じる処理（それ以外は null）。二画面では後から置いた
     * スレッド側の戻るが先に受けるので、アプリ側がこれを見て先に閉じる（監査A3）。
     */
    var paneBack by mutableStateOf<(() -> Unit)?>(null)
}

/** カタログの長押しダイアログ（原作の ThumbDialog）の項目。長押し拡大がOFFの時に出る。 */
internal enum class FtutterThumbAction(val label: String) {
    DEL("del送信"),
    HIDE("スレッドを非表示"),
    PICKUP("ピックアップタイトルに追加"),
    NG_TITLE("NGタイトルに追加"),
    DELETE_HISTORY("履歴から削除"),
    ADD_TAB("タブに追加")
}

@Composable
internal fun FtutterCatalogScreen(
    board: BoardSummary,
    boards: List<BoardSummary>,
    repository: BoardRepository,
    history: List<ThreadHistoryEntry>,
    preferences: Map<String, String>,
    holder: FtutterCatalogHolder,
    savePreference: (String, String) -> Unit,
    /** 保存されている値に変更を当てる（監査M2）。 */
    editPreference: (String, (String?) -> String?) -> Unit,
    onBack: () -> Unit,
    onSwitchBoard: (BoardSummary) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenThread: (CatalogItem) -> Unit,
    onOpenHiddenAndNg: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDeleteHistory: (ThreadHistoryEntry) -> Unit,
    onNotice: (String) -> Unit,
    onOpenDrawer: () -> Unit = {},
    /** 長押しの「タブに追加」：裏でタブに足す（開かない）。 */
    onAddTab: (CatalogItem) -> Unit = {},
    /** タブ帯（表示する時だけ中身がある）と、その位置。 */
    tabOnTop: Boolean = false,
    tabStrip: @Composable () -> Unit = {},
    ngRules: List<FtutterNgRule> = emptyList(),
    /** 保存できた時だけ true（上限を超えて断った時は、呼ばれた側が理由を出す）。 */
    onSaveNgRules: FtutterNgEdit = { _, _ -> },
    /** スレ立て（ツールバーの「スレ立て」）。 */
    onCreateThread: () -> Unit = {},
    /** del送信（スレの本文＝No.がスレ番号）。確認の後に呼ぶ。 */
    onDelThread: (CatalogItem) -> Unit = {},
    gesture: FtutterGestureSettings = FtutterGestureSettings(enabled = false),
    onAppGesture: (FtutterGestureAction) -> Unit = {},
    /** 「ジェスチャーの操作を選ぶ」から選んだ時（実行できなければ理由を知らせる。L17）。 */
    onAppMenuGesture: (FtutterGestureAction) -> Unit = onAppGesture,
    /** ジェスチャーがOFFの時の左右スワイプ（お気に入りの隣の板へ）。 */
    onSwipeBoard: ((forward: Boolean) -> Unit)? = null,
    onEditToolbar: () -> Unit = {},
    /** 二画面の時の列数（縦3・横5。画面全体の向きで決める）。null は通常の列数。 */
    columnsOverride: Int? = null,
    /** 拡張「共通のNGを使う」がONの時の、共有のカタログNGワード（OFFなら空）。 */
    commonCatalogNgWords: List<String> = emptyList(),
    /** 拡張「過去ログ検索」がONの時の通信（OFFなら null で、ボタンを出さない）。 */
    archiveClient: io.ktor.client.HttpClient? = null,
    openTabUrls: Set<String> = emptySet()
) {
    val colors = LocalFtutterColors.current
    val design = remember(preferences) { parseFtutterDesign(preferences) }
    val settings = remember(preferences) { parseFtutterCatalogSettings(preferences) }
    val toolbar = remember(preferences) { parseFtutterCatalogToolbar(preferences) }
    val metrics = remember(preferences) { parseFtutterCatalogToolbarMetrics(preferences) }
    val hidden = remember(preferences) { parseFtutterHiddenThreads(preferences[FtutterKeys.HIDDEN_THREADS]) }
    val readCounts = remember(preferences) { parseFtutterReadCounts(preferences[FtutterKeys.READ_COUNTS]) }
    var order by remember(holder) { holder.orderState(settings.startOrder) }
    val search = remember(board.id, holder) { holder.search(board.id) }
    var searching by search::open
    var query by search::query
    var zoomed by remember { mutableStateOf<CatalogItem?>(null) }
    var thumbMenu by remember { mutableStateOf<CatalogItem?>(null) }
    var openUrlDialog by remember { mutableStateOf(false) }
    var refreshFromBottom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    // 履歴（最大2万件）から作る表は解析用のスレッドで作り、カタログより長く持つ（再監査M9）。前の板の表は出さない。
    val historyIndex = com.valoser.futacha.shared.ui.rememberOffMain(
        holder.historyIndexMemo, listOf(com.valoser.futacha.shared.ui.SameInstance(history), board), emptyMap(),
        usable = { held, wanted -> (held[1] as BoardSummary).id == (wanted[1] as BoardSummary).id }
    ) { _, _ -> ftutterHistoryIndex(history, board) }
    val key = "${board.id}|${order.name}"

    PlatformBackHandler(enabled = searching) { searching = false; query = "" }

    LaunchedEffect(board.id) {
        if (holder.scrolledBoardId != null && holder.scrolledBoardId != board.id) {
            holder.gridState.scrollToItem(0)
            holder.staggeredState.scrollToItem(0)
        }
        holder.scrolledBoardId = board.id
    }

    LaunchedEffect(key, holder.refreshTick) {
        val current = holder.load
        val tick = holder.refreshTick
        holder.reusableLoad(board.id, key, tick)
            ?.let { holder.load = it; return@LaunchedEffect }
        val retained = current?.takeIf { it.key == key } ?: FtutterCatalogLoad(key = key)
        // Keep the displayed rows and their viewed-first placement until a new response succeeds.
        val started = retained.copy(tick = tick, loading = true, error = null)
        holder.load = started
        try {
            val page = repository.getCatalogPage(board.url, order.mode)
            holder.load = FtutterCatalogLoad(key = key, tick = tick, items = page.items,
                viewedFirstId = if (holder.manualRefreshKey == key && preferences[com.valoser.futacha.shared.ui.board.viewedThreadFirstKey(board.url)] == "ON")
                    history.filter { it.boardId == board.id }.maxByOrNull { it.lastVisitedEpochMillis }?.threadId
                else current?.takeIf { it.key == key }?.viewedFirstId)
            holder.manualRefreshKey = null
            // 落ちたスレの非表示の記録を消す。取得を始めた時の一覧でなく保存されている一覧から除く（監査M4：取得中に「非表示」に
            // したスレが、古い一覧で上書きされて戻っていた）。
            editPreference(FtutterKeys.HIDDEN_THREADS) { stored -> ftutterPrunedHiddenValue(stored, board.url, page.items) }
            if (refreshFromBottom) {
                // 原作：下に引っ張った更新の後は先頭へ戻る。
                holder.gridState.scrollToItem(0)
                holder.staggeredState.scrollToItem(0)
                refreshFromBottom = false
            }
        } catch (cancelled: CancellationException) {
            holder.load = ftutterCatalogLoadAfterCancel(holder.load, started, retained)
            throw cancelled
        } catch (error: Throwable) {
            kotlin.coroutines.coroutineContext.ensureActive()
            val message = com.valoser.futacha.shared.ui.futaber.futaberFriendlyLoadError(error, "カタログ")
            holder.load = retained.copy(tick = tick, loading = false, error = message)
            // The inline error is only shown for an empty list. Notify refresh failures too,
            // just as the thread screen does, while keeping the catalog and scroll position.
            onNotice(message)
            holder.manualRefreshKey = null
            refreshFromBottom = false
        } finally {
            if (holder.manualRefreshKey == key && holder.refreshTick == tick) holder.manualRefreshKey = null
        }
    }
    val load = holder.load?.takeIf { it.key == key }
    val matcher = remember(ngRules, board.url) {
        val inputs = ngRules to board.url
        holder.matcher?.takeIf { holder.matcherInputs == inputs }
            ?: FtutterNgMatcher(ngRules, board.url).also { holder.matcher = it; holder.matcherInputs = inputs }
    }
    val pickupOrders = remember(preferences) { parseFtutterPickupOrders(preferences[FtutterKeys.PICKUP_ORDER_MODES]) }
    // NGタイトル・ピックアップ・共通のカタログNGの照合（正規表現もあり重い）は、題名ごとにメインスレッドの外で1回だけ行い、
    // 検索語・並び順・非表示が変わってもやり直さない。照合の途中は、同じカタログの前の結果を出したままにする。
    val items = load?.items.orEmpty()
    val needsTitleNg = !matcher.isEmpty || commonCatalogNgWords.any { it.isNotBlank() }
    val titleNg by androidx.compose.runtime.produceState(
        holder.titleNg?.takeIf { it.isFor(items, matcher, commonCatalogNgWords) }, items, matcher, commonCatalogNgWords
    ) {
        if (matcher.isEmpty && commonCatalogNgWords.none { it.isNotBlank() }) {
            value = null
            return@produceState
        }
        if (value?.isFor(items, matcher, commonCatalogNgWords) == true) return@produceState
        val job = kotlinx.coroutines.currentCoroutineContext()
        value = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
            ftutterCatalogTitleNg(items, matcher, commonCatalogNgWords) { job.ensureActive() }
        }.also { holder.titleNg = it }
    }
    val titleNgNow = titleNg?.takeIf { it.isFor(items, matcher, commonCatalogNgWords) }
    // 原作の順：検索→非表示→NGタイトル→ピックアップ→表示数の上限。
    // 検索は、カタログごとに1回だけ作った題名の正規化・URLで絞り、打ち終わってから効かせる（監査M10：以前は1文字ごとに、全スレの
    // 題名とURLを正規化し直していた）。
    val searchIndex = remember(items) { FtutterCatalogSearchIndex.of(items) }
    val hiddenSet = remember(hidden) { hidden.toSet() }
    val appliedQuery = com.valoser.futacha.shared.ui.futaber.rememberFutaberDebouncedQuery(query)
    val computedNg = remember(searchIndex, appliedQuery, hiddenSet, matcher, order, pickupOrders, commonCatalogNgWords, needsTitleNg, titleNgNow) {
        if (needsTitleNg && titleNgNow == null) null else ftutterApplyCommonCatalogNg(
            ftutterApplyCatalogNg(ftutterVisibleCatalog(searchIndex, appliedQuery, hiddenSet, Int.MAX_VALUE), matcher, order, pickupOrders, titleNgNow?.byTitle),
            commonCatalogNgWords,
            titleNgNow?.byTitle
        )
    }
    var lastNgResult by remember { mutableStateOf(holder.lastNgResult) }
    LaunchedEffect(computedNg) { computedNg?.let { result ->
        lastNgResult = key to result; holder.lastNgResult = key to result
        holder.ngReasons = result.ngRemoved.mapNotNull { item ->
            titleNgNow?.byTitle?.get(item.title.orEmpty())?.reason?.let { item.threadUrl to it }
        }.toMap()
    } }
    val ngResult = computedNg ?: lastNgResult?.takeIf { it.first == key }?.second
        ?: FtutterCatalogNgResult(emptyList(), emptyMap(), emptyList())
    // 照合に時間がかかりすぎて使わなかったNGの正規表現は、一度だけ知らせる（設定のNGの一覧にも出る）。
    var slowNgNoticed by remember(matcher) { mutableStateOf(emptySet<FtutterNgRule>()) }
    LaunchedEffect(titleNg) {
        val fresh = titleNg?.matcher?.takeIf { it === matcher }?.slowRules.orEmpty().filter { it !in slowNgNoticed }
        if (fresh.isNotEmpty()) {
            slowNgNoticed = slowNgNoticed + fresh
            ftutterSlowNgNotice(fresh)?.let(onNotice)
        }
    }
    var archiveQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(computedNg) { computedNg?.let { holder.ngRemoved = it.ngRemoved } }
    val viewedFirstKey = com.valoser.futacha.shared.ui.board.viewedThreadFirstKey(board.url)
    val viewedFirstEnabled = preferences[viewedFirstKey] == "ON"
    LaunchedEffect(viewedFirstEnabled, key) {
        if (!viewedFirstEnabled) holder.load?.takeIf { it.key == key && it.viewedFirstId != null }?.let {
            holder.load = it.copy(viewedFirstId = null)
        }
    }
    val visible = rememberFtutterVisibleCatalog(ngResult.items, settings.maxThreadCount, load?.viewedFirstId.takeIf { viewedFirstEnabled })
    var ngDraft by remember { mutableStateOf<FtutterNgRule?>(null) }
    var delTarget by remember { mutableStateOf<CatalogItem?>(null) }
    var historyDeleteTarget by remember { mutableStateOf<CatalogItem?>(null) }
    fun refresh() { holder.manualRefreshKey = key; holder.refreshTick++ }
    val paneBackOpen = (archiveClient != null && archiveQuery != null) || searching
    androidx.compose.runtime.DisposableEffect(paneBackOpen) {
        holder.paneBack = if (!paneBackOpen) null else {
            { if (archiveQuery != null) archiveQuery = null else { searching = false; query = "" } }
        }
        onDispose { holder.paneBack = null }
    }

    fun handleGesture(action: FtutterGestureAction, fromMenu: Boolean = false) {
        // カタログでは、ツリー・人気・画像抽出は何もしない（原作）。
        when (action) {
            FtutterGestureAction.TOP -> scope.launch { holder.gridState.scrollToItem(0); holder.staggeredState.scrollToItem(0) }
            FtutterGestureAction.BOTTOM -> scope.launch {
                val last = (visible.size - 1).coerceAtLeast(0)
                holder.gridState.scrollToItem(last); holder.staggeredState.scrollToItem(last)
            }
            FtutterGestureAction.REFRESH -> { refreshFromBottom = false; refresh() }
            FtutterGestureAction.SEARCH -> searching = true
            FtutterGestureAction.EDIT -> onCreateThread()
            FtutterGestureAction.TREE, FtutterGestureAction.POPULAR, FtutterGestureAction.IMAGES, FtutterGestureAction.NONE -> Unit
            else -> if (fromMenu) onAppMenuGesture(action) else onAppGesture(action)
        }
    }

    Column(Modifier.fillMaxSize().testTag("ftutter-catalog")) {
        if (searching) {
            FtutterSearchBar(query = query, onQueryChange = { query = it }, onClose = { searching = false; query = "" })
        } else {
            FtutterCatalogTopBar(board, boards, onBack, onSwitchBoard, onOpenSettings,
                onOpenDrawer = if (preferences.ftutterBool(FtutterKeys.DRAWER_BUTTON_TOP_LEFT, false)) onOpenDrawer else null)
        }
        if (tabOnTop) tabStrip()
        FtutterGestureLayer(
            settings = gesture,
            enabled = zoomed == null && archiveQuery == null,
            onAction = ::handleGesture,
            onSwipe = onSwipeBoard,
            modifier = Modifier.weight(1f).fillMaxWidth().background(colors.background)
        ) {
            val isLoading = load?.loading == true
            FtutterCatalogGrid(
                items = visible,
                board = board,
                settings = columnsOverride?.let { settings.copy(columnsPortrait = it, columnsLandscape = it) } ?: settings,
                design = design,
                historyIndex = historyIndex,
                openTabUrls = openTabUrls,
                readCounts = readCounts,
                pickupWords = ngResult.pickupWords,
                holder = holder,
                isRefreshing = isLoading,
                zoomedActive = zoomed != null,
                onRefresh = { fromBottom -> refreshFromBottom = fromBottom; refresh() },
                onOpen = onOpenThread,
                onZoom = { zoomed = it },
                onThumbMenu = { thumbMenu = it }
            )
            if (isLoading && visible.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.indicator)
            }
            load?.error?.let { message ->
                if (visible.isEmpty()) {
                    Text(message, color = colors.text, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }
            }
            zoomed?.let { item -> FtutterCatalogZoom(item, settings, design) }
            if (archiveClient != null && searching && query.isNotBlank() && zoomed == null) {
                FtutterArchiveSearchButton(query, onClick = { archiveQuery = query }, modifier = Modifier.align(Alignment.BottomEnd))
            }
            val archiveSearch = archiveQuery
            if (archiveClient != null && archiveSearch != null) {
                PlatformBackHandler(enabled = true) { archiveQuery = null }
                FtutterArchiveSearchSheet(
                    query = archiveSearch, board = board, httpClient = archiveClient,
                    onOpen = { item -> archiveQuery = null; onOpenThread(item) },
                    onDismiss = { archiveQuery = null }
                )
            }
        }
        if (!tabOnTop) tabStrip()
        FtutterBottomToolbar(
            entries = toolbar,
            metrics = metrics,
            isSpace = { it == FtutterCatalogTool.SPACE },
            isOrder = { it == FtutterCatalogTool.ORDER },
            modifier = Modifier.testTag("ftutter-catalog-toolbar")
        ) { tool, _ ->
            when (tool) {
                FtutterCatalogTool.ORDER -> FtutterOrderDropdown(order) { selected ->
                    order = selected
                    savePreference(FtutterKeys.PREVIOUS_ORDER_MODE, selected.stored.toString())
                }
                FtutterCatalogTool.MENU -> FtutterCatalogMenu(
                    privacy = settings.privacy,
                    viewedFirstEnabled = viewedFirstEnabled,
                    onToggleViewedFirst = { savePreference(viewedFirstKey, if (viewedFirstEnabled) "OFF" else "ON") },
                    onGesture = { handleGesture(it, fromMenu = true) },
                    onTogglePrivacy = { savePreference(FtutterKeys.IS_PRIVACY, (!settings.privacy).toString()) },
                    onToolbarEdit = onEditToolbar,
                    onHiddenAndNg = onOpenHiddenAndNg,
                    onOpenFromUrl = { openUrlDialog = true },
                    onCopyUrl = {
                        clipboard.setText(AnnotatedString(ftutterBoardBaseUrl(board.url) + "futaba.php?mode=cat"))
                        onNotice("URLをコピーしました")
                    }
                )
                else -> FtutterToolButton(
                    tool = tool,
                    active = (tool == FtutterCatalogTool.TAB && settings.showTab) ||
                        (tool == FtutterCatalogTool.PRIVACY && settings.privacy),
                    onClick = {
                        when (tool) {
                            FtutterCatalogTool.DRAWER -> onOpenDrawer()
                            FtutterCatalogTool.TAB -> savePreference(FtutterKeys.SHOW_TAB, (!settings.showTab).toString())
                            FtutterCatalogTool.PRIVACY -> savePreference(FtutterKeys.IS_PRIVACY, (!settings.privacy).toString())
                            FtutterCatalogTool.UP -> scope.launch {
                                holder.gridState.scrollToItem(0); holder.staggeredState.scrollToItem(0)
                            }
                            FtutterCatalogTool.DOWN -> scope.launch {
                                val last = (visible.size - 1).coerceAtLeast(0)
                                holder.gridState.scrollToItem(last); holder.staggeredState.scrollToItem(last)
                            }
                            FtutterCatalogTool.CREATE -> onCreateThread()
                            FtutterCatalogTool.SEARCH -> searching = true
                            FtutterCatalogTool.REFRESH -> { refreshFromBottom = false; refresh() }
                            else -> Unit
                        }
                    }
                )
            }
        }
    }

    thumbMenu?.let { item ->
        FtutterThumbDialog(
            hasHistory = historyIndex[item.id] != null,
            onDismiss = { thumbMenu = null },
            onAction = { action ->
                thumbMenu = null
                when (action) {
                    FtutterThumbAction.HIDE -> {
                        // 保存値の上限に入らなければ、古い記録から消して知らせる（監査A9）。保存されている一覧に足す（監査M2）。
                        val url = ftutterCanonicalThreadUrl(item.threadUrl)
                        editPreference(FtutterKeys.HIDDEN_THREADS) { stored ->
                            val (next, dropped) = ftutterHiddenThreadsWith(parseFtutterHiddenThreads(stored), url)
                            onNotice(ftutterHiddenNotice(dropped))
                            encodeFtutterList(next)
                        }
                    }
                    // 原作：確認の後、タブを閉じて履歴から削除する。
                    FtutterThumbAction.DELETE_HISTORY -> if (historyIndex[item.id] != null) historyDeleteTarget = item
                    FtutterThumbAction.ADD_TAB -> { onAddTab(item); onNotice("タブに追加しました") }
                    FtutterThumbAction.DEL -> delTarget = item
                    FtutterThumbAction.PICKUP -> ngDraft = FtutterNgRule(item.title.orEmpty(), FtutterNgType.PICKUP.stored, ita = FtutterNgBoard(board.name, board.url))
                    FtutterThumbAction.NG_TITLE -> ngDraft = FtutterNgRule(item.title.orEmpty(), FtutterNgType.TITLE.stored, ita = FtutterNgBoard(board.name, board.url))
                }
            }
        )
    }
    ngDraft?.let { draft ->
        FtutterNgDialog(
            initial = draft,
            boards = boards,
            existing = ngRules,
            onDismiss = { ngDraft = null },
            onSave = { rule -> ngDraft = null; onSaveNgRules({ it + rule }, "登録しました") }
        )
    }
    delTarget?.let { item ->
        // 原作は確認なしで送るが、誤操作を防ぐため確認する。
        FtutterConfirmDialog(
            title = "delを送りますか？",
            message = "${item.title.orEmpty()}\n掲示板の管理側へ削除依頼（del）を送ります。",
            confirmLabel = "送る",
            onDismiss = { delTarget = null }
        ) { delTarget = null; onDelThread(item) }
    }
    historyDeleteTarget?.let { item ->
        FtutterHistoryDeleteConfirm(item.title.orEmpty(), onDismiss = { historyDeleteTarget = null }) {
            historyDeleteTarget = null
            historyIndex[item.id]?.let(onDeleteHistory)
        }
    }
    if (openUrlDialog) {
        FtutterOpenUrlDialog(onDismiss = { openUrlDialog = false }, onOpen = { url -> openUrlDialog = false; onOpenUrl(url) })
    }
}

@Composable
private fun FtutterCatalogTopBar(
    board: BoardSummary,
    boards: List<BoardSummary>,
    onBack: () -> Unit,
    onSwitchBoard: (BoardSummary) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null
) {
    val colors = LocalFtutterColors.current
    var boardMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(colors.toolBar).statusBarsPadding().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onOpenDrawer != null) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("ftutter-topbar-drawer")) {
                Icon(Icons.Rounded.Menu, contentDescription = "ドロワーを開く", tint = colors.onToolBar)
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.testTag("ftutter-back")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る", tint = colors.onToolBar)
        }
        Text(
            board.name,
            color = colors.onToolBar,
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp).testTag("ftutter-catalog-title")
        )
        Box {
            IconButton(onClick = { boardMenu = true }, modifier = Modifier.testTag("ftutter-board-select")) {
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "板を選ぶ", tint = colors.onToolBar)
            }
            DropdownMenu(expanded = boardMenu, onDismissRequest = { boardMenu = false }) {
                boards.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(candidate.name) },
                        onClick = { boardMenu = false; onSwitchBoard(candidate) }
                    )
                }
            }
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("ftutter-open-settings")) {
            Icon(Icons.Rounded.Settings, contentDescription = "設定", tint = colors.onToolBar)
        }
    }
}

@Composable
internal fun FtutterSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val colors = LocalFtutterColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.toolBar).statusBarsPadding().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("検索") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.toolBar, unfocusedContainerColor = colors.toolBar,
                focusedTextColor = colors.onToolBar, unfocusedTextColor = colors.onToolBar
            ),
            modifier = Modifier.weight(1f).testTag("ftutter-search-input")
        )
        IconButton(onClick = onClose, modifier = Modifier.testTag("ftutter-search-close")) {
            Icon(Icons.Rounded.Close, contentDescription = "検索を閉じる", tint = colors.onToolBar)
        }
    }
}

@Composable
private fun FtutterToolButton(tool: FtutterCatalogTool, active: Boolean, onClick: () -> Unit) {
    val colors = LocalFtutterColors.current
    val icon = when (tool) {
        FtutterCatalogTool.DRAWER -> Icons.Rounded.Menu
        FtutterCatalogTool.TAB -> Icons.Rounded.WebAsset
        FtutterCatalogTool.PRIVACY -> if (active) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
        FtutterCatalogTool.UP -> Icons.Rounded.KeyboardArrowUp
        FtutterCatalogTool.DOWN -> Icons.Rounded.KeyboardArrowDown
        FtutterCatalogTool.CREATE -> Icons.Rounded.Edit
        FtutterCatalogTool.SEARCH -> Icons.Rounded.Search
        FtutterCatalogTool.REFRESH -> Icons.Rounded.Refresh
        else -> Icons.Rounded.MoreVert
    }
    IconButton(onClick = onClick, modifier = Modifier.testTag("ftutter-catalog-tool-${tool.stored}")) {
        FtutterToggleIcon(icon, contentDescription = tool.label, active = active)
    }
}

@Composable
private fun FtutterOrderDropdown(order: FtutterOrder, onSelect: (FtutterOrder) -> Unit) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clickable { open = true }.padding(horizontal = 4.dp).testTag("ftutter-catalog-order"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(order.label, color = colors.onToolBar, fontSize = 15.sp)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "並び順", tint = colors.onToolBar)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FtutterOrder.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.label) },
                    onClick = { open = false; onSelect(candidate) },
                    modifier = Modifier.testTag("ftutter-order-${candidate.name}")
                )
            }
        }
    }
}

@Composable
private fun FtutterCatalogMenu(
    privacy: Boolean,
    viewedFirstEnabled: Boolean,
    onToggleViewedFirst: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onToolbarEdit: () -> Unit,
    onHiddenAndNg: () -> Unit,
    onOpenFromUrl: () -> Unit,
    onCopyUrl: () -> Unit,
    onGesture: (FtutterGestureAction) -> Unit
) {
    val colors = LocalFtutterColors.current
    var gestureMenu by remember { mutableStateOf(false) }
    if (gestureMenu) FtutterGestureActionDialog(false, { gestureMenu = false }, onGesture)
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("ftutter-catalog-tool-menu")) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "メニュー", tint = colors.onToolBar)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("更新時に閲覧スレを先頭へ：" + if (viewedFirstEnabled) "ON" else "OFF") },
                onClick = { open = false; onToggleViewedFirst() }, modifier = Modifier.testTag("ftutter-catalog-viewed-first"))
            DropdownMenuItem(text = { Text("ツールバー編集") }, onClick = { open = false; onToolbarEdit() })
            DropdownMenuItem(
                text = { Text("プライバシー") },
                leadingIcon = { Checkbox(checked = privacy, onCheckedChange = null) },
                onClick = { open = false; onTogglePrivacy() }
            )
            DropdownMenuItem(
                text = { Text("非表示・NG") },
                onClick = { open = false; onHiddenAndNg() },
                modifier = Modifier.testTag("ftutter-menu-hidden-ng")
            )
            DropdownMenuItem(text = { Text("URLから開く") }, onClick = { open = false; onOpenFromUrl() })
            DropdownMenuItem(text = { Text("URLをコピー") }, onClick = { open = false; onCopyUrl() })
            DropdownMenuItem(text = { Text("ジェスチャーの操作を選ぶ") }, onClick = { open = false; gestureMenu = true },
                modifier = Modifier.testTag("ftutter-catalog-menu-gesture-actions"))
        }
    }
}

@Composable
private fun FtutterCatalogGrid(
    items: List<CatalogItem>,
    board: BoardSummary,
    settings: FtutterCatalogSettings,
    design: FtutterDesignSettings,
    historyIndex: Map<String, ThreadHistoryEntry>,
    openTabUrls: Set<String>,
    readCounts: Map<String, Int>,
    pickupWords: Map<String, String>,
    holder: FtutterCatalogHolder,
    isRefreshing: Boolean,
    zoomedActive: Boolean,
    onRefresh: (fromBottom: Boolean) -> Unit,
    onOpen: (CatalogItem) -> Unit,
    onZoom: (CatalogItem?) -> Unit,
    onThumbMenu: (CatalogItem) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val columns = if (landscape) settings.columnsLandscape else settings.columnsPortrait
        val density = LocalDensity.current
        val cellPx = with(density) { (maxWidth / columns).toPx() }.toInt().coerceAtLeast(1)
        val cell: @Composable (CatalogItem) -> Unit = { item ->
            FtutterCatalogCell(
                item = item,
                board = board,
                settings = settings,
                design = design,
                history = historyIndex[item.id],
                inTab = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, item.id)) in openTabUrls,
                readCount = readCounts[ftutterCanonicalThreadUrl(item.threadUrl)],
                pickupWord = pickupWords[item.threadUrl],
                targetSizePx = cellPx,
                onOpen = onOpen,
                onZoom = onZoom,
                onThumbMenu = onThumbMenu
            )
        }
        val blur = if (zoomedActive) Modifier.blur(10.dp) else Modifier
        if (settings.sameHeight) {
            val binding = rememberEdgeSwipeRefreshBinding(holder.gridState, isRefreshing, "ftutterCatalogGrid")
            LazyVerticalGrid(
                state = holder.gridState,
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().then(blur).ftutterPullRefresh(binding, settings, isRefreshing, onRefresh)
                    .testTag("ftutter-catalog-grid")
            ) {
                items(items, key = { it.threadUrl }) { cell(it) }
            }
            FtutterScrollbar(settings.scrollbar, holder.gridState)
        } else {
            val binding = rememberStaggeredRefreshBinding(holder.staggeredState, isRefreshing)
            LazyVerticalStaggeredGrid(
                state = holder.staggeredState,
                columns = StaggeredGridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().then(blur).ftutterPullRefresh(binding, settings, isRefreshing, onRefresh)
                    .testTag("ftutter-catalog-grid")
            ) {
                items(items, key = { it.threadUrl }) { cell(it) }
            }
            FtutterScrollbar(settings.scrollbar, holder.staggeredState)
        }
    }
}

@Composable
private fun rememberStaggeredRefreshBinding(state: LazyStaggeredGridState, isRefreshing: Boolean): EdgeSwipeRefreshBinding {
    val metrics = rememberEdgeSwipeRefreshMetrics()
    val visual = rememberEdgeSwipeRefreshVisualState(isRefreshing, "ftutterCatalogStaggered")
    val edge = remember(state) { ScrollEdgeStateReader({ !state.canScrollBackward }, { !state.canScrollForward }) }
    return remember(edge, metrics, visual) { EdgeSwipeRefreshBinding(edge, metrics, visual) }
}

/** 上・下の引っ張り更新。原作は上・下を別々にON/OFFできる。下で更新した時は、取得の後に先頭へ戻る。 */
private fun Modifier.ftutterPullRefresh(
    binding: EdgeSwipeRefreshBinding,
    settings: FtutterCatalogSettings,
    isRefreshing: Boolean,
    onRefresh: (fromBottom: Boolean) -> Unit
): Modifier = this
    .offset { IntOffset(0, binding.visualState.overscrollOffset.value.toInt()) }
    .edgeSwipeRefresh(
        isRefreshing = isRefreshing,
        isAtTop = { settings.pullTop && binding.edgeState.isAtTop() },
        isAtBottom = { settings.pullBottom && binding.edgeState.isAtBottom() },
        maxOverscrollPx = binding.metrics.maxOverscrollPx,
        refreshTriggerPx = binding.metrics.refreshTriggerPx,
        onOverscrollTargetChanged = binding.visualState::onOverscrollTargetChanged,
        onRefresh = {
            val fromBottom = binding.edgeState.isAtBottom() && !binding.edgeState.isAtTop()
            onRefresh(fromBottom)
        }
    )

@Composable
private fun FtutterCatalogCell(
    item: CatalogItem,
    board: BoardSummary,
    settings: FtutterCatalogSettings,
    design: FtutterDesignSettings,
    history: ThreadHistoryEntry?,
    inTab: Boolean,
    readCount: Int?,
    pickupWord: String?,
    targetSizePx: Int,
    onOpen: (CatalogItem) -> Unit,
    onZoom: (CatalogItem?) -> Unit,
    onThumbMenu: (CatalogItem) -> Unit
) {
    val colors = LocalFtutterColors.current
    val numbers = ftutterCatalogNumbers(item, history, readCount)
    val title = ftutterCatalogTitle(item.title, board.url, settings)
    val imageAlpha = if (settings.privacy) (1.0 - design.privacyCatalog).toFloat() else 1f
    val numberSize = design.catalogNumberFontSize.sp
    val badge: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.background(colors.resuSuu).padding(horizontal = 3.dp)) {
            Text(numbers.replies.toString(), color = colors.onResuSuu, fontSize = numberSize, maxLines = 1)
        }
    }
    val unread: @Composable (Modifier) -> Unit = { modifier ->
        numbers.unread?.let { count ->
            Box(modifier.background(colors.midoku).padding(horizontal = 3.dp)) {
                Text(count.toString(), color = colors.onMidoku, fontSize = numberSize, maxLines = 1)
            }
        }
    }
    val numbersRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            unread(Modifier)
            Box(Modifier.weight(1f))
            badge(Modifier)
        }
    }
    // ジェスチャーはスレのURLで続け、押した時の動作は最新を使う（監査L1：最初の構成の動作のまま、古い履歴・設定で開いていた）。
    val currentItem by androidx.compose.runtime.rememberUpdatedState(item)
    val currentOnOpen by androidx.compose.runtime.rememberUpdatedState(onOpen)
    val currentOnZoom by androidx.compose.runtime.rememberUpdatedState(onZoom)
    val currentOnThumbMenu by androidx.compose.runtime.rememberUpdatedState(onThumbMenu)
    Column(
        Modifier
            .background(if (inTab) colors.inTab else colors.background)
            .border(0.5.dp, colors.outline)
            .pointerInput(item.threadUrl, settings.useZoom) {
                detectTapGestures(
                    onTap = { currentOnOpen(currentItem) },
                    onLongPress = { if (settings.useZoom) currentOnZoom(currentItem) else currentOnThumbMenu(currentItem) },
                    onPress = {
                        // 原作：長押し拡大は押している間だけ。指を離すと閉じる。
                        tryAwaitRelease()
                        if (settings.useZoom) currentOnZoom(null)
                    }
                )
            }
            .testTag("ftutter-catalog-item")
    ) {
        if (settings.numberPosition == FtutterNumberPosition.ABOVE) numbersRow()
        val aspect = if (settings.sameHeight) 1f else ftutterThumbAspectRatio(item.thumbnailWidth, item.thumbnailHeight, settings)
        Box(Modifier.fillMaxWidth().aspectRatio(aspect)) {
            FtutterThumbnail(
                thumbnailUrl = item.thumbnailUrl,
                fullImageUrl = item.fullImageUrl,
                quality = settings.highRes.ordinal, catalog = true,
                targetSizePx = targetSizePx,
                contentDescription = title,
                modifier = Modifier.fillMaxSize().alpha(imageAlpha)
            )
            if (settings.numberPosition == FtutterNumberPosition.OVERLAY) {
                unread(Modifier.align(Alignment.TopStart))
                badge(Modifier.align(Alignment.BottomEnd))
            }
            // ピックアップの一致語の印（原作：pickUpColor の角丸の箱に onPickUpColor の文字）。
            pickupWord?.let { word ->
                Text(
                    word, color = colors.onPickUp, fontSize = numberSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                        .background(colors.pickUp, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(horizontal = 3.dp).testTag("ftutter-pickup-word")
                )
            }
        }
        if (settings.numberPosition == FtutterNumberPosition.BELOW) numbersRow()
        if (settings.titleLines > 0) {
            val lineHeight = (design.catalogFontSize * 1.4f).sp
            Text(
                text = title,
                color = if (history != null) colors.kidoku else colors.text,
                fontSize = design.catalogFontSize.sp,
                lineHeight = lineHeight,
                maxLines = settings.titleLines,
                minLines = if (settings.sameHeight) settings.titleLines else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }
        if (settings.numberPosition == FtutterNumberPosition.UNDER_TITLE) numbersRow()
    }
}

private val FTUTTER_VIDEO_EXTENSIONS = listOf(".mp4", ".webm")

/** 長押し拡大（原作の KakudaiImage）：背景をぼかし、中央に原寸画像。動画のスレはサムネイル。 */
@Composable
private fun FtutterCatalogZoom(item: CatalogItem, settings: FtutterCatalogSettings, design: FtutterDesignSettings) {
    val full = item.fullImageUrl
    val isVideo = full != null && FTUTTER_VIDEO_EXTENSIONS.any { full.lowercase().substringBefore('?').endsWith(it) }
    val alpha = if (settings.privacy) (1.0 - design.privacyCatalogZoom).toFloat() else 1f
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).testTag("ftutter-catalog-zoom"), contentAlignment = Alignment.Center) {
        FtutterThumbnail(
            thumbnailUrl = if (isVideo) item.thumbnailUrl else full,
            fullImageUrl = item.thumbnailUrl,
            targetSizePx = 1080,
            contentDescription = item.title.orEmpty(),
            modifier = Modifier.fillMaxWidth().aspectRatio(
                if (item.thumbnailWidth != null && item.thumbnailHeight != null && item.thumbnailHeight > 0)
                    item.thumbnailWidth.toFloat() / item.thumbnailHeight else 1f
            ).alpha(alpha)
        )
        if (isVideo) {
            Text("動画ファイルです", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp))
        }
    }
}

@Composable
internal fun FtutterThumbDialog(hasHistory: Boolean, onDismiss: () -> Unit, onAction: (FtutterThumbAction) -> Unit) {
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(Modifier.testTag("ftutter-thumb-dialog")) {
                FtutterThumbAction.entries.forEach { action ->
                    val enabled = action != FtutterThumbAction.DELETE_HISTORY || hasHistory
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = enabled) { onAction(action) }.padding(vertical = 10.dp)
                            .testTag("ftutter-thumb-${action.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (action) {
                                FtutterThumbAction.DEL -> Icons.Rounded.ThumbDown
                                FtutterThumbAction.HIDE -> Icons.Rounded.VisibilityOff
                                FtutterThumbAction.PICKUP -> Icons.Rounded.NotificationsActive
                                FtutterThumbAction.NG_TITLE -> Icons.Rounded.Block
                                FtutterThumbAction.DELETE_HISTORY -> Icons.Rounded.Delete
                                FtutterThumbAction.ADD_TAB -> Icons.Rounded.LibraryAdd
                            },
                            contentDescription = null
                        )
                        Text(action.label, modifier = Modifier.padding(start = 16.dp).alpha(if (enabled) 1f else 0.38f))
                    }
                }
            }
        },
        confirmButton = {}
    ) }
}

/** 原作の「履歴を削除しますか？」。 */
@Composable
internal fun FtutterHistoryDeleteConfirm(threadTitle: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    FtutterConfirmDialog(title = "履歴を削除しますか？", message = threadTitle, confirmLabel = "OK", onDismiss = onDismiss, onConfirm = onConfirm)
}

@Composable
private fun FtutterOpenUrlDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("URLから開く") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("スレッドまたは板のURL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ftutter-open-url-input")
            )
        },
        confirmButton = { TextButton(onClick = { onOpen(input.trim()) }, enabled = input.isNotBlank()) { Text("開く") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    ) }
}

/**
 * 原作の「非表示・NG」画面。非表示にしたスレの一覧と、NG（第5段階で接続）。
 */
@Composable
internal fun FtutterHiddenAndNgScreen(
    preferences: Map<String, String>,
    editPreference: (String, (String?) -> String?) -> Unit,
    onBack: () -> Unit,
    /** 今の板のカタログでNGタイトルに当たったスレ。 */
    ngRemoved: List<CatalogItem> = emptyList(),
    ngReasons: Map<String, String> = emptyMap()
) {
    val colors = LocalFtutterColors.current
    val hidden = remember(preferences) { parseFtutterHiddenThreads(preferences[FtutterKeys.HIDDEN_THREADS]) }
    var hiddenOpen by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().testTag("ftutter-hidden-ng")) {
        FtutterTopBar(title = "非表示・NG", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { hiddenOpen = !hiddenOpen }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("非表示にしたスレ（${hidden.size}）", color = colors.text, modifier = Modifier.weight(1f))
                    Icon(if (hiddenOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.text)
                }
                HorizontalDivider(color = colors.outline)
            }
            if (hiddenOpen) {
                items(hidden, key = { it }) { url ->
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp).testTag("ftutter-hidden-row"), verticalAlignment = Alignment.CenterVertically) {
                        Text(url, color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = {
                            // 保存されている一覧から外す（監査M2：続けて解除すると1件目が戻っていた）。
                            editPreference(FtutterKeys.HIDDEN_THREADS) { stored ->
                                val before = parseFtutterHiddenThreads(stored)
                                if (url in before) encodeFtutterList(before - url) else null
                            }
                        }, modifier = Modifier.testTag("ftutter-hidden-remove")) { Text("解除") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("NG（${ngRemoved.size}）", color = colors.text, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = colors.outline)
            }
            items(ngRemoved, key = { "ng-" + it.threadUrl }) { item ->
                Column {
                Text(
                    item.title.orEmpty(), color = colors.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).testTag("ftutter-ng-removed-row")
                )
                ngReasons[item.threadUrl]?.let { reason -> Text(reason, color = colors.text, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, bottom = 8.dp).testTag("ftutter-ng-removed-reason")) }
                }
            }
        }
    }
}
