@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// PostDrawingActivity in both sample APKs creates a canvas at 90% of the
// display width and a fixed 344:135 ratio, centered on Futaba's #FFFFEE
// background. Keep these values separate from the selected chrome theme: the
// reference deliberately uses the Futaba drawing surface for every theme.
internal const val COMPAT_DRAWING_WIDTH_FRACTION = 0.9f
internal const val COMPAT_DRAWING_ASPECT_RATIO = 344f / 135f
internal const val COMPAT_DRAWING_SURROUNDING_COLOR_ARGB: Long = 0xFFFFFFEE
internal const val COMPAT_DRAWING_CANVAS_COLOR_ARGB: Long = 0xFFF0E0D6

@Composable
fun CompatPostDrawingScreen(
    onSaved: (ImageData) -> Unit,
    onBack: () -> Unit,
    forceLandscape: Boolean = true
) {
    if (forceLandscape) CompatDrawingLandscapeEffect()
    val scope = rememberCoroutineScope()
    val chrome = LocalCompatibilityPalette.current.chrome
    val strokes = remember { mutableStateListOf<CompatDrawingStroke>() }
    val redoStrokes = remember { mutableStateListOf<CompatDrawingStroke>() }
    val currentPoints = remember { mutableStateListOf<CompatDrawingPoint>() }
    var mainBrush by remember {
        mutableStateOf(CompatDrawingBrush(COMPAT_DRAWING_MAIN_COLOR_ARGB, COMPAT_DRAWING_MAIN_SIZE))
    }
    var subBrush by remember {
        mutableStateOf(CompatDrawingBrush(COMPAT_DRAWING_SUB_COLOR_ARGB, COMPAT_DRAWING_SUB_SIZE))
    }
    var selectedBrushIndex by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var overflowOpen by remember { mutableStateOf(false) }
    var paletteOpen by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var saveConfirm by remember { mutableStateOf(false) }
    var closeConfirm by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val activeBrush = if (selectedBrushIndex == 0) mainBrush else subBrush
    fun requestBack() {
        // Both reference APKs ask even when the canvas was never touched.
        closeConfirm = true
    }

    if (helpOpen) {
        PlatformBackHandler { helpOpen = false }
        CompatHelpScreen(onBack = { helpOpen = false })
        return
    }

    PlatformBackHandler { requestBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text("手書き") },
                navigationIcon = {
                    TextButton(onClick = ::requestBack) { Text("戻る", color = Color.White) }
                },
                actions = {
                    IconButton(onClick = { paletteOpen = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = "パレット", tint = Color.White)
                    }
                    IconButton(
                        enabled = strokes.isNotEmpty(),
                        onClick = {
                            val removed = strokes.lastOrNull() ?: return@IconButton
                            strokes.removeAt(strokes.lastIndex)
                            redoStrokes.add(removed)
                        }
                    ) { Icon(Icons.Filled.Undo, contentDescription = "元に戻す", tint = Color.White) }
                    IconButton(
                        enabled = redoStrokes.isNotEmpty(),
                        onClick = {
                            val restored = redoStrokes.lastOrNull() ?: return@IconButton
                            redoStrokes.removeAt(redoStrokes.lastIndex)
                            strokes.add(restored)
                        }
                    ) { Icon(Icons.Filled.Redo, contentDescription = "やり直す", tint = Color.White) }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "その他", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                            shape = RoundedCornerShape(2.dp),
                            containerColor = compatibilityPopupSurface(LocalCompatibilityPalette.current),
                            tonalElevation = 0.dp,
                            shadowElevation = 8.dp
                        ) {
                            DropdownMenuItem(text = { Text("クリアー") }, colors = compatibilityMenuItemColors(), onClick = {
                                overflowOpen = false
                                clearConfirm = true
                            })
                            DropdownMenuItem(text = { Text("保存する") }, colors = compatibilityMenuItemColors(), onClick = {
                                overflowOpen = false
                                saveConfirm = true
                            })
                            DropdownMenuItem(text = { Text("ヘルプ") }, colors = compatibilityMenuItemColors(), onClick = {
                                overflowOpen = false
                                helpOpen = true
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = chrome,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(COMPAT_DRAWING_SURROUNDING_COLOR_ARGB)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(COMPAT_DRAWING_WIDTH_FRACTION)
                    .aspectRatio(COMPAT_DRAWING_ASPECT_RATIO)
                    .background(Color(COMPAT_DRAWING_CANVAS_COLOR_ARGB))
                    .testTag("compat-drawing-canvas")
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(activeBrush) {
                        detectDragGestures(
                            onDragStart = { position ->
                                currentPoints.clear()
                                currentPoints.add(CompatDrawingPoint(position.x, position.y))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPoints.add(CompatDrawingPoint(change.position.x, change.position.y))
                            },
                            onDragEnd = {
                                if (currentPoints.isNotEmpty()) {
                                    strokes.add(CompatDrawingStroke(
                                        colorArgb = Color(activeBrush.colorArgb).toArgb(),
                                        widthPx = activeBrush.widthPx,
                                        points = currentPoints.toList()
                                    ))
                                    redoStrokes.clear()
                                }
                                currentPoints.clear()
                            },
                            onDragCancel = { currentPoints.clear() }
                        )
                    }
            ) {
                fun drawStroke(stroke: CompatDrawingStroke) {
                    val points = stroke.points
                    if (points.size == 1) {
                        drawCircle(
                            color = Color(stroke.colorArgb),
                            radius = stroke.widthPx / 2f,
                            center = Offset(points[0].x, points[0].y)
                        )
                    } else {
                        for (index in 1 until points.size) {
                            val from = points[index - 1]
                            val to = points[index]
                            drawLine(
                                color = Color(stroke.colorArgb),
                                start = Offset(from.x, from.y),
                                end = Offset(to.x, to.y),
                                strokeWidth = stroke.widthPx,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                strokes.forEach(::drawStroke)
                if (currentPoints.isNotEmpty()) {
                    drawStroke(
                        CompatDrawingStroke(
                            Color(activeBrush.colorArgb).toArgb(),
                            activeBrush.widthPx,
                            currentPoints
                        )
                    )
                }
            }
            if (saving) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            error?.let {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xFF646464)).padding(12.dp)
                )
            }
        }
    }

    if (paletteOpen) {
        CompatDrawingPaletteDialog(
            mainBrush = mainBrush,
            subBrush = subBrush,
            selectedBrushIndex = selectedBrushIndex,
            onDismiss = { paletteOpen = false },
            onBrushesChanged = { newMain, newSub, newSelected ->
                mainBrush = newMain
                subBrush = newSub
                selectedBrushIndex = newSelected
            }
        )
    }
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("確認") },
            text = { Text("最初の状態に戻します\n本当によろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    strokes.clear()
                    redoStrokes.clear()
                    currentPoints.clear()
                    clearConfirm = false
                }) { Text("クリアー") }
            },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("キャンセル") } }
        )
    }
    if (saveConfirm) {
        AlertDialog(
            onDismissRequest = { saveConfirm = false },
            title = { Text("確認") },
            text = { Text("添付画像として保存します\n本当によろしいですか？") },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saveConfirm = false
                    saving = true
                    val snapshot = strokes.toList()
                    val size = canvasSize
                    scope.launch {
                        renderCompatDrawingPng(
                            scaleCompatDrawingStrokesForReferencePng(snapshot, size.width, size.height),
                            Color(COMPAT_DRAWING_CANVAS_COLOR_ARGB).toArgb(),
                            COMPAT_DRAWING_OUTPUT_WIDTH_PX,
                            COMPAT_DRAWING_OUTPUT_HEIGHT_PX
                        ).map { drawing ->
                            drawing.copy(fileName = compatDrawingFileName(kotlin.time.Clock.System.now().toEpochMilliseconds()))
                        }.onSuccess(onSaved)
                            .onFailure { error = it.message ?: "手書き画像を保存できませんでした" }
                        saving = false
                    }
                }) { Text("保存する") }
            },
            dismissButton = { TextButton(onClick = { saveConfirm = false }) { Text("キャンセル") } }
        )
    }
    if (closeConfirm) {
        AlertDialog(
            onDismissRequest = { closeConfirm = false },
            title = { Text("確認") },
            text = { Text("画像が保存されていません\n本当によろしいですか？") },
            confirmButton = {
                TextButton(onClick = { closeConfirm = false; onBack() }) { Text("送信画面に戻る") }
            },
            dismissButton = { TextButton(onClick = { closeConfirm = false }) { Text("キャンセル") } }
        )
    }
}

@Composable
internal fun CompatDrawingPaletteDialog(
    mainBrush: CompatDrawingBrush,
    subBrush: CompatDrawingBrush,
    selectedBrushIndex: Int,
    onDismiss: () -> Unit,
    onBrushesChanged: (CompatDrawingBrush, CompatDrawingBrush, Int) -> Unit
) {
    var currentMain by remember(mainBrush) { mutableStateOf(mainBrush) }
    var currentSub by remember(subBrush) { mutableStateOf(subBrush) }
    var selected by remember(selectedBrushIndex) { mutableIntStateOf(selectedBrushIndex.coerceIn(0, 1)) }
    var pickerOpen by remember { mutableStateOf(false) }
    val active = if (selected == 0) currentMain else currentSub
    val activeColor = Color(active.colorArgb)

    fun publish(main: CompatDrawingBrush = currentMain, sub: CompatDrawingBrush = currentSub, index: Int = selected) {
        currentMain = main
        currentSub = sub
        selected = index
        onBrushesChanged(main, sub, index)
    }

    fun updateActive(colorArgb: Long = active.colorArgb, size: Int = active.logicalSize) {
        val updated = CompatDrawingBrush(colorArgb, size.coerceIn(COMPAT_DRAWING_MIN_SIZE, COMPAT_DRAWING_MAX_SIZE))
        if (selected == 0) publish(main = updated) else publish(sub = updated)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            color = compatibilityPopupSurface(LocalCompatibilityPalette.current),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(active.logicalSize.dp)
                                .background(activeColor, RoundedCornerShape(50))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .size(50.dp)
                            .background(Color(currentMain.colorArgb))
                            .semantics { contentDescription = "主筆" }
                            .clickable { publish(index = 0) },
                        contentAlignment = Alignment.Center
                    ) {}
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .size(50.dp)
                            .background(Color(currentSub.colorArgb))
                            .semantics { contentDescription = "副筆" }
                            .clickable { publish(index = 1) },
                        contentAlignment = Alignment.Center
                    ) {}
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        modifier = Modifier.size(50.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        onClick = { pickerOpen = true }
                    ) { Icon(Icons.Filled.Palette, contentDescription = "色見本") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        modifier = Modifier.size(44.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        onClick = {
                            publish(
                                CompatDrawingBrush(COMPAT_DRAWING_MAIN_COLOR_ARGB, COMPAT_DRAWING_MAIN_SIZE),
                                CompatDrawingBrush(COMPAT_DRAWING_SUB_COLOR_ARGB, COMPAT_DRAWING_SUB_SIZE),
                                0
                            )
                        }
                    ) { Icon(Icons.Filled.Refresh, contentDescription = "リセット") }
                }
                CompatDrawingSliderRow(
                    label = "線",
                    value = active.logicalSize,
                    maximum = COMPAT_DRAWING_MAX_SIZE,
                    minimum = COMPAT_DRAWING_MIN_SIZE,
                    onValueChanged = { updateActive(size = it) }
                )
                Spacer(Modifier.height(10.dp))
                CompatDrawingSliderRow(
                    label = "R",
                    value = (activeColor.red * 255f).roundToInt(),
                    maximum = 255,
                    labelColor = Color.Red,
                    onValueChanged = { red ->
                        updateActive(
                            compatDrawingArgb(
                                red,
                                (activeColor.green * 255f).roundToInt(),
                                (activeColor.blue * 255f).roundToInt()
                            )
                        )
                    }
                )
                CompatDrawingSliderRow(
                    label = "G",
                    value = (activeColor.green * 255f).roundToInt(),
                    maximum = 255,
                    labelColor = Color(0xFF008000),
                    onValueChanged = { green ->
                        updateActive(
                            compatDrawingArgb(
                                (activeColor.red * 255f).roundToInt(),
                                green,
                                (activeColor.blue * 255f).roundToInt()
                            )
                        )
                    }
                )
                CompatDrawingSliderRow(
                    label = "B",
                    value = (activeColor.blue * 255f).roundToInt(),
                    maximum = 255,
                    labelColor = Color.Blue,
                    onValueChanged = { blue ->
                        updateActive(
                            compatDrawingArgb(
                                (activeColor.red * 255f).roundToInt(),
                                (activeColor.green * 255f).roundToInt(),
                                blue
                            )
                        )
                    }
                )
            }
        }
    }

    if (pickerOpen) {
        CompatDrawingPresetPicker(
            onDismiss = { pickerOpen = false },
            onSelected = { color ->
                updateActive(colorArgb = color)
                pickerOpen = false
            }
        )
    }
}

@Composable
private fun CompatDrawingSliderRow(
    label: String,
    value: Int,
    maximum: Int,
    minimum: Int = 0,
    labelColor: Color = LocalCompatibilityPalette.current.text,
    onValueChanged: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = labelColor, modifier = Modifier.width(30.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.roundToInt().coerceIn(minimum, maximum)) },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = (maximum - minimum - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f)
        )
        Text(value.toString(), modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun CompatDrawingPresetPicker(
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            color = compatibilityPopupSurface(LocalCompatibilityPalette.current),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                compatDrawingReferencePresets.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.Center) {
                        row.forEach { color ->
                            Box(
                                Modifier
                                    .padding(10.dp)
                                    .size(50.dp)
                                    .background(Color(color), RoundedCornerShape(50))
                                    .testTag("compat-drawing-preset")
                                    .clickable { onSelected(color) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun compatDrawingArgb(red: Int, green: Int, blue: Int): Long =
    0xFF000000L or
        ((red.coerceIn(0, 255).toLong()) shl 16) or
        ((green.coerceIn(0, 255).toLong()) shl 8) or
        blue.coerceIn(0, 255).toLong()
