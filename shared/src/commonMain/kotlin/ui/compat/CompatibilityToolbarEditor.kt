package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatToolbarGlyph
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatToolbarMaster
import com.valoser.futacha.shared.compat.compatToolbarGlyph
import com.valoser.futacha.shared.compat.compatToolbarShowsOverflow
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class CompatToolbarDragPlacement(
    val targetIndex: Int,
    val visualOffsetPx: Float
)

/**
 * Resolves the drag against the immutable order captured at pointer-down.
 * Rebuilding from that order avoids feeding a just-moved row position back
 * into the next pointer event, which caused the high-speed oscillation seen
 * in the review video.
 */
internal fun compatToolbarDragPlacement(
    startIndex: Int,
    itemCount: Int,
    travelPx: Float,
    rowHeightPx: Float
): CompatToolbarDragPlacement {
    if (itemCount <= 0 || rowHeightPx <= 0f) {
        return CompatToolbarDragPlacement(startIndex.coerceAtLeast(0), 0f)
    }
    val halfRow = rowHeightPx / 2f
    val requestedDelta = when {
        travelPx > halfRow -> ((travelPx + halfRow) / rowHeightPx).toInt()
        travelPx < -halfRow -> -(((-travelPx + halfRow) / rowHeightPx).toInt())
        else -> 0
    }
    val target = (startIndex + requestedDelta).coerceIn(0, itemCount - 1)
    val movedRows = target - startIndex
    return CompatToolbarDragPlacement(
        targetIndex = target,
        visualOffsetPx = (travelPx - movedRows * rowHeightPx)
            .coerceIn(-rowHeightPx, rowHeightPx)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatToolbarEditorScreen(
    surface: CompatToolbarSurface,
    store: CompatibilityStore,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val palette = LocalCompatibilityPalette.current
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 60.dp.toPx() }
    val edgeScrollStepPx = with(density) { 6.dp.toPx() }
    val edgeScrollZonePx = with(density) { 56.dp.toPx() }
    val listState = rememberLazyListState()
    val master = remember(surface) { compatToolbarMaster(surface).associateBy { it.key } }
    var items by remember(surface) { mutableStateOf<List<CompatToolbarItem>>(emptyList()) }
    var loaded by remember(surface) { mutableStateOf(false) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragTravel by remember { mutableFloatStateOf(0f) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragStartItems by remember { mutableStateOf<List<CompatToolbarItem>>(emptyList()) }
    var listBounds by remember { mutableStateOf(0f to 0f) }
    var pendingPersist by remember(surface) { mutableStateOf<Job?>(null) }
    var edgeScrollJob by remember(surface) { mutableStateOf<Job?>(null) }
    var edgeScrollDirection by remember(surface) { mutableStateOf(0) }

    fun applyDragTravel() {
        val key = draggedKey ?: return
        val placement = compatToolbarDragPlacement(
            startIndex = dragStartIndex,
            itemCount = dragStartItems.size,
            travelPx = dragTravel,
            rowHeightPx = rowHeightPx
        )
        val sourceIndex = dragStartItems.indexOfFirst { it.key == key }
        if (sourceIndex < 0) return
        items = dragStartItems.toMutableList().apply {
            add(placement.targetIndex, removeAt(sourceIndex))
        }
        dragOffset = placement.visualOffsetPx
    }

    fun updateEdgeScroll(direction: Int) {
        if (edgeScrollDirection == direction && edgeScrollJob?.isActive == true) return
        edgeScrollJob?.cancel()
        edgeScrollDirection = direction
        edgeScrollJob = if (direction == 0) null else scope.launch {
            while (isActive) {
                val consumed = listState.scrollBy(direction * edgeScrollStepPx)
                if (consumed != 0f && draggedKey != null) {
                    dragTravel += consumed
                    applyDragTravel()
                }
                delay(16)
            }
        }
    }

    DisposableEffect(surface) {
        onDispose { edgeScrollJob?.cancel() }
    }

    fun persist(next: List<CompatToolbarItem>) {
        val normalized = next.mapIndexed { index, item -> item.copy(position = index) }
        items = normalized
        val previous = pendingPersist
        pendingPersist = scope.launch {
            previous?.join()
            store.saveToolbar(surface, normalized)
        }
    }

    fun leaveAfterSaving() {
        updateEdgeScroll(0)
        val lastWrite = pendingPersist
        scope.launch {
            lastWrite?.join()
            onBack()
        }
    }

    LaunchedEffect(surface) {
        items = store.loadToolbar(surface)
        loaded = true
    }

    PlatformBackHandler(enabled = loaded) { leaveAfterSaving() }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text("ツールバー編集") },
                navigationIcon = {
                    IconButton(onClick = ::leaveAfterSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.chrome,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("読み込み中…") }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("compat-toolbar-editor-list").onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        listBounds = bounds.top to bounds.bottom
                    }
                ) {
                    items(items, key = { it.key }) { item ->
                        val isDragged = item.key == draggedKey
                        var itemHandleTop by remember(item.key) { mutableFloatStateOf(0f) }
                        Row(
                            // sample/1.apk's catalog/thread/viewer toolbar
                            // editor uses a 60dp list item (including the
                            // 20dp left inset and 50dp drag handle).
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                                .testTag("compat-toolbar-editor-row-${item.key}")
                                .graphicsLayer {
                                    alpha = if (isDragged) 0.1f else 1f
                                    translationY = if (isDragged) dragOffset else 0f
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).fillMaxHeight()
                                    .toggleable(
                                        value = item.active,
                                        role = Role.Checkbox,
                                        onValueChange = { checked ->
                                            persist(items.map { candidate ->
                                                if (candidate.key == item.key) candidate.copy(active = checked) else candidate
                                            })
                                        }
                                    )
                                    .testTag("compat-toolbar-toggle-${item.key}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(
                                    Modifier.width(20.dp)
                                        .testTag("compat-toolbar-editor-left-inset-${item.key}")
                                )
                                Checkbox(
                                    checked = item.active,
                                    onCheckedChange = null,
                                    modifier = Modifier.testTag("compat-toolbar-checkbox-${item.key}")
                                )
                                CompatToolbarArtworkIcon(
                                    // ToolbarData always previews the bypass-on
                                    // artwork in the editor; the live toolbar
                                    // swaps it according to the current setting.
                                    artwork = compatToolbarArtwork(
                                        surface,
                                        item.key,
                                        selected = item.key == "bypass"
                                    ),
                                    contentDescription = null,
                                    tint = palette.text,
                                    preserveResourceColors = false,
                                    modifier = Modifier.size(30.dp).testTag("compat-toolbar-editor-icon-${item.key}")
                                )
                                Text(
                                    master[item.key]?.label ?: item.key,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "${master[item.key]?.label ?: item.key}を並び替え",
                                tint = palette.text.copy(alpha = 0.62f),
                                modifier = Modifier.size(50.dp).testTag("compat-toolbar-handle-${item.key}")
                                    .onGloballyPositioned { itemHandleTop = it.boundsInRoot().top }
                                    .pointerInput(item.key) {
                                        detectDragGestures(
                                            onDragStart = { pointerOffset ->
                                                draggedKey = item.key
                                                dragStartItems = items
                                                dragStartIndex = items.indexOfFirst { it.key == item.key }
                                                dragTravel = 0f
                                                dragOffset = 0f
                                                dragPointerY = itemHandleTop + pointerOffset.y
                                            },
                                            onDragCancel = {
                                                updateEdgeScroll(0)
                                                items = dragStartItems
                                                draggedKey = null
                                                dragStartIndex = -1
                                                dragTravel = 0f
                                                dragOffset = 0f
                                            },
                                            onDragEnd = {
                                                updateEdgeScroll(0)
                                                val committed = items
                                                draggedKey = null
                                                dragStartIndex = -1
                                                dragTravel = 0f
                                                dragOffset = 0f
                                                persist(committed)
                                            }
                                        ) { change, amount ->
                                            change.consume()
                                            dragTravel += amount.y
                                            dragPointerY += amount.y
                                            applyDragTravel()
                                            val (top, bottom) = listBounds
                                            updateEdgeScroll(
                                                when {
                                                    dragPointerY < top + edgeScrollZonePx -> -1
                                                    dragPointerY > bottom - edgeScrollZonePx -> 1
                                                    else -> 0
                                                }
                                            )
                                        }
                                    }
                            )
                        }
                        HorizontalDivider()
                    }
                }
                CompatToolbarPreviewRow(
                    surface = surface,
                    items = items.filterNot(CompatToolbarItem::active),
                    master = master,
                    showMore = false,
                    active = false,
                    palette = palette
                )
                CompatToolbarPreviewRow(
                    surface = surface,
                    items = items.filter(CompatToolbarItem::active),
                    master = master,
                    showMore = compatToolbarShowsOverflow(surface, items),
                    active = true,
                    palette = palette
                )
            }
        }
    }
}

@Composable
private fun CompatToolbarPreviewRow(
    surface: CompatToolbarSurface,
    items: List<CompatToolbarItem>,
    master: Map<String, com.valoser.futacha.shared.compat.CompatToolbarMasterItem>,
    showMore: Boolean,
    active: Boolean,
    palette: CompatibilityPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp)
            .background(palette.chrome.copy(alpha = 0.14f))
            .testTag(if (active) "compat-toolbar-preview-active" else "compat-toolbar-preview-inactive"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                CompatToolbarArtworkIcon(
                    artwork = compatToolbarArtwork(
                        surface,
                        item.key,
                        selected = item.key == "bypass"
                    ),
                    contentDescription = master[item.key]?.label,
                    tint = palette.text.copy(alpha = if (active) 1f else 0.5f),
                    modifier = Modifier.size(24.dp)
                        .testTag("compat-toolbar-preview-${if (active) "active" else "inactive"}-${item.key}")
                )
            }
        }
        if (showMore) {
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                CompatToolbarArtworkIcon(
                    artwork = compatToolbarArtwork(surface, "other"),
                    contentDescription = "その他",
                    tint = palette.text,
                    modifier = Modifier.size(24.dp).testTag("compat-toolbar-preview-active-more")
                )
            }
        }
    }
}

internal fun compatToolbarIcon(key: String): ImageVector = when (compatToolbarGlyph(key)) {
    CompatToolbarGlyph.EDIT -> Icons.Filled.Edit
    CompatToolbarGlyph.SEND -> Icons.Filled.Send
    CompatToolbarGlyph.REFRESH -> Icons.Filled.Refresh
    CompatToolbarGlyph.SEARCH -> Icons.Filled.Search
    CompatToolbarGlyph.SORT -> Icons.AutoMirrored.Filled.Sort
    CompatToolbarGlyph.BOARD -> Icons.Filled.Book
    CompatToolbarGlyph.TABS -> Icons.Filled.ViewCarousel
    CompatToolbarGlyph.PRIVACY -> Icons.Filled.Security
    CompatToolbarGlyph.NETWORK -> Icons.Filled.Speed
    CompatToolbarGlyph.CHECK_UPDATES -> Icons.Filled.Sync
    CompatToolbarGlyph.UNDO -> CompatReferenceCatalogUndoIcon
    CompatToolbarGlyph.HISTORY -> CompatReferenceCatalogDroppedIcon
    CompatToolbarGlyph.FILTER -> Icons.Filled.FilterList
    CompatToolbarGlyph.NG_TOGGLE -> Icons.Filled.Block
    CompatToolbarGlyph.DRAWER -> Icons.Filled.Menu
    CompatToolbarGlyph.TOP -> Icons.Filled.VerticalAlignTop
    CompatToolbarGlyph.PAGE_UP -> Icons.Filled.KeyboardArrowUp
    CompatToolbarGlyph.PAGE_DOWN -> Icons.Filled.KeyboardArrowDown
    CompatToolbarGlyph.BOTTOM -> Icons.Filled.VerticalAlignBottom
    CompatToolbarGlyph.GALLERY -> Icons.Filled.GridView
    CompatToolbarGlyph.BACK_TO_POST -> Icons.Filled.Image
    CompatToolbarGlyph.SCROLL -> CompatReferenceThreadScrollIcon
    CompatToolbarGlyph.SCREEN -> CompatReferenceViewerScreenIcon
    CompatToolbarGlyph.CLOSE -> Icons.Filled.Close
    CompatToolbarGlyph.AUTO_SCROLL -> Icons.Filled.PlayArrow
    CompatToolbarGlyph.PREVIOUS -> Icons.Filled.KeyboardArrowLeft
    CompatToolbarGlyph.NEXT -> Icons.Filled.KeyboardArrowRight
    CompatToolbarGlyph.SHARE -> Icons.Filled.Share
    CompatToolbarGlyph.DOWNLOAD -> Icons.Filled.Download
    CompatToolbarGlyph.ATTACH -> Icons.Filled.Image
    CompatToolbarGlyph.UPLOAD -> Icons.Filled.CloudUpload
    CompatToolbarGlyph.INFO -> Icons.Filled.Info
    CompatToolbarGlyph.VOICE_INPUT -> Icons.Filled.Mic
    CompatToolbarGlyph.DEVICE_INFO -> Icons.Filled.PhoneAndroid
    CompatToolbarGlyph.MORE -> Icons.Filled.MoreVert
}
