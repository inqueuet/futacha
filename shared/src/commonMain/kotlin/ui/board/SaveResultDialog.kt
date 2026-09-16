package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A separate dialog keeps the result visible above the full-screen media preview. */
@Composable
internal fun SaveResultDialog(message: String, onDismiss: () -> Unit, onShare: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存結果") },
        text = { Text(message, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        dismissButton = {
            if (onShare != null) TextButton(onClick = onShare) { Text("共有") }
        }
    )
}
