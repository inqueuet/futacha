package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
actual fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    volume: Float,
    isMuted: Boolean
) {
    val context = LocalContext.current
    val currentCallback by rememberUpdatedState(onStateChanged)
    val currentSizeCallback by rememberUpdatedState(onVideoSizeKnown)
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    val mediaItem = remember(videoUrl) {
        MediaItem.fromUri(videoUrl)
    }

    LaunchedEffect(mediaItem) {
        currentCallback(VideoPlayerState.Buffering)
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    LaunchedEffect(volume, isMuted, player) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        player.volume = if (isMuted) 0f else clampedVolume
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
        modifier = modifier
    )

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> currentCallback(VideoPlayerState.Buffering)
                    Player.STATE_READY -> currentCallback(VideoPlayerState.Ready)
                    Player.STATE_ENDED -> currentCallback(VideoPlayerState.Ready)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentCallback(VideoPlayerState.Error)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                currentSizeCallback(videoSize.width, videoSize.height)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
}
