@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.valoser.futacha.shared.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.media.edit.*
import com.valoser.futacha.shared.media.MediaFeature
import com.valoser.futacha.shared.media.analysis.*
import com.valoser.futacha.shared.media.video.model.MosaicMaskTool
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class EditorTool(val label: String) { PAINT("手描き"), ERASE("消しゴム"), REGIONS("枠"), VIEW("拡大・移動") }

@Composable
internal fun ImageEditorDialog(
    input: ImageEditInput,
    onDismiss: () -> Unit,
    onResult: suspend (ImageData) -> Unit,
    confirmLabel: String = "保存",
    exportEnabled: Boolean = true,
    dismissAfterResult: Boolean = true,
    destination: @Composable () -> Unit = {}
) {
    val gate = LocalMediaFeatureGate.current
    val settings = LocalMediaFeatureSettings.current
    // The root applies the permit in SideEffect after composition. Checking it here
    // could close a newly enabled editor before that effect runs; open/export check it.
    if (!settings.imageEditorEnabled || gate == null) {
        LaunchedEffect(Unit) { onDismiss() }; return
    }
    val loader = LocalFutachaImageLoader.current
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val history = remember(input) { ImageEditHistory() }
    val document by history.document.collectAsState()
    val availability by history.availability.collectAsState()
    var session by remember(input) { mutableStateOf<ImageEditSession?>(null) }
    var loading by remember(input) { mutableStateOf(true) }
    var error by remember(input) { mutableStateOf<String?>(null) }
    var preview by remember(input) { mutableStateOf<ImageBitmap?>(null) }
    var tool by remember { mutableStateOf(EditorTool.PAINT) }
    var brush by remember { mutableFloatStateOf(.08f) }
    var contourTool by remember { mutableStateOf(MosaicMaskTool.MOVE) }
    var contourBrush by remember { mutableFloatStateOf(.06f) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val models = LocalAnalysisModelStore.current
    var detectionSettings by remember { mutableStateOf(DetectionSettings()) }
    var showDetectionOptions by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var analysing by remember { mutableStateOf(false) }
    var cancellingAnalysis by remember { mutableStateOf(false) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    // Inference reports from a worker; StateFlow safely carries progress to Compose.
    val analysisProgress = remember { kotlinx.coroutines.flow.MutableStateFlow<Pair<String, Float?>?>(null) }
    val phase by analysisProgress.collectAsState()
    val busy = saving || analysing
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var discard by remember { mutableStateOf(false) }
    val currentResult by rememberUpdatedState(onResult)
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(input, loader, gate) {
        var opened: ImageEditSession? = null
        try {
            opened = ImageEditSession.open(input, gate, loader, context)
            session = opened; loading = false
            awaitCancellation()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "画像を読み込めませんでした"; loading = false }
        finally { opened?.close() }
    }
    // One collector renders each state to completion and then the newest one:
    // StateFlow conflates the edits made meanwhile. Restarting on every document
    // change cancelled the render after it had already allocated the full-size
    // buffers, so a fast drag kept restarting and the preview lagged behind.
    LaunchedEffect(session, history) {
        val active = session ?: return@LaunchedEffect
        history.document.collect { snapshot ->
            try {
                preview = withContext(Dispatchers.Default) { imageEditBitmap(renderImageEdit(active.original, snapshot)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "プレビューを作れませんでした" }
        }
    }
    fun close() {
        if (document.strokes.isNotEmpty() || document.regions.isNotEmpty() || document.analysed || busy) discard = true
        else onDismiss()
    }
    fun extractContours(all: Boolean) {
        val active = session ?: return
        val store = models ?: return
        val permit = gate.permit(MediaFeature.IMAGE_EDITOR) ?: return
        if (busy) return
        history.cancelGesture()
        val snapshot = history.document.value
        val regions = snapshot.regions.filter { all || it.id == selected }
        if (regions.isEmpty()) return
        analysing = true; cancellingAnalysis = false; error = null
        analysisJob = scope.launch {
            try {
                val contours = withContext(Dispatchers.Default) {
                    val frame = imageAnalysisFrame(active.original, 1024)
                    MobileSamSegmenter.open(store, gate, permit).use { segmenter ->
                        segmenter.segment(frame, regions.map { it.bounds.asMosaicBounds() }) { message, progress ->
                            analysisProgress.value = message to progress
                        }
                    }
                }
                ensureActive(); active.checkActive()
                history.change { snapshot.withContours(regions.map { it.id }, contours) }
                contourTool = MosaicMaskTool.MOVE
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "輪郭を抽出できませんでした。枠は変更していません。" }
            finally { analysing = false; cancellingAnalysis = false; analysisProgress.value = null }
        }
    }
    Dialog(onDismissRequest = ::close, properties = mediaEditorDialogProperties()) {
        Surface(Modifier.fillMaxSize().testTag("image-editor")) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::close) { Text("戻る") }
                    Text("画像編集", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(enabled = session != null && !busy && exportEnabled && !document.needsReview && !document.hasEmptyContour, onClick = {
                        history.commit()
                        val active = session ?: return@TextButton
                        val snapshot = history.document.value
                        saving = true; error = null
                        saveJob = scope.launch {
                            try {
                                val result = active.export(snapshot)
                                active.checkActive(); ensureActive()
                                currentResult(result)
                                active.checkActive(); ensureActive()
                                if (dismissAfterResult) currentDismiss()
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "編集結果を保存できませんでした" }
                            finally { saving = false }
                        }
                    }, modifier = Modifier.testTag("image-editor-export")) { Text(if (saving) "処理中…" else confirmLabel) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xff222222)), contentAlignment = Alignment.Center) {
                    session?.let { active ->
                        EditorCanvas(active.original, preview, history, tool, brush, selected, contourTool, contourBrush,
                            onSelected = { selected = it }, enabled = !busy,
                            onError = { error = it })
                    }
                    if (loading || busy) CircularProgressIndicator()
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 270.dp).verticalScroll(rememberScrollState()).padding(8.dp).testTag("image-editor-controls")) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("image-editor-error")) }
                    if (document.hasEmptyContour) Text("空の輪郭があります。塗り足すか、枠全体に戻してから保存してください。",
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("image-contour-empty"))
                    Text("JPEG・長辺2048px以内で保存します。透過は白背景になります。読み取れた生成情報を保持します。", style = MaterialTheme.typography.bodySmall)
                    if (session?.hasPartialMetadata == true) Text("一部の生成情報は引き継げません。元画像はそのまま残ります。",
                        modifier = Modifier.testTag("image-editor-metadata-partial"), style = MaterialTheme.typography.bodySmall)
                    destination()
                    if (models != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !busy && session != null, modifier = Modifier.testTag("image-editor-detect"), onClick = { history.cancelGesture(); showDetectionOptions = true }) { Text("自動検出") }
                            TextButton(enabled = !busy, modifier = Modifier.testTag("image-editor-models"), onClick = { showModels = true }) { Text("モデル") }
                            if (analysing) TextButton(enabled = !cancellingAnalysis, onClick = { cancellingAnalysis = true; analysisJob?.cancel() }, modifier = Modifier.testTag("image-editor-cancel-analysis")) { Text("取消") }
                        }
                    }
                    if (analysing) {
                        Text(if (cancellingAnalysis) "解析を停止しています…" else phase?.first ?: "画像を準備しています…", modifier = Modifier.testTag("image-editor-analysis-progress"))
                        phase?.second?.let { value -> LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth()) }
                    }
                    if (document.analysed) {
                        Text("候補は${document.regions.size}か所です。画像全体を確認し、隠し漏れや不要な枠を修正してください。", modifier = Modifier.testTag("image-editor-review-message"))
                        if (document.reviewed) Text("確認・修正済み", modifier = Modifier.testTag("image-editor-reviewed"))
                        else TextButton(enabled = !busy, modifier = Modifier.testTag("image-editor-confirm-review"), onClick = { history.change { it.copy(reviewed = true) } }) { Text("確認・修正済みにする") }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        EditorTool.entries.forEach { t ->
                            FilterChip(selected = tool == t, onClick = { history.cancelGesture(); tool = t },
                                label = { Text(t.label) }, enabled = !busy && session != null,
                                modifier = Modifier.testTag("image-editor-tool-${t.name.lowercase()}"))
                        }
                        TextButton(enabled = !busy && availability.first, onClick = { history.undo() }, modifier = Modifier.testTag("image-editor-undo")) { Text("戻す") }
                        TextButton(enabled = !busy && availability.second, onClick = { history.redo() }) { Text("やり直す") }
                    }
                    if (tool == EditorTool.VIEW) {
                        Text("2本指で拡大・縮小、1本指で移動できます。位置を合わせて手描きや枠に切り替えてください。", style = MaterialTheme.typography.bodySmall)
                    } else if (tool != EditorTool.REGIONS) {
                        Text(if (tool == EditorTool.ERASE) "消す太さ（手描き部分のみ）" else "筆の太さ")
                        Slider(brush, onValueChange = { brush = it }, valueRange = .005f.. .3f, enabled = !busy)
                        if (tool == EditorTool.PAINT) {
                            Text("粒度")
                            Slider(document.brushBlockFraction, { history.preview { d -> d.copy(brushBlockFraction = it) } },
                                valueRange = .005f.. .2f, onValueChangeFinished = history::commit, enabled = !busy)
                            Text("不透明度：${(document.brushOpacity * 100).roundToInt()}％（下げると元画像が透けます）")
                            Slider(document.brushOpacity, { history.preview { d -> d.copy(brushOpacity = it) } },
                                onValueChangeFinished = history::commit, enabled = !busy)
                        }
                    } else {
                        Row {
                            TextButton(enabled = !busy && session != null && document.regions.size < 16, onClick = {
                                val id = (document.regions.maxOfOrNull { it.id } ?: 0) + 1
                                history.change { it.copy(regions = it.regions + EditRegion(id)) }; selected = id
                                contourTool = MosaicMaskTool.MOVE
                            }, modifier = Modifier.testTag("image-editor-add-region")) { Text("枠を追加") }
                            TextButton(enabled = !busy && document.regions.any { it.id == selected }, onClick = {
                                history.change { it.copy(regions = it.regions.filterNot { r -> r.id == selected }) }; selected = null
                            }) { Text("選択した枠を削除") }
                        }
                        Text("枠をドラッグで移動・右下の印で拡縮できます", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            document.regions.forEachIndexed { index, region ->
                                FilterChip(selected == region.id, onClick = { selected = region.id }, enabled = !busy,
                                    label = { Text("${index + 1}: ${region.label ?: "手動の枠"}") }, modifier = Modifier.testTag("image-editor-region-${region.id}"))
                            }
                        }
                        document.regions.firstOrNull { it.id == selected }?.let { region ->
                            fun change(transform: (EditRegion) -> EditRegion, commit: Boolean = true) {
                                history.preview { it.copy(regions = it.regions.map { r -> if (r.id == region.id) transform(r) else r }) }
                                if (commit) history.commit()
                            }
                            Row {
                                FilterChip(region.style == EditStyle.MOSAIC, { change({ it.copy(style = EditStyle.MOSAIC) }) }, label = { Text("モザイク") }, enabled = !busy)
                                Spacer(Modifier.width(8.dp))
                                FilterChip(region.style == EditStyle.BLACK, { change({ it.copy(style = EditStyle.BLACK) }) }, label = { Text("黒塗り") }, enabled = !busy,
                                    modifier = Modifier.testTag("image-editor-black"))
                            }
                            if (region.style == EditStyle.MOSAIC) {
                                Text("枠の粒度")
                                Slider(region.blockFraction, { value -> change({ it.copy(blockFraction = value) }, false) },
                                    valueRange = .005f.. .2f, onValueChangeFinished = history::commit, enabled = !busy)
                                Text("黒の濃さ")
                                Slider(region.darkness, { value -> change({ it.copy(darkness = value) }, false) },
                                    onValueChangeFinished = history::commit, enabled = !busy)
                            }
                            ImageContourControls(region, !busy, models != null, contourTool, contourBrush,
                                onTool = { history.cancelGesture(); contourTool = it }, onBrush = { contourBrush = it },
                                onExtract = ::extractContours,
                                onReset = { change({ it.copy(contour = null, contourUncertain = false) }); contourTool = MosaicMaskTool.MOVE },
                                onMargin = { margin -> change({ it.copy(contourMargin = margin) }, false) }, onMarginFinished = history::commit)
                        }
                    }
                }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("編集を終了しますか？") },
        text = { Text("確定していない編集は破棄されます。元の画像は変更されません。") },
        confirmButton = { TextButton(onClick = { saveJob?.cancel(); analysisJob?.cancel(); onDismiss() }) { Text("終了") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("編集を続ける") } })
    if (showModels) AnalysisModelDialog(MediaFeature.IMAGE_EDITOR, onDismiss = { showModels = false })
    else if (showDetectionOptions) DetectionOptionsDialog(detectionSettings, onSettingsChanged = { detectionSettings = it }, onModels = { showModels = true },
        onDismiss = { showDetectionOptions = false }, onDetect = { options ->
            val active = session
            val store = models
            val permit = gate.permit(MediaFeature.IMAGE_EDITOR)
            if (active != null && store != null && permit != null && !busy) {
                history.commit()
                val snapshot = history.document.value
                detectionSettings = options; showDetectionOptions = false; analysing = true; cancellingAnalysis = false; error = null
                analysisJob = scope.launch {
                    try {
                        val updated = ImageSensitiveAnalyser.analyse(active.original, options, snapshot, store, gate, permit) { message, progress ->
                            analysisProgress.value = message to progress
                        }
                        ensureActive(); active.checkActive()
                        history.change { updated }
                        tool = EditorTool.REGIONS
                        contourTool = MosaicMaskTool.MOVE
                        selected = updated.regions.firstOrNull { r -> snapshot.regions.none { it.id == r.id } }?.id ?: selected
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "画像を解析できませんでした" }
                    finally { analysing = false; cancellingAnalysis = false; analysisProgress.value = null }
                }
            }
        })
}

@Composable
private fun EditorCanvas(
    original: EditRaster, bitmap: ImageBitmap?, history: ImageEditHistory, tool: EditorTool,
    brush: Float, selected: Int?, contourTool: MosaicMaskTool, contourBrush: Float,
    onSelected: (Int?) -> Unit, enabled: Boolean, onError: (String) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember(original) { mutableStateOf(ImageEditorCamera()) }
    val document by history.document.collectAsState()
    val latestSelected by rememberUpdatedState(selected)
    fun paintContour(from: EditPoint, to: EditPoint) {
        history.preview { it.copy(regions = it.regions.map { region ->
            if (region.id == latestSelected) region.paintContour(from, to, original.width, original.height,
                contourBrush, contourTool == MosaicMaskTool.ERASE) else region
        }) }
    }
    val viewport = camera.viewport(original.width, original.height, canvasSize.width, canvasSize.height)
    val transform by rememberUpdatedState<(Offset, Offset, Float) -> Unit>({ anchor, pan, scale ->
        camera = camera.transform(original.width, original.height, canvasSize.width, canvasSize.height,
            scale, anchor.x, anchor.y, pan.x, pan.y)
    })
    fun zoomBy(scale: Float) {
        history.cancelGesture()
        transform(Offset(canvasSize.width / 2f, canvasSize.height / 2f), Offset.Zero, scale)
    }
    Column(Modifier.fillMaxSize()) {
        Surface {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("表示 ${(camera.zoom * 100).roundToInt()}％", Modifier.weight(1f).testTag("image-editor-zoom-level"), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = enabled && camera.zoom > 1f, onClick = { zoomBy(.5f) }, modifier = Modifier.testTag("image-editor-zoom-out")) { Text("縮小") }
                TextButton(enabled = enabled, onClick = { history.cancelGesture(); camera = ImageEditorCamera() }, modifier = Modifier.testTag("image-editor-zoom-reset")) { Text("全体") }
                TextButton(enabled = enabled && camera.zoom < 8f, onClick = { zoomBy(2f) }, modifier = Modifier.testTag("image-editor-zoom-in")) { Text("拡大") }
            }
        }
    Canvas(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { canvasSize = it }.testTag("image-editor-canvas")
        .pointerInput(tool, enabled) {
            if (enabled && tool == EditorTool.VIEW) detectTransformGestures { centroid, pan, zoom, _ -> transform(centroid, pan, zoom) }
        }
        .pointerInput(history, tool, brush, enabled, viewport, contourTool, contourBrush) {
            if (!enabled || tool == EditorTool.VIEW) return@pointerInput
            var start: EditPoint? = null
            var bounds: EditBounds? = null
            var regionId: Int? = null
            var resizing = false
            var painting = false
            fun change(block: () -> Unit) {
                try { block() } catch (failure: IllegalArgumentException) { history.cancelGesture(); painting = false; onError(failure.message ?: "編集の上限です") }
            }
            detectDragGestures(
                onDragStart = { offset ->
                    val point = viewport.point(offset.x, offset.y)
                    start = point; painting = false; regionId = null
                    if (point != null) {
                        if (tool == EditorTool.REGIONS) {
                            if (contourTool != MosaicMaskTool.MOVE) {
                                change { paintContour(point, point) }; painting = true
                                return@detectDragGestures
                            }
                            val regions = history.document.value.regions
                            val selectedRegion = regions.firstOrNull { it.id == latestSelected }
                            fun nearHandle(r: EditRegion) = abs((point.x - r.bounds.left - r.bounds.width) * viewport.width) < 24.dp.toPx() &&
                                abs((point.y - r.bounds.top - r.bounds.height) * viewport.height) < 24.dp.toPx()
                            val hit = selectedRegion?.takeIf { nearHandle(it) || it.bounds.contains(point) }
                                ?: regions.lastOrNull { it.bounds.contains(point) }
                            onSelected(hit?.id); regionId = hit?.id; bounds = hit?.bounds
                            resizing = hit != null && nearHandle(hit)
                        } else change {
                            history.preview { it.copy(strokes = it.strokes + EditStroke(listOf(point), brush, tool == EditorTool.ERASE)) }
                            painting = true
                        }
                    }
                },
                onDrag = { pointer, _ ->
                    pointer.consume()
                    val point = viewport.point(pointer.position.x, pointer.position.y, clamp = true)
                    if (point != null) change {
                        if (tool == EditorTool.REGIONS) {
                            if (contourTool != MosaicMaskTool.MOVE) {
                                if (painting) start?.let { paintContour(it, point) }
                                start = point
                                return@change
                            }
                            val origin = start; val b = bounds
                            if (origin != null && b != null) {
                                val dx = point.x - origin.x; val dy = point.y - origin.y
                                val next = (if (resizing) b.copy(width = b.width + dx, height = b.height + dy)
                                    else b.copy(left = b.left + dx, top = b.top + dy)).constrained()
                                history.preview { it.copy(regions = it.regions.map { r -> if (r.id == regionId) r.copy(bounds = next) else r }) }
                            }
                        } else if (painting) history.preview { d ->
                            val last = d.strokes.last()
                            d.copy(strokes = d.strokes.dropLast(1) + last.copy(points = last.points + point))
                        }
                    }
                }, onDragEnd = { history.commit() }, onDragCancel = { history.cancelGesture() }
            )
        }.pointerInput(history, tool, brush, enabled, viewport, contourTool, contourBrush) {
            if (!enabled || tool == EditorTool.VIEW) return@pointerInput
            detectTapGestures { offset ->
                val point = viewport.point(offset.x, offset.y) ?: return@detectTapGestures
                if (tool == EditorTool.REGIONS) {
                    if (contourTool == MosaicMaskTool.MOVE) onSelected(history.document.value.regions.lastOrNull { it.bounds.contains(point) }?.id)
                    else { paintContour(point, point); history.commit() }
                }
                else try {
                    history.change { it.copy(strokes = it.strokes + EditStroke(listOf(point), brush, tool == EditorTool.ERASE)) }
                } catch (failure: IllegalArgumentException) { onError(failure.message ?: "編集の上限です") }
            }
        }) {
        bitmap?.let { drawImage(it, dstOffset = IntOffset(viewport.left.roundToInt(), viewport.top.roundToInt()),
            dstSize = IntSize(viewport.width.roundToInt().coerceAtLeast(1), viewport.height.roundToInt().coerceAtLeast(1))) }
        if (tool == EditorTool.REGIONS) document.regions.forEach { region ->
            val b = region.bounds
            val topLeft = Offset(viewport.left + b.left * viewport.width, viewport.top + b.top * viewport.height)
            val rectSize = Size(b.width * viewport.width, b.height * viewport.height)
            val color = if (region.id == selected) Color.Cyan else Color.Yellow
            drawRect(color, topLeft, rectSize, style = Stroke(2.dp.toPx()))
            if (region.id == selected) drawCircle(color, 7.dp.toPx(), topLeft + Offset(rectSize.width, rectSize.height))
        }
    }
    }
}
