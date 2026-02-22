package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress

/**
 * スレッド保存進捗ダイアログ
 */
@Composable
fun SaveProgressDialog(
    progress: SaveProgress?,
    onDismissRequest: () -> Unit,
    onCancelRequest: (() -> Unit)? = null
) {
    if (progress == null) return

    val isCompleted = progress.phase == SavePhase.FINALIZING && progress.current == progress.total

    AlertDialog(
        onDismissRequest = {
            if (isCompleted) {
                onDismissRequest()
            } else {
                onCancelRequest?.invoke()
            }
        },
        title = {
            Text(text = getPhaseTitle(progress.phase))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 全体進捗パーセンテージ
                val overallPercentage = progress.getOverallProgressPercentage()
                Text(
                    text = "$overallPercentage%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // 進捗バー
                LinearProgressIndicator(
                    progress = { overallPercentage / 100f },
                    modifier = Modifier.fillMaxWidth()
                )

                // フェーズ内の詳細進捗
                if (progress.total > 0) {
                    Text(
                        text = "${progress.current} / ${progress.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 現在の処理内容
                Text(
                    text = progress.currentItem,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            if (isCompleted) {
                TextButton(onClick = onDismissRequest) {
                    Text("閉じる")
                }
            }
        },
        dismissButton = {
            if (!isCompleted && onCancelRequest != null) {
                TextButton(onClick = onCancelRequest) {
                    Text("キャンセル")
                }
            }
        }
    )
}

/**
 * フェーズタイトルを取得
 */
private fun getPhaseTitle(phase: SavePhase): String {
    return when (phase) {
        SavePhase.PREPARING -> "準備中"
        SavePhase.DOWNLOADING -> "ダウンロード中"
        SavePhase.CONVERTING -> "変換中"
        SavePhase.FINALIZING -> "完了処理中"
    }
}
