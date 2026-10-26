package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.semantics.contentDescription

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.valoser.futacha.shared.model.ThreadHistoryEntry

/** タブ・ドロワーの行の長押しメニュー（原作 TabDialog）。 */
internal enum class FtutterTabAction { PROTECT, REFRESH_ALL, CLOSE_FALLEN, CLOSE_AFTER, CLOSE_ALL }

/** 1行に要る値。 */
internal data class FtutterTabView(
    val url: String,
    val entry: ThreadHistoryEntry?,
    val numbers: FtutterTabNumbers,
    val protected: Boolean,
    val isTab: Boolean
)

/**
 * ドロワー（原作 Drawer）。左（設定で右・両方・無効）に、画面の約74%の幅で出す。
 * 上：分類（履歴／タブ／タブ（更新順））。タブ表示の時だけ、閉じたタブを1件ずつ戻すボタン。
 * 下：書込・未読の絞り込みと、題名の検索。
 */
@Composable
internal fun FtutterDrawerHost(
    drawerState: DrawerState,
    settings: FtutterTabSettings,
    enabled: Boolean,
    drawer: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    if (!enabled || settings.drawerSide == FtutterDrawerSide.NONE) {
        content()
        return
    }
    var openedOnRight by remember(settings.drawerSide) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val rightSide = settings.drawerSide == FtutterDrawerSide.RIGHT || settings.drawerSide == FtutterDrawerSide.BOTH && openedOnRight
    val outer = LocalLayoutDirection.current
    // 右に出す時は、ドロワーの部品を右から左の向きで置き、中身は元の向きへ戻す。
    CompositionLocalProvider(LocalLayoutDirection provides if (rightSide) LayoutDirection.Rtl else outer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // 開いている時だけスワイプで閉じられる。開く時は端の帯だけ（原作のドロワーも端から引いた時だけ開く）。
            // 画面のどこからでも開くと、横に引く折れ線ジェスチャーとぶつかる。
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides outer) {
                    val colors = LocalFtutterColors.current
                    ModalDrawerSheet(
                        drawerContainerColor = colors.background,
                        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth(0.74f).testTag("ftutter-drawer")
                    ) { drawer() }
                }
            }
        ) {
            // Observe from the content's ancestor. A transparent sibling edge strip wins hit testing
            // before its gesture detector runs, so even unconsumed taps cannot reach the controls below.
            // Keep this detector installed until the finger is up, including after the opening animation completes.
            val edgeGestures = if (settings.drawerOpenByDrag) {
                Modifier.pointerInput(settings.drawerSide) {
                    val edgeWidth = 20.dp.toPx()
                    val openDistance = 24.dp.toPx()
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (drawerState.isOpen) return@awaitEachGesture
                        val fromRight = when {
                            down.position.x <= edgeWidth && settings.drawerSide != FtutterDrawerSide.RIGHT -> false
                            down.position.x >= size.width - edgeWidth && settings.drawerSide != FtutterDrawerSide.LEFT -> true
                            else -> return@awaitEachGesture
                        }
                        var horizontal = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - down.position
                            val inward = if (fromRight) -delta.x else delta.x
                            if (!horizontal) {
                                // Leave vertical reading/scrollbar drags and outward swipes to the child.
                                if ((abs(delta.y) > slop && abs(delta.y) >= abs(delta.x)) || inward < -slop) break
                                horizontal = inward > slop && inward > abs(delta.y)
                            }
                            if (horizontal) {
                                change.consume()
                                if (inward > openDistance) {
                                    openedOnRight = fromRight
                                    scope.launch { drawerState.open() }
                                    // The rest of this swipe belongs to the drawer: unconsumed, the gesture layer
                                    // below also took it as a swipe to the next tab or back to the catalog (M78).
                                    while (true) {
                                        val rest = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                        rest.consume()
                                        if (!rest.pressed) break
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
            } else Modifier
            Box(Modifier.fillMaxSize().then(edgeGestures)) {
                CompositionLocalProvider(LocalLayoutDirection provides outer) { content() }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FtutterDrawerPanel(
    content: FtutterDrawerContent,
    rows: List<FtutterTabView>,
    filter: FtutterDrawerFilter,
    closedCount: Int,
    dismissDirection: FtutterDismissDirection,
    design: FtutterDesignSettings,
    onContentChange: (FtutterDrawerContent) -> Unit,
    onFilterChange: (FtutterDrawerFilter) -> Unit,
    /** 復元したタブを示す案内。ドロワーの外の通知は背面に隠れるので、ここで表示する。 */
    onRestoreClosed: () -> String?,
    onOpen: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onClose: (String) -> Unit,
    onToggleProtect: (String) -> Unit,
    /** 拡張「履歴の選択削除・すべて削除」。ONの時だけ「履歴」の上に選択のボタンを出す。 */
    historyTools: Boolean = false,
    showStatus: Boolean = false,
    privacy: Boolean = false,
    /** 変わると選択をやめる（ドロワーを閉じた時など）。 */
    selectionReset: Any? = null,
    onDeleteHistory: (List<String>) -> Unit = {},
    onDeleteAllHistory: () -> Unit = {}
) {
    val colors = LocalFtutterColors.current
    var searching by remember { mutableStateOf(filter.query.isNotEmpty()) }
    val toolsShown = historyTools && content == FtutterDrawerContent.HISTORY
    var selecting by remember(toolsShown, selectionReset) { mutableStateOf(false) }
    var selected by remember(toolsShown, selectionReset) { mutableStateOf(emptySet<String>()) }
    var confirmSelected by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    // 見えている行だけを選べる（絞り込みで隠れた行は選択から外す）。
    val visibleUrls = remember(rows) { rows.map { it.url }.toSet() }
    val chosen = selected intersect visibleUrls
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FtutterDrawerHeader(
            content, closedCount, toolsShown, selecting, selectionReset, onContentChange, onRestoreClosed,
            onBeginSelection = { selecting = true; selected = emptySet() },
            onDeleteAll = { confirmAll = true }
        )
        if (searching) {
            TextField(
                value = filter.query,
                onValueChange = { onFilterChange(filter.copy(query = it)) },
                placeholder = { Text("タイトル検索") },
                singleLine = true,
                colors = TextFieldDefaults.colors(focusedContainerColor = colors.popUp, unfocusedContainerColor = colors.popUp),
                modifier = Modifier.fillMaxWidth().testTag("ftutter-drawer-search")
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("ftutter-drawer-list")) {
            items(rows, key = { it.url }) { row ->
                if (selecting) {
                    FtutterDrawerRow(
                        row, design,
                        privacy = privacy,
                        showStatus = showStatus,
                        onOpen = { url -> selected = if (url in selected) selected - url else selected + url },
                        onLongPress = {},
                        checked = row.url in chosen
                    )
                    return@items
                }
                val swipeable = row.isTab && content != FtutterDrawerContent.HISTORY && dismissDirection != FtutterDismissDirection.NONE
                if (swipeable) {
                    FtutterSwipeRow(row, dismissDirection, onClose, onToggleProtect) {
                        FtutterDrawerRow(row, design, onOpen, onLongPress, privacy = privacy, showStatus = showStatus)
                    }
                } else {
                    FtutterDrawerRow(row, design, onOpen, onLongPress, privacy = privacy, showStatus = showStatus)
                }
            }
        }
        HorizontalDivider(color = colors.outline)
        if (selecting) {
            // 選択中：全選択・全解除／選んだものを削除／やめる。
            Row(Modifier.fillMaxWidth().height(48.dp).background(colors.toolBar).navigationBarsPadding()) {
                val allChosen = rows.isNotEmpty() && chosen.size == rows.size
                FtutterDrawerCheck(if (allChosen) "全解除" else "全選択", allChosen, Modifier.weight(1f).testTag("ftutter-drawer-history-select-all")) {
                    selected = if (allChosen) emptySet() else visibleUrls
                }
                VerticalDivider(color = colors.outline)
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(enabled = chosen.isNotEmpty()) { confirmSelected = true }
                        .testTag("ftutter-drawer-history-delete-selected"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "削除（${chosen.size}）", fontSize = 16.sp,
                        color = if (chosen.isNotEmpty()) colors.onToolBar else colors.onToolBar.copy(alpha = 0.38f)
                    )
                }
                VerticalDivider(color = colors.outline)
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { selecting = false; selected = emptySet() }
                        .testTag("ftutter-drawer-history-select-cancel"),
                    contentAlignment = Alignment.Center
                ) { Text("やめる", color = colors.onToolBar, fontSize = 16.sp) }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(48.dp).background(colors.toolBar).navigationBarsPadding()) {
                FtutterDrawerCheck("書込", filter.writtenOnly, Modifier.weight(1f).testTag("ftutter-drawer-written")) {
                    onFilterChange(filter.copy(writtenOnly = !filter.writtenOnly))
                }
                VerticalDivider(color = colors.outline)
                FtutterDrawerCheck("未読", filter.unreadOnly, Modifier.weight(1f).testTag("ftutter-drawer-unread")) {
                    onFilterChange(filter.copy(unreadOnly = !filter.unreadOnly))
                }
                VerticalDivider(color = colors.outline)
                IconButton(
                    onClick = {
                        searching = !searching
                        if (!searching) onFilterChange(filter.copy(query = ""))
                    },
                    modifier = Modifier.width(56.dp).fillMaxHeight().testTag("ftutter-drawer-search-toggle")
                ) {
                    FtutterToggleIcon(Icons.Rounded.Search, contentDescription = "タイトル検索", active = searching)
                }
            }
        }
    }
    if (confirmSelected) {
        FtutterConfirmDialog(
            title = "履歴を削除しますか？",
            message = "選んだ${chosen.size}件の履歴を削除します。タブも閉じます。",
            confirmLabel = "OK",
            onDismiss = { confirmSelected = false }
        ) {
            confirmSelected = false
            onDeleteHistory(rows.map { it.url }.filter { it in chosen })
            selecting = false
            selected = emptySet()
        }
    }
    if (confirmAll) {
        FtutterConfirmDialog(
            title = "履歴をすべて削除しますか？",
            // 監査L13：ここに出ていない履歴（未登録の板・絞り込みで隠れた行）も消え、履歴は他のモードと共有であることを伝える。
            message = "保護したスレッドの履歴は残します。削除した履歴のタブも閉じます。\n" +
                "ここに出ていない履歴（未登録の板のスレ、絞り込みで隠れているスレ）も消えます。履歴はふたちゃ・ふたばー風と共有なので、" +
                "そちらの履歴からも消えます。",
            confirmLabel = "OK",
            onDismiss = { confirmAll = false }
        ) {
            confirmAll = false
            onDeleteAllHistory()
            selecting = false
            selected = emptySet()
        }
    }
}

/** Keep the header and its transient feedback separate from the history list's selection state. */
@Composable
private fun FtutterDrawerHeader(
    content: FtutterDrawerContent,
    closedCount: Int,
    toolsShown: Boolean,
    selecting: Boolean,
    selectionReset: Any?,
    onContentChange: (FtutterDrawerContent) -> Unit,
    onRestoreClosed: () -> String?,
    onBeginSelection: () -> Unit,
    onDeleteAll: () -> Unit
) {
    val colors = LocalFtutterColors.current
    val restoreNoticeHolder = remember(content, selectionReset) { FtutterNoticeHolder() }
    var restoreNotice by restoreNoticeHolder
    LaunchedEffect(restoreNoticeHolder.serial) {
        if (restoreNotice != null) {
            kotlinx.coroutines.delay(4_000)
            restoreNotice = null
        }
    }
    Column {
        Row(
            Modifier.fillMaxWidth().background(colors.toolBar).statusBarsPadding().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var open by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("ftutter-drawer-content"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(content.label, color = colors.onToolBar, fontSize = 18.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = "分類", tint = colors.onToolBar)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    FtutterDrawerContent.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.label) },
                            onClick = { open = false; onContentChange(candidate) },
                            modifier = Modifier.testTag("ftutter-drawer-content-${candidate.name}")
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (toolsShown && selecting) {
                    IconButton(onClick = { onDeleteAll() }, modifier = Modifier.testTag("ftutter-drawer-history-delete-all")) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "履歴をすべて削除", tint = colors.onToolBar)
                    }
                } else if (toolsShown) {
                    IconButton(onClick = { onBeginSelection() }, modifier = Modifier.testTag("ftutter-drawer-history-select")) {
                        Icon(Icons.Rounded.Checklist, contentDescription = "履歴を選んで削除", tint = colors.onToolBar)
                    }
                }
                // 原作 Drawer.dart は履歴ではこのボタンを出さない。履歴の削除・復元ではなく、タブを閉じた操作のUndo。
                if (content != FtutterDrawerContent.HISTORY) {
                    TextButton(
                        onClick = { restoreNotice = onRestoreClosed() },
                        enabled = closedCount > 0,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onToolBar,
                            disabledContentColor = colors.onToolBar.copy(alpha = 0.38f)),
                        modifier = Modifier.testTag("ftutter-drawer-restore")
                    ) {
                        Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("タブを戻す", fontSize = 12.sp, maxLines = 1)
                            Text("残り${closedCount}件", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        restoreNotice?.let { message ->
            Text(message, color = colors.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().background(colors.popUp).padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("ftutter-drawer-restore-notice"))
        }
    }
}

@Composable
private fun FtutterDrawerCheck(label: String, checked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalFtutterColors.current
    Row(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = null, tint = if (checked) colors.onToolBarActive else colors.onToolBar
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = colors.onToolBar, fontSize = 16.sp)
    }
}

/** 行スワイプ：既定は右へ動かすと閉じ、左へ動かすと保護の切り替え（行は残る）。 */
@Composable
private fun FtutterSwipeRow(
    row: FtutterTabView,
    direction: FtutterDismissDirection,
    onClose: (String) -> Unit,
    onToggleProtect: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val colors = LocalFtutterColors.current
    val closeValue = if (direction == FtutterDismissDirection.CLOSE_TO_RIGHT) SwipeToDismissBoxValue.StartToEnd else SwipeToDismissBoxValue.EndToStart
    // 判定は1回の操作で1回だけ（confirmValueChange は1回のスワイプで何度も呼ばれ、保護が付いてすぐ外れた）。
    val state = rememberSwipeToDismissBoxState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        onDismiss = { value ->
            if (value == closeValue) onClose(row.url) else if (value != SwipeToDismissBoxValue.Settled) onToggleProtect(row.url)
            // 閉じた行も保護の行も、行はリストの側が消す（閉じた時）か残す（保護）ので、スワイプの位置は戻す。
            scope.launch { state.snapTo(SwipeToDismissBoxValue.Settled) }
        },
        backgroundContent = {
            val target = state.dismissDirection
            val closing = target == closeValue
            Box(
                Modifier.fillMaxSize().background(if (closing) colors.deleted else colors.pickUp).padding(horizontal = 16.dp),
                contentAlignment = if (target == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Text(
                    if (closing) "閉じる" else if (row.protected) "保護を解除" else "保護",
                    color = Color.White, fontSize = 16.sp
                )
            }
        },
        modifier = Modifier.testTag("ftutter-drawer-swipe")
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FtutterDrawerRow(
    row: FtutterTabView,
    design: FtutterDesignSettings,
    onOpen: (String) -> Unit,
    onLongPress: (String) -> Unit,
    /** 選択中だけ値がある（null は通常の行）。 */
    checked: Boolean? = null,
    privacy: Boolean = false,
    showStatus: Boolean = false
) {
    val colors = LocalFtutterColors.current
    val entry = row.entry
    val status = if (showStatus) com.valoser.futacha.shared.ui.board.threadStatusText(ftutterDisplayStatus(entry))
        else if (entry?.isAutoRefreshDisabled == true) "落ち" else ""
    Column(
        Modifier.fillMaxWidth().background(colors.background)
            .combinedClickable(onClick = { onOpen(row.url) }, onLongClick = { onLongPress(row.url) })
            .testTag(if (checked == true) "ftutter-drawer-row-checked" else "ftutter-drawer-row")
    ) {
        Row(Modifier.fillMaxWidth().height(66.dp).padding(4.dp)) {
            if (checked != null) {
                Icon(
                    if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                    contentDescription = if (checked) "選択中" else "未選択",
                    tint = colors.text,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp)
                )
            }
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(2.dp))) {
                entry?.titleImageUrl?.takeIf { it.isNotBlank() }?.let { thumb ->
                    FtutterThumbnail(
                        thumbnailUrl = thumb, fullImageUrl = null, targetSizePx = 160, contentDescription = "",
                        modifier = Modifier.fillMaxSize().then(
                            Modifier.alpha(if (privacy) (1.0 - design.privacyTabDrawer).toFloat() else 1f)
                        )
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    entry?.title?.takeIf { it.isNotBlank() } ?: row.url,
                    color = colors.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.protected) {
                        Icon(Icons.Rounded.Lock, contentDescription = "保護", tint = colors.headerText, modifier = Modifier.size(14.dp).testTag("ftutter-drawer-protected"))
                    }
                    if (status.isNotEmpty()) {
                        Text(status, color = colors.text, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp)
                            .testTag("ftutter-drawer-status"))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(entry?.boardName.orEmpty(), color = colors.text, fontSize = 13.sp, maxLines = 1)
                }
            }
            Column(
                Modifier.width(44.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("${row.numbers.replies}", color = colors.text, fontSize = design.catalogNumberFontSize.sp)
                row.numbers.unread?.let { unread ->
                    Text(
                        "$unread", color = colors.onMidoku, fontSize = design.catalogNumberFontSize.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.background(colors.midoku, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)
                            .testTag("ftutter-drawer-unread-count")
                    )
                }
            }
        }
        HorizontalDivider(color = colors.outline)
    }
}

/** タブの長押しメニュー（原作 TabDialog）。ドロワーからは「これより下」、タブ帯からは「これより右」。 */
@Composable
internal fun FtutterTabDialog(
    protected: Boolean,
    fromDrawer: Boolean,
    onDismiss: () -> Unit,
    onAction: (FtutterTabAction) -> Unit
) {
    val colors = LocalFtutterColors.current
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        modifier = Modifier.testTag("ftutter-tab-dialog"),
        text = {
            Column {
                @Composable
                fun item(action: FtutterTabAction, icon: ImageVector, label: String) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onAction(action) }.padding(vertical = 14.dp)
                            .testTag("ftutter-tab-${action.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = colors.text)
                        Spacer(Modifier.width(24.dp))
                        Text(label, color = colors.text, fontSize = 17.sp)
                    }
                }
                item(FtutterTabAction.PROTECT, if (protected) Icons.Rounded.LockOpen else Icons.Rounded.Lock, if (protected) "タブの保護を解除" else "タブを保護")
                item(FtutterTabAction.REFRESH_ALL, Icons.Rounded.Refresh, "タブを全て更新")
                item(FtutterTabAction.CLOSE_FALLEN, Icons.Rounded.DeleteForever, "落ちたタブを全て閉じる")
                item(FtutterTabAction.CLOSE_AFTER, Icons.Rounded.DeleteForever, if (fromDrawer) "これより下をすべて閉じる" else "これより右を全て閉じる")
                item(FtutterTabAction.CLOSE_ALL, Icons.Rounded.DeleteForever, "全てのタブを閉じる")
            }
        },
        confirmButton = {}
    ) }
}

/** 保護したタブを1つ閉じる時の確認（原作「保護されたタブを閉じますか？」）。 */
@Composable
internal fun FtutterProtectedCloseDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = {},
        title = { Text("保護されたタブを閉じますか？") },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("ftutter-protected-ok")) { Text("OK") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("キャンセル") } }
    ) }
}

/** タブ帯（既定は非表示・下・高さ40・幅60）。開いているタブは枠で示し、未読があれば数を重ねる。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FtutterTabStrip(
    tabs: List<FtutterTabView>,
    opening: String?,
    settings: FtutterTabSettings,
    design: FtutterDesignSettings,
    onOpen: (String) -> Unit,
    onLongPress: (String) -> Unit,
    /** 長押しのまま横へ動かすと並べ替え（原作 DraggableTabbar。`disableDragTab`がONなら並べ替えない）。 */
    onMove: ((from: Int, to: Int) -> Unit)? = null,
    /** タブ帯の位置。スレ画面はスレッドごとに作り直すので、アプリ側で持って渡す（再監査M3：切り替えると先頭に戻った）。 */
    listState: LazyListState = rememberLazyListState(),
    showStatus: Boolean = false
) {
    val colors = LocalFtutterColors.current
    if (tabs.isEmpty()) return
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    // ジェスチャーの処理は、タブのURLだけを鍵にして続ける。並べ替えの口と並びは最新を読む（監査M8：毎回作り直される口と位置を
    // 鍵にしていたので、ドラッグ中に履歴の更新などで組み直されると処理が取り消され、ずらした表示が残っていた）。
    val currentTabs by rememberUpdatedState(tabs)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    LazyRow(
        Modifier.fillMaxWidth().height(settings.tabHeight.dp).background(colors.toolBar).testTag("ftutter-tab-strip"),
        state = listState
    ) {
        items(tabs, key = { it.url }) { tab ->
            val selected = tab.url == opening
            val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { settings.tabWidth.dp.toPx() }
            Box(
                Modifier.width(settings.tabWidth.dp).fillMaxHeight().padding(1.dp)
                    .then(if (dragging == tab.url) Modifier.offset { androidx.compose.ui.unit.IntOffset(dragOffset.toInt(), 0) } else Modifier)
                    .then(if (selected) Modifier.border(2.dp, colors.onToolBarActive) else Modifier)
                    .clickable { onOpen(tab.url) }
                    .pointerInput(tab.url) {
                        // 長押しで始まる。動かさずに離せばメニュー、横へ動かせば並べ替え。
                        try {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = tab.url; dragOffset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (currentOnMove != null) dragOffset += amount.x
                                },
                                onDragEnd = {
                                    val move = ftutterTabDragMove(currentTabs.map { it.url }, tab.url, dragOffset, widthPx)
                                    dragging = null
                                    dragOffset = 0f
                                    val onMoveNow = currentOnMove
                                    if (onMoveNow != null && move != null) onMoveNow(move.first, move.second) else currentOnLongPress(tab.url)
                                },
                                onDragCancel = { dragging = null; dragOffset = 0f }
                            )
                        } finally {
                            // 処理が取り消された時も、ずらした表示を残さない。
                            if (dragging == tab.url) {
                                dragging = null
                                dragOffset = 0f
                            }
                        }
                    }
                    .semantics {
                        contentDescription = tab.entry?.title.orEmpty() +
                            if (showStatus) "、" + com.valoser.futacha.shared.ui.board.threadStatusText(ftutterDisplayStatus(tab.entry)) else ""
                        onLongClick(label = "タブの操作") { onLongPress(tab.url); true }
                    }
                    .testTag(if (selected) "ftutter-tab-selected" else "ftutter-tab")
            ) {
                val thumb = tab.entry?.titleImageUrl.orEmpty()
                val titleBesideUnread = thumb.isBlank() && showStatus &&
                    com.valoser.futacha.shared.ui.board.threadStatusBadgeText(ftutterDisplayStatus(tab.entry)).isNotEmpty()
                if (thumb.isNotBlank()) {
                    FtutterThumbnail(thumbnailUrl = thumb, fullImageUrl = null, targetSizePx = 120, contentDescription = tab.entry?.title.orEmpty(),
                        modifier = Modifier.fillMaxSize().alpha(if (settings.privacy) (1.0 - design.privacyTabDrawer).toFloat() else 1f))
                } else if (titleBesideUnread) {
                    Row(Modifier.fillMaxWidth().align(Alignment.BottomStart), verticalAlignment = Alignment.Bottom) {
                        Text(tab.entry?.title.orEmpty(), color = colors.onToolBar, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(2.dp))
                        tab.numbers.unread?.let { unread ->
                            Text("$unread", color = colors.onMidoku, fontSize = (design.catalogNumberFontSize - 2).coerceAtLeast(8).sp,
                                lineHeight = design.catalogNumberFontSize.coerceAtLeast(10).sp,
                                modifier = Modifier.background(colors.midoku).padding(horizontal = 2.dp))
                        }
                    }
                } else {
                    Text(tab.entry?.title.orEmpty(), color = colors.onToolBar, fontSize = 10.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(2.dp))
                }
                tab.numbers.unread?.takeUnless { titleBesideUnread }?.let { unread ->
                    Text(
                        "$unread", color = colors.onMidoku, fontSize = (design.catalogNumberFontSize - 2).coerceAtLeast(8).sp,
                        modifier = Modifier.align(Alignment.BottomEnd).background(colors.midoku).padding(horizontal = 2.dp)
                    )
                }
                if (tab.protected) {
                    Icon(Icons.Rounded.Lock, contentDescription = "保護", tint = colors.onToolBar,
                        modifier = Modifier.align(Alignment.TopEnd).size(12.dp).background(colors.toolBar))
                }
                if (showStatus) {
                    val status = com.valoser.futacha.shared.ui.board.threadStatusBadgeText(ftutterDisplayStatus(tab.entry))
                    if (status.isNotEmpty()) Text(status, color = colors.onToolBar, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.TopStart).background(colors.toolBar)
                            .testTag("ftutter-tab-status"))
                }
            }
        }
    }
}
