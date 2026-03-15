package com.valoser.futacha.shared.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.version.UpdateInfo

/**
 * アプリ更新通知ダイアログ
 */
private fun buildUpdateNotificationMessage(updateInfo: UpdateInfo): String {
    return buildString {
        append(updateInfo.message)
        append("\n\n")
        append("今後のアップデートで広告を有効化する予定です。")
        append("\n")
        append("広告表示は設定からいつでもOFFにできます。")
        append("\n")
        append("広告収益はサーバ運用費や開発費に充てますので、協力いただける方はよろしくおねがいします。")
    }
}

@Composable
fun UpdateNotificationDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("お詫びとアップデートのお知らせ")
        },
        text = {
            Text(buildUpdateNotificationMessage(updateInfo))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
