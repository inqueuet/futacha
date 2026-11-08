package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.distinctBoardsById
import kotlinx.coroutines.launch

/**
 * The board list shown behind the pushed-aside screen (V14). The round 「編集」 at the lower left switches it to futaber's
 * edit (S04): a red filled − deletes (after asking), the handle ≡ at the right drags a board up or down, and the round
 * 「完了」 and the capsule 「板の追加」 sit at the bottom; a row's tap does nothing while editing. This app's own additions
 * (the bulk add and the rename) are small buttons after futaber's two ([futaberBoardDrawerEditExtras]).
 */
@Composable
internal fun FutaberBoardDrawer(
    boards: List<BoardSummary>,
    selectedBoardId: String?,
    modifier: Modifier,
    onSelect: (BoardSummary) -> Unit,
    onAddBoard: (name: String, url: String) -> Unit,
    onDeleteBoard: (BoardSummary) -> Unit,
    onMoveBoard: (BoardSummary, up: Boolean) -> Unit,
    /** Changes the name of a board (「名前を変更」 in the edit footer). */
    onRenameBoard: (BoardSummary, name: String) -> Unit = { _, _ -> },
    /** Adds the boards of the official list that are not registered yet and returns how many were added; null = unavailable. */
    onBulkAdd: (suspend () -> Result<Int>)? = null,
    boardsFromBottom: Boolean = false,
    /** F-B4 E1: pins or unpins a board (the AppState's own pin, as the other modes); null = the pin is not offered. */
    onTogglePin: ((BoardSummary) -> Unit)? = null,
    /** Where the alerts and the sheet of the edit are drawn (over the whole screen); null draws them over the list. */
    overlay: FutaberScreenOverlay? = null,
    /**
     * Stores the order a drag left (the ids of the boards as shown) in one write; null moves the board one step at a
     * time as before (one write per row passed; audit B4).
     */
    onReorderBoards: ((List<String>) -> Unit)? = null
) {
    val colors = LocalFutaberColors.current
    var editing by rememberSaveable { mutableStateOf(false) }
    val ownOverlay = remember { FutaberScreenOverlay() }
    val alerts = overlay ?: ownOverlay
    // With no board there is nothing to open, so the list starts in the mode that can add one.
    LaunchedEffect(boards.isEmpty()) { if (boards.isEmpty()) editing = true }
    // Rows are keyed by id: a board stored twice is shown once instead of crashing the list.
    val shownBoards = remember(boards) { distinctBoardsById(boards) }
    val askAdd: () -> Unit = { alerts.show { FutaberAddBoardAlert(boards, onAddBoard, alerts::dismiss) } }
    val askDelete: (BoardSummary) -> Unit = { target -> alerts.show { FutaberDeleteBoardAlert(target, onDeleteBoard, alerts::dismiss) } }
    val askRename: () -> Unit = {
        alerts.show { FutaberRenameBoardSheet(shownBoards, alerts, onRenameBoard) }
    }
    val askBulk: () -> Unit = { onBulkAdd?.let { bulk -> alerts.show { FutaberBulkAddAlert(bulk, alerts::dismiss) } } }

    Box(modifier.background(colors.drawerBackground).testTag("futaber-drawer")) {
        FutaberDrawerBackdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // V31: futaber's heading sits about 100pt from the top, in a light weight; it stays 「掲示板」 while editing.
            Text(
                "掲示板",
                color = colors.drawerText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 20.dp, top = FUTABER_DRAWER_HEADING_TOP_DP.dp, bottom = 24.dp)
                    .testTag("futaber-drawer-heading")
            )
            FutaberDrawerBoardList(
                shownBoards, selectedBoardId, editing, boardsFromBottom, onSelect, onMoveBoard, askDelete, onTogglePin,
                Modifier.weight(1f).fillMaxWidth(), onReorderBoards
            )
            FutaberDrawerFooter(
                editing = editing,
                onToggle = { editing = !editing },
                onAdd = askAdd,
                extras = futaberBoardDrawerEditExtras(onBulkAdd != null, shownBoards.isNotEmpty()),
                onExtra = { extra ->
                    when (extra) {
                        FutaberDrawerEditExtra.BulkAdd -> askBulk()
                        FutaberDrawerEditExtra.Rename -> askRename()
                    }
                }
            )
        }
    }
    if (overlay == null) FutaberScreenOverlayHost(ownOverlay)
}

/** Where futaber's 「掲示板」 starts under the status area (about 100pt from the top on an iPhone SE, V31). */
internal const val FUTABER_DRAWER_HEADING_TOP_DP = 68

/** Own geometric backdrop (no borrowed artwork): a few translucent facets. */
@Composable
private fun FutaberDrawerBackdrop() {
    val colors = LocalFutaberColors.current
    // C17: the menu's background is the polygons (デフォルト), the old blue gradient (デフォルト(旧)) or the picked picture.
    val kind = LocalFutaberDisplaySettings.current.wallpapers.menuBackground
    if (kind == FutaberMenuBackground.Old) {
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF1E5FBF), androidx.compose.ui.graphics.Color(0xFF0B2A5B)))
            ).testTag("futaber-drawer-gradient")
        )
        return
    }
    if (kind == FutaberMenuBackground.Image && LocalFutaberDisplaySettings.current.wallpapers.file(FutaberWallpaperSlot.Menu) != null) {
        FutaberWallpaperImage(FutaberWallpaperSlot.Menu)
        return
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun facet(points: List<Offset>) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                close()
            }
            drawPath(path, colors.drawerShape)
        }
        facet(listOf(Offset(0f, h * 0.15f), Offset(w * 0.6f, 0f), Offset(w * 0.35f, h * 0.32f)))
        facet(listOf(Offset(w, h * 0.28f), Offset(w * 0.55f, h * 0.55f), Offset(w, h * 0.7f)))
        facet(listOf(Offset(0f, h * 0.62f), Offset(w * 0.45f, h * 0.82f), Offset(0f, h)))
    }
}

/**
 * The boards. While editing, each row has the red − and the handle; dragging the handle moves the board, row by row
 * ([futaberDragTargetIndex]), and the move is stored when the finger is lifted. The rows also offer 「上へ移動」／「下へ移動」
 * to the screen reader (the arrows of before).
 */
@Composable
private fun FutaberDrawerBoardList(
    shownBoards: List<BoardSummary>,
    selectedBoardId: String?,
    editing: Boolean,
    boardsFromBottom: Boolean,
    onSelect: (BoardSummary) -> Unit,
    onMoveBoard: (BoardSummary, up: Boolean) -> Unit,
    onAskDelete: (BoardSummary) -> Unit,
    onTogglePin: ((BoardSummary) -> Unit)?,
    modifier: Modifier,
    onReorderBoards: ((List<String>) -> Unit)? = null
) {
    // The order while a board is dragged (the stored list changes when the finger is lifted).
    var order by remember(shownBoards) { mutableStateOf(shownBoards) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableIntStateOf(0) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableFloatStateOf(0f) }
    fun finishDrag() {
        val id = dragId ?: return
        val board = shownBoards.firstOrNull { it.id == id }
        val to = order.indexOfFirst { it.id == id }
        val from = dragFrom
        dragId = null
        dragDy = 0f
        if (board == null || to < 0 || to == from) return
        if (onReorderBoards != null) onReorderBoards(order.map(BoardSummary::id))
        else repeat(kotlin.math.abs(to - from)) { onMoveBoard(board, to < from) }
    }
    // The edit row below keeps the bottom inset; the list only avoids a side navigation bar (L28: both took the
    // bottom inset, leaving a gap the height of the navigation bar under the boards).
    LazyColumn(
        modifier
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars.only(
                androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
            .testTag("futaber-board-list"),
        verticalArrangement = if (boardsFromBottom) androidx.compose.foundation.layout.Arrangement.Bottom else androidx.compose.foundation.layout.Arrangement.Top
    ) {
        itemsIndexed(order, key = { _, item -> item.id }) { index, item ->
            val dragging = dragId == item.id
            FutaberDrawerBoardRow(
                item = item,
                selected = item.id == selectedBoardId,
                editing = editing,
                canMoveUp = index > 0,
                canMoveDown = index < order.lastIndex,
                onSelect = { onSelect(item) },
                onAskDelete = { onAskDelete(item) },
                onTogglePin = onTogglePin?.let { toggle -> { toggle(item) } },
                onMove = { up -> onMoveBoard(item, up) },
                dragOffset = if (dragging) dragDy else 0f,
                onRowHeight = { rowPx = it },
                onDragStart = { dragId = item.id; dragFrom = shownBoards.indexOfFirst { it.id == item.id }; dragDy = 0f },
                onDrag = { delta ->
                    if (dragId == item.id) {
                        dragDy += delta
                        val now = order.indexOfFirst { it.id == item.id }
                        val target = futaberDragTargetIndex(now, dragDy, rowPx, order.size)
                        if (target != now && now >= 0) {
                            order = order.toMutableList().apply { add(target, removeAt(now)) }
                            dragDy -= (target - now) * rowPx
                        }
                    }
                },
                onDragEnd = ::finishDrag
            )
        }
    }
}

@Composable
private fun FutaberDrawerBoardRow(
    item: BoardSummary,
    selected: Boolean,
    editing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onAskDelete: () -> Unit,
    onTogglePin: (() -> Unit)?,
    onMove: (up: Boolean) -> Unit,
    dragOffset: Float,
    onRowHeight: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val colors = LocalFutaberColors.current
    Row(
        Modifier.fillMaxWidth()
            .zIndex(if (dragOffset != 0f) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .onSizeChanged { onRowHeight(it.height.toFloat()) }
            .heightIn(min = 56.dp)
            // S04: a row's tap does nothing while editing (futaber's); otherwise it opens the board.
            .clickable(enabled = !editing, onClickLabel = "${item.name}を開く", onClick = onSelect)
            .semantics {
                if (editing) customActions = listOfNotNull(
                    if (canMoveUp) CustomAccessibilityAction("${item.name}を上へ移動") { onMove(true); true } else null,
                    if (canMoveDown) CustomAccessibilityAction("${item.name}を下へ移動") { onMove(false); true } else null
                )
            }
            .padding(start = if (editing) 8.dp else 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            .testTag("futaber-drawer-board"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editing) {
            Box(
                Modifier.size(44.dp).clickable(onClickLabel = "${item.name}を削除", role = Role.Button, onClick = onAskDelete)
                    .semantics { contentDescription = "${item.name}を削除" }
                    .testTag("futaber-board-delete"),
                contentAlignment = Alignment.Center
            ) {
                // futaber's red filled − (D/board-edit.png).
                Box(Modifier.size(22.dp).clip(CircleShape).background(colors.destructive), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 11.dp, height = 2.dp).background(colors.drawerText))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = if (editing) 4.dp else 0.dp)) {
            Text(
                // F-B4 E1: a pinned board carries a small mark after its name (the order does not change).
                if (onTogglePin != null && item.pinned) item.name + " 📌" else item.name,
                color = colors.drawerText,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(futaberBoardAddress(item.url), color = colors.drawerMeta, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (editing && onTogglePin != null) {
            Box(
                Modifier.size(44.dp)
                    .clickable(onClickLabel = if (item.pinned) "${item.name}のピン留めを外す" else "${item.name}をピン留め", role = Role.Button, onClick = onTogglePin)
                    .semantics { contentDescription = if (item.pinned) "${item.name}のピン留めを外す" else "${item.name}をピン留め" }
                    .testTag("futaber-board-pin"),
                contentAlignment = Alignment.Center
            ) {
                FutaberIcon(Icons.Outlined.PushPin, contentDescription = null, tint = if (item.pinned) colors.link else colors.drawerMeta)
            }
        }
        if (editing) {
            // The handle ≡: a drag moves the board (it starts at once, as futaber's reorder control does).
            // The gesture outlives a recomposition: it calls the newest callbacks, which see the order after the last
            // drag was stored (it kept the first ones, so a second drag moved an old list and nothing on screen; audit B3).
            val latestStart by androidx.compose.runtime.rememberUpdatedState(onDragStart)
            val latestDrag by androidx.compose.runtime.rememberUpdatedState(onDrag)
            val latestEnd by androidx.compose.runtime.rememberUpdatedState(onDragEnd)
            Box(
                Modifier.size(44.dp)
                    .pointerInput(item.id) {
                        detectVerticalDragGestures(
                            onDragStart = { latestStart() },
                            onDragEnd = { latestEnd() },
                            onDragCancel = { latestEnd() },
                            onVerticalDrag = { change, delta -> change.consume(); latestDrag(delta) }
                        )
                    }
                    .semantics(mergeDescendants = true) {}
                    .testTag("futaber-board-handle"),
                contentAlignment = Alignment.Center
            ) {
                FutaberIcon(Icons.Outlined.DragHandle, contentDescription = "${item.name}を並べ替え", tint = colors.drawerMeta)
            }
        }
    }
}

/**
 * The bottom of the list: futaber's round 「編集」, or while editing the round 「完了」 and the capsule 「板の追加」; this
 * app's additions are small buttons after them.
 */
@Composable
private fun FutaberDrawerFooter(
    editing: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    extras: List<FutaberDrawerEditExtra>,
    onExtra: (FutaberDrawerEditExtra) -> Unit
) {
    val colors = LocalFutaberColors.current
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(colors.drawerShape)
                    .clickable(onClickLabel = if (editing) "板一覧の編集を終える" else "板一覧を編集", onClick = onToggle)
                    .testTag("futaber-board-edit"),
                contentAlignment = Alignment.Center
            ) {
                Text(if (editing) "完了" else "編集", color = colors.drawerText, fontSize = 13.sp)
            }
            if (editing) {
                Box(
                    Modifier.padding(start = 20.dp).height(50.dp).widthIn(min = 150.dp).clip(FutaberShapes.capsule)
                        .background(colors.drawerShape)
                        .clickable(onClickLabel = "板の追加", onClick = onAdd)
                        .padding(horizontal = 24.dp)
                        .testTag("futaber-board-add"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("板の追加", color = colors.drawerText, fontSize = 13.sp)
                }
            }
        }
        if (editing && extras.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                extras.forEach { extra ->
                    Box(
                        Modifier.heightIn(min = 40.dp).clickable(onClickLabel = extra.label, onClick = { onExtra(extra) })
                            .padding(end = 16.dp, top = 8.dp, bottom = 8.dp).testTag(extra.tag),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(extra.label, color = colors.drawerMeta, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** S05: futaber's 「板の追加」 alert, one field holding 「https://」; OK with no address says why and adds nothing. */
@Composable
private fun FutaberAddBoardAlert(existing: List<BoardSummary>, onAddBoard: (name: String, url: String) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(FUTABER_ADD_BOARD_PREFILL) }
    var reason by remember { mutableStateOf<String?>(null) }
    FutaberAlert(
        title = "板の追加",
        message = reason,
        field = FutaberAlertField(url, { url = futaberSafeTake(it, 300); reason = null }, tag = "futaber-add-board-url", keyboardType = KeyboardType.Uri),
        buttons = listOf(
            FutaberAlertButton("add-board-cancel", "キャンセル", FutaberAlertStyle.Cancel, tag = "futaber-add-board-cancel") { onDismiss() },
            FutaberAlertButton("add-board-ok", "OK", tag = "futaber-add-board-submit") {
                val why = futaberAddBoardAlertReason(url, existing)
                if (why != null) reason = why else {
                    onDismiss()
                    // The name is made from the address, as an add without a name always did (no request is sent to name it).
                    onAddBoard("", url)
                }
            }
        ),
        onDismiss = onDismiss
    )
}

@Composable
private fun FutaberDeleteBoardAlert(target: BoardSummary, onDeleteBoard: (BoardSummary) -> Unit, onDismiss: () -> Unit) {
    FutaberAlert(
        title = "板を削除しますか？",
        message = futaberDeleteBoardMessage(target.name),
        buttons = listOf(
            FutaberAlertButton("board-delete-cancel", "キャンセル", FutaberAlertStyle.Cancel) { onDismiss() },
            FutaberAlertButton("board-delete-confirm", "削除", FutaberAlertStyle.Destructive, tag = "futaber-board-delete-confirm") {
                onDismiss()
                onDeleteBoard(target)
            }
        ),
        onDismiss = onDismiss
    )
}

/** 「板一覧から一括追加」 (this app's): asks, fetches the official list and adds the boards not registered yet. */
@Composable
private fun FutaberBulkAddAlert(onBulkAdd: suspend () -> Result<Int>, onDismiss: () -> Unit) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    FutaberAlert(
        title = "板を一括追加",
        message = error ?: "公式の板一覧にある、まだ登録していない板をまとめて追加します。",
        buttons = listOf(
            FutaberAlertButton("bulk-cancel", "キャンセル", FutaberAlertStyle.Cancel, enabled = !loading) { onDismiss() },
            FutaberAlertButton("bulk-submit", if (loading) "取得中…" else "一括追加", enabled = !loading, tag = "futaber-add-board-bulk-submit") {
                loading = true
                error = null
                scope.launch {
                    val result = onBulkAdd()
                    loading = false
                    result.onSuccess { added ->
                        if (added > 0) onDismiss() else error = "追加できる新しい板はありませんでした"
                    }.onFailure {
                        // The address of the list is not part of what the user sees.
                        error = "板一覧を取得できませんでした"
                    }
                }
            }
        ),
        onDismiss = onDismiss
    )
}

/** 「名前を変更」 (this app's): which board, then its name in an alert. */
@Composable
private fun FutaberRenameBoardSheet(boards: List<BoardSummary>, overlay: FutaberScreenOverlay, onRenameBoard: (BoardSummary, String) -> Unit) {
    FutaberActionSheet(
        title = "名前を変更する板",
        actions = boards.map { board ->
            FutaberPostAction("board-rename-${board.id}", board.name) {
                overlay.show { FutaberRenameBoardAlert(board, overlay::dismiss) { name -> onRenameBoard(board, name) } }
            }
        },
        onDismiss = overlay::dismiss
    )
}

@Composable
private fun FutaberRenameBoardAlert(board: BoardSummary, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var name by rememberSaveable(board.id) { mutableStateOf(board.name) }
    val canSubmit = name.isNotBlank() && name.trim() != board.name
    FutaberAlert(
        title = "板の名前を変更",
        message = futaberBoardAddress(board.url),
        field = FutaberAlertField(name, { name = futaberSafeTake(it, FUTABER_BOARD_NAME_MAX_CHARS) }, placeholder = "名前", tag = "futaber-rename-board-name"),
        buttons = listOf(
            FutaberAlertButton("rename-cancel", "キャンセル", FutaberAlertStyle.Cancel) { onDismiss() },
            FutaberAlertButton("rename-submit", "変更", enabled = canSubmit, tag = "futaber-rename-board-submit") {
                onDismiss()
                onSubmit(name)
            }
        ),
        onDismiss = onDismiss
    )
}

/** The confirmation text of deleting a board: what goes with it (its favourites and tabs) and what stays. */
internal fun futaberDeleteBoardMessage(boardName: String): String =
    "「$boardName」を板一覧から外します。この板のお気に入りとタブも外れます。履歴や保存済みのスレッドは削除されません。"

