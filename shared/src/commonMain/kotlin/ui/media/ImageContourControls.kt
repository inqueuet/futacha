@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.valoser.futacha.shared.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.media.edit.EditRegion
import com.valoser.futacha.shared.media.video.model.MosaicMask
import com.valoser.futacha.shared.media.video.model.MosaicMaskTool
import kotlin.math.roundToInt

@Composable
internal fun ImageContourControls(region: EditRegion, enabled: Boolean, modelsAvailable: Boolean,
    tool: MosaicMaskTool, brush: Float, onTool: (MosaicMaskTool) -> Unit, onBrush: (Float) -> Unit,
    onExtract: (Boolean) -> Unit, onReset: () -> Unit, onMargin: (Int) -> Unit, onMarginFinished: () -> Unit) {
    if (modelsAvailable) FlowRow {
        TextButton(enabled = enabled, onClick = { onExtract(false) }, modifier = Modifier.testTag("image-contour-extract")) { Text("選択した枠の輪郭を抽出") }
        TextButton(enabled = enabled, onClick = { onExtract(true) }, modifier = Modifier.testTag("image-contour-extract-all")) { Text("すべての枠の輪郭を抽出") }
    }
    if (region.contourUncertain) Text(
        if (region.contour == null) "輪郭を特定できなかったため、枠全体を隠しています。" else "輪郭が不確実です。隠す範囲を確認して修正してください。",
        modifier = Modifier.testTag("image-contour-uncertain"), style = MaterialTheme.typography.bodySmall)
    FlowRow {
        MosaicMaskTool.entries.forEach { mode ->
            FilterChip(selected = tool == mode, onClick = { onTool(mode) }, enabled = enabled,
                label = { Text(when (mode) { MosaicMaskTool.MOVE -> "枠の移動"; MosaicMaskTool.ADD -> "輪郭を塗る"; MosaicMaskTool.ERASE -> "輪郭を削る" }) },
                modifier = Modifier.testTag("image-contour-tool-${mode.name.lowercase()}"))
        }
        TextButton(enabled = enabled && (region.contour != null || region.contourUncertain), onClick = onReset,
            modifier = Modifier.testTag("image-contour-reset")) { Text("枠全体に戻す") }
    }
    if (tool != MosaicMaskTool.MOVE) {
        Text(if (tool == MosaicMaskTool.ADD) "枠の内側をなぞって隠す範囲を塗り足します。" else "枠の内側をなぞって隠す範囲を削ります。", style = MaterialTheme.typography.bodySmall)
        Text("輪郭の筆の太さ")
        Slider(brush, onValueChange = onBrush, valueRange = .01f.. .2f, enabled = enabled, modifier = Modifier.testTag("image-contour-brush"))
    }
    if (region.contour != null) {
        Text("輪郭の余白：${(region.contourMargin * 100f / MosaicMask.EDGE).roundToInt()}％")
        Slider(region.contourMargin.toFloat(), onValueChange = { onMargin(it.roundToInt()) },
            valueRange = 0f..MosaicMask.MAX_MARGIN.toFloat(), steps = MosaicMask.MAX_MARGIN - 1,
            onValueChangeFinished = onMarginFinished, enabled = enabled, modifier = Modifier.testTag("image-contour-margin"))
    }
}
