package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatImageCacheUsage
import com.valoser.futacha.shared.compat.formatCompatCacheUsage
import com.valoser.futacha.shared.model.ThreadDisplayMode

@Composable
internal fun CatalogLayoutGuide() {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("catalog-layout-guide"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("カタログの表示方法", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (grid in listOf(true, false)) Column(Modifier.weight(1f)) {
                Text(if (grid) "グリッド：格子状" else "リスト：縦一列")
                Canvas(Modifier.fillMaxWidth().height(64.dp).padding(vertical = 8.dp)) {
                    if (grid) repeat(6) { index ->
                        drawRect(ink, Offset((index % 3) * size.width / 3, (index / 3) * size.height / 2),
                            Size(size.width / 3 - 6.dp.toPx(), size.height / 2 - 4.dp.toPx()))
                    } else repeat(3) { index ->
                        val y = index * size.height / 3
                        drawRect(ink, Offset(0f, y), Size(10.dp.toPx(), 10.dp.toPx()))
                        drawLine(ink, Offset(16.dp.toPx(), y + 5.dp.toPx()),
                            Offset(size.width, y + 5.dp.toPx()), 2.dp.toPx())
                    }
                }
            }
        }
        Text("カタログ画面の「表示の切り替え」で選びます。下の各設定は、その表示方法を使ったときに反映されます。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ThreadDisplayQuickSetting(mode: ThreadDisplayMode, onChange: (ThreadDisplayMode) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("thread-display-quick-setting"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("スレ表示モード", style = MaterialTheme.typography.titleMedium)
        Text("通常は時系列順、ツリーは引用先の下に返信をまとめます。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThreadDisplayMode.entries.forEach { option ->
                FilterChip(selected = mode == option, onClick = { onChange(option) }, label = { Text(option.label) })
            }
        }
    }
}

@Composable
internal fun StorageUsageGuide(
    images: CompatImageCacheUsage?, threadBytes: Long?, attachmentBytes: Long?, autoSaveBytes: Long?,
    onOpenSavedThreads: () -> Unit
) {
    fun size(value: Long?) = value?.let(::formatCompatCacheUsage) ?: "未取得"
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("storage-usage-guide"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("容量の内訳と整理", style = MaterialTheme.typography.titleMedium)
        Text("画像キャッシュ：${size(images?.imageBytes)}\nカタログ画像：${size(images?.catalogBytes)}\nスレッド本文キャッシュ：${size(threadBytes)}\n投稿用の一時添付：${size(attachmentBytes)}\n履歴の自動保存：${size(autoSaveBytes)}")
        Text("画像・本文キャッシュを消すと、次に開くとき再取得します。削除済みのスレッドは再取得できない場合があります。")
        Text("履歴の自動保存は、履歴を削除すると関連データも消えます。一時添付を消すと、下書きの画像を選び直す必要があります。")
        Text("手動で保存したスレッド・画像は別に残ります。OSの使用量には設定やデータベースなども含まれるため、この内訳の合計とは一致しません。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onOpenSavedThreads) { Text("保存済みスレッドを確認") }
    }
}
