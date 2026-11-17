package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlin.math.roundToInt

/**
 * The "ペーストボード" over the writing, as the original's iOS table (M39, `P/out-post/90-06・90-07`): 閉じる / ペーストボード /
 * 編集, the explanation, the kept texts and pictures (folders first). A tap puts a row into the writing, a folder opens.
 * 編集 shows the selection circles and the grips, and the bar 削除／移動／追加／すべて選択／新規フォルダ; 追加 keeps the text being
 * written or the attached picture (this app's two ways of keeping, as before).
 */
@Composable
internal fun FutaberPasteboardSheet(
    link: FutaberPasteboardLink,
    canKeepText: Boolean,
    canKeepImage: Boolean,
    onKeepText: (folder: String?) -> Unit,
    onKeepImage: (folder: String?) -> Unit,
    onPick: (FutaberPasteItem) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalFutaberColors.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    var addSheet by remember { mutableStateOf(false) }
    var moveSheet by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf<String?>(null) }
    // The folder shown went away (removed, or read again without it): back to the top.
    val openFolder = folder?.let { id -> link.items.firstOrNull { it.id == id && it.isFolder } }
    androidx.compose.runtime.LaunchedEffect(folder, openFolder) { if (folder != null && openFolder == null) folder = null }
    val rows = futaberPasteboardLevel(link.items, openFolder?.id)
    // Only the rows of the level on screen count as chosen (a row removed elsewhere drops out).
    val selected = chosen.filterTo(mutableSetOf()) { id -> rows.any { it.id == id } }
    fun choose(next: Set<String>) { chosen = next }
    PlatformBackHandler(enabled = true) {
        when {
            openFolder != null && !editing -> folder = null
            editing -> { editing = false; choose(emptySet()) }
            else -> onClose()
        }
    }
    // The keyboard of the writing would cover the bar at the bottom: it goes away while the board is open.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    Box(Modifier.fillMaxSize().testTag("futaber-pasteboard")) {
        Column(Modifier.fillMaxSize().background(colors.settingsPage).statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(colors.settingsRow).heightIn(min = FUTABER_TOP_BAR_HEIGHT_DP.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PasteboardBarButton(
                    if (openFolder != null && !editing) "‹ ペーストボード" else "閉じる",
                    if (openFolder != null && !editing) "futaber-pasteboard-back" else "futaber-pasteboard-close",
                    bold = true
                ) { if (openFolder != null && !editing) folder = null else onClose() }
                Text(
                    openFolder?.folderName ?: "ペーストボード", color = colors.body, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("futaber-pasteboard-title")
                )
                PasteboardBarButton(if (editing) "完了" else "編集", "futaber-pasteboard-edit") {
                    editing = !editing
                    choose(emptySet())
                }
            }
            HorizontalDivider(color = colors.settingsSeparator, thickness = 0.5.dp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("futaber-pasteboard-list")) {
                if (openFolder == null) item(key = "note") {
                    // The original's first row is this sentence (a sample it keeps); here it explains the board.
                    Column(Modifier.fillMaxWidth().background(colors.settingsRow)) {
                        Text(
                            "よく使うテキストや画像を保存して素早く貼り付けられます",
                            color = colors.body, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                        HorizontalDivider(color = colors.settingsSeparator, thickness = 0.5.dp)
                    }
                }
                items(rows, key = { it.id }) { item ->
                    PasteboardRow(
                        item = item,
                        link = link,
                        editing = editing,
                        selected = item.id in selected,
                        levelIds = futaberPasteboardDragLevel(rows, item),
                        onTap = {
                            when {
                                editing -> choose(if (item.id in selected) selected - item.id else selected + item.id)
                                item.isFolder -> folder = item.id
                                else -> onPick(item)
                            }
                        },
                        onReorder = { ordered -> link.onEdit { futaberPasteboardReordered(it, ordered) } }
                    )
                }
                if (rows.isEmpty()) item(key = "empty") {
                    Text(
                        if (openFolder != null) "このフォルダは空です。編集の「追加」で、書き込み中の本文や添付した画像を保存できます"
                        else "まだありません。編集の「追加」で、書き込み中の本文や添付した画像を保存できます",
                        color = colors.settingsHeader, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("futaber-pasteboard-empty")
                    )
                }
            }
            if (editing) PasteboardEditBar(
                rows = rows, selected = selected, inFolder = openFolder != null, choose = ::choose,
                onEdit = link.onEdit, onMove = { moveSheet = true }, onAdd = { addSheet = true }, onNewFolder = { newFolder = "" }
            )
        }
        PasteboardEditOverlays(
            link = link, openFolder = openFolder, selected = selected, choose = ::choose,
            addSheet = addSheet, onAddSheet = { addSheet = it }, moveSheet = moveSheet, onMoveSheet = { moveSheet = it },
            newFolder = newFolder, onNewFolder = { newFolder = it },
            canKeepText = canKeepText, canKeepImage = canKeepImage, onKeepText = onKeepText, onKeepImage = onKeepImage
        )
    }
}

@Composable
private fun PasteboardBarButton(label: String, tag: String, enabled: Boolean = true, bold: Boolean = false, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Box(
        Modifier.heightIn(min = 44.dp).clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp).testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (enabled) colors.iosBlue else colors.iconDisabled, fontSize = 16.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1
        )
    }
}

/** One row of the table: the selection circle (editing), the picture or folder, the text, the grip (editing) or ›. */
@Composable
private fun PasteboardRow(
    item: FutaberPasteItem,
    link: FutaberPasteboardLink,
    editing: Boolean,
    selected: Boolean,
    levelIds: List<String>,
    onTap: () -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val colors = LocalFutaberColors.current
    val latestIds by rememberUpdatedState(levelIds)
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }
    Column(
        Modifier.fillMaxWidth().offset { IntOffset(0, dragY.roundToInt()) }.background(colors.settingsRow)
            .onSizeChanged { if (it.height > 0) rowHeight = it.height.toFloat() }
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .clickable(onClickLabel = if (editing) "選択" else if (item.isFolder) "開く" else "書き込みに貼り付け", onClick = onTap)
                .semantics(mergeDescendants = true) { if (editing) { this.selected = selected; role = Role.Checkbox } }
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                .testTag(if (item.isFolder) "futaber-pasteboard-folder" else "futaber-pasteboard-item"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (editing) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .then(if (selected) Modifier.background(colors.iosBlue) else Modifier.border(1.dp, colors.separator, CircleShape))
                        .testTag("futaber-pasteboard-select"),
                    contentAlignment = Alignment.Center
                ) { if (selected) FutaberIcon(Icons.Outlined.Check, contentDescription = null, tint = colors.onIosBlue, modifier = Modifier.size(16.dp)) }
            }
            when {
                item.isFolder -> {
                    FutaberIcon(Icons.Outlined.Folder, contentDescription = "フォルダ", tint = colors.iosBlue)
                    Text(item.folderName.orEmpty(), color = colors.body, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${link.items.count { it.folder == item.id }}", color = colors.meta, fontSize = 15.sp)
                }
                item.isImage -> {
                    val image by produceState<com.valoser.futacha.shared.util.ImageData?>(null, item.id) {
                        value = link.loadImage(item).getOrNull()
                    }
                    Box(Modifier.size(56.dp).background(colors.fill), contentAlignment = Alignment.Center) {
                        val loaded = image
                        if (loaded != null) {
                            AsyncImage(
                                model = loaded.bytes, imageLoader = LocalFutachaImageLoader.current, contentDescription = "保存した画像",
                                contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp)
                            )
                        } else Text("画像", color = colors.body, fontSize = 12.sp)
                    }
                    Text(item.imageName.ifBlank { "画像" }, color = colors.body, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                else -> Text(item.text, color = colors.body, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (editing) {
                // The grip: drag it up or down to move the row (one place each time it passes half a row).
                Box(
                    Modifier.size(44.dp).semantics { contentDescription = "並べ替え" }
                        .pointerInput(item.id) {
                            // The order this drag has made so far: the next step is taken from it, not from the stored
                            // list that may not have come back yet (a fast drag skipped rows; audit B5).
                            var working: List<String>? = null
                            detectDragGestures(
                                onDragStart = { working = latestIds },
                                onDragEnd = { dragY = 0f; working = null },
                                onDragCancel = { dragY = 0f; working = null },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragY += amount.y
                                    val ids = working ?: latestIds
                                    val (next, step) = futaberPasteboardGripStep(ids, item.id, dragY, rowHeight)
                                    if (step != 0) {
                                        dragY -= step * rowHeight
                                        working = next
                                        onReorder(next)
                                    }
                                }
                            )
                        }
                        .testTag("futaber-pasteboard-grip"),
                    contentAlignment = Alignment.Center
                ) { FutaberIcon(Icons.Outlined.DragHandle, contentDescription = null, tint = colors.chevron) }
            }
        }
        HorizontalDivider(color = colors.settingsSeparator, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
    }
}

/** The edit mode's bar: 削除／移動／追加／すべて選択／新規フォルダ (a folder is not put in a folder). */
@Composable
private fun PasteboardEditBar(
    rows: List<FutaberPasteItem>,
    selected: Set<String>,
    inFolder: Boolean,
    choose: (Set<String>) -> Unit,
    onEdit: ((List<FutaberPasteItem>) -> List<FutaberPasteItem>) -> Unit,
    onMove: () -> Unit,
    onAdd: () -> Unit,
    onNewFolder: () -> Unit
) {
    val colors = LocalFutaberColors.current
    HorizontalDivider(color = colors.settingsSeparator, thickness = 0.5.dp)
    val allSelected = rows.isNotEmpty() && selected.size == rows.size
    Row(
        Modifier.fillMaxWidth().background(colors.settingsRow).heightIn(min = 44.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PasteboardBarButton("削除", "futaber-pasteboard-delete", enabled = selected.isNotEmpty()) {
            val gone = selected.toSet()
            choose(emptySet())
            onEdit { futaberPasteboardWithoutAll(it, gone) }
        }
        PasteboardBarButton(
            "移動", "futaber-pasteboard-move",
            enabled = selected.isNotEmpty() && rows.none { it.id in selected && it.isFolder },
            onClick = onMove
        )
        PasteboardBarButton("追加", "futaber-pasteboard-add", onClick = onAdd)
        PasteboardBarButton(if (allSelected) "選択解除" else "すべて選択", "futaber-pasteboard-select-all", enabled = rows.isNotEmpty()) {
            choose(if (allSelected) emptySet() else rows.map { it.id }.toSet())
        }
        PasteboardBarButton("新規フォルダ", "futaber-pasteboard-new-folder", enabled = !inFolder, onClick = onNewFolder)
    }
}

/** The edit mode's 追加 and 移動 sheets and the 新規フォルダ alert. */
@Composable
private fun PasteboardEditOverlays(
    link: FutaberPasteboardLink,
    openFolder: FutaberPasteItem?,
    selected: Set<String>,
    choose: (Set<String>) -> Unit,
    addSheet: Boolean,
    onAddSheet: (Boolean) -> Unit,
    moveSheet: Boolean,
    onMoveSheet: (Boolean) -> Unit,
    newFolder: String?,
    onNewFolder: (String?) -> Unit,
    canKeepText: Boolean,
    canKeepImage: Boolean,
    onKeepText: (folder: String?) -> Unit,
    onKeepImage: (folder: String?) -> Unit
) {
    if (addSheet) {
        FutaberActionSheet(
            title = "追加",
            actions = listOf(
                FutaberPostAction("pasteboard-keep-text", "本文を保存", enabled = canKeepText) { onAddSheet(false); onKeepText(openFolder?.id) },
                FutaberPostAction("pasteboard-keep-image", "添付画像を保存", enabled = canKeepImage) { onAddSheet(false); onKeepImage(openFolder?.id) }
            ),
            onDismiss = { onAddSheet(false) }
        )
    }
    if (moveSheet) {
        val folders = link.items.filter { it.isFolder }
        FutaberActionSheet(
            title = "移動先",
            actions = buildList {
                add(FutaberPostAction("pasteboard-move-top", "フォルダなし", enabled = openFolder != null) {
                    onMoveSheet(false)
                    val moving = selected.toSet()
                    choose(emptySet())
                    link.onEdit { futaberPasteboardMoved(it, moving, null) }
                })
                folders.forEach { target ->
                    add(FutaberPostAction("pasteboard-move-${target.id}", target.folderName.orEmpty(), enabled = target.id != openFolder?.id) {
                        onMoveSheet(false)
                        val moving = selected.toSet()
                        choose(emptySet())
                        link.onEdit { futaberPasteboardMoved(it, moving, target.id) }
                    })
                }
            },
            onDismiss = { onMoveSheet(false) }
        )
    }
    newFolder?.let { name ->
        FutaberAlert(
            title = "新規フォルダ",
            message = "フォルダの名前を入れてください",
            field = FutaberAlertField(name, { onNewFolder(it.take(FUTABER_PASTEBOARD_FOLDER_NAME_MAX_CHARS)) }),
            buttons = listOf(
                FutaberAlertButton("folder-cancel", "キャンセル", FutaberAlertStyle.Cancel) { onNewFolder(null) },
                FutaberAlertButton("folder-create", "作成", enabled = name.isNotBlank()) {
                    onNewFolder(null)
                    link.onEdit { futaberPasteboardWithFolder(it, name, futaberNewPasteId()) }
                }
            ),
            onDismiss = { onNewFolder(null) }
        )
    }
}
