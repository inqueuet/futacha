@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.valoser.futacha.shared.ui.media

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.media.MediaFeature
import com.valoser.futacha.shared.media.analysis.*
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher
import com.valoser.futacha.shared.ui.image.LocalMediaFeatureGate
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.*
import kotlin.math.roundToInt

@Composable
internal fun VideoEditorDialog(source: VideoEditSource, fs: FileSystem, stateStore: AppStateStore, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val history = remember { MosaicHistory() }
    var document by remember { mutableStateOf(history.current) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<VideoEditInfo?>(null) }
    var time by remember { mutableLongStateOf(0L) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewReady by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf("") }
    var cancelling by remember { mutableStateOf(false) }
    var analysisStart by remember { mutableLongStateOf(0L) }
    var analysisEnd by remember { mutableLongStateOf(0L) }
    var detectionSettings by remember { mutableStateOf(DetectionSettings()) }
    var showDetection by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(MosaicMaskTool.MOVE) }
    var brush by remember { mutableFloatStateOf(.06f) }
    val models = LocalAnalysisModelStore.current
    val gate = LocalMediaFeatureGate.current
    var output by remember { mutableStateOf<String?>(null) }
    var editingPlayback by remember { mutableStateOf<VideoEditPlayback?>(null) }
    var playbackState by remember { mutableStateOf(VideoPlayerState.Buffering) }
    val editable = !working && editingPlayback == null
    var discard by remember { mutableStateOf(false) }
    var nextId by remember { mutableIntStateOf(1) }
    var saved by remember { mutableStateOf<Pair<SaveLocation, String>?>(null) }
    val persisted by stateStore.manualSaveLocation.collectAsState(initial = null)
    var chosen by remember { mutableStateOf<SaveLocation?>(null) }
    val location = chosen ?: persisted
    val directoryPicker = rememberDirectoryPickerLauncher(onDirectorySelected = { selected ->
        chosen = selected
        scope.launch {
            try { stateStore.setManualSaveLocation(selected) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "保存先を設定に記録できませんでした。今回の保存には選んだ場所を使います。" }
        }
    })
    val share = rememberCompatShareLauncher()
    fun commit(value: MosaicDocument) { history.commit(value); document = history.current }
    fun stopPreview() { editingPlayback?.pause(); editingPlayback?.close(); editingPlayback = null }
    fun close() { stopPreview(); if (document.regions.isNotEmpty() || working) discard = true else onDismiss() }
    fun analyse(operation: suspend (VideoEditInfo, MosaicDocument, (String, Float?) -> Unit) -> MosaicDocument) {
        val video = info ?: return
        if (!editable) return
        val permit = gate?.permit(MediaFeature.VIDEO_EDITOR) ?: return
        val snapshot = document
        working = true; cancelling = false; phase = "解析を準備しています"; progress = Float.NaN; error = null
        job = scope.launch {
            try {
                val result = operation(video, snapshot) { message, value -> phase = message; progress = value ?: Float.NaN }
                ensureActive(); source.checkActive()
                if (!gate.isCurrent(permit)) throw CancellationException("動画編集は無効になりました")
                commit(result)
                selectedId = result.regions.firstOrNull { region -> snapshot.regions.none { it.id == region.id } }?.id
                    ?: selectedId?.takeIf { id -> result.regions.any { it.id == id } }
                tool = MosaicMaskTool.MOVE
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "解析できませんでした。編集内容は変更していません" }
            finally { working = false; cancelling = false; phase = "" }
        }
    }
    LaunchedEffect(source) {
        try {
            info = source.useFile { inspectDeviceVideo(it).also { found -> require(!found.hdr) { "現在はSDR動画に対応しています。HDR動画は編集できません" } } }
            time = info!!.frames.timeAt(0)
            analysisStart = time; analysisEnd = info!!.frames.durationUs
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "動画を読み取れません" }
        finally { loading = false }
    }
    LaunchedEffect(info, time, document, output, working, editingPlayback) {
        val video = info ?: return@LaunchedEffect
        if (output != null || working || editingPlayback != null) return@LaunchedEffect
        previewReady = false
        try {
            // Native frame reads cannot always interrupt immediately; serialize them through the source.
            delay(50)
            preview = source.useFile { previewDeviceVideo(it, video, time, document) }
            previewReady = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "プレビューを作れません" }
    }
    Dialog(onDismissRequest = ::close, properties = mediaEditorDialogProperties()) {
        Surface(Modifier.fillMaxSize().testTag("video-editor")) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::close) { Text("戻る") }
                    Text(if (output == null) "動画編集" else "書き出し結果の確認", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (output == null) TextButton(enabled = info != null && document.regions.isNotEmpty() && editable &&
                        document.review?.confirmed != false && document.regions.none { it.hasEmptyActiveMask() }, modifier = Modifier.testTag("video-editor-export"), onClick = {
                        val video = info ?: return@TextButton
                        working = true; progress = 0f; error = null; phase = "動画を書き出しています"; cancelling = false
                        job = scope.launch {
                            try { output = source.export(context, fs, video, document) { progress = it } }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "動画を書き出せませんでした" }
                            finally { working = false; phase = ""; cancelling = false }
                        }
                    }) { Text("書き出して確認") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xff222222)), contentAlignment = Alignment.Center) {
                    if (output != null && saved == null) PlatformVideoPlayer(localMediaFileUri(output!!), Modifier.fillMaxSize())
                    else if (editingPlayback != null) EditedVideoPreview(source, editingPlayback!!,
                        Modifier.fillMaxSize().testTag("video-editor-playback"), onState = { playbackState = it },
                        onError = { error = it; stopPreview() })
                    else info?.let { video -> VideoEditCanvas(video, preview, document, selectedId, time, editable, tool, brush,
                        onPreview = { document = it }, onCommit = { commit(document) }) }
                    if (loading || working || (output == null && info != null &&
                        if (editingPlayback != null) playbackState == VideoPlayerState.Buffering else !previewReady)) CircularProgressIndicator()
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 290.dp).testTag("video-editor-controls").verticalScroll(rememberScrollState()).padding(8.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("video-editor-error")) }
                    if (working) {
                        Text(if (cancelling) "取り消しています…" else phase, modifier = Modifier.testTag("video-editor-progress"))
                        if (progress.isFinite()) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(enabled = !cancelling, onClick = { cancelling = true; job?.cancel() }, modifier = Modifier.testTag("video-editor-cancel")) { Text("処理をキャンセル") }
                    }
                    if (output != null) {
                        Text("再生して範囲と音声を確認し、保存してください。MP4で新規保存し、対応する元の生成情報と編集履歴を保持します。", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("選択した保存先の edited_videos に保存", Modifier.weight(1f))
                            TextButton(onClick = directoryPicker, enabled = !working) { Text("保存先") }
                        }
                        Row {
                            TextButton(enabled = !working, onClick = {
                                val previous = output; output = null
                                scope.launch { previous?.let { fs.delete(it).onFailure { error = "前の書き出し結果を削除できません" } } }
                            }) { Text("編集に戻る") }
                            Button(enabled = !working && location != null && !requiresVisibleManualSaveDestination(isAndroid(), location), onClick = {
                                val target = location ?: return@Button; val rendered = output ?: return@Button
                                working = true; progress = Float.NaN; error = null; phase = "動画を保存しています"; cancelling = false
                                job = scope.launch {
                                    try { val path = saveEditedVideo(source, fs, rendered, target); saved = target to path }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { error = failure.message ?: "保存できませんでした" }
                                    finally { working = false; phase = ""; cancelling = false }
                                }
                            }, modifier = Modifier.testTag("video-editor-save")) { Text("動画を保存") }
                        }
                    } else info?.let { video ->
                        Text("${videoTime(time - video.frames.timeAt(0))} / ${videoTime(video.frames.durationUs - video.frames.timeAt(0))}", modifier = Modifier.testTag("video-editor-time"))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !working, modifier = Modifier.testTag("video-editor-play-pause"), onClick = {
                                if (editingPlayback != null) stopPreview() else {
                                    error = null; playbackState = VideoPlayerState.Buffering
                                    val start = if (time >= video.frames.timeAt(video.frames.size - 1)) video.frames.timeAt(0) else time
                                    editingPlayback = VideoEditPlayback(video, document, start, onPosition = { time = it }, onEnded = ::stopPreview)
                                }
                            }) { Text(if (editingPlayback == null) "編集内容を再生" else "止めて編集") }
                            if (editingPlayback != null) Text(if (playbackState == VideoPlayerState.Ready) "再生中" else "再生を準備中…",
                                modifier = Modifier.testTag("video-editor-preview-state"), style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(time.toFloat(), { stopPreview(); time = video.frames.atOrBefore(it.toLong()) },
                            valueRange = 0f..video.frames.timeAt(video.frames.size - 1).toFloat().coerceAtLeast(1f), enabled = !working,
                            modifier = Modifier.testTag("video-editor-timeline"))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(enabled = editable, onClick = { time = video.frames.step(time, false) }) { Text("前のコマ") }
                            TextButton(enabled = editable, onClick = { time = video.frames.step(time, true) }) { Text("次のコマ") }
                            TextButton(enabled = editable && history.canUndo, onClick = { document = history.undo() }) { Text("戻す") }
                            TextButton(enabled = editable && history.canRedo, onClick = { document = history.redo() }) { Text("やり直す") }
                            TextButton(enabled = editable && document.regions.size < MosaicDocument.MAX_REGIONS, modifier = Modifier.testTag("video-editor-add"), onClick = {
                                val id = (nextId++).toString(); selectedId = id
                                commit(document.copy(regions = document.regions + MosaicRegion(id, endUs = video.frames.durationUs), review = document.review?.copy(confirmed = false)))
                            }) { Text("範囲を追加") }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            document.regions.forEachIndexed { index, region -> FilterChip(selectedId == region.id, { selectedId = region.id; tool = MosaicMaskTool.MOVE },
                                label = { Text("範囲${index + 1}${region.label?.let { "・$it" }.orEmpty()}") }, enabled = editable,
                                modifier = Modifier.testTag("video-editor-region-${index + 1}")) }
                        }
                        VideoAnalysisControls(video, time, analysisStart, analysisEnd, document.regions.firstOrNull { it.id == selectedId },
                            editable, models != null && gate != null, onInterval = { start, end -> analysisStart = start; analysisEnd = end },
                            onDetect = { showDetection = true }, onModels = { showModels = true }, onTrack = { forward ->
                                val id = selectedId ?: return@VideoAnalysisControls
                                val anchor = time
                                analyse { found, snapshot, report -> trackVideoRegion(source, found, snapshot, id, anchor, forward, report) }
                            }, onContour = { interval ->
                                val id = selectedId ?: return@VideoAnalysisControls
                                val permit = gate?.permit(MediaFeature.VIDEO_EDITOR) ?: return@VideoAnalysisControls
                                val store = models ?: return@VideoAnalysisControls
                                val start = if (interval) analysisStart else time
                                val end = if (interval) analysisEnd else video.frames.endAfter(time)
                                analyse { found, snapshot, report -> VideoSensitiveAnalyser.contours(source, found, snapshot, setOf(id), start, end, store, gate, permit, report) }
                            })
                        VideoReviewControls(document, video, selectedId, time, editable, onSeek = { time = video.frames.atOrBefore(it) },
                            onConfirm = { commit(document.copy(review = document.review?.copy(confirmed = true))) })
                        if (document.regions.any { it.hasEmptyActiveMask() }) Text("空の輪郭があります。塗り足すか枠全体に戻してから書き出してください。",
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("video-empty-mask"))
                        document.regions.firstOrNull { it.id == selectedId }?.let { region ->
                            VideoRegionControls(region, time, video, editable, change = { change, finish ->
                                val value = document.update(region.id, change)
                                if (finish) commit(value) else document = value
                            }, finish = { commit(document) }, remove = {
                                commit(document.copy(regions = document.regions.filterNot { it.id == region.id }, review = document.review?.copy(confirmed = false))); selectedId = null
                            })
                            VideoContourControls(region, time, editable, tool, brush, onTool = { tool = it }, onBrush = { brush = it },
                                onReset = { commit(document.update(region.id) { it.withMask(time, null) }) },
                                onMargin = { value -> document = document.update(region.id) { it.copy(maskMargin = value) } }, onFinish = { commit(document) })
                        }
                        Text("枠をドラッグして移動できます。位置・大きさの変更は現在のコマに記録し、間の位置を補間します。SDR動画をMP4で出力します。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (showDetection) DetectionOptionsDialog(detectionSettings, { detectionSettings = it }, onModels = { showModels = true },
        onDismiss = { showDetection = false }, onDetect = { settings ->
            showDetection = false
            val store = models ?: return@DetectionOptionsDialog
            val permit = gate?.permit(MediaFeature.VIDEO_EDITOR) ?: return@DetectionOptionsDialog
            val start = analysisStart; val end = analysisEnd
            analyse { video, snapshot, report -> VideoSensitiveAnalyser.detect(source, video, snapshot, start, end, settings, store, gate, permit, report) }
        }, video = true)
    if (showModels) AnalysisModelDialog(MediaFeature.VIDEO_EDITOR) { showModels = false }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("編集を終了しますか？") }, text = { Text("保存していない編集内容を破棄します。") },
        confirmButton = { TextButton(onClick = { job?.cancel(); onDismiss() }) { Text("破棄して終了") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("続ける") } })
    saved?.let { (target, path) -> AlertDialog(onDismissRequest = onDismiss, title = { Text("編集した動画を保存しました") }, text = { Text(error ?: path) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }, dismissButton = { TextButton(onClick = { scope.launch {
            try { share("", "video/mp4", fs.resolveSavedFile(target, path).getOrThrow()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "共有できません" }
        } }) { Text("共有") } }) }
}

@Composable
private fun VideoRegionControls(region: MosaicRegion, time: Long, info: VideoEditInfo, enabled: Boolean,
    change: ((MosaicRegion) -> MosaicRegion, Boolean) -> Unit, finish: () -> Unit, remove: () -> Unit) {
    Row(Modifier.testTag("video-region-controls").horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MosaicShape.entries.forEach { shape -> FilterChip(region.shape == shape, { change({ it.copy(shape = shape) }, true) }, label = { Text(if (shape == MosaicShape.RECTANGLE) "四角" else "楕円") }, enabled = enabled) }
        MosaicStyle.entries.forEach { style -> FilterChip(region.style == style, { change({ it.copy(style = style) }, true) }, label = { Text(if (style == MosaicStyle.BLACK) "黒塗り" else "モザイク") }, enabled = enabled) }
        TextButton(onClick = remove, enabled = enabled) { Text("削除") }
    }
    Text("適用：${videoTime((region.startUs - info.frames.timeAt(0)).coerceAtLeast(0))} 〜 ${videoTime(region.endUs - info.frames.timeAt(0))}${if (!region.activeAt(time)) "（このコマは範囲外）" else ""}")
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        TextButton(enabled = enabled && time < region.endUs, onClick = { change({ it.copy(startUs = time) }, true) }) { Text("このコマから") }
        TextButton(enabled = enabled && info.frames.endAfter(time) > region.startUs, onClick = { change({ it.copy(endUs = info.frames.endAfter(time)) }, true) }) { Text("このコマまで") }
        TextButton(enabled = enabled, onClick = { change({ it.copy(startUs = 0, endUs = info.frames.durationUs) }, true) }) { Text("全区間") }
    }
    val bounds = region.boundsAt(time)
    Text("幅")
    Slider(bounds.width, { value -> change({ it.withBounds(time, it.boundsAt(time).copy(width = value)) }, false) }, valueRange = .025f..1f, enabled = enabled, onValueChangeFinished = finish)
    Text("高さ")
    Slider(bounds.height, { value -> change({ it.withBounds(time, it.boundsAt(time).copy(height = value)) }, false) }, valueRange = .025f..1f, enabled = enabled, onValueChangeFinished = finish)
    if (region.style == MosaicStyle.PIXELATE) {
        Text("粒度")
        Slider(region.blockFraction, { value -> change({ it.copy(blockFraction = value) }, false) }, valueRange = .005f.. .2f, enabled = enabled, onValueChangeFinished = finish)
    }
}

@Composable
private fun VideoEditCanvas(info: VideoEditInfo, preview: ImageBitmap?, document: MosaicDocument, selected: String?, time: Long,
    enabled: Boolean, tool: MosaicMaskTool, brush: Float, onPreview: (MosaicDocument) -> Unit, onCommit: () -> Unit) {
    val current by rememberUpdatedState(document)
    val update by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    fun paint(from: Offset, to: Offset) {
        val id = selected ?: return
        val region = current.regions.firstOrNull { it.id == id } ?: return
        if (!region.activeAt(time)) return
        val old = region.maskAt(time) ?: if (tool == MosaicMaskTool.ADD) MosaicMask.EMPTY else MosaicMask.FULL
        val mask = old.paintWithin(region.boundsAt(time), info.width, info.height, from.x, from.y, to.x, to.y, brush, tool == MosaicMaskTool.ERASE)
        update(current.update(id) { it.withMask(time, mask) })
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val aspect = info.width.toFloat() / info.height
        val width = minOf(maxWidth, maxHeight * aspect); val height = width / aspect
        Canvas(Modifier.size(width, height).testTag("video-editor-canvas").pointerInput(selected, time, enabled, tool, brush) {
            if (enabled && tool != MosaicMaskTool.MOVE) detectTapGestures { position ->
                val point = Offset(position.x / size.width, position.y / size.height)
                paint(point, point); commit()
            }
        }.pointerInput(selected, time, enabled, tool, brush) {
            if (enabled) detectDragGestures(onDragStart = {}, onDragCancel = { commit() }, onDragEnd = { commit() }) { change, drag ->
                val id = selected ?: return@detectDragGestures
                change.consume()
                if (tool == MosaicMaskTool.MOVE) {
                    if (current.regions.none { it.id == id && it.activeAt(time) }) return@detectDragGestures
                    update(current.update(id) { region -> val b = region.boundsAt(time); region.withBounds(time,
                        b.copy(centerX = b.centerX + drag.x / size.width, centerY = b.centerY + drag.y / size.height)) })
                } else paint(Offset((change.position.x - drag.x) / size.width, (change.position.y - drag.y) / size.height),
                    Offset(change.position.x / size.width, change.position.y / size.height))
            }
        }) {
            preview?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            document.regions.filter { it.activeAt(time) }.forEach { r ->
                val b = r.boundsAt(time); val origin = Offset((b.centerX - b.width / 2) * size.width, (b.centerY - b.height / 2) * size.height)
                val dimensions = Size(b.width * size.width, b.height * size.height)
                val color = if (r.id == selected) Color.Yellow else Color.White
                if (r.shape == MosaicShape.ELLIPSE) drawOval(color, origin, dimensions, style = Stroke(2.dp.toPx()))
                else drawRect(color, origin, dimensions, style = Stroke(2.dp.toPx()))
            }
        }
    }
}
internal fun videoTime(timeUs: Long): String = "${timeUs / 60_000_000}:${(timeUs / 1_000_000 % 60).toString().padStart(2, '0')}.${(timeUs / 1000 % 1000).toString().padStart(3, '0')}"
