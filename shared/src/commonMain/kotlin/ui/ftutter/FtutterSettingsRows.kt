package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 設定の1画面：上のバーと一覧。 */
@Composable
internal fun FtutterSettingsPage(title: String, tag: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val colors = LocalFtutterColors.current
    Column(Modifier.fillMaxSize().background(colors.background).testTag(tag)) {
        FtutterTopBar(title = title, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize().testTag("$tag-list"), content = content)
    }
}

/** 見出し。 */
internal fun LazyListScope.ftutterSection(text: String) {
    item(key = "section-$text") {
        val colors = LocalFtutterColors.current
        Text(text, color = colors.onToolBarActive, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
    }
}

/** 行の下に出す補足（このモードで効かない項目など）。 */
@Composable
private fun FtutterRowNote(note: String?) {
    if (note == null) return
    val colors = LocalFtutterColors.current
    Text(note, color = colors.text, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
}

/** 押すと次の画面へ進む行。 */
@Composable
internal fun FtutterNavRow(label: String, tag: String, detail: String? = null, onClick: () -> Unit) {
    val colors = LocalFtutterColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        detail?.let { Text(it, color = colors.text, fontSize = 14.sp) }
    }
    HorizontalDivider(color = colors.outline)
}

/** チェックの行（保存は "true"/"false"）。 */
@Composable
internal fun FtutterCheckSetting(
    label: String, key: String, default: Boolean, preferences: Map<String, String>, save: (String, String) -> Unit, note: String? = null
) {
    val colors = LocalFtutterColors.current
    val checked = preferences.ftutterBool(key, default)
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { save(key, (!checked).toString()) }.padding(horizontal = 8.dp).testTag("ftutter-setting-$key"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { save(key, it.toString()) })
            Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        }
        FtutterRowNote(note)
    }
}

/** 選択肢から1つを選ぶ行（原作 RadioDialog）。保存は整数。 */
@Composable
internal fun FtutterChoiceSetting(
    label: String, key: String, options: List<Pair<Int, String>>, default: Int,
    preferences: Map<String, String>, save: (String, String) -> Unit, note: String? = null
) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    val current = preferences[key]?.trim()?.toIntOrNull()?.takeIf { v -> options.any { it.first == v } } ?: default
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(16.dp).testTag("ftutter-setting-$key"), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(options.firstOrNull { it.first == current }?.second.orEmpty(), color = colors.text, fontSize = 14.sp)
        }
        FtutterRowNote(note)
    }
    if (open) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column {
                    options.forEach { (value, text) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open = false; save(key, value.toString()) }.testTag("ftutter-option-$value"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = value == current, onClick = { open = false; save(key, value.toString()) })
                            Text(text)
                        }
                    }
                }
            },
            confirmButton = {}
        ) }
    }
}

/** 数値の行（原作 SliderDialog：最小・最大・刻み・リセット値）。 */
@Composable
internal fun FtutterSliderSetting(
    label: String, key: String, min: Double, max: Double, step: Double, default: Double,
    preferences: Map<String, String>, save: (String, String) -> Unit, integer: Boolean = true, note: String? = null
) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    val current = preferences[key]?.trim()?.toDoubleOrNull()?.takeIf { it in min..max } ?: default
    fun show(v: Double) = if (integer) v.toLong().toString() else ((v * 100).toLong() / 100.0).toString()
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(16.dp).testTag("ftutter-setting-$key"), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(show(current), color = colors.text, fontSize = 14.sp)
        }
        FtutterRowNote(note)
    }
    if (open) {
        var value by remember { mutableStateOf(current.toFloat()) }
        val steps = ((max - min) / step).toInt() - 1
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column {
                    Text(show(value.toDouble()), fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Slider(value = value, onValueChange = { value = it }, valueRange = min.toFloat()..max.toFloat(), steps = steps.coerceAtLeast(0),
                        modifier = Modifier.testTag("ftutter-slider-$key"))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    val snapped = min + kotlin.math.round((value - min) / step) * step
                    save(key, if (integer) snapped.toLong().toString() else snapped.toString())
                }, modifier = Modifier.testTag("ftutter-slider-ok")) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { open = false; save(key, "") }) { Text("リセット") }
                    TextButton(onClick = { open = false }) { Text("キャンセル") }
                }
            }
        ) }
    }
}

/** 色の候補（メインカラーの19色と、白・黒・灰）。 */
internal val FTUTTER_COLOR_CHOICES: List<Long> = FTUTTER_SEED_COLORS.toList() +
    listOf(0xFFFFFFFFL, 0xFFF5F5F5L, 0xFF9E9E9EL, 0xFF616161L, 0xFF212121L, 0xFF000000L)

/** 色の行（原作「色個別設定」）。保存はARGBの数。リセットで保存を消す（テーマの色へ戻る）。 */
@Composable
internal fun FtutterColorSetting(label: String, name: String, shown: Color, preferences: Map<String, String>, save: (String, String) -> Unit) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    val key = FtutterKeys.colorOverride(name)
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("ftutter-color-$name"),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (preferences[key].isNullOrBlank().not()) Text("個別  ", color = colors.text, fontSize = 12.sp)
        Box(Modifier.size(28.dp).background(shown, CircleShape).border(1.dp, colors.outline, CircleShape))
    }
    if (open) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FTUTTER_COLOR_CHOICES.forEach { argb ->
                        Box(
                            Modifier.size(36.dp).background(Color(argb.toInt()), CircleShape).border(1.dp, colors.outline, CircleShape)
                                .clickable { open = false; save(key, argb.toString()) }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false; save(key, "") }) { Text("リセット") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("キャンセル") } }
        ) }
    }
}

/** 複数を選ぶ行（ピックアップを行う並び順）。 */
@Composable
internal fun FtutterMultiChoiceSetting(label: String, tag: String, options: List<String>, selected: Set<Int>, onSave: (Set<Int>) -> Unit) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(16.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (selected.size == options.size) "全て" else options.filterIndexed { i, _ -> i in selected }.joinToString("・"), color = colors.text, fontSize = 14.sp)
    }
    if (open) {
        var chosen by remember { mutableStateOf(selected) }
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column {
                    options.forEachIndexed { i, text ->
                        Row(Modifier.fillMaxWidth().clickable { chosen = if (i in chosen) chosen - i else chosen + i }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = i in chosen, onCheckedChange = null)
                            Text(text)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false; onSave(chosen) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("キャンセル") } }
        ) }
    }
}

/** このモードでまだ使われない設定に付ける補足。 */
internal const val FTUTTER_NOT_USED_NOTE = "このモードでは使われません（原作のバックアップを取り込んだ時のために保存します）"
