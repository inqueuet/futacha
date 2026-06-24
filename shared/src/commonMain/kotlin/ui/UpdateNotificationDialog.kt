package com.valoser.futacha.shared.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.version.UpdateInfo

/**
 * アプリ更新通知ダイアログ
 */
private fun buildUpdateNotificationMessage(updateInfo: UpdateInfo): String {
    return updateInfo.message
}

@Composable
fun UpdateNotificationDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("アップデートのお知らせ")
        },
        text = {
            Text(
                text = buildUpdateNotificationMessage(updateInfo),
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
