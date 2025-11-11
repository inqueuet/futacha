package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.repository.SavedThreadRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 保存済みスレッド一覧画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedThreadsScreen(
    repository: SavedThreadRepository,
    onThreadClick: (SavedThread) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var threads by remember { mutableStateOf<List<SavedThread>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalSize by remember { mutableStateOf(0L) }
    var deleteConfirmTarget by remember { mutableStateOf<SavedThread?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // データ読み込み
    LaunchedEffect(Unit) {
        isLoading = true
        threads = repository.getAllThreads()
        totalSize = repository.getTotalSize()
        isLoading = false
    }

    val refreshList: () -> Unit = {
        coroutineScope.launch {
            threads = repository.getAllThreads()
            totalSize = repository.getTotalSize()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("保存済みスレッド")
                        if (!isLoading) {
                            Text(
                                text = "${threads.size} 件 / ${formatSize(totalSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                threads.isEmpty() -> {
                    Text(
                        text = "保存済みスレッドがありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(threads, key = { it.threadId }) { thread ->
                            SavedThreadCard(
                                thread = thread,
                                onClick = { onThreadClick(thread) },
                                onDeleteClick = { deleteConfirmTarget = thread }
                            )
                        }
                    }
                }
            }
        }

        // 削除確認ダイアログ
        deleteConfirmTarget?.let { thread ->
            AlertDialog(
                onDismissRequest = { deleteConfirmTarget = null },
                title = { Text("スレッドを削除") },
                text = {
                    Text("「${thread.title}」を削除しますか？\nこの操作は取り消せません。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.deleteThread(thread.threadId)
                                    .onSuccess {
                                        snackbarHostState.showSnackbar("削除しました")
                                        refreshList()
                                    }
                                    .onFailure { e ->
                                        snackbarHostState.showSnackbar("削除に失敗しました: ${e.message}")
                                    }
                                deleteConfirmTarget = null
                            }
                        }
                    ) {
                        Text("削除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmTarget = null }) {
                        Text("キャンセル")
                    }
                }
            )
        }
    }
}

/**
 * 保存済みスレッドカード
 */
@Composable
private fun SavedThreadCard(
    thread: SavedThread,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // メイン情報
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // タイトル
                Text(
                    text = thread.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 板名
                Text(
                    text = thread.boardName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ステータスバッジ
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(status = thread.status)
                }

                // 統計情報
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatItem("投稿", thread.postCount.toString())
                    StatItem("画像", thread.imageCount.toString())
                    StatItem("動画", thread.videoCount.toString())
                    StatItem("容量", formatSize(thread.totalSize))
                }

                // 保存日時
                Text(
                    text = "保存日時: ${formatDate(thread.savedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 削除ボタン
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.align(Alignment.Top)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * ステータスバッジ
 */
@Composable
private fun StatusBadge(status: SaveStatus) {
    val (text, color) = when (status) {
        SaveStatus.DOWNLOADING -> "ダウンロード中" to MaterialTheme.colorScheme.primary
        SaveStatus.COMPLETED -> "完了" to MaterialTheme.colorScheme.tertiary
        SaveStatus.FAILED -> "失敗" to MaterialTheme.colorScheme.error
        SaveStatus.PARTIAL -> "一部" to MaterialTheme.colorScheme.secondary
    }

    AssistChip(
        onClick = {},
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.1f),
            labelColor = color
        ),
        border = null
    )
}

/**
 * 統計アイテム
 */
@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * ファイルサイズをフォーマット
 */
private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * 日時をフォーマット
 */
private fun formatDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
