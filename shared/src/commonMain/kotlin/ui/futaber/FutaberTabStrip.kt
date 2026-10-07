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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage

private const val FUTABER_TAB_STRIP_MAX_VISIBLE = 3
internal const val FUTABER_TAB_STRIP_HEIGHT_DP = 48

/**
 * The registered threads, shown above the bottom bar of the catalog and thread screens.
 * Up to three share the width; more scroll sideways.
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
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = FUTABER_TAB_STRIP_HEIGHT_DP.dp)) {
            val tabWidth = maxWidth / minOf(tabs.size, FUTABER_TAB_STRIP_MAX_VISIBLE)
            LazyRow {
                items(tabs, key = { "${it.key.boardId}:${it.key.threadId}" }) { tab ->
                    val selected = tab.key == current
                    Row(
                        Modifier.width(tabWidth).heightIn(min = FUTABER_TAB_STRIP_HEIGHT_DP.dp)
                            .background(if (selected) colors.catalogGap else colors.bar)
                            .combinedClickable(
                                onClickLabel = "タブのスレッドを開く",
                                onLongClickLabel = "タブの操作を開く",
                                onLongClick = { haptic(); onLongPress(tab) },
                                onClick = { onOpen(tab) }
                            )
                            .semantics {
                                this.selected = selected
                                contentDescription = "${tab.title}、${tab.replyCount}レス"
                            }
                            .padding(horizontal = 6.dp)
                            .testTag("futaber-tab"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CatalogPreviewImage(
                            thumbnailUrl = tab.thumbnailUrl.ifBlank { null },
                            fullImageUrl = null,
                            targetSizePx = 100,
                            contentDescription = "",
                            modifier = Modifier.size(34.dp).background(colors.catalogGap),
                            fallbackTint = colors.meta
                        )
                        // No fillMaxHeight: a tab grows with a large font, and a lazy row must not hand its items the whole screen height.
                        Column(Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp).weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
                            Text(
                                tab.title, color = colors.body, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "${tab.replyCount} レス", color = colors.meta, fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End).padding(bottom = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
