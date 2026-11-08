package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import kotlin.math.roundToInt

/** 色相（0〜360）・彩度・明度（0〜1）。原作の色の面と色相の帯の位置。 */
internal data class FtutterHsv(val hue: Float, val saturation: Float, val value: Float, val alpha: Float = 1f) {
    fun toArgb(): Long = (Color.hsv(hue.coerceIn(0f, 360f) % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
        .toArgb().toLong() and 0xFFFFFFFFL)
}

/** ARGB の数から色相・彩度・明度へ（色の面の印の位置）。 */
internal fun ftutterHsvOf(argb: Long): FtutterHsv {
    val a = ((argb ushr 24) and 0xFF) / 255f
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val hue = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return FtutterHsv(hue, if (max == 0f) 0f else d / max, max, a)
}

/** RGBA の欄に出す数（R・G・B は 0〜255、A は %）。 */
internal fun ftutterRgbaLabels(argb: Long): List<String> = listOf(
    ((argb ushr 16) and 0xFF).toString(), ((argb ushr 8) and 0xFF).toString(), (argb and 0xFF).toString(),
    "${(((argb ushr 24) and 0xFF) * 100 / 255.0).roundToInt()}%"
)

/**
 * M51：原作の色個別設定の色を選ぶダイアログ。題と「RGB」「テーマカラー」の切り替え、
 * RGB は色の面・色相の帯・透明度の帯・選んだ色の丸と R・G・B・A の数、テーマカラーは今の配色の19色。
 * ボタンは「リセット」「OK」だけで、戻るは保存せずに閉じる。
 */
@Composable
internal fun FtutterColorPickerDialog(title: String, initial: Color, onReset: () -> Unit, onOk: (Long) -> Unit, onDismiss: () -> Unit) {
    var hsv by remember { mutableStateOf(ftutterHsvOf(initial.toArgb().toLong() and 0xFFFFFFFFL)) }
    var themeTab by remember { mutableStateOf(false) }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.testTag("ftutter-color-picker"), horizontalAlignment = Alignment.CenterHorizontally) {
                FtutterPickerTabs(themeTab) { themeTab = it }
                Spacer(Modifier.height(16.dp))
                if (themeTab) FtutterThemeColorSwatches { hsv = ftutterHsvOf(it) }
                else FtutterRgbPicker(hsv) { hsv = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(hsv.toArgb()) }, modifier = Modifier.testTag("ftutter-color-ok")) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onReset, modifier = Modifier.testTag("ftutter-color-reset")) { Text("リセット") }
        }
    ) }
}

@Composable
private fun FtutterPickerTabs(themeTab: Boolean, onSelect: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.background(scheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(2.dp)) {
        listOf(false to "RGB", true to "テーマカラー").forEach { (theme, label) ->
            val selected = theme == themeTab
            Box(
                Modifier.width(110.dp).background(if (selected) scheme.surface else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(theme) }.padding(vertical = 6.dp)
                    .testTag(if (theme) "ftutter-color-tab-theme" else "ftutter-color-tab-rgb"),
                contentAlignment = Alignment.Center
            ) { Text(label, color = scheme.onSurface, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun FtutterRgbPicker(hsv: FtutterHsv, onChange: (FtutterHsv) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val current = hsv.toArgb()
    // 色の面（横が彩度、縦が明度）。
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).testTag("ftutter-color-plane")) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        fun pick(p: Offset) = onChange(hsv.copy(saturation = ftutterPickerFraction(p.x, w), value = 1f - ftutterPickerFraction(p.y, h)))
        Canvas(Modifier.matchParentSize()
            .pointerInput(hsv.hue, hsv.alpha) { detectTapGestures { pick(it) } }
            .pointerInput(hsv.hue, hsv.alpha) { detectDragGestures { change, _ -> pick(change.position) } }) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv.hue % 360f, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            drawCircle(Color.Black, radius = 14.dp.toPx(), center = Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height),
                style = Stroke(1.5.dp.toPx()))
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).background(Color(current.toInt()), CircleShape).border(1.dp, scheme.outlineVariant, CircleShape)
            .testTag("ftutter-color-preview"))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            FtutterPickerBar("ftutter-color-hue", hsv.hue / 360f,
                Brush.horizontalGradient((0..6).map { Color.hsv((it * 60f) % 360f, 1f, 1f) })) { onChange(hsv.copy(hue = it * 360f)) }
            Spacer(Modifier.height(16.dp))
            FtutterPickerBar("ftutter-color-alpha", hsv.alpha,
                Brush.horizontalGradient(listOf(Color(current.toInt()).copy(alpha = 0f), Color(current.toInt()).copy(alpha = 1f))), checker = true) {
                onChange(hsv.copy(alpha = it))
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        Text("RGB", color = scheme.onSurface, fontSize = 17.sp)
        listOf("R", "G", "B", "A").zip(ftutterRgbaLabels(current)).forEach { (name, number) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("ftutter-color-$name")) {
                Text(name, color = scheme.onSurface, fontSize = 17.sp)
                Text(number, color = scheme.onSurface, fontSize = 15.sp)
            }
        }
    }
}

/** 色相・透明度の帯。押した所・動かした所の割合（0〜1）を返す。 */
@Composable
private fun FtutterPickerBar(tag: String, fraction: Float, brush: Brush, checker: Boolean = false, onChange: (Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp).testTag(tag)) {
        val w = constraints.maxWidth.toFloat()
        // The gestures outlive this composition: they call the latest onChange (which holds the latest saturation and
        // value; the first one put them back to the values at opening) and the latest width (audit C3).
        val latestOnChange by androidx.compose.runtime.rememberUpdatedState(onChange)
        val latestWidth by androidx.compose.runtime.rememberUpdatedState(w)
        Canvas(Modifier.matchParentSize()
            .pointerInput(Unit) { detectTapGestures { latestOnChange(ftutterPickerFraction(it.x, latestWidth)) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> latestOnChange(ftutterPickerFraction(change.position.x, latestWidth)) } }) {
            val barHeight = 10.dp.toPx()
            val top = (size.height - barHeight) / 2
            if (checker) {
                val cell = barHeight / 2
                var x = 0f
                var i = 0
                while (x < size.width) {
                    drawRect(if (i % 2 == 0) Color.LightGray else Color.White, Offset(x, top), androidx.compose.ui.geometry.Size(cell, cell))
                    drawRect(if (i % 2 == 0) Color.White else Color.LightGray, Offset(x, top + cell), androidx.compose.ui.geometry.Size(cell, cell))
                    x += cell; i++
                }
            }
            drawRoundRect(brush, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, barHeight),
                androidx.compose.ui.geometry.CornerRadius(barHeight / 2))
            val center = Offset(fraction.coerceIn(0f, 1f) * size.width, size.height / 2)
            drawCircle(Color.White, radius = size.height / 2, center = center)
            drawCircle(Color.LightGray, radius = size.height / 2, center = center, style = Stroke(1.dp.toPx()))
        }
    }
}

/** 原作の「テーマカラー」：今の配色の役割の19色（4列、最後の行は左が空き）。 */
@Composable
private fun FtutterThemeColorSwatches(onPick: (Long) -> Unit) {
    val s = MaterialTheme.colorScheme
    val rows = listOf(
        listOf(s.primary, s.onPrimary, s.primaryContainer, s.onPrimaryContainer),
        listOf(s.secondary, s.onSecondary, s.secondaryContainer, s.onSecondaryContainer),
        listOf(s.tertiary, s.onTertiary, s.tertiaryContainer, s.onTertiaryContainer),
        listOf(s.surface, s.onSurface, s.surfaceVariant, s.onSurfaceVariant),
        listOf(null, s.outline, s.outlineVariant, s.inversePrimary)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, color ->
                    if (color == null) Spacer(Modifier.size(56.dp))
                    else Box(
                        Modifier.size(56.dp).background(color, RoundedCornerShape(10.dp))
                            .clickable { onPick(color.toArgb().toLong() and 0xFFFFFFFFL) }
                            .semantics { contentDescription = "テーマカラー ${r * 4 + c + 1}" }
                            .testTag("ftutter-theme-color-${r * 4 + c}")
                    )
                }
            }
        }
    }
}

/** The share of [length] that [position] is at, 0 to 1. A bar of no width gives 0, not NaN (audit C3). */
internal fun ftutterPickerFraction(position: Float, length: Float): Float =
    if (length <= 0f || position.isNaN()) 0f else (position / length).coerceIn(0f, 1f)
