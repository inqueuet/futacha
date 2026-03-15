package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDrawerContent(
    history: List<ThreadHistoryEntry>,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBoardClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onBatchDeleteClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val drawerWidth = 320.dp
    ModalDrawerSheet(
        modifier = Modifier
            .width(drawerWidth)
            .fillMaxHeight(),
        drawerShape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item { HistoryListHeader() }
                items(
                    items = history,
                    key = {
                        buildString {
                            append(it.boardId)
                            append('|')
                            append(it.threadId)
                            append('|')
                            append(it.boardUrl)
                        }
                    }
                ) { entry ->
                    DismissibleHistoryEntry(
                        entry = entry,
                        onDismissed = onHistoryEntryDismissed,
                        onClicked = { onHistoryEntrySelected(entry) }
                    )
                }
            }
            HistoryBottomBar(
                onBoardClick = onBoardClick,
                onRefreshClick = onRefreshClick,
                onBatchDeleteClick = onBatchDeleteClick,
                onSettingsClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun HistoryListHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = "閲覧中のスレッド",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun HistoryBottomBar(
    onBoardClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onBatchDeleteClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryBottomIcon(Icons.Rounded.Home, "板", onBoardClick)
            HistoryBottomIcon(Icons.Rounded.Refresh, "更新", onRefreshClick)
            HistoryBottomIcon(Icons.Rounded.DeleteSweep, "一括削除", onBatchDeleteClick)
            HistoryBottomIcon(Icons.Rounded.Settings, "設定", onSettingsClick)
        }
    }
}

@Composable
private fun HistoryBottomIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleHistoryEntry(
    entry: ThreadHistoryEntry,
    onDismissed: (ThreadHistoryEntry) -> Unit,
    onClicked: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(entry, dismissState) {
        snapshotFlow { dismissState.currentValue }
            .distinctUntilChanged()
            .filter { it == SwipeToDismissBoxValue.StartToEnd }
            .take(1)
            .collect {
                onDismissed(entry)
            }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = { HistoryDismissBackground() }
    ) {
        HistoryEntryCard(entry = entry, onClick = onClicked)
    }
}

@Composable
private fun HistoryDismissBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "削除",
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "削除",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: ThreadHistoryEntry,
    onClick: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    val titleImageRequest = remember(platformContext, entry.titleImageUrl) {
        ImageRequest.Builder(platformContext)
            .data(entry.titleImageUrl)
            .crossfade(true)
            .build()
    }
    val formattedLastVisited = remember(entry.lastVisitedEpochMillis) {
        formatLastVisited(entry.lastVisitedEpochMillis)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = titleImageRequest,
                imageLoader = LocalFutachaImageLoader.current,
                contentDescription = "${entry.title} のタイトル画像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.boardUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "最終閲覧: $formattedLastVisited",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (entry.hasAutoSave) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = "自動保存あり",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "保存済",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = entry.replyCount.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "レス数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
