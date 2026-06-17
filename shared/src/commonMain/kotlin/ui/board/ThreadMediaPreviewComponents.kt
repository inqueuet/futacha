package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ThreadMediaPreviewDialog(
    state: ThreadMediaPreviewDialogState,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onSave: (MediaPreviewEntry) -> Unit
) {
    when (state.entry.mediaType) {
        MediaType.Image -> {
            val displayTitle by rememberMediaPreviewDisplayTitle(state.entry)
            ImagePreviewDialog(
                entry = state.entry,
                displayTitle = displayTitle,
                currentIndex = state.currentIndex,
                totalCount = state.totalCount,
                onDismiss = onDismiss,
                onNavigateNext = onNavigateNext,
                onNavigatePrevious = onNavigatePrevious,
                onSave = { onSave(state.entry) },
                isSaveEnabled = state.isSaveEnabled,
                isSaveInProgress = state.isSaveInProgress
            )
        }

        MediaType.Video -> VideoPreviewDialog(
            entry = state.entry,
            currentIndex = state.currentIndex,
            totalCount = state.totalCount,
            onDismiss = onDismiss,
            onNavigateNext = onNavigateNext,
            onNavigatePrevious = onNavigatePrevious,
            onSave = { onSave(state.entry) },
            isSaveEnabled = state.isSaveEnabled,
            isSaveInProgress = state.isSaveInProgress
        )
    }
}

@Composable
private fun rememberMediaPreviewDisplayTitle(entry: MediaPreviewEntry) =
    produceState(
        initialValue = entry.title,
        key1 = entry.url,
        key2 = entry.postId,
        key3 = entry.messageHtml
    ) {
        value = withContext(AppDispatchers.parsing) {
            resolveMediaPreviewDisplayTitle(entry)
        }
    }

@Composable
internal fun ThreadMediaPreviewDialogFrame(
    navigationKey: String,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    isSwipeNavigationEnabled: Boolean = true,
    isTapNavigationEnabled: Boolean = true,
    isNavigationOverlayVisible: Boolean = true,
    navigationOverlayPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    swipeNavigationPadding: PaddingValues = PaddingValues(),
    containerModifier: Modifier = Modifier,
    content: @Composable BoxScope.(IntSize) -> Unit
) {
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val swipeThresholdPx = rememberSwipeNavigationThresholdPx()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val swipeStartPaddingPx = remember(density, layoutDirection, swipeNavigationPadding) {
        with(density) { swipeNavigationPadding.calculateLeftPadding(layoutDirection).toPx() }
    }
    val swipeTopPaddingPx = remember(density, swipeNavigationPadding) {
        with(density) { swipeNavigationPadding.calculateTopPadding().toPx() }
    }
    val swipeEndPaddingPx = remember(density, layoutDirection, swipeNavigationPadding) {
        with(density) { swipeNavigationPadding.calculateRightPadding(layoutDirection).toPx() }
    }
    val swipeBottomPaddingPx = remember(density, swipeNavigationPadding) {
        with(density) { swipeNavigationPadding.calculateBottomPadding().toPx() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { previewSize = it }
                .pointerInput(navigationKey, isSwipeNavigationEnabled) {
                    if (!isSwipeNavigationEnabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                        )
                        if (!isSwipeNavigationStartWithinBounds(
                                position = down.position,
                                containerSize = previewSize,
                                startPaddingPx = swipeStartPaddingPx,
                                topPaddingPx = swipeTopPaddingPx,
                                endPaddingPx = swipeEndPaddingPx,
                                bottomPaddingPx = swipeBottomPaddingPx
                            )
                        ) {
                            return@awaitEachGesture
                        }
                        val pointerId = down.id
                        var totalDx = 0f
                        var totalDy = 0f
                        var cancelled = false

                        while (true) {
                            val event = awaitPointerEvent(
                                pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                            )
                            if (event.changes.count { it.pressed } > 1) {
                                cancelled = true
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                                ?: continue
                            if (!change.pressed || event.changes.none { it.pressed }) {
                                break
                            }
                            val delta = change.positionChange()
                            totalDx += delta.x
                            totalDy += delta.y
                        }
                        if (cancelled) return@awaitEachGesture
                        when (resolveSwipeNavigationAction(totalDx, totalDy, swipeThresholdPx)) {
                            SwipeNavigationAction.Next -> onNavigateNext()
                            SwipeNavigationAction.Previous -> onNavigatePrevious()
                            SwipeNavigationAction.None -> Unit
                        }
                    }
                }
                .then(containerModifier)
        ) {
            content(previewSize)
            if (isNavigationOverlayVisible && isTapNavigationEnabled) {
                ThreadMediaPreviewNavigationOverlay(
                    navigationKey = navigationKey,
                    onNavigateNext = onNavigateNext,
                    onNavigatePrevious = onNavigatePrevious,
                    isTapNavigationEnabled = isTapNavigationEnabled,
                    paddingValues = navigationOverlayPadding
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ThreadMediaPreviewNavigationOverlay(
    navigationKey: String,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    isTapNavigationEnabled: Boolean,
    paddingValues: PaddingValues
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThreadMediaPreviewNavigationZone(
            navigationKey = "$navigationKey-prev",
            onNavigate = onNavigatePrevious,
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "前の添付へ移動",
            isTapNavigationEnabled = isTapNavigationEnabled
        )
        Spacer(modifier = Modifier.weight(1f))
        ThreadMediaPreviewNavigationZone(
            navigationKey = "$navigationKey-next",
            onNavigate = onNavigateNext,
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = "次の添付へ移動",
            isTapNavigationEnabled = isTapNavigationEnabled
        )
    }
}

@Composable
private fun ThreadMediaPreviewNavigationZone(
    navigationKey: String,
    onNavigate: () -> Unit,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isTapNavigationEnabled: Boolean
) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .pointerInput(navigationKey, isTapNavigationEnabled) {
                if (!isTapNavigationEnabled) return@pointerInput
                detectTapGestures(onTap = { onNavigate() })
            },
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onNavigate,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.28f),
                contentColor = Color.White.copy(alpha = 0.88f)
            )
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
private fun rememberSwipeNavigationThresholdPx(): Float {
    val density = LocalDensity.current
    return remember(density) {
        density.run { 56.dp.toPx() }
    }
}

@Composable
internal fun ThreadMediaPreviewHeader(
    title: String,
    currentIndex: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 6.dp,
        modifier = modifier
            .padding(top = 32.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${currentIndex + 1}/${totalCount}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun ThreadMediaPreviewCloseButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onDismiss,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "プレビューを閉じる"
        )
    }
}
