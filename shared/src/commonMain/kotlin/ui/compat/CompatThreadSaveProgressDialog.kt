package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SaveProgress

@Composable
fun CompatThreadSaveProgressDialog(
    progress: SaveProgress,
    cancelRequested: Boolean = false,
    onCancel: () -> Unit
) {
    val overall = (progress.getOverallProgressPercentage() / 100f).coerceIn(0f, 1f)
    val current = if (progress.currentItemTotalBytes > 0L) {
        (progress.currentItemBytes.toFloat() / progress.currentItemTotalBytes.toFloat()).coerceIn(0f, 1f)
    } else if (progress.total > 0) {
        (progress.current.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
    } else 0f
    AlertDialog(
        modifier = Modifier.testTag("compat-thread-save-progress-dialog"),
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("スレッドを保存中") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(compatThreadSaveProgressItem(progress, cancelRequested))
                Text("全体の進行状況")
                LinearProgressIndicator(
                    progress = { overall },
                    modifier = Modifier.fillMaxWidth().testTag("compat-save-progress-overall")
                )
                LinearProgressIndicator(
                    progress = { current },
                    modifier = Modifier.fillMaxWidth().testTag("compat-save-progress-current")
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                enabled = !cancelRequested,
                onClick = onCancel,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text("キャンセル")
            }
        }
    )
}

internal fun compatThreadSaveProgressItem(progress: SaveProgress, cancelRequested: Boolean): String =
    if (cancelRequested) "中断しています…" else progress.currentItem.ifBlank { "しばらくお待ち下さい" }

internal fun compatThreadSaveCompletionMessage(savedThread: SavedThread): String = buildString {
    append("保存しました")
    if (savedThread.incompleteMediaCount > 0) {
        append('\n')
        append(savedThread.incompleteMediaCount)
        append("件のメディアを取得できませんでした")
    }
}

internal fun compatThreadSaveCancellationMessage(partiallySavedCount: Int = 0): String = buildString {
    append("キャンセルしました")
    if (partiallySavedCount > 0) {
        append('\n')
        append(partiallySavedCount)
        append("件のメディアをここまで保存しました")
    }
}
