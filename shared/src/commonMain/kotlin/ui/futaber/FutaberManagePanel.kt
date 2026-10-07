package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtBox
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtDetail
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtSummary
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtThumbnailUri
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.board.HistoryViewSettings
import com.valoser.futacha.shared.ui.board.applyHistoryViewSettings
import com.valoser.futacha.shared.ui.board.formatLastVisited
import com.valoser.futacha.shared.ui.util.PlatformBackHandler

internal enum class FutaberManageCategory(val title: String, val label: String, val icon: ImageVector) {
    SavedBox("保存箱", "保存箱", Icons.Outlined.ViewInAr),
    Notifications("通知", "通知", Icons.Outlined.Notifications),
    Favorites("お気に入り", "お気に入り", Icons.Outlined.StarOutline),
    History("スレッド履歴", "履歴", Icons.Outlined.History),
    Tabs("タブ", "タブ", Icons.Outlined.FilterNone)
}

/** Full-screen list of the viewing history and the registered tabs, with an edit mode. */
@Composable
internal fun FutaberManagePanel(
    history: List<ThreadHistoryEntry>,
    boards: List<BoardSummary>,
    tabs: List<FutaberTabView>,
    savedBox: FutaberSavedBoxState,
    savedBoxAvailable: Boolean,
    onOpenSaved: (SavedThread) -> Unit,
    onDeleteSaved: (SavedThread) -> Unit,
    /** The MHT files, shown above the saved threads in the same box (the original app's "MHTファイル" list). */
    mht: FutaberMhtBox = FutaberMhtBox(),
    notifications: List<CompatWatchResult>,
    notificationsAvailable: Boolean,
    notificationsStatus: String,
    onOpenNotification: (CompatWatchResult) -> Unit,
    onRemoveNotification: (CompatWatchResult) -> Unit,
    onClearNotifications: () -> Unit,
    onOpenWatcherManager: () -> Unit,
    favorites: List<FutaberFavoriteView>,
    onOpenFavorite: (FutaberFavoriteView) -> Unit,
    onRemoveFavorite: (FutaberFavoriteView) -> Unit,
    onClearFavorites: () -> Unit,
    onOpenHistory: (ThreadHistoryEntry) -> Unit,
    onDeleteHistory: (ThreadHistoryEntry) -> Unit,
    onOpenTab: (FutaberTabView) -> Unit,
    onRemoveTab: (FutaberTabView) -> Unit,
    onClearTabs: () -> Unit,
    onClose: () -> Unit,
    /** Refreshes the threads of the history ("一括更新" of the history band, an extension); null hides the button. */
    onRefreshHistory: (suspend () -> Unit)? = null
) {
    val colors = LocalFutaberColors.current
    var category by rememberSaveable { mutableStateOf(FutaberManageCategory.History) }
    var editing by rememberSaveable { mutableStateOf(false) }
    PlatformBackHandler(onBack = onClose)

    // The panel's own empty areas (header, gaps between icons, an empty list's message) take the touch themselves: a
    // node with a background but no pointer input lets the touch fall through to the catalog or thread under it.
    Column(Modifier.fillMaxSize().background(colors.background).pointerInput(Unit) {}.testTag("futaber-manage-panel")) {
        Box(Modifier.fillMaxWidth().background(colors.bar).height(FUTABER_PANEL_BAR_HEIGHT_DP.dp)) {
            Text(
                category.title, color = colors.body, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center)
            )
            if (category == FutaberManageCategory.Tabs) {
                if (tabs.isNotEmpty()) {
                    TextButton(
                        onClick = onClearTabs,
                        modifier = Modifier.align(Alignment.CenterStart).testTag("futaber-tabs-clear")
                    ) {
                        Text("すべて削除", color = colors.link)
                    }
                }
            } else {
                TextButton(
                    onClick = { editing = !editing },
                    modifier = Modifier.align(Alignment.CenterEnd).testTag("futaber-manage-edit")
                ) {
                    Text(if (editing) "完了" else "編集", color = colors.link)
                }
            }
        }
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (category) {
                FutaberManageCategory.SavedBox -> when {
                    !savedBoxAvailable && mht.entries.isEmpty() -> FutaberMessage("保存箱を利用できません")
                    savedBox.isLoading && mht.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                    savedBox.errorMessage != null && mht.entries.isEmpty() -> FutaberMessage(savedBox.errorMessage)
                    else -> LazyColumn(Modifier.fillMaxSize().testTag("futaber-saved-list")) {
                        if (mht.available) {
                            item(key = "mht-header") {
                                MhtHeader(mht, onImport = mht.onImport)
                            }
                            items(mht.entries, key = { "mht:" + it.fileName }) { entry ->
                                ManageRow(
                                    title = entry.title,
                                    thumbnail = futaberMhtThumbnailUri(entry),
                                    subtitle = entry.boardName.ifBlank { "MHT" },
                                    trailing = futaberMhtDetail(entry),
                                    count = entry.postCount,
                                    editing = editing,
                                    enabled = true,
                                    deleteLabel = "${entry.title}のMHTファイルを削除",
                                    rowTag = "futaber-mht-row",
                                    onOpen = { mht.onOpen(entry) },
                                    onDelete = { mht.onDelete(entry) },
                                    endAction = {
                                        IconButton(onClick = { mht.onShare(entry) }, modifier = Modifier.testTag("futaber-mht-share")) {
                                            FutaberIcon(Icons.Outlined.IosShare, contentDescription = "${entry.title}を共有", tint = colors.icon)
                                        }
                                    }
                                )
                            }
                        }
                        if (savedBoxAvailable && savedBox.threads.isNotEmpty()) {
                            if (mht.available) item(key = "saved-header") { MhtSectionTitle("保存したスレッド（HTML）") }
                            items(savedBox.threads, key = { "${it.boardId}:${it.threadId}" }) { saved ->
                                val openable = futaberBoardForSaved(saved, boards) != null
                                ManageRow(
                                    title = saved.title.ifBlank { "(無題)" },
                                    thumbnail = "",
                                    subtitle = futaberSavedBoardLine(saved, openable),
                                    trailing = futaberSavedDetail(saved),
                                    count = saved.postCount,
                                    editing = editing,
                                    enabled = openable,
                                    deleteLabel = "${saved.title}を保存箱から削除",
                                    rowTag = "futaber-saved-row",
                                    onOpen = { onOpenSaved(saved) },
                                    onDelete = { onDeleteSaved(saved) }
                                )
                            }
                        }
                        if (mht.entries.isEmpty() && (!savedBoxAvailable || savedBox.threads.isEmpty())) {
                            item(key = "empty") {
                                Text(
                                    "保存したスレッドはありません。スレッドの操作メニューの「MHTで保存」か「スレッドを保存」から保存できます",
                                    color = colors.meta, fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("futaber-saved-empty")
                                )
                            }
                        }
                    }
                }
                FutaberManageCategory.Notifications -> Column(Modifier.fillMaxSize()) {
                    if (!notificationsAvailable) {
                        FutaberMessage("通知を利用できません")
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                notificationsStatus,
                                color = colors.meta, fontSize = 12.sp,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("futaber-notifications-status")
                            )
                            TextButton(onClick = onOpenWatcherManager, modifier = Modifier.testTag("futaber-watch-manage")) {
                                Text("キーワード管理", color = colors.link)
                            }
                        }
                        if (editing && notifications.isNotEmpty()) {
                            TextButton(onClick = onClearNotifications, modifier = Modifier.testTag("futaber-notifications-clear")) {
                                Text("すべて削除", color = colors.link)
                            }
                        }
                        if (notifications.isEmpty()) {
                            FutaberMessage("一致したスレッドはまだありません")
                        } else {
                            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("futaber-notifications-list")) {
                                items(notifications, key = { it.history.canonicalUrl }) { result ->
                                    val openable = futaberRefForWatchResult(result, boards) != null
                                    ManageRow(
                                        title = result.history.title.ifBlank { "(無題)" },
                                        thumbnail = result.history.thumbnailUrl.orEmpty(),
                                        subtitle = futaberWatchSubtitle(result, openable),
                                        trailing = null,
                                        count = result.history.replyCount,
                                        editing = editing,
                                        enabled = openable,
                                        deleteLabel = "${result.history.title}を通知から削除",
                                        rowTag = "futaber-notification-row",
                                        onOpen = { onOpenNotification(result) },
                                        onDelete = { onRemoveNotification(result) }
                                    )
                                }
                            }
                        }
                    }
                }
                FutaberManageCategory.Favorites -> Column(Modifier.fillMaxSize()) {
                    if (editing && favorites.isNotEmpty()) {
                        TextButton(onClick = onClearFavorites, modifier = Modifier.testTag("futaber-favorites-clear")) {
                            Text("すべて削除", color = colors.link)
                        }
                    }
                    if (favorites.isEmpty()) {
                        FutaberMessage("お気に入りはありません。スレッドの操作メニューから追加できます")
                    } else {
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("futaber-favorites-list")) {
                            items(favorites, key = { "${it.favorite.boardId}:${it.favorite.threadId}" }) { view ->
                                val openable = futaberRefForFavorite(view, boards) != null
                                ManageRow(
                                    title = view.favorite.title,
                                    thumbnail = view.favorite.thumbnailUrl,
                                    subtitle = view.favorite.boardName + if (openable) "" else "（板が未登録）",
                                    trailing = null,
                                    count = view.replyCount,
                                    editing = editing,
                                    enabled = openable,
                                    deleteLabel = "${view.favorite.title}をお気に入りから外す",
                                    rowTag = "futaber-favorite-row",
                                    onOpen = { onOpenFavorite(view) },
                                    onDelete = { onRemoveFavorite(view) }
                                )
                            }
                        }
                    }
                }
                FutaberManageCategory.History -> if (history.isEmpty()) {
                    FutaberMessage("閲覧したスレッドはまだありません")
                } else Column(Modifier.fillMaxSize()) {
                    val bandOn = LocalFutaberDisplaySettings.current.extHistory
                    var viewSettings by rememberSaveable(stateSaver = HistoryViewSettings.Saver) { mutableStateOf(HistoryViewSettings.Default) }
                    val shown = remember(history, viewSettings, bandOn) {
                        futaberDistinctHistoryRows(if (bandOn) applyHistoryViewSettings(history, viewSettings) else history)
                    }
                    if (bandOn) {
                        FutaberHistoryBand(viewSettings, { viewSettings = it }, shown.size, history.size, onRefreshHistory)
                    }
                    if (shown.isEmpty()) FutaberMessage("条件に合う履歴はありません") else
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("futaber-history-list")) {
                        items(shown, key = ::futaberHistoryRowKey) { entry ->
                            val openable = futaberRefForHistory(entry, boards) != null
                            ManageRow(
                                title = entry.title.ifBlank { "(無題)" },
                                thumbnail = entry.titleImageUrl,
                                subtitle = entry.boardName + if (openable) "" else "（板が未登録）",
                                marks = if (bandOn) futaberHistoryMarks(entry) else null,
                                trailing = formatLastVisited(entry.lastVisitedEpochMillis),
                                count = entry.replyCount,
                                editing = editing,
                                enabled = openable,
                                deleteLabel = "${entry.title}を履歴から削除",
                                rowTag = "futaber-history-row",
                                onOpen = { onOpenHistory(entry) },
                                onDelete = { onDeleteHistory(entry) }
                            )
                        }
                    }
                }
                FutaberManageCategory.Tabs -> Column(Modifier.fillMaxSize()) {
                    if (tabs.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxWidth().testTag("futaber-tabs-list")) {
                            items(tabs, key = { "${it.key.boardId}:${it.key.threadId}" }) { tab ->
                                ManageRow(
                                    title = tab.title,
                                    thumbnail = tab.thumbnailUrl,
                                    subtitle = tab.boardName.ifBlank { null },
                                    trailing = null,
                                    count = tab.replyCount,
                                    editing = false,
                                    enabled = true,
                                    deleteLabel = "${tab.title}のタブを閉じる",
                                    rowTag = "futaber-tab-row",
                                    removeAtEnd = true,
                                    onOpen = { onOpenTab(tab) },
                                    onDelete = { onRemoveTab(tab) }
                                )
                            }
                        }
                    }
                    // How a tab is added and removed; the thread's panel button is the one to hold down.
                    Text(
                        "パネルのボタンを長押しでタブを追加　タブ長押しで削除",
                        color = colors.meta, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("futaber-tabs-hint")
                    )
                }
            }
        }
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
        Row(
            Modifier.fillMaxWidth().background(colors.bar).height(FUTABER_PANEL_BAR_HEIGHT_DP.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FutaberManageCategory.entries.forEach { item ->
                val selected = item == category
                IconButton(
                    onClick = { category = item; editing = false },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) colors.action else colors.bar)
                        .semantics {
                            this.selected = selected
                            contentDescription = item.label
                        }
                        .testTag("futaber-manage-category-${item.name}")
                ) {
                    FutaberIcon(item.icon, contentDescription = null, tint = if (selected) colors.onAction else colors.icon)
                }
            }
        }
    }
}

/** Height of the panel's own header and category bar. */
internal const val FUTABER_PANEL_BAR_HEIGHT_DP = 44

/**
 * One row of the panel as in the original app: a square thumbnail, the title with the count at
 * the right, and under it the board on the left and the time on the right.
 */
@Composable
private fun ManageRow(
    title: String,
    thumbnail: String,
    subtitle: String?,
    trailing: String?,
    count: Int,
    editing: Boolean,
    enabled: Boolean,
    deleteLabel: String,
    rowTag: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    removeAtEnd: Boolean = false,
    /** An extra button at the right end (the MHT rows share their file from here). */
    endAction: (@Composable () -> Unit)? = null,
    /** A line under the board and date (the history marks of the history extension); nothing when null. */
    marks: String? = null
) {
    val colors = LocalFutaberColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp)
                .clickable(enabled = enabled && !editing, onClickLabel = "スレッドを開く", onClick = onOpen)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag(rowTag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("futaber-manage-delete")) {
                    FutaberIcon(Icons.Outlined.RemoveCircleOutline, contentDescription = deleteLabel, tint = colors.accent)
                }
            }
            CatalogPreviewImage(
                thumbnailUrl = thumbnail.ifBlank { null },
                fullImageUrl = null,
                targetSizePx = 150,
                contentDescription = "",
                modifier = Modifier.size(50.dp).background(colors.catalogGap),
                fallbackTint = colors.meta
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, color = if (enabled) colors.body else colors.meta, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text(
                        count.toString(), color = colors.body, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    )
                }
                if (subtitle != null || trailing != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            subtitle.orEmpty(), color = colors.meta, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        trailing?.let {
                            Text(
                                it, color = colors.meta, fontSize = 12.sp, maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                            )
                        }
                    }
                }
                if (!marks.isNullOrEmpty()) {
                    Text(marks, color = colors.accent, fontSize = 11.sp, maxLines = 1, modifier = Modifier.testTag("futaber-history-marks"))
                }
            }
            if (endAction != null && !editing) endAction()
            if (removeAtEnd) {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("futaber-manage-delete")) {
                    FutaberIcon(Icons.Outlined.Close, contentDescription = deleteLabel, tint = colors.meta)
                }
            }
        }
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
    }
}

@Composable
private fun MhtSectionTitle(text: String) {
    val colors = LocalFutaberColors.current
    Text(
        text, color = colors.meta, fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().background(colors.catalogGap).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** The head of the MHT list: how many files and how big, a note while one is being read, and the button that opens a file from outside. */
@Composable
private fun MhtHeader(mht: FutaberMhtBox, onImport: () -> Unit) {
    val colors = LocalFutaberColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.catalogGap).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            futaberMhtSummary(mht.entries, mht.busyMessage), color = colors.meta, fontSize = 12.sp,
            modifier = Modifier.weight(1f).testTag("futaber-mht-summary")
        )
        TextButton(onClick = onImport, enabled = mht.busyMessage == null, modifier = Modifier.testTag("futaber-mht-import")) {
            Text("ファイルを開く", color = colors.link, fontSize = 13.sp)
        }
    }
}
