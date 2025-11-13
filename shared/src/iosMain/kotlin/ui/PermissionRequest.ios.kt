package com.valoser.futacha.shared.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * iOSではストレージパーミッションが不要なため、保存先を案内するダイアログを表示する。
 */
@Composable
actual fun RequestStoragePermission(
    onPermissionResult: (Boolean) -> Unit
) {
    val dialogVisible = remember { mutableStateOf(true) }

    if (dialogVisible.value) {
        AlertDialog(
            onDismissRequest = { dialogVisible.value = false },
            title = { Text("データ保存について") },
            text = {
                Text(
                    "iOS版はこのアプリのドキュメントディレクトリにデータを保存します。特別な許可は不要ですが、" +
                        "保存後はファイルアプリなどから `Documents/futacha` 配下を確認できます。"
                )
            },
            confirmButton = {
                TextButton(onClick = { dialogVisible.value = false }) {
                    Text("了解")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogVisible.value = false }) {
                    Text("閉じる")
                }
            }
        )
    }

    LaunchedEffect(dialogVisible.value) {
        if (!dialogVisible.value) {
            onPermissionResult(true)
        }
    }
}
