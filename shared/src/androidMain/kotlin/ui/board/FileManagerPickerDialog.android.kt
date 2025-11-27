package com.valoser.futacha.shared.ui.board

/**
 * ファイラーアプリ選択ダイアログ（Android実装）
 * デバイスにインストールされているファイラーアプリの一覧を表示し、
 * ユーザーが選択したファイラーを優先ファイラーとして設定できる
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.valoser.futacha.shared.util.FileManagerApp
import com.valoser.futacha.shared.util.getAvailableFileManagers

@Composable
actual fun FileManagerPickerDialog(
    onDismiss: () -> Unit,
    onFileManagerSelected: (packageName: String, label: String) -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val fileManagers = remember {
        getAvailableFileManagers(packageManager)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ファイラーアプリを選択") },
        text = {
            if (fileManagers.isEmpty()) {
                Text("利用可能なファイラーアプリが見つかりませんでした。")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(fileManagers) { fileManager ->
                        FileManagerItem(
                            fileManager = fileManager,
                            onClick = {
                                onFileManagerSelected(fileManager.packageName, fileManager.label)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun FileManagerItem(
    fileManager: FileManagerApp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fileManager.icon?.let { icon ->
                Image(
                    bitmap = icon.toBitmap(48, 48).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
            Column {
                Text(
                    text = fileManager.label,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = fileManager.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
