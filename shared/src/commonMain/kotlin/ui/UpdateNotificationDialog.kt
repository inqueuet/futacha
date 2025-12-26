package com.valoser.futacha.shared.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.version.UpdateInfo

/**
 * アプリ更新通知ダイアログ
 */
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
            Text(updateInfo.message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
