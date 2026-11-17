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
import com.valoser.futacha.shared.version.UpdatePromptStyle
import com.valoser.futacha.shared.util.rememberUrlLauncher

@Composable
fun UpdateNotificationDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val openUrl = rememberUrlLauncher()
    val updateUrl = updateInfo.updateUrl?.takeIf { it.isNotBlank() }
    val isImmediate = updateUrl != null && updateInfo.promptStyle == UpdatePromptStyle.IMMEDIATE
    AlertDialog(
        onDismissRequest = {
            if (!isImmediate) {
                AnalyticsTracker.uiControl("update_notification", "更新通知を閉じる")
                onDismiss()
            }
        },
        title = {
            Text("アップデートのお知らせ")
        },
        text = {
            Text(
                text = if (isImmediate) {
                    "${updateInfo.message}\n\nこのバージョンは更新が必要です。App Storeで最新版へ更新してください。"
                } else {
                    updateInfo.message
                },
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = {
                AnalyticsTracker.uiControl("update_notification", "更新通知を確認")
                if (updateUrl != null) {
                    openUrl(updateUrl)
                    if (!isImmediate) onDismiss()
                } else {
                    onDismiss()
                }
            }) {
                Text(if (updateUrl != null) "App Storeで更新" else "OK")
            }
        },
        dismissButton = if (updateUrl != null && !isImmediate) {
            {
                TextButton(onClick = {
                    AnalyticsTracker.uiControl("update_notification", "更新通知を後回し")
                    onDismiss()
                }) {
                    Text("後で")
                }
            }
        } else {
            null
        }
    )
}
