package com.valoser.futacha.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.Wearable
import com.valoser.futacha.shared.watch.WatchBoard
import com.valoser.futacha.shared.watch.WatchPostPreview
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchThreadSummary
import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_STALE_AGE_MILLIS
import com.valoser.futacha.wear.sync.PhoneCommandClient
import com.valoser.futacha.wear.sync.WatchSnapshotStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FutachaWearApp()
        }
    }
}

private const val WATCH_NOW_TICK_MILLIS = 30_000L

@Composable
private fun FutachaWearApp() {
    val context = LocalContext.current
    val commandClient = remember(context) { PhoneCommandClient(context.applicationContext) }
    val snapshot by WatchSnapshotStore.observe().collectAsState()
    var isPhoneReachable by remember { mutableStateOf<Boolean?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        WatchSnapshotStore.loadPersisted(context.applicationContext)
        WatchSnapshotStore.loadDataLayerSnapshotAsync(context.applicationContext)
        commandClient.requestSnapshot()
    }

    LaunchedEffect(context) {
        while (true) {
            Wearable.getNodeClient(context.applicationContext)
                .connectedNodes
                .addOnSuccessListener { nodes ->
                    isPhoneReachable = nodes.isNotEmpty()
                }
                .addOnFailureListener {
                    isPhoneReachable = false
                }
            delay(WATCH_NOW_TICK_MILLIS)
        }
    }

    MaterialTheme {
        FutachaWearContent(
            snapshot = snapshot,
            isPhoneReachable = isPhoneReachable,
            statusMessage = statusMessage,
            onRefresh = {
                statusMessage = "スマホで更新を要求しました"
                commandClient.requestRefresh()
            },
            onRequestSync = {
                statusMessage = "同期を要求しました"
                commandClient.requestSnapshot()
            },
            onOpenBoardOnPhone = { board ->
                statusMessage = "スマホで板を開きます"
                commandClient.openBoardOnPhone(board.id, board.url)
            },
            onOpenThreadOnPhone = { thread ->
                statusMessage = "スマホでスレを開きます"
                commandClient.openThreadOnPhone(thread.boardId, thread.boardUrl, thread.threadId)
            },
            onStartReadAloudOnPhone = { thread ->
                statusMessage = "読み上げ開始を要求しました"
                commandClient.startReadAloudOnPhone(thread)
            },
            onPauseReadAloudOnPhone = { thread ->
                statusMessage = "一時停止を要求しました"
                commandClient.pauseReadAloudOnPhone(thread)
            },
            onStopReadAloudOnPhone = { thread ->
                statusMessage = "停止を要求しました"
                commandClient.stopReadAloudOnPhone(thread)
            },
            onNextReadAloudOnPhone = { thread ->
                statusMessage = "次へ移動を要求しました"
                commandClient.nextReadAloudOnPhone(thread)
            },
            onPreviousReadAloudOnPhone = { thread ->
                statusMessage = "前へ移動を要求しました"
                commandClient.previousReadAloudOnPhone(thread)
            }
        )
    }
}

@Composable
private fun FutachaWearContent(
    snapshot: WatchSnapshot?,
    isPhoneReachable: Boolean?,
    statusMessage: String?,
    onRefresh: () -> Unit,
    onRequestSync: () -> Unit,
    onOpenBoardOnPhone: (WatchBoard) -> Unit,
    onOpenThreadOnPhone: (WatchThreadSummary) -> Unit,
    onStartReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onPauseReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onStopReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onNextReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onPreviousReadAloudOnPhone: (WatchThreadSummary) -> Unit
) {
    var selectedBoardId by remember { mutableStateOf<String?>(null) }
    var selectedThreadKey by remember { mutableStateOf<WearThreadKey?>(null) }
    val selectedBoard = snapshot?.boards?.firstOrNull { it.id == selectedBoardId }
    val selectedThread = snapshot?.threads?.firstOrNull {
        it.boardId == selectedThreadKey?.boardId && it.threadId == selectedThreadKey?.threadId
    }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(WATCH_NOW_TICK_MILLIS)
            nowMillis = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            snapshot == null -> EmptySnapshotView(onRequestSync = onRequestSync)
            selectedThread != null -> ThreadDetailView(
                thread = selectedThread,
                nowMillis = nowMillis,
                isPhoneReachable = isPhoneReachable,
                statusMessage = statusMessage,
                onBack = { selectedThreadKey = null },
                onRequestSync = onRequestSync,
                onOpenThreadOnPhone = onOpenThreadOnPhone,
                onStartReadAloudOnPhone = onStartReadAloudOnPhone,
                onPauseReadAloudOnPhone = onPauseReadAloudOnPhone,
                onStopReadAloudOnPhone = onStopReadAloudOnPhone,
                onNextReadAloudOnPhone = onNextReadAloudOnPhone,
                onPreviousReadAloudOnPhone = onPreviousReadAloudOnPhone
            )
            selectedBoard != null -> BoardThreadsView(
                board = selectedBoard,
                snapshot = snapshot,
                nowMillis = nowMillis,
                isPhoneReachable = isPhoneReachable,
                statusMessage = statusMessage,
                onBack = { selectedBoardId = null },
                onRequestSync = onRequestSync,
                onOpenBoardOnPhone = onOpenBoardOnPhone,
                onThreadSelected = { selectedThreadKey = it.toWearThreadKey() }
            )
            else -> HomeView(
                snapshot = snapshot,
                nowMillis = nowMillis,
                isPhoneReachable = isPhoneReachable,
                statusMessage = statusMessage,
                onBoardSelected = { selectedBoardId = it.id },
                onThreadSelected = { selectedThreadKey = it.toWearThreadKey() },
                onRefresh = onRefresh,
                onRequestSync = onRequestSync
            )
        }
    }
}

@Composable
private fun EmptySnapshotView(
    onRequestSync: () -> Unit
) {
    WatchScreen {
        Text(
            text = "futacha",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "スマホと同期してください",
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))
        ActionRow(label = "同期を要求", onClick = onRequestSync)
    }
}

@Composable
private fun HomeView(
    snapshot: WatchSnapshot,
    nowMillis: Long,
    isPhoneReachable: Boolean?,
    statusMessage: String?,
    onBoardSelected: (WatchBoard) -> Unit,
    onThreadSelected: (WatchThreadSummary) -> Unit,
    onRefresh: () -> Unit,
    onRequestSync: () -> Unit
) {
    val watchMatches = snapshot.threads.filter { it.isWatchWordMatch }
    val unreadThreads = snapshot.threads.filter { it.newReplyCount > 0 }
    val activeReadAloudThread = snapshot.threads.firstOrNull {
        it.freshReadAloudStatus(nowMillis) != null
    }
    val syncLabel = buildSnapshotSyncLabel(snapshot.generatedAtMillis, nowMillis)
    WatchScreen {
        Text(
            text = "futacha",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "新着 ${snapshot.unreadTotal} / 監視 ${snapshot.watchMatchTotal}",
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$syncLabel / ${snapshot.boards.size}板 / ${snapshot.threads.size}スレ",
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        WatchStatusText(
            connectionLabel = buildPhoneConnectionLabel(isPhoneReachable),
            statusMessage = statusMessage
        )
        Spacer(Modifier.height(10.dp))
        ActionRow(label = "スマホで更新", onClick = onRefresh)
        Spacer(Modifier.height(6.dp))
        ActionRow(label = "同期を要求", onClick = onRequestSync)
        activeReadAloudThread?.let { thread ->
            SectionTitle("読み上げ中")
            ThreadRow(thread = thread, nowMillis = nowMillis, onClick = { onThreadSelected(thread) })
        }
        SectionTitle("板")
        if (snapshot.boards.isEmpty()) {
            MutedText("板なし")
        } else {
            snapshot.boards.forEach { board ->
                BoardRow(board = board, snapshot = snapshot, onClick = { onBoardSelected(board) })
            }
        }
        SectionTitle("スレ")
        if (snapshot.threads.isEmpty()) {
            MutedText("履歴なし")
        } else {
            snapshot.threads.forEach { thread ->
                ThreadRow(thread = thread, nowMillis = nowMillis, onClick = { onThreadSelected(thread) })
            }
        }
        SectionTitle("監視")
        if (watchMatches.isEmpty()) {
            MutedText("一致なし")
        } else {
            watchMatches.take(3).forEach { thread ->
                ThreadRow(thread = thread, nowMillis = nowMillis, onClick = { onThreadSelected(thread) })
            }
        }
        SectionTitle("新着")
        if (unreadThreads.isEmpty()) {
            MutedText("新着なし")
        } else {
            unreadThreads.take(5).forEach { thread ->
                ThreadRow(thread = thread, nowMillis = nowMillis, onClick = { onThreadSelected(thread) })
            }
        }
    }
}

@Composable
private fun BoardThreadsView(
    board: WatchBoard,
    snapshot: WatchSnapshot,
    nowMillis: Long,
    isPhoneReachable: Boolean?,
    statusMessage: String?,
    onBack: () -> Unit,
    onRequestSync: () -> Unit,
    onOpenBoardOnPhone: (WatchBoard) -> Unit,
    onThreadSelected: (WatchThreadSummary) -> Unit
) {
    val threads = snapshot.threads.filter { it.boardId == board.id }
    WatchScreen {
        TopBackRow(title = board.name, onBack = onBack)
        Text(
            text = "${threads.size}スレ",
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        WatchStatusText(
            connectionLabel = buildPhoneConnectionLabel(isPhoneReachable),
            statusMessage = statusMessage
        )
        Spacer(Modifier.height(8.dp))
        ActionRow(label = "スマホで板を開く", onClick = { onOpenBoardOnPhone(board) })
        Spacer(Modifier.height(6.dp))
        ActionRow(label = "同期を要求", onClick = onRequestSync)
        SectionTitle("スレ")
        if (threads.isEmpty()) {
            MutedText("履歴なし")
        } else {
            threads.forEach { thread ->
                ThreadRow(thread = thread, nowMillis = nowMillis, onClick = { onThreadSelected(thread) })
            }
        }
    }
}

@Composable
private fun ThreadDetailView(
    thread: WatchThreadSummary,
    nowMillis: Long,
    isPhoneReachable: Boolean?,
    statusMessage: String?,
    onBack: () -> Unit,
    onRequestSync: () -> Unit,
    onOpenThreadOnPhone: (WatchThreadSummary) -> Unit,
    onStartReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onPauseReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onStopReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onNextReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onPreviousReadAloudOnPhone: (WatchThreadSummary) -> Unit
) {
    WatchScreen {
        TopBackRow(title = thread.boardName, onBack = onBack)
        Text(
            text = thread.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "レス ${thread.replyCount} / 新着 ${thread.newReplyCount}",
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = thread.boardName,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        WatchStatusText(
            connectionLabel = buildPhoneConnectionLabel(isPhoneReachable),
            statusMessage = statusMessage
        )
        thread.freshReadAloudStatus(nowMillis)?.let { status ->
            Text(
                text = buildReadAloudStatusLabel(
                    stateName = status.state.name,
                    postId = status.postId,
                    currentIndex = status.currentIndex,
                    totalPosts = status.totalPosts
                ),
                fontSize = 11.sp,
                color = Color(0xFF8ED6A5),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        ActionRow(label = "スマホで開く", onClick = { onOpenThreadOnPhone(thread) })
        Spacer(Modifier.height(6.dp))
        ActionRow(label = "同期を要求", onClick = onRequestSync)
        SectionTitle("読み上げ")
        ControlRow(
            icon = "▶",
            label = "再生 / 再開",
            backgroundColor = Color(0xFF245C34),
            onClick = { onStartReadAloudOnPhone(thread) }
        )
        ControlRow(
            icon = "<",
            label = "前へ",
            backgroundColor = Color(0xFF333F51),
            onClick = { onPreviousReadAloudOnPhone(thread) }
        )
        ControlRow(
            icon = ">",
            label = "次へ",
            backgroundColor = Color(0xFF333F51),
            onClick = { onNextReadAloudOnPhone(thread) }
        )
        ControlRow(
            icon = "Ⅱ",
            label = "一時停止",
            backgroundColor = Color(0xFF3D4B62),
            onClick = { onPauseReadAloudOnPhone(thread) }
        )
        ControlRow(
            icon = "■",
            label = "停止",
            backgroundColor = Color(0xFF6A2D2D),
            onClick = { onStopReadAloudOnPhone(thread) }
        )
        SectionTitle("プレビュー")
        if (thread.previewPosts.isEmpty()) {
            MutedText("保存済みプレビューなし")
        } else {
            thread.previewPosts.forEach { post ->
                PreviewPostRow(post)
            }
        }
    }
}

@Composable
private fun WatchScreen(
    content: @Composable ColumnScopeMarker.() -> Unit
) {
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val horizontalPadding = if (configuration.isScreenRound) 30.dp else 18.dp
    val verticalPadding = if (configuration.isScreenRound) 30.dp else 18.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            ColumnScopeMarker.content()
        }
        ScrollIndicator(
            state = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

private object ColumnScopeMarker

@Composable
private fun TopBackRow(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B2B2B))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "<", fontSize = 14.sp)
        }
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun BoardRow(
    board: WatchBoard,
    snapshot: WatchSnapshot,
    onClick: () -> Unit
) {
    val threadCount = snapshot.threads.count { it.boardId == board.id }
    val unreadCount = snapshot.threads.count { it.boardId == board.id && it.newReplyCount > 0 }
    RowCard(onClick = onClick) {
        Text(
            text = board.name,
            fontSize = 13.sp,
            fontWeight = if (board.pinned) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (unreadCount > 0) "${threadCount}スレ / +$unreadCount" else "${threadCount}スレ",
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ThreadRow(
    thread: WatchThreadSummary,
    nowMillis: Long,
    onClick: () -> Unit
) {
    RowCard(onClick = onClick) {
        Text(
            text = thread.title,
            fontSize = 13.sp,
            fontWeight = if (thread.isWatchWordMatch) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${thread.boardName} / ${thread.replyCount}レス / +${thread.newReplyCount}",
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (thread.isWatchWordMatch) {
            Text(
                text = "監視ワード",
                fontSize = 10.sp,
                color = Color(0xFFFFD56A),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        thread.freshReadAloudStatus(nowMillis)?.let { status ->
            Text(
                text = buildReadAloudStatusLabel(
                    stateName = status.state.name,
                    postId = status.postId,
                    currentIndex = status.currentIndex,
                    totalPosts = status.totalPosts
                ),
                fontSize = 10.sp,
                color = Color(0xFF8ED6A5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PreviewPostRow(post: WatchPostPreview) {
    RowCard(onClick = {}) {
        Text(
            text = "No.${post.postId}",
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = post.text,
            fontSize = 12.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WatchStatusText(
    connectionLabel: String,
    statusMessage: String?
) {
    Text(
        text = connectionLabel,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    statusMessage?.let { message ->
        Text(
            text = message,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RowCard(
    onClick: () -> Unit,
    content: @Composable ColumnScopeMarker.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF181818))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        ColumnScopeMarker.content()
    }
}

@Composable
private fun ControlRow(
    icon: String,
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF245C34))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun MutedText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

private fun formatSnapshotGeneratedAt(epochMillis: Long): String {
    if (epochMillis <= 0L) return "--:--"
    return SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date(epochMillis))
}

private fun buildReadAloudStatusLabel(
    stateName: String,
    postId: String?,
    currentIndex: Int,
    totalPosts: Int
): String {
    val stateLabel = when (stateName) {
        "Speaking" -> "読み上げ中"
        "Paused" -> "一時停止中"
        else -> "読み上げ"
    }
    val postLabel = postId?.let { "No.$it" } ?: "${currentIndex + 1}/$totalPosts"
    return "$stateLabel $postLabel"
}

private fun buildSnapshotSyncLabel(
    generatedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val timeLabel = formatSnapshotGeneratedAt(generatedAtMillis)
    val ageMillis = nowMillis - generatedAtMillis
    return if (generatedAtMillis <= 0 || ageMillis !in 0..WATCH_SNAPSHOT_STALE_AGE_MILLIS) {
        "同期古い $timeLabel"
    } else {
        "同期 $timeLabel"
    }
}

private fun buildPhoneConnectionLabel(isPhoneReachable: Boolean?): String {
    return when (isPhoneReachable) {
        true -> "スマホ接続中"
        false -> "キャッシュ表示"
        null -> "接続確認中"
    }
}

private fun WatchThreadSummary.freshReadAloudStatus(
    nowMillis: Long = System.currentTimeMillis()
): WatchReadAloudStatus? {
    val status = readAloudStatus ?: return null
    val ageMillis = nowMillis - status.updatedAtMillis
    return status.takeIf {
        status.updatedAtMillis > 0 &&
            ageMillis in 0..WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
    }
}

private fun WatchThreadSummary.toWearThreadKey(): WearThreadKey =
    WearThreadKey(boardId = boardId, threadId = threadId)

private data class WearThreadKey(
    val boardId: String,
    val threadId: String
)
