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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
actual fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit
) {
    val context = LocalContext.current
    val currentCallback by rememberUpdatedState(onStateChanged)
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

    AndroidView(
        factory = {
            PlayerView(context).apply {
                useController = true
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
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
}
