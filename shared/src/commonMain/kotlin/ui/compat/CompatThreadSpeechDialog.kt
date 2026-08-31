package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.toCompatPlainText

/**
 * Full-width, titleless speech surface used by old.apk and 1.apk. Closing the
 * surface is the stop operation; the reference dialog has no extra player row.
 */
@Composable
internal fun CompatThreadSpeechDialog(
    post: CompatPostSnapshot?,
    message: String?,
    fontSize: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .offset(y = 35.dp)
                    .testTag("compat-thread-speech-dialog")
                    .clickable(onClick = {}),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp
            ) {
                if (message != null) {
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth().padding(15.dp),
                        fontSize = fontSize.sp
                    )
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = post?.compatSpeechHeader().orEmpty(),
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = (fontSize - 2).coerceAtLeast(8).sp
                        )
                        Text(
                            text = post?.messageHtml?.toCompatPlainText().orEmpty(),
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 10.dp),
                            fontSize = fontSize.sp
                        )
                    }
                }
            }
        }
    }
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
