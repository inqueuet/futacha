package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.rememberUrlLauncher

@Composable
internal fun ImagePreviewDialog(
    entry: MediaPreviewEntry,
    displayTitle: String = entry.title,
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onSave: (() -> Unit)? = null,
    isSaveEnabled: Boolean = true,
    isSaveInProgress: Boolean = false
) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = LocalFutachaImageLoader.current
    val urlLauncher = rememberUrlLauncher()
    var isZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(entry.url) { isZoomed = false }

    ThreadMediaPreviewDialogFrame(
        navigationKey = entry.url,
        onDismiss = onDismiss,
        onNavigateNext = onNavigateNext,
        onNavigatePrevious = onNavigatePrevious,
        isSwipeNavigationEnabled = !isZoomed,
        isTapNavigationEnabled = !isZoomed,
        navigationOverlayPadding = PaddingValues(start = 8.dp, top = 72.dp, end = 8.dp, bottom = 8.dp),
    ) { previewSize ->
        val requestSize = remember(previewSize) {
            resolveImagePreviewRequestSize(previewSize)
        }
        val previewRequest = remember(platformContext, entry.url, requestSize) {
            requestSize?.let { size ->
                ImageRequest.Builder(platformContext)
                    .data(entry.url)
                    // The viewer is an interactive surface. A crossfade keeps the
                    // old frame on screen while the new one is already ready and
                    // makes a quick swipe feel like loading has stalled.
                    .crossfade(false)
                    .size(size.width, size.height)
                    .build()
            }
        }
        val thumbnailUrl = entry.previewUrl
            ?.takeIf { it.isNotBlank() && it != entry.url }
        val thumbnailRequest = remember(platformContext, thumbnailUrl, requestSize) {
            if (thumbnailUrl == null || requestSize == null) {
                null
            } else {
                ImageRequest.Builder(platformContext)
                    .data(thumbnailUrl)
                    .crossfade(false)
                    .size(requestSize.width, requestSize.height)
                    .build()
            }
        }
        val painter = rememberAsyncImagePainter(
            model = previewRequest,
            imageLoader = imageLoader
        )
        val thumbnailPainter = rememberAsyncImagePainter(
            model = thumbnailRequest,
            imageLoader = imageLoader
        )
        val painterState by painter.state.collectAsState()
        val thumbnailPainterState by thumbnailPainter.state.collectAsState()
        val isLoadingState = previewRequest == null || painterState is AsyncImagePainter.State.Loading
        val isErrorState = painterState is AsyncImagePainter.State.Error
        val loadFailureDetail = formatMediaLoadFailure(
            (painterState as? AsyncImagePainter.State.Error)?.result?.throwable
        )
        val targetContentScale by remember(previewSize, painterState) {
            derivedStateOf {
                val imageSize = painter.intrinsicSize
                val containerWidth = previewSize.width.toFloat()
                val containerHeight = previewSize.height.toFloat()
                if (
                    imageSize.width > 0f &&
                    imageSize.height > 0f &&
                    containerWidth > 0f &&
                    containerHeight > 0f
                ) {
                    val imageAspect = imageSize.width / imageSize.height
                    val containerAspect = containerWidth / containerHeight
                    if (imageAspect < containerAspect) {
                        ContentScale.FillHeight
                    } else {
                        ContentScale.FillWidth
                    }
                } else {
                    ContentScale.Fit
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            ImagePreviewTransformSurface(
                resetKey = entry.url,
                viewportSize = previewSize,
                painter = painter,
                thumbnailPainter = thumbnailPainter,
                showThumbnail = thumbnailRequest != null &&
                    thumbnailPainterState !is AsyncImagePainter.State.Error &&
                    thumbnailPainterState !is AsyncImagePainter.State.Empty,
                contentScale = targetContentScale,
                isTargetError = isErrorState,
                onZoomedChanged = { isZoomed = it }
            )
            if (isLoadingState) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isErrorState) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "画像を読み込めませんでした",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    loadFailureDetail?.let { detail ->
                        Text(
                            text = detail,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    TextButton(onClick = {
                        AnalyticsTracker.uiControl("image_preview_open_external", "画像をブラウザで開く")
                        urlLauncher(entry.url)
                    }) {
                        Text("ブラウザで開く")
                    }
                }
            }
            ThreadMediaPreviewHeader(
                title = displayTitle,
                currentIndex = currentIndex,
                totalCount = totalCount,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSave != null) {
                    FilledTonalButton(
                        onClick = {
                            AnalyticsTracker.uiControl("image_preview_save", "プレビュー画像を保存")
                            onSave()
                        },
                        enabled = isSaveEnabled && !isSaveInProgress,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.Black.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.55f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (isSaveInProgress) "保存中..." else "保存")
                    }
                }
                ThreadMediaPreviewCloseButton(onDismiss = onDismiss)
            }
        }
    }
}

/**
 * Keeps high-frequency pinch/pan state below the dialog and pager.
 * The parent only observes the threshold crossing needed to enable/disable
 * navigation, so pointer samples update a graphics layer rather than the
 * complete preview chrome.
 */
@Composable
private fun ImagePreviewTransformSurface(
    resetKey: String,
    viewportSize: IntSize,
    painter: androidx.compose.ui.graphics.painter.Painter,
    thumbnailPainter: androidx.compose.ui.graphics.painter.Painter,
    showThumbnail: Boolean,
    contentScale: ContentScale,
    isTargetError: Boolean,
    onZoomedChanged: (Boolean) -> Unit
) {
    var scale by remember(resetKey) { mutableStateOf(1f) }
    var translation by remember(resetKey) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(resetKey, viewportSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var ownsGesture = scale > IMAGE_PREVIEW_ZOOM_THRESHOLD
                    var lastWasZoomed = ownsGesture
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) ownsGesture = true
                        if (ownsGesture) {
                            val gestureZoom = if (pointerCount >= 2) event.calculateZoom() else 1f
                            val pan = event.calculatePan()
                            val oldScale = scale
                            val updatedScale =
                                (oldScale * gestureZoom).coerceIn(1f, IMAGE_PREVIEW_MAX_ZOOM)
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val viewportCenter = Offset(
                                viewportSize.width / 2f,
                                viewportSize.height / 2f
                            )
                            val focalCompensation = (centroid - viewportCenter) * (1f - gestureZoom)
                            scale = updatedScale
                            translation = if (updatedScale <= IMAGE_PREVIEW_ZOOM_THRESHOLD) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = clampImagePreviewZoomOffset(
                                        translation.x + pan.x + focalCompensation.x,
                                        viewportSize.width.toFloat(),
                                        updatedScale
                                    ),
                                    y = clampImagePreviewZoomOffset(
                                        translation.y + pan.y + focalCompensation.y,
                                        viewportSize.height.toFloat(),
                                        updatedScale
                                    )
                                )
                            }
                            val nowZoomed = updatedScale > IMAGE_PREVIEW_ZOOM_THRESHOLD
                            if (nowZoomed != lastWasZoomed) {
                                lastWasZoomed = nowZoomed
                                onZoomedChanged(nowZoomed)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        if (showThumbnail) {
            Image(
                painter = thumbnailPainter,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = translation.x
                        translationY = translation.y
                    }
            )
        }
        Image(
            painter = painter,
            contentDescription = "プレビュー画像",
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = translation.x
                    translationY = translation.y
                    alpha = if (isTargetError) 0f else 1f
                }
        )
    }
}

private const val IMAGE_PREVIEW_ZOOM_THRESHOLD = 1.05f
private const val IMAGE_PREVIEW_MAX_ZOOM = 6f

private fun clampImagePreviewZoomOffset(
    offset: Float,
    viewport: Float,
    scale: Float
): Float {
    val maxOffset = (viewport * (scale - 1f) / 2f).coerceAtLeast(0f)
    return offset.coerceIn(-maxOffset, maxOffset)
}

internal fun resolveImagePreviewRequestSize(previewSize: IntSize): IntSize? {
    return previewSize.takeIf { it.width > 0 && it.height > 0 }
}
