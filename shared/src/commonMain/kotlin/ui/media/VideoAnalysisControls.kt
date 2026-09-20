@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.valoser.futacha.shared.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.media.video.VideoEditInfo
import com.valoser.futacha.shared.media.video.model.*
import kotlin.math.roundToInt

/** Controls for the existing edit model, detector, tracker and stored silhouettes. */
@Composable
internal fun VideoAnalysisControls(info: VideoEditInfo, time: Long, start: Long, end: Long,
    region: MosaicRegion?, enabled: Boolean, modelsAvailable: Boolean,
    onInterval: (Long, Long) -> Unit, onDetect: () -> Unit, onModels: () -> Unit,
    onTrack: (Boolean) -> Unit, onContour: (Boolean) -> Unit) {
    Text("解析区間：${videoTime(start - info.frames.timeAt(0))} 〜 ${videoTime(end - info.frames.timeAt(0))}",
        modifier = Modifier.testTag("video-analysis-interval"), style = MaterialTheme.typography.bodySmall)
    FlowRow {
        TextButton(enabled = enabled && time < end, onClick = { onInterval(time, end) }) { Text("解析はこのコマから") }
        TextButton(enabled = enabled && info.frames.endAfter(time) > start, onClick = { onInterval(start, info.frames.endAfter(time)) }) { Text("解析はこのコマまで") }
        TextButton(enabled = enabled, onClick = { onInterval(info.frames.timeAt(0), info.frames.durationUs) }) { Text("全区間を解析") }
    }
    if (modelsAvailable) FlowRow {
        TextButton(enabled = enabled, onClick = onDetect, modifier = Modifier.testTag("video-editor-detect")) { Text("性器の候補を自動検出") }
        TextButton(enabled = enabled, onClick = onModels, modifier = Modifier.testTag("video-editor-models")) { Text("モデルの導入・確認") }
    }
    if (region != null) {
        FlowRow {
            TextButton(enabled = enabled && region.activeAt(time), onClick = { onTrack(true) }, modifier = Modifier.testTag("video-track-forward")) { Text("このコマから前方追尾") }
            TextButton(enabled = enabled && region.activeAt(time), onClick = { onTrack(false) }, modifier = Modifier.testTag("video-track-backward")) { Text("このコマから逆方向追尾") }
        }
        if (modelsAvailable) FlowRow {
            TextButton(enabled = enabled && region.activeAt(time), onClick = { onContour(false) }, modifier = Modifier.testTag("video-contour-frame")) { Text("このコマの輪郭を抽出") }
            TextButton(enabled = enabled && region.startUs < end && region.endUs > start, onClick = { onContour(true) }, modifier = Modifier.testTag("video-contour-interval")) { Text("解析区間の輪郭を抽出") }
        }
    }
}

@Composable
internal fun VideoReviewControls(document: MosaicDocument, info: VideoEditInfo, selectedId: String?, time: Long,
    enabled: Boolean, onSeek: (Long) -> Unit, onConfirm: () -> Unit) {
    val review = document.review ?: return
    var filter by remember { mutableStateOf(MosaicReviewFilter.DETECTED) }
    var issueFilter by remember { mutableStateOf(MosaicIssueFilter.ATTENTION) }
    val index = remember(document, filter, selectedId, info) { MosaicReviewIndex.create(document, filter, selectedId, info.frames) }
    val groups = remember(review.issues) { MosaicIssueGroup.from(review.issues) }
    val visible = groups.filter { when (issueFilter) {
        MosaicIssueFilter.ATTENTION -> !it.noCandidate
        MosaicIssueFilter.NO_CANDIDATE -> it.noCandidate
        MosaicIssueFilter.ALL -> true
    } }
    Column(Modifier.testTag("video-review")) {
        Text(if (review.confirmed) "解析区間の確認済み" else "解析区間の確認が必要です", style = MaterialTheme.typography.titleSmall)
        Text("${review.description}。${videoTime(review.startUs - info.frames.timeAt(0))} 〜 ${videoTime(review.endUs - info.frames.timeAt(0))}を通して確認してください。", style = MaterialTheme.typography.bodySmall)
        FlowRow {
            MosaicReviewFilter.entries.forEach { value -> FilterChip(filter == value, { filter = value }, enabled = enabled,
                label = { Text(when (value) { MosaicReviewFilter.DETECTED -> "検出した範囲"; MosaicReviewFilter.ALL -> "すべての範囲"; MosaicReviewFilter.SELECTED -> "選択中の範囲" }) }) }
        }
        FlowRow {
            TextButton(enabled = enabled, onClick = { onSeek(review.startUs) }) { Text("解析区間の先頭") }
            TextButton(enabled = enabled && index.intervals.isNotEmpty(), onClick = { index.previousStart(time)?.let(onSeek) }) { Text("前の範囲") }
            TextButton(enabled = enabled && index.intervals.isNotEmpty(), onClick = { index.nextStart(time)?.let(onSeek) }) { Text("次の範囲") }
        }
        if (groups.isNotEmpty()) {
            FlowRow {
                MosaicIssueFilter.entries.forEach { value -> FilterChip(issueFilter == value, { issueFilter = value }, enabled = enabled,
                    label = { Text(when (value) { MosaicIssueFilter.ATTENTION -> "要確認"; MosaicIssueFilter.NO_CANDIDATE -> "候補なし"; MosaicIssueFilter.ALL -> "全指摘" }) }) }
            }
            visible.forEach { group ->
                Text(group.reason, style = MaterialTheme.typography.bodySmall)
                FlowRow {
                    val previous = group.intervals.lastOrNull { it.startUs < time } ?: group.intervals.last()
                    val next = group.intervals.firstOrNull { it.startUs > time } ?: group.intervals.first()
                    TextButton(enabled = enabled, onClick = { onSeek(previous.startUs) }) { Text("前の該当箇所") }
                    TextButton(enabled = enabled, onClick = { onSeek(next.startUs) }) { Text("次の該当箇所（${group.intervals.size}区間）") }
                }
            }
        }
        if (!review.confirmed) TextButton(enabled = enabled && document.regions.none { it.hasEmptyActiveMask() },
            onClick = onConfirm, modifier = Modifier.testTag("video-review-confirm")) { Text("区間全体の隠す範囲を確認した") }
    }
}

@Composable
internal fun VideoContourControls(region: MosaicRegion, time: Long, enabled: Boolean, tool: MosaicMaskTool, brush: Float,
    onTool: (MosaicMaskTool) -> Unit, onBrush: (Float) -> Unit, onReset: () -> Unit, onMargin: (Int) -> Unit, onFinish: () -> Unit) {
    FlowRow {
        MosaicMaskTool.entries.forEach { mode -> FilterChip(tool == mode, { onTool(mode) }, enabled = enabled && region.activeAt(time),
            label = { Text(when (mode) { MosaicMaskTool.MOVE -> "枠の移動"; MosaicMaskTool.ADD -> "輪郭を塗る"; MosaicMaskTool.ERASE -> "輪郭を削る" }) },
            modifier = Modifier.testTag("video-contour-tool-${mode.name.lowercase()}")) }
        TextButton(enabled = enabled && region.activeAt(time) && region.maskAt(time) != null, onClick = onReset,
            modifier = Modifier.testTag("video-contour-reset")) { Text("このコマから枠全体に戻す") }
    }
    if (tool != MosaicMaskTool.MOVE) {
        Text("枠の内側をなぞって輪郭を修正します。変更は次の輪郭指定があるコマまで適用されます。", style = MaterialTheme.typography.bodySmall)
        Slider(brush, onBrush, valueRange = .01f.. .2f, enabled = enabled, modifier = Modifier.testTag("video-contour-brush"))
    }
    if (region.maskAt(time) != null) {
        Text("輪郭の余白：${(region.maskMargin * 100f / MosaicMask.EDGE).roundToInt()}％")
        Slider(region.maskMargin.toFloat(), { onMargin(it.roundToInt()) }, valueRange = 0f..MosaicMask.MAX_MARGIN.toFloat(),
            steps = MosaicMask.MAX_MARGIN - 1, onValueChangeFinished = onFinish, enabled = enabled,
            modifier = Modifier.testTag("video-contour-margin"))
    }
}
