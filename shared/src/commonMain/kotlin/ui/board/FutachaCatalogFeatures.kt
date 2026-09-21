package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

internal val LocalFutachaCatalogTools = staticCompositionLocalOf<List<FutachaThreadTool>> { emptyList() }
internal val LocalFutachaCatalogLongPress = staticCompositionLocalOf<((CatalogItem) -> Unit)?> { null }

internal fun CatalogMode.sharedCatalogSort(): CompatCatalogSort? = when (this) {
    CatalogMode.Catalog -> CompatCatalogSort.CATALOG
    CatalogMode.New -> CompatCatalogSort.NEW
    CatalogMode.Old -> CompatCatalogSort.OLD
    CatalogMode.Many -> CompatCatalogSort.MANY
    CatalogMode.Few -> CompatCatalogSort.FEW
    CatalogMode.Momentum -> CompatCatalogSort.LIVELY
    else -> null
}

@Composable
internal fun FutachaCatalogFeatureHost(
    board: BoardSummary?, mode: CatalogMode, state: CatalogUiState,
    repository: BoardRepository, onOpenThread: (CatalogItem) -> Unit,
    onRestore: (CatalogUiState.Success) -> Unit,
    onScrollPage: (Int) -> Unit, onScrollTop: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHistoryThread: (ThreadHistoryEntry) -> Unit,
    ngFilteringEnabled: Boolean = true,
    content: @Composable (CatalogUiState) -> Unit
) {
    val features = LocalFutachaSharedFeatures.current
    val boardUrl = board?.url?.let(::canonicalizeBoardUrl)
    if (features == null || board == null || boardUrl == null) { content(state); return }
    val boardKey = compatBoardKey(boardUrl)
    val scope = rememberCoroutineScope()
    val rules by features.store.ngRules.collectAsState(emptyList())
    val history by features.store.history.collectAsState(emptyList())
    val tabs by features.store.tabs.collectAsState(emptyList())
    var stripVisible by remember(boardKey) { mutableStateOf(features.value("design", "designTabSelectorOpened") == "ON") }
    LaunchedEffect(features.value("design", "designTabSelectorOpened")) {
        stripVisible = features.value("design", "designTabSelectorOpened") == "ON"
    }
    var preference by remember(boardKey) { mutableStateOf(CompatCatalogPreference(boardKey)) }
    var dropped by remember(boardKey) { mutableStateOf<List<CompatDroppedCatalogItem>>(emptyList()) }
    var previous by remember(boardKey, mode) { mutableStateOf<List<CatalogUiState.Success>>(emptyList()) }
    var latest by remember(boardKey, mode) { mutableStateOf<CatalogUiState.Success?>(null) }
    var restoring by remember(boardKey, mode) { mutableStateOf(false) }
    var ngOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var droppedOpen by remember { mutableStateOf(false) }
    var undoOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CatalogItem?>(null) }
    var imageNg by remember { mutableStateOf<CatalogItem?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun action(block: suspend () -> Unit) { scope.launch {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { message = error.message ?: "操作できませんでした" }
    } }
    LaunchedEffect(boardKey, mode) {
        try {
            features.store.importModernBoards(listOf(board))
            preference = features.store.loadCatalogPreference(boardKey)
            dropped = features.store.loadDroppedCatalogItems(boardKey)
            mode.sharedCatalogSort()?.let { sort ->
                previous = (1..4).mapNotNull { generation -> features.store.loadCatalogSnapshot(boardKey, sort, generation)
                    ?.let { CatalogUiState.Success(CatalogPageContent(it.items)) } }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { message = "カタログ設定を読み込めませんでした" }
    }
    LaunchedEffect(state, boardKey, mode) {
        val success = state as? CatalogUiState.Success ?: return@LaunchedEffect
        if (success.content.items == latest?.content?.items) return@LaunchedEffect
        val wasRestoring = restoring
        restoring = false
        if (!wasRestoring) latest?.let { previous = (listOf(it) + previous).take(4) }
        val hadLatest = latest != null
        latest = success
        if (!wasRestoring) {
            try {
                val now = Clock.System.now().toEpochMilliseconds()
                mode.sharedCatalogSort()?.let { sort ->
                    val stored = features.store.loadCatalogSnapshot(boardKey, sort)
                    val trackDropped = features.value("catalog", "catalogFindThreadDeleted") == "ON"
                    val requestedCount = features.intValue("catalog", "catalogThreadSize", 100..3000) ?: 300
                    val activeDropped = if (trackDropped && stored != null) {
                        val candidates = diffCompatCatalogGenerations(success.content.items, stored.items, requestedCount, true).vanishedWithin.take(64)
                        withTimeoutOrNull(5_000) { buildSet {
                            candidates.forEach { if (repository.probeThreadExists(it.threadUrl)) add(it.id) }
                        } }.orEmpty()
                    } else emptySet()
                    if (stored?.items != success.content.items) features.store.saveCatalogSnapshot(
                        CompatCatalogSnapshot(boardKey, sort, now, now, success.content.items),
                        trackDropped = trackDropped, requestedThreadCount = requestedCount, activeDroppedThreadIds = activeDropped)
                    dropped = features.store.loadDroppedCatalogItems(boardKey)
                }
                if (hadLatest && features.value("catalog", "catalogReloadScrollTop") == "ON") onScrollTop()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = "カタログ履歴を保存できませんでした" }
        }
    }
    fun savePreference(next: CompatCatalogPreference) { action {
        features.store.saveCatalogPreference(next); preference = next
    } }
    fun register(item: CatalogItem) { action {
        val value = item.threadUrl
        check(features.store.upsertNgRule(CompatNgRule(compatNgRuleId(CompatNgKind.CATALOG_REFUSE, boardKey, value),
            CompatNgKind.CATALOG_REFUSE, boardKey, value, Clock.System.now().toEpochMilliseconds(), memo = item.title.orEmpty().take(300))))
        selected = null
    } }
    fun addTab(item: CatalogItem) { action {
        val now = Clock.System.now().toEpochMilliseconds()
        features.store.openTab(CompatTab(compatTabKey(item.threadUrl), item.threadUrl, item.threadUrl,
            boardKey, board.name, item.id, item.title.orEmpty(), thumbnailUrl = item.thumbnailUrl,
            replyCount = item.replyCount, insertedAtEpochMillis = now, contentUpdatedAtEpochMillis = now))
        selected = null
    } }
    fun requestDeletion(item: CatalogItem) { action {
        repository.requestDeletion(boardUrl, item.id, item.id, "110")
        selected = null
        message = "DEL依頼を送信しました"
    } }
    val tools = listOf(
        FutachaThreadTool(if (stripVisible) "タブバーを隠す" else "タブバーを表示") { stripVisible = !stripVisible },
        FutachaThreadTool("更新前のカタログ", previous.isNotEmpty()) { undoOpen = true },
        FutachaThreadTool("消えたスレ・隔離") { droppedOpen = true },
        FutachaThreadTool(if (preference.replyPriorityEnabled) "レス数による優先表示を解除" else "レス数による優先表示") {
            savePreference(preference.copy(replyPriorityEnabled = !preference.replyPriorityEnabled))
        },
        FutachaThreadTool(if (preference.showNonPriority) "少ないレスを非表示" else "少ないレスも表示") {
            savePreference(preference.copy(showNonPriority = !preference.showNonPriority))
        },
        FutachaThreadTool("NGの詳細管理") { ngOpen = true },
        FutachaThreadTool("キャッシュ検索") { searchOpen = true },
        FutachaThreadTool("表示・取得の詳細設定") { features.openSettings("catalog") }
    )
    val volume = features.displayValue("control", "controlCatalogVolumeKey")
    val owner = remember { Any() }
    val scroll by rememberUpdatedState(onScrollPage)
    DisposableEffect(volume) {
        CompatVolumeKeyBus.register(owner) { key ->
            if (volume == "スクロール") { scroll(if (key == CompatVolumeKey.UP) -1 else 1); true } else false
        }
        onDispose { CompatVolumeKeyBus.unregister(owner) }
    }
    val items = (state as? CatalogUiState.Success)?.content?.items.orEmpty()
    val scopedRules = remember(rules, boardKey, ngFilteringEnabled) {
        if (ngFilteringEnabled) compatCatalogRulesForBoard(rules, boardKey) else emptyList()
    }
    val phashRules = remember(scopedRules) { scopedRules.filter { it.kind == CompatNgKind.CATALOG_IMAGE_PHASH } }
    val threshold = features.value("thread", "threadImageNgPhashThreshold")?.toIntOrNull() ?: CompatImagePhash.DEFAULT_THRESHOLD
    val phashes by produceState(emptyMap<String, String>(), items, phashRules) {
        value = if (phashRules.isEmpty() || features.httpClient == null) emptyMap() else buildMap {
            items.take(256).forEach { item ->
                (item.fullImageUrl ?: item.thumbnailUrl)?.let { url ->
                    fetchCompatImagePhash(features.httpClient, url).getOrNull()?.let { put(item.id, it) }
                }
            }
        }
    }
    val projected by produceState(items, items, scopedRules, preference, features.preferences, phashes, dropped) {
        value = withContext(AppDispatchers.parsing) {
            val index = buildCompatCatalogRuleIndex(scopedRules)
            val titleLimit = features.intValue("catalog", "catalogTitleLength", 10..30)
            val filtered = items.filterNot { item -> index.hides(item) || phashes[item.id]?.let { phash ->
                phashRules.any { CompatImagePhash.isSimilar(phash, it.normalizedValue, threshold) }
            } == true }
            val prioritized = projectCompatCatalogItems(filtered, preference.replyPriorityEnabled,
                features.intValue("catalog", "delayFewReplies", 0..30) ?: preference.fewRepliesDelay,
                preference.showNonPriority, index::extracts)
            val combined = appendCompatDroppedCatalogItems(prioritized, dropped.filterNot { index.hides(it.item) },
                features.value("catalog", "catalogAppendDropped") == "ON")
            if (titleLimit == null) combined else combined.map { it.copy(title = it.title?.take(titleLimit)) }
        }
    }
    val strip: (@Composable () -> Unit)? = if (!stripVisible) null else ({
        CompatTabSelector(tabs, null, false, { tab -> onOpenHistoryThread(tab.toFutachaHistoryEntry()) },
            { tab -> action { features.store.closeTabs(setOf(tab.key), Clock.System.now().toEpochMilliseconds())?.let(features.onTabsClosed) } },
            onCheckUpdates = { action { refreshCompatTabsInBackground(features.store, repository, maxTabs = 100) } }, onReload = onRefresh,
            longTapAction = features.displayValue("control", "controlTabSelectorLongTap") ?: "選択メニュー")
    })
    CompositionLocalProvider(LocalFutachaCatalogTools provides tools,
        LocalFutachaTabStrip provides strip,
        LocalFutachaScrollRefreshEnabled provides (features.value("catalog", "catalogPullToRefresh") != "OFF"),
        LocalFutachaCatalogLongPress provides { item ->
            when (features.value("control", "controlCatalogLongTap") ?: "menu") {
                "none", "何もしない" -> Unit
                "ng", "NGスレッドに登録" -> register(item)
                "add", "タブに追加する" -> addTab(item)
                "del", "delを送信する" -> requestDeletion(item)
                else -> selected = item
            }
        }) {
        content(if (state is CatalogUiState.Success) state.copy(content = state.content.copy(items = projected)) else state)
    }
    if (undoOpen) AlertDialog(onDismissRequest = { undoOpen = false }, title = { Text("更新前のカタログ") }, text = {
        Column { previous.forEachIndexed { index, snapshot -> TextButton(onClick = {
            restoring = true; onRestore(snapshot); undoOpen = false
        }) { Text("${index + 1}回前 (${snapshot.content.items.size}スレ)") } } }
    }, confirmButton = { TextButton(onClick = { undoOpen = false }) { Text("閉じる") } })
    if (ngOpen) FutachaNgManagementDialog(features, boardKey, null, board.name, onDismiss = { ngOpen = false })
    if (searchOpen) CompatCatalogCacheSearchDialog(features.httpClient, features.store, boardKey, boardUrl,
        localHistory = history, onDismiss = { searchOpen = false }, onOpenThread = { searchOpen = false; onOpenThread(it) })
    if (droppedOpen) Dialog(onDismissRequest = { droppedOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompatDroppedCatalogScreen(board.name, dropped, { droppedOpen = false }, { droppedOpen = false; onOpenThread(it) }, {
            features.store.deleteDroppedCatalogItems(boardKey, CompatCatalogDroppedClass.DIE)
            dropped = features.store.loadDroppedCatalogItems(boardKey)
        })
    }
    selected?.let { item -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(item.title.orEmpty()) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TextButton(onClick = { selected = null; onOpenThread(item) }) { Text("スレッドを開く") }
            TextButton(onClick = { addTab(item) }) { Text("タブに追加") }
            TextButton(onClick = { register(item) }) { Text("NGスレッドに登録") }
            if (item.fullImageUrl != null || item.thumbnailUrl != null) TextButton(onClick = { imageNg = item; selected = null }) { Text("NG画像に登録") }
            TextButton(onClick = { requestDeletion(item) }) { Text("DEL依頼を送信") }
        }
    }, confirmButton = { TextButton(onClick = { selected = null }) { Text("閉じる") } }) }
    imageNg?.let { item -> FutachaImageNgRegistration(features, boardKey, CompatImageNgSource.CATALOG,
        item.fullImageUrl ?: item.thumbnailUrl.orEmpty(), item.title.orEmpty(), { imageNg = null }) }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("閉じる") } }) }
}
