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
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.version.UpdateInfo

@Composable
fun UpdateNotificationDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            AnalyticsTracker.uiControl("update_notification", "更新通知を閉じる")
            onDismiss()
        },
        title = {
            Text("アップデートのお知らせ")
        },
        text = {
            Text(
                text = updateInfo.message,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = {
                AnalyticsTracker.uiControl("update_notification", "更新通知を確認")
                onDismiss()
            }) {
                Text("OK")
            }
        }
    )
}
