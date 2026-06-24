package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.FutachaHistoryArchivePreview
import com.valoser.futacha.shared.ui.FutachaHistoryArchivePreviewEntry
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val HISTORY_DRAWER_WARNING_ENTRY_COUNT = 100

internal fun shouldShowHistoryDrawerGrowthWarning(historyCount: Int): Boolean {
    return historyCount >= HISTORY_DRAWER_WARNING_ENTRY_COUNT
}

internal fun buildHistoryDrawerGrowthWarningTitle(historyCount: Int): String {
    return "履歴が増えています"
}

internal fun buildHistoryDrawerGrowthWarningBody(historyCount: Int): String {
    return "現在の履歴は${historyCount.coerceAtLeast(0)}件です。表示や保存が重くなる場合があります。"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDrawerContent(
    history: List<ThreadHistoryEntry>,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBoardClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onBatchDeleteClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onExportThenClearClick: () -> Unit = {},
    onExportSelectedClick: (List<ThreadHistoryEntry>) -> Unit = {},
    onLoadImportPreview: suspend () -> FutachaHistoryArchivePreview? = { null },
    onImportClick: () -> Unit = {},
    onImportSelectedClick: (Set<String>) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    bodyTextSize: ThreadBodyTextSize? = null
) {
    val content: @Composable () -> Unit = {
    val drawerWidth = 320.dp
    val coroutineScope = rememberCoroutineScope()
    var archiveActionDialog by remember { mutableStateOf<HistoryArchiveAction?>(null) }
    var isExportSelectionVisible by remember { mutableStateOf(false) }
    var importSelectionState by remember {
        mutableStateOf<HistoryImportSelectionState>(HistoryImportSelectionState.Hidden)
    }
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
                if (shouldShowHistoryDrawerGrowthWarning(history.size)) {
                    item(key = "history-growth-warning") {
                        HistoryGrowthWarningBanner(historyCount = history.size)
                    }
                }
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
                onExportClick = { archiveActionDialog = HistoryArchiveAction.Export },
                onImportClick = { archiveActionDialog = HistoryArchiveAction.Import },
                onSettingsClick = onSettingsClick
            )
        }
    }
    archiveActionDialog?.let { action ->
        HistoryArchiveActionDialog(
            action = action,
            onDismiss = { archiveActionDialog = null },
            onAllClick = {
                archiveActionDialog = null
                if (action == HistoryArchiveAction.Export) {
                    onExportClick()
                } else {
                    onImportClick()
                }
            },
            onExportThenClearClick = if (action == HistoryArchiveAction.Export) {
                {
                    archiveActionDialog = null
                    onExportThenClearClick()
                }
            } else {
                null
            },
            onSelectedClick = {
                archiveActionDialog = null
                if (action == HistoryArchiveAction.Export) {
                    isExportSelectionVisible = true
                } else {
                    importSelectionState = HistoryImportSelectionState.Loading
                    coroutineScope.launch {
                        importSelectionState = try {
                            val preview = onLoadImportPreview()
                            if (preview == null || preview.entries.isEmpty()) {
                                HistoryImportSelectionState.Error("選択できる履歴アーカイブがありません")
                            } else {
                                HistoryImportSelectionState.Ready(preview)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            HistoryImportSelectionState.Error(e.message ?: "履歴アーカイブを読み込めませんでした")
                        }
                    }
                }
            }
        )
    }
    if (isExportSelectionVisible) {
        HistoryExportSelectionDialog(
            history = history,
            onDismiss = { isExportSelectionVisible = false },
            onExport = { selectedEntries ->
                isExportSelectionVisible = false
                onExportSelectedClick(selectedEntries)
            }
        )
    }
    when (val state = importSelectionState) {
        HistoryImportSelectionState.Hidden -> Unit
        HistoryImportSelectionState.Loading -> {
            HistoryImportLoadingDialog(onDismiss = {
                importSelectionState = HistoryImportSelectionState.Hidden
            })
        }
        is HistoryImportSelectionState.Error -> {
            HistoryImportErrorDialog(
                message = state.message,
                onDismiss = { importSelectionState = HistoryImportSelectionState.Hidden }
            )
        }
        is HistoryImportSelectionState.Ready -> {
            HistoryImportSelectionDialog(
                preview = state.preview,
                onDismiss = { importSelectionState = HistoryImportSelectionState.Hidden },
                onImport = { selectedSnapshotIds ->
                    importSelectionState = HistoryImportSelectionState.Hidden
                    onImportSelectedClick(selectedSnapshotIds)
                }
            )
        }
    }
    }
    if (bodyTextSize != null) {
        ProvideThreadTextSizeTypography(bodyTextSize, content)
    } else {
        content()
    }
}

@Composable
private fun HistoryGrowthWarningBanner(historyCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = buildHistoryDrawerGrowthWarningTitle(historyCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = buildHistoryDrawerGrowthWarningBody(historyCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
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
    onExportClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HistoryBottomIcon(Icons.Rounded.Home, "板", onBoardClick)
                HistoryBottomIcon(Icons.Rounded.Refresh, "更新", onRefreshClick)
                HistoryBottomIcon(Icons.Rounded.DeleteSweep, "一括削除", onBatchDeleteClick)
                HistoryBottomIcon(Icons.Rounded.Settings, "設定", onSettingsClick)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HistoryBottomIcon(Icons.Rounded.FileUpload, "エクスポート", onExportClick)
                HistoryBottomIcon(Icons.Rounded.FileDownload, "インポート", onImportClick)
            }
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

private enum class HistoryArchiveAction {
    Export,
    Import
}

private sealed interface HistoryImportSelectionState {
    data object Hidden : HistoryImportSelectionState
    data object Loading : HistoryImportSelectionState
    data class Ready(val preview: FutachaHistoryArchivePreview) : HistoryImportSelectionState
    data class Error(val message: String) : HistoryImportSelectionState
}

@Composable
private fun HistoryArchiveActionDialog(
    action: HistoryArchiveAction,
    onDismiss: () -> Unit,
    onAllClick: () -> Unit,
    onExportThenClearClick: (() -> Unit)?,
    onSelectedClick: () -> Unit
) {
    val title = if (action == HistoryArchiveAction.Export) "履歴エクスポート" else "履歴インポート"
    val allLabel = if (action == HistoryArchiveAction.Export) "すべてエクスポート" else "すべてインポート"
    val selectedLabel = if (action == HistoryArchiveAction.Export) "選択してエクスポート" else "選択してインポート"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAllClick) {
                    Text(allLabel)
                }
                if (onExportThenClearClick != null) {
                    TextButton(onClick = onExportThenClearClick) {
                        Text("エクスポートして一括削除")
                    }
                }
                TextButton(onClick = onSelectedClick) {
                    Text(selectedLabel)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@Composable
private fun HistoryExportSelectionDialog(
    history: List<ThreadHistoryEntry>,
    onDismiss: () -> Unit,
    onExport: (List<ThreadHistoryEntry>) -> Unit
) {
    var selectedKeys by remember(history) {
        mutableStateOf(history.mapIndexedTo(linkedSetOf()) { index, entry ->
            buildHistorySelectionKey(index, entry)
        })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("エクスポートする履歴") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        selectedKeys = history.mapIndexedTo(linkedSetOf()) { index, entry ->
                            buildHistorySelectionKey(index, entry)
                        }
                    }) {
                        Text("全選択")
                    }
                    TextButton(onClick = { selectedKeys = linkedSetOf() }) {
                        Text("解除")
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = history,
                        key = { index, entry -> buildHistorySelectionKey(index, entry) }
                    ) { index, entry ->
                        val key = buildHistorySelectionKey(index, entry)
                        HistoryEntrySelectionRow(
                            entry = entry,
                            checked = key in selectedKeys,
                            onCheckedChange = { checked ->
                                selectedKeys = selectedKeys.toMutableSet().also { keys ->
                                    if (checked) keys.add(key) else keys.remove(key)
                                }.toCollection(linkedSetOf())
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedKeys.isNotEmpty(),
                onClick = {
                    val selectedEntries = history.filterIndexed { index, entry ->
                        buildHistorySelectionKey(index, entry) in selectedKeys
                    }
                    onExport(selectedEntries)
                }
            ) {
                Text("エクスポート")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@Composable
private fun HistoryEntrySelectionRow(
    entry: ThreadHistoryEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = entry.title.ifBlank { "無題" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${entry.boardName} / ${entry.threadId} / ${entry.replyCount}レス",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "最終閲覧: ${formatLastVisited(entry.lastVisitedEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryImportLoadingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("履歴アーカイブ") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("読み込み中です")
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@Composable
private fun HistoryImportErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("履歴アーカイブ") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun HistoryImportSelectionDialog(
    preview: FutachaHistoryArchivePreview,
    onDismiss: () -> Unit,
    onImport: (Set<String>) -> Unit
) {
    var selectedSnapshotIds by remember(preview) {
        mutableStateOf(preview.entries.mapTo(linkedSetOf()) { it.snapshotId })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("インポートする履歴") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${preview.entries.size}件 / ${formatLastVisited(preview.exportedAtEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        selectedSnapshotIds = preview.entries.mapTo(linkedSetOf()) { it.snapshotId }
                    }) {
                        Text("全選択")
                    }
                    TextButton(onClick = { selectedSnapshotIds = linkedSetOf() }) {
                        Text("解除")
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = preview.entries,
                        key = { it.snapshotId }
                    ) { entry ->
                        HistoryArchiveEntrySelectionRow(
                            entry = entry,
                            checked = entry.snapshotId in selectedSnapshotIds,
                            onCheckedChange = { checked ->
                                selectedSnapshotIds = selectedSnapshotIds.toMutableSet().also { ids ->
                                    if (checked) ids.add(entry.snapshotId) else ids.remove(entry.snapshotId)
                                }.toCollection(linkedSetOf())
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSnapshotIds.isNotEmpty(),
                onClick = { onImport(selectedSnapshotIds) }
            ) {
                Text("インポート")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@Composable
private fun HistoryArchiveEntrySelectionRow(
    entry: FutachaHistoryArchivePreviewEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val historyEntry = entry.historyEntry
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = historyEntry.title.ifBlank { "無題" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${historyEntry.boardName} / ${historyEntry.threadId} / ${historyEntry.replyCount}レス",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(
                    "最終閲覧: ${formatLastVisited(historyEntry.lastVisitedEpochMillis)}",
                    buildHistoryArchivePayloadLabel(entry.payloadStatus),
                    if (entry.alreadyExistsInHistory) "既存あり" else "新規"
                ).joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildHistorySelectionKey(index: Int, entry: ThreadHistoryEntry): String {
    return "$index|${entry.boardId}|${entry.threadId}|${entry.boardUrl}"
}

internal fun buildHistoryArchivePayloadLabel(status: HistoryArchivePayloadStatus): String {
    return when (status) {
        HistoryArchivePayloadStatus.FULL -> "スレ本体あり"
        HistoryArchivePayloadStatus.PARTIAL -> "一部あり"
        HistoryArchivePayloadStatus.HISTORY_ONLY -> "履歴のみ"
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleHistoryEntry(
    entry: ThreadHistoryEntry,
    onDismissed: (ThreadHistoryEntry) -> Unit,
    onClicked: () -> Unit
) {
    val latestEntry by rememberUpdatedState(entry)
    val latestOnDismissed by rememberUpdatedState(onDismissed)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                latestOnDismissed(latestEntry)
            }
            true
        }
    )
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
    val imageLoader = LocalFutachaImageLoader.current
    val density = LocalDensity.current
    val titleImageSizePx = remember(density) {
        with(density) { 48.dp.roundToPx() }
    }
    val titleImageRequest = remember(platformContext, entry.titleImageUrl, titleImageSizePx) {
        ImageRequest.Builder(platformContext)
            .data(entry.titleImageUrl)
            .crossfade(false)
            .size(titleImageSizePx, titleImageSizePx)
            .build()
    }
    val titlePainter = rememberAsyncImagePainter(
        model = titleImageRequest,
        imageLoader = imageLoader
    )
    val titlePainterState by titlePainter.state.collectAsState()
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                when (titlePainterState) {
                    is AsyncImagePainter.State.Error, is AsyncImagePainter.State.Empty -> {
                        MediaThumbnailFallbackIcon(
                            url = entry.titleImageUrl,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        Image(
                            painter = titlePainter,
                            contentDescription = "${entry.title} のタイトル画像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
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
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = entry.replyCount.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "レス",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
