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
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.board.EdgeSwipeRefreshBinding
import com.valoser.futacha.shared.ui.board.ScrollEdgeStateReader
import com.valoser.futacha.shared.ui.board.edgeSwipeRefresh
import com.valoser.futacha.shared.ui.board.rememberEdgeSwipeRefreshBinding
import com.valoser.futacha.shared.ui.board.rememberEdgeSwipeRefreshMetrics
import com.valoser.futacha.shared.ui.board.rememberEdgeSwipeRefreshVisualState
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 取得したカタログ（板と並び順ごと）。スレから戻っても取り直さないよう、画面の外で持つ。 */
internal data class FtutterCatalogLoad(
    val key: String,
    /** 取得した時の更新ボタンの回数。同じ回数なら取り直さない。 */
    val tick: Int = 0,
    val items: List<CatalogItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

internal class FtutterCatalogHolder {
    var load by mutableStateOf<FtutterCatalogLoad?>(null)
    var refreshTick by mutableIntStateOf(0)
    /** 直近のカタログでNGタイトルに当たって消えたスレ（「非表示・NG」の画面に出す）。 */
    var ngRemoved by mutableStateOf<List<CatalogItem>>(emptyList())
    val gridState = LazyGridState()
    val staggeredState = LazyStaggeredGridState()
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
    onSaveNgRules: (List<FtutterNgRule>) -> Unit = {},
    /** スレ立て（ツールバーの「スレ立て」）。 */
    onCreateThread: () -> Unit = {},
    /** del送信（スレの本文＝No.がスレ番号）。確認の後に呼ぶ。 */
    onDelThread: (CatalogItem) -> Unit = {},
    gesture: FtutterGestureSettings = FtutterGestureSettings(enabled = false),
    onAppGesture: (FtutterGestureAction) -> Unit = {},
    /** ジェスチャーがOFFの時の左右スワイプ（お気に入りの隣の板へ）。 */
    onSwipeBoard: ((forward: Boolean) -> Unit)? = null,
    onEditToolbar: () -> Unit = {},
    /** 二画面の時の列数（縦3・横5。画面全体の向きで決める）。null は通常の列数。 */
    columnsOverride: Int? = null,
    /** 拡張「共通のNGを使う」がONの時の、共有のカタログNGワード（OFFなら空）。 */
    commonCatalogNgWords: List<String> = emptyList(),
    /** 拡張「過去ログ検索」がONの時の通信（OFFなら null で、ボタンを出さない）。 */
    archiveClient: io.ktor.client.HttpClient? = null
) {
    val colors = LocalFtutterColors.current
    val design = remember(preferences) { parseFtutterDesign(preferences) }
    val settings = remember(preferences) { parseFtutterCatalogSettings(preferences) }
    val toolbar = remember(preferences) { parseFtutterCatalogToolbar(preferences) }
    val metrics = remember(preferences) { parseFtutterCatalogToolbarMetrics(preferences) }
    val hidden = remember(preferences) { parseFtutterHiddenThreads(preferences[FtutterKeys.HIDDEN_THREADS]) }
    val readCounts = remember(preferences) { parseFtutterReadCounts(preferences[FtutterKeys.READ_COUNTS]) }
    var order by remember { mutableStateOf(settings.startOrder) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var zoomed by remember { mutableStateOf<CatalogItem?>(null) }
    var thumbMenu by remember { mutableStateOf<CatalogItem?>(null) }
    var openUrlDialog by remember { mutableStateOf(false) }
    var refreshFromBottom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val historyIndex = remember(history, board) { ftutterHistoryIndex(history, board) }
    val key = "${board.id}|${order.name}"

    PlatformBackHandler(enabled = searching) { searching = false; query = "" }

    LaunchedEffect(key, holder.refreshTick) {
        val current = holder.load
        val tick = holder.refreshTick
        if (current?.key == key && current.tick == tick && current.items.isNotEmpty() && current.error == null) return@LaunchedEffect
        holder.load = FtutterCatalogLoad(key = key, tick = tick, items = current?.takeIf { it.key == key }?.items.orEmpty(), loading = true)
        try {
            val page = repository.getCatalogPage(board.url, order.mode)
            holder.load = FtutterCatalogLoad(key = key, tick = tick, items = page.items)
            val pruned = ftutterPruneHiddenThreads(hidden, board.url, page.items)
            if (pruned.size != hidden.size) savePreference(FtutterKeys.HIDDEN_THREADS, encodeFtutterList(pruned))
            if (refreshFromBottom) {
                // 原作：下に引っ張った更新の後は先頭へ戻る。
                holder.gridState.scrollToItem(0)
                holder.staggeredState.scrollToItem(0)
                refreshFromBottom = false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            holder.load = FtutterCatalogLoad(
                key = key,
                tick = tick,
                items = current?.takeIf { it.key == key }?.items.orEmpty(),
                error = error.message ?: "カタログを取得できませんでした"
            )
            refreshFromBottom = false
        }
    }
    val load = holder.load?.takeIf { it.key == key }
    val matcher = remember(ngRules, board.url) { FtutterNgMatcher(ngRules, board.url) }
    val pickupOrders = remember(preferences) { parseFtutterPickupOrders(preferences[FtutterKeys.PICKUP_ORDER_MODES]) }
    // 原作の順：検索→非表示→NGタイトル→ピックアップ→表示数の上限。
    val ngResult = remember(load?.items, query, hidden, matcher, order, pickupOrders, commonCatalogNgWords) {
        ftutterApplyCommonCatalogNg(
            ftutterApplyCatalogNg(ftutterVisibleCatalog(load?.items.orEmpty(), query, hidden.toSet(), Int.MAX_VALUE), matcher, order, pickupOrders),
            commonCatalogNgWords
        )
    }
    var archiveQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ngResult) { holder.ngRemoved = ngResult.ngRemoved }
    val visible = remember(ngResult, settings.maxThreadCount) { ngResult.items.take(settings.maxThreadCount) }
    var ngDraft by remember { mutableStateOf<FtutterNgRule?>(null) }
    var delTarget by remember { mutableStateOf<CatalogItem?>(null) }
    fun refresh() { holder.refreshTick++ }

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
            onAction = { action ->
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
                    else -> onAppGesture(action)
                }
            },
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
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.highlight)
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
                        val next = (hidden + ftutterCanonicalThreadUrl(item.threadUrl)).distinct()
                        savePreference(FtutterKeys.HIDDEN_THREADS, encodeFtutterList(next))
                        onNotice("非表示にしました")
                    }
                    // 原作：タブを閉じて履歴から削除する。
                    FtutterThumbAction.DELETE_HISTORY -> historyIndex[item.id]?.let(onDeleteHistory)
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
            onSave = { rule -> ngDraft = null; onSaveNgRules(ngRules + rule); onNotice("登録しました") }
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
    onTogglePrivacy: () -> Unit,
    onToolbarEdit: () -> Unit,
    onHiddenAndNg: () -> Unit,
    onOpenFromUrl: () -> Unit,
    onCopyUrl: () -> Unit
) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("ftutter-catalog-tool-menu")) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "メニュー", tint = colors.onToolBar)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
    Column(
        Modifier
            .border(0.5.dp, colors.outline)
            .pointerInput(item.threadUrl, settings.useZoom) {
                detectTapGestures(
                    onTap = { onOpen(item) },
                    onLongPress = { if (settings.useZoom) onZoom(item) else onThumbMenu(item) },
                    onPress = {
                        // 原作：長押し拡大は押している間だけ。指を離すと閉じる。
                        tryAwaitRelease()
                        if (settings.useZoom) onZoom(null)
                    }
                )
            }
            .testTag("ftutter-catalog-item")
    ) {
        if (settings.numberPosition == FtutterNumberPosition.ABOVE) numbersRow()
        val aspect = if (settings.sameHeight) 1f else ftutterThumbAspectRatio(item.thumbnailWidth, item.thumbnailHeight, settings)
        Box(Modifier.fillMaxWidth().aspectRatio(aspect)) {
            CatalogPreviewImage(
                thumbnailUrl = item.thumbnailUrl,
                fullImageUrl = item.fullImageUrl,
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
        CatalogPreviewImage(
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
private fun FtutterThumbDialog(hasHistory: Boolean, onDismiss: () -> Unit, onAction: (FtutterThumbAction) -> Unit) {
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
    savePreference: (String, String) -> Unit,
    onBack: () -> Unit,
    /** 今の板のカタログでNGタイトルに当たったスレ。 */
    ngRemoved: List<CatalogItem> = emptyList()
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
                            savePreference(FtutterKeys.HIDDEN_THREADS, encodeFtutterList(hidden - url))
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
                Text(
                    item.title.orEmpty(), color = colors.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).testTag("ftutter-ng-removed-row")
                )
            }
        }
    }
}
