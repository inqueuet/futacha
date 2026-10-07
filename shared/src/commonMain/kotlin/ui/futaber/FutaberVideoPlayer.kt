package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.board.VideoPlaybackError
import com.valoser.futacha.shared.ui.board.VideoPlayerState
import com.valoser.futacha.shared.ui.board.formatVideoPlaybackError
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long the controls stay after a video starts playing before they hide (a tap brings them back). */
private const val FUTABER_VIDEO_CONTROLS_HIDE_MILLIS = 4_000L

/**
 * The video player of the picture viewer, laid out like the video preview ふたちゃ and としあき(仮) open. It opens stopped
 * (the shared player never autoplays), ready for a deliberate play:
 * the picture's thumbnail while it buffers, the platform player with its own play / pause / seek controls, and a
 * panel with mute, volume, "停止" (stops the video and leaves the viewer) and "ブラウザで開く". A tap shows the
 * controls again once they have hidden. A video that cannot play says so and offers the browser.
 */
@Composable
fun FutaberVideoPlayer(
    videoUrl: String,
    posterUrl: String?,
    active: Boolean,
    onStop: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
    /** Space kept free under the panel for a bar the host draws over the bottom edge. */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val colors = LocalFutaberColors.current
    var state by remember(videoUrl) { mutableStateOf(VideoPlayerState.Buffering) }
    var controlsVisible by remember(videoUrl) { mutableStateOf(true) }
    var muted by remember(videoUrl) { mutableStateOf(false) }
    var volume by remember(videoUrl) { mutableFloatStateOf(0.9f) }
    var videoSize by remember(videoUrl) { mutableStateOf<IntSize?>(null) }
    var error by remember(videoUrl) { mutableStateOf<VideoPlaybackError?>(null) }

    // Counts the person's actions on the controls; each one restarts the time the controls stay.
    var interactions by remember(videoUrl) { mutableIntStateOf(0) }
    // Playing, the controls hide by themselves; anything else keeps them.
    LaunchedEffect(state, controlsVisible, interactions) {
        if (state == VideoPlayerState.Ready && controlsVisible) {
            delay(FUTABER_VIDEO_CONTROLS_HIDE_MILLIS)
            controlsVisible = false
        }
    }
    val buffering = state == VideoPlayerState.Buffering
    val failed = state == VideoPlayerState.Error
    val showPanel = futaberVideoShowsPanel(state, controlsVisible)

    Box(modifier.fillMaxSize().background(Color.Black).testTag("futaber-video-player")) {
        val size = videoSize
        val videoModifier = if (size == null || size.width <= 0 || size.height <= 0) Modifier.fillMaxSize()
        else {
            val aspect = size.width.toFloat() / size.height.toFloat()
            // Fitted inside the area, whichever side is the tighter.
            Modifier.fillMaxWidth().aspectRatio(aspect).let { wide -> if (aspect < 1f) Modifier.fillMaxHeight().aspectRatio(aspect, matchHeightConstraintsFirst = true) else wide }
        }
        PlatformVideoPlayer(
            videoUrl = videoUrl,
            modifier = videoModifier.align(Alignment.Center),
            isActive = active,
            onStateChanged = { next ->
                state = next
                if (next != VideoPlayerState.Error) error = null
            },
            onVideoSizeKnown = { width, height -> if (width > 0 && height > 0) videoSize = IntSize(width, height) },
            onPlaybackError = { error = it },
            areControlsVisible = controlsVisible,
            onControlsVisibilityChanged = { controlsVisible = it; interactions += 1 },
            volume = volume,
            isMuted = muted
        )
        if (buffering && !posterUrl.isNullOrBlank()) {
            AsyncImage(
                // Read only here: the host always provides the loader, and a video without a poster needs none.
                model = posterUrl, imageLoader = LocalFutachaImageLoader.current, contentDescription = "動画のプレビュー",
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
            )
        }
        if (state == VideoPlayerState.Ready && !controlsVisible) {
            // The platform controls are hidden: a tap anywhere shows them again.
            Box(Modifier.fillMaxSize().pointerInput(videoUrl) { detectTapGestures { controlsVisible = true } })
        }
        if (buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("futaber-video-spinner"), color = colors.accent)
        }
        if (failed) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 24.dp).testTag("futaber-video-error"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("動画を再生できませんでした", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                formatVideoPlaybackError(error)?.let {
                    Text(it, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
        if (showPanel) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = FutaberShapes.card,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)
                    .padding(bottom = bottomInset + 12.dp).navigationBarsPadding().testTag("futaber-video-panel")
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!failed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { muted = !muted; interactions += 1 }, modifier = Modifier.testTag("futaber-video-mute")) {
                                FutaberIcon(
                                    if (muted || volume <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = if (muted) "ミュート解除" else "ミュート", tint = Color.White
                                )
                            }
                            Text(
                                if (muted) "ミュート中" else "音量 ${(volume * 100).roundToInt()}%",
                                color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp)
                            )
                            Slider(
                                value = volume,
                                onValueChange = { volume = it; interactions += 1; if (muted && it > 0f) muted = false },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accent, activeTrackColor = colors.accent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp).testTag("futaber-video-volume")
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = onStop, modifier = Modifier.testTag("futaber-video-stop")) {
                            Text("停止", color = Color.White, fontSize = 15.sp)
                        }
                        TextButton(onClick = onOpenExternal, modifier = Modifier.testTag("futaber-video-external")) {
                            Text("ブラウザで開く", color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

/** The panel stays while the video is anything but playing (loading, failed, ended); playing, it follows the controls. */
internal fun futaberVideoShowsPanel(state: VideoPlayerState, controlsVisible: Boolean): Boolean =
    state != VideoPlayerState.Ready || controlsVisible
