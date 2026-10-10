package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import kotlin.math.roundToInt

@Composable
internal fun CompatThreadSpeechDialog(
    post: CompatPostSnapshot?,
    message: String?,
    fontSize: Int,
    currentIndex: Int,
    postCount: Int,
    playing: Boolean,
    onSeek: (Int) -> Unit,
    onSeekToVisible: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onDismiss: () -> Unit,
    follow: Boolean = false,
    showThumbnails: Boolean = false,
    onFollowChange: (Boolean) -> Unit = {},
    onThumbnailsChange: (Boolean) -> Unit = {},
    onHide: () -> Unit = {}
) {
    val lastIndex = (postCount - 1).coerceAtLeast(0)
    var seekPosition by remember(currentIndex, postCount) { mutableFloatStateOf(currentIndex.coerceIn(0, lastIndex).toFloat()) }
    FutachaAppLockAwareWindow { AlertDialog(
        modifier = Modifier.testTag("compat-thread-speech-dialog"),
        onDismissRequest = onDismiss,
        title = { Text("読み上げ") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("${if (postCount == 0) 0 else seekPosition.roundToInt() + 1} / $postCount レス")
                Slider(
                    value = seekPosition,
                    onValueChange = { seekPosition = it },
                    onValueChangeFinished = { onSeek(seekPosition.roundToInt()) },
                    valueRange = 0f..lastIndex.coerceAtLeast(1).toFloat(),
                    enabled = postCount > 1,
                    modifier = Modifier.testTag("compat-speech-position")
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onSeek(currentIndex - 1) }, enabled = currentIndex > 0) { Text("前のレス") }
                    TextButton(onClick = { onSeek(currentIndex + 1) }, enabled = currentIndex < lastIndex) { Text("次のレス") }
                }
                Row {
                    Checkbox(follow, onFollowChange)
                    Text("読み上げ中のレスへ追従")
                }
                Row {
                    Checkbox(showThumbnails, onThumbnailsChange)
                    Text("サムネイルを表示")
                }
                TextButton(onClick = onHide) { Text("本文を見ながら続ける") }
                TextButton(onClick = onSeekToVisible, enabled = postCount > 0) { Text("表示中のレスから") }
                message?.let { Text(it, modifier = Modifier.testTag("compat-speech-status")) }
                post?.let {
                    Text(it.compatSpeechHeader(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = (fontSize - 2).coerceAtLeast(8).sp)
                    if (showThumbnails && !it.thumbnailUrl.isNullOrBlank()) coil3.compose.AsyncImage(
                        model = it.thumbnailUrl, imageLoader = com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader.current,
                        contentDescription = "読み上げ中のレスの添付画像", modifier = Modifier.size(120.dp))
                    Text(it.messageHtml.toCompatPlainText(), fontSize = fontSize.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (playing) onPause else onPlay, enabled = postCount > 0) {
                Text(if (playing) "一時停止" else "再開")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("停止して閉じる") } }
    ) }
}

private fun CompatPostSnapshot.compatSpeechHeader(): String = buildString {
    subject?.takeIf(String::isNotBlank)?.let { append(it).append(' ') }
    author?.takeIf(String::isNotBlank)?.let { append(it).append(' ') }
    mail?.takeIf(String::isNotBlank)?.let { append('[').append(it).append("] ") }
    append(timestamp)
    if (isNotEmpty()) append(' ')
    append("No.").append(postNo)
    posterId?.takeIf(String::isNotBlank)?.let { identity ->
        append(' ')
        if (identity.startsWith("ID:") || identity.startsWith("IP:")) append(identity)
        else append("ID:").append(identity)
    }
}

internal fun compatReadAloudReloadTimer(remainingSeconds: Int): String =
    "自動リロードまで${remainingSeconds.coerceAtLeast(0)}秒"
