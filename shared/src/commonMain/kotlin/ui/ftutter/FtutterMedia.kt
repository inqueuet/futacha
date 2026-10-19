package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.util.ScrollableToolbarRow

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import com.valoser.futacha.shared.ui.compat.CompatViewerImagePage
import com.valoser.futacha.shared.ui.compat.CompatViewerTransform
import com.valoser.futacha.shared.ui.compat.compatUniqueMediaKeys
import com.valoser.futacha.shared.ui.compat.isCompatVideoMediaUrl
import com.valoser.futacha.shared.ui.compat.resolveCompatViewerMediaUrl
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.launch

/** 保存のための道具（保存先・1件の保存・まとめての保存）。アプリ側で作って渡す。 */
internal class FtutterMediaSaveTools(
    /** 保存先の名前（ボタンに出す）。未設定は既定の場所。 */
    val destinationLabel: String,
    /** 保存先を選び直す（ふたちゃと共有の保存先）。 */
    val chooseDestination: () -> Unit,
    /** 1件を保存する（保存先が未設定なら先に選ぶ）。 */
    val saveOne: (url: String) -> Unit,
    /** まとめて保存する。結果は保存できた数。 */
    val saveMany: suspend (urls: List<String>, onProgress: (done: Int) -> Unit) -> Int,
    val isSaving: Boolean
)

/** 番号カード（原作 BackToIndexCard）：左上の小さなカードにレスの通し番号。タップでスレのそのレスへ戻る。 */
@Composable
private fun FtutterIndexCard(position: Int, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val colors = LocalFtutterColors.current
    Box(
        modifier.widthIn(min = 20.dp).background(colors.popUp, CutCornerShape(topStart = 0.dp, bottomEnd = 6.dp))
            .clickable(onClick = onTap).padding(horizontal = 4.dp).testTag("ftutter-index-card"),
        contentAlignment = Alignment.Center
    ) { Text("$position", color = colors.text, fontSize = 12.sp) }
}

/** 一覧・帯の1枚。動画には再生の印を重ねる。 */
@Composable
private fun FtutterMediaCell(post: CompatPostSnapshot, privacyAlpha: Float, modifier: Modifier = Modifier, thumbnailPixels: Int = 250, useOriginal: Boolean = false) {
    val url = resolveCompatViewerMediaUrl(post)
    Box(modifier) {
        FtutterThumbnail(
            thumbnailUrl = post.thumbnailUrl ?: url.orEmpty(),
            fullImageUrl = url?.takeUnless(::isCompatVideoMediaUrl),
            quality = if (useOriginal) 2 else 0,
            targetSizePx = thumbnailPixels,
            contentDescription = "No.${post.postNo}",
            modifier = Modifier.fillMaxSize().alpha(privacyAlpha)
        )
        if (url != null && isCompatVideoMediaUrl(url)) {
            Icon(Icons.Rounded.PlayCircleOutline, contentDescription = "動画", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
    }
}

/** 保存先のボタン（原作 ChooseDirectoryButton）：枠の中に保存先の名前とフォルダの印。 */
@Composable
private fun FtutterDestinationButton(tools: FtutterMediaSaveTools, modifier: Modifier = Modifier) {
    val colors = LocalFtutterColors.current
    Row(
        modifier.padding(horizontal = 4.dp).border(1.dp, colors.outline).clickable(onClick = tools.chooseDestination)
            .padding(horizontal = 6.dp, vertical = 6.dp).testTag("ftutter-save-destination"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tools.destinationLabel, color = colors.onToolBar, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.FolderOpen, contentDescription = "保存先", tint = colors.onToolBar)
    }
}

/**
 * 画像一覧（原作 GazouItiran）：既定4列、−（下限1）／＋で列数を変えてすぐ保存。各セルの左上にレス番号カード（タップでスレの
 * そのレスへ戻る）。長押しで選択モード（チェック、下に保存先・保存・全選択・全解除）。戻るは選択モードを解くだけ。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FtutterGalleryScreen(
    gesture: FtutterGestureSettings = FtutterGestureSettings(enabled = false),
    onRefresh: () -> Unit = {},
    media: List<CompatPostSnapshot>,
    columns: Int,
    settings: FtutterMediaSettings = FtutterMediaSettings(),
    privacyAlpha: Float,
    tools: FtutterMediaSaveTools,
    onColumns: (Int) -> Unit,
    onOpenViewer: (index: Int) -> Unit,
    onBackToPost: (position: Int) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalFtutterColors.current
    val scope = rememberCoroutineScope()
    var selecting by remember { mutableStateOf(false) }
    // 選んだ画像は位置でなく画像のアドレスで持つ（監査L5：選んでいる間にスレを取り直すと並びがずれ、別の画像を保存していた）。
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    fun keyOf(post: CompatPostSnapshot): String = resolveCompatViewerMediaUrl(post) ?: "#${post.position}"
    var confirm by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    PlatformBackHandler(enabled = true) {
        if (selecting) { selecting = false; selected.clear() } else onClose()
    }
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(3000); message = null } }
    // 背面のスレッドへタップを通さない（監査L4）。
    Box(Modifier.fillMaxSize().background(colors.background).pointerInput(Unit) {}.testTag("ftutter-gallery")) {
        Column(Modifier.fillMaxSize()) {
            FtutterTopBar(title = if (selecting) "選択保存" else "画像一覧", onBack = {
                if (selecting) { selecting = false; selected.clear() } else onClose()
            }) {
                if (!selecting) {
                    IconButton(onClick = { if (columns > 1) onColumns(columns - 1) }, modifier = Modifier.testTag("ftutter-gallery-minus")) {
                        Icon(Icons.Rounded.Remove, contentDescription = "列を減らす", tint = colors.onToolBar)
                    }
                    Text("$columns", color = colors.onToolBar, fontSize = 18.sp, modifier = Modifier.testTag("ftutter-gallery-columns"))
                    IconButton(onClick = { onColumns(columns + 1) }, modifier = Modifier.testTag("ftutter-gallery-plus")) {
                        Icon(Icons.Rounded.Add, contentDescription = "列を増やす", tint = colors.onToolBar)
                    }
                }
            }
            // 画像一覧のジェスチャー（原作）：戻る・前へで閉じ、一番上へ・一番下へ、更新（スレッドを取り直す）。
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            FtutterGestureLayer(
                settings = gesture,
                enabled = !selecting,
                onAction = { action ->
                    when (action) {
                        FtutterGestureAction.BACK, FtutterGestureAction.PREVIOUS -> onClose()
                        FtutterGestureAction.TOP -> scope.launch { gridState.scrollToItem(0) }
                        FtutterGestureAction.BOTTOM -> scope.launch { gridState.scrollToItem((media.size - 1).coerceAtLeast(0)) }
                        FtutterGestureAction.REFRESH -> onRefresh()
                        else -> Unit
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
            LazyVerticalGrid(GridCells.Fixed(columns), state = gridState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(media, key = { i, _ -> i }) { index, post ->
                    Box(
                        Modifier.aspectRatio(1f).padding(1.dp)
                            .combinedClickable(
                                onClick = {
                                    if (selecting) selected[keyOf(post)] = selected[keyOf(post)] != true else onOpenViewer(index)
                                },
                                onLongClick = { if (!selecting) { selecting = true; selected[keyOf(post)] = true } }
                            )
                            .testTag("ftutter-gallery-cell")
                    ) {
                        FtutterMediaCell(post, privacyAlpha, Modifier.fillMaxSize(), thumbnailPixels = if (settings.reduceThumbnail) settings.thumbnailPixels else 4096, useOriginal = !settings.reduceThumbnail)
                        if (selecting) {
                            Checkbox(checked = selected[keyOf(post)] == true, onCheckedChange = { selected[keyOf(post)] = it }, modifier = Modifier.align(Alignment.TopEnd))
                        } else {
                            FtutterIndexCard(post.position, Modifier.align(Alignment.TopStart)) { onBackToPost(post.position) }
                        }
                    }
                }
            }
            }
            if (selecting) {
                ScrollableToolbarRow(
                    itemCount = 4, minItemWidth = 48.dp,
                    modifier = Modifier.fillMaxWidth().height(50.dp).background(colors.toolBar).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FtutterDestinationButton(tools, Modifier.weight(1f))
                    IconButton(onClick = { if (selected.values.any { it }) confirm = true }, modifier = Modifier.testTag("ftutter-gallery-save")) {
                        Icon(Icons.Rounded.Download, contentDescription = "保存", tint = colors.onToolBar)
                    }
                    IconButton(onClick = { media.forEach { selected[keyOf(it)] = true } }, modifier = Modifier.testTag("ftutter-gallery-select-all")) {
                        Icon(Icons.Rounded.SelectAll, contentDescription = "全選択", tint = colors.onToolBar)
                    }
                    IconButton(onClick = { selected.clear() }) {
                        Icon(Icons.Rounded.Deselect, contentDescription = "全解除", tint = colors.onToolBar)
                    }
                }
            }
        }
        message?.let {
            Text(it, color = colors.text, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
                .background(colors.popUp).padding(12.dp).testTag("ftutter-gallery-message"))
        }
    }
    if (confirm) {
        // 今の一覧の並びで、選んだ画像（取り直しで消えた画像は除く）。
        val chosen = media.filter { selected[keyOf(it)] == true }
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { if (progress == null) confirm = false },
            title = { Text(ftutterSelectSaveQuestion(chosen.size)) },
            text = {
                progress?.let { (done, total) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Text("保存中... $done/$total")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = progress == null, onClick = {
                    val urls = chosen.mapNotNull(::resolveCompatViewerMediaUrl).distinct()
                    progress = 0 to urls.size
                    scope.launch {
                        val saved = tools.saveMany(urls) { done -> progress = done to urls.size }
                        progress = null
                        confirm = false
                        selecting = false
                        selected.clear()
                        message = "${saved}個のファイルを保存しました"
                    }
                }, modifier = Modifier.testTag("ftutter-gallery-save-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(enabled = progress == null, onClick = { confirm = false }) { Text("キャンセル") } }
        ) }
    }
}

/**
 * ビューア（原作 GalleryGati）：上のバーは無い。左右でめくり、縦のスワイプ（画面の4割か速い払い）で閉じる。拡大中はめくり・
 * 閉じを止める。下に、サムネイル帯（既定ON・高さ80、今の画像に枠、番号カード付き）と、［保存先］［保存］［帯の表示］
 * ［レス表示］［画像検索］［メニュー（ブラウザで開く）］。
 */
@Composable
internal fun FtutterViewerScreen(
    media: List<CompatPostSnapshot>,
    initialIndex: Int,
    settings: FtutterMediaSettings,
    privacyAlpha: Float,
    tools: FtutterMediaSaveTools,
    posts: List<Post>,
    conversationOf: suspend (position: Int) -> List<Pair<Int, Int>>,
    onToggleStrip: () -> Unit,
    onSearch: (imageUrl: String) -> Unit,
    onOpenBrowser: (url: String) -> Unit,
    onBackToPost: (position: Int) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalFtutterColors.current
    val scope = rememberCoroutineScope()
    val keys = remember(media) { compatUniqueMediaKeys(media) }
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, (media.size - 1).coerceAtLeast(0))) { media.size }
    val transforms = remember { mutableStateMapOf<String, CompatViewerTransform>() }
    var zoomed by remember { mutableStateOf(false) }
    var conversation by remember { mutableStateOf<List<Pair<Int, Int>>?>(null) }
    val stripState = rememberLazyListState()
    PlatformBackHandler(enabled = true, onBack = onClose)
    // 帯は今の画像を中央へ追う。
    LaunchedEffect(pager.currentPage, settings.showStrip) {
        if (settings.showStrip && media.isNotEmpty()) stripState.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0))
    }
    val current = media.getOrNull(pager.currentPage)
    val currentUrl = current?.let(::resolveCompatViewerMediaUrl)
    var loop by remember(currentUrl) { mutableStateOf(settings.videoLoop) }
    val isVideo = currentUrl?.let(::isCompatVideoMediaUrl) == true
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).testTag("ftutter-viewer")) {
        val heightPx = constraints.maxHeight.toFloat()
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxWidth().statusBarsPadding()
                    .pointerInput(zoomed) {
                        if (zoomed) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var dx = 0f
                            var dy = 0f
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                if (event.changes.size > 1) return@awaitEachGesture
                                if (!change.pressed) {
                                    if (ftutterViewerShouldClose(dy, dx, heightPx, change.uptimeMillis - down.uptimeMillis)) onClose()
                                    return@awaitEachGesture
                                }
                                dx += change.positionChange().x
                                dy += change.positionChange().y
                            }
                        }
                    }
            ) {
                HorizontalPager(state = pager, key = { keys[it] }, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
                    val post = media.getOrNull(page)
                    val url = post?.let(::resolveCompatViewerMediaUrl)
                    Box(Modifier.fillMaxSize().alpha(privacyAlpha)) {
                        if (url != null && isCompatVideoMediaUrl(url)) {
                            // 自動再生・ループは原作の設定から端末の再生部品へ渡す。
                            androidx.compose.runtime.CompositionLocalProvider(
                                com.valoser.futacha.shared.ui.board.LocalVideoPlaybackOptions provides
                                    com.valoser.futacha.shared.ui.board.VideoPlaybackOptions(settings.videoAutoPlay, loop)
                            ) {
                                PlatformVideoPlayer(videoUrl = url, isActive = page == pager.currentPage, modifier = Modifier.fillMaxSize().testTag("ftutter-viewer-video"))
                            }
                        } else {
                            CompatViewerImagePage(
                                post = post,
                                mediaUrl = url,
                                reloadSuffix = "",
                                viewerTransform = url?.let(transforms::get) ?: CompatViewerTransform(),
                                isCurrentPage = page == pager.currentPage,
                                resetKey = if (page == pager.currentPage) pager.currentPage else -1,
                                privacyEnabled = false,
                                privacyAlpha = 0f,
                                onZoomedChanged = { if (page == pager.currentPage) zoomed = it },
                                onTransformChanged = { scale, translation -> url?.let { transforms[it] = CompatViewerTransform(scale, translation) } },
                                onHorizontalSwipe = { distance, width ->
                                    val target = when {
                                        distance < -width * 0.25f -> page + 1
                                        distance > width * 0.25f -> page - 1
                                        else -> page
                                    }.coerceIn(0, media.size - 1)
                                    if (target != page) scope.launch { pager.animateScrollToPage(target) }
                                },
                                onDimensionsKnown = { _, _ -> },
                                // 原作は画像のタップ・長押しに何も割り当てていない（バーは常に出ている）。
                                onClick = {},
                                onLongClick = {}
                            )
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(colors.toolBar).navigationBarsPadding()) {
                if (settings.showStrip) {
                    LazyRow(state = stripState, modifier = Modifier.fillMaxWidth().height(settings.stripSize.dp).testTag("ftutter-viewer-strip")) {
                        itemsIndexed(media, key = { i, _ -> i }) { index, post ->
                            val selected = index == pager.currentPage
                            Box(
                                Modifier.size(settings.stripSize.dp).padding(1.dp)
                                    .then(if (selected) Modifier.border(1.5.dp, colors.highlight).background(colors.highlight.copy(alpha = 0.3f)) else Modifier)
                                    .clickable { scope.launch { pager.scrollToPage(index) } }
                            ) {
                                FtutterMediaCell(post, privacyAlpha, Modifier.fillMaxSize(), thumbnailPixels = if (settings.reduceThumbnail) settings.thumbnailPixels else 4096, useOriginal = !settings.reduceThumbnail)
                                FtutterIndexCard(post.position, Modifier.align(Alignment.TopStart)) { onBackToPost(post.position) }
                            }
                        }
                    }
                }
                ScrollableToolbarRow(itemCount = if (isVideo) 7 else 6, minItemWidth = 48.dp, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    FtutterDestinationButton(tools, Modifier.weight(1f))
                    IconButton(onClick = { currentUrl?.let(tools.saveOne) }, enabled = !tools.isSaving, modifier = Modifier.testTag("ftutter-viewer-save")) {
                        Icon(Icons.Rounded.Download, contentDescription = "保存", tint = colors.onToolBar)
                    }
                    IconButton(onClick = onToggleStrip, modifier = Modifier.testTag("ftutter-viewer-strip-toggle")) {
                        FtutterToggleIcon(Icons.Rounded.Tab, contentDescription = "サムネイルの帯", active = settings.showStrip)
                    }
                    IconButton(onClick = {
                        current?.let { post -> scope.launch { conversation = conversationOf(post.position).ifEmpty { listOf(post.position to 0) } } }
                    }, modifier = Modifier.testTag("ftutter-viewer-res")) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "レスを表示", tint = colors.onToolBar)
                    }
                    IconButton(onClick = { currentUrl?.let(onSearch) }, modifier = Modifier.testTag("ftutter-viewer-search")) {
                        Icon(Icons.Rounded.Search, contentDescription = "画像検索", tint = colors.onToolBar)
                    }
                    if (isVideo) IconButton(onClick = { loop = !loop }, modifier = Modifier.testTag("ftutter-viewer-loop")) {
                        FtutterToggleIcon(Icons.Rounded.Repeat, contentDescription = "ループ再生", active = loop)
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("ftutter-viewer-menu")) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "メニュー", tint = colors.onToolBar)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("ブラウザで開く") }, onClick = { menu = false; currentUrl?.let(onOpenBrowser) })
                        }
                    }
                }
            }
        }
    }
    // レス表示（原作 e153）：今の画像のレスを含む会話を出す。中の画像をタップすると、ビューアがその画像へ移る。
    conversation?.let { items ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { conversation = null },
            containerColor = colors.popUp,
            modifier = Modifier.testTag("ftutter-viewer-conversation"),
            text = {
                LazyColumn {
                    itemsIndexed(items) { _, (position, depth) ->
                        val post = posts.getOrNull(position) ?: return@itemsIndexed
                        Column(Modifier.fillMaxWidth().padding(start = ftutterIndentDp(depth).dp, top = 4.dp, bottom = 4.dp)) {
                            Text("$position  No.${post.id}", color = colors.headerText, fontSize = 12.sp)
                            // 本文は1つのレスにつき1回だけ変換する（監査L9：描くたびに変換していた）。
                            val plain = remember(post.messageHtml) { messageHtmlToPlainText(post.messageHtml) }
                            Text(plain, color = colors.text, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            val target = media.indexOfFirst { it.position == position }
                            if (target >= 0) {
                                Box(Modifier.size(80.dp).clickable {
                                    conversation = null
                                    scope.launch { pager.scrollToPage(target) }
                                }.testTag("ftutter-viewer-conversation-image")) {
                                    FtutterMediaCell(media[target], privacyAlpha, Modifier.fillMaxSize())
                                }
                            }
                            HorizontalDivider(color = colors.outline)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { conversation = null }) { Text("閉じる") } }
        ) }
    }
}

/** 保存先の名前（ボタンに出す）：選んだフォルダの最後の名前。未設定は既定の場所。 */
internal fun ftutterDestinationLabel(location: SaveLocation?): String = when (location) {
    null -> "既定の保存先"
    is SaveLocation.Path -> location.path.trimEnd('/').substringAfterLast('/').ifBlank { location.path }
    is SaveLocation.TreeUri -> location.uri.substringAfterLast("%3A").substringAfterLast("%2F").ifBlank { "選択したフォルダ" }
    is SaveLocation.Bookmark -> "選択したフォルダ"
}
