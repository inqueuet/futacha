package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.repository.SavedThreadRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.pow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

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
    var loadError by remember { mutableStateOf<String?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<SavedThread?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    suspend fun reloadSavedThreads(showLoadingState: Boolean, showAsScreenError: Boolean): Boolean {
        if (showLoadingState) {
            isLoading = true
        }
        if (showAsScreenError) {
            loadError = null
        }
        try {
            withTimeout(15_000L) { // 15秒のタイムアウト
                val index = repository.loadIndex()
                threads = index.threads
                totalSize = index.totalSize
            }
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = buildSavedThreadsLoadErrorMessage(e)
            if (showAsScreenError) {
                loadError = message
            } else {
                snackbarHostState.showSnackbar(message)
            }
            return false
        } finally {
            if (showLoadingState) {
                isLoading = false
            }
        }
    }

    // FIX: データ読み込みにタイムアウトとエラーハンドリングを追加
    LaunchedEffect(Unit) {
        reloadSavedThreads(showLoadingState = true, showAsScreenError = true)
    }

    val refreshList: () -> Unit = {
        coroutineScope.launch {
            reloadSavedThreads(showLoadingState = false, showAsScreenError = false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("保存済みスレッド")
                        buildSavedThreadsSummaryText(
                            threadCount = threads.size,
                            totalSize = totalSize,
                            isLoading = isLoading
                        )?.let { summary ->
                            Text(
                                text = summary,
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
            when (val contentState = resolveSavedThreadsContentState(isLoading, loadError, threads)) {
                SavedThreadsContentState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is SavedThreadsContentState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = contentState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = {
                            coroutineScope.launch {
                                reloadSavedThreads(showLoadingState = true, showAsScreenError = true)
                            }
                        }) {
                            Text("再試行")
                        }
                    }
                }
                SavedThreadsContentState.Empty -> {
                    Text(
                        text = "保存済みスレッドがありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is SavedThreadsContentState.Data -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(contentState.threads, key = { it.storageId ?: "${it.boardId}:${it.threadId}" }) { thread ->
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
                                repository.deleteThread(
                                    threadId = thread.threadId,
                                    boardId = thread.boardId.ifBlank { null }
                                )
                                    .onSuccess {
                                        snackbarHostState.showSnackbar(
                                            buildSavedThreadsDeleteMessage(Result.success(Unit))
                                        )
                                        refreshList()
                                    }
                                    .onFailure { e ->
                                        snackbarHostState.showSnackbar(
                                            buildSavedThreadsDeleteMessage(Result.failure(e))
                                        )
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
        SaveStatus.DOWNLOADING -> savedThreadStatusLabel(status) to MaterialTheme.colorScheme.primary
        SaveStatus.COMPLETED -> savedThreadStatusLabel(status) to MaterialTheme.colorScheme.tertiary
        SaveStatus.FAILED -> savedThreadStatusLabel(status) to MaterialTheme.colorScheme.error
        SaveStatus.PARTIAL -> savedThreadStatusLabel(status) to MaterialTheme.colorScheme.secondary
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
internal fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> {
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            "${formatDecimal(gb, 2)} GB"
        }
    }
}

internal fun formatDecimal(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = kotlin.math.round(value * factor) / factor
    val parts = rounded.toString().split('.')
    val fraction = parts.getOrNull(1).orEmpty().padEnd(decimals, '0').take(decimals)
    return "${parts.first()}.$fraction"
}

internal fun savedThreadStatusLabel(status: SaveStatus): String {
    return when (status) {
        SaveStatus.DOWNLOADING -> "ダウンロード中"
        SaveStatus.COMPLETED -> "完了"
        SaveStatus.FAILED -> "失敗"
        SaveStatus.PARTIAL -> "一部"
    }
}

/**
 * 日時をフォーマット
 */
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun formatDate(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    @Suppress("DEPRECATION")
    return "${dateTime.year}/${dateTime.monthNumber.toString().padStart(2, '0')}/${dateTime.dayOfMonth.toString().padStart(2, '0')} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}
