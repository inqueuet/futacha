package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ui.util.ScrollableToolbarRow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtEntry
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtDetail
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtSummary
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtThumbnailUri
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.board.HistoryViewSettings
import com.valoser.futacha.shared.ui.board.applyHistoryViewSettings
import com.valoser.futacha.shared.ui.util.PlatformBackHandler

/** The colour the original gives the chosen category's icon (saved box green, notices orange, favourites yellow, ...). */
internal fun futaberCategoryColor(category: FutaberManageCategory, colors: FutaberColors): Color = when (category) {
    FutaberManageCategory.SavedBox -> colors.categorySaved
    FutaberManageCategory.Notifications -> colors.categoryNotifications
    FutaberManageCategory.Favorites -> colors.categoryFavorites
    FutaberManageCategory.History -> colors.categoryHistory
    FutaberManageCategory.Tabs -> colors.categoryTabs
}

internal enum class FutaberManageCategory(val title: String, val label: String, val icon: ImageVector) {
    SavedBox("保存箱", "保存箱", Icons.Outlined.ViewInAr),
    Notifications("通知", "通知", Icons.Outlined.Notifications),
    Favorites("お気に入り", "お気に入り", Icons.Outlined.StarOutline),
    History("スレッド履歴", "履歴", Icons.Outlined.History),
    Tabs("タブ", "タブ", Icons.Outlined.FilterNone);

    /** V22: the chosen category's icon, filled (and tinted with [futaberCategoryColor]); the others are [icon]'s thin lines. */
    val filledIcon: ImageVector
        get() = when (this) {
            SavedBox -> FutaberPanelCubeFilledIcon
            Notifications -> FutaberPanelBellFilledIcon
            Favorites -> FutaberPanelStarFilledIcon
            History -> FutaberPanelClockFilledIcon
            Tabs -> FutaberPanelTabsFilledIcon
        }
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
    /**
     * 「すべて削除」 of the saved box: one job deletes everything in turn and the box is read again once
     * (one launch per item ran N deletions at the same time and re-read the box N times; audit B2).
     */
    onClearSavedBox: ((List<FutaberMhtEntry>, List<SavedThread>) -> Unit)? = null,
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
    onRefreshHistory: (suspend () -> Unit)? = null,
    /** A row whose board is not registered asks to add the board and opens it (official boards); null keeps it disabled. */
    onAddBoardAndOpen: ((FutaberBoardAddRequest) -> Unit)? = null,
    /** Set with the saved-box extension: a long press on a saved thread shows how complete the save is. */
    onReopenSaved: ((SavedThread) -> Unit)? = null,
    /** The other modes' tabs (with the favourites extension): their starred threads are listed under the own ones. */
    otherModeTabs: kotlinx.coroutines.flow.Flow<List<com.valoser.futacha.shared.compat.CompatTab>>? = null,
    onOpenOtherFavorite: (BoardSummary, FutaberThreadRef) -> Unit = { _, _ -> },
    /** The history band's "全件削除" and "アーカイブ" (the batch B4 extensions); the defaults add nothing. */
    historyTools: FutaberHistoryTools = FutaberHistoryTools(),
    /** The history rows with what they were made from, kept by the host so reopening the panel does not make them again. */
    historyRowsMemo: com.valoser.futacha.shared.ui.OffMainMemo<List<Any?>, List<com.valoser.futacha.shared.model.ThreadHistoryEntry>?> =
        remember { com.valoser.futacha.shared.ui.OffMainMemo() },
    /** S24 (L-B10): the category to open on (the one chosen last) and where a change of it is kept. */
    initialCategory: FutaberManageCategory = FutaberManageCategory.History,
    onCategoryChange: (FutaberManageCategory) -> Unit = {},
    /** M29: 「すべて削除」 of the history (it asks with the history's own dialog); null = the band's, if any. */
    onClearHistory: (() -> Unit)? = null,
    /** Where the confirmation of 「すべて削除」 is drawn over the whole screen; null draws it over the panel. */
    overlay: FutaberScreenOverlay? = null
) {
    val colors = LocalFutaberColors.current
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var savedStatus by remember { mutableStateOf<SavedThread?>(null) }
    val ownOverlay = remember { FutaberScreenOverlay() }
    val alerts = overlay ?: ownOverlay
    // G36: iOS's edge swipe does not close the panel (futaber's); Android's system Back still does (look doc §1.6).
    FutaberBackHandler(onBack = onClose, iosEdgeGestureEnabled = false)
    val count = when (category) {
        FutaberManageCategory.SavedBox -> mht.entries.size + if (savedBoxAvailable) savedBox.threads.size else 0
        FutaberManageCategory.Notifications -> notifications.size
        FutaberManageCategory.Favorites -> favorites.size
        FutaberManageCategory.History -> history.size
        FutaberManageCategory.Tabs -> tabs.size
    }
    val clearAll: () -> Unit = {
        when (category) {
            FutaberManageCategory.History -> (onClearHistory ?: historyTools.onClearAll)?.invoke()
            FutaberManageCategory.Tabs -> onClearTabs()
            else -> {
                val asked = category
                val mhtNow = mht.entries
                val savedNow = if (savedBoxAvailable) savedBox.threads else emptyList()
                alerts.show {
                    FutaberManageClearAlert(asked, mhtNow.size, savedNow.size, count, onDismiss = alerts::dismiss) {
                        when (asked) {
                            FutaberManageCategory.SavedBox -> onClearSavedBox?.invoke(mhtNow, savedNow)
                                ?: run { mhtNow.forEach(mht.onDelete); savedNow.forEach(onDeleteSaved) }
                            FutaberManageCategory.Notifications -> onClearNotifications()
                            FutaberManageCategory.Favorites -> onClearFavorites()
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    // The panel's own empty areas (header, gaps between icons, an empty list's message) take the touch themselves: a
    // node with a background but no pointer input lets the touch fall through to the catalog or thread under it.
    Column(Modifier.fillMaxSize().background(colors.settingsPage).pointerInput(Unit) {}.testTag("futaber-manage-panel")) {
        FutaberManageHeaderBar(
            futaberManageHeader(category, editing, count),
            leftTag = if (category == FutaberManageCategory.Tabs) "futaber-tabs-clear" else "futaber-manage-clear",
            onLeft = clearAll,
            onRight = { editing = !editing }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (category) {
                FutaberManageCategory.SavedBox -> FutaberSavedBoxBody(
                    savedBox, savedBoxAvailable, boards, mht, editing, onOpenSaved, onDeleteSaved,
                    onLongPressSaved = if (onReopenSaved != null) ({ savedStatus = it }) else null
                )
                FutaberManageCategory.Notifications -> FutaberNotificationsBody(
                    notifications, notificationsAvailable, notificationsStatus, boards, editing,
                    onOpenNotification, onRemoveNotification, onOpenWatcherManager, onAddBoardAndOpen
                )
                FutaberManageCategory.Favorites -> FutaberFavoritesBody(
                    favorites, boards, editing, onOpenFavorite, onRemoveFavorite, otherModeTabs, onOpenOtherFavorite, onAddBoardAndOpen
                )
                FutaberManageCategory.History -> FutaberHistoryBody(
                    history, boards, editing, onOpenHistory, onDeleteHistory, onRefreshHistory, onAddBoardAndOpen, historyTools, historyRowsMemo
                )
                FutaberManageCategory.Tabs -> FutaberTabsBody(tabs, onOpenTab, onRemoveTab)
            }
        }
        HorizontalDivider(color = colors.barSeparator, thickness = 0.5.dp)
        FutaberManageCategoryBar(category) { item ->
            category = item
            editing = false
            onCategoryChange(item)
        }
    }
    savedStatus?.let { saved ->
        val reopen = onReopenSaved
        FutaberSavedStatusDialog(
            saved = saved,
            onReopen = if (reopen != null && futaberBoardForSaved(saved, boards) != null) ({ reopen(saved) }) else null,
            onDismiss = { savedStatus = null }
        )
    }
    if (overlay == null) FutaberScreenOverlayHost(ownOverlay)
}

/** M50: 「すべて削除」 asks first (futaber's confirmation is not recorded; nothing is deleted without one here). */
@Composable
private fun FutaberManageClearAlert(
    category: FutaberManageCategory, mhtCount: Int, savedCount: Int, count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    FutaberAlert(
        title = "すべて削除しますか？",
        message = futaberManageClearMessage(category, mhtCount, savedCount, count),
        buttons = listOf(
            FutaberAlertButton("manage-clear-cancel", "キャンセル", FutaberAlertStyle.Cancel) { onDismiss() },
            FutaberAlertButton("manage-clear-confirm", "削除", FutaberAlertStyle.Destructive) { onDismiss(); onConfirm() }
        ),
        onDismiss = onDismiss
    )
}

/** The panel's heading: 「すべて削除」 at the left while editing, the title, 「編集」／「選択」／「完了」 at the right (M29・M50). */
@Composable
private fun FutaberManageHeaderBar(header: FutaberManageHeader, leftTag: String, onLeft: () -> Unit, onRight: () -> Unit) {
    val colors = LocalFutaberColors.current
    Box(Modifier.fillMaxWidth().background(colors.bar).height(FUTABER_PANEL_BAR_HEIGHT_DP.dp)) {
        Text(
            header.title, color = colors.body, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).testTag("futaber-manage-title")
        )
        header.leftLabel?.let { label ->
            TextButton(onClick = onLeft, enabled = header.leftEnabled, modifier = Modifier.align(Alignment.CenterStart).testTag(leftTag)) {
                Text(label, color = if (header.leftEnabled) colors.iosBlue else colors.iconDisabled, fontSize = 14.sp)
            }
        }
        header.rightLabel?.let { label ->
            TextButton(onClick = onRight, modifier = Modifier.align(Alignment.CenterEnd).testTag("futaber-manage-edit")) {
                Text(label, color = colors.iosBlue, fontSize = 14.sp)
            }
        }
    }
    HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
}

/** V22: the five categories; the chosen one filled in its colour, the others thin grey lines, no ground behind any. */
@Composable
private fun FutaberManageCategoryBar(category: FutaberManageCategory, onChoose: (FutaberManageCategory) -> Unit) {
    val colors = LocalFutaberColors.current
    ScrollableToolbarRow(
        itemCount = FutaberManageCategory.entries.size, minItemWidth = 48.dp,
        modifier = Modifier.fillMaxWidth().background(colors.bar).height(FUTABER_PANEL_BAR_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FutaberManageCategory.entries.forEach { item ->
            val selected = item == category
            IconButton(
                onClick = { onChoose(item) },
                modifier = Modifier
                    .semantics {
                        this.selected = selected
                        contentDescription = item.label
                    }
                    .testTag("futaber-manage-category-${item.name}")
            ) {
                FutaberIcon(
                    if (selected) item.filledIcon else item.icon, contentDescription = null,
                    tint = if (selected) futaberCategoryColor(item, colors) else colors.iconDisabled
                )
            }
        }
    }
}

/** S21: the MHT files under futaber's total line; a tap shares the file, the row's 「開く」 opens it here. HTML saves after. */
@Composable
private fun FutaberSavedBoxBody(
    savedBox: FutaberSavedBoxState,
    savedBoxAvailable: Boolean,
    boards: List<BoardSummary>,
    mht: FutaberMhtBox,
    editing: Boolean,
    onOpenSaved: (SavedThread) -> Unit,
    onDeleteSaved: (SavedThread) -> Unit,
    onLongPressSaved: ((SavedThread) -> Unit)?
) {
    val colors = LocalFutaberColors.current
    when {
        !savedBoxAvailable && mht.entries.isEmpty() && !mht.available -> FutaberMessage("保存箱を利用できません")
        savedBox.isLoading && mht.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent)
        }
        savedBox.errorMessage != null && mht.entries.isEmpty() -> FutaberMessage(savedBox.errorMessage)
        else -> LazyColumn(Modifier.fillMaxSize().testTag("futaber-saved-list")) {
            if (mht.available) {
                item(key = "mht-total") {
                    Text(
                        futaberMhtSummary(mht.entries, mht.busyMessage), color = colors.settingsHeader, fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("futaber-mht-summary")
                    )
                }
                items(mht.entries, key = { "mht:" + it.fileName }) { entry ->
                    ManageRow(
                        title = entry.title,
                        thumbnail = futaberMhtThumbnailUri(entry),
                        subtitle = entry.boardName.ifBlank { "MHT" },
                        trailing = futaberMhtDetail(entry),
                        count = entry.postCount,
                        editing = editing,
                        enabled = mht.busyMessage == null,
                        deleteLabel = "${entry.title}のMHTファイルを削除",
                        rowTag = "futaber-mht-row",
                        clickLabel = "共有",
                        onOpen = { mht.onShare(entry) },
                        onDelete = { mht.onDelete(entry) },
                        endAction = {
                            TextButton(onClick = { mht.onOpen(entry) }, enabled = mht.busyMessage == null,
                                modifier = Modifier.testTag("futaber-mht-open")) {
                                Text("開く", color = colors.iosBlue, fontSize = 14.sp,
                                    modifier = Modifier.semantics { contentDescription = "${entry.title}をアプリで開く" })
                            }
                        }
                    )
                }
            }
            // This app's additions after futaber's list (one function, F-B2 puts them behind 「その他の機能」).
            val extras = futaberManagePanelExtras(
                FutaberManageCategory.SavedBox,
                htmlSaves = savedBoxAvailable && savedBox.threads.isNotEmpty(), mhtAvailable = mht.available
            )
            extras.forEach { extra ->
                when (extra) {
                    FutaberManageExtra.HtmlSaves -> {
                        item(key = "saved-header") { MhtSectionTitle("保存したスレッド（HTML）") }
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
                                onDelete = { onDeleteSaved(saved) },
                                onLongPress = onLongPressSaved?.let { handler -> { handler(saved) } },
                                longPressLabel = "保存状態を表示"
                            )
                        }
                    }
                    FutaberManageExtra.MhtImport -> item(key = "mht-import") {
                        FutaberManageExtraRow(
                            "ファイルを開く", extra.tag, enabled = mht.busyMessage == null && !editing, onClick = mht.onImport
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

/** M31: futaber's notices, plain when empty; the patrol's state and 「キーワード管理」 are small rows at the end. */
@Composable
private fun FutaberNotificationsBody(
    notifications: List<CompatWatchResult>,
    notificationsAvailable: Boolean,
    notificationsStatus: String,
    boards: List<BoardSummary>,
    editing: Boolean,
    onOpenNotification: (CompatWatchResult) -> Unit,
    onRemoveNotification: (CompatWatchResult) -> Unit,
    onOpenWatcherManager: () -> Unit,
    onAddBoardAndOpen: ((FutaberBoardAddRequest) -> Unit)?
) {
    val colors = LocalFutaberColors.current
    if (!notificationsAvailable) {
        FutaberMessage("通知を利用できません")
        return
    }
    val rowKeys = remember(notifications) { futaberNotificationRowKeys(notifications) }
    LazyColumn(Modifier.fillMaxSize().testTag("futaber-notifications-list")) {
        itemsIndexed(notifications, key = { index, _ -> rowKeys[index] }) { _, result ->
            val openable = futaberRefForWatchResult(result, boards) != null
            val addRequest = if (openable || onAddBoardAndOpen == null) null else futaberBoardAddRequestForWatchResult(result)
            ManageRow(
                title = result.history.title.ifBlank { "(無題)" },
                thumbnail = result.history.thumbnailUrl.orEmpty(),
                subtitle = futaberWatchSubtitle(result, openable),
                trailing = null,
                count = result.history.replyCount,
                editing = editing,
                enabled = openable || addRequest != null,
                deleteLabel = "${result.history.title}を通知から削除",
                rowTag = "futaber-notification-row",
                onOpen = { if (addRequest != null) onAddBoardAndOpen?.invoke(addRequest) else onOpenNotification(result) },
                onDelete = { onRemoveNotification(result) }
            )
        }
        futaberManagePanelExtras(FutaberManageCategory.Notifications, notificationsAvailable).forEach { extra ->
            when (extra) {
                FutaberManageExtra.NotificationsStatus -> item(key = "status") {
                    Text(
                        notificationsStatus, color = colors.settingsHeader, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp).testTag(extra.tag)
                    )
                }
                FutaberManageExtra.KeywordManager -> item(key = "keywords") {
                    FutaberManageExtraRow("キーワード管理", extra.tag, enabled = !editing, onClick = onOpenWatcherManager)
                }
                else -> Unit
            }
        }
    }
}

/** M32: futaber's favourites, plain when empty; the other modes' starred threads (an extension) after them. */
@Composable
private fun FutaberFavoritesBody(
    favorites: List<FutaberFavoriteView>,
    boards: List<BoardSummary>,
    editing: Boolean,
    onOpenFavorite: (FutaberFavoriteView) -> Unit,
    onRemoveFavorite: (FutaberFavoriteView) -> Unit,
    otherModeTabs: kotlinx.coroutines.flow.Flow<List<com.valoser.futacha.shared.compat.CompatTab>>?,
    onOpenOtherFavorite: (BoardSummary, FutaberThreadRef) -> Unit,
    onAddBoardAndOpen: ((FutaberBoardAddRequest) -> Unit)?
) {
    val otherSource = remember(otherModeTabs) { otherModeTabs ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val otherTabs by otherSource.collectAsState(emptyList())
    val others = remember(otherTabs, boards) { futaberOtherModeFavorites(otherTabs, boards) }
    LazyColumn(Modifier.fillMaxSize().testTag("futaber-favorites-list")) {
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
        if (FutaberManageExtra.OtherFavorites in futaberManagePanelExtras(FutaberManageCategory.Favorites, otherFavorites = others.isNotEmpty())) {
            item(key = "other-favorites-header") {
                MhtSectionTitle("他のモードのお気に入り（読み取り専用）", Modifier.testTag(FutaberManageExtra.OtherFavorites.tag))
            }
            items(others, key = { "other:" + it.tab.key }) { other ->
                val ref = other.ref
                val target = ref?.let { r -> boards.firstOrNull { it.id == r.boardId } }
                val addRequest = if (target != null || onAddBoardAndOpen == null) null else futaberBoardAddRequestForOtherFavorite(other)
                ManageRow(
                    title = other.tab.title.ifBlank { "(無題)" },
                    thumbnail = other.tab.thumbnailUrl.orEmpty(),
                    subtitle = other.tab.boardName + if (target != null) "" else "（板が未登録）",
                    trailing = null,
                    count = other.tab.replyCount,
                    // Read only: no delete button even while editing the own favourites.
                    editing = false,
                    enabled = (target != null || addRequest != null) && !editing,
                    deleteLabel = "",
                    rowTag = "futaber-other-favorite-row",
                    onOpen = {
                        if (target != null && ref != null) onOpenOtherFavorite(target, ref)
                        else addRequest?.let { onAddBoardAndOpen?.invoke(it) }
                    },
                    onDelete = {}
                )
            }
        }
    }
}

/** M29・G25・V35: futaber's history (「編集」, a long press does nothing, dates as 26/10/31 01:08); the band above it. */
@Composable
private fun FutaberHistoryBody(
    history: List<ThreadHistoryEntry>,
    boards: List<BoardSummary>,
    editing: Boolean,
    onOpenHistory: (ThreadHistoryEntry) -> Unit,
    onDeleteHistory: (ThreadHistoryEntry) -> Unit,
    onRefreshHistory: (suspend () -> Unit)?,
    onAddBoardAndOpen: ((FutaberBoardAddRequest) -> Unit)?,
    historyTools: FutaberHistoryTools,
    historyRowsMemo: com.valoser.futacha.shared.ui.OffMainMemo<List<Any?>, List<ThreadHistoryEntry>?>
) {
    // With the archive extension on, the band stays even with nothing listed (an archive can be read in).
    if (history.isEmpty() && historyTools.onOpenArchive == null) {
        FutaberMessage("閲覧したスレッドはまだありません")
        return
    }
    Column(Modifier.fillMaxSize()) {
        val bandOn = LocalFutaberDisplaySettings.current.extHistory
        var viewSettings by rememberSaveable(stateSaver = HistoryViewSettings.Saver) { mutableStateOf(HistoryViewSettings.Default) }
        // The filter and sort of up to 20,000 rows run on the parsing dispatcher, as in ふたちゃ (audit M17), and the
        // result is kept by the host with what it was made from (re-audit M9/M10). Until a new one is ready the last
        // stays, and typing is debounced.
        val appliedSettings = viewSettings.copy(titleQuery = rememberFutaberDebouncedQuery(viewSettings.titleQuery))
        val made = com.valoser.futacha.shared.ui.rememberOffMain(
            historyRowsMemo, listOf(com.valoser.futacha.shared.ui.SameInstance(history), appliedSettings, bandOn), null
        ) { _, _ -> futaberManageHistoryRows(history, appliedSettings, bandOn) }
        val shown = made.orEmpty()
        // The band filters the list, so it stays above it (the features batch decides where it folds, look doc §2.10).
        if (bandOn || historyTools.onOpenArchive != null) {
            val viewTools = bandOn && history.isNotEmpty()
            FutaberHistoryBand(
                viewSettings, { viewSettings = it }, shown.size, history.size,
                onRefresh = if (viewTools) onRefreshHistory else null,
                showViewTools = viewTools,
                onClearAll = if (viewTools) historyTools.onClearAll else null,
                onOpenArchive = historyTools.onOpenArchive
            )
        }
        if (history.isEmpty()) FutaberMessage("閲覧したスレッドはまだありません")
        else if (made == null) Unit
        else if (shown.isEmpty()) FutaberMessage("条件に合う履歴はありません") else
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("futaber-history-list")) {
            items(shown, key = ::futaberHistoryRowKey) { entry ->
                val openable = futaberRefForHistory(entry, boards) != null
                val addRequest = if (openable || onAddBoardAndOpen == null) null else futaberBoardAddRequestForHistory(entry)
                ManageRow(
                    title = entry.title.ifBlank { "(無題)" },
                    thumbnail = entry.titleImageUrl,
                    subtitle = entry.boardName + if (openable) "" else "（板が未登録）",
                    marks = if (bandOn) futaberHistoryMarks(entry) else null,
                    trailing = futaberPanelDate(entry.lastVisitedEpochMillis),
                    // F27: replies, as futaber counts them (the history keeps the posts with the first).
                    count = futaberShownReplyCount(entry.replyCount),
                    editing = editing,
                    enabled = openable || addRequest != null,
                    deleteLabel = "${entry.title}を履歴から削除",
                    rowTag = "futaber-history-row",
                    onOpen = { if (addRequest != null) onAddBoardAndOpen?.invoke(addRequest) else onOpenHistory(entry) },
                    onDelete = { onDeleteHistory(entry) },
                    // G25: futaber's long press on a history row does nothing (before, letting go opened the thread).
                    onLongPress = {}
                )
            }
        }
    }
}

/**
 * The tabs: a row's × closes it, and how a tab is added and removed under the list. The other modes' tabs are in the
 * strip and in this list (plan B8a, Q1), so the separate 「他のモードのタブ」 list is gone (plan B8b).
 */
@Composable
private fun FutaberTabsBody(
    tabs: List<FutaberTabView>,
    onOpenTab: (FutaberTabView) -> Unit,
    onRemoveTab: (FutaberTabView) -> Unit
) {
    val colors = LocalFutaberColors.current
    Column(Modifier.fillMaxSize()) {
        if (tabs.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().testTag("futaber-tabs-list")) {
                items(tabs, key = { "${it.key.boardId}:${it.key.threadId}" }) { tab ->
                    ManageRow(
                        title = tab.title,
                        thumbnail = tab.thumbnailUrl,
                        subtitle = listOf(tab.boardName, com.valoser.futacha.shared.ui.board.threadStatusText(tab.status))
                            .filter { it.isNotBlank() }.joinToString("・").ifBlank { null },
                        trailing = null,
                        count = futaberShownReplyCount(tab.replyCount),
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
            color = colors.settingsHeader, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("futaber-tabs-hint")
        )
    }
}

/** Height of the panel's own header and category bar. */
internal const val FUTABER_PANEL_BAR_HEIGHT_DP = 44

/**
 * One row of the panel as in the original app: a square thumbnail, the title with the count at
 * the right, and under it the board on the left and the time on the right. While editing, a grey × at the right
 * removes it (M29) and the row does not open.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    /** An extra button at the right end (the MHT rows open their file in the app from here). */
    endAction: (@Composable () -> Unit)? = null,
    /** A line under the board and date (the history marks of the history extension); nothing when null. */
    marks: String? = null,
    /** A long press; null keeps the row a plain tap target. An empty lambda takes the long press and does nothing (G25). */
    onLongPress: (() -> Unit)? = null,
    longPressLabel: String? = null,
    clickLabel: String = "スレッドを開く"
) {
    val colors = LocalFutaberColors.current
    val opens = futaberManageRowOpens(editing, enabled)
    Column(Modifier.background(colors.settingsRow)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp)
                .then(
                    if (onLongPress == null) Modifier.clickable(enabled = opens, onClickLabel = clickLabel, onClick = onOpen)
                    else Modifier.combinedClickable(
                        enabled = !editing, onClickLabel = clickLabel, onLongClickLabel = longPressLabel,
                        onClick = { if (opens) onOpen() }, onLongClick = onLongPress
                    )
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag(rowTag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CatalogPreviewImage(
                thumbnailUrl = thumbnail.ifBlank { null },
                fullImageUrl = null,
                targetSizePx = 150,
                contentDescription = "",
                modifier = Modifier.size(50.dp).background(colors.fill),
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
                                modifier = Modifier.padding(start = 8.dp, end = 4.dp).testTag("futaber-manage-row-date")
                            )
                        }
                    }
                }
                if (!marks.isNullOrEmpty()) {
                    Text(marks, color = colors.accent, fontSize = 11.sp, maxLines = 1, modifier = Modifier.testTag("futaber-history-marks"))
                }
            }
            if (endAction != null && !editing) endAction()
            if (removeAtEnd || editing) {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("futaber-manage-delete")) {
                    FutaberIcon(Icons.Outlined.Close, contentDescription = deleteLabel, tint = colors.iconDisabled)
                }
            }
        }
        HorizontalDivider(color = colors.settingsSeparator, thickness = 0.5.dp)
    }
}

/** A small row of this app's own at the end of a category's list (「キーワード管理」, 「ファイルを開く」). */
@Composable
private fun FutaberManageExtraRow(label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Box(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp).testTag(tag),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = if (enabled) colors.iosBlueOnSheet else colors.settingsHeader, fontSize = 13.sp)
    }
}

@Composable
private fun MhtSectionTitle(text: String, modifier: Modifier = Modifier) {
    val colors = LocalFutaberColors.current
    Text(
        text, color = colors.settingsHeader, fontSize = 12.sp,
        modifier = modifier.fillMaxWidth().background(colors.settingsPage).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

/** The rows of the manage panel's history: with the band on, ふたちゃ's filter and sort; then one row per thread. */
internal fun futaberManageHistoryRows(
    history: List<com.valoser.futacha.shared.model.ThreadHistoryEntry>,
    settings: HistoryViewSettings,
    bandOn: Boolean
): List<com.valoser.futacha.shared.model.ThreadHistoryEntry> =
    futaberDistinctHistoryRows(if (bandOn) applyHistoryViewSettings(history, settings) else history)
