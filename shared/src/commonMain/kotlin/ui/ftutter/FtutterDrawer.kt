package com.valoser.futacha.shared.ui.ftutter

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage

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
 * 上：分類（履歴／タブ／タブ（更新順））と、閉じたタブを1件ずつ戻すボタン。
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
    val rightSide = settings.drawerSide == FtutterDrawerSide.RIGHT
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
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalLayoutDirection provides outer) { content() }
                if (settings.drawerOpenByDrag && !drawerState.isOpen) {
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    // 端の帯（20dp）。内側へ24dp引いたら開く。右に出す時は、右から左の向きなので右端になる。
                    Box(
                        Modifier.align(Alignment.CenterStart).fillMaxHeight().width(20.dp)
                            .pointerInput(Unit) {
                                var pulled = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { pulled = 0f },
                                    onHorizontalDrag = { change, amount ->
                                        pulled += amount
                                        change.consume()
                                        if (pulled > 24.dp.toPx()) scope.launch { drawerState.open() }
                                    }
                                )
                            }
                            .testTag("ftutter-drawer-edge")
                    )
                }
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
    onRestoreClosed: () -> Unit,
    onOpen: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onClose: (String) -> Unit,
    onToggleProtect: (String) -> Unit
) {
    val colors = LocalFtutterColors.current
    var searching by remember { mutableStateOf(filter.query.isNotEmpty()) }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().background(colors.toolBar).statusBarsPadding().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            var open by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.widthIn(min = 160.dp).clickable { open = true }.padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("ftutter-drawer-content"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(content.label, color = colors.onToolBar, fontSize = 18.sp, modifier = Modifier.weight(1f, fill = false))
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (closedCount > 0) {
                    IconButton(onClick = onRestoreClosed, modifier = Modifier.testTag("ftutter-drawer-restore")) {
                        Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "閉じたタブを開く", tint = colors.onToolBar)
                    }
                }
            }
        }
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
                val swipeable = row.isTab && content != FtutterDrawerContent.HISTORY && dismissDirection != FtutterDismissDirection.NONE
                if (swipeable) {
                    FtutterSwipeRow(row, dismissDirection, onClose, onToggleProtect) {
                        FtutterDrawerRow(row, design, onOpen, onLongPress)
                    }
                } else {
                    FtutterDrawerRow(row, design, onOpen, onLongPress)
                }
            }
        }
        HorizontalDivider(color = colors.outline)
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
    onLongPress: (String) -> Unit
) {
    val colors = LocalFtutterColors.current
    val entry = row.entry
    Column(
        Modifier.fillMaxWidth().background(colors.background)
            .combinedClickable(onClick = { onOpen(row.url) }, onLongClick = { onLongPress(row.url) })
            .testTag("ftutter-drawer-row")
    ) {
        Row(Modifier.fillMaxWidth().height(66.dp).padding(4.dp)) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(2.dp))) {
                entry?.titleImageUrl?.takeIf { it.isNotBlank() }?.let { thumb ->
                    CatalogPreviewImage(
                        thumbnailUrl = thumb, fullImageUrl = null, targetSizePx = 160, contentDescription = "",
                        modifier = Modifier.fillMaxSize().then(
                            Modifier.background(Color.Transparent)
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
                    if (entry?.isAutoRefreshDisabled == true) {
                        Text("落ち", color = colors.deleted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
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
    onMove: ((from: Int, to: Int) -> Unit)? = null
) {
    val colors = LocalFtutterColors.current
    if (tabs.isEmpty()) return
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    LazyRow(
        Modifier.fillMaxWidth().height(settings.tabHeight.dp).background(colors.toolBar).testTag("ftutter-tab-strip")
    ) {
        items(tabs, key = { it.url }) { tab ->
            val selected = tab.url == opening
            val index = tabs.indexOf(tab)
            val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { settings.tabWidth.dp.toPx() }
            Box(
                Modifier.width(settings.tabWidth.dp).fillMaxHeight().padding(1.dp)
                    .then(if (dragging == tab.url) Modifier.offset { androidx.compose.ui.unit.IntOffset(dragOffset.toInt(), 0) } else Modifier)
                    .then(if (selected) Modifier.border(2.dp, colors.onToolBarActive) else Modifier)
                    .clickable { onOpen(tab.url) }
                    .pointerInput(tab.url, index, onMove) {
                        // 長押しで始まる。動かさずに離せばメニュー、横へ動かせば並べ替え。
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = tab.url; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                if (onMove != null) dragOffset += amount.x
                            },
                            onDragEnd = {
                                val moved = (dragOffset / widthPx).let { kotlin.math.round(it).toInt() }
                                val wasDragged = kotlin.math.abs(dragOffset) > widthPx / 3
                                dragging = null
                                dragOffset = 0f
                                if (onMove != null && wasDragged && moved != 0) {
                                    onMove(index, (index + moved).coerceIn(0, tabs.lastIndex))
                                } else {
                                    onLongPress(tab.url)
                                }
                            },
                            onDragCancel = { dragging = null; dragOffset = 0f }
                        )
                    }
                    .semantics { onLongClick(label = "タブの操作") { onLongPress(tab.url); true } }
                    .testTag(if (selected) "ftutter-tab-selected" else "ftutter-tab")
            ) {
                val thumb = tab.entry?.titleImageUrl.orEmpty()
                if (thumb.isNotBlank()) {
                    CatalogPreviewImage(thumbnailUrl = thumb, fullImageUrl = null, targetSizePx = 120, contentDescription = tab.entry?.title.orEmpty(),
                        modifier = Modifier.fillMaxSize())
                } else {
                    Text(tab.entry?.title.orEmpty(), color = colors.onToolBar, fontSize = 10.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(2.dp))
                }
                tab.numbers.unread?.let { unread ->
                    Text(
                        "$unread", color = colors.onMidoku, fontSize = (design.catalogNumberFontSize - 2).coerceAtLeast(8).sp,
                        modifier = Modifier.align(Alignment.BottomEnd).background(colors.midoku).padding(horizontal = 2.dp)
                    )
                }
                if (tab.protected) {
                    Icon(Icons.Rounded.Lock, contentDescription = "保護", tint = colors.onToolBar,
                        modifier = Modifier.align(Alignment.TopEnd).size(12.dp).background(colors.toolBar))
                }
            }
        }
    }
}
