package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlin.math.roundToInt

@Composable
internal fun ThreadMediaPreviewDialog(
    state: ThreadMediaPreviewDialogState,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onSave: (MediaPreviewEntry) -> Unit
) {
    when (state.entry.mediaType) {
        MediaType.Image -> ImagePreviewDialog(
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
private fun ThreadMediaPreviewDialogFrame(
    navigationKey: String,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    containerModifier: Modifier = Modifier,
    content: @Composable BoxScope.(IntSize) -> Unit
) {
    var swipeDistance by remember { mutableStateOf(0f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

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
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeDistance += dragAmount
                            if (swipeDistance < -200f) {
                                swipeDistance = 0f
                                onDismiss()
                            }
                        },
                        onDragEnd = { swipeDistance = 0f },
                        onDragCancel = { swipeDistance = 0f }
                    )
                }
                .then(containerModifier)
                .pointerInput(navigationKey, previewSize.width) {
                    detectTapGestures { offset ->
                        val width = previewSize.width.toFloat()
                        if (width <= 0f) return@detectTapGestures
                        if (offset.x < width / 2f) {
                            onNavigatePrevious()
                        } else {
                            onNavigateNext()
                        }
                    }
                }
        ) {
            content(previewSize)
        }
    }
}

@Composable
private fun ThreadMediaPreviewHeader(
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
private fun ThreadMediaPreviewCloseButton(
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

@Composable
private fun ImagePreviewDialog(
    entry: MediaPreviewEntry,
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
    var scale by remember { mutableStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(entry.url) {
        scale = 1f
        translation = Offset.Zero
    }
    val previewRequest = remember(entry.url) {
        ImageRequest.Builder(platformContext)
            .data(entry.url)
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = previewRequest,
        imageLoader = imageLoader
    )
    val painterState by painter.state.collectAsState()
    val isLoadingState = painterState is AsyncImagePainter.State.Loading
    val isErrorState = painterState is AsyncImagePainter.State.Error

    ThreadMediaPreviewDialogFrame(
        navigationKey = entry.url,
        onDismiss = onDismiss,
        onNavigateNext = onNavigateNext,
        onNavigatePrevious = onNavigatePrevious,
        containerModifier = Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan: Offset, zoom: Float, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                translation += pan
            }
        }
    ) { previewSize ->
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
            Image(
                painter = painter,
                contentDescription = "プレビュー画像",
                contentScale = targetContentScale,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = translation.x
                        translationY = translation.y
                        alpha = if (isErrorState) 0f else 1f
                    }
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
                    TextButton(onClick = { urlLauncher(entry.url) }) {
                        Text("ブラウザで開く")
                    }
                }
            }
            ThreadMediaPreviewHeader(
                title = entry.title,
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
                        onClick = onSave,
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

@Composable
private fun VideoPreviewDialog(
    entry: MediaPreviewEntry,
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onSave: (() -> Unit)? = null,
    isSaveEnabled: Boolean = true,
    isSaveInProgress: Boolean = false
) {
    var playbackState by remember { mutableStateOf(VideoPlayerState.Buffering) }
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.9f) }
    var videoSize by remember { mutableStateOf<IntSize?>(null) }
    val urlLauncher = rememberUrlLauncher()

    ThreadMediaPreviewDialogFrame(
        navigationKey = entry.url,
        onDismiss = onDismiss,
        onNavigateNext = onNavigateNext,
        onNavigatePrevious = onNavigatePrevious
    ) { previewSize ->
        val videoContentModifier by remember(previewSize, videoSize) {
            derivedStateOf {
                val containerWidth = previewSize.width.toFloat()
                val containerHeight = previewSize.height.toFloat()
                val size = videoSize
                if (containerWidth <= 0f || containerHeight <= 0f || size == null || size.width <= 0 || size.height <= 0) {
                    Modifier.fillMaxSize()
                } else {
                    val videoAspect = size.width.toFloat() / size.height.toFloat()
                    val containerAspect = containerWidth / containerHeight
                    if (videoAspect < containerAspect) {
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(videoAspect, matchHeightConstraintsFirst = true)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(videoAspect)
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            PlatformVideoPlayer(
                videoUrl = entry.url,
                modifier = videoContentModifier.align(Alignment.Center),
                onStateChanged = { playbackState = it },
                onVideoSizeKnown = { width, height ->
                    videoSize = if (width > 0 && height > 0) IntSize(width, height) else null
                },
                volume = volume,
                isMuted = isMuted
            )
            val chromeState = resolveVideoPreviewChromeState(playbackState)
            if (chromeState.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (chromeState.showsError) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "動画を再生できませんでした",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { urlLauncher(entry.url) }) {
                        Text("ブラウザで開く")
                    }
                }
            }
            if (chromeState.showsCloseButton) {
                ThreadMediaPreviewCloseButton(
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isMuted || volume <= 0f) {
                                        Icons.AutoMirrored.Rounded.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Rounded.VolumeUp
                                    },
                                    contentDescription = if (isMuted) "ミュート解除" else "ミュート",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = if (isMuted) "ミュート中" else "音量 ${(volume * 100).roundToInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        TextButton(onClick = { volume = 1f; isMuted = false }) {
                            Text(
                                text = "リセット",
                                color = Color.White
                            )
                        }
                    }
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            if (isMuted && it > 0f) {
                                isMuted = false
                            }
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    if (onSave != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onSave,
                                enabled = isSaveEnabled && !isSaveInProgress,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(if (isSaveInProgress) "保存中..." else "この動画を保存")
                            }
                        }
                    }
                }
            }
        }
    }
}
