package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

internal data class FutachaThreadTool(val label: String, val enabled: Boolean = true, val action: () -> Unit)
internal val LocalFutachaThreadTools = staticCompositionLocalOf<List<FutachaThreadTool>> { emptyList() }

internal fun CompatTab.toFutachaHistoryEntry(): ThreadHistoryEntry = ThreadHistoryEntry(
    threadId = threadNo, boardId = boardKey, boardName = boardName, boardUrl = originalUrl,
    title = title, titleImageUrl = thumbnailUrl.orEmpty(), replyCount = replyCount,
    lastVisitedEpochMillis = contentUpdatedAtEpochMillis
)

@Composable
internal fun FutachaThreadFeatureHost(
    board: BoardSummary,
    threadId: String,
    threadTitle: String,
    currentState: ThreadUiState,
    listState: LazyListState,
    repository: BoardRepository,
    onRestore: (ThreadUiState.Success) -> Unit,
    onRefresh: () -> Unit,
    onOpenThread: (ThreadHistoryEntry) -> Unit,
    onShowPost: (Post) -> Unit,
    onOpenDrawer: () -> Unit,
    onReply: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    val features = LocalFutachaSharedFeatures.current
    val canonicalBoard = canonicalizeBoardUrl(board.url)
    if (features == null || canonicalBoard == null) { content(); return }
    val sourceUrl = "${canonicalBoard}res/$threadId.htm"
    val tabKey = compatTabKey(sourceUrl)
    val boardKey = compatBoardKey(canonicalBoard)
    val tabs by features.store.tabs.collectAsState(emptyList())
    val ngRules by features.store.ngRules.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val openUrl = rememberUrlLauncher()
    var previous by remember(tabKey) { mutableStateOf<ThreadUiState.Success?>(null) }
    var latest by remember(tabKey) { mutableStateOf<ThreadUiState.Success?>(null) }
    var restoring by remember(tabKey) { mutableStateOf(false) }
    var automatic by remember(tabKey) { mutableStateOf(false) }
    var touchedAt by remember(tabKey) { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var mediaOpen by remember(tabKey) { mutableStateOf(false) }
    var viewerIndex by remember(tabKey) { mutableStateOf<Int?>(null) }
    var viewerPostNo by remember(tabKey) { mutableStateOf<String?>(null) }
    var viewerToolbarOpen by remember { mutableStateOf(false) }
    var viewerToolbarRevision by remember { mutableLongStateOf(0) }
    var urlsOpen by remember { mutableStateOf(false) }
    var cacheSearchOpen by remember { mutableStateOf(false) }
    var tabsOpen by remember { mutableStateOf(false) }
    var ngOpen by remember { mutableStateOf(false) }
    var pageSaveOpen by remember { mutableStateOf(false) }
    var stripVisible by remember(tabKey) { mutableStateOf(features.value("design", "designTabSelectorOpened") == "ON") }
    LaunchedEffect(features.value("design", "designTabSelectorOpened")) {
        stripVisible = features.value("design", "designTabSelectorOpened") == "ON"
    }
    var extraction by remember(tabKey) { mutableStateOf<CompatExtractionKind?>(null) }
    val history by features.store.history.collectAsState(emptyList())
    val apngCache = remember(scope) { CompatApngMarkerCache(scope) }
    fun launchAction(block: suspend () -> Unit) = scope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { message = failure.message ?: "操作に失敗しました" }
    }
    LaunchedEffect(currentState, tabKey) {
        val next = currentState as? ThreadUiState.Success ?: return@LaunchedEffect
        if (latest?.page != next.page) {
            if (!restoring) previous = latest
            restoring = false
            latest = next
        }
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            features.store.importModernBoards(listOf(board))
            // Also supports fixture/locally restored responses whose cache was not written by HTTP loading.
            val snapshot = next.page.toCompatThreadSnapshot(tabKey, now)
            val storedSnapshot = features.store.loadThreadSnapshot(tabKey)
            val snapshotChanged = storedSnapshot?.copy(revision = snapshot.revision,
                fetchedAtEpochMillis = snapshot.fetchedAtEpochMillis) != snapshot
            if (snapshotChanged) {
                features.store.saveSharedThreadSnapshot(sourceUrl, sourceUrl, board.name, threadTitle,
                    next.page.posts.firstOrNull()?.thumbnailUrl, snapshot)
            }
            val existing = features.store.tabs.first().firstOrNull { it.key == tabKey }
            val updated = (existing ?: CompatTab(tabKey, sourceUrl, sourceUrl, boardKey, board.name,
                threadId, threadTitle, insertedAtEpochMillis = now, contentUpdatedAtEpochMillis = now)).copy(
                title = threadTitle, replyCount = next.page.compatReplyCount(),
                checkedReplyCount = next.page.compatReplyCount(), snapshotRevision = if (snapshotChanged) snapshot.revision else storedSnapshot?.revision ?: snapshot.revision,
                contentUpdatedAtEpochMillis = now, thumbnailUrl = next.page.posts.firstOrNull()?.thumbnailUrl)
            if (existing == null) features.store.openTab(updated) else features.store.updateTab(updated)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { message = "追加操作用のスレッドを保存できませんでした" }
    }
    val page = (currentState as? ThreadUiState.Success)?.page ?: latest?.page
    val tab = tabs.firstOrNull { it.key == tabKey } ?: CompatTab(
        tabKey, sourceUrl, sourceUrl, boardKey, board.name, threadId, threadTitle,
        insertedAtEpochMillis = 0, contentUpdatedAtEpochMillis = 0
    )
    val latestTabs by rememberUpdatedState(tabs)
    val latestRefresh by rememberUpdatedState(onRefresh)
    fun moveThread(direction: Int) {
        val index = latestTabs.indexOfFirst { it.key == tabKey }
        latestTabs.getOrNull(index + direction)?.let { onOpenThread(it.toFutachaHistoryEntry()) }
    }
    fun scrollPage(direction: Int) { scope.launch {
        listState.scrollBy((listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) * direction.toFloat())
    } }
    fun closeTab(key: String) { launchAction {
        features.store.closeTabs(setOf(key), Clock.System.now().toEpochMilliseconds())?.let(features.onTabsClosed)
        if (key == tabKey) {
            val next = latestTabs.firstOrNull { it.key != key }
            if (features.value("control", "controlThreadCloseBack") == "ON" || next == null) onClose()
            else onOpenThread(next.toFutachaHistoryEntry())
        }
    } }
    val volumeAction = features.displayValue("control", "controlThreadVolumeKey", "ボリュームキー")
    val volumeOwner = remember { Any() }
    DisposableEffect(volumeAction, tabKey) {
        CompatVolumeKeyBus.register(volumeOwner) { key ->
            val direction = if (key == CompatVolumeKey.UP) -1 else 1
            when (volumeAction) {
                "1レス分スクロール" -> { scope.launch {
                    listState.animateScrollToItem((listState.firstVisibleItemIndex + direction)
                        .coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                }; true }
                "1画面分スクロール" -> { scrollPage(direction); true }
                "スレッドの切り替え" -> { moveThread(direction); true }
                else -> false
            }
        }
        onDispose { CompatVolumeKeyBus.unregister(volumeOwner) }
    }
    val scrollPixels = features.value("thread", "autoScrollPixel", "オートスクロール量")?.filter(Char::isDigit)?.toFloatOrNull()?.coerceIn(1f, 100f) ?: 5f
    val scrollDelay = features.value("thread", "autoScrollSpeed", "オートスクロール速度")?.filter(Char::isDigit)?.toLongOrNull()?.coerceIn(10, 1000) ?: 50L
    LaunchedEffect(automatic, scrollPixels, scrollDelay, tab.isDead) {
        if (!automatic || tab.isDead) { automatic = false; return@LaunchedEffect }
        while (isActive) {
            delay(scrollDelay)
            if (Clock.System.now().toEpochMilliseconds() - touchedAt < 5_000 || listState.isScrollInProgress) continue
            if (listState.canScrollForward) listState.scrollBy(scrollPixels)
            else { delay(12_000); if (isActive && Clock.System.now().toEpochMilliseconds() - touchedAt >= 5_000) latestRefresh() }
        }
    }
    val tools = listOf(
        FutachaThreadTool("タブ一覧") { tabsOpen = true },
        FutachaThreadTool(if (stripVisible) "タブバーを隠す" else "タブバーを表示") { stripVisible = !stripVisible },
        FutachaThreadTool("前のスレッド", tabs.indexOfFirst { it.key == tabKey } > 0) { moveThread(-1) },
        FutachaThreadTool("次のスレッド", tabs.indexOfFirst { it.key == tabKey } in 0 until tabs.lastIndex) { moveThread(1) },
        FutachaThreadTool("更新前に戻す", previous != null) { previous?.let { restoring = true; previous = null; onRestore(it) } },
        FutachaThreadTool("1ページ上へ") { scrollPage(-1) },
        FutachaThreadTool("1ページ下へ") { scrollPage(1) },
        FutachaThreadTool(if (automatic) "自動スクロールを停止" else "自動スクロール", !tab.isDead) { automatic = !automatic },
        FutachaThreadTool("画像一覧・選択保存", page != null) { mediaOpen = true },
        FutachaThreadTool("形式を選んでスレッド保存", page != null && features.fileSystem != null && features.httpClient != null) { pageSaveOpen = true },
        FutachaThreadTool("NGの詳細管理") { ngOpen = true },
        FutachaThreadTool(if (extraction == CompatExtractionKind.NG) "NG抽出を解除" else "NGレスを抽出") {
            extraction = if (extraction == CompatExtractionKind.NG) null else CompatExtractionKind.NG
        },
        FutachaThreadTool(if (extraction == CompatExtractionKind.MANY_SAIDANE) "そうだね抽出を解除" else "しきい値で抽出：そうだね") {
            extraction = if (extraction == CompatExtractionKind.MANY_SAIDANE) null else CompatExtractionKind.MANY_SAIDANE
        },
        FutachaThreadTool(if (extraction == CompatExtractionKind.MANY_REPLIES) "返信抽出を解除" else "しきい値で抽出：返信") {
            extraction = if (extraction == CompatExtractionKind.MANY_REPLIES) null else CompatExtractionKind.MANY_REPLIES
        },
        FutachaThreadTool("キャッシュ検索") { cacheSearchOpen = true },
        FutachaThreadTool("URL・アーカイブ") { urlsOpen = true },
        FutachaThreadTool("表示・抽出の詳細設定") { features.openSettings("thread") },
        FutachaThreadTool("スレッドを閉じる") { closeTab(tabKey) }
    )
    val fontSize = features.intValue("thread", "threadFontSize", 10..30)
    val baseTypography = MaterialTheme.typography
    val readingTypography = if (fontSize == null) baseTypography else baseTypography.copy(
        bodyLarge = baseTypography.bodyLarge.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.45).sp),
        bodyMedium = baseTypography.bodyMedium.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.45).sp),
        bodySmall = baseTypography.bodySmall.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.45).sp))
    val strip: (@Composable () -> Unit)? = if (!stripVisible) null else ({
        CompatTabSelector(tabs, tabKey, true, { onOpenThread(it.toFutachaHistoryEntry()) }, { closing -> closeTab(closing.key) },
            onReply = onReply, onCheckUpdates = { launchAction {
                refreshCompatTabsInBackground(features.store, repository, maxTabs = 100)
            } }, onReload = onRefresh,
            longTapAction = features.displayValue("control", "controlTabSelectorLongTap") ?: "選択メニュー")
    })
    CompositionLocalProvider(LocalFutachaThreadTools provides tools,
        LocalFutachaTabStrip provides strip,
        LocalFutachaScrollRefreshEnabled provides (features.value("thread", "threadPullToRefresh") != "OFF"),
        LocalFutachaPostTap provides if (features.value("control", "controlTouchOpenDrawer") == "ON") onOpenDrawer else null,
        LocalFutachaThreadProjection provides FutachaThreadProjection(tabKey, boardKey, extraction)) {
        MaterialTheme(typography = readingTypography) {
        Box(Modifier.fillMaxSize().pointerInput(tabKey) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.pressed }) touchedAt = Clock.System.now().toEpochMilliseconds()
                }
            }
        }) { content() }
        }
    }
    if (tabsOpen) FutachaTabsDialog(features, repository, onOpenThread, onDismiss = { tabsOpen = false })
    if (pageSaveOpen && page != null) FutachaPageSaveDialog(features, page, boardKey, board.name,
        canonicalBoard, threadTitle, { pageSaveOpen = false })
    if (ngOpen) FutachaNgManagementDialog(features, boardKey, tabKey, board.name, onDismiss = { ngOpen = false })
    if (mediaOpen) {
        Dialog(onDismissRequest = { mediaOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                when {
                    viewerToolbarOpen -> CompatToolbarEditorScreen(CompatToolbarSurface.VIEWER, features.store) {
                        viewerToolbarOpen = false; viewerToolbarRevision++
                    }
                    viewerIndex != null -> CompatViewerScreen(
                        tab = tab, initialIndex = viewerIndex ?: 0, initialPostNo = viewerPostNo,
                        store = features.store, preferences = features.preferences, ngRules = ngRules,
                        httpClient = features.httpClient, fileSystem = features.fileSystem,
                        cookieRepository = features.cookieRepository, toolbarRefreshToken = viewerToolbarRevision,
                        onToolbarEdit = { viewerToolbarOpen = true },
                        onShowSourcePost = { anchor ->
                            page?.posts?.firstOrNull { it.id == anchor.postNo }?.let(onShowPost)
                            mediaOpen = false; viewerIndex = null
                        },
                        onOpenGallery = { _, _ -> viewerIndex = null },
                        onOpenSettings = { features.openSettings("viewer") },
                        onOpenCommonSettings = { features.openSettings("viewer") },
                        onBack = { viewerIndex = null }
                    )
                    else -> CompatGalleryScreen(tab = tab, store = features.store, preferences = features.preferences,
                        ngRules = ngRules, httpClient = features.httpClient, apngMarkerCache = apngCache,
                        fileSystem = features.fileSystem, cookieRepository = features.cookieRepository,
                        onOpenViewer = { index, post -> viewerIndex = index; viewerPostNo = post },
                        onOpenSettings = { features.openSettings("viewer") },
                        onOpenCommonSettings = { features.openSettings("viewer") }, onBack = { mediaOpen = false })
                }
            }
        }
    }
    if (cacheSearchOpen) CompatCatalogCacheSearchDialog(features.httpClient, features.store, boardKey, canonicalBoard,
        localHistory = history,
        onDismiss = { cacheSearchOpen = false }, onOpenThread = { item ->
            onOpenThread(tab.copy(threadNo = item.id, title = item.title.orEmpty(), originalUrl = item.threadUrl,
                canonicalUrl = item.threadUrl, key = compatTabKey(item.threadUrl)).toFutachaHistoryEntry())
            cacheSearchOpen = false
        })
    if (urlsOpen) {
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        val share = rememberCompatShareLauncher()
        AlertDialog(onDismissRequest = { urlsOpen = false }, title = { Text("URL・アーカイブ") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(sourceUrl)); urlsOpen = false }) { Text("URLをコピー") }
                TextButton(onClick = { share(sourceUrl, "text/plain", null) }) { Text("URLを共有") }
                TextButton(onClick = { openUrl(buildCompatFtbucketUrl(sourceUrl)) }) { Text("FTBucketに登録") }
                TextButton(onClick = { launchAction {
                    val client = features.httpClient ?: error("通信機能を利用できません")
                    openUrl(registerCompatTsumanne(client, sourceUrl, tab.title).getOrThrow())
                } }) { Text("つまんね。に登録") }
                buildCompatForestUrl(sourceUrl)?.let { url -> TextButton(onClick = { openUrl(url) }) { Text("ふたばフォレストで開く") } }
                buildCompatFutapoUrl(sourceUrl)?.let { url -> TextButton(onClick = { openUrl(url) }) { Text("ふたポで開く") } }
            }
        }, confirmButton = { TextButton(onClick = { urlsOpen = false }) { Text("閉じる") } })
    }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("閉じる") } }) }
}
