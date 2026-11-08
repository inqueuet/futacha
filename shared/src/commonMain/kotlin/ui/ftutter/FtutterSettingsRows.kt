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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
internal fun FtutterSettingsPage(
    title: String, tag: String, onBack: () -> Unit,
    actions: @Composable () -> Unit = {}, content: LazyListScope.() -> Unit
) {
    val colors = LocalFtutterColors.current
    Column(Modifier.fillMaxSize().background(colors.background).testTag(tag)) {
        FtutterTopBar(title = title, onBack = onBack, actions = actions)
        // Keep the last setting tappable above Android's navigation bar and the
        // iOS home indicator; the edge-to-edge theme only paints their background.
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().testTag("$tag-list"), content = content)
    }
}

/** 見出し。 */
internal fun LazyListScope.ftutterSection(text: String) {
    item(key = "section-$text") {
        val colors = LocalFtutterColors.current
        // M78：原作の節見出しは端から端までの灰色の帯。
        Text(
            text, color = colors.text, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().background(colors.outline.copy(alpha = 0.35f))
                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp)
        )
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
internal fun FtutterNavRow(
    label: String, tag: String, detail: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** 原作の「個別設定」のように、行の右に → を出す。 */
    trailingArrow: Boolean = false, onClick: () -> Unit
) {
    val colors = LocalFtutterColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            androidx.compose.material3.Icon(it, contentDescription = null, tint = colors.text,
                modifier = Modifier.padding(end = 24.dp).size(28.dp))
        }
        Text(label, color = colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        detail?.let { Text(it, color = colors.text, fontSize = 14.sp) }
        if (trailingArrow) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null, tint = colors.text)
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
            // M78：原作は名前が左、チェックが右。
            Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Checkbox(checked = checked, onCheckedChange = { save(key, it.toString()) })
        }
        FtutterRowNote(note)
    }
}

/** 選択肢から1つを選ぶ行（原作 RadioDialog）。保存は整数。 */
@Composable
internal fun FtutterChoiceSetting(
    label: String, key: String, options: List<Pair<Int, String>>, default: Int,
    preferences: Map<String, String>, save: (String, String) -> Unit, note: String? = null,
    /** 選ぶダイアログに出す名前（原作は一覧の名前と違うことがある。null なら [options] と同じ）。 */
    dialogOptions: List<Pair<Int, String>>? = null,
    /** L1：原作の「明るさ」は丸の印も無い、文字だけの行。 */
    plainRows: Boolean = false
) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    val current = preferences[key]?.trim()?.toIntOrNull()?.takeIf { v -> options.any { it.first == v } } ?: default
    val legacy = preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false)
    Column {
        // M78：原作は値を名前の下に小さな字で出す。
        Column(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("ftutter-setting-$key")) {
            Text(label, color = colors.text, fontSize = 15.sp)
            Text(options.firstOrNull { it.first == current }?.second.orEmpty(), color = colors.text, fontSize = 13.sp)
        }
        FtutterRowNote(note)
    }
    if (open) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { open = false },
            // M75：原作の選ぶダイアログは題が無い（従来の動きでは題を出す）。
            title = if (legacy) ({ Text(label) }) else null,
            text = {
                Column(Modifier.testTag("ftutter-choice-dialog")) {
                    (if (legacy) options else dialogOptions ?: options).forEach { (value, text) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open = false; save(key, value.toString()) }.testTag("ftutter-option-$value")
                                .then(if (plainRows && !legacy) Modifier.padding(vertical = 12.dp) else Modifier),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (legacy || !plainRows) RadioButton(selected = value == current, onClick = { open = false; save(key, value.toString()) })
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
    preferences: Map<String, String>, save: (String, String) -> Unit, integer: Boolean = true, note: String? = null,
    /** OK の前に確かめること（値を受け取り、確かめる文を返す。null なら確かめずに保存する）。 */
    confirm: ((Double) -> String?)? = null,
    /** こちらだけの設定（その他の機能）。原作に無いので、数と −／＋ のある前の形のまま。 */
    own: Boolean = false
) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<Double, String>?>(null) }
    var pendingReset by remember { mutableStateOf(false) }
    val current = preferences[key]?.trim()?.toDoubleOrNull()?.takeIf { it in min..max } ?: default
    fun show(v: Double) = if (integer) v.toLong().toString() else ((v * 100).toLong() / 100.0).toString()
    Column {
        Column(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("ftutter-setting-$key")) {
            Text(label, color = colors.text, fontSize = 15.sp)
            Text(show(current), color = colors.text, fontSize = 13.sp)
        }
        FtutterRowNote(note)
    }
    if (open) {
        var value by remember { mutableStateOf(current) }
        // 段が多い時は目盛りを描かない（L21：数千段で狙った値に合わせにくく、目盛りの点も重かった）。範囲・刻みは原作どおり。
        val full = own || preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false)
        // M74：原作の数のダイアログは題と目盛りの無いスライダーだけ（数・−／＋・キャンセルは無い）。
        val steps = if (full) ftutterSliderSteps(min, max, step) else 0
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                if (!full) Slider(value = value.toFloat(), onValueChange = { value = it.toDouble() }, valueRange = min.toFloat()..max.toFloat(),
                    modifier = Modifier.testTag("ftutter-slider-$key"))
                else Column {
                    Text(show(ftutterSliderSnap(value, min, max, step)), fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Slider(value = value.toFloat(), onValueChange = { value = it.toDouble() }, valueRange = min.toFloat()..max.toFloat(), steps = steps,
                        modifier = Modifier.testTag("ftutter-slider-$key"))
                    // 1刻みずつ合わせる。
                    Row(Modifier.align(Alignment.CenterHorizontally)) {
                        TextButton(onClick = { value = ftutterSliderNudge(value, min, max, step, -1) },
                            modifier = Modifier.testTag("ftutter-slider-minus")) { Text("−", fontSize = 20.sp) }
                        TextButton(onClick = { value = ftutterSliderNudge(value, min, max, step, 1) },
                            modifier = Modifier.testTag("ftutter-slider-plus")) { Text("＋", fontSize = 20.sp) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    val snapped = ftutterSliderSnap(value, min, max, step)
                    val question = confirm?.invoke(snapped)
                    pendingReset = false
                    if (question != null) pending = snapped to question
                    else save(key, if (integer) snapped.toLong().toString() else snapped.toString())
                }, modifier = Modifier.testTag("ftutter-slider-ok")) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        open = false
                        val question = confirm?.invoke(default)
                        pendingReset = true
                        if (question != null) pending = default to question else save(key, "")
                    }, modifier = Modifier.testTag("ftutter-slider-reset")) { Text("リセット") }
                    if (full) TextButton(onClick = { open = false }) { Text("キャンセル") }
                }
            }
        ) }
    }
    pending?.let { (snapped, question) ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(label) },
            text = { Text(question) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    save(key, if (pendingReset) "" else if (integer) snapped.toLong().toString() else snapped.toString())
                }, modifier = Modifier.testTag("ftutter-slider-confirm")) { Text("変更する") }
            },
            dismissButton = { TextButton(onClick = { pending = null }, modifier = Modifier.testTag("ftutter-slider-confirm-cancel")) { Text("キャンセル") } }
        ) }
    }
}

/** M74／M75：設定の数・選ぶダイアログを前の形（題・数・−／＋・キャンセル・丸の印）にする。既定OFF。 */
internal val FTUTTER_LEGACY_DIALOGS_KEY: String get() = FtutterKeys.PREFIX + "legacySettingDialogs"
internal const val FTUTTER_LEGACY_DIALOGS_LABEL = "設定のダイアログを前の形にする"
internal const val FTUTTER_LEGACY_DIALOGS_NOTE =
    "数を選ぶダイアログに数と −／＋・キャンセルを、選ぶダイアログに題を出します（原作は出しません）"

/** これより段が多いスライダーは目盛りを描かない連続のものにする（L21）。 */
internal const val FTUTTER_SLIDER_MAX_TICKS = 100

internal fun ftutterSliderSteps(min: Double, max: Double, step: Double): Int {
    val steps = kotlin.math.round((max - min) / step).toLong() - 1
    return if (steps > FTUTTER_SLIDER_MAX_TICKS) 0 else steps.coerceAtLeast(0).toInt()
}

/** 刻みに丸めて範囲に収める。0.1 刻みでも 20.099999… のような値にしない。 */
internal fun ftutterSliderSnap(value: Double, min: Double, max: Double, step: Double): Double {
    val index = kotlin.math.round((value.coerceIn(min, max) - min) / step)
    var scale = 1.0
    while (scale < 1e6 && kotlin.math.abs(step * scale - kotlin.math.round(step * scale)) > 1e-9) scale *= 10
    return (kotlin.math.round((min + index * step) * scale) / scale).coerceIn(min, max)
}

internal fun ftutterSliderNudge(value: Double, min: Double, max: Double, step: Double, delta: Int): Double =
    ftutterSliderSnap(ftutterSliderSnap(value, min, max, step) + delta * step, min, max, step)

/** 色の候補（メインカラーの19色と、白・黒・灰）。 */
internal val FTUTTER_COLOR_CHOICES: List<Long> = FTUTTER_SEED_COLORS.toList() +
    listOf(0xFFFFFFFFL, 0xFFF5F5F5L, 0xFF9E9E9EL, 0xFF616161L, 0xFF212121L, 0xFF000000L)

/** 色の行（原作「色個別設定」）。保存はARGBの数。リセットで保存を消す（テーマの色へ戻る）。 */
@Composable
internal fun FtutterColorSetting(label: String, name: String, shown: Color, preferences: Map<String, String>, save: (String, String) -> Unit) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    val key = FtutterKeys.colorOverride(name)
    val legacy = preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false)
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("ftutter-color-$name"),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (legacy && preferences[key].isNullOrBlank().not()) Text("個別  ", color = colors.text, fontSize = 12.sp)
        // M51：原作は行の右に大きめの丸（約40dp）。
        Box(Modifier.size(if (legacy) 28.dp else 40.dp).background(shown, CircleShape).border(1.dp, colors.outline, CircleShape))
    }
    HorizontalDivider(color = colors.outline)
    if (open && !legacy) {
        FtutterColorPickerDialog(label, shown, onReset = { open = false; save(key, "") },
            onOk = { open = false; save(key, it.toString()) }, onDismiss = { open = false })
    } else if (open) {
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

/** M50：原作の「メインカラー」は1行（名前・「テーマの元となる色」・右に今の色の丸）で、押すと「メインカラー選択」。 */
@Composable
internal fun FtutterMainColorSetting(current: Long, save: (String, String) -> Unit) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("ftutter-setting-main-color"),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("メインカラー", color = colors.text, fontSize = 15.sp)
            Text("テーマの元となる色", color = colors.text, fontSize = 13.sp)
        }
        Box(Modifier.size(40.dp).background(Color(current.toInt()), CircleShape).testTag("ftutter-main-color-current"))
    }
    HorizontalDivider(color = colors.outline)
    if (open) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("メインカラー選択") },
            text = { FtutterMainColorCircles(current, size = 56) { open = false; save(FtutterKeys.MAIN_COLOR, it.toString()) } },
            confirmButton = {}
        ) }
    }
}

/** メインカラーの19色の丸。4列に並べる（原作の「メインカラー選択」）。 */
@Composable
internal fun FtutterMainColorCircles(current: Long, size: Int, columns: Int = 4, onPick: (Long) -> Unit) {
    val colors = LocalFtutterColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FTUTTER_SEED_COLORS.toList().chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { argb ->
                    // 原作は選ばれた色に印を付けない。読み上げのために「選択中」だけ伝える。
                    Box(
                        Modifier.size(size.dp).background(Color(argb.toInt()), CircleShape).clickable { onPick(argb) }
                            .androidxSemanticsSelected(argb == current)
                            .testTag("ftutter-main-color-${argb.toString(16)}")
                    )
                }
            }
        }
    }
}

private fun Modifier.androidxSemanticsSelected(selected: Boolean): Modifier =
    this.then(Modifier.semantics { this.selected = selected })

/**
 * M76：原作の「画像の透過度」は1行で、押すとスライダー1本（5つをまとめて動かす）と「個別設定」の開閉、
 * 「キャンセル」「OK」のダイアログ。保存する値は今までどおり5つ。
 */
@Composable
internal fun FtutterPrivacySetting(preferences: Map<String, String>, save: (String, String) -> Unit) {
    val colors = LocalFtutterColors.current
    var open by remember { mutableStateOf(false) }
    val keys = FTUTTER_PRIVACY_ROWS.map { it.second }
    fun value(key: String) = preferences[key]?.trim()?.toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: 0.8
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("ftutter-setting-privacy")) {
        Text("画像の透過度", color = colors.text, fontSize = 15.sp)
        Text(((value(keys.first()) * 100).toLong() / 100.0).toString(), color = colors.text, fontSize = 13.sp)
    }
    HorizontalDivider(color = colors.outline)
    if (open) {
        val values = remember { androidx.compose.runtime.mutableStateListOf(*keys.map { value(it).toFloat() }.toTypedArray()) }
        var all by remember { mutableStateOf(values.first()) }
        var expanded by remember { mutableStateOf(false) }
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("画像の透過度") },
            text = {
                Column(Modifier.verticalScrollCompat()) {
                    Slider(value = all, onValueChange = { v -> all = v; values.indices.forEach { values[it] = v } }, valueRange = 0f..1f, steps = 19,
                        modifier = Modifier.testTag("ftutter-privacy-all"))
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("ftutter-privacy-each"), verticalAlignment = Alignment.CenterVertically) {
                        Text("個別設定", fontSize = 16.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Icon(
                            if (expanded) androidx.compose.material.icons.Icons.Rounded.KeyboardArrowUp else androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null)
                    }
                    if (expanded) FTUTTER_PRIVACY_ROWS.forEachIndexed { i, (label, key) ->
                        Text(label, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                        Slider(value = values[i], onValueChange = { values[i] = it }, valueRange = 0f..1f, steps = 19,
                            modifier = Modifier.testTag("ftutter-slider-$key"))
                    }
                    HorizontalDivider()
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    keys.forEachIndexed { i, key -> save(key, ftutterSliderSnap(values[i].toDouble(), 0.0, 1.0, 0.05).toString()) }
                }, modifier = Modifier.testTag("ftutter-privacy-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("キャンセル") } }
        ) }
    }
}

/** 画像の透過度の5つ（名前と保存の鍵）。 */
internal val FTUTTER_PRIVACY_ROWS: List<Pair<String, String>> get() = listOf(
    "カタログ" to FtutterKeys.PRIVACY_CATALOG, "カタログ（長押し拡大時）" to FtutterKeys.PRIVACY_CATALOG_ZOOM,
    "スレッド" to FtutterKeys.PRIVACY_THREAD, "スレッド（拡大時）" to FtutterKeys.PRIVACY_THREAD_ZOOM,
    "タブ、ドロワー、他" to FtutterKeys.PRIVACY_TAB_DRAWER
)

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

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
