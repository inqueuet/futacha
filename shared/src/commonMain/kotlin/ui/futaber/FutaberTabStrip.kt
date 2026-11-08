package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage

private const val FUTABER_TAB_STRIP_MAX_VISIBLE = 3
internal const val FUTABER_TAB_STRIP_HEIGHT_DP = 44

/**
 * The registered threads, shown above the bottom bar of the catalog and thread screens.
 * Up to three share the width; more scroll sideways. As in the original, the tab of the open thread looks like the
 * others (V23); a screen reader still hears which one is selected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FutaberTabStrip(
    tabs: List<FutaberTabView>,
    current: FutaberTabKey?,
    onOpen: (FutaberTabView) -> Unit,
    onLongPress: (FutaberTabView) -> Unit = {}
) {
    if (tabs.isEmpty()) return
    val colors = LocalFutaberColors.current
    val haptic = rememberFutaberLongPressHaptic()
    Column(Modifier.fillMaxWidth().background(colors.bar).testTag("futaber-tab-strip")) {
        HorizontalDivider(color = colors.barSeparator, thickness = 0.5.dp)
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = FUTABER_TAB_STRIP_HEIGHT_DP.dp)) {
            val tabWidth = maxWidth / minOf(tabs.size, FUTABER_TAB_STRIP_MAX_VISIBLE)
            LazyRow {
                itemsIndexed(tabs, key = { _, tab -> "${tab.key.boardId}:${tab.key.threadId}" }) { index, tab ->
                    val selected = tab.key == current
                    Row(
                        Modifier.width(tabWidth).heightIn(min = FUTABER_TAB_STRIP_HEIGHT_DP.dp)
                            .background(colors.bar)
                            // A hairline between two tabs.
                            .then(if (index < tabs.lastIndex) Modifier.drawBehind {
                                val stroke = 0.5.dp.toPx()
                                drawLine(colors.barSeparator, Offset(size.width - stroke / 2f, 0f), Offset(size.width - stroke / 2f, size.height), stroke)
                            } else Modifier)
                            .combinedClickable(
                                onClickLabel = "タブのスレッドを開く",
                                onLongClickLabel = "タブの操作を開く",
                                onLongClick = { haptic(); onLongPress(tab) },
                                onClick = { onOpen(tab) }
                            )
                            .semantics {
                                this.selected = selected
                                contentDescription = "${tab.title}、${futaberShownReplyCount(tab.replyCount)}レス" +
                                    com.valoser.futacha.shared.ui.board.threadStatusText(tab.status).takeIf { it.isNotEmpty() }?.let { "、$it" }.orEmpty()
                            }
                            .padding(start = 8.dp, end = 6.dp)
                            .testTag("futaber-tab"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CatalogPreviewImage(
                            thumbnailUrl = tab.thumbnailUrl.ifBlank { null },
                            fullImageUrl = null,
                            targetSizePx = 100,
                            contentDescription = "",
                            modifier = Modifier.size(34.dp).background(colors.fill),
                            fallbackTint = colors.meta
                        )
                        // No fillMaxHeight: a tab grows with a large font, and a lazy row must not hand its items the whole screen height.
                        Column(Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp).weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
                            Text(
                                tab.title, color = colors.catalogTitle, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                listOf(com.valoser.futacha.shared.ui.board.threadStatusText(tab.status), "${futaberShownReplyCount(tab.replyCount)} レス")
                                    .filter { it.isNotEmpty() }.joinToString("・"), color = colors.meta, fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End).padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A strip tab's long-press sheet (L-B5): headed by the thread's thumbnail and title as the original's
 * (`F:catalog-015`: a 40×40 picture and the title left-aligned), its five items in the original's order and the
 * extensions after them ([futaberTabSheetExtras]: [restore], [checkAll]). The sheet closes before an item runs.
 */
@Composable
internal fun FutaberTabSheetHost(
    tab: FutaberTabView,
    tabViews: List<FutaberTabView>,
    tabKeys: List<FutaberTabKey>,
    onClose: () -> Unit,
    onRemoveTab: () -> Unit,
    closeTabsInBulk: ((List<FutaberTabKey>) -> List<FutaberTabKey>) -> Unit,
    restore: FutaberPostAction?,
    checkAll: FutaberPostAction?,
    closeFallen: FutaberPostAction? = null
) {
    val colors = LocalFutaberColors.current
    val fallen = androidx.compose.runtime.remember(tabViews) { tabViews.filter { it.fallen }.mapTo(HashSet()) { it.key } }
    FutaberActionSheet(
        title = tab.title,
        titleLeading = {
            CatalogPreviewImage(
                thumbnailUrl = tab.thumbnailUrl.ifBlank { null },
                fullImageUrl = null,
                targetSizePx = 120,
                contentDescription = "",
                modifier = Modifier.size(FUTABER_TAB_SHEET_THUMBNAIL_DP.dp).background(colors.fill)
                    .testTag("futaber-sheet-title-thumbnail"),
                fallbackTint = colors.meta
            )
        },
        actions = futaberTabSheetActions(
            hasRight = tabKeys.lastOrNull() != tab.key,
            hasOthers = tabKeys.size > 1,
            hasFallen = fallen.isNotEmpty(),
            onRemove = onRemoveTab,
            onRemoveRight = { closeTabsInBulk { futaberRemoveTabsToTheRight(it, tab.key) } },
            onRemoveOthers = { closeTabsInBulk { futaberKeepOnlyTab(it, tab.key) } },
            onRemoveFallen = { closeTabsInBulk { futaberRemoveFallenTabs(it, fallen) } },
            onRemoveAll = { closeTabsInBulk { emptyList() } },
            extras = futaberTabSheetExtras(restore, checkAll, closeFallen)
        ).map { action -> action.copy(onClick = { onClose(); action.onClick() }) },
        onDismiss = onClose,
        cancelId = "tab-cancel"
    )
}

/** The tab sheet's thumbnail (the original's 40×40). */
internal const val FUTABER_TAB_SHEET_THUMBNAIL_DP = 40
