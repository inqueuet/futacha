@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
// The reference help's 手書き section, which the drawing screens of the modes other than としあき(仮) show (L70).
internal const val COMPAT_DRAWING_HELP_SECTION = "drawing"
private const val COMPAT_DRAWING_SCREEN_TAG = "CompatPostDrawingScreen"

@Composable
fun CompatPostDrawingScreen(
    onSaved: suspend (ImageData) -> Unit,
    onBack: () -> Unit,
    forceLandscape: Boolean = true,
    /**
     * A section of the reference help that ヘルプ shows on its own (L70: ふたちゃ, ふたばー風 and ふたったー風 show the
     * 手書き section); null opens the whole reference help, as としあき(仮) does.
     */
    helpSectionId: String? = null,
    /**
     * Where the drawing is kept while it is drawn, so one lost with the app is offered back the next time (L69); null keeps
     * nothing, as before.
     */
    recoveryFileSystem: FileSystem? = null
) {
    if (forceLandscape) CompatDrawingLandscapeEffect()
    val scope = rememberCoroutineScope()
    val chrome = LocalCompatibilityPalette.current.chrome
    // All points and pen-down widths live in the fixed PNG space; only input/rendering use the current canvas size.
    val strokes = remember { mutableStateListOf<CompatDrawingStroke>() }
    val redoStrokes = remember { mutableStateListOf<CompatDrawingStroke>() }
    val currentPoints = remember { mutableStateListOf<CompatDrawingPoint>() }
    var currentStrokeWidth by remember { mutableFloatStateOf(0f) }
    // Finished strokes are drawn from a bitmap; only the active one is live (E-12).
    val strokeCache = remember { CompatDrawingStrokeCache() }
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
    // L69: the drawing is kept in a file while it is drawn. Until the kept one has been checked (and, if there is one,
    // the person has chosen) nothing is drawn or kept, so the check never overwrites it.
    val recovery = remember(recoveryFileSystem) { recoveryFileSystem?.let { CompatDrawingRecovery(it) } }
    var recoveryPending by remember(recovery) { mutableStateOf(recovery != null) }
    var recoveryOffer by remember(recovery) { mutableStateOf<CompatDrawingRecovered?>(null) }
    val recoveryState = remember(recovery) { CompatDrawingRecoveryState() }

    val activeBrush = if (selectedBrushIndex == 0) mainBrush else subBrush
    fun drawingSnapshot() = CompatDrawingSnapshot(strokes.toList(), mainBrush, subBrush, selectedBrushIndex)
    fun requestBack() {
        if (saving) return
        // Both reference APKs ask even when the canvas was never touched.
        closeConfirm = true
    }

    if (recovery != null) {
        LaunchedEffect(recovery) {
            val kept = try {
                recovery.load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Logger.e(COMPAT_DRAWING_SCREEN_TAG, "Failed to read the kept drawing", failure)
                null
            }
            if (kept == null) {
                recoveryState.kept = drawingSnapshot()
                recoveryPending = false
            } else {
                recoveryOffer = kept
            }
        }
        // Kept a moment after each change, so an app ended by the system (which closes nothing) still has it.
        LaunchedEffect(recovery, recoveryPending) {
            if (recoveryPending) return@LaunchedEffect
            snapshotFlow { drawingSnapshot() }.collectLatest { current ->
                if (current == recoveryState.kept) return@collectLatest
                delay(COMPAT_DRAWING_RECOVERY_DELAY_MILLIS)
                if (current != recoveryState.kept) {
                    recovery.keep(current)
                    recoveryState.kept = current
                }
            }
        }
        // And as the screen goes without being left on purpose (the mode closing it, the activity ending).
        DisposableEffect(recovery) {
            onDispose {
                if (!recoveryPending) {
                    val current = drawingSnapshot()
                    if (current != recoveryState.kept) recovery.keep(current)
                }
            }
        }
    }

    if (helpOpen) {
        PlatformBackHandler { helpOpen = false }
        if (helpSectionId == null) CompatHelpScreen(onBack = { helpOpen = false })
        else CompatDrawingHelpScreen(helpSectionId, onBack = { helpOpen = false })
        return
    }

    PlatformBackHandler { requestBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text("手書き") },
                navigationIcon = {
                    TextButton(onClick = ::requestBack, enabled = !saving) { Text("戻る", color = Color.White) }
                },
                actions = {
                    IconButton(onClick = { paletteOpen = true }, enabled = !saving && !recoveryPending) {
                        Icon(Icons.Filled.Palette, contentDescription = "パレット", tint = Color.White)
                    }
                    IconButton(
                        enabled = !saving && strokes.isNotEmpty(),
                        onClick = {
                            val removed = strokes.lastOrNull() ?: return@IconButton
                            strokes.removeAt(strokes.lastIndex)
                            redoStrokes.add(removed)
                        }
                    ) { Icon(Icons.Filled.Undo, contentDescription = "元に戻す", tint = Color.White) }
                    IconButton(
                        enabled = !saving && redoStrokes.isNotEmpty(),
                        onClick = {
                            val restored = redoStrokes.lastOrNull() ?: return@IconButton
                            redoStrokes.removeAt(redoStrokes.lastIndex)
                            strokes.add(restored)
                        }
                    ) { Icon(Icons.Filled.Redo, contentDescription = "やり直す", tint = Color.White) }
                    Box {
                        IconButton(onClick = { overflowOpen = true }, enabled = !saving && !recoveryPending) {
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
                    .pointerInput(activeBrush, saving, recoveryPending) {
                        if (saving || recoveryPending) return@pointerInput
                        detectCompatDrawingStrokes(
                            onStart = { position ->
                                currentPoints.clear()
                                currentStrokeWidth = compatDrawingReferenceWidth(activeBrush.widthPx, canvasSize.width, canvasSize.height)
                                currentPoints.add(compatDrawingReferencePoint(
                                    CompatDrawingPoint(position.x, position.y), canvasSize.width, canvasSize.height))
                            },
                            onPoint = { position ->
                                val point = compatDrawingReferencePoint(
                                    CompatDrawingPoint(position.x, position.y), canvasSize.width, canvasSize.height)
                                if (currentPoints.lastOrNull() != point) currentPoints.add(point)
                            },
                            onEnd = {
                                if (currentPoints.isNotEmpty()) {
                                    strokes.add(CompatDrawingStroke(
                                        colorArgb = Color(activeBrush.colorArgb).toArgb(),
                                        widthPx = currentStrokeWidth,
                                        points = currentPoints.toList()
                                    ))
                                    redoStrokes.clear()
                                }
                                currentPoints.clear()
                            },
                            onCancel = { currentPoints.clear() }
                        )
                    }
            ) {
                val logicalSize = Size(COMPAT_DRAWING_OUTPUT_WIDTH_PX.toFloat(), COMPAT_DRAWING_OUTPUT_HEIGHT_PX.toFloat())
                strokeCache.draw(this, strokes, logicalSize)
                if (currentPoints.isNotEmpty()) {
                    withTransform({ scale(size.width / logicalSize.width, size.height / logicalSize.height, Offset.Zero) }) {
                        drawCompatDrawingStroke(
                            CompatDrawingStroke(
                                Color(activeBrush.colorArgb).toArgb(),
                                currentStrokeWidth,
                                currentPoints
                            )
                        )
                    }
                }
            }
            if (saving) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)).testTag("compat-drawing-saving"), contentAlignment = Alignment.Center) {
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
        FutachaAppLockAwareWindow { AlertDialog(
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
        ) }
    }
    if (saveConfirm) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { saveConfirm = false },
            title = { Text("確認") },
            text = { Text("添付画像として保存します\n本当によろしいですか？") },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    if (saving) return@TextButton
                    saveConfirm = false
                    saving = true
                    closeConfirm = false
                    error = null
                    val snapshot = strokes.toList()
                    // L69: exactly this drawing is on file while it is saved, in case the save is cut off with the screen.
                    val kept = drawingSnapshot()
                    if (recovery != null && !recoveryPending && kept != recoveryState.kept) {
                        recovery.keep(kept)
                        recoveryState.kept = kept
                    }
                    scope.launch {
                        // saving must be released even when rendering or the save callback throws.
                        try {
                            val drawing = renderCompatDrawingPng(
                                scaleCompatDrawingStrokesForReferencePng(snapshot, COMPAT_DRAWING_OUTPUT_WIDTH_PX, COMPAT_DRAWING_OUTPUT_HEIGHT_PX),
                                Color(COMPAT_DRAWING_CANVAS_COLOR_ARGB).toArgb(),
                                COMPAT_DRAWING_OUTPUT_WIDTH_PX,
                                COMPAT_DRAWING_OUTPUT_HEIGHT_PX
                            ).map { drawing ->
                                drawing.copy(fileName = compatDrawingFileName(kotlin.time.Clock.System.now().toEpochMilliseconds()), isHandwriting = true)
                            }.getOrThrow()
                            // Completion acknowledges attachment/draft persistence, not just a launched job.
                            onSaved(drawing)
                            // Saved: nothing to offer back. Still the drawing on the canvas, so only a change keeps again.
                            if (recovery != null && !recoveryPending) {
                                recovery.discard()
                                recoveryState.kept = kept
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            error = failure.toCompatUserMessage("手書き画像を保存できませんでした")
                        } finally {
                            saving = false
                        }
                    }
                }) { Text("保存する") }
            },
            dismissButton = { TextButton(onClick = { saveConfirm = false }) { Text("キャンセル") } }
        ) }
    }
    if (closeConfirm) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { closeConfirm = false },
            title = { Text("確認") },
            text = { Text("画像が保存されていません\n本当によろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    closeConfirm = false
                    // Left on purpose (L69): the drawing is not kept, and the screen going does not keep it again. A kept
                    // drawing not yet checked (the check is still reading) is left for the next screen.
                    if (recovery != null && !recoveryPending) {
                        recovery.discard()
                        recoveryState.kept = drawingSnapshot()
                    }
                    onBack()
                }) { Text("送信画面に戻る") }
            },
            dismissButton = { TextButton(onClick = { closeConfirm = false }) { Text("キャンセル") } }
        ) }
    }
    recoveryOffer?.let { offer ->
        FutachaAppLockAwareWindow { AlertDialog(
            modifier = Modifier.testTag("compat-drawing-recovery"),
            // Back leaves the screen and decides nothing: the drawing stays for the next time. A tap beside the
            // question does nothing, so it is not lost (or left) by accident.
            onDismissRequest = onBack,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text("確認") },
            text = { Text(compatDrawingRecoveryMessage(offer)) },
            confirmButton = {
                TextButton(onClick = {
                    strokes.clear()
                    strokes.addAll(offer.snapshot.strokes)
                    redoStrokes.clear()
                    currentPoints.clear()
                    mainBrush = offer.snapshot.mainBrush
                    subBrush = offer.snapshot.subBrush
                    selectedBrushIndex = offer.snapshot.selectedBrushIndex
                    // Still on file until it is saved, left or changed.
                    recoveryState.kept = offer.snapshot
                    recoveryOffer = null
                    recoveryPending = false
                }) { Text("復元する") }
            },
            dismissButton = {
                TextButton(onClick = {
                    recovery?.discard()
                    recoveryState.kept = drawingSnapshot()
                    recoveryOffer = null
                    recoveryPending = false
                }) { Text("破棄する") }
            }
        ) }
    }
}

/** What the drawing screen last had kept (or saved / left on purpose): only a change from it is kept again (L69). */
private class CompatDrawingRecoveryState {
    var kept: CompatDrawingSnapshot? = null
}

/** One section of the reference help (L70), without the whole help's search, report and store actions. */
@Composable
private fun CompatDrawingHelpScreen(sectionId: String, onBack: () -> Unit) {
    val openUrl = rememberUrlLauncher()
    val palette = LocalCompatibilityPalette.current
    val html = remember(palette, sectionId) { compatibilityReferenceHelpSectionHtml(palette, sectionId) }
    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text("ヘルプ（手書き）") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "戻る") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryTeal,
                    titleContentColor = SecondaryChromeContent,
                    navigationIconContentColor = SecondaryChromeContent
                )
            )
        }
    ) { padding ->
        CompatReferenceChangeLogView(
            html,
            Modifier.fillMaxSize().padding(padding).testTag("compat-drawing-help"),
            openUrl
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

    FutachaAppLockAwareWindow { Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
    } }

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
    FutachaAppLockAwareWindow { Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
    } }
}

private fun compatDrawingArgb(red: Int, green: Int, blue: Int): Long =
    0xFF000000L or
        ((red.coerceIn(0, 255).toLong()) shl 16) or
        ((green.coerceIn(0, 255).toLong()) shl 8) or
        blue.coerceIn(0, 255).toLong()
