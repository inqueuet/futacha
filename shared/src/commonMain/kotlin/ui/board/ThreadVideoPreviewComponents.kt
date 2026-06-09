package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun VideoPreviewDialog(
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
    var playbackState by remember(entry.url) { mutableStateOf(VideoPlayerState.Buffering) }
    var areControlsVisible by remember(entry.url) { mutableStateOf(true) }
    var isMuted by remember(entry.url) { mutableStateOf(false) }
    var volume by remember(entry.url) { mutableFloatStateOf(0.9f) }
    var videoSize by remember(entry.url) { mutableStateOf<IntSize?>(null) }
    var controlPanelHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val urlLauncher = rememberUrlLauncher()
    val navigationBottomPadding = with(density) {
        if (controlPanelHeightPx > 0) {
            controlPanelHeightPx.toDp() + 48.dp
        } else {
            180.dp
        }
    }
    val chromeState = resolveVideoPreviewChromeState(playbackState, areControlsVisible)

    LaunchedEffect(playbackState, areControlsVisible) {
        if (playbackState == VideoPlayerState.Ready && areControlsVisible) {
            delay(4_000)
            areControlsVisible = false
        }
    }

    ThreadMediaPreviewDialogFrame(
        navigationKey = entry.url,
        onDismiss = onDismiss,
        onNavigateNext = onNavigateNext,
        onNavigatePrevious = onNavigatePrevious,
        isSwipeNavigationEnabled = chromeState.showsControlPanel,
        isTapNavigationEnabled = chromeState.showsControlPanel,
        isNavigationOverlayVisible = chromeState.showsControlPanel,
        navigationOverlayPadding = PaddingValues(
            start = 8.dp,
            top = 72.dp,
            end = 8.dp,
            bottom = navigationBottomPadding
        ),
        swipeNavigationPadding = PaddingValues(bottom = navigationBottomPadding)
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
                onStateChanged = { state ->
                    playbackState = state
                    areControlsVisible = state != VideoPlayerState.Ready
                },
                onVideoSizeKnown = { width, height ->
                    videoSize = if (width > 0 && height > 0) IntSize(width, height) else null
                },
                areControlsVisible = chromeState.showsControlPanel,
                onControlsVisibilityChanged = { isVisible ->
                    areControlsVisible = isVisible || playbackState != VideoPlayerState.Ready
                },
                volume = volume,
                isMuted = isMuted
            )
            if (playbackState == VideoPlayerState.Ready && !areControlsVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(entry.url) {
                            detectTapGestures {
                                areControlsVisible = true
                            }
                        }
                )
            }
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
            if (chromeState.showsControlPanel) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .fillMaxWidth()
                        .onSizeChanged { controlPanelHeightPx = it.height }
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
}
