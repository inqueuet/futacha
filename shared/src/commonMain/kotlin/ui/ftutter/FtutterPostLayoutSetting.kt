package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

/** レスのレイアウトの6つ（行の値の名前・ダイアログの名前・保存の鍵・既定）。 */
internal data class FtutterPostLayoutRow(val rowLabel: String, val dialogLabel: String, val key: String, val default: Int)

internal val FTUTTER_POST_LAYOUT_ROWS: List<FtutterPostLayoutRow> get() = listOf(
    FtutterPostLayoutRow("上", "上", FtutterKeys.PADDING_TOP, 5),
    FtutterPostLayoutRow("左", "左", FtutterKeys.PADDING_LEFT, 5),
    FtutterPostLayoutRow("右", "右", FtutterKeys.PADDING_RIGHT, 5),
    FtutterPostLayoutRow("下", "下", FtutterKeys.PADDING_BOTTOM, 5),
    FtutterPostLayoutRow("ヘッダーと本文の間", "ヘッダー", FtutterKeys.PADDING_HEADER_TEXT, 3),
    FtutterPostLayoutRow("本文と画像の間", "画像", FtutterKeys.PADDING_TEXT_FILE, 8)
)

internal const val FTUTTER_POST_LAYOUT_MAX = 30

/** 保存した値（0〜30の整数、壊れた値は既定）。 */
internal fun ftutterPostLayoutValue(preferences: Map<String, String>, row: FtutterPostLayoutRow): Int =
    preferences[row.key]?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()?.takeIf { it in 0..FTUTTER_POST_LAYOUT_MAX } ?: row.default

/** 行の値の文（原作の「上：5」の並び、改行で区切る）。 */
internal fun ftutterPostLayoutSummary(preferences: Map<String, String>): String =
    FTUTTER_POST_LAYOUT_ROWS.joinToString("\n") { "${it.rowLabel}：${ftutterPostLayoutValue(preferences, it)}" }

/**
 * M52：原作の「レスのレイアウト」は1行で、押すと見本のレスと6本のスライダー（0〜30）、「リセット」「OK」のダイアログ。
 * 見本はスライダーを動かすとすぐ変わる。保存はOKの時だけ（外を押すと何もしない）。
 */
@Composable
internal fun FtutterPostLayoutSetting(preferences: Map<String, String>, save: (String, String) -> Unit) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("ftutter-setting-post-layout")) {
        Text("レスのレイアウト", color = colors.text, fontSize = 15.sp)
        Text(ftutterPostLayoutSummary(preferences), color = colors.text, fontSize = 13.sp)
    }
    HorizontalDivider(color = colors.outline)
    if (!open) return
    val values = remember { mutableStateListOf(*FTUTTER_POST_LAYOUT_ROWS.map { ftutterPostLayoutValue(preferences, it) }.toTypedArray()) }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("レスのレイアウト") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                HorizontalDivider()
                FtutterPostLayoutPreview(values[0], values[1], values[2], values[3], values[4], values[5])
                HorizontalDivider()
                FTUTTER_POST_LAYOUT_ROWS.forEachIndexed { i, row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.dialogLabel, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
                        Slider(
                            value = values[i].toFloat(), onValueChange = { values[i] = it.toInt().coerceIn(0, FTUTTER_POST_LAYOUT_MAX) },
                            valueRange = 0f..FTUTTER_POST_LAYOUT_MAX.toFloat(), steps = FTUTTER_POST_LAYOUT_MAX - 1,
                            modifier = Modifier.weight(1f).testTag("ftutter-post-layout-${row.key.removePrefix(FtutterKeys.PREFIX)}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                open = false
                FTUTTER_POST_LAYOUT_ROWS.forEachIndexed { i, row -> save(row.key, values[i].toString()) }
            }, modifier = Modifier.testTag("ftutter-post-layout-ok")) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = { FTUTTER_POST_LAYOUT_ROWS.forEachIndexed { i, row -> values[i] = row.default } },
                modifier = Modifier.testTag("ftutter-post-layout-reset")) { Text("リセット") }
        }
    ) }
}

/** 見本のレス（原作のダイアログと同じ文）。余白は dp で描く。 */
@Composable
private fun FtutterPostLayoutPreview(top: Int, left: Int, right: Int, bottom: Int, header: Int, image: Int) {
    val colors = LocalFtutterColors.current
    Column(Modifier.fillMaxWidth().padding(start = left.dp, end = right.dp, top = top.dp, bottom = bottom.dp).testTag("ftutter-post-layout-preview")) {
        Text("0 無念 としあき 23/08/31(木)12:00:00 No.1234567890", color = colors.text.copy(alpha = 1f), fontSize = 12.sp)
        Text("そうだねx15", color = Color(0xFFD9443A), fontSize = 12.sp)
        Column(Modifier.padding(top = header.dp)) {
            Text(">テストテストテストテストテスト", color = Color(0xFF6E8F0A), fontSize = 14.sp)
            Text("テスト".repeat(18), color = colors.text, fontSize = 14.sp)
        }
        Box(Modifier.padding(top = image.dp).size(90.dp).background(Color(0xFFEECCD0)), contentAlignment = Alignment.Center) {
            Text("画像", color = Color(0xFF202020), fontSize = 13.sp)
        }
    }
}
