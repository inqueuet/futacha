package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.launch
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

/**
 * The board list shown behind the pushed-aside screen. The round button at the lower left
 * switches it to edit mode: delete, move up/down and add a board by its address.
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
    /** Changes the name of a board (tap its name while editing). */
    onRenameBoard: (BoardSummary, name: String) -> Unit = { _, _ -> },
    /** Adds the boards of the official list that are not registered yet and returns how many were added; null = unavailable. */
    onBulkAdd: (suspend () -> Result<Int>)? = null
) {
    val colors = LocalFutaberColors.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<BoardSummary?>(null) }
    var renameTarget by remember { mutableStateOf<BoardSummary?>(null) }
    // With no board there is nothing to open, so the list starts in the mode that can add one.
    LaunchedEffect(boards.isEmpty()) { if (boards.isEmpty()) editing = true }

    Box(modifier.background(colors.drawerBackground).testTag("futaber-drawer")) {
        // Own geometric backdrop (no borrowed artwork): a few translucent facets.
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
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                if (editing) "掲示板を編集（名前をタップで変更）" else "掲示板",
                color = colors.drawerText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(boards, key = { _, item -> item.id }) { index, item ->
                    val selected = item.id == selectedBoardId
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(
                                onClickLabel = if (editing) "${item.name}の名前を変更" else "${item.name}を開く"
                            ) { if (editing) renameTarget = item else onSelect(item) }
                            .padding(horizontal = if (editing) 4.dp else 16.dp, vertical = 4.dp)
                            .testTag("futaber-drawer-board"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (editing) {
                            IconButton(
                                onClick = { deleteTarget = item },
                                modifier = Modifier.testTag("futaber-board-delete")
                            ) {
                                FutaberIcon(
                                    Icons.Outlined.RemoveCircleOutline,
                                    contentDescription = "${item.name}を削除",
                                    tint = colors.drawerAccent
                                )
                            }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = if (editing) 4.dp else 0.dp)) {
                            Text(
                                item.name,
                                color = colors.drawerText,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(futaberBoardAddress(item.url), color = colors.drawerMeta, fontSize = 12.sp)
                        }
                        if (editing) {
                            IconButton(
                                onClick = { onMoveBoard(item, true) },
                                enabled = index > 0,
                                modifier = Modifier.testTag("futaber-board-up")
                            ) {
                                FutaberIcon(
                                    Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = "${item.name}を上へ移動",
                                    tint = if (index > 0) colors.drawerText else colors.drawerShape
                                )
                            }
                            IconButton(
                                onClick = { onMoveBoard(item, false) },
                                enabled = index < boards.lastIndex,
                                modifier = Modifier.testTag("futaber-board-down")
                            ) {
                                FutaberIcon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "${item.name}を下へ移動",
                                    tint = if (index < boards.lastIndex) colors.drawerText else colors.drawerShape
                                )
                            }
                        }
                    }
                }
                if (editing) {
                    item(key = "add-board") {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clickable(onClickLabel = "板を追加") { addDialog = true }
                                .padding(horizontal = 16.dp)
                                .testTag("futaber-board-add"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FutaberIcon(Icons.Outlined.Add, contentDescription = null, tint = colors.drawerAccent)
                            Text(
                                "板を追加",
                                color = colors.drawerAccent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The original app's round "編集" button: a soft filled circle holding the word.
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(colors.drawerShape)
                        .clickable(
                            onClickLabel = if (editing) "板一覧の編集を終える" else "板一覧を編集"
                        ) { editing = !editing }
                        .testTag("futaber-board-edit"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (editing) "完了" else "編集", color = colors.drawerText, fontSize = 14.sp)
                }
            }
        }
    }

    if (addDialog) {
        FutaberAddBoardDialog(
            existingBoards = boards,
            onDismiss = { addDialog = false },
            onSubmit = { name, url ->
                addDialog = false
                onAddBoard(name, url)
            },
            onBulkAdd = onBulkAdd
        )
    }
    renameTarget?.let { target ->
        FutaberRenameBoardDialog(
            board = target,
            onDismiss = { renameTarget = null },
            onSubmit = { name -> renameTarget = null; onRenameBoard(target, name) }
        )
    }
    deleteTarget?.let { target ->
        val colorsNow = LocalFutaberColors.current
        FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                containerColor = colorsNow.bar,
                titleContentColor = colorsNow.body,
                textContentColor = colorsNow.body,
                title = { Text("板を削除しますか？") },
                text = { Text(futaberDeleteBoardMessage(target.name)) },
                confirmButton = {
                    TextButton(
                        onClick = { deleteTarget = null; onDeleteBoard(target) },
                        modifier = Modifier.testTag("futaber-board-delete-confirm")
                    ) { Text("削除", color = colorsNow.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("キャンセル", color = colorsNow.link) }
                }
            )
        }
    }
}

/** The confirmation text of deleting a board: what goes with it (its favourites and tabs) and what stays. */
internal fun futaberDeleteBoardMessage(boardName: String): String =
    "「$boardName」を板一覧から外します。この板のお気に入りとタブも外れます。履歴や保存済みのスレッドは削除されません。"

@Composable
private fun FutaberAddBoardDialog(
    existingBoards: List<BoardSummary>,
    onDismiss: () -> Unit,
    onSubmit: (name: String, url: String) -> Unit,
    onBulkAdd: (suspend () -> Result<Int>)?
) {
    val colors = LocalFutaberColors.current
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var bulk by rememberSaveable { mutableStateOf(false) }
    var bulkLoading by remember { mutableStateOf(false) }
    var bulkError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state = futaberAddBoardState(name, url, existingBoards)
    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            title = { Text(if (bulk) "板を一括追加" else "板を追加") },
            text = {
                Column {
                    if (bulk) {
                        Text(
                            "公式の板一覧にある、まだ登録していない板をまとめて追加します。",
                            color = colors.body, fontSize = 14.sp, modifier = Modifier.testTag("futaber-bulk-note")
                        )
                        bulkError?.let {
                            Text(it, color = colors.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).testTag("futaber-bulk-error"))
                        }
                    } else OutlinedTextField(
                        value = url,
                        onValueChange = { url = futaberSafeTake(it, 300) },
                        label = { Text("板のURL") },
                        placeholder = { Text("https://may.2chan.net/b/") },
                        singleLine = true,
                        isError = state.helperText != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().testTag("futaber-add-board-url")
                    )
                    if (!bulk) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = futaberSafeTake(it, FUTABER_BOARD_NAME_MAX_CHARS) },
                            label = { Text("名前（省略するとURLから付けます）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("futaber-add-board-name")
                        )
                        state.helperText?.let {
                            Text(it, color = colors.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    if (onBulkAdd != null) {
                        TextButton(
                            onClick = { bulk = !bulk; bulkError = null },
                            enabled = !bulkLoading,
                            modifier = Modifier.padding(top = 4.dp).testTag("futaber-add-board-bulk-toggle")
                        ) { Text(if (bulk) "1件ずつ追加" else "板一覧から一括追加", color = colors.link) }
                    }
                }
            },
            confirmButton = {
                if (bulk && onBulkAdd != null) {
                    TextButton(
                        onClick = {
                            bulkLoading = true
                            bulkError = null
                            scope.launch {
                                val result = onBulkAdd()
                                bulkLoading = false
                                result.onSuccess { added ->
                                    if (added > 0) onDismiss() else bulkError = "追加できる新しい板はありませんでした"
                                }.onFailure {
                                    // The address of the list is not part of what the user sees.
                                    bulkError = "板一覧を取得できませんでした"
                                }
                            }
                        },
                        enabled = !bulkLoading,
                        modifier = Modifier.testTag("futaber-add-board-bulk-submit")
                    ) { Text(if (bulkLoading) "取得中…" else "一括追加", color = if (bulkLoading) colors.meta else colors.link) }
                } else TextButton(
                    onClick = { onSubmit(name, url) },
                    enabled = state.canSubmit,
                    modifier = Modifier.testTag("futaber-add-board-submit")
                ) { Text("追加", color = if (state.canSubmit) colors.link else colors.meta) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.link) } }
        )
    }
}

@Composable
private fun FutaberRenameBoardDialog(board: BoardSummary, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val colors = LocalFutaberColors.current
    var name by rememberSaveable(board.id) { mutableStateOf(board.name) }
    val canSubmit = name.isNotBlank() && name.trim() != board.name
    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            title = { Text("板の名前を変更") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = futaberSafeTake(it, FUTABER_BOARD_NAME_MAX_CHARS) },
                        label = { Text("名前") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("futaber-rename-board-name")
                    )
                    Text(futaberBoardAddress(board.url), color = colors.meta, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSubmit(name) }, enabled = canSubmit,
                    modifier = Modifier.testTag("futaber-rename-board-submit")
                ) { Text("変更", color = if (canSubmit) colors.link else colors.meta) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.link) } }
        )
    }
}
