package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 二画面（原作 CatalogOrTablet）：見出し・本文・下部バーを同じ比率で左右に並べる。既定は左がカタログ、右がスレッド。
 * [leftPercent]は左側の割合（入れ替えても左基準）。区切り線は1dp。タブ帯は全幅に1本。
 */
@Composable
internal fun FtutterTwoPane(
    settings: FtutterTabletSettings,
    catalog: @Composable (strip: @Composable () -> Unit) -> Unit,
    thread: (@Composable (strip: @Composable () -> Unit) -> Unit)?,
    tabStrip: @Composable () -> Unit
) {
    val colors = LocalFtutterColors.current
    val noStrip: @Composable () -> Unit = {}
    val threadPane: @Composable () -> Unit = {
        if (thread != null) thread(noStrip) else {
            Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
                Text("スレッドを選んでください", color = colors.text, modifier = Modifier.testTag("ftutter-two-pane-empty"))
            }
        }
    }
    val left = settings.leftPercent.coerceIn(FTUTTER_TABLET_PERCENT_RANGE).toFloat()
    Column(Modifier.fillMaxSize().testTag("ftutter-two-pane")) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(left).fillMaxHeight()) {
                if (settings.threadOnLeft) threadPane() else catalog(noStrip)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outline))
            Box(Modifier.weight(100f - left).fillMaxHeight()) {
                if (settings.threadOnLeft) catalog(noStrip) else threadPane()
            }
        }
        tabStrip()
    }
}

@Composable
private fun FtutterCheckRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val colors = LocalFtutterColors.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, color = colors.text, fontSize = 15.sp)
    }
}

@Composable
private fun FtutterSectionTitle(text: String) {
    val colors = LocalFtutterColors.current
    Text(text, color = colors.text, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

/**
 * ジェスチャーの設定（原作 SettingGesture）：有効、「前へ」で戻る（カタログ・スレ）、ジェスチャーが無効の時のスワイプ、
 * 26の軌跡の割り当て（19択）、割り当てのリセット（確認あり）。
 */
@Composable
internal fun FtutterGestureSettingsScreen(
    preferences: Map<String, String>,
    savePreference: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalFtutterColors.current
    val settings = remember(preferences) { parseFtutterGestureSettings(preferences) }
    var choosing by remember { mutableStateOf<String?>(null) }
    var resetting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("ftutter-gesture-settings")) {
        FtutterTopBar(title = "ジェスチャー", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                FtutterSectionTitle("ジェスチャー")
                FtutterCheckRow("ジェスチャーを有効にする", settings.enabled, "ftutter-gesture-enabled") {
                    savePreference(FtutterKeys.USE_GESTURE, it.toString())
                }
                FtutterCheckRow("スワイプで隣のタブへ移動する（※ジェスチャー無効時）", settings.moveTabBySwipe, "ftutter-gesture-swipe") {
                    savePreference(FtutterKeys.MOVE_TAB_BY_SWIPE, it.toString())
                }
                FtutterSectionTitle("「前へ」で戻る")
                FtutterCheckRow("左にタブがない時に戻る（カタログ）", settings.backByPreviousInCatalog, "ftutter-gesture-back-catalog") {
                    savePreference(FtutterKeys.BACK_BY_PREVIOUS_CATALOG, it.toString())
                }
                FtutterCheckRow("左にタブがない時に戻る（スレ）", settings.backByPreviousInThread, "ftutter-gesture-back-thread") {
                    savePreference(FtutterKeys.BACK_BY_PREVIOUS_THREAD, it.toString())
                }
                FtutterSliderSetting("方向を切り替える距離", FtutterKeys.GESTURE_SENSITIVITY, 0.1, 1000.0, 0.1, 20.0, preferences, savePreference, integer = false)
                FtutterSectionTitle("割り当て")
                Text(
                    "割り当てのリセット", color = colors.text, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clickable { resetting = true }.padding(16.dp).testTag("ftutter-gesture-reset")
                )
                HorizontalDivider(color = colors.outline)
            }
            items(settings.map.entries.toList(), key = { it.key }) { (code, action) ->
                Row(
                    Modifier.fillMaxWidth().clickable { choosing = code }.padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("ftutter-gesture-row-$code"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ftutterGestureArrows(code), color = colors.text, fontSize = 18.sp, modifier = Modifier.width(96.dp))
                    Text(action.label, color = colors.text, fontSize = 15.sp)
                }
                HorizontalDivider(color = colors.outline)
            }
        }
    }
    choosing?.let { code ->
        FtutterChoiceDialog(
            title = ftutterGestureArrows(code),
            choices = FtutterGestureAction.entries.map { it.label },
            onDismiss = { choosing = null }
        ) { index ->
            choosing = null
            savePreference(ftutterGestureKey(code), FtutterGestureAction.entries[index].stored.toString())
        }
    }
    if (resetting) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { resetting = false },
            title = { Text("割り当てを初期設定に戻しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    resetting = false
                    FTUTTER_GESTURE_DEFAULTS.keys.forEach { savePreference(ftutterGestureKey(it), "") }
                }, modifier = Modifier.testTag("ftutter-gesture-reset-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { resetting = false }) { Text("キャンセル") } }
        ) }
    }
}

/**
 * 二画面の設定（原作 Setting_Tablet）：有効、幅の閾値（0〜2000の10刻み。「より広い時」）、左右の割合（原作は0〜100だが、
 * 0で表示が崩れたので20〜80）、左右を入れ替える、列数（縦・横）。
 */
@Composable
internal fun FtutterTabletSettingsScreen(
    preferences: Map<String, String>,
    savePreference: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalFtutterColors.current
    val settings = remember(preferences) { parseFtutterTabletSettings(preferences) }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("ftutter-tablet-settings")) {
        FtutterTopBar(title = "二画面", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                FtutterCheckRow("二画面を有効にする", settings.enabled, "ftutter-tablet-enabled") {
                    savePreference(FtutterKeys.USE_TABLET, it.toString())
                }
                FtutterCheckRow("左右を入れ替える", settings.threadOnLeft, "ftutter-tablet-swap") {
                    savePreference(FtutterKeys.THREAD_ON_LEFT, it.toString())
                }
                FtutterSectionTitle("画面の幅が ${settings.minWidth} より広い時に二画面にする")
                var width by remember(settings.minWidth) { mutableStateOf(settings.minWidth.toFloat()) }
                Slider(
                    value = width, onValueChange = { width = it }, valueRange = 0f..2000f, steps = 199,
                    onValueChangeFinished = { savePreference(FtutterKeys.TABLET_MIN_WIDTH, ((width / 10).toInt() * 10).toString()) },
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("ftutter-tablet-width")
                )
                FtutterSectionTitle("左右の割合（左 ${settings.leftPercent}%）")
                var ratio by remember(settings.leftPercent) { mutableStateOf(settings.leftPercent.toFloat()) }
                Slider(
                    value = ratio, onValueChange = { ratio = it },
                    valueRange = FTUTTER_TABLET_PERCENT_RANGE.first.toFloat()..FTUTTER_TABLET_PERCENT_RANGE.last.toFloat(),
                    steps = FTUTTER_TABLET_PERCENT_RANGE.last - FTUTTER_TABLET_PERCENT_RANGE.first - 1,
                    onValueChangeFinished = { savePreference(FtutterKeys.DIVIDE_PERCENTAGE, ratio.toInt().toString()) },
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("ftutter-tablet-ratio")
                )
                FtutterSectionTitle("カタログの列数（縦 ${settings.columnsPortrait}・横 ${settings.columnsLandscape}）")
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("縦", color = colors.text)
                    TextButton(onClick = { savePreference(FtutterKeys.TABLET_ROW_PORTRAIT, (settings.columnsPortrait - 1).coerceAtLeast(1).toString()) }) { Text("−") }
                    TextButton(onClick = { savePreference(FtutterKeys.TABLET_ROW_PORTRAIT, (settings.columnsPortrait + 1).coerceAtMost(20).toString()) }) { Text("＋") }
                    Text("横", color = colors.text, modifier = Modifier.padding(start = 16.dp))
                    TextButton(onClick = { savePreference(FtutterKeys.TABLET_ROW_LANDSCAPE, (settings.columnsLandscape - 1).coerceAtLeast(1).toString()) }) { Text("−") }
                    TextButton(onClick = { savePreference(FtutterKeys.TABLET_ROW_LANDSCAPE, (settings.columnsLandscape + 1).coerceAtMost(20).toString()) }) { Text("＋") }
                }
            }
        }
    }
}
