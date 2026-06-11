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

    LaunchedEffect(context) {
        WatchSnapshotStore.loadPersisted(context.applicationContext)
        WatchSnapshotStore.loadDataLayerSnapshotAsync(context.applicationContext)
        commandClient.requestSnapshot()
    }

    MaterialTheme {
        FutachaWearContent(
            snapshot = snapshot,
            onRefresh = commandClient::requestRefresh,
            onRequestSync = commandClient::requestSnapshot,
            onStartReadAloudOnPhone = commandClient::startReadAloudOnPhone,
            onPauseReadAloudOnPhone = commandClient::pauseReadAloudOnPhone,
            onStopReadAloudOnPhone = commandClient::stopReadAloudOnPhone
        )
    }
}

@Composable
private fun FutachaWearContent(
    snapshot: WatchSnapshot?,
    onRefresh: () -> Unit,
    onRequestSync: () -> Unit,
    onStartReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onPauseReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onStopReadAloudOnPhone: (WatchThreadSummary) -> Unit
) {
    var selectedThreadId by remember { mutableStateOf<String?>(null) }
    val selectedThread = snapshot?.threads?.firstOrNull { it.threadId == selectedThreadId }
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
            selectedThread != null -> ReadAloudControlView(
                thread = selectedThread,
                nowMillis = nowMillis,
                onBack = { selectedThreadId = null },
                onStartReadAloudOnPhone = onStartReadAloudOnPhone,
                onPauseReadAloudOnPhone = onPauseReadAloudOnPhone,
                onStopReadAloudOnPhone = onStopReadAloudOnPhone
            )
            else -> HomeView(
                snapshot = snapshot,
                nowMillis = nowMillis,
                onThreadSelected = { selectedThreadId = it.threadId },
                onRefresh = onRefresh
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
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        ActionRow(label = "同期を要求", onClick = onRequestSync)
    }
}

@Composable
private fun HomeView(
    snapshot: WatchSnapshot,
    nowMillis: Long,
    onThreadSelected: (WatchThreadSummary) -> Unit,
    onRefresh: () -> Unit
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
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "新着 ${snapshot.unreadTotal} / 監視 ${snapshot.watchMatchTotal}",
            fontSize = 12.sp
        )
        Text(
            text = "$syncLabel / ${snapshot.threads.size}スレ",
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        ActionRow(label = "更新", onClick = onRefresh)
        activeReadAloudThread?.let { thread ->
            SectionTitle("読み上げ中")
            ThreadRow(thread = thread, nowMillis = nowMillis, onClick = { onThreadSelected(thread) })
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
private fun ReadAloudControlView(
    thread: WatchThreadSummary,
    nowMillis: Long,
    onBack: () -> Unit,
    onStartReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onPauseReadAloudOnPhone: (WatchThreadSummary) -> Unit,
    onStopReadAloudOnPhone: (WatchThreadSummary) -> Unit
) {
    WatchScreen {
        TopBackRow(title = "読み上げ", onBack = onBack)
        Text(
            text = thread.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "レス ${thread.replyCount} / 新着 ${thread.newReplyCount}",
            fontSize = 12.sp
        )
        Text(
            text = thread.boardName,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
        SectionTitle("読み上げ")
        ControlRow(
            icon = "▶",
            label = "再生 / 再開",
            backgroundColor = Color(0xFF245C34),
            onClick = { onStartReadAloudOnPhone(thread) }
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
            modifier = Modifier.padding(start = 10.dp)
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
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
