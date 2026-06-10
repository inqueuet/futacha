package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.view.MotionEvent
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
@Composable
actual fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    areControlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    volume: Float,
    isMuted: Boolean
) {
    val context = LocalContext.current
    val currentCallback by rememberUpdatedState(onStateChanged)
    val currentSizeCallback by rememberUpdatedState(onVideoSizeKnown)
    val currentControlsCallback by rememberUpdatedState(onControlsVisibilityChanged)
    var player by remember(context) { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(context) {
        val createdPlayer = ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
        player = createdPlayer
        onDispose {
            if (player === createdPlayer) {
                player = null
            }
            createdPlayer.release()
        }
    }

    val mediaItem = remember(videoUrl) {
        MediaItem.fromUri(videoUrl)
    }

    LaunchedEffect(mediaItem, player) {
        val activePlayer = player ?: return@LaunchedEffect
        currentCallback(VideoPlayerState.Buffering)
        activePlayer.setMediaItem(mediaItem)
        activePlayer.prepare()
    }

    LaunchedEffect(volume, isMuted, player) {
        player?.volume = normalizeVideoPlayerVolume(volume, isMuted)
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        currentControlsCallback(true)
                    }
                    false
                }
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
            if (areControlsVisible) {
                view.showController()
            } else {
                view.hideController()
            }
        },
        modifier = modifier
    )

    DisposableEffect(player) {
        val activePlayer = player ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> currentCallback(VideoPlayerState.Buffering)
                    Player.STATE_READY -> currentCallback(resolveReadyVideoPlayerState(activePlayer.isPlaying))
                    Player.STATE_ENDED -> currentCallback(VideoPlayerState.Idle)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                currentCallback(resolveReadyVideoPlayerState(isPlaying))
            }

            override fun onPlayerError(error: PlaybackException) {
                currentCallback(VideoPlayerState.Error)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                currentSizeCallback(videoSize.width, videoSize.height)
            }
        }
        activePlayer.addListener(listener)
        onDispose {
            activePlayer.removeListener(listener)
        }
    }
}
